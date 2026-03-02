package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "physical_tests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "academic_year", "semester"})
})
public class PhysicalTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear; // e.g., "2025-2026"

    @Column(nullable = false, length = 10)
    private String semester; // "上学期" / "下学期"

    @Column(name = "test_date")
    private LocalDate testDate;

    // 测试项目
    private Double height;          // 身高 cm
    private Double weight;          // 体重 kg
    private Double bmi;             // 体重指数，自动计算

    @Column(name = "lung_capacity")
    private Integer lungCapacity;   // 肺活量 mL

    @Column(name = "sprint_50m")
    private Double sprint50m;       // 50米跑 秒

    @Column(name = "sit_reach")
    private Double sitReach;        // 坐位体前屈 cm

    @Column(name = "standing_jump")
    private Double standingJump;    // 立定跳远 cm

    @Column(name = "pull_ups")
    private Integer pullUps;        // 引体向上 个（男）

    @Column(name = "sit_ups")
    private Integer sitUps;         // 仰卧起坐 个/min（女）

    @Column(name = "run_800m")
    private Double run800m;         // 800米跑 秒（女）

    @Column(name = "run_1000m")
    private Double run1000m;        // 1000米跑 秒（男）

    // 评分结果（自动计算存储）
    @Column(name = "total_score")
    private Double totalScore;

    @Column(length = 10)
    private String level;           // 优秀 / 良好 / 及格 / 不及格

    @Column(length = 200)
    private String remark;
}
