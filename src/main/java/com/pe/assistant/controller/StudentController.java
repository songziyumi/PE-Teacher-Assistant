package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/teacher/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final ClassService classService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping("/class/{classId}")
    public String listStudents(@PathVariable Long classId, Model model) {
        SchoolClass sc = classService.findById(classId);
        List<Student> students = "选修课".equals(sc.getType())
                ? studentService.findByElectiveClass(sc.getName())
                : studentService.findByClassId(classId);
        model.addAttribute("schoolClass", sc);
        model.addAttribute("students", students);
        return "teacher/students";
    }
}
