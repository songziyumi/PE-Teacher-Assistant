package com.pe.assistant.config;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!teacherRepository.existsByUsername("admin")) {
            Teacher admin = new Teacher();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setName("管理员");
            admin.setRole("ADMIN");
            teacherRepository.save(admin);
        }
        if (!teacherRepository.existsByUsername("teacher")) {
            Teacher teacher = new Teacher();
            teacher.setUsername("teacher");
            teacher.setPassword(passwordEncoder.encode("teacher123"));
            teacher.setName("示例教师");
            teacher.setRole("TEACHER");
            teacherRepository.save(teacher);
        }
    }
}
