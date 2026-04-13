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
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "mail_outbox",
        indexes = {
                @Index(name = "idx_mail_outbox_status", columnList = "status, next_retry_at"),
                @Index(name = "idx_mail_outbox_principal", columnList = "principal_type, principal_id")
        })
@Data
public class MailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_type", nullable = false, length = 30)
    private String bizType;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    private AccountPrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    private Long principalId;

    @Column(name = "recipient_email", nullable = false, length = 100)
    private String recipientEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_data", columnDefinition = "TEXT")
    private String templateData;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MailOutboxStatus status = MailOutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "provider_request_id", length = 120)
    private String providerRequestId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = MailOutboxStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
