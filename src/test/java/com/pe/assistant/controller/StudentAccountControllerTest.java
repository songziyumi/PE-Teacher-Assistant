package com.pe.assistant.controller;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.service.AccountEmailService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private StudentAccountService studentAccountService;
    @MockBean
    private AccountEmailService accountEmailService;

    @Test
    void requestEmailBindShouldRedirectBackToPasswordPage() throws Exception {
        StudentAccount account = new StudentAccount();
        account.setId(11L);
        Student student = new Student();
        student.setId(101L);

        when(currentUserService.getCurrentStudentAccount()).thenReturn(account);
        when(currentUserService.getCurrentStudent()).thenReturn(student);

        mockMvc.perform(post("/student/password/email-bind/request")
                        .param("email", "student@example.com")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/student/password?force=true*"));

        verify(accountEmailService).requestStudentEmailBind(eq(account), eq("student@example.com"), any(), any());
    }

    @Test
    void updateEmailNotifyShouldRedirectBackToPasswordPage() throws Exception {
        StudentAccount account = new StudentAccount();
        account.setId(12L);

        when(currentUserService.getCurrentStudentAccount()).thenReturn(account);

        mockMvc.perform(post("/student/password/email-notify")
                        .param("enabled", "true")
                        .param("force", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/student/password?force=false*"));

        verify(accountEmailService).updateStudentNotifyEnabled(account, true);
    }
}
