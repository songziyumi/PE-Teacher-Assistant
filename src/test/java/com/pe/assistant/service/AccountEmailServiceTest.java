package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.AccountEmailToken;
import com.pe.assistant.entity.AccountEmailTokenPurpose;
import com.pe.assistant.entity.AccountPrincipalType;
import com.pe.assistant.entity.MailOutbox;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.AccountEmailTokenRepository;
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountEmailServiceTest {

    @Mock
    private StudentAccountRepository studentAccountRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private AccountEmailTokenRepository accountEmailTokenRepository;
    @Mock
    private MailOutboxService mailOutboxService;
    @Mock
    private EmailRateLimitService emailRateLimitService;
    @Mock
    private AppMailProperties appMailProperties;
    @Mock
    private AppMailProperties.SesApi sesApi;

    @InjectMocks
    private AccountEmailService accountEmailService;

    @Test
    void requestStudentEmailBindShouldPersistNormalizedEmailAndQueueVerificationMail() {
        StudentAccount account = new StudentAccount();
        account.setId(10L);

        when(studentAccountRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());
        when(teacherRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());
        when(studentAccountRepository.save(any(StudentAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountEmailTokenRepository.findByPrincipalTypeAndPrincipalIdAndPurposeAndUsedAtIsNull(
                AccountPrincipalType.STUDENT, 10L, AccountEmailTokenPurpose.VERIFY_EMAIL)).thenReturn(Collections.emptyList());
        when(accountEmailTokenRepository.save(any(AccountEmailToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mailOutboxService.queueTemplate(eq("VERIFY_EMAIL"), eq(AccountPrincipalType.STUDENT), eq(10L),
                eq("student@example.com"), any(), eq(101L), any(), any(), eq(null))).thenReturn(new MailOutbox());
        when(appMailProperties.getVerifyEmailLimitPerAccount()).thenReturn(3);
        when(appMailProperties.getVerifyEmailLimitPerIp()).thenReturn(10);
        when(appMailProperties.getVerifyEmailExpireMinutes()).thenReturn(30);
        when(appMailProperties.getBaseUrl()).thenReturn("http://localhost:8080");
        when(appMailProperties.getProductName()).thenReturn("体育教师助手");
        when(appMailProperties.isSesApiTransport()).thenReturn(true);
        when(appMailProperties.getSesApi()).thenReturn(sesApi);
        when(sesApi.requireVerifyEmailTemplateId()).thenReturn(101L);

        accountEmailService.requestStudentEmailBind(account, " Student@Example.com ", "127.0.0.1", "JUnit");

        assertEquals("student@example.com", account.getEmail());
        assertFalse(Boolean.TRUE.equals(account.getEmailVerified()));
        assertNotNull(account.getEmailBoundAt());
        verify(mailOutboxService).queueTemplate(eq("VERIFY_EMAIL"), eq(AccountPrincipalType.STUDENT), eq(10L),
                eq("student@example.com"), any(), eq(101L), any(), any(), eq(null));
    }

    @Test
    void confirmEmailBindShouldMarkTeacherEmailVerified() {
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        teacher.setEmail("teacher@example.com");
        teacher.setEmailVerified(false);

        AccountEmailToken token = new AccountEmailToken();
        token.setPrincipalType(AccountPrincipalType.TEACHER);
        token.setPrincipalId(20L);
        token.setPurpose(AccountEmailTokenPurpose.VERIFY_EMAIL);
        token.setTargetEmail("teacher@example.com");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(accountEmailTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(Optional.of(teacher));
        when(accountEmailTokenRepository.findByPrincipalTypeAndPrincipalIdAndPurposeAndUsedAtIsNull(
                AccountPrincipalType.TEACHER, 20L, AccountEmailTokenPurpose.VERIFY_EMAIL)).thenReturn(Collections.emptyList());
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountEmailTokenRepository.save(any(AccountEmailToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountEmailService.confirmEmailBind("raw-token");

        assertTrue(Boolean.TRUE.equals(teacher.getEmailVerified()));
        assertNotNull(teacher.getEmailVerifiedAt());
    }
}
