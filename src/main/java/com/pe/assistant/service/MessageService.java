package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Set<String> SUPPORTED_TEACHER_MESSAGE_TYPES = Set.of(
            "ALL",
            "GENERAL",
            "COURSE_REQUEST");

    private final InternalMessageRepository messageRepo;
    private final CourseRequestAuditRepository courseRequestAuditRepo;
    private final CourseService courseService;
    private final CourseRepository courseRepository;
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
        msg.setContent(normalizeRequestContent(content));
        msg.setType("COURSE_REQUEST");
        msg.setRelatedCourseId(course.getId());
        msg.setRelatedCourseName(course.getName());
        msg.setStatus("PENDING");
        return messageRepo.save(msg);
    }

    // ===== 教师审批申请 =====

    @Transactional
    public void approveRequest(Long messageId, Teacher teacher) {
        approveRequest(messageId, teacher, null);
    }

    @Transactional
    public void approveRequest(Long messageId, Teacher teacher, String remark) {
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
        String beforeStatus = msg.getStatus();
        String normalizedRemark = normalizeRemark(remark);
        LocalDateTime handledAt = LocalDateTime.now();
        msg.setStatus("APPROVED");
        msg.setIsRead(true);
        msg.setHandledById(teacher.getId());
        msg.setHandledByName(teacher.getName());
        msg.setHandledAt(handledAt);
        msg.setHandleRemark(normalizedRemark);
        messageRepo.save(msg);
        saveRequestAudit(msg, teacher, "APPROVE", beforeStatus, "APPROVED", normalizedRemark, handledAt);
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
        rejectRequest(messageId, teacher, null);
    }

    @Transactional
    public void rejectRequest(Long messageId, Teacher teacher, String remark) {
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
        String beforeStatus = msg.getStatus();
        String normalizedRemark = normalizeRemark(remark);
        LocalDateTime handledAt = LocalDateTime.now();
        msg.setStatus("REJECTED");
        msg.setIsRead(true);
        msg.setHandledById(teacher.getId());
        msg.setHandledByName(teacher.getName());
        msg.setHandledAt(handledAt);
        msg.setHandleRemark(normalizedRemark);
        messageRepo.save(msg);
        saveRequestAudit(msg, teacher, "REJECT", beforeStatus, "REJECTED", normalizedRemark, handledAt);
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

    public List<InternalMessage> getTeacherInbox(Teacher teacher, String type) {
        String normalizedType = normalizeTeacherMessageType(type);
        if ("ALL".equals(normalizedType)) {
            return getTeacherInbox(teacher);
        }
        return messageRepo.findByRecipientTypeAndRecipientIdAndTypeOrderBySentAtDesc(
                "TEACHER", teacher.getId(), normalizedType);
    }

    public List<InternalMessage> getTeacherInbox(Teacher teacher, String type, boolean unreadOnly) {
        List<InternalMessage> messages = getTeacherInbox(teacher, type);
        if (!unreadOnly) {
            return messages;
        }
        return messages.stream()
                .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()))
                .toList();
    }

    public List<InternalMessage> getTeacherCourseRequests(Teacher teacher, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return messageRepo.findByRecipientTypeAndRecipientIdAndTypeOrderBySentAtDesc(
                    "TEACHER", teacher.getId(), "COURSE_REQUEST");
        }
        return messageRepo.findByRecipientTypeAndRecipientIdAndTypeAndStatusOrderBySentAtDesc(
                "TEACHER", teacher.getId(), "COURSE_REQUEST", status.trim().toUpperCase());
    }

    public long countTeacherCourseRequests(Teacher teacher, String status) {
        if (status == null || status.isBlank()) {
            return 0L;
        }
        return messageRepo.countByRecipientTypeAndRecipientIdAndTypeAndStatus(
                "TEACHER", teacher.getId(), "COURSE_REQUEST", status.trim().toUpperCase());
    }

    public InternalMessage getTeacherCourseRequestById(Teacher teacher, Long messageId) {
        InternalMessage msg = getTeacherMessageById(teacher, messageId);
        if (!"COURSE_REQUEST".equals(msg.getType())) {
            throw new RuntimeException("该消息不是选课申请");
        }
        return msg;
    }

    public List<CourseRequestAudit> getTeacherCourseRequestAudits(Teacher teacher, Long messageId) {
        InternalMessage msg = getTeacherCourseRequestById(teacher, messageId);
        return courseRequestAuditRepo.findByRequestMessageIdOrderByHandledAtDesc(msg.getId());
    }

    public InternalMessage getTeacherMessageById(Teacher teacher, Long messageId) {
        InternalMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        if (!"TEACHER".equals(msg.getRecipientType())) {
            throw new RuntimeException("该消息不属于教师收件箱");
        }
        if (!teacher.getId().equals(msg.getRecipientId())) {
            throw new RuntimeException("无权查看他人的消息");
        }
        return msg;
    }

    public List<InternalMessage> getStudentInbox(Student student) {
        return messageRepo.findByRecipientTypeAndRecipientIdOrderBySentAtDesc("STUDENT", student.getId());
    }

    public Map<Long, InternalMessage> getLatestStudentCourseRequests(Student student, SelectionEvent event) {
        if (student == null || student.getId() == null || event == null) {
            return Map.of();
        }
        Set<Long> eventCourseIds = courseService.findByEvent(event).stream()
                .map(Course::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (eventCourseIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, InternalMessage> latestRequestMap = new LinkedHashMap<>();
        List<InternalMessage> requests = messageRepo.findByTypeAndSenderIdAndSenderTypeOrderBySentAtDesc(
                "COURSE_REQUEST", student.getId(), "STUDENT");
        for (InternalMessage request : requests) {
            Long courseId = request.getRelatedCourseId();
            if (courseId == null || !eventCourseIds.contains(courseId) || latestRequestMap.containsKey(courseId)) {
                continue;
            }
            latestRequestMap.put(courseId, request);
        }
        return latestRequestMap;
    }

    public InternalMessage getStudentMessageById(Student student, Long messageId) {
        InternalMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        if (!"STUDENT".equals(msg.getRecipientType())) {
            throw new RuntimeException("该消息不属于学生收件箱");
        }
        if (!student.getId().equals(msg.getRecipientId())) {
            throw new RuntimeException("无权查看他人的消息");
        }
        return msg;
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

    @Transactional
    public void markTeacherMessageRead(Long messageId, Teacher teacher) {
        InternalMessage msg = getTeacherMessageById(teacher, messageId);
        if (!Boolean.TRUE.equals(msg.getIsRead())) {
            msg.setIsRead(true);
            messageRepo.save(msg);
        }
    }

    @Transactional
    public void markStudentMessageRead(Long messageId, Student student) {
        InternalMessage msg = getStudentMessageById(student, messageId);
        if (!Boolean.TRUE.equals(msg.getIsRead())) {
            msg.setIsRead(true);
            messageRepo.save(msg);
        }
    }

    // ===== 全校审批记录（管理员导出用） =====

    public List<InternalMessage> getSchoolCourseRequests(School school) {
        return messageRepo.findBySchoolAndTypeOrderBySentAtDesc(school, "COURSE_REQUEST");
    }

    // ===== 审批记录 xlsx 导出 =====

    public byte[] exportCourseRequestsXlsx(List<InternalMessage> messages) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("审批记录");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] cols = {"编号", "申请时间", "申请人", "申请课程", "申请内容", "状态", "审批人", "审批时间", "审批备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            for (InternalMessage m : messages) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(m.getId() != null ? m.getId() : 0);
                row.createCell(1).setCellValue(m.getSentAt() != null ? m.getSentAt().format(fmt) : "");
                row.createCell(2).setCellValue(m.getSenderName() != null ? m.getSenderName() : "");
                row.createCell(3).setCellValue(m.getRelatedCourseName() != null ? m.getRelatedCourseName() : "");
                row.createCell(4).setCellValue(m.getContent() != null ? m.getContent() : "");
                row.createCell(5).setCellValue(translateStatus(m.getStatus()));
                row.createCell(6).setCellValue(m.getHandledByName() != null ? m.getHandledByName() : "");
                row.createCell(7).setCellValue(m.getHandledAt() != null ? m.getHandledAt().format(fmt) : "");
                row.createCell(8).setCellValue(m.getHandleRemark() != null ? m.getHandleRemark() : "");
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String translateStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PENDING"  -> "待处理";
            case "APPROVED" -> "已同意";
            case "REJECTED" -> "已拒绝";
            default         -> status;
        };
    }

    // ===== 教师列表（供学生发消息选择） =====

    public List<TeacherMessageRecipient> findTeachersBySchool(School school) {
        List<Course> assignedCourses = courseRepository.findBySchoolAndTeacherIsNotNullOrderByNameAsc(school);
        java.util.Map<Long, java.util.LinkedHashSet<String>> courseNamesByTeacherId = new java.util.LinkedHashMap<>();
        for (Course course : assignedCourses) {
            if (course.getTeacher() == null || course.getTeacher().getId() == null) {
                continue;
            }
            courseNamesByTeacherId
                    .computeIfAbsent(course.getTeacher().getId(), key -> new java.util.LinkedHashSet<>())
                    .add(course.getName());
        }

        return teacherRepository.findBySchool(school).stream()
                .filter(this::isStudentMessageRecipient)
                .map(teacher -> new TeacherMessageRecipient(
                        teacher.getId(),
                        teacher.getName(),
                        buildTeacherRecipientLabel(teacher, courseNamesByTeacherId.get(teacher.getId()))))
                .toList();
    }

    public Teacher findTeacherMessageRecipient(School school, Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("教师不存在"));
        if (school == null || teacher.getSchool() == null || !school.getId().equals(teacher.getSchool().getId())) {
            throw new RuntimeException("教师不存在");
        }
        if (!isStudentMessageRecipient(teacher)) {
            throw new RuntimeException("该账号不能作为站内信收件教师");
        }
        return teacher;
    }

    private String normalizeTeacherMessageType(String type) {
        if (type == null || type.isBlank()) {
            return "ALL";
        }
        String normalizedType = type.trim().toUpperCase();
        if (!SUPPORTED_TEACHER_MESSAGE_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("消息类型仅支持 ALL、GENERAL、COURSE_REQUEST");
        }
        return normalizedType;
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private String normalizeRequestContent(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > 200) {
            throw new RuntimeException("申请理由不能超过200字");
        }
        return trimmed;
    }

    private void saveRequestAudit(
            InternalMessage msg,
            Teacher teacher,
            String action,
            String beforeStatus,
            String afterStatus,
            String remark,
            LocalDateTime handledAt) {
        CourseRequestAudit audit = new CourseRequestAudit();
        audit.setSchool(msg.getSchool());
        audit.setRequestMessageId(msg.getId());
        audit.setAction(action);
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(afterStatus);
        audit.setOperatorTeacherId(teacher.getId());
        audit.setOperatorTeacherName(teacher.getName());
        audit.setSenderId(msg.getSenderId());
        audit.setSenderName(msg.getSenderName());
        audit.setRelatedCourseId(msg.getRelatedCourseId());
        audit.setRelatedCourseName(msg.getRelatedCourseName());
        audit.setRemark(remark);
        audit.setHandledAt(handledAt != null ? handledAt : LocalDateTime.now());
        courseRequestAuditRepo.save(audit);
    }

    private boolean isStudentMessageRecipient(Teacher teacher) {
        return teacher != null && teacher.resolveAccountType() == TeacherAccountType.TEACHER;
    }

    private String buildTeacherRecipientLabel(Teacher teacher, java.util.Set<String> courseNames) {
        if (courseNames == null || courseNames.isEmpty()) {
            return teacher.getName();
        }
        return teacher.getName() + "（" + String.join("、", courseNames) + "）";
    }

    public static final class TeacherMessageRecipient {
        private final Long id;
        private final String name;
        private final String displayName;

        public TeacherMessageRecipient(Long id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
