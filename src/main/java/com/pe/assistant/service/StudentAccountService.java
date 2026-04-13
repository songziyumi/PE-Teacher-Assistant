package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StudentAccountService {

    private static final String LOGIN_ID_PREFIX = "S";
    private static final int LOGIN_ID_RANDOM_LENGTH = 6;
    private static final int INITIAL_PASSWORD_LENGTH = 10;
    private static final String LOGIN_ID_CHARS = "0123456789";
    private static final String PASSWORD_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String PASSWORD_DIGITS = "23456789";
    private static final String PASSWORD_ALL = PASSWORD_LETTERS + PASSWORD_DIGITS;
    private static final Pattern CUSTOM_LOGIN_ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");

    private final StudentAccountRepository studentAccountRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public Optional<StudentAccount> findByStudent(Student student) {
        if (student == null) {
            return Optional.empty();
        }
        return studentAccountRepository.findByStudent(student);
    }

    public Optional<StudentAccount> findByStudentId(Long studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        return studentAccountRepository.findByStudentId(studentId);
    }

    public Optional<StudentAccount> findByLoginId(String loginId) {
        String normalized = normalizeLoginId(loginId);
        if (normalized == null) {
            return Optional.empty();
        }
        return studentAccountRepository.findByLoginIdIgnoreCase(normalized);
    }

    public Optional<StudentAccount> findByLoginAlias(String loginAlias) {
        String normalized = normalizeLoginAlias(loginAlias);
        if (normalized == null) {
            return Optional.empty();
        }
        return studentAccountRepository.findByLoginAliasIgnoreCase(normalized);
    }

    public Optional<StudentAccount> findByLoginCredential(String loginInput) {
        String normalized = normalizePrincipal(loginInput);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<StudentAccount> aliasMatch = findByLoginAlias(normalized);
        return aliasMatch.isPresent() ? aliasMatch : findByLoginId(normalized);
    }

    public boolean existsLoginAlias(String loginAlias) {
        String normalized = normalizeLoginAlias(loginAlias);
        return normalized != null && studentAccountRepository.existsByLoginAliasIgnoreCase(normalized);
    }

    public void assertTeacherUsernameAvailable(String username) {
        String normalized = normalizeLoginAlias(username);
        if (normalized != null && studentAccountRepository.existsByLoginAliasIgnoreCase(normalized)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
    }

    public Optional<StudentAccount> resolvePrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return Optional.empty();
        }
        if (principal.startsWith("student-account:")) {
            try {
                Long id = Long.parseLong(principal.substring("student-account:".length()));
                return studentAccountRepository.findById(id);
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        if (principal.startsWith("student:")) {
            try {
                Long studentId = Long.parseLong(principal.substring("student:".length()));
                return findByStudentId(studentId);
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return findByLoginCredential(principal);
    }

    public Map<Long, StudentAccount> mapByStudents(Collection<Student> students) {
        Map<Long, StudentAccount> result = new LinkedHashMap<>();
        if (students == null || students.isEmpty()) {
            return result;
        }
        for (StudentAccount account : studentAccountRepository.findByStudentIn(students)) {
            if (account.getStudent() != null && account.getStudent().getId() != null) {
                result.put(account.getStudent().getId(), account);
            }
        }
        return result;
    }

    public boolean requiresPasswordChange(StudentAccount account) {
        return account != null && Boolean.TRUE.equals(account.getPasswordResetRequired());
    }

    public boolean isLocked(StudentAccount account) {
        if (account == null) {
            return false;
        }
        if (Boolean.TRUE.equals(account.getLocked())) {
            return true;
        }
        return account.getLockedUntil() != null && account.getLockedUntil().isAfter(LocalDateTime.now());
    }

    public String resolveStatus(StudentAccount account) {
        if (account == null) {
            return "未生成";
        }
        if (Boolean.FALSE.equals(account.getEnabled())) {
            return "已禁用";
        }
        if (isLocked(account)) {
            return "已锁定";
        }
        if (!Boolean.TRUE.equals(account.getActivated()) || Boolean.TRUE.equals(account.getPasswordResetRequired())) {
            return "未激活";
        }
        return "正常";
    }

    @Transactional
    public Optional<IssuedStudentAccount> initializeAccount(Student student) {
        if (student == null || student.getId() == null) {
            return Optional.empty();
        }
        Optional<StudentAccount> existing = findByStudent(student);
        if (existing.isPresent() && hasUsablePassword(existing.get())) {
            return Optional.empty();
        }
        return Optional.of(issueAccount(student, false, existing.orElse(null)));
    }

    @Transactional
    public IssuedStudentAccount generateAccount(Student student) {
        return issueAccount(student, false, findByStudent(student).orElse(null));
    }

    @Transactional
    public IssuedStudentAccount regenerateAccount(Student student) {
        return issueAccount(student, true, findByStudent(student).orElse(null));
    }

    @Transactional
    public IssuedStudentAccount resetPassword(Student student) {
        StudentAccount account = findByStudent(student)
                .orElseThrow(() -> new IllegalArgumentException("该学生尚未生成账号"));
        if (account.getLoginId() == null || account.getLoginId().isBlank()) {
            account.setLoginId(generateUniqueLoginId());
        }
        return issuePassword(account, false);
    }

    @Transactional
    public void setEnabled(Student student, boolean enabled) {
        StudentAccount account = findByStudent(student)
                .orElseThrow(() -> new IllegalArgumentException("该学生尚未生成账号"));
        account.setEnabled(enabled);
        if (enabled) {
            account.setLocked(false);
            account.setLockedUntil(null);
        }
        studentAccountRepository.save(account);
    }

    @Transactional
    public void changePassword(StudentAccount account, String oldPassword, String newPassword) {
        changePasswordAndUpdateLoginAlias(account, oldPassword, newPassword, null);
    }

    @Transactional
    public void changePasswordAndUpdateLoginAlias(StudentAccount account,
                                                  String oldPassword,
                                                  String newPassword,
                                                  String loginAlias) {
        if (account == null) {
            throw new IllegalArgumentException("学生账号不存在");
        }
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("旧密码和新密码不能为空");
        }
        if (!passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }

        String resolvedAlias = resolveLoginAliasForPasswordChange(account, loginAlias);

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setActivated(true);
        account.setPasswordResetRequired(false);
        account.setIssuedPassword(null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setLocked(false);
        account.setLockedUntil(null);
        account.setFailedAttempts(0);
        applyLoginAlias(account, resolvedAlias);
        studentAccountRepository.save(account);
    }

    @Transactional
    public void markLoginSuccess(StudentAccount account) {
        if (account == null) {
            return;
        }
        account.setLastLoginAt(LocalDateTime.now());
        account.setFailedAttempts(0);
        account.setLocked(false);
        account.setLockedUntil(null);
        studentAccountRepository.save(account);
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度不能少于 8 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }

    public void validateCustomLoginAlias(String loginAlias) {
        if (loginAlias == null || loginAlias.isBlank()) {
            throw new IllegalArgumentException("便捷账号不能为空");
        }
        if (!CUSTOM_LOGIN_ALIAS_PATTERN.matcher(loginAlias).matches()) {
            throw new IllegalArgumentException("便捷账号需为 4-20 位字母或数字");
        }
    }

    private boolean hasUsablePassword(StudentAccount account) {
        return account != null
                && account.getPasswordHash() != null
                && !account.getPasswordHash().isBlank();
    }

    private IssuedStudentAccount issueAccount(Student student, boolean regenerateLoginId, StudentAccount current) {
        if (student == null || student.getId() == null) {
            throw new IllegalArgumentException("学生不存在");
        }
        StudentAccount account = current != null ? current : new StudentAccount();
        account.setStudent(student);
        if (regenerateLoginId || account.getLoginId() == null || account.getLoginId().isBlank()) {
            account.setLoginId(generateUniqueLoginId());
        }
        return issuePassword(account, current == null);
    }

    private IssuedStudentAccount issuePassword(StudentAccount account, boolean created) {
        String rawPassword = generateInitialPassword();
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setEnabled(true);
        account.setLocked(false);
        account.setLockedUntil(null);
        account.setFailedAttempts(0);
        account.setActivated(false);
        account.setPasswordResetRequired(true);
        account.setIssuedPassword(rawPassword);
        account.setLastPasswordResetAt(LocalDateTime.now());
        StudentAccount saved = studentAccountRepository.save(account);
        return new IssuedStudentAccount(saved, rawPassword, created);
    }

    private String resolveLoginAliasForPasswordChange(StudentAccount account, String loginAlias) {
        String currentAlias = normalizeLoginAlias(account.getLoginAlias());
        String requestedAlias = normalizeLoginAlias(loginAlias);
        boolean aliasRequired = requiresPasswordChange(account) && currentAlias == null;

        if (requestedAlias == null) {
            if (aliasRequired) {
                throw new IllegalArgumentException("请先设置便捷账号");
            }
            return currentAlias;
        }

        validateCustomLoginAlias(requestedAlias);
        if (account.getLoginId() != null && requestedAlias.equalsIgnoreCase(account.getLoginId())) {
            throw new IllegalArgumentException("便捷账号不能与系统账号相同");
        }

        Optional<StudentAccount> existingAlias = studentAccountRepository.findByLoginAliasIgnoreCase(requestedAlias);
        if (existingAlias.isPresent() && !Objects.equals(existingAlias.get().getId(), account.getId())) {
            throw new IllegalArgumentException("便捷账号已被其他学生使用");
        }
        if (studentAccountRepository.existsByLoginIdIgnoreCase(requestedAlias)) {
            throw new IllegalArgumentException("便捷账号不能与已有系统账号重复");
        }
        if (teacherRepository.existsByUsernameIgnoreCase(requestedAlias)) {
            throw new IllegalArgumentException("便捷账号已被教师账号使用");
        }
        return requestedAlias;
    }

    private void applyLoginAlias(StudentAccount account, String loginAlias) {
        if (loginAlias == null) {
            return;
        }
        String currentAlias = normalizeLoginAlias(account.getLoginAlias());
        if (!Objects.equals(currentAlias, loginAlias)) {
            account.setLoginAlias(loginAlias);
            account.setLoginAliasBoundAt(LocalDateTime.now());
            return;
        }
        if (account.getLoginAliasBoundAt() == null) {
            account.setLoginAliasBoundAt(LocalDateTime.now());
        }
    }

    private String generateUniqueLoginId() {
        for (int i = 0; i < 50; i++) {
            String loginId = LOGIN_ID_PREFIX + randomChars(LOGIN_ID_RANDOM_LENGTH, LOGIN_ID_CHARS);
            if (!studentAccountRepository.existsByLoginIdIgnoreCase(loginId)) {
                return loginId;
            }
        }
        throw new IllegalStateException("生成学生账号失败，请重试");
    }

    private String generateInitialPassword() {
        StringBuilder builder = new StringBuilder(INITIAL_PASSWORD_LENGTH);
        builder.append(randomChars(1, PASSWORD_LETTERS));
        builder.append(randomChars(1, PASSWORD_DIGITS));
        builder.append(randomChars(INITIAL_PASSWORD_LENGTH - 2, PASSWORD_ALL));
        char[] chars = builder.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int swapIndex = secureRandom.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[swapIndex];
            chars[swapIndex] = temp;
        }
        return new String(chars);
    }

    private String randomChars(int length, String chars) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private String normalizeLoginId(String loginId) {
        String normalized = normalizePrincipal(loginId);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeLoginAlias(String loginAlias) {
        String normalized = normalizePrincipal(loginAlias);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePrincipal(String principal) {
        if (principal == null) {
            return null;
        }
        String normalized = principal.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public void assertStudentInSchool(Student student, School school) {
        if (student == null || student.getSchool() == null || school == null
                || !Objects.equals(student.getSchool().getId(), school.getId())) {
            throw new IllegalArgumentException("学生不属于当前学校");
        }
    }

    @Data
    @AllArgsConstructor
    public static class IssuedStudentAccount {
        private StudentAccount account;
        private String rawPassword;
        private boolean created;
    }
}
