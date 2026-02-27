package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SchoolRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    public List<School> findAll() {
        return schoolRepository.findAll();
    }

    public School findById(Long id) {
        return schoolRepository.findById(id).orElseThrow();
    }

    @Transactional
    public School create(String name, String code, String address, String contactPhone) {
        if (schoolRepository.existsByName(name)) throw new IllegalArgumentException("学校名称已存在");
        if (schoolRepository.existsByCode(code)) throw new IllegalArgumentException("学校编码已存在");
        School s = new School();
        s.setName(name);
        s.setCode(code);
        s.setAddress(address);
        s.setContactPhone(contactPhone);
        return schoolRepository.save(s);
    }

    @Transactional
    public School update(Long id, String name, String code, String address, String contactPhone) {
        School s = schoolRepository.findById(id).orElseThrow();
        if (schoolRepository.existsByNameAndIdNot(name, id)) throw new IllegalArgumentException("学校名称已存在");
        if (schoolRepository.existsByCodeAndIdNot(code, id)) throw new IllegalArgumentException("学校编码已存在");
        s.setName(name);
        s.setCode(code);
        s.setAddress(address);
        s.setContactPhone(contactPhone);
        return schoolRepository.save(s);
    }

    @Transactional
    public void toggleEnabled(Long id) {
        School s = schoolRepository.findById(id).orElseThrow();
        s.setEnabled(!s.getEnabled());
        schoolRepository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        schoolRepository.deleteById(id);
    }

    /** 为学校创建或重置管理员账号 */
    @Transactional
    public Teacher createOrResetAdmin(Long schoolId, String username, String rawPassword) {
        if (!rawPassword.matches("^(?=.*[a-zA-Z])(?=.*\\d).{8,}$")) {
            throw new IllegalArgumentException("密码至少8位，且须同时包含字母和数字");
        }
        School school = schoolRepository.findById(schoolId).orElseThrow();
        Teacher admin = teacherRepository.findByUsername(username).map(existing -> {
            // 用户名已存在：只允许重置本学校的管理员，其他账号拒绝
            if (existing.getSchool() == null || !existing.getSchool().getId().equals(schoolId)) {
                throw new IllegalArgumentException("用户名「" + username + "」已被其他账号占用，请更换用户名");
            }
            return existing;
        }).orElseGet(() -> {
            Teacher t = new Teacher();
            t.setUsername(username);
            return t;
        });
        admin.setName("管理员");
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setSchool(school);
        admin.setRole("ADMIN");
        return teacherRepository.save(admin);
    }

    public Teacher findAdminBySchool(School school) {
        return teacherRepository.findBySchoolAndRole(school, "ADMIN").orElse(null);
    }
}
