package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_account_student", columnNames = "student_id"),
                @UniqueConstraint(name = "uk_student_account_login_id", columnNames = "login_id"),
                @UniqueConstraint(name = "uk_student_account_login_alias", columnNames = "login_alias")
        },
        indexes = {
                @Index(name = "idx_student_account_email", columnList = "email")
        })
@Data
public class StudentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "login_id", nullable = false, length = 32)
    private String loginId;

    @Column(name = "login_alias", length = 32)
    private String loginAlias;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Boolean locked = false;

    @Column(nullable = false)
    private Boolean activated = false;

    @Column(name = "password_reset_required", nullable = false)
    private Boolean passwordResetRequired = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "issued_password", length = 50)
    private String issuedPassword;

    @Column(name = "last_password_reset_at")
    private LocalDateTime lastPasswordResetAt;

    @Column(name = "login_alias_bound_at")
    private LocalDateTime loginAliasBoundAt;

    @Column(length = 100)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "email_bound_at")
    private LocalDateTime emailBoundAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "email_notify_enabled", nullable = false)
    private Boolean emailNotifyEnabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
