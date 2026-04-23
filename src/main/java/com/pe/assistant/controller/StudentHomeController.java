package com.pe.assistant.controller;

import com.pe.assistant.entity.Attendance;
import com.pe.assistant.entity.PhysicalTest;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.TermGrade;
import com.pe.assistant.repository.PhysicalTestRepository;
import com.pe.assistant.repository.TermGradeRepository;
import com.pe.assistant.service.AttendanceService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StudentHomeController {

    private final CurrentUserService currentUserService;
    private final AttendanceService attendanceService;
    private final MessageService messageService;
    private final PhysicalTestRepository physicalTestRepository;
    private final TermGradeRepository termGradeRepository;

    @GetMapping("/student")
    public String home(Model model) {
        Student student = currentUserService.getCurrentStudent();
        List<PhysicalTest> physicalTests = physicalTestRepository.findByStudentOrderByAcademicYearDescSemesterDesc(student);
        List<TermGrade> termGrades = termGradeRepository.findByStudentOrderByAcademicYearDescSemesterDesc(student);
        Map<String, Object> attendanceStats = attendanceService.getStudentStats(student.getId());

        model.addAttribute("student", student);
        model.addAttribute("latestPhysicalTest", physicalTests.isEmpty() ? null : physicalTests.get(0));
        model.addAttribute("latestTermGrade", termGrades.isEmpty() ? null : termGrades.get(0));
        model.addAttribute("attendanceStats", attendanceStats);
        model.addAttribute("unreadMessageCount", messageService.getUnreadCount("STUDENT", student.getId()));
        return "student/home";
    }

    @GetMapping("/student/physical-tests")
    public String physicalTests(Model model) {
        Student student = currentUserService.getCurrentStudent();
        model.addAttribute("student", student);
        model.addAttribute("records", physicalTestRepository.findByStudentOrderByAcademicYearDescSemesterDesc(student));
        return "student/physical-tests";
    }

    @GetMapping("/student/attendance")
    public String attendance(Model model) {
        Student student = currentUserService.getCurrentStudent();
        List<Attendance> records = attendanceService.findByStudent(student.getId());
        model.addAttribute("student", student);
        model.addAttribute("records", records);
        model.addAttribute("stats", attendanceService.getStudentStats(student.getId()));
        return "student/attendance";
    }

    @GetMapping("/student/term-grades")
    public String termGrades(Model model) {
        Student student = currentUserService.getCurrentStudent();
        model.addAttribute("student", student);
        model.addAttribute("records", termGradeRepository.findByStudentOrderByAcademicYearDescSemesterDesc(student));
        return "student/term-grades";
    }
}
