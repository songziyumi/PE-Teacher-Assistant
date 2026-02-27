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
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students = "选修课".equals(sc.getType())
                ? studentService.findByElectiveClass(electiveName(sc))
                : studentService.findByClassId(classId);
        List<SchoolClass> electiveClasses = classService.findAll(school).stream()
                .filter(c -> "选修课".equals(c.getType())).toList();
        long withElective = students.stream()
                .filter(s -> s.getElectiveClass() != null && !s.getElectiveClass().isBlank())
                .count();
        model.addAttribute("schoolClass", sc);
        model.addAttribute("students", students);
        model.addAttribute("electiveClasses", electiveClasses);
        model.addAttribute("withElectiveCount", (int) withElective);
        model.addAttribute("noElectiveCount", students.size() - (int) withElective);
        return "teacher/students";
    }

    /** 返回选修课的规范名称（年级/班级名，无年级时仅班级名） */
    private static String electiveName(SchoolClass sc) {
        if (sc.getGrade() == null) return sc.getName();
        return sc.getGrade().getName() + "/" + sc.getName();
    }

    @PostMapping("/{id}/elective")
    public String updateElective(@PathVariable Long id,
                                 @RequestParam(required = false) String electiveClass,
                                 @RequestParam Long classId,
                                 RedirectAttributes ra) {
        studentService.updateElective(id, electiveClass);
        ra.addFlashAttribute("success", "选修班已更新");
        return "redirect:/teacher/students/class/" + classId;
    }
}
