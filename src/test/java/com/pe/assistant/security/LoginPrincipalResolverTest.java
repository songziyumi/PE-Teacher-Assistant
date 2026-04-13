package com.pe.assistant.security;

import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.StudentAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginPrincipalResolverTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private StudentAccountService studentAccountService;

    @InjectMocks
    private LoginPrincipalResolver loginPrincipalResolver;

    @Test
    void resolveAttemptKeyShouldUseTeacherUsernameWhenTeacherExists() {
        Teacher teacher = new Teacher();
        teacher.setUsername("13800138000");
        when(teacherRepository.findByUsername("13800138000")).thenReturn(Optional.of(teacher));

        String key = loginPrincipalResolver.resolveAttemptKey("13800138000");

        assertEquals("13800138000", key);
    }

    @Test
    void resolveAttemptKeyShouldCollapseStudentAliasToInternalAccountKey() {
        StudentAccount account = new StudentAccount();
        account.setId(88L);
        when(teacherRepository.findByUsername("easy001")).thenReturn(Optional.empty());
        when(studentAccountService.findByLoginCredential("easy001")).thenReturn(Optional.of(account));

        String key = loginPrincipalResolver.resolveAttemptKey("easy001");

        assertEquals("student-account:88", key);
    }
}
