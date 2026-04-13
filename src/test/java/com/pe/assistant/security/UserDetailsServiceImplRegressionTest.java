package com.pe.assistant.security;

import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.StudentAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplRegressionTest {

    @Mock
    private LoginPrincipalResolver loginPrincipalResolver;

    @Mock
    private StudentAccountService studentAccountService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadUserByUsernameShouldResolveInternalStudentPrincipal() {
        StudentAccount account = new StudentAccount();
        account.setId(11L);
        account.setPasswordHash("$2a$10$encodedPassword");
        account.setEnabled(true);
        account.setLocked(false);

        when(loginAttemptService.isBlocked("student-account:11")).thenReturn(false);
        when(loginPrincipalResolver.findTeacher("student-account:11")).thenReturn(Optional.empty());
        when(studentAccountService.resolvePrincipal("student-account:11")).thenReturn(Optional.of(account));
        when(studentAccountService.isLocked(account)).thenReturn(false);

        UserDetails userDetails = userDetailsService.loadUserByUsername("student-account:11");

        assertEquals("student-account:11", userDetails.getUsername());
        assertEquals("$2a$10$encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority())));
    }

    @Test
    void loadUserByUsernameShouldKeepTeacherPrincipal() {
        Teacher teacher = new Teacher();
        teacher.setUsername("t001");
        teacher.setPassword("$2a$10$teacherPassword");
        teacher.setRole("TEACHER");

        when(loginPrincipalResolver.resolveAttemptKey("t001")).thenReturn("t001");
        when(loginAttemptService.isBlocked("t001")).thenReturn(false);
        when(loginPrincipalResolver.findTeacher("t001")).thenReturn(Optional.of(teacher));

        UserDetails userDetails = userDetailsService.loadUserByUsername("t001");

        assertEquals("t001", userDetails.getUsername());
        assertEquals("$2a$10$teacherPassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority())));
    }

    @Test
    void loadUserByUsernameShouldKeepAdminPrincipal() {
        Teacher admin = new Teacher();
        admin.setUsername("admin001");
        admin.setPassword("$2a$10$adminPassword");
        admin.setRole("ADMIN");

        when(loginPrincipalResolver.resolveAttemptKey("admin001")).thenReturn("admin001");
        when(loginAttemptService.isBlocked("admin001")).thenReturn(false);
        when(loginPrincipalResolver.findTeacher("admin001")).thenReturn(Optional.of(admin));

        UserDetails userDetails = userDetailsService.loadUserByUsername("admin001");

        assertEquals("admin001", userDetails.getUsername());
        assertEquals("$2a$10$adminPassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }
}
