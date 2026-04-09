package com.pe.assistant.repository;

import com.pe.assistant.entity.CourseOverflowAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseOverflowAuditRepository extends JpaRepository<CourseOverflowAudit, Long> {

    long countByEventId(Long eventId);

    List<CourseOverflowAudit> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<CourseOverflowAudit> findTop100ByOperatorTeacherIdOrderByCreatedAtDesc(Long operatorTeacherId);

    List<CourseOverflowAudit> findTop200BySchoolIdOrderByCreatedAtDesc(Long schoolId);

    List<CourseOverflowAudit> findTop100ByOperatorTeacherIdAndSchoolIdOrderByCreatedAtDesc(Long operatorTeacherId,
                                                                                           Long schoolId);
}
