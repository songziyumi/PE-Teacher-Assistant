package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "term_grades",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_year", "semester"}))
public class TermGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(length = 10)
    private String semester;

    /** 出勤分 (0-100) */
    @Column(name = "attendance_score")
    private Double attendanceScore;

    /** 技能分 (0-100) */
    @Column(name = "skill_score")
    private Double skillScore;

    /** 理论分 (0-100) */
    @Column(name = "theory_score")
    private Double theoryScore;

    /** 综合分（自动计算：出勤40% + 技能40% + 理论20%，空项权重等比重分） */
    @Column(name = "total_score")
    private Double totalScore;

    /** 等级：优秀 / 良好 / 及格 / 不及格 */
    @Column(length = 10)
    private String level;

    @Column(length = 200)
    private String remark;
}
