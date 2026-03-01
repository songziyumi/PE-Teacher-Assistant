package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "exam_records")
@Data
public class ExamRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "exam_name", length = 100, nullable = false)
    private String examName; // 考试名称

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate; // 考试日期

    @Column(name = "exam_type", length = 50)
    private String examType; // 考试类型：期中/期末/模拟等

    // 考试项目成绩
    @Column(name = "project_1_name", length = 50)
    private String project1Name;

    @Column(name = "project_1_score", precision = 5, scale = 2)
    private BigDecimal project1Score;

    @Column(name = "project_2_name", length = 50)
    private String project2Name;

    @Column(name = "project_2_score", precision = 5, scale = 2)
    private BigDecimal project2Score;

    @Column(name = "project_3_name", length = 50)
    private String project3Name;

    @Column(name = "project_3_score", precision = 5, scale = 2)
    private BigDecimal project3Score;

    @Column(name = "project_4_name", length = 50)
    private String project4Name;

    @Column(name = "project_4_score", precision = 5, scale = 2)
    private BigDecimal project4Score;

    @Column(name = "project_5_name", length = 50)
    private String project5Name;

    @Column(name = "project_5_score", precision = 5, scale = 2)
    private BigDecimal project5Score;

    // 总分和排名
    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "class_rank")
    private Integer classRank; // 班级排名

    @Column(name = "grade_rank")
    private Integer gradeRank; // 年级排名

    @Column(name = "is_passed")
    private Boolean isPassed; // 是否及格

    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment; // 教师评语

    @Column(name = "created_at")
    private LocalDate createdAt;

    @Column(name = "updated_at")
    private LocalDate updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();
    }
}