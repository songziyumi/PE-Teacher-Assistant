package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "competition_registration")
public class CompetitionRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "applicant_org_id", nullable = false)
    private Organization applicantOrg;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "applicant_school_id", nullable = false)
    private School applicantSchool;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompetitionRegistrationStatus status = CompetitionRegistrationStatus.DRAFT;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "submitted_by")
    private Teacher submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "current_approval_level", length = 20)
    private String currentApprovalLevel;

    @Column(length = 500)
    private String remark;

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