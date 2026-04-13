package com.pe.assistant.security;

import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.StudentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoginPrincipalResolver {

    private final TeacherRepository teacherRepository;
    private final StudentAccountService studentAccountService;

    public Optional<Teacher> findTeacher(String loginInput) {
        String normalized = normalize(loginInput);
        if (normalized == null) {
            return Optional.empty();
        }
        return teacherRepository.findByUsername(normalized);
    }

    public Optional<StudentAccount> findStudentAccount(String loginInput) {
        return studentAccountService.findByLoginCredential(loginInput);
    }

    public String resolveAttemptKey(String loginInput) {
        String normalized = normalize(loginInput);
        if (normalized == null) {
            return "";
        }
        Optional<Teacher> teacher = findTeacher(normalized);
        if (teacher.isPresent()) {
            return teacher.get().getUsername();
        }
        Optional<StudentAccount> account = findStudentAccount(normalized);
        if (account.isPresent() && account.get().getId() != null) {
            return "student-account:" + account.get().getId();
        }
        return normalized;
    }

    private String normalize(String loginInput) {
        if (loginInput == null) {
            return null;
        }
        String normalized = loginInput.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
