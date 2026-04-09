package com.pe.assistant.service;

import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.repository.StudentAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAccountServiceRegressionTest {

    @Mock
    private StudentAccountRepository studentAccountRepository;

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
}
