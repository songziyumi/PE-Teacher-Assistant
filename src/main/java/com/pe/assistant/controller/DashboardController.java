package com.pe.assistant.controller;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final TeacherService teacherService;
    private final ClassService classService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        if ("SUPER_ADMIN".equals(teacher.getRole())) {
            return "redirect:/super-admin/schools";
        }
        if ("ORG_ADMIN".equals(teacher.getRole())) {
            return "redirect:/admin/competitions";
        }
        if ("ADMIN".equals(teacher.getRole())) {
            return "redirect:/admin";
        }
        model.addAttribute("teacher", teacher);
        model.addAttribute("classes", classService.findAdminClassesByTeacher(teacher));
        model.addAttribute("electiveClasses", classService.findElectiveClassesByTeacher(teacher));
        return "dashboard";
    }
}