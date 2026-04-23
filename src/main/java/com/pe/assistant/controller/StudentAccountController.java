package com.pe.assistant.controller;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.service.AccountEmailService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentAccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/password")
@RequiredArgsConstructor
public class StudentAccountController {

    private final CurrentUserService currentUserService;
    private final StudentAccountService studentAccountService;
    private final AccountEmailService accountEmailService;

    @GetMapping
    public String passwordPage(@RequestParam(defaultValue = "false") boolean force, Model model) {
        Student student = currentUserService.getCurrentStudent();
        StudentAccount account = currentUserService.getCurrentStudentAccount();
        model.addAttribute("student", student);
        model.addAttribute("account", account);
        model.addAttribute("force", force || studentAccountService.requiresPasswordChange(account));
        return "student/password";
    }

    @PostMapping
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 @RequestParam(required = false) String loginAlias,
                                 @RequestParam(defaultValue = "false") boolean force,
                                 RedirectAttributes ra) {
        try {
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("两次输入的新密码不一致");
            }
            studentAccountService.changePasswordAndUpdateLoginAlias(account, oldPassword, newPassword, loginAlias);
            ra.addFlashAttribute("success", "密码修改成功，便捷账号已保存");
            return "redirect:/student";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/password?force=" + force;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "密码修改失败");
            return "redirect:/student/password?force=" + force;
        }
    }

    @PostMapping("/email-bind/request")
    public String requestEmailBind(@RequestParam(required = false) String email,
                                   @RequestParam(defaultValue = "false") boolean force,
                                   HttpServletRequest request,
                                   RedirectAttributes ra) {
        try {
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            accountEmailService.requestStudentEmailBind(
                    account,
                    email,
                    resolveClientIp(request),
                    request.getHeader("User-Agent"));
            ra.addFlashAttribute("success", "验证邮件已生成，请前往邮箱完成验证");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "邮箱验证邮件发送失败");
        }
        return "redirect:/student/password?force=" + force;
    }

    @PostMapping("/email-notify")
    public String updateEmailNotify(@RequestParam(defaultValue = "false") boolean enabled,
                                    @RequestParam(defaultValue = "false") boolean force,
                                    RedirectAttributes ra) {
        try {
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            accountEmailService.updateStudentNotifyEnabled(account, enabled);
            ra.addFlashAttribute("success", "邮箱通知设置已更新");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "邮箱通知设置更新失败");
        }
        return "redirect:/student/password?force=" + force;
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
