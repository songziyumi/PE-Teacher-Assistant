package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Student;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.GradeService;
import com.pe.assistant.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/teacher/students")
@RequiredArgsConstructor
public class StudentController {

    private static final List<String> STUDENT_STATUSES = List.of("在籍", "休学", "毕业", "在外借读", "借读");

    private final StudentService studentService;
    private final ClassService classService;
    private final GradeService gradeService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping("/class/{classId}")
    public String listStudents(@PathVariable Long classId, Model model) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);

        List<Student> students = isElectiveType(sc.getType())
                ? studentService.findByElectiveClassForTeacher(school, electiveName(sc))
                : studentService.findByClassIdForTeacher(school, classId);

        List<SchoolClass> electiveClasses = classService.findAll(school).stream()
                .filter(c -> isElectiveType(c.getType()))
                .toList();
        List<SchoolClass> adminClasses = classService.findAll(school).stream()
                .filter(c -> !isElectiveType(c.getType()))
                .toList();

        long withElective = students.stream()
                .filter(s -> s.getElectiveClass() != null && !s.getElectiveClass().isBlank())
                .count();

        model.addAttribute("schoolClass", sc);
        model.addAttribute("students", students);
        model.addAttribute("electiveClasses", electiveClasses);
        model.addAttribute("adminClasses", adminClasses);
        model.addAttribute("withElectiveCount", (int) withElective);
        model.addAttribute("noElectiveCount", students.size() - (int) withElective);
        model.addAttribute("grades", gradeService.findAll(school));
        model.addAttribute("studentStatuses", STUDENT_STATUSES);
        return "teacher/students";
    }

    @PostMapping("/{id}/edit")
    public String updateStudent(@PathVariable Long id,
                                @RequestParam String name,
                                @RequestParam String gender,
                                @RequestParam(required = false) String studentNo,
                                @RequestParam(required = false) String studentStatus,
                                @RequestParam(required = false) Long newClassId,
                                @RequestParam(required = false) String electiveClass,
                                @RequestParam Long classId,
                                RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        try {
            Student current = studentService.findById(id);
            if (newClassId != null) {
                SchoolClass targetClass = classService.findById(newClassId);
                if (targetClass.getSchool() == null
                        || !targetClass.getSchool().getId().equals(school.getId())) {
                    throw new IllegalArgumentException("无权调整到该班级");
                }
            }
            String normalizedElective = electiveClass == null || electiveClass.isBlank()
                    ? null
                    : electiveClass.trim();
            studentService.update(
                    id,
                    name.trim(),
                    gender,
                    studentNo == null ? null : studentNo.trim(),
                    current.getIdCard(),
                    normalizedElective,
                    newClassId,
                    studentStatus);
            ra.addFlashAttribute("success", "学生信息更新成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "更新失败：" + e.getMessage());
        }
        return "redirect:/teacher/students/class/" + classId;
    }

    @PostMapping("/{id}/elective")
    public String updateElective(@PathVariable Long id,
                                 @RequestParam(required = false) String electiveClass,
                                 @RequestParam Long classId,
                                 RedirectAttributes ra) {
        studentService.updateElective(id, electiveClass);
        ra.addFlashAttribute("success", "选课班已更新");
        return "redirect:/teacher/students/class/" + classId;
    }

    @PostMapping("/{id}/class")
    public String updateClass(@PathVariable Long id,
                              @RequestParam Long newClassId,
                              @RequestParam Long classId,
                              RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass target = classService.findById(newClassId);
        if (!school.getId().equals(target.getSchool().getId())) {
            ra.addFlashAttribute("error", "无权将学生调至该班级");
            return "redirect:/teacher/students/class/" + classId;
        }
        studentService.updateClass(id, newClassId);
        ra.addFlashAttribute("success", "班级已更新");
        return "redirect:/teacher/students/class/" + classId;
    }

    private static String electiveName(SchoolClass sc) {
        if (sc.getGrade() == null) return sc.getName();
        return sc.getGrade().getName() + "/" + sc.getName();
    }

    private boolean isElectiveType(String type) {
        if (type == null) return false;
        String value = type.trim();
        return "选修课".equals(value) || value.contains("选修") || value.contains("閫変慨");
    }
}
