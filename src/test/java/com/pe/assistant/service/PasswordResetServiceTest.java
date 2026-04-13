package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.AccountEmailToken;
import com.pe.assistant.entity.AccountEmailTokenPurpose;
import com.pe.assistant.entity.AccountPrincipalType;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.security.LoginPrincipalResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private LoginPrincipalResolver loginPrincipalResolver;
    @Mock
    private StudentAccountRepository studentAccountRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private AccountEmailService accountEmailService;
    @Mock
    private EmailRateLimitService emailRateLimitService;
    @Mock
    private AppMailProperties appMailProperties;
    @Mock
    private AppMailProperties.SesApi sesApi;
    @Mock
    private StudentAccountService studentAccountService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailOutboxService mailOutboxService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void confirmPasswordResetShouldClearStudentLockAndForceFlag() {
        StudentAccount account = new StudentAccount();
        account.setId(12L);
        account.setLocked(true);
        account.setLockedUntil(LocalDateTime.now().plusHours(1));
        account.setPasswordResetRequired(true);
        account.setIssuedPassword("Init12345");
        account.setFailedAttempts(5);

        AccountEmailToken token = new AccountEmailToken();
        token.setPrincipalType(AccountPrincipalType.STUDENT);
        token.setPrincipalId(12L);
        token.setPurpose(AccountEmailTokenPurpose.RESET_PASSWORD);

        when(accountEmailService.resolveUsableToken("raw-token", AccountEmailTokenPurpose.RESET_PASSWORD)).thenReturn(token);
        when(studentAccountRepository.findById(12L)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded-new");
        when(studentAccountRepository.save(any(StudentAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.confirmPasswordReset("raw-token", "NewPass123");

        assertEquals("encoded-new", account.getPasswordHash());
        assertTrue(Boolean.TRUE.equals(account.getActivated()));
        assertFalse(Boolean.TRUE.equals(account.getPasswordResetRequired()));
        assertFalse(Boolean.TRUE.equals(account.getLocked()));
        assertNull(account.getLockedUntil());
        assertEquals(0, account.getFailedAttempts());
        verify(accountEmailService).markTokenUsed(token);
        verify(accountEmailService).invalidateOutstandingTokens(AccountPrincipalType.STUDENT, 12L, AccountEmailTokenPurpose.RESET_PASSWORD);
    }

    @Test
    void requestPasswordResetShouldKeepGenericResponseWhenEmailDoesNotMatch() {
        StudentAccount account = new StudentAccount();
        account.setId(30L);
        account.setEmail("bound@example.com");
        account.setEmailVerified(true);

        when(studentAccountService.findByLoginCredential("easy001")).thenReturn(Optional.of(account));
        when(loginPrincipalResolver.findTeacher("easy001")).thenReturn(Optional.empty());
        when(accountEmailService.normalizeOptionalEmail("other@example.com")).thenReturn("other@example.com");
        when(appMailProperties.getResetPasswordLimitPerAccount()).thenReturn(3);
        when(appMailProperties.getResetPasswordLimitPerIp()).thenReturn(10);
        String message = passwordResetService.requestPasswordReset("easy001", "other@example.com", "127.0.0.1", "JUnit");

        assertEquals("如信息匹配，重置邮件已发送", message);
        verify(accountEmailService, never()).issuePasswordResetToken(any(), any(), any(), any(), any());
        verify(mailOutboxService, never()).queueTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
