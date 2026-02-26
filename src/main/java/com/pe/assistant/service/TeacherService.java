package com.pe.assistant.service;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final SchoolClassRepository classRepository;

    public List<Teacher> findAll() {
        return teacherRepository.findAll();
    }

    public Page<Teacher> findByFilters(String name, String username, String phone, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String n = (name == null || name.isBlank()) ? null : name.trim();
        String u = (username == null || username.isBlank()) ? null : username.trim();
        String p = (phone == null || phone.isBlank()) ? null : phone.trim();
        return teacherRepository.findByFilters(n, u, p, pageable);
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
        Teacher teacher = teacherRepository.findById(id).orElseThrow();
        // 先解除班级关联，避免外键约束报错
        classRepository.findAll().stream()
            .filter(c -> teacher.equals(c.getTeacher()))
            .forEach(c -> { c.setTeacher(null); classRepository.save(c); });
        teacherRepository.delete(teacher);
    }

    @Transactional
    public void assignClasses(Long teacherId, List<Long> classIds) {
        Teacher teacher = teacherRepository.findById(teacherId).orElseThrow();
        // 先解除该教师所有已有班级关联
        classRepository.findAll().stream()
            .filter(c -> teacher.equals(c.getTeacher()))
            .forEach(c -> { c.setTeacher(null); classRepository.save(c); });
        // 重新分配
        if (classIds != null) {
            classIds.forEach(cid -> {
                classRepository.findById(cid).ifPresent(c -> {
                    c.setTeacher(teacher);
                    classRepository.save(c);
                });
            });
        }
    }

    @Transactional
    public void deleteAll() {
        // 只删除非管理员教师，先解除班级关联
        List<Teacher> teachers = teacherRepository.findAll().stream()
            .filter(t -> !"ADMIN".equals(t.getRole())).toList();
        teachers.forEach(t -> {
            classRepository.findAll().stream()
                .filter(c -> t.equals(c.getTeacher()))
                .forEach(c -> { c.setTeacher(null); classRepository.save(c); });
            teacherRepository.delete(t);
        });
    }
}
