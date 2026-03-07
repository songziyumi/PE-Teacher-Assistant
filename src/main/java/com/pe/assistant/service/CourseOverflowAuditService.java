package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseOverflowAudit;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.CourseOverflowAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseOverflowAuditService {

    private final CourseOverflowAuditRepository auditRepository;

    @Transactional
    public void recordForcedOverflow(School school, Course course, Student student, Teacher operator, String reason) {
        CourseOverflowAudit audit = new CourseOverflowAudit();
        audit.setSchoolId(school.getId());
        audit.setSchoolName(school.getName());
        audit.setEventId(course.getEvent().getId());
        audit.setEventName(course.getEvent().getName());
        audit.setCourseId(course.getId());
        audit.setCourseName(course.getName());
        audit.setStudentId(student.getId());
        audit.setStudentName(student.getName());
        audit.setStudentNo(student.getStudentNo());
        audit.setOperatorTeacherId(operator.getId());
        audit.setOperatorTeacherName(operator.getName());
        audit.setOperatorUsername(operator.getUsername());
        audit.setReason(reason);
        auditRepository.save(audit);
    }
}
