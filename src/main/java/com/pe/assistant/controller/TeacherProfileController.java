package com.pe.assistant.controller;

import com.pe.assistant.entity.CourseRequestAudit;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.CourseRequestAuditRepository;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class TeacherProfileController {

    @Value("${app.upload-dir:${user.home}/.pe-teacher-assistant/uploads}")
    private String uploadDir;

    private final TeacherRepository teacherRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final ClassService classService;
    private final AttendanceRepository attendanceRepository;
    private final MessageService messageService;
    private final CourseRequestAuditRepository courseRequestAuditRepository;

    @GetMapping("/teacher/profile")
    public String profilePage(Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        model.addAttribute("teacher", teacher);
        return "teacher/profile";
    }

    @GetMapping("/teacher/profile/stats")
    @ResponseBody
    public Map<String, Object> profileStats() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        int classCount = classService.findByTeacher(teacher).size();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        long monthlyAttendanceCount = attendanceRepository
                .countDistinctDatesByTeacherAndDateRange(teacher.getId(), monthStart, today);
        long pendingRequestCount = messageService.countTeacherCourseRequests(teacher, "PENDING");
        long processedRequestCount = courseRequestAuditRepository.countByOperatorTeacherId(teacher.getId());
        List<CourseRequestAudit> recentAudits = courseRequestAuditRepository
                .findTop10ByOperatorTeacherIdOrderByHandledAtDesc(teacher.getId());
        List<Map<String, Object>> activities = new ArrayList<>();
        for (CourseRequestAudit a : recentAudits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action", a.getAction());
            m.put("time", a.getHandledAt() != null ? a.getHandledAt().toString().substring(0, 16).replace("T", " ") : "");
            m.put("studentName", a.getSenderName());
            m.put("courseName", a.getRelatedCourseName());
            m.put("remark", a.getRemark());
            activities.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classCount", classCount);
        result.put("monthlyAttendanceCount", monthlyAttendanceCount);
        result.put("pendingRequestCount", pendingRequestCount);
        result.put("processedRequestCount", processedRequestCount);
        result.put("recentActivities", activities);
        return result;
    }

    @PostMapping("/teacher/profile/save")
    public String saveProfile(@RequestParam(required = false) String gender,
                              @RequestParam(required = false) String birthDate,
                              @RequestParam(required = false) String specialty,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String bio,
                              @RequestParam(value = "photo", required = false) MultipartFile photo,
                              RedirectAttributes ra) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            teacher.setGender(emptyToNull(gender));
            teacher.setSpecialty(emptyToNull(specialty));
            teacher.setEmail(emptyToNull(email));
            teacher.setBio(emptyToNull(bio));
            if (birthDate != null && !birthDate.isBlank()) {
                teacher.setBirthDate(LocalDate.parse(birthDate));
            } else {
                teacher.setBirthDate(null);
            }
            if (photo != null && !photo.isEmpty()) {
                String photoUrl = savePhoto(teacher.getId(), photo);
                teacher.setPhotoUrl(photoUrl);
            }
            teacherRepository.save(teacher);
            ra.addFlashAttribute("success", "\u4e2a\u4eba\u4e3b\u9875\u5df2\u66f4\u65b0");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "\u4fdd\u5b58\u5931\u8d25\uff1a" + e.getMessage());
        }
        return "redirect:/teacher/profile";
    }

    @PostMapping("/teacher/profile/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            if (!passwordEncoder.matches(oldPassword, teacher.getPassword())) {
                ra.addFlashAttribute("error", "\u539f\u5bc6\u7801\u9519\u8bef");
                return "redirect:/teacher/profile";
            }
            if (!newPassword.equals(confirmPassword)) {
                ra.addFlashAttribute("error", "\u4e24\u6b21\u8f93\u5165\u7684\u65b0\u5bc6\u7801\u4e0d\u4e00\u81f4");
                return "redirect:/teacher/profile";
            }
            validatePassword(newPassword);
            teacher.setPassword(passwordEncoder.encode(newPassword));
            teacherRepository.save(teacher);
            ra.addFlashAttribute("success", "\u5bc6\u7801\u4fee\u6539\u6210\u529f");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "\u5bc6\u7801\u4fee\u6539\u5931\u8d25\uff1a" + e.getMessage());
        }
        return "redirect:/teacher/profile";
    }

    private String savePhoto(Long teacherId, MultipartFile photo) throws IOException {
        Path dir = Paths.get(uploadDir, "teachers").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String original = photo.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : ".jpg";
        if (!ext.matches("\\.(jpg|jpeg|png|webp|gif)")) {
            ext = ".jpg";
        }
        String filename = teacherId + ext;
        Path dest = dir.resolve(filename);
        photo.transferTo(dest.toFile());
        return "/uploads/teachers/" + filename;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("\u5bc6\u7801\u957f\u5ea6\u4e0d\u80fd\u5c11\u4e8e8\u4f4d");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("\u5bc6\u7801\u5fc5\u987b\u540c\u65f6\u5305\u542b\u5b57\u6bcd\u548c\u6570\u5b57");
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @GetMapping("/student/teachers/{id}")
    public String viewTeacherProfile(@PathVariable Long id, Model model) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("\u6559\u5e08\u4e0d\u5b58\u5728"));
        model.addAttribute("teacher", teacher);
        Student student = currentUserService.getCurrentStudent();
        model.addAttribute("student", student);
        return "student/teacher-profile";
    }
}
