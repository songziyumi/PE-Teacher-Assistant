package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.AccountEmailToken;
import com.pe.assistant.entity.AccountEmailTokenPurpose;
import com.pe.assistant.entity.AccountPrincipalType;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.AccountEmailTokenRepository;
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AccountEmailService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final StudentAccountRepository studentAccountRepository;
    private final TeacherRepository teacherRepository;
    private final AccountEmailTokenRepository accountEmailTokenRepository;
    private final MailOutboxService mailOutboxService;
    private final EmailRateLimitService emailRateLimitService;
    private final AppMailProperties appMailProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public Map<String, Object> buildStudentAccountSecurity(StudentAccount account) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loginId", account != null ? account.getLoginId() : null);
        result.put("loginAlias", account != null ? account.getLoginAlias() : null);
        result.put("email", account != null ? account.getEmail() : null);
        result.put("emailVerified", account != null && Boolean.TRUE.equals(account.getEmailVerified()));
        result.put("emailNotifyEnabled", account == null || !Boolean.FALSE.equals(account.getEmailNotifyEnabled()));
        return result;
    }

    @Transactional
    public void requestStudentEmailBind(StudentAccount account, String email, String requestIp, String userAgent) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("学生账号不存在");
        }
        String normalizedEmail = normalizeAndValidateEmail(email);
        ensureEmailAvailable(normalizedEmail, account.getId(), null);
        applyEmailDraft(account, normalizedEmail);
        studentAccountRepository.save(account);
        issueVerificationToken(AccountPrincipalType.STUDENT, account.getId(), normalizedEmail, requestIp, userAgent);
    }

    @Transactional
    public void requestTeacherEmailBind(Teacher teacher, String email, String requestIp, String userAgent) {
        if (teacher == null || teacher.getId() == null) {
            throw new IllegalArgumentException("教师账号不存在");
        }
        String normalizedEmail = normalizeAndValidateEmail(email);
        ensureEmailAvailable(normalizedEmail, null, teacher.getId());
        applyEmailDraft(teacher, normalizedEmail);
        teacherRepository.save(teacher);
        issueVerificationToken(AccountPrincipalType.TEACHER, teacher.getId(), normalizedEmail, requestIp, userAgent);
    }

    @Transactional
    public void confirmEmailBind(String token) {
        AccountEmailToken emailToken = resolveUsableToken(token, AccountEmailTokenPurpose.VERIFY_EMAIL);
        String targetEmail = normalizeAndValidateEmail(emailToken.getTargetEmail());
        if (emailToken.getPrincipalType() == AccountPrincipalType.STUDENT) {
            StudentAccount account = studentAccountRepository.findById(emailToken.getPrincipalId())
                    .orElseThrow(() -> new IllegalArgumentException("学生账号不存在"));
            ensureEmailMatchesDraft(account.getEmail(), targetEmail);
            ensureEmailAvailable(targetEmail, account.getId(), null);
            markEmailVerified(account);
            studentAccountRepository.save(account);
        } else {
            Teacher teacher = teacherRepository.findById(emailToken.getPrincipalId())
                    .orElseThrow(() -> new IllegalArgumentException("教师账号不存在"));
            ensureEmailMatchesDraft(teacher.getEmail(), targetEmail);
            ensureEmailAvailable(targetEmail, null, teacher.getId());
            markEmailVerified(teacher);
            teacherRepository.save(teacher);
        }
        markTokenUsed(emailToken);
        invalidateOutstandingTokens(emailToken.getPrincipalType(), emailToken.getPrincipalId(), AccountEmailTokenPurpose.VERIFY_EMAIL);
    }

    @Transactional
    public void updateStudentNotifyEnabled(StudentAccount account, boolean enabled) {
        if (account == null) {
            throw new IllegalArgumentException("学生账号不存在");
        }
        account.setEmailNotifyEnabled(enabled);
        studentAccountRepository.save(account);
    }

    @Transactional
    public void updateTeacherNotifyEnabled(Teacher teacher, boolean enabled) {
        if (teacher == null) {
            throw new IllegalArgumentException("教师账号不存在");
        }
        teacher.setEmailNotifyEnabled(enabled);
        teacherRepository.save(teacher);
    }

    @Transactional
    public void updateTeacherEmailDraft(Teacher teacher, String email) {
        if (teacher == null) {
            throw new IllegalArgumentException("教师账号不存在");
        }
        String normalizedEmail = normalizeOptionalEmail(email);
        if (normalizedEmail == null) {
            clearTeacherEmail(teacher);
            teacherRepository.save(teacher);
            return;
        }
        validateEmailFormat(normalizedEmail);
        ensureEmailAvailable(normalizedEmail, null, teacher.getId());
        boolean changed = !Objects.equals(normalizedEmail, normalizeOptionalEmail(teacher.getEmail()));
        teacher.setEmail(normalizedEmail);
        if (changed) {
            teacher.setEmailVerified(false);
            teacher.setEmailVerifiedAt(null);
            teacher.setEmailBoundAt(LocalDateTime.now());
        } else if (teacher.getEmailBoundAt() == null) {
            teacher.setEmailBoundAt(LocalDateTime.now());
        }
        teacherRepository.save(teacher);
    }

    public void ensureEmailAvailable(String email, Long excludeStudentAccountId, Long excludeTeacherId) {
        String normalizedEmail = normalizeAndValidateEmail(email);

        Optional<StudentAccount> studentConflict = studentAccountRepository.findByEmailIgnoreCase(normalizedEmail);
        if (studentConflict.isPresent() && !Objects.equals(studentConflict.get().getId(), excludeStudentAccountId)) {
            throw new IllegalArgumentException("该邮箱已被其他学生账号使用");
        }

        Optional<Teacher> teacherConflict = teacherRepository.findByEmailIgnoreCase(normalizedEmail);
        if (teacherConflict.isPresent() && !Objects.equals(teacherConflict.get().getId(), excludeTeacherId)) {
            throw new IllegalArgumentException("该邮箱已被其他教师或管理员账号使用");
        }
    }

    public String normalizeOptionalEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public String normalizeAndValidateEmail(String email) {
        String normalizedEmail = normalizeOptionalEmail(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        validateEmailFormat(normalizedEmail);
        return normalizedEmail;
    }

    public void validateEmailFormat(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
    }

    public void checkVerifyEmailRateLimit(AccountPrincipalType principalType, Long principalId, String requestIp) {
        emailRateLimitService.checkLimit(
                "verify-email:principal:" + principalType + ":" + principalId,
                appMailProperties.getVerifyEmailLimitPerAccount(),
                Duration.ofMinutes(10),
                "验证邮件发送过于频繁，请稍后再试");
        if (requestIp != null && !requestIp.isBlank()) {
            emailRateLimitService.checkLimit(
                    "verify-email:ip:" + requestIp,
                    appMailProperties.getVerifyEmailLimitPerIp(),
                    Duration.ofMinutes(10),
                    "验证邮件发送过于频繁，请稍后再试");
        }
    }

    public AccountEmailToken resolveUsableToken(String rawToken, AccountEmailTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("令牌不能为空");
        }
        String tokenHash = hashToken(rawToken.trim());
        AccountEmailToken token = accountEmailTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("链接无效或已过期"));
        if (token.getPurpose() != purpose) {
            throw new IllegalArgumentException("链接无效或已过期");
        }
        if (token.getUsedAt() != null || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("链接无效或已过期");
        }
        return token;
    }

    @Transactional
    public void invalidateOutstandingTokens(AccountPrincipalType principalType,
                                            Long principalId,
                                            AccountEmailTokenPurpose purpose) {
        List<AccountEmailToken> tokens = accountEmailTokenRepository
                .findByPrincipalTypeAndPrincipalIdAndPurposeAndUsedAtIsNull(principalType, principalId, purpose);
        if (tokens.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AccountEmailToken token : tokens) {
            token.setUsedAt(now);
        }
        accountEmailTokenRepository.saveAll(tokens);
    }

    @Transactional
    public void markTokenUsed(AccountEmailToken token) {
        token.setUsedAt(LocalDateTime.now());
        accountEmailTokenRepository.save(token);
    }

    @Transactional
    public IssuedEmailToken issuePasswordResetToken(AccountPrincipalType principalType,
                                                    Long principalId,
                                                    String targetEmail,
                                                    String requestIp,
                                                    String userAgent) {
        return issueToken(principalType, principalId, targetEmail, AccountEmailTokenPurpose.RESET_PASSWORD,
                appMailProperties.getResetPasswordExpireMinutes(), requestIp, userAgent);
    }

    @Transactional
    public IssuedEmailToken issueVerificationToken(AccountPrincipalType principalType,
                                                   Long principalId,
                                                   String targetEmail,
                                                   String requestIp,
                                                   String userAgent) {
        checkVerifyEmailRateLimit(principalType, principalId, requestIp);
        invalidateOutstandingTokens(principalType, principalId, AccountEmailTokenPurpose.VERIFY_EMAIL);
        IssuedEmailToken issuedToken = issueToken(principalType, principalId, targetEmail,
                AccountEmailTokenPurpose.VERIFY_EMAIL, appMailProperties.getVerifyEmailExpireMinutes(), requestIp, userAgent);
        String verifyLink = buildVerifyEmailLink(issuedToken.rawToken());
        String subject = "邮箱验证";
        String body = "请点击以下链接完成邮箱验证：\n" + verifyLink + "\n\n如非本人操作，请忽略本邮件。";
        if (appMailProperties.isSesApiTransport()) {
            mailOutboxService.queueTemplate(
                    "VERIFY_EMAIL",
                    principalType,
                    principalId,
                    targetEmail,
                    subject,
                    appMailProperties.getSesApi().requireVerifyEmailTemplateId(),
                    AccountSecurityMailTemplateDataFactory.buildVerifyEmailTemplateData(
                            issuedToken.rawToken(),
                            appMailProperties.getVerifyEmailExpireMinutes(),
                            appMailProperties.getProductName()),
                    body,
                    null);
        } else {
            mailOutboxService.queue("VERIFY_EMAIL", principalType, principalId, targetEmail, subject, body, null);
        }
        return issuedToken;
    }

    public String buildVerifyEmailLink(String rawToken) {
        return appMailProperties.getBaseUrl() + "/email-verify?token=" + rawToken;
    }

    public String buildPasswordResetLink(String rawToken) {
        return appMailProperties.getBaseUrl() + "/reset-password?token=" + rawToken;
    }

    private IssuedEmailToken issueToken(AccountPrincipalType principalType,
                                        Long principalId,
                                        String targetEmail,
                                        AccountEmailTokenPurpose purpose,
                                        int expireMinutes,
                                        String requestIp,
                                        String userAgent) {
        String rawToken = generateRawToken();
        AccountEmailToken token = new AccountEmailToken();
        token.setPurpose(purpose);
        token.setPrincipalType(principalType);
        token.setPrincipalId(principalId);
        token.setTargetEmail(targetEmail);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(expireMinutes, 1)));
        token.setRequestIp(truncate(requestIp, 45));
        token.setUserAgent(truncate(userAgent, 255));
        AccountEmailToken saved = accountEmailTokenRepository.save(token);
        return new IssuedEmailToken(saved, rawToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void applyEmailDraft(StudentAccount account, String normalizedEmail) {
        boolean changed = !Objects.equals(normalizeOptionalEmail(account.getEmail()), normalizedEmail);
        account.setEmail(normalizedEmail);
        if (changed) {
            account.setEmailVerified(false);
            account.setEmailVerifiedAt(null);
            account.setEmailBoundAt(LocalDateTime.now());
        } else if (account.getEmailBoundAt() == null) {
            account.setEmailBoundAt(LocalDateTime.now());
        }
    }

    private void applyEmailDraft(Teacher teacher, String normalizedEmail) {
        boolean changed = !Objects.equals(normalizeOptionalEmail(teacher.getEmail()), normalizedEmail);
        teacher.setEmail(normalizedEmail);
        if (changed) {
            teacher.setEmailVerified(false);
            teacher.setEmailVerifiedAt(null);
            teacher.setEmailBoundAt(LocalDateTime.now());
        } else if (teacher.getEmailBoundAt() == null) {
            teacher.setEmailBoundAt(LocalDateTime.now());
        }
    }

    private void clearTeacherEmail(Teacher teacher) {
        teacher.setEmail(null);
        teacher.setEmailVerified(false);
        teacher.setEmailVerifiedAt(null);
        teacher.setEmailBoundAt(null);
    }

    private void markEmailVerified(StudentAccount account) {
        LocalDateTime now = LocalDateTime.now();
        account.setEmailVerified(true);
        if (account.getEmailBoundAt() == null) {
            account.setEmailBoundAt(now);
        }
        account.setEmailVerifiedAt(now);
    }

    private void markEmailVerified(Teacher teacher) {
        LocalDateTime now = LocalDateTime.now();
        teacher.setEmailVerified(true);
        if (teacher.getEmailBoundAt() == null) {
            teacher.setEmailBoundAt(now);
        }
        teacher.setEmailVerifiedAt(now);
    }

    private void ensureEmailMatchesDraft(String currentEmail, String targetEmail) {
        if (!Objects.equals(normalizeOptionalEmail(currentEmail), targetEmail)) {
            throw new IllegalArgumentException("邮箱已变更，请重新发起绑定");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record IssuedEmailToken(AccountEmailToken token, String rawToken) {
    }
}
