package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "course_request_audits")
public class CourseRequestAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "request_message_id", nullable = false)
    private Long requestMessageId;

    @Column(name = "action", nullable = false, length = 20)
    private String action; // APPROVE / REJECT

    @Column(name = "before_status", length = 20)
    private String beforeStatus;

    @Column(name = "after_status", length = 20)
    private String afterStatus;

    @Column(name = "operator_teacher_id", nullable = false)
    private Long operatorTeacherId;

    @Column(name = "operator_teacher_name", length = 50)
    private String operatorTeacherName;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "sender_name", length = 50)
    private String senderName;

    @Column(name = "related_course_id")
    private Long relatedCourseId;

    @Column(name = "related_course_name", length = 100)
    private String relatedCourseName;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt = LocalDateTime.now();
}
