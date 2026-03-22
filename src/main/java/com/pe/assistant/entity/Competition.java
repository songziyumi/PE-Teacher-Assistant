package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "competition")
public class Competition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompetitionLevel level;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "host_org_id", nullable = false)
    private Organization hostOrg;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "undertake_org_id")
    private Organization undertakeOrg;

    @Column(name = "school_year", length = 20)
    private String schoolYear;

    @Column(length = 20)
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompetitionStatus status = CompetitionStatus.DRAFT;

    @Column(name = "registration_start_at")
    private LocalDateTime registrationStartAt;

    @Column(name = "registration_end_at")
    private LocalDateTime registrationEndAt;

    @Column(name = "competition_start_at")
    private LocalDateTime competitionStartAt;

    @Column(name = "competition_end_at")
    private LocalDateTime competitionEndAt;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private Teacher createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}