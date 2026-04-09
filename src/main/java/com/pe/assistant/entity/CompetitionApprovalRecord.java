package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "competition_approval_record")
public class CompetitionApprovalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registration_id", nullable = false)
    private CompetitionRegistration registration;

    @Column(name = "approval_level", nullable = false, length = 20)
    private String approvalLevel;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approver_id", nullable = false)
    private Teacher approver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approver_org_id", nullable = false)
    private Organization approverOrg;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}