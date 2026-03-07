package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/teacher/physical-tests")
@RequiredArgsConstructor
public class TeacherPhysicalTestController {

    private final PhysicalTestService physicalTestService;
    private final StudentService studentService;
    private final ClassService classService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    /**
     * 教师体质测试录入页
     * GET /teacher/physical-tests?classId=X&academicYear=Y&semester=Z
     * 选好班级+学年+学期后，展示全班学生的录入表格
     */
    @GetMapping
    public String entryPage(@RequestParam(required = false) Long classId,
                            @RequestParam(defaultValue = "") String academicYear,
                            @RequestParam(defaultValue = "上学期") String semester,
                            Model model) {
        School school = currentUserService.getCurrentSchool();
        Teacher teacher = currentUserService.getCurrentTeacher();

        // 教师自己的班级
        List<SchoolClass> myClasses = classService.findByTeacher(teacher);

        List<Student> students = new ArrayList<>();
        Map<Long, PhysicalTest> existingMap = new HashMap<>();

        // 学年默认值：当前学年（放在查询前，确保有值）
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
                students = studentService.findByElectiveClassForTeacher(school,
                        (sc.getGrade() != null ? sc.getGrade().getName() + "/" : "") + sc.getName());
            } else {
                students = studentService.findByClassIdForTeacher(school, classId);
            }
            // 查当前已有记录
            for (Student s : students) {
                physicalTestService.findExisting(s, academicYear, semester)
                        .ifPresent(t -> existingMap.put(s.getId(), t));
            }
        }

        model.addAttribute("myClasses", myClasses);
        model.addAttribute("classId", classId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("semester", semester);
        model.addAttribute("students", students);
        model.addAttribute("existingMap", existingMap);
        return "teacher/physical-test-entry";
    }

    /**
     * 批量保存（教师录入全班）
     * POST /teacher/physical-tests/save-batch
     * 表单字段：studentIds[], height[], weight[], ... (并行数组)
     */
    @PostMapping("/save-batch")
    public String saveBatch(@RequestParam Long classId,
                            @RequestParam String academicYear,
                            @RequestParam String semester,
                            @RequestParam List<Long> studentIds,
                            @RequestParam(required = false) List<String> testDateList,
                            @RequestParam(required = false) List<String> heightList,
                            @RequestParam(required = false) List<String> weightList,
                            @RequestParam(required = false) List<String> lungList,
                            @RequestParam(required = false) List<String> sprint50mList,
                            @RequestParam(required = false) List<String> sitReachList,
                            @RequestParam(required = false) List<String> jumpList,
                            @RequestParam(required = false) List<String> pullUpsList,
                            @RequestParam(required = false) List<String> sitUpsList,
                            @RequestParam(required = false) List<String> run800mList,
                            @RequestParam(required = false) List<String> run1000mList,
                            RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();

        int n = studentIds.size();
        List<Student> students = new ArrayList<>();
        List<PhysicalTest> records  = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Student stu = studentService.findById(studentIds.get(i));
            students.add(stu);

            PhysicalTest t = new PhysicalTest();
            t.setTestDate(parseDate(safeGet(testDateList, i)));
            t.setHeight(parseDouble(safeGet(heightList, i)));
            t.setWeight(parseDouble(safeGet(weightList, i)));
            t.setLungCapacity(parseInt(safeGet(lungList, i)));
            t.setSprint50m(parseDouble(safeGet(sprint50mList, i)));
            t.setSitReach(parseDouble(safeGet(sitReachList, i)));
            t.setStandingJump(parseDouble(safeGet(jumpList, i)));
            t.setPullUps(parseInt(safeGet(pullUpsList, i)));
            t.setSitUps(parseInt(safeGet(sitUpsList, i)));
            t.setRun800m(parseDouble(safeGet(run800mList, i)));
            t.setRun1000m(parseDouble(safeGet(run1000mList, i)));
            records.add(t);
        }

        int saved = 0;
        try {
            saved = physicalTestService.saveBatch(students, records, school, academicYear, semester);
            ra.addFlashAttribute("success", "保存成功，共录入/更新 " + saved + " 条记录");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        String encYear = URLEncoder.encode(academicYear, StandardCharsets.UTF_8);
        String encSem  = URLEncoder.encode(semester,     StandardCharsets.UTF_8);
        return "redirect:/teacher/physical-tests?classId=" + classId
                + "&academicYear=" + encYear + "&semester=" + encSem;
    }

    // ========== 工具方法 ==========

    private String safeGet(List<String> list, int i) {
        if (list == null || i >= list.size()) return null;
        String v = list.get(i);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private Double parseDouble(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private LocalDate parseDate(String s) {
        if (s == null) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }
}
