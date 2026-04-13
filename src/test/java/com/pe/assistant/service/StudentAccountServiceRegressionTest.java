package com.pe.assistant.service;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAccountServiceRegressionTest {

    @Mock
    private StudentAccountRepository studentAccountRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StudentAccountService studentAccountService;

    @Test
    void changePasswordShouldReturnReadableMessageWhenOldPasswordIsWrong() {
        StudentAccount account = new StudentAccount();
        account.setPasswordHash("encoded");

        when(passwordEncoder.matches("wrong-old-password", "encoded")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> studentAccountService.changePassword(account, "wrong-old-password", "NewPass123"));

        assertEquals("旧密码错误", ex.getMessage());
    }

    @Test
    void changePasswordShouldBindNormalizedLoginAlias() {
        StudentAccount account = new StudentAccount();
        account.setId(10L);
        account.setLoginId("S123456");
        account.setPasswordHash("encoded");
        account.setPasswordResetRequired(true);

        when(passwordEncoder.matches("OldPass123", "encoded")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded-new");
        when(studentAccountRepository.findByLoginAliasIgnoreCase("easy001")).thenReturn(Optional.empty());
        when(studentAccountRepository.existsByLoginIdIgnoreCase("easy001")).thenReturn(false);
        when(teacherRepository.existsByUsernameIgnoreCase("easy001")).thenReturn(false);
        when(studentAccountRepository.save(any(StudentAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentAccountService.changePasswordAndUpdateLoginAlias(account, "OldPass123", "NewPass123", "Easy001");

        assertEquals("easy001", account.getLoginAlias());
        assertTrue(account.getActivated());
        assertFalse(account.getPasswordResetRequired());
        assertNotNull(account.getLoginAliasBoundAt());
    }

    @Test
    void changePasswordShouldRejectAliasThatConflictsWithTeacherUsername() {
        StudentAccount account = new StudentAccount();
        account.setId(11L);
        account.setLoginId("S123457");
        account.setPasswordHash("encoded");
        account.setPasswordResetRequired(true);

        when(passwordEncoder.matches("OldPass123", "encoded")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "encoded")).thenReturn(false);
        when(teacherRepository.existsByUsernameIgnoreCase("teacher001")).thenReturn(true);
        when(studentAccountRepository.findByLoginAliasIgnoreCase("teacher001")).thenReturn(Optional.empty());
        when(studentAccountRepository.existsByLoginIdIgnoreCase("teacher001")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> studentAccountService.changePasswordAndUpdateLoginAlias(
                        account, "OldPass123", "NewPass123", "Teacher001"));

        assertEquals("便捷账号已被教师账号使用", ex.getMessage());
    }

    @Test
    void generateAccountShouldCreateShortNumericLoginId() {
        Student student = new Student();
        student.setId(101L);

        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(studentAccountRepository.existsByLoginIdIgnoreCase(anyString())).thenReturn(false);
        when(studentAccountRepository.save(any(StudentAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentAccountService.IssuedStudentAccount issued = studentAccountService.generateAccount(student);

        assertNotNull(issued.getAccount().getLoginId());
        assertTrue(issued.getAccount().getLoginId().matches("S\\d{6}"));
        assertNotNull(issued.getRawPassword());
    }

    @Test
    void findByLoginCredentialShouldPreferAliasThenFallbackToSystemLoginId() {
        StudentAccount account = new StudentAccount();
        account.setId(12L);
        account.setLoginAlias("easy001");

        when(studentAccountRepository.findByLoginAliasIgnoreCase("easy001")).thenReturn(Optional.of(account));

        Optional<StudentAccount> resolved = studentAccountService.findByLoginCredential("Easy001");

        assertTrue(resolved.isPresent());
        assertEquals(12L, resolved.get().getId());
    }
}
