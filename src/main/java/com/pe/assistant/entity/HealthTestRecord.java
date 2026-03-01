package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "health_test_records")
@Data
public class HealthTestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    // 身体形态指标
    @Column(name = "height", precision = 5, scale = 2)
    private BigDecimal height; // 身高(cm)

    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight; // 体重(kg)

    @Column(name = "bmi", precision = 4, scale = 2)
    private BigDecimal bmi; // 身体质量指数

    // 身体机能指标
    @Column(name = "lung_capacity")
    private Integer lungCapacity; // 肺活量(ml)

    // 身体素质指标
    @Column(name = "run_50m", precision = 4, scale = 2)
    private BigDecimal run50m; // 50米跑(秒)

    @Column(name = "sit_and_reach", precision = 4, scale = 2)
    private BigDecimal sitAndReach; // 坐位体前屈(cm)

    @Column(name = "standing_long_jump", precision = 4, scale = 2)
    private BigDecimal standingLongJump; // 立定跳远(m)

    @Column(name = "pull_ups")
    private Integer pullUps; // 引体向上(次) - 男生

    @Column(name = "sit_ups")
    private Integer sitUps; // 仰卧起坐(次) - 女生

    @Column(name = "run_1000m")
    private Integer run1000m; // 1000米跑(秒) - 男生

    @Column(name = "run_800m")
    private Integer run800m; // 800米跑(秒) - 女生

    // 综合评价
    @Column(name = "total_score")
    private Integer totalScore; // 总分

    @Column(name = "grade_level", length = 20)
    private String gradeLevel; // 等级：优秀/良好/及格/不及格

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