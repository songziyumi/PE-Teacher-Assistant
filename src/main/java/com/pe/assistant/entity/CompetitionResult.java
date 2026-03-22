package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "competition_result")
public class CompetitionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "competition_event_id", nullable = false)
    private CompetitionEvent competitionEvent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "district_org_id")
    private Organization districtOrg;

    @Column(name = "result_value", nullable = false, length = 100)
    private String resultValue;

    @Column(name = "rank_no")
    private Integer rankNo;

    @Column(name = "score_points", precision = 10, scale = 2)
    private BigDecimal scorePoints;

    @Column(name = "record_status", nullable = false, length = 30)
    private String recordStatus = "ENTERED";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entered_by", nullable = false)
    private Teacher enteredBy;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "verified_by")
    private Teacher verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (enteredAt == null) {
            enteredAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}