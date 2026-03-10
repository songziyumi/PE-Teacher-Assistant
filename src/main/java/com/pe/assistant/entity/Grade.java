package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "grades", uniqueConstraints = {
        @UniqueConstraint(name = "uk_grades_school_name", columnNames = {"school_id", "name"})
})
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id")
    private School school;
}
