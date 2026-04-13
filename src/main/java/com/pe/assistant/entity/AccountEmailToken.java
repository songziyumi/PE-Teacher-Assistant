package com.pe.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_email_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_email_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_account_email_token_principal", columnList = "principal_type, principal_id, purpose"),
                @Index(name = "idx_account_email_token_expires", columnList = "expires_at")
        })
@Data
public class AccountEmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountEmailTokenPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    private AccountPrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    private Long principalId;

    @Column(name = "target_email", nullable = false, length = 100)
    private String targetEmail;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
