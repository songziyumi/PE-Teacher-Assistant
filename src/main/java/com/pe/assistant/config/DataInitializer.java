package com.pe.assistant.config;

import com.pe.assistant.entity.OrganizationAdminType;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.TeacherAccountType;
import com.pe.assistant.repository.GradeRepository;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.SchoolRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.admin.default-password:Admin@2024!}")
    private String adminDefaultPassword;

    @Value("${app.super-admin.default-password:SuperAdmin@2024!}")
    private String superAdminDefaultPassword;

    @Override
    public void run(String... args) {
        jdbcTemplate.update("UPDATE students SET version = 0 WHERE version IS NULL");

        if (!teacherRepository.existsByUsername("superadmin")) {
            Teacher superAdmin = new Teacher();
            superAdmin.setUsername("superadmin");
            superAdmin.setPassword(passwordEncoder.encode(superAdminDefaultPassword));
            superAdmin.setName("超级管理员");
            superAdmin.setRole("SUPER_ADMIN");
            superAdmin.setAccountType(TeacherAccountType.SUPER_ADMIN);
            teacherRepository.save(superAdmin);
        }

        School school = schoolRepository.findByName("江苏省清江中学").orElseGet(() -> {
            School item = new School();
            item.setName("江苏省清江中学");
            item.setCode("JSQJZX");
            return schoolRepository.save(item);
        });

        if (!teacherRepository.existsByUsername("admin")) {
            Teacher admin = new Teacher();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminDefaultPassword));
            admin.setName("管理员");
            admin.setRole("ADMIN");
            admin.setAccountType(TeacherAccountType.SCHOOL_ADMIN);
            admin.setSchool(school);
            teacherRepository.save(admin);
        } else {
            teacherRepository.findByUsername("admin").ifPresent(admin -> {
                if (admin.getSchool() == null) {
                    admin.setSchool(school);
                }
                admin.setAccountType(TeacherAccountType.SCHOOL_ADMIN);
                admin.setOrgAdminType(null);
                teacherRepository.save(admin);
            });
        }

        gradeRepository.findAll().stream()
                .filter(grade -> grade.getSchool() == null)
                .forEach(grade -> {
                    grade.setSchool(school);
                    gradeRepository.save(grade);
                });

        classRepository.findAll().stream()
                .filter(clazz -> clazz.getSchool() == null)
                .forEach(clazz -> {
                    clazz.setSchool(school);
                    classRepository.save(clazz);
                });

        studentRepository.findAll().stream()
                .filter(student -> student.getSchool() == null)
                .forEach(student -> {
                    student.setSchool(school);
                    studentRepository.save(student);
                });

        teacherRepository.findAll().stream()
                .filter(teacher -> teacher.getSchool() == null && !"SUPER_ADMIN".equals(teacher.getRole()))
                .forEach(teacher -> {
                    teacher.setSchool(school);
                    teacherRepository.save(teacher);
                });

        teacherRepository.findAll().forEach(this::backfillTeacherAccountType);
    }

    private void backfillTeacherAccountType(Teacher teacher) {
        TeacherAccountType resolvedAccountType = teacher.resolveAccountType();
        OrganizationAdminType resolvedOrgAdminType = teacher.resolveOrgAdminType();
        if (teacher.getAccountType() != resolvedAccountType || teacher.getOrgAdminType() != resolvedOrgAdminType) {
            teacher.setAccountType(resolvedAccountType);
            teacher.setOrgAdminType(resolvedOrgAdminType);
            teacherRepository.save(teacher);
        }
    }
}
