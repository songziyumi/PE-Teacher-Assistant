package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_operation_logs", indexes = {
        @Index(name = "idx_op_log_teacher", columnList = "teacher_id"),
        @Index(name = "idx_op_log_operated_at", columnList = "operated_at")
})
public class TeacherOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "teacher_name", length = 50)
    private String teacherName;

    /**
     * 操作类型：ATTENDANCE_SAVE / PHYSICAL_TEST_SAVE / TERM_GRADE_SAVE /
     * STUDENT_UPDATE / STUDENT_BATCH_STATUS / STUDENT_BATCH_ELECTIVE /
     * BATCH_APPROVE / BATCH_REJECT
     */
    @Column(name = "action", length = 50, nullable = false)
    private String action;

    /** 人类可读描述，如"提交考勤：高一1班 2026-03-16，共 35 条" */
    @Column(name = "description", length = 300)
    private String description;

    /** 本次操作涉及的记录数（考勤条数、学生人数等），可为 null */
    @Column(name = "target_count")
    private Integer targetCount;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();
}
