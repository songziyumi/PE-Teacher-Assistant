package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "course_class_capacities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "school_class_id"}))
public class CourseClassCapacity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity = 0;

    @Column(name = "current_count", nullable = false)
    private int currentCount = 0;
}
