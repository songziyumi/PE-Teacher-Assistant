package com.pe.assistant.controller;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class TeacherProfileController {

    private final TeacherRepository teacherRepository;
    private final CurrentUserService currentUserService;

    /** 静态资源上传根目录（相对项目启动目录） */
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/teachers/";

    // ===== 教师编辑自己的主页 =====

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
            teacher.setGender(gender);
            teacher.setSpecialty(specialty);
            teacher.setEmail(email);
            teacher.setBio(bio);
            if (birthDate != null && !birthDate.isBlank()) {
                teacher.setBirthDate(LocalDate.parse(birthDate));
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

    private String savePhoto(Long teacherId, MultipartFile photo) throws IOException {
        // 确保目录存在
        Path dir = Paths.get(UPLOAD_DIR);
        Files.createDirectories(dir);
        // 取扩展名（仅允许图片）
        String original = photo.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')) : ".jpg";
        if (!ext.matches("\\.(jpg|jpeg|png|webp|gif)")) ext = ".jpg";
        String filename = teacherId + ext;
        Path dest = dir.resolve(filename);
        photo.transferTo(dest.toFile());
        return "/uploads/teachers/" + filename;
    }

    // ===== 学生查看教师主页（只读） =====

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
