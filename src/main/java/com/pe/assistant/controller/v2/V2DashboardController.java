package com.pe.assistant.controller.v2;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/v2")
@RequiredArgsConstructor
public class V2DashboardController {

    private final TeacherService teacherService;
    private final HealthTestService healthTestService;
    private final ExamService examService;
    private final ResourceService resourceService;
    private final StudentService studentService;

    @GetMapping("/dashboard")
    public String v2Dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        if ("SUPER_ADMIN".equals(teacher.getRole())) {
            return "redirect:/super-admin/schools";
        }

        // 获取统计数据
        Long healthTestCount = healthTestService.countByTeacher(teacher);
        Long examRecordCount = examService.countByTeacher(teacher);
        Long resourceCount = resourceService.countByTeacher(teacher);
        Long studentCount = studentService.countByTeacher(teacher);

        model.addAttribute("teacher", teacher);
        model.addAttribute("pageTitle", "v2.0 教学管理平台");
        model.addAttribute("healthTestCount", healthTestCount);
        model.addAttribute("examRecordCount", examRecordCount);
        model.addAttribute("resourceCount", resourceCount);
        model.addAttribute("studentCount", studentCount);

        return "v2/dashboard";
    }

    @GetMapping("/overview")
    public String overview(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        model.addAttribute("teacher", teacher);
        model.addAttribute("pageTitle", "平台概览");

        // 添加平台功能模块介绍
        model.addAttribute("modules", new String[] {
                "体质健康测试管理",
                "期末成绩管理系统",
                "备课资源库",
                "成绩统计分析",
                "Excel批量导入导出"
        });

        return "v2/overview";
    }
}