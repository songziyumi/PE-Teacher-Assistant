package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ClassService classService;
    private final StudentService studentService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping("/class/{classId}")
    public String attendancePage(@PathVariable Long classId,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 Model model) {
        if (date == null) date = LocalDate.now();
        SchoolClass sc = classService.findById(classId);
        boolean isElective = "选修课".equals(sc.getType());
        List<Student> students;
        List<Attendance> existing;
        if (isElective) {
            students = studentService.findByElectiveClass(sc.getName());
            existing = attendanceService.findByElectiveClassAndDate(sc.getName(), date);
        } else {
            students = studentService.findByClassId(classId);
            existing = attendanceService.findByClassAndDate(classId, date);
        }
        Map<Long, String> statusMap = new HashMap<>();
        for (Attendance a : existing) statusMap.put(a.getStudent().getId(), a.getStatus());
        model.addAttribute("schoolClass", sc);
        model.addAttribute("classTitle", (sc.getGrade() != null ? sc.getGrade().getName() + " " : "") + sc.getName());
        model.addAttribute("mode", isElective ? "elective" : "admin");
        model.addAttribute("electiveName", sc.getName());
        model.addAttribute("students", students);
        model.addAttribute("statusMap", statusMap);
        model.addAttribute("date", date);
        return "teacher/attendance";
    }

    @GetMapping("/elective/{name}")
    public String electiveAttendancePage(@PathVariable String name,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         Model model) {
        if (date == null) date = LocalDate.now();
        List<Student> students = studentService.findByElectiveClass(name);
        List<Attendance> existing = attendanceService.findByElectiveClassAndDate(name, date);
        Map<Long, String> statusMap = new HashMap<>();
        for (Attendance a : existing) statusMap.put(a.getStudent().getId(), a.getStatus());
        model.addAttribute("classTitle", "选修课：" + name);
        model.addAttribute("electiveName", name);
        model.addAttribute("mode", "elective");
        model.addAttribute("students", students);
        model.addAttribute("statusMap", statusMap);
        model.addAttribute("date", date);
        return "teacher/attendance";
    }

    @PostMapping("/elective/{name}/save")
    public String saveElectiveAttendance(@PathVariable String name,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                          @RequestParam Map<String, String> params,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          RedirectAttributes ra) {
        Map<Long, String> statusMap = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith("status_")) {
                Long studentId = Long.parseLong(e.getKey().substring(7));
                statusMap.put(studentId, e.getValue());
            }
        }
        // 选修课学生可能来自不同行政班，直接按 studentId 保存
        attendanceService.saveAttendanceByUsername(date, statusMap, userDetails.getUsername());
        ra.addFlashAttribute("success", "考勤保存成功");
        return "redirect:/attendance/elective/" + name + "?date=" + date;
    }

    @PostMapping("/class/{classId}/save")
    public String saveAttendance(@PathVariable Long classId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam Map<String, String> params,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes ra) {
        Map<Long, String> statusMap = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith("status_")) {
                Long studentId = Long.parseLong(e.getKey().substring(7));
                statusMap.put(studentId, e.getValue());
            }
        }
        attendanceService.saveAttendance(classId, date, statusMap, userDetails.getUsername());
        ra.addFlashAttribute("success", "考勤保存成功");
        return "redirect:/attendance/class/" + classId + "?date=" + date;
    }

    @GetMapping("/student/{studentId}")
    public String studentHistory(@PathVariable Long studentId, Model model) {
        Student student = studentService.findById(studentId);
        List<Attendance> records = attendanceService.findByStudent(studentId);
        Map<String, Object> stats = attendanceService.getStudentStats(studentId);
        model.addAttribute("student", student);
        model.addAttribute("records", records);
        model.addAttribute("stats", stats);
        return "teacher/student-attendance";
    }

    @PostMapping("/update/{id}")
    public String updateRecord(@PathVariable Long id,
                               @RequestParam String status,
                               @RequestParam Long studentId,
                               RedirectAttributes ra) {
        attendanceService.updateRecord(id, status);
        ra.addFlashAttribute("success", "修改成功");
        return "redirect:/attendance/student/" + studentId;
    }
}
