package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.AccountEmailToken;
import com.pe.assistant.entity.AccountEmailTokenPurpose;
import com.pe.assistant.entity.AccountPrincipalType;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.security.LoginPrincipalResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String GENERIC_REQUEST_MESSAGE = "如信息匹配，重置邮件已发送";

    private final LoginPrincipalResolver loginPrincipalResolver;
    private final StudentAccountRepository studentAccountRepository;
    private final TeacherRepository teacherRepository;
    private final AccountEmailService accountEmailService;
    private final EmailRateLimitService emailRateLimitService;
    private final AppMailProperties appMailProperties;
    private final StudentAccountService studentAccountService;
    private final PasswordEncoder passwordEncoder;
    private final MailOutboxService mailOutboxService;

    @Transactional
    public String requestPasswordReset(String account, String email, String requestIp, String userAgent) {
        String normalizedAccount = normalize(account);
        String normalizedEmail = accountEmailService.normalizeOptionalEmail(email);
        if (normalizedAccount == null || normalizedEmail == null) {
            return GENERIC_REQUEST_MESSAGE;
        }

        emailRateLimitService.checkLimit(
                "password-reset:account:" + normalizedAccount.toLowerCase(Locale.ROOT),
                appMailProperties.getResetPasswordLimitPerAccount(),
                Duration.ofMinutes(10),
                "重置请求过于频繁，请稍后再试");
        if (requestIp != null && !requestIp.isBlank()) {
            emailRateLimitService.checkLimit(
                    "password-reset:ip:" + requestIp,
                    appMailProperties.getResetPasswordLimitPerIp(),
                    Duration.ofMinutes(10),
                    "重置请求过于频繁，请稍后再试");
        }

        Optional<Teacher> teacherOptional = loginPrincipalResolver.findTeacher(normalizedAccount);
        if (teacherOptional.isPresent()) {
            Teacher teacher = teacherOptional.get();
            if (matchesVerifiedEmail(teacher.getEmail(), teacher.getEmailVerified(), normalizedEmail)) {
                accountEmailService.invalidateOutstandingTokens(AccountPrincipalType.TEACHER, teacher.getId(), AccountEmailTokenPurpose.RESET_PASSWORD);
                AccountEmailService.IssuedEmailToken issuedToken = accountEmailService.issuePasswordResetToken(
                        AccountPrincipalType.TEACHER, teacher.getId(), teacher.getEmail(), requestIp, userAgent);
                queuePasswordResetMail(AccountPrincipalType.TEACHER, teacher.getId(), teacher.getEmail(), issuedToken.rawToken());
            }
            return GENERIC_REQUEST_MESSAGE;
        }

        Optional<StudentAccount> studentOptional = studentAccountService.findByLoginCredential(normalizedAccount);
        if (studentOptional.isPresent()) {
            StudentAccount accountEntity = studentOptional.get();
            if (matchesVerifiedEmail(accountEntity.getEmail(), accountEntity.getEmailVerified(), normalizedEmail)) {
                accountEmailService.invalidateOutstandingTokens(AccountPrincipalType.STUDENT, accountEntity.getId(), AccountEmailTokenPurpose.RESET_PASSWORD);
                AccountEmailService.IssuedEmailToken issuedToken = accountEmailService.issuePasswordResetToken(
                        AccountPrincipalType.STUDENT, accountEntity.getId(), accountEntity.getEmail(), requestIp, userAgent);
                queuePasswordResetMail(AccountPrincipalType.STUDENT, accountEntity.getId(), accountEntity.getEmail(), issuedToken.rawToken());
            }
        }
        return GENERIC_REQUEST_MESSAGE;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verifyResetToken(String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            AccountEmailToken emailToken = accountEmailService.resolveUsableToken(token, AccountEmailTokenPurpose.RESET_PASSWORD);
            result.put("valid", true);
            result.put("principalType", emailToken.getPrincipalType().name());
        } catch (IllegalArgumentException ex) {
            result.put("valid", false);
            result.put("principalType", null);
        }
        return result;
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        AccountEmailToken emailToken = accountEmailService.resolveUsableToken(token, AccountEmailTokenPurpose.RESET_PASSWORD);
        if (emailToken.getPrincipalType() == AccountPrincipalType.STUDENT) {
            StudentAccount account = studentAccountRepository.findById(emailToken.getPrincipalId())
                    .orElseThrow(() -> new IllegalArgumentException("学生账号不存在"));
            studentAccountService.validatePassword(newPassword);
            account.setPasswordHash(passwordEncoder.encode(newPassword));
            account.setActivated(true);
            account.setPasswordResetRequired(false);
            account.setIssuedPassword(null);
            account.setLastPasswordResetAt(LocalDateTime.now());
            account.setFailedAttempts(0);
            account.setLocked(false);
            account.setLockedUntil(null);
            studentAccountRepository.save(account);
        } else {
            Teacher teacher = teacherRepository.findById(emailToken.getPrincipalId())
                    .orElseThrow(() -> new IllegalArgumentException("教师账号不存在"));
            studentAccountService.validatePassword(newPassword);
            teacher.setPassword(passwordEncoder.encode(newPassword));
            teacherRepository.save(teacher);
        }
        accountEmailService.markTokenUsed(emailToken);
        accountEmailService.invalidateOutstandingTokens(emailToken.getPrincipalType(), emailToken.getPrincipalId(), AccountEmailTokenPurpose.RESET_PASSWORD);
    }

    private boolean matchesVerifiedEmail(String currentEmail, Boolean verified, String requestedEmail) {
        return requestedEmail != null
                && Boolean.TRUE.equals(verified)
                && requestedEmail.equals(accountEmailService.normalizeOptionalEmail(currentEmail));
    }

    private void queuePasswordResetMail(AccountPrincipalType principalType, Long principalId, String targetEmail, String rawToken) {
        String resetLink = accountEmailService.buildPasswordResetLink(rawToken);
        String subject = "密码重置";
        String body = "请点击以下链接重置密码：\n" + resetLink + "\n\n如非本人操作，请忽略本邮件。";
        if (appMailProperties.isSesApiTransport()) {
            mailOutboxService.queueTemplate(
                    "RESET_PASSWORD",
                    principalType,
                    principalId,
                    targetEmail,
                    subject,
                    appMailProperties.getSesApi().requireResetPasswordTemplateId(),
                    AccountSecurityMailTemplateDataFactory.buildResetPasswordTemplateData(
                            rawToken,
                            appMailProperties.getResetPasswordExpireMinutes(),
                            appMailProperties.getProductName()),
                    body,
                    null);
        } else {
            mailOutboxService.queue("RESET_PASSWORD", principalType, principalId, targetEmail, subject, body, null);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
