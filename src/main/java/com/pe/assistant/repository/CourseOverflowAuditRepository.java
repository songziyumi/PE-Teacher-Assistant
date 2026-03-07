package com.pe.assistant.repository;

import com.pe.assistant.entity.CourseOverflowAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOverflowAuditRepository extends JpaRepository<CourseOverflowAudit, Long> {
}
