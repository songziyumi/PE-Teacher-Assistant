package com.pe.assistant.controller;

import com.pe.assistant.service.AccountEmailService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = PasswordResetPageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetPageControllerTest {

    private static final class FakeCsrfToken {
        public String getParameterName() {
            return "_csrf";
        }

        public String getToken() {
            return "token";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordResetService passwordResetService;
    @MockBean
    private AccountEmailService accountEmailService;
    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void forgotPasswordPageShouldRender() throws Exception {
        mockMvc.perform(get("/forgot-password").requestAttr("_csrf", new FakeCsrfToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"));
    }

    @Test
    void requestPasswordResetShouldShowGenericSuccess() throws Exception {
        when(passwordResetService.requestPasswordReset(eq("easy001"), eq("student@example.com"), any(), any()))
                .thenReturn("如信息匹配，重置邮件已发送");

        mockMvc.perform(post("/forgot-password")
                        .requestAttr("_csrf", new FakeCsrfToken())
                        .param("account", "easy001")
                        .param("email", "student@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attribute("submitted", true))
                .andExpect(model().attribute("success", "如信息匹配，重置邮件已发送"));
    }

    @Test
    void resetPasswordPageShouldRenderValidTokenForm() throws Exception {
        when(passwordResetService.verifyResetToken("raw-token"))
                .thenReturn(Map.of("valid", true, "principalType", "STUDENT"));

        mockMvc.perform(get("/reset-password")
                        .requestAttr("_csrf", new FakeCsrfToken())
                        .param("token", "raw-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("valid", true))
                .andExpect(model().attribute("principalType", "STUDENT"));
    }

    @Test
    void confirmPasswordResetShouldRejectMismatchedPassword() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .requestAttr("_csrf", new FakeCsrfToken())
                        .param("token", "raw-token")
                        .param("newPassword", "NewPass123")
                        .param("confirmPassword", "OtherPass123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("valid", true))
                .andExpect(model().attribute("error", "两次输入的新密码不一致"));

        verify(passwordResetService, never()).confirmPasswordReset(any(), any());
    }

    @Test
    void emailVerifyShouldRenderSuccessPage() throws Exception {
        mockMvc.perform(get("/email-verify").param("token", "raw-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/email-verify"))
                .andExpect(model().attribute("verified", true));

        verify(accountEmailService).confirmEmailBind("raw-token");
    }
}
