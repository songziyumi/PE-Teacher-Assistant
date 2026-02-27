package com.pe.assistant.repository;

import com.pe.assistant.entity.Grade;
import com.pe.assistant.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findBySchool(School school);
    Optional<Grade> findByNameAndSchool(String name, School school);
    boolean existsByNameAndSchool(String name, School school);
}
