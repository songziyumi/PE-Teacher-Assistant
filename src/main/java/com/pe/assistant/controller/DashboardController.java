package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final TeacherService teacherService;
    private final ClassService classService;
    private final StudentService studentService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        model.addAttribute("teacher", teacher);
        boolean isAdmin = userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            model.addAttribute("classes", classService.findAll());
            model.addAttribute("electiveClasses", studentService.findAllElectiveClassNames());
        } else {
            model.addAttribute("classes", classService.findByTeacher(teacher));
            model.addAttribute("electiveClasses", studentService.findElectiveClassNamesByTeacher(teacher));
        }
        return "dashboard";
    }
}
