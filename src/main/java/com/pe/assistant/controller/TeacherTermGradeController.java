package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/teacher/term-grades")
@RequiredArgsConstructor
public class TeacherTermGradeController {

    private final TermGradeService termGradeService;
    private final ClassService classService;
    private final StudentService studentService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping
    public String entryPage(@RequestParam(required = false) Long classId,
                            @RequestParam(defaultValue = "") String academicYear,
                            @RequestParam(defaultValue = "上学期") String semester,
                            Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        List<SchoolClass> myClasses = classService.findByTeacher(teacher);

        List<Student> students = new ArrayList<>();
        Map<Long, TermGrade> existingMap = new HashMap<>();

        // 学年默认值（放在查询前）
        if (academicYear.isBlank()) {
            int year = LocalDate.now().getYear();
            int month = LocalDate.now().getMonthValue();
            academicYear = month >= 9
                    ? year + "-" + (year + 1)
                    : (year - 1) + "-" + year;
        }

        if (classId != null) {
            SchoolClass sc = classService.findById(classId);
            if ("选修课".equals(sc.getType())) {
                students = studentService.findByElectiveClass(
                        (sc.getGrade() != null ? sc.getGrade().getName() + "/" : "") + sc.getName());
            } else {
                students = studentService.findByClassId(classId);
            }
            for (Student s : students) {
                termGradeService.findExisting(s, academicYear, semester)
                        .ifPresent(g -> existingMap.put(s.getId(), g));
            }
        }

        model.addAttribute("myClasses", myClasses);
        model.addAttribute("classId", classId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("semester", semester);
        model.addAttribute("students", students);
        model.addAttribute("existingMap", existingMap);
        return "teacher/term-grade-entry";
    }

    @PostMapping("/save-batch")
    public String saveBatch(@RequestParam Long classId,
                            @RequestParam String academicYear,
                            @RequestParam String semester,
                            @RequestParam List<Long> studentIds,
                            @RequestParam(required = false) List<String> attendanceList,
                            @RequestParam(required = false) List<String> skillList,
                            @RequestParam(required = false) List<String> theoryList,
                            @RequestParam(required = false) List<String> remarkList,
                            RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        List<Student> students = new ArrayList<>();
        List<TermGrade> records = new ArrayList<>();

        for (int i = 0; i < studentIds.size(); i++) {
            students.add(studentService.findById(studentIds.get(i)));
            TermGrade g = new TermGrade();
            g.setAttendanceScore(parseDouble(safeGet(attendanceList, i)));
            g.setSkillScore(parseDouble(safeGet(skillList, i)));
            g.setTheoryScore(parseDouble(safeGet(theoryList, i)));
            g.setRemark(safeGet(remarkList, i));
            records.add(g);
        }

        int saved = 0;
        try {
            saved = termGradeService.saveBatch(students, records, school, academicYear, semester);
            ra.addFlashAttribute("success", "保存成功，共录入 " + saved + " 条记录");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        String encYear = URLEncoder.encode(academicYear, StandardCharsets.UTF_8);
        String encSem  = URLEncoder.encode(semester,     StandardCharsets.UTF_8);
        return "redirect:/teacher/term-grades?classId=" + classId
                + "&academicYear=" + encYear + "&semester=" + encSem;
    }

    private String safeGet(List<String> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
    }
}
