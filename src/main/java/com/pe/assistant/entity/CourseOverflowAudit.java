package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "course_overflow_audits")
public class CourseOverflowAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "school_name", length = 100)
    private String schoolName;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_name", length = 100)
    private String eventName;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_name", length = 100)
    private String courseName;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_name", length = 50)
    private String studentName;

    @Column(name = "student_no", length = 50)
    private String studentNo;

    @Column(name = "operator_teacher_id", nullable = false)
    private Long operatorTeacherId;

    @Column(name = "operator_teacher_name", length = 50)
    private String operatorTeacherName;

    @Column(name = "operator_username", length = 50)
    private String operatorUsername;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
