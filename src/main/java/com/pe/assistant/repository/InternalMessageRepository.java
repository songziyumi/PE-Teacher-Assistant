package com.pe.assistant.repository;

import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InternalMessageRepository extends JpaRepository<InternalMessage, Long> {

    /** 某人的收件箱（按时间倒序） */
    List<InternalMessage> findByRecipientTypeAndRecipientIdOrderBySentAtDesc(
            String recipientType, Long recipientId);

    /** 某人的指定类型收件箱（按时间倒序） */
    List<InternalMessage> findByRecipientTypeAndRecipientIdAndTypeOrderBySentAtDesc(
            String recipientType, Long recipientId, String type);

    /** 某人的指定类型 + 指定状态收件箱（按时间倒序） */
    List<InternalMessage> findByRecipientTypeAndRecipientIdAndTypeAndStatusOrderBySentAtDesc(
            String recipientType, Long recipientId, String type, String status);

    /** 某课程的待处理申请 */
    List<InternalMessage> findByTypeAndRelatedCourseIdAndStatus(
            String type, Long relatedCourseId, String status);

    /** 未读数 */
    long countByRecipientTypeAndRecipientIdAndIsRead(
            String recipientType, Long recipientId, boolean isRead);

    /** 某人的指定类型 + 指定状态数量 */
    long countByRecipientTypeAndRecipientIdAndTypeAndStatus(
            String recipientType, Long recipientId, String type, String status);

    /** 是否已经对某课程发过申请（防重复） */
    boolean existsByTypeAndRelatedCourseIdAndSenderIdAndStatusNot(
            String type, Long relatedCourseId, Long senderId, String status);

    /** 某学生发出的课程申请（按时间倒序） */
    List<InternalMessage> findByTypeAndSenderIdAndSenderTypeOrderBySentAtDesc(
            String type, Long senderId, String senderType);

    /** 全校某类型消息（用于管理员导出） */
    List<InternalMessage> findBySchoolAndTypeOrderBySentAtDesc(School school, String type);
}
