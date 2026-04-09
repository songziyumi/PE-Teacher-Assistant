package com.pe.assistant.controller;

import com.pe.assistant.entity.Attendance;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Student;
import com.pe.assistant.service.AttendanceService;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        if (date == null) {
            date = LocalDate.now();
        }
        School school = currentUserService.getCurrentSchool();
        SchoolClass schoolClass = classService.findById(classId);
        boolean electiveMode = isElectiveType(schoolClass.getType());

        List<Student> students;
        List<Attendance> existing;
        if (electiveMode) {
            String electiveClassName = electiveName(schoolClass);
            students = studentService.findByElectiveClassForTeacher(school, electiveClassName);
            existing = attendanceService.findByElectiveClassAndDate(school, electiveClassName, date);
        } else {
            students = studentService.findByClassIdForTeacher(school, classId);
            existing = attendanceService.findByClassAndDate(classId, date);
        }

        Map<Long, String> statusMap = new HashMap<>();
        for (Attendance attendance : existing) {
            statusMap.put(attendance.getStudent().getId(), attendance.getStatus());
        }

        model.addAttribute("schoolClass", schoolClass);
        model.addAttribute("classTitle", (schoolClass.getGrade() != null ? schoolClass.getGrade().getName() + " " : "") + schoolClass.getName());
        model.addAttribute("mode", electiveMode ? "elective" : "admin");
        model.addAttribute("electiveName", schoolClass.getName());
        model.addAttribute("students", students);
        model.addAttribute("studentClassBands", buildStudentClassBands(students, electiveMode));
        model.addAttribute("statusMap", statusMap);
        model.addAttribute("date", date);
        return "teacher/attendance";
    }

    @GetMapping("/elective/{name}")
    public String electiveAttendancePage(@PathVariable String name,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         Model model) {
        if (date == null) {
            date = LocalDate.now();
        }
        School school = currentUserService.getCurrentSchool();
        List<Student> students = studentService.findByElectiveClassForTeacher(school, name);
        List<Attendance> existing = attendanceService.findByElectiveClassAndDate(school, name, date);
        Map<Long, String> statusMap = new HashMap<>();
        for (Attendance attendance : existing) {
            statusMap.put(attendance.getStudent().getId(), attendance.getStatus());
        }
        model.addAttribute("classTitle", "选修课：" + name);
        model.addAttribute("electiveName", name);
        model.addAttribute("mode", "elective");
        model.addAttribute("students", students);
        model.addAttribute("studentClassBands", buildStudentClassBands(students, true));
        model.addAttribute("statusMap", statusMap);
        model.addAttribute("date", date);
        return "teacher/attendance";
    }

    private static String electiveName(SchoolClass schoolClass) {
        if (schoolClass.getGrade() == null) {
            return schoolClass.getName();
        }
        return schoolClass.getGrade().getName() + "/" + schoolClass.getName();
    }

    @PostMapping("/elective/{name}/save")
    public String saveElectiveAttendance(@PathVariable String name,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam Map<String, String> params,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         RedirectAttributes ra) {
        Map<Long, String> statusMap = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("status_")) {
                Long studentId = Long.parseLong(entry.getKey().substring(7));
                statusMap.put(studentId, entry.getValue());
            }
        }
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
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("status_")) {
                Long studentId = Long.parseLong(entry.getKey().substring(7));
                statusMap.put(studentId, entry.getValue());
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

    private Map<Long, Integer> buildStudentClassBands(List<Student> students, boolean alternateByClass) {
        if (students == null || students.isEmpty()) {
            return Map.of();
        }

        if (!alternateByClass) {
            Map<Long, Integer> singleBand = new HashMap<>();
            for (Student student : students) {
                if (student != null && student.getId() != null) {
                    singleBand.put(student.getId(), 0);
                }
            }
            return singleBand;
        }

        students.sort(Comparator
                .comparing(this::resolveAdminClassLabel)
                .thenComparing(student -> safeText(student != null ? student.getStudentNo() : null))
                .thenComparing(student -> safeText(student != null ? student.getName() : null)));

        Map<Long, Integer> bands = new HashMap<>();
        String previousClassLabel = null;
        int band = -1;
        for (Student student : students) {
            if (student == null || student.getId() == null) {
                continue;
            }
            String currentClassLabel = resolveAdminClassLabel(student);
            if (!Objects.equals(previousClassLabel, currentClassLabel)) {
                band = (band + 1) % 2;
                previousClassLabel = currentClassLabel;
            }
            bands.put(student.getId(), band < 0 ? 0 : band);
        }
        return bands;
    }

    private String resolveAdminClassLabel(Student student) {
        if (student == null || student.getSchoolClass() == null) {
            return "";
        }
        SchoolClass schoolClass = student.getSchoolClass();
        String gradeName = schoolClass.getGrade() != null ? safeText(schoolClass.getGrade().getName()) : "";
        return (gradeName + " " + safeText(schoolClass.getName())).trim();
    }

    private boolean isElectiveType(String type) {
        if (type == null) {
            return false;
        }
        String value = type.trim();
        return "选修课".equals(value) || value.contains("选修") || value.contains("elective");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
