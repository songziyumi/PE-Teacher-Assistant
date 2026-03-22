package com.pe.assistant.service;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.School;
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

    public List<Teacher> findAll(School school) {
        return teacherRepository.findBySchool(school);
    }

    public Page<Teacher> findByFilters(School school, String name, String username, String phone, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String n = (name == null || name.isBlank()) ? null : name.trim();
        String u = (username == null || username.isBlank()) ? null : username.trim();
        String p = (phone == null || phone.isBlank()) ? null : phone.trim();
        return teacherRepository.findByFilters(school, n, u, p, pageable);
    }

    public Teacher findByUsername(String username) {
        return teacherRepository.findByUsername(username).orElse(null);
    }

    public Teacher findOrgAdminByManagedOrg(Long managedOrgId) {
        if (managedOrgId == null) {
            return null;
        }
        return teacherRepository.findByManagedOrgId(managedOrgId).stream()
                .filter(t -> "ORG_ADMIN".equals(t.getRole()))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public Teacher create(String username, String name, String rawPassword, String role, String phone, School school) {
        if (teacherRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        Teacher t = new Teacher();
        t.setUsername(username);
        t.setName(name);
        t.setPassword(passwordEncoder.encode(rawPassword));
        t.setRole(role == null ? "TEACHER" : role);
        t.setPhone(phone);
        t.setSchool(school);
        return teacherRepository.save(t);
    }

    @Transactional
    public Teacher createOrResetOrgAdmin(String username,
                                         String rawPassword,
                                         String name,
                                         String phone,
                                         Organization managedOrg) {
        if (managedOrg == null || managedOrg.getId() == null) {
            throw new IllegalArgumentException("组织节点不能为空");
        }
        if (rawPassword == null || !rawPassword.matches("^(?=.*[a-zA-Z])(?=.*\\d).{8,}$")) {
            throw new IllegalArgumentException("密码至少8位，且需同时包含字母和数字");
        }

        Teacher existing = teacherRepository.findByUsername(username).orElse(null);
        if (existing != null && (existing.getManagedOrg() == null || !managedOrg.getId().equals(existing.getManagedOrg().getId()) || !"ORG_ADMIN".equals(existing.getRole()))) {
            throw new IllegalArgumentException("用户名已存在，请使用新的组织管理员用户名，避免覆盖现有学校管理员或教师账号");
        }

        Teacher teacher = existing != null ? existing : new Teacher();

        Teacher oldAdmin = findOrgAdminByManagedOrg(managedOrg.getId());
        if (oldAdmin != null) {
            boolean sameTeacher = teacher.getId() != null && oldAdmin.getId().equals(teacher.getId());
            boolean sameUsername = oldAdmin.getUsername() != null && oldAdmin.getUsername().equals(username);
            if (!sameTeacher && !sameUsername) {
                oldAdmin.setRole("TEACHER");
                teacherRepository.save(oldAdmin);
            }
        }

        teacher.setUsername(username);
        teacher.setPassword(passwordEncoder.encode(rawPassword));
        teacher.setName(name == null || name.isBlank() ? managedOrg.getName() + "管理员" : name.trim());
        teacher.setPhone(phone);
        teacher.setRole("ORG_ADMIN");
        teacher.setManagedOrg(managedOrg);
        teacher.setSchool(null);
        return teacherRepository.save(teacher);
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
        classRepository.findByTeacher(teacher)
            .forEach(c -> { c.setTeacher(null); classRepository.save(c); });
        teacherRepository.delete(teacher);
    }

    @Transactional
    public void assignClasses(Long teacherId, List<Long> classIds) {
        Teacher teacher = teacherRepository.findById(teacherId).orElseThrow();
        classRepository.findByTeacher(teacher)
            .forEach(c -> { c.setTeacher(null); classRepository.save(c); });
        if (classIds != null) {
            classIds.forEach(cid -> classRepository.findById(cid).ifPresent(c -> {
                c.setTeacher(teacher);
                classRepository.save(c);
            }));
        }
    }

    @Transactional
    public void deleteAll(School school) {
        List<Teacher> teachers = teacherRepository.findBySchool(school).stream()
            .filter(t -> !"ADMIN".equals(t.getRole())).toList();
        teachers.forEach(t -> {
            classRepository.findByTeacher(t)
                .forEach(c -> { c.setTeacher(null); classRepository.save(c); });
            teacherRepository.delete(t);
        });
    }
}
