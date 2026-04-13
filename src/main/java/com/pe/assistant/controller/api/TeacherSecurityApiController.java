package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.AccountEmailService;
import com.pe.assistant.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherSecurityApiController {

    private final CurrentUserService currentUserService;
    private final AccountEmailService accountEmailService;

    @PostMapping("/email/bind/request")
    public ApiResponse<String> requestEmailBind(@RequestBody EmailBindRequest request,
                                                HttpServletRequest httpServletRequest) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            accountEmailService.requestTeacherEmailBind(
                    teacher,
                    request != null ? request.getEmail() : null,
                    resolveClientIp(httpServletRequest),
                    httpServletRequest.getHeader("User-Agent"));
            return ApiResponse.ok("验证邮件已生成", null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        }
    }

    @PostMapping("/email/bind/confirm")
    public ApiResponse<String> confirmEmailBind(@RequestBody TokenRequest request) {
        try {
            accountEmailService.confirmEmailBind(request != null ? request.getToken() : null);
            return ApiResponse.ok("邮箱绑定成功", null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, ex.getMessage());
        }
    }

    @PostMapping("/email/notify-toggle")
    public ApiResponse<String> updateNotifyToggle(@RequestBody NotifyToggleRequest request) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            accountEmailService.updateTeacherNotifyEnabled(teacher, request != null && Boolean.TRUE.equals(request.getEnabled()));
            return ApiResponse.ok("邮箱通知设置已更新", null);
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
    static class EmailBindRequest {
        private String email;
    }

    @Data
    static class TokenRequest {
        private String token;
    }

    @Data
    static class NotifyToggleRequest {
        private Boolean enabled;
    }
}
