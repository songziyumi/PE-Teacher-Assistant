package com.pe.assistant.repository;

import com.pe.assistant.entity.CourseRequestAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRequestAuditRepository extends JpaRepository<CourseRequestAudit, Long> {

    List<CourseRequestAudit> findByRequestMessageIdOrderByHandledAtDesc(Long requestMessageId);
}
