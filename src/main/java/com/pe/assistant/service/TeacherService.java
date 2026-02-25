package com.pe.assistant.service;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Teacher> findAll() {
        return teacherRepository.findAll();
    }

    public Teacher findByUsername(String username) {
        return teacherRepository.findByUsername(username).orElse(null);
    }

    @Transactional
    public Teacher create(String username, String name, String rawPassword, String role) {
        return create(username, name, rawPassword, role, null);
    }

    @Transactional
    public Teacher create(String username, String name, String rawPassword, String role, String phone) {
        if (teacherRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        Teacher t = new Teacher();
        t.setUsername(username);
        t.setName(name);
        t.setPassword(passwordEncoder.encode(rawPassword));
        t.setRole(role == null ? "TEACHER" : role);
        t.setPhone(phone);
        return teacherRepository.save(t);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        Teacher t = teacherRepository.findById(id).orElseThrow();
        t.setPassword(passwordEncoder.encode(newPassword));
        teacherRepository.save(t);
    }

    @Transactional
    public void delete(Long id) {
        teacherRepository.deleteById(id);
    }
}
