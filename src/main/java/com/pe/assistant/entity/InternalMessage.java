package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 站内消息：支持普通消息（GENERAL）和第三轮课程申请（COURSE_REQUEST）。
 * senderType / recipientType：TEACHER 或 STUDENT
 * status（课程申请用）：PENDING / APPROVED / REJECTED；普通消息为 null
 */
@Data
@Entity
@Table(name = "internal_messages")
public class InternalMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;   // TEACHER / STUDENT / SYSTEM

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "sender_name", length = 50)
    private String senderName;   // 冗余姓名，避免关联查询

    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType; // TEACHER / STUDENT

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "recipient_name", length = 50)
    private String recipientName;

    @Column(length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** GENERAL（普通消息） / COURSE_REQUEST（第三轮选课申请） */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /** 仅 COURSE_REQUEST 时使用，关联的课程 ID */
    @Column(name = "related_course_id")
    private Long relatedCourseId;

    @Column(name = "related_course_name", length = 100)
    private String relatedCourseName;

    /** COURSE_REQUEST：PENDING / APPROVED / REJECTED；普通消息为 null */
    @Column(length = 20)
    private String status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
}
