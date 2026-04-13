package com.pe.assistant.controller;

import com.pe.assistant.service.AccountEmailService;
import com.pe.assistant.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PasswordResetPageController {

    private final PasswordResetService passwordResetService;
    private final AccountEmailService accountEmailService;

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam(required = false) String account,
                                       @RequestParam(required = false) String email,
                                       HttpServletRequest request,
                                       Model model) {
        try {
            String message = passwordResetService.requestPasswordReset(
                    account,
                    email,
                    resolveClientIp(request),
                    request.getHeader("User-Agent"));
            model.addAttribute("success", message);
            model.addAttribute("submitted", true);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("account", account);
            model.addAttribute("email", email);
        }
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        Map<String, Object> verification = passwordResetService.verifyResetToken(token);
        boolean valid = Boolean.TRUE.equals(verification.get("valid"));
        model.addAttribute("token", token);
        model.addAttribute("valid", valid);
        model.addAttribute("principalType", verification.get("principalType"));
        if (!valid) {
            model.addAttribute("error", "重置链接无效或已过期，请重新申请。");
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String confirmPasswordReset(@RequestParam(required = false) String token,
                                       @RequestParam(required = false) String newPassword,
                                       @RequestParam(required = false) String confirmPassword,
                                       Model model) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("valid", true);
            model.addAttribute("error", "两次输入的新密码不一致");
            return "auth/reset-password";
        }
        try {
            passwordResetService.confirmPasswordReset(token, newPassword);
            return "redirect:/login?resetSuccess=true";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("token", token);
            model.addAttribute("valid", true);
            model.addAttribute("error", ex.getMessage());
            return "auth/reset-password";
        }
    }

    @GetMapping("/email-verify")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        try {
            accountEmailService.confirmEmailBind(token);
            model.addAttribute("success", "邮箱验证成功，当前可用于忘记密码。");
            model.addAttribute("verified", true);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("verified", false);
        }
        return "auth/email-verify";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex >= 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
