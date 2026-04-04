package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseRequestAudit;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.TeacherAccountType;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseRequestAuditRepository;
import com.pe.assistant.repository.InternalMessageRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceRegressionTest {

    @Mock
    private InternalMessageRepository messageRepo;
    @Mock
    private CourseRequestAuditRepository courseRequestAuditRepo;
    @Mock
    private CourseService courseService;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private TeacherRepository teacherRepository;

    @InjectMocks
    private MessageService messageService;

    @Test
    void approveShouldFailWhenRequestAlreadyHandled() {
        InternalMessage msg = buildCourseRequestMessage("APPROVED", 10L);
        when(messageRepo.findById(1L)).thenReturn(Optional.of(msg));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> messageService.approveRequest(1L, buildTeacher(10L, "T"), null));
        assertNotNull(ex.getMessage());
        verify(messageRepo, never()).save(any());
        verify(courseService, never()).adminEnroll(any(), any(), any());
    }

    @Test
    void approveShouldFailForNonOwnerTeacher() {
        InternalMessage msg = buildCourseRequestMessage("PENDING", 10L);
        when(messageRepo.findById(1L)).thenReturn(Optional.of(msg));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> messageService.approveRequest(1L, buildTeacher(20L, "Other"), null));
        assertNotNull(ex.getMessage());
        verify(messageRepo, never()).save(any());
        verify(courseService, never()).adminEnroll(any(), any(), any());
    }

    @Test
    void approveShouldMarkMessageReadAndWriteAudit() {
        InternalMessage msg = buildCourseRequestMessage("PENDING", 10L);
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        Course course = new Course();
        course.setId(3L);
        SelectionEvent event = new SelectionEvent();
        event.setId(4L);
        course.setEvent(event);

        when(messageRepo.findById(1L)).thenReturn(Optional.of(msg));
        when(courseService.findById(3L)).thenReturn(course);
        when(messageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        messageService.approveRequest(1L, teacher, "ok");

        assertEquals("APPROVED", msg.getStatus());
        assertEquals(Boolean.TRUE, msg.getIsRead());
        assertEquals(teacher.getId(), msg.getHandledById());
        assertEquals(teacher.getName(), msg.getHandledByName());
        assertEquals("ok", msg.getHandleRemark());
        assertNotNull(msg.getHandledAt());
        verify(courseService).adminEnroll(3L, 99L, 4L);
        verify(messageRepo, times(2)).save(any());

        ArgumentCaptor<CourseRequestAudit> captor = ArgumentCaptor.forClass(CourseRequestAudit.class);
        verify(courseRequestAuditRepo).save(captor.capture());
        CourseRequestAudit audit = captor.getValue();
        assertEquals("APPROVE", audit.getAction());
        assertEquals("PENDING", audit.getBeforeStatus());
        assertEquals("APPROVED", audit.getAfterStatus());
        assertEquals("ok", audit.getRemark());
        assertEquals(teacher.getId(), audit.getOperatorTeacherId());
    }

    @Test
    void rejectShouldNormalizeBlankAndLongRemark() {
        InternalMessage msgBlank = buildCourseRequestMessage("PENDING", 10L);
        InternalMessage msgLong = buildCourseRequestMessage("PENDING", 10L);
        msgLong.setId(2L);
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(messageRepo.findById(1L)).thenReturn(Optional.of(msgBlank));
        when(messageRepo.findById(2L)).thenReturn(Optional.of(msgLong));
        when(messageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        messageService.rejectRequest(1L, teacher, "   ");
        assertNull(msgBlank.getHandleRemark());

        String longRemark = "x".repeat(600);
        messageService.rejectRequest(2L, teacher, longRemark);
        assertNotNull(msgLong.getHandleRemark());
        assertEquals(500, msgLong.getHandleRemark().length());

        ArgumentCaptor<CourseRequestAudit> captor = ArgumentCaptor.forClass(CourseRequestAudit.class);
        verify(courseRequestAuditRepo, times(2)).save(captor.capture());
        List<CourseRequestAudit> audits = captor.getAllValues();
        assertNull(audits.get(0).getRemark());
        assertEquals(500, audits.get(1).getRemark().length());
    }

    @Test
    void courseRequestAuditShouldKeepRepositoryDescOrder() {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        InternalMessage msg = buildCourseRequestMessage("PENDING", 10L);
        msg.setRecipientType("TEACHER");

        CourseRequestAudit newer = new CourseRequestAudit();
        newer.setHandledAt(LocalDateTime.now());
        CourseRequestAudit older = new CourseRequestAudit();
        older.setHandledAt(LocalDateTime.now().minusDays(1));

        when(messageRepo.findById(1L)).thenReturn(Optional.of(msg));
        when(courseRequestAuditRepo.findByRequestMessageIdOrderByHandledAtDesc(1L))
                .thenReturn(List.of(newer, older));

        List<CourseRequestAudit> result = messageService.getTeacherCourseRequestAudits(teacher, 1L);
        assertEquals(2, result.size());
        assertSame(newer, result.get(0));
        assertSame(older, result.get(1));
    }

    @Test
    void getTeacherInboxShouldRejectInvalidType() {
        Teacher teacher = buildTeacher(10L, "Teacher-A");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> messageService.getTeacherInbox(teacher, "NOTICE", false));

        assertEquals("消息类型仅支持 ALL、GENERAL、COURSE_REQUEST", ex.getMessage());
        verify(messageRepo, never()).findByRecipientTypeAndRecipientIdOrderBySentAtDesc(any(), any());
        verify(messageRepo, never()).findByRecipientTypeAndRecipientIdAndTypeOrderBySentAtDesc(any(), any(), any());
    }

    @Test
    void getTeacherInboxShouldCombineTypeAndUnreadOnly() {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        InternalMessage unreadGeneral = buildGeneralMessage(2L, false);
        InternalMessage readGeneral = buildGeneralMessage(3L, true);

        when(messageRepo.findByRecipientTypeAndRecipientIdAndTypeOrderBySentAtDesc("TEACHER", 10L, "GENERAL"))
                .thenReturn(List.of(unreadGeneral, readGeneral));

        List<InternalMessage> result = messageService.getTeacherInbox(teacher, "GENERAL", true);

        assertEquals(1, result.size());
        assertSame(unreadGeneral, result.get(0));
    }

    @Test
    void findTeachersBySchoolShouldExcludeAdminsAndAppendCourseNames() {
        School school = new School();
        school.setId(1L);

        Teacher teacher = buildTeacher(10L, "Teacher-A");
        teacher.setAccountType(TeacherAccountType.TEACHER);
        teacher.setSchool(school);

        Teacher schoolAdmin = buildTeacher(20L, "Admin");
        schoolAdmin.setRole("ADMIN");
        schoolAdmin.setAccountType(TeacherAccountType.SCHOOL_ADMIN);
        schoolAdmin.setSchool(school);

        Teacher orgAdmin = buildTeacher(30L, "Org Admin");
        orgAdmin.setRole("TEACHER");
        orgAdmin.setAccountType(TeacherAccountType.ORG_ADMIN);
        orgAdmin.setSchool(school);

        Course football = new Course();
        football.setId(1L);
        football.setName("Football");
        football.setTeacher(teacher);

        Course basketball = new Course();
        basketball.setId(2L);
        basketball.setName("Basketball");
        basketball.setTeacher(teacher);

        when(teacherRepository.findBySchool(school)).thenReturn(List.of(teacher, schoolAdmin, orgAdmin));
        when(courseRepository.findBySchoolAndTeacherIsNotNullOrderByNameAsc(school))
                .thenReturn(List.of(basketball, football));

        List<MessageService.TeacherMessageRecipient> recipients = messageService.findTeachersBySchool(school);

        assertEquals(1, recipients.size());
        assertEquals(teacher.getId(), recipients.get(0).getId());
        assertEquals("Teacher-A（Basketball、Football）", recipients.get(0).getDisplayName());
    }

    @Test
    void getLatestStudentCourseRequestsShouldKeepLatestPerCourse() {
        Student student = buildStudent(99L, "Student-A");
        SelectionEvent event = new SelectionEvent();
        event.setId(7L);

        Course basketball = new Course();
        basketball.setId(3L);
        Course football = new Course();
        football.setId(4L);

        InternalMessage latestBasketball = buildCourseRequestMessage("PENDING", 10L);
        latestBasketball.setRelatedCourseId(3L);
        InternalMessage olderBasketball = buildCourseRequestMessage("REJECTED", 10L);
        olderBasketball.setId(2L);
        olderBasketball.setRelatedCourseId(3L);
        InternalMessage otherCourse = buildCourseRequestMessage("PENDING", 10L);
        otherCourse.setId(3L);
        otherCourse.setRelatedCourseId(99L);

        when(courseService.findByEvent(event)).thenReturn(List.of(basketball, football));
        when(messageRepo.findByTypeAndSenderIdAndSenderTypeOrderBySentAtDesc("COURSE_REQUEST", 99L, "STUDENT"))
                .thenReturn(List.of(latestBasketball, olderBasketball, otherCourse));

        Map<Long, InternalMessage> result = messageService.getLatestStudentCourseRequests(student, event);

        assertEquals(1, result.size());
        assertSame(latestBasketball, result.get(3L));
    }

    @Test
    void sendCourseRequestShouldRejectTooLongContent() {
        Student student = buildStudent(99L, "Student-A");
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        Course course = new Course();
        course.setId(3L);
        course.setTeacher(teacher);

        when(messageRepo.existsByTypeAndRelatedCourseIdAndSenderIdAndStatusNot(
                "COURSE_REQUEST", 3L, 99L, "REJECTED")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> messageService.sendCourseRequest(student, course, "x".repeat(201)));

        assertEquals("申请理由不能超过200字", ex.getMessage());
        verify(messageRepo, never()).save(any());
    }

    private InternalMessage buildCourseRequestMessage(String status, Long recipientTeacherId) {
        InternalMessage msg = new InternalMessage();
        msg.setId(1L);
        msg.setType("COURSE_REQUEST");
        msg.setStatus(status);
        msg.setRecipientType("TEACHER");
        msg.setRecipientId(recipientTeacherId);
        msg.setSenderId(99L);
        msg.setSenderName("Student-A");
        msg.setRelatedCourseId(3L);
        msg.setRelatedCourseName("Basketball");
        msg.setSchool(new School());
        msg.setIsRead(false);
        return msg;
    }

    private InternalMessage buildGeneralMessage(Long id, boolean isRead) {
        InternalMessage msg = new InternalMessage();
        msg.setId(id);
        msg.setType("GENERAL");
        msg.setRecipientType("TEACHER");
        msg.setRecipientId(10L);
        msg.setSenderId(1L);
        msg.setSenderName("System");
        msg.setSchool(new School());
        msg.setIsRead(isRead);
        return msg;
    }

    private Teacher buildTeacher(Long id, String name) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName(name);
        teacher.setRole("TEACHER");
        return teacher;
    }

    private Student buildStudent(Long id, String name) {
        Student student = new Student();
        student.setId(id);
        student.setName(name);
        student.setSchool(new School());
        return student;
    }
}
