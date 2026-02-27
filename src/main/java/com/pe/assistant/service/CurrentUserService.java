package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final TeacherRepository teacherRepository;

    public Teacher getCurrentTeacher() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return teacherRepository.findByUsername(username).orElseThrow();
    }

    public School getCurrentSchool() {
        return getCurrentTeacher().getSchool();
    }
}
