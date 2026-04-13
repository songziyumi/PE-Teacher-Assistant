package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetApiController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    public ApiResponse<String> requestPasswordReset(@RequestBody PasswordResetRequest request,
                                                    HttpServletRequest httpServletRequest) {
        try {
            String message = passwordResetService.requestPasswordReset(
                    request != null ? request.getAccount() : null,
                    request != null ? request.getEmail() : null,
                    resolveClientIp(httpServletRequest),
                    httpServletRequest.getHeader("User-Agent"));
            return ApiResponse.ok(message, null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        }
    }

    @GetMapping("/verify")
    public ApiResponse<Map<String, Object>> verifyToken(@RequestParam String token) {
        return ApiResponse.ok(passwordResetService.verifyResetToken(token));
    }

    @PostMapping("/confirm")
    public ApiResponse<String> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        try {
            passwordResetService.confirmPasswordReset(
                    request != null ? request.getToken() : null,
                    request != null ? request.getNewPassword() : null);
            return ApiResponse.ok("密码重置成功", null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex >= 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    static class PasswordResetRequest {
        private String account;
        private String email;
    }

    @Data
    static class PasswordResetConfirmRequest {
        private String token;
        private String newPassword;
    }
}
