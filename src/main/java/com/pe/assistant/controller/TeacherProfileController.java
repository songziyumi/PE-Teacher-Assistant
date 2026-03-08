package com.pe.assistant.controller;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class TeacherProfileController {

    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/teachers/";

    private final TeacherRepository teacherRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/teacher/profile")
    public String profilePage(Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        model.addAttribute("teacher", teacher);
        return "teacher/profile";
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
            ra.addFlashAttribute("success", "个人主页已更新");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
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
                ra.addFlashAttribute("error", "原密码错误");
                return "redirect:/teacher/profile";
            }
            if (!newPassword.equals(confirmPassword)) {
                ra.addFlashAttribute("error", "两次输入的新密码不一致");
                return "redirect:/teacher/profile";
            }
            validatePassword(newPassword);
            teacher.setPassword(passwordEncoder.encode(newPassword));
            teacherRepository.save(teacher);
            ra.addFlashAttribute("success", "密码修改成功");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "密码修改失败：" + e.getMessage());
        }
        return "redirect:/teacher/profile";
    }

    private String savePhoto(Long teacherId, MultipartFile photo) throws IOException {
        Path dir = Paths.get(UPLOAD_DIR);
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
            throw new IllegalArgumentException("密码长度不能少于8位");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    @GetMapping("/student/teachers/{id}")
    public String viewTeacherProfile(@PathVariable Long id, Model model) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("教师不存在"));
        model.addAttribute("teacher", teacher);
        Student student = currentUserService.getCurrentStudent();
        model.addAttribute("student", student);
        return "student/teacher-profile";
    }
}
