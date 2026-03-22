package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "competition_event")
public class CompetitionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    @Column(name = "gender_limit", length = 20)
    private String genderLimit;

    @Column(name = "group_rule", length = 100)
    private String groupRule;

    @Column(name = "team_or_individual", nullable = false, length = 20)
    private String teamOrIndividual;

    @Column(name = "max_entries_per_school")
    private Integer maxEntriesPerSchool;

    @Column(name = "max_entries_per_district")
    private Integer maxEntriesPerDistrict;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

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