package com.pe.assistant.config;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TeacherRepository teacherRepository;
    private final SchoolRepository schoolRepository;
    private final GradeRepository gradeRepository;
    private final SchoolClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-password:Admin@2024!}")
    private String adminDefaultPassword;

    @Value("${app.super-admin.default-password:SuperAdmin@2024!}")
    private String superAdminDefaultPassword;

    @Override
    public void run(String... args) {
        // 1. 创建超级管理员
        if (!teacherRepository.existsByUsername("superadmin")) {
            Teacher superAdmin = new Teacher();
            superAdmin.setUsername("superadmin");
            superAdmin.setPassword(passwordEncoder.encode(superAdminDefaultPassword));
            superAdmin.setName("超级管理员");
            superAdmin.setRole("SUPER_ADMIN");
            teacherRepository.save(superAdmin);
        }

        // 2. 创建默认学校
        School school = schoolRepository.findByName("江苏省清江中学").orElseGet(() -> {
            School s = new School();
            s.setName("江苏省清江中学");
            s.setCode("JSQJZX");
            return schoolRepository.save(s);
        });

        // 3. 创建默认学校管理员（兼容旧数据：若已存在则关联学校）
        if (!teacherRepository.existsByUsername("admin")) {
            Teacher admin = new Teacher();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminDefaultPassword));
            admin.setName("管理员");
            admin.setRole("ADMIN");
            admin.setSchool(school);
            teacherRepository.save(admin);
        } else {
            teacherRepository.findByUsername("admin").ifPresent(admin -> {
                if (admin.getSchool() == null) {
                    admin.setSchool(school);
                    teacherRepository.save(admin);
                }
            });
        }

        // 4. 将所有 school=null 的数据迁移到默认学校
        gradeRepository.findAll().stream()
            .filter(g -> g.getSchool() == null)
            .forEach(g -> { g.setSchool(school); gradeRepository.save(g); });

        classRepository.findAll().stream()
            .filter(c -> c.getSchool() == null)
            .forEach(c -> { c.setSchool(school); classRepository.save(c); });

        studentRepository.findAll().stream()
            .filter(s -> s.getSchool() == null)
            .forEach(s -> { s.setSchool(school); studentRepository.save(s); });

        teacherRepository.findAll().stream()
            .filter(t -> t.getSchool() == null && !"SUPER_ADMIN".equals(t.getRole()))
            .forEach(t -> { t.setSchool(school); teacherRepository.save(t); });
    }
}
