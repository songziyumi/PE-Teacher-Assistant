package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "course_selections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "student_id", "preference"}))
public class CourseSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private SelectionEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** 1 = 1志愿，2 = 2志愿，0 = 第二轮（无志愿序号） */
    @Column(nullable = false)
    private int preference = 0;

    /** 1 = 第一轮，2 = 第二轮 */
    @Column(nullable = false)
    private int round = 1;

    /**
     * PENDING       第一轮已提交，等待抽签
     * CONFIRMED     已确认选中
     * LOTTERY_FAIL  第一轮抽签未中
     * CANCELLED     主动退课 / 冲突处理被移除
     */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "selected_at")
    private LocalDateTime selectedAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
}
