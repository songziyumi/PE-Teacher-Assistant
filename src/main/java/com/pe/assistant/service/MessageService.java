package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final InternalMessageRepository messageRepo;
    private final CourseService courseService;
    private final TeacherRepository teacherRepository;

    // ===== 发送消息 =====

    @Transactional
    public InternalMessage sendMessage(String senderType, Long senderId, String senderName,
                                       String recipientType, Long recipientId, String recipientName,
                                       String subject, String content, School school) {
        InternalMessage msg = new InternalMessage();
        msg.setSchool(school);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setRecipientType(recipientType);
        msg.setRecipientId(recipientId);
        msg.setRecipientName(recipientName);
        msg.setSubject(subject);
        msg.setContent(content);
        msg.setType("GENERAL");
        return messageRepo.save(msg);
    }

    /**
     * 学生发送第三轮选课申请。
     * 条件校验由控制器完成（活动 CLOSED + 学生无确认选课 + 未重复申请）。
     */
    @Transactional
    public InternalMessage sendCourseRequest(Student student, Course course, String content) {
        // 防重：同一学生对同一课程不能重复发送待处理的申请
        boolean exists = messageRepo.existsByTypeAndRelatedCourseIdAndSenderIdAndStatusNot(
                "COURSE_REQUEST", course.getId(), student.getId(), "REJECTED");
        if (exists) {
            throw new RuntimeException("您已经对该课程发送过申请，请等待教师处理");
        }
        // 找到课程对应的教师
        if (course.getTeacher() == null) {
            throw new RuntimeException("该课程暂未指定授课教师，无法发送申请");
        }
        Teacher teacher = course.getTeacher();
        InternalMessage msg = new InternalMessage();
        msg.setSchool(student.getSchool());
        msg.setSenderType("STUDENT");
        msg.setSenderId(student.getId());
        msg.setSenderName(student.getName());
        msg.setRecipientType("TEACHER");
        msg.setRecipientId(teacher.getId());
        msg.setRecipientName(teacher.getName());
        msg.setSubject("第三轮选课申请：" + course.getName());
        msg.setContent(content);
        msg.setType("COURSE_REQUEST");
        msg.setRelatedCourseId(course.getId());
        msg.setRelatedCourseName(course.getName());
        msg.setStatus("PENDING");
        return messageRepo.save(msg);
    }

    // ===== 教师审批申请 =====

    @Transactional
    public void approveRequest(Long messageId, Teacher teacher) {
        InternalMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        if (!"COURSE_REQUEST".equals(msg.getType())) {
            throw new RuntimeException("该消息不是选课申请");
        }
        if (!"PENDING".equals(msg.getStatus())) {
            throw new RuntimeException("申请已处理，无法重复操作");
        }
        if (!teacher.getId().equals(msg.getRecipientId())) {
            throw new RuntimeException("无权处理他人的申请");
        }
        // 找到申请人和对应活动（通过课程关联）
        Course course = courseService.findById(msg.getRelatedCourseId());
        // 复用 adminEnroll 逻辑（内部处理容量检查和记录创建）
        courseService.adminEnroll(course.getId(), msg.getSenderId(), course.getEvent().getId());
        msg.setStatus("APPROVED");
        msg.setIsRead(true);
        messageRepo.save(msg);
        // 向学生发送通知
        InternalMessage notify = new InternalMessage();
        notify.setSchool(msg.getSchool());
        notify.setSenderType("TEACHER");
        notify.setSenderId(teacher.getId());
        notify.setSenderName(teacher.getName());
        notify.setRecipientType("STUDENT");
        notify.setRecipientId(msg.getSenderId());
        notify.setRecipientName(msg.getSenderName());
        notify.setSubject("您的选课申请已通过：" + msg.getRelatedCourseName());
        notify.setContent("您申请加入《" + msg.getRelatedCourseName() + "》的申请已获批准，请查看我的选课。");
        notify.setType("GENERAL");
        messageRepo.save(notify);
    }

    @Transactional
    public void rejectRequest(Long messageId, Teacher teacher) {
        InternalMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        if (!"COURSE_REQUEST".equals(msg.getType())) {
            throw new RuntimeException("该消息不是选课申请");
        }
        if (!"PENDING".equals(msg.getStatus())) {
            throw new RuntimeException("申请已处理，无法重复操作");
        }
        if (!teacher.getId().equals(msg.getRecipientId())) {
            throw new RuntimeException("无权处理他人的申请");
        }
        msg.setStatus("REJECTED");
        msg.setIsRead(true);
        messageRepo.save(msg);
        // 向学生发送通知
        InternalMessage notify = new InternalMessage();
        notify.setSchool(msg.getSchool());
        notify.setSenderType("TEACHER");
        notify.setSenderId(teacher.getId());
        notify.setSenderName(teacher.getName());
        notify.setRecipientType("STUDENT");
        notify.setRecipientId(msg.getSenderId());
        notify.setRecipientName(msg.getSenderName());
        notify.setSubject("您的选课申请未通过：" + msg.getRelatedCourseName());
        notify.setContent("您申请加入《" + msg.getRelatedCourseName() + "》的申请未获批准。如有疑问请联系教师。");
        notify.setType("GENERAL");
        messageRepo.save(notify);
    }

    // ===== 收件箱 =====

    public List<InternalMessage> getTeacherInbox(Teacher teacher) {
        return messageRepo.findByRecipientTypeAndRecipientIdOrderBySentAtDesc("TEACHER", teacher.getId());
    }

    public List<InternalMessage> getStudentInbox(Student student) {
        return messageRepo.findByRecipientTypeAndRecipientIdOrderBySentAtDesc("STUDENT", student.getId());
    }

    public long getUnreadCount(String recipientType, Long recipientId) {
        return messageRepo.countByRecipientTypeAndRecipientIdAndIsRead(recipientType, recipientId, false);
    }

    @Transactional
    public void markRead(Long messageId) {
        messageRepo.findById(messageId).ifPresent(msg -> {
            msg.setIsRead(true);
            messageRepo.save(msg);
        });
    }

    // ===== 教师列表（供学生发消息选择） =====

    public List<Teacher> findTeachersBySchool(School school) {
        return teacherRepository.findBySchool(school).stream()
                .filter(t -> !"ADMIN".equals(t.getRole()))
                .collect(java.util.stream.Collectors.toList());
    }
}
