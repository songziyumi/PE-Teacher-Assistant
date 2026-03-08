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

    private static final List<String> STUDENT_STATUSES = List.of(
            "\u5728\u7c4d",
            "\u4f11\u5b66",
            "\u6bd5\u4e1a",
            "\u5728\u5916\u501f\u8bfb",
            "\u501f\u8bfb");

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
            if (current.getSchool() == null || !school.getId().equals(current.getSchool().getId())) {
                throw new IllegalArgumentException("\u65e0\u6743\u4fee\u6539\u8be5\u5b66\u751f");
            }

            if (newClassId != null) {
                SchoolClass targetClass = classService.findById(newClassId);
                if (targetClass.getSchool() == null
                        || !targetClass.getSchool().getId().equals(school.getId())) {
                    throw new IllegalArgumentException("\u65e0\u6743\u8c03\u6574\u5230\u8be5\u73ed\u7ea7");
                }
            }

            String normalizedStudentNo = studentNo == null ? null : studentNo.trim();
            if (normalizedStudentNo != null
                    && !normalizedStudentNo.isBlank()
                    && !studentService.isStudentNoAvailable(school, normalizedStudentNo, id)) {
                throw new IllegalArgumentException("\u5b66\u53f7\u5df2\u5b58\u5728\uff0c\u8bf7\u4f7f\u7528\u5176\u4ed6\u5b66\u53f7");
            }

            String normalizedElective = electiveClass == null || electiveClass.isBlank()
                    ? null
                    : electiveClass.trim();
            studentService.update(
                    id,
                    name.trim(),
                    gender,
                    normalizedStudentNo,
                    current.getIdCard(),
                    normalizedElective,
                    newClassId,
                    studentStatus);
            ra.addFlashAttribute("success", "\u5b66\u751f\u4fe1\u606f\u66f4\u65b0\u6210\u529f");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "\u66f4\u65b0\u5931\u8d25\uff1a" + e.getMessage());
        }
        return "redirect:/teacher/students/class/" + classId;
    }

    @PostMapping("/{id}/elective")
    public String updateElective(@PathVariable Long id,
                                 @RequestParam(required = false) String electiveClass,
                                 @RequestParam Long classId,
                                 RedirectAttributes ra) {
        studentService.updateElective(id, electiveClass);
        ra.addFlashAttribute("success", "\u9009\u8bfe\u73ed\u5df2\u66f4\u65b0");
        return "redirect:/teacher/students/class/" + classId;
    }

    @PostMapping("/{id}/class")
    public String updateClass(@PathVariable Long id,
                              @RequestParam Long newClassId,
                              @RequestParam Long classId,
                              RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass target = classService.findById(newClassId);
        if (target.getSchool() == null || !school.getId().equals(target.getSchool().getId())) {
            ra.addFlashAttribute("error", "\u65e0\u6743\u5c06\u5b66\u751f\u8c03\u81f3\u8be5\u73ed\u7ea7");
            return "redirect:/teacher/students/class/" + classId;
        }
        studentService.updateClass(id, newClassId);
        ra.addFlashAttribute("success", "\u73ed\u7ea7\u5df2\u66f4\u65b0");
        return "redirect:/teacher/students/class/" + classId;
    }

    private static String electiveName(SchoolClass sc) {
        if (sc.getGrade() == null) return sc.getName();
        return sc.getGrade().getName() + "/" + sc.getName();
    }

    private boolean isElectiveType(String type) {
        if (type == null) return false;
        String value = type.trim();
        return "\u9009\u4fee\u8bfe".equals(value) || value.contains("\u9009\u4fee") || value.contains("elective");
    }
}