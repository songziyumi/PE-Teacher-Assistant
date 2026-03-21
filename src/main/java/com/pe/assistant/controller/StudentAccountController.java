package com.pe.assistant.controller;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentAccountService;
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
                                 @RequestParam(defaultValue = "false") boolean force,
                                 RedirectAttributes ra) {
        try {
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("两次输入的新密码不一致");
            }
            studentAccountService.changePassword(account, oldPassword, newPassword);
            ra.addFlashAttribute("success", "密码修改成功");
            return "redirect:/student/courses";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/password?force=" + force;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "密码修改失败");
            return "redirect:/student/password?force=" + force;
        }
    }
}
