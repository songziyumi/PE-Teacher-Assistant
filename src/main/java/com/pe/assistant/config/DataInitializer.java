package com.pe.assistant.config;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.OrganizationType;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.*;
import com.pe.assistant.service.OrganizationService;
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
    private final OrganizationService organizationService;

    @Value("${app.admin.default-password:Admin@2024!}")
    private String adminDefaultPassword;

    @Value("${app.super-admin.default-password:SuperAdmin@2024!}")
    private String superAdminDefaultPassword;

    @Override
    public void run(String... args) {
        jdbcTemplate.update("UPDATE students SET version = 0 WHERE version IS NULL");

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

        // 3. 初始化默认组织树并绑定默认学校
        Organization cityOrg = organizationService.ensureNode(
                "DEFAULT_CITY",
                "默认市级组织",
                OrganizationType.CITY,
                null,
                0);
        Organization districtOrg = organizationService.ensureNode(
                "DEFAULT_DISTRICT",
                "默认区县组织",
                OrganizationType.DISTRICT,
                cityOrg,
                10);
        Organization schoolOrg = organizationService.ensureNode(
                "SCHOOL_" + school.getCode(),
                school.getName(),
                OrganizationType.SCHOOL,
                districtOrg,
                0);
        if (school.getOrganization() == null) {
            school.setOrganization(schoolOrg);
            schoolRepository.save(school);
        }

        // 4. 创建默认学校管理员（兼容旧数据：若已存在则关联学校）
        if (!teacherRepository.existsByUsername("admin")) {
            Teacher admin = new Teacher();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminDefaultPassword));
            admin.setName("管理员");
            admin.setRole("ADMIN");
            admin.setSchool(school);
            admin.setManagedOrg(school.getOrganization());
            teacherRepository.save(admin);
        } else {
            teacherRepository.findByUsername("admin").ifPresent(admin -> {
                boolean changed = false;
                if (admin.getSchool() == null) {
                    admin.setSchool(school);
                    changed = true;
                }
                if (admin.getManagedOrg() == null && school.getOrganization() != null) {
                    admin.setManagedOrg(school.getOrganization());
                    changed = true;
                }
                if (changed) {
                    teacherRepository.save(admin);
                }
            });
        }

        // 5. 将所有 school=null 的数据迁移到默认学校
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
            .forEach(t -> {
                t.setSchool(school);
                if (t.getManagedOrg() == null && school.getOrganization() != null) {
                    t.setManagedOrg(school.getOrganization());
                }
                teacherRepository.save(t);
            });
    }
}