package com.pe.assistant.repository;

import com.pe.assistant.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    Optional<Grade> findByName(String name);
    boolean existsByName(String name);
}
