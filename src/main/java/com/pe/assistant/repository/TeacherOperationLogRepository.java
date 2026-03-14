package com.pe.assistant.repository;

import com.pe.assistant.entity.TeacherOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherOperationLogRepository extends JpaRepository<TeacherOperationLog, Long> {

    List<TeacherOperationLog> findTop100ByTeacherIdOrderByOperatedAtDesc(Long teacherId);

    List<TeacherOperationLog> findTop200BySchool_IdOrderByOperatedAtDesc(Long schoolId);

    List<TeacherOperationLog> findTop100ByTeacherIdAndSchool_IdOrderByOperatedAtDesc(Long teacherId, Long schoolId);
}
