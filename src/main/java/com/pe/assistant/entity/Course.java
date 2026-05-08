package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private SelectionEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "gender_limit", nullable = false, length = 20)
    private String genderLimit = "ALL";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /**
     * GLOBAL：所有参与班级共享一个总名额池
     * PER_CLASS：每个班级单独设置名额，见 CourseClassCapacity
     */
    @Column(name = "capacity_mode", nullable = false, length = 20)
    private String capacityMode = "GLOBAL";

    /** GLOBAL 模式下的总名额；PER_CLASS 模式下为各班名额之和（冗余，方便展示） */
    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity = 0;

    /** 当前已确认选课人数（冗余，防并发时频繁 COUNT） */
    @Column(name = "current_count", nullable = false)
    private int currentCount = 0;

    /** DRAFT / ACTIVE / CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
}
