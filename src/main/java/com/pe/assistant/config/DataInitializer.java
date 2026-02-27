package com.pe.assistant.config;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-password:Admin@2024!}")
    private String adminDefaultPassword;

    @Override
    public void run(String... args) {
        if (!teacherRepository.existsByUsername("admin")) {
            Teacher admin = new Teacher();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminDefaultPassword));
            admin.setName("管理员");
            admin.setRole("ADMIN");
            teacherRepository.save(admin);
        }
    }
}
