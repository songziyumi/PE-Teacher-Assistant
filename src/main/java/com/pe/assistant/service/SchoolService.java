package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.TeacherAccountType;
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
        if (schoolRepository.existsByName(name)) {
            throw new IllegalArgumentException("学校名称已存在");
        }
        if (schoolRepository.existsByCode(code)) {
            throw new IllegalArgumentException("学校编码已存在");
        }
        School school = new School();
        school.setName(name);
        school.setCode(code);
        school.setAddress(address);
        school.setContactPhone(contactPhone);
        return schoolRepository.save(school);
    }

    @Transactional
    public School update(Long id, String name, String code, String address, String contactPhone) {
        School school = schoolRepository.findById(id).orElseThrow();
        if (schoolRepository.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("学校名称已存在");
        }
        if (schoolRepository.existsByCodeAndIdNot(code, id)) {
            throw new IllegalArgumentException("学校编码已存在");
        }
        school.setName(name);
        school.setCode(code);
        school.setAddress(address);
        school.setContactPhone(contactPhone);
        return schoolRepository.save(school);
    }

    @Transactional
    public School updateTeacherStudentVisibility(Long id, boolean showSuspendedOnTeacherPage,
                                                 boolean showOutgoingBorrowOnTeacherPage) {
        School school = schoolRepository.findById(id).orElseThrow();
        school.setShowSuspendedOnTeacherPage(showSuspendedOnTeacherPage);
        school.setShowOutgoingBorrowOnTeacherPage(showOutgoingBorrowOnTeacherPage);
        return schoolRepository.save(school);
    }

    @Transactional
    public void toggleEnabled(Long id) {
        School school = schoolRepository.findById(id).orElseThrow();
        school.setEnabled(!school.getEnabled());
        schoolRepository.save(school);
    }

    @Transactional
    public void delete(Long id) {
        schoolRepository.deleteById(id);
    }

    @Transactional
    public Teacher createOrResetAdmin(Long schoolId, String username, String rawPassword) {
        if (!rawPassword.matches("^(?=.*[a-zA-Z])(?=.*\\d).{8,}$")) {
            throw new IllegalArgumentException("密码至少 8 位，且必须同时包含字母和数字");
        }
        School school = schoolRepository.findById(schoolId).orElseThrow();
        Teacher admin = teacherRepository.findByUsername(username).map(existing -> {
            if (existing.getSchool() == null || !existing.getSchool().getId().equals(schoolId)) {
                throw new IllegalArgumentException("用户名「" + username + "」已被其他账号占用，请更换用户名");
            }
            return existing;
        }).orElseGet(() -> {
            Teacher teacher = new Teacher();
            teacher.setUsername(username);
            return teacher;
        });
        admin.setName("管理员");
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setSchool(school);
        admin.setRole("ADMIN");
        admin.setAccountType(TeacherAccountType.SCHOOL_ADMIN);
        admin.setOrgAdminType(null);

        teacherRepository.findBySchoolAndRole(school, "ADMIN").forEach(old -> {
            if (!old.getUsername().equals(username)) {
                old.setRole("TEACHER");
                old.setAccountType(TeacherAccountType.TEACHER);
                old.setOrgAdminType(null);
                teacherRepository.save(old);
            }
        });
        return teacherRepository.save(admin);
    }

    public Teacher findAdminBySchool(School school) {
        return teacherRepository.findBySchoolAndRole(school, "ADMIN").stream().findFirst().orElse(null);
    }
}
