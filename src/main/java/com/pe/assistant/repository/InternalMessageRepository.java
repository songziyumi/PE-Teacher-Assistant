package com.pe.assistant.repository;

import com.pe.assistant.entity.InternalMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InternalMessageRepository extends JpaRepository<InternalMessage, Long> {

    /** 某人的收件箱（按时间倒序） */
    List<InternalMessage> findByRecipientTypeAndRecipientIdOrderBySentAtDesc(
            String recipientType, Long recipientId);

    /** 某课程的待处理申请 */
    List<InternalMessage> findByTypeAndRelatedCourseIdAndStatus(
            String type, Long relatedCourseId, String status);

    /** 未读数 */
    long countByRecipientTypeAndRecipientIdAndIsRead(
            String recipientType, Long recipientId, boolean isRead);

    /** 是否已经对某课程发过申请（防重复） */
    boolean existsByTypeAndRelatedCourseIdAndSenderIdAndStatusNot(
            String type, Long relatedCourseId, Long senderId, String status);
}
