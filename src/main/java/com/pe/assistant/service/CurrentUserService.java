package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.Organization;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final TeacherRepository teacherRepository;
    private final StudentAccountService studentAccountService;
    private final OrganizationScopeService organizationScopeService;

    public Teacher getCurrentTeacher() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return teacherRepository.findByUsername(username).orElseThrow();
    }

    public School getCurrentSchool() {
        return getCurrentTeacher().getSchool();
    }

    public Organization getCurrentManagedOrg() {
        return organizationScopeService.resolveManagedOrg(getCurrentTeacher());
    }

    public Student getCurrentStudent() {
        StudentAccount account = getCurrentStudentAccount();
        if (account == null || account.getStudent() == null) {
            throw new IllegalStateException("当前学生账号不存在");
        }
        return account.getStudent();
    }

    public StudentAccount getCurrentStudentAccount() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        return studentAccountService.resolvePrincipal(principal).orElseThrow();
    }
}
