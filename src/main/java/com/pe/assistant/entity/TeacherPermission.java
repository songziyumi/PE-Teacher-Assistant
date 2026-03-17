package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 教师功能权限配置（学校粒度，每校一条记录）。
 * 所有字段默认 true（允许），管理员可按需关闭。
 */
@Data
@Entity
@Table(name = "teacher_permissions")
public class TeacherPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id", nullable = false, unique = true)
    private School school;

    // ===== 学生信息编辑字段开关 =====
    @Column(nullable = false)
    private boolean editStudentName = true;

    @Column(nullable = false)
    private boolean editStudentGender = true;

    @Column(nullable = false)
    private boolean editStudentNo = true;

    @Column(nullable = false)
    private boolean editStudentStatus = true;

    @Column(nullable = false)
    private boolean editStudentClass = true;

    @Column(nullable = false)
    private boolean editStudentElectiveClass = true;

    // ===== 功能模块开关 =====
    @Column(nullable = false)
    private boolean attendanceEdit = true;

    @Column(nullable = false)
    private boolean physicalTestEdit = true;

    @Column(nullable = false)
    private boolean termGradeEdit = true;

    @Column(nullable = false)
    private boolean batchOperation = true;
}
