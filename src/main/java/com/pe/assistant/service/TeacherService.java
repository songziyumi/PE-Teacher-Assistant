package com.pe.assistant.service;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.OrganizationAdminType;
import com.pe.assistant.entity.OrganizationType;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.TeacherAccountType;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.SchoolRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private final SchoolRepository schoolRepository;
    private final StudentAccountService studentAccountService;

    public List<Teacher> findAll(School school) {
        return teacherRepository.findBySchool(school);
    }

    public List<Teacher> findCourseAssignableTeachers(School school) {
        return teacherRepository.findBySchool(school).stream()
                .filter(Teacher::isCourseAssignableTeacher)
                .toList();
    }

    public Page<Teacher> findByFilters(School school, String name, String username, String phone, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String n = (name == null || name.isBlank()) ? null : name.trim();
        String u = (username == null || username.isBlank()) ? null : username.trim();
        String p = (phone == null || phone.isBlank()) ? null : phone.trim();
        return teacherRepository.findByFilters(school, n, u, p, pageable);
    }

    public Page<Teacher> findManageableByFilters(Teacher operator, School school, String name, String username, String phone,
                                                 int page, int size) {
        if (operator == null || operator.resolveAccountType() != TeacherAccountType.SCHOOL_ADMIN) {
            return findByFilters(school, name, username, phone, page, size);
        }

        String n = (name == null || name.isBlank()) ? null : name.trim().toLowerCase();
        String u = (username == null || username.isBlank()) ? null : username.trim().toLowerCase();
        String p = (phone == null || phone.isBlank()) ? null : phone.trim();
        Pageable pageable = PageRequest.of(page, size);
        List<Teacher> filteredTeachers = teacherRepository.findBySchool(school).stream()
                .filter(this::isVisibleToSchoolAdmin)
                .filter(t -> n == null || (t.getName() != null && t.getName().toLowerCase().contains(n)))
                .filter(t -> u == null || (t.getUsername() != null && t.getUsername().toLowerCase().contains(u)))
                .filter(t -> p == null || (t.getPhone() != null && t.getPhone().contains(p)))
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredTeachers.size());
        List<Teacher> pageContent = start >= filteredTeachers.size()
                ? List.of()
                : filteredTeachers.subList(start, end);
        return new PageImpl<>(pageContent, pageable, filteredTeachers.size());
    }

    public Teacher findByUsername(String username) {
        return teacherRepository.findByUsername(username).orElse(null);
    }

    public Teacher findOrgAdminByManagedOrg(Long managedOrgId) {
        if (managedOrgId == null) {
            return null;
        }
        return teacherRepository.findByManagedOrgId(managedOrgId).stream()
                .findFirst()
                .orElse(null);
    }

    public boolean canManageTeacher(Teacher operator, Teacher target) {
        if (operator == null || target == null) {
            return false;
        }
        if (operator.getSchool() == null || target.getSchool() == null) {
            return false;
        }
        if (!operator.getSchool().getId().equals(target.getSchool().getId())) {
            return false;
        }
        if (operator.resolveAccountType() != TeacherAccountType.SCHOOL_ADMIN) {
            return true;
        }
        return isVisibleToSchoolAdmin(target);
    }

    public void validateManageableTeacher(Teacher operator, Long targetTeacherId) {
        Teacher target = teacherRepository.findById(targetTeacherId).orElseThrow();
        if (!canManageTeacher(operator, target)) {
            throw new IllegalArgumentException("无权操作该账号");
        }
    }

    public void validateCreatableAccount(Teacher operator, TeacherAccountType accountType,
                                         OrganizationAdminType orgAdminType) {
        TeacherAccountType resolvedAccountType = accountType == null ? TeacherAccountType.TEACHER : accountType;
        if (resolvedAccountType == TeacherAccountType.SUPER_ADMIN) {
            throw new IllegalArgumentException("后台页面不支持创建超级管理员账号");
        }
        if (operator != null && operator.resolveAccountType() == TeacherAccountType.SCHOOL_ADMIN
                && resolvedAccountType == TeacherAccountType.ORG_ADMIN
                && orgAdminType != null
                && orgAdminType != OrganizationAdminType.SCHOOL) {
            throw new IllegalArgumentException("学校管理员只能创建学校组织管理员");
        }
    }

    private boolean isVisibleToSchoolAdmin(Teacher teacher) {
        return teacher.resolveAccountType() != TeacherAccountType.ORG_ADMIN
                || teacher.resolveOrgAdminType() == null
                || teacher.resolveOrgAdminType() == OrganizationAdminType.SCHOOL;
    }

    @Transactional
    public Teacher create(String username, String name, String rawPassword, String role, String phone, School school) {
        return create(username, name, rawPassword, role, phone, school, TeacherAccountType.TEACHER, null);
    }

    @Transactional
    public Teacher create(String username, String name, String rawPassword, String role, String phone, School school,
                          TeacherAccountType accountType, OrganizationAdminType orgAdminType) {
        studentAccountService.assertTeacherUsernameAvailable(username);
        if (teacherRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        TeacherAccountType resolvedAccountType = accountType == null ? TeacherAccountType.TEACHER : accountType;
        OrganizationAdminType resolvedOrgAdminType = resolvedAccountType == TeacherAccountType.ORG_ADMIN
                ? orgAdminType
                : null;
        if (resolvedAccountType == TeacherAccountType.ORG_ADMIN && resolvedOrgAdminType == null) {
            throw new IllegalArgumentException("组织管理员必须选择管理员类型");
        }

        Teacher teacher = new Teacher();
        teacher.setUsername(username);
        teacher.setName(name);
        teacher.setPassword(passwordEncoder.encode(rawPassword));
        teacher.setRole(role == null ? "TEACHER" : role);
        teacher.setAccountType(resolvedAccountType);
        teacher.setOrgAdminType(resolvedOrgAdminType);
        teacher.setPhone(phone);
        teacher.setSchool(school);
        return teacherRepository.save(teacher);
    }

    @Transactional
    public Teacher createOrResetOrgAdmin(String username, String password, String name, String phone, Organization org) {
        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException("组织节点不存在");
        }
        OrganizationAdminType orgAdminType = mapOrgAdminType(org.getType());
        School school = org.getType() == OrganizationType.SCHOOL
                ? schoolRepository.findByOrganizationId(org.getId()).orElse(null)
                : null;
        Teacher existing = teacherRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            existing.setName(name == null || name.isBlank() ? org.getName() + "组织管理员" : name);
            existing.setPhone(phone);
            existing.setPassword(passwordEncoder.encode(password));
            existing.setRole("ORG_ADMIN");
            existing.setAccountType(TeacherAccountType.ORG_ADMIN);
            existing.setOrgAdminType(orgAdminType);
            existing.setManagedOrg(org);
            existing.setSchool(school);
            return teacherRepository.save(existing);
        }
        studentAccountService.assertTeacherUsernameAvailable(username);
        Teacher created = create(
                username,
                name == null || name.isBlank() ? org.getName() + "组织管理员" : name,
                password,
                "ORG_ADMIN",
                phone,
                school,
                TeacherAccountType.ORG_ADMIN,
                orgAdminType
        );
        created.setManagedOrg(org);
        created.setSchool(school);
        return teacherRepository.save(created);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow();
        teacher.setPassword(passwordEncoder.encode(newPassword));
        teacherRepository.save(teacher);
    }

    @Transactional
    public void delete(Long id) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow();
        classRepository.findByTeacher(teacher)
                .forEach(c -> {
                    c.setTeacher(null);
                    classRepository.save(c);
                });
        teacherRepository.delete(teacher);
    }

    @Transactional
    public void assignClasses(Long teacherId, List<Long> classIds) {
        Teacher teacher = teacherRepository.findById(teacherId).orElseThrow();
        if (!teacher.isCourseAssignableTeacher()) {
            throw new IllegalArgumentException("只有教师账号可以分配班级");
        }
        classRepository.findByTeacher(teacher)
                .forEach(c -> {
                    c.setTeacher(null);
                    classRepository.save(c);
                });
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
                .filter(t -> !"ADMIN".equals(t.getRole()))
                .toList();
        teachers.forEach(t -> {
            classRepository.findByTeacher(t)
                    .forEach(c -> {
                        c.setTeacher(null);
                        classRepository.save(c);
                    });
            teacherRepository.delete(t);
        });
    }

    @Transactional
    public void resetPassword(Teacher operator, Long id, String newPassword) {
        validateManageableTeacher(operator, id);
        resetPassword(id, newPassword);
    }

    @Transactional
    public void delete(Teacher operator, Long id) {
        validateManageableTeacher(operator, id);
        delete(id);
    }

    @Transactional
    public void assignClasses(Teacher operator, Long teacherId, List<Long> classIds) {
        validateManageableTeacher(operator, teacherId);
        assignClasses(teacherId, classIds);
    }

    @Transactional
    public void deleteAll(Teacher operator, School school) {
        List<Teacher> teachers = teacherRepository.findBySchool(school).stream()
                .filter(t -> !"ADMIN".equals(t.getRole()))
                .filter(t -> canManageTeacher(operator, t))
                .toList();
        teachers.forEach(t -> {
            classRepository.findByTeacher(t)
                    .forEach(c -> {
                        c.setTeacher(null);
                        classRepository.save(c);
                    });
            teacherRepository.delete(t);
        });
    }

    private OrganizationAdminType mapOrgAdminType(OrganizationType organizationType) {
        if (organizationType == null) {
            throw new IllegalArgumentException("组织类型不能为空");
        }
        return switch (organizationType) {
            case CITY -> OrganizationAdminType.CITY;
            case DISTRICT -> OrganizationAdminType.DISTRICT;
            case SCHOOL -> OrganizationAdminType.SCHOOL;
        };
    }
}
