package com.pe.assistant.repository;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.TeacherPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherPermissionRepository extends JpaRepository<TeacherPermission, Long> {
    Optional<TeacherPermission> findBySchool(School school);
}
