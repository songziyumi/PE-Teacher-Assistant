package com.pe.assistant.repository;

import com.pe.assistant.entity.CourseRequestAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRequestAuditRepository extends JpaRepository<CourseRequestAudit, Long> {

    List<CourseRequestAudit> findByRequestMessageIdOrderByHandledAtDesc(Long requestMessageId);

    long countByOperatorTeacherId(Long operatorTeacherId);

    List<CourseRequestAudit> findTop10ByOperatorTeacherIdOrderByHandledAtDesc(Long operatorTeacherId);

    List<CourseRequestAudit> findTop100ByOperatorTeacherIdOrderByHandledAtDesc(Long operatorTeacherId);

    List<CourseRequestAudit> findTop200BySchool_IdOrderByHandledAtDesc(Long schoolId);

    List<CourseRequestAudit> findTop100ByOperatorTeacherIdAndSchool_IdOrderByHandledAtDesc(Long operatorTeacherId, Long schoolId);
}
