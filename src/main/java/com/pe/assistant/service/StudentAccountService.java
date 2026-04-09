package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.repository.StudentAccountRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentAccountService {

    private static final String LOGIN_ID_PREFIX = "S";
    private static final int LOGIN_ID_RANDOM_LENGTH = 8;
    private static final int INITIAL_PASSWORD_LENGTH = 10;
    private static final String LOGIN_ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PASSWORD_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String PASSWORD_DIGITS = "23456789";
    private static final String PASSWORD_ALL = PASSWORD_LETTERS + PASSWORD_DIGITS;

    private final StudentAccountRepository studentAccountRepository;
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
        return findByLoginId(principal);
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
            return "\u672a\u751f\u6210";
        }
        if (Boolean.FALSE.equals(account.getEnabled())) {
            return "\u5df2\u7981\u7528";
        }
        if (isLocked(account)) {
            return "\u5df2\u9501\u5b9a";
        }
        if (!Boolean.TRUE.equals(account.getActivated()) || Boolean.TRUE.equals(account.getPasswordResetRequired())) {
            return "\u672a\u6fc0\u6d3b";
        }
        return "\u6b63\u5e38";
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
                .orElseThrow(() -> new IllegalArgumentException("\u8be5\u5b66\u751f\u5c1a\u672a\u751f\u6210\u8d26\u53f7"));
        if (account.getLoginId() == null || account.getLoginId().isBlank()) {
            account.setLoginId(generateUniqueLoginId());
        }
        return issuePassword(account, false);
    }

    @Transactional
    public void setEnabled(Student student, boolean enabled) {
        StudentAccount account = findByStudent(student)
                .orElseThrow(() -> new IllegalArgumentException("\u8be5\u5b66\u751f\u5c1a\u672a\u751f\u6210\u8d26\u53f7"));
        account.setEnabled(enabled);
        if (enabled) {
            account.setLocked(false);
            account.setLockedUntil(null);
        }
        studentAccountRepository.save(account);
    }

    @Transactional
    public void changePassword(StudentAccount account, String oldPassword, String newPassword) {
        if (account == null) {
            throw new IllegalArgumentException("\u5b66\u751f\u8d26\u53f7\u4e0d\u5b58\u5728");
        }
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("\u65e7\u5bc6\u7801\u548c\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e0e\u65e7\u5bc6\u7801\u76f8\u540c");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setActivated(true);
        account.setPasswordResetRequired(false);
        account.setIssuedPassword(null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setLocked(false);
        account.setLockedUntil(null);
        account.setFailedAttempts(0);
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
            throw new IllegalArgumentException("\u5bc6\u7801\u957f\u5ea6\u4e0d\u80fd\u5c11\u4e8e 8 \u4f4d");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("\u5bc6\u7801\u5fc5\u987b\u540c\u65f6\u5305\u542b\u5b57\u6bcd\u548c\u6570\u5b57");
        }
    }

    private boolean hasUsablePassword(StudentAccount account) {
        return account != null
                && account.getPasswordHash() != null
                && !account.getPasswordHash().isBlank();
    }

    private IssuedStudentAccount issueAccount(Student student, boolean regenerateLoginId, StudentAccount current) {
        if (student == null || student.getId() == null) {
            throw new IllegalArgumentException("\u5b66\u751f\u4e0d\u5b58\u5728");
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

    private String generateUniqueLoginId() {
        for (int i = 0; i < 50; i++) {
            String loginId = LOGIN_ID_PREFIX + randomChars(LOGIN_ID_RANDOM_LENGTH, LOGIN_ID_CHARS);
            if (!studentAccountRepository.existsByLoginIdIgnoreCase(loginId)) {
                return loginId;
            }
        }
        throw new IllegalStateException("\u751f\u6210\u5b66\u751f\u8d26\u53f7\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5");
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
        if (loginId == null) {
            return null;
        }
        String normalized = loginId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public void assertStudentInSchool(Student student, School school) {
        if (student == null || student.getSchool() == null || school == null
                || !Objects.equals(student.getSchool().getId(), school.getId())) {
            throw new IllegalArgumentException("\u5b66\u751f\u4e0d\u5c5e\u4e8e\u5f53\u524d\u5b66\u6821");
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
