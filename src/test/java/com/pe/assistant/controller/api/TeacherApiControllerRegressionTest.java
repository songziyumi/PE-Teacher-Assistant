package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.CourseRequestAudit;
import com.pe.assistant.entity.Grade;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.AttendanceService;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.GradeService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.PhysicalTestService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TermGradeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeacherApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeacherApiControllerRegressionTest {

    private static final String STATUS_ACTIVE = "\u5728\u7c4d";
    private static final String STATUS_OUTGOING_BORROW = "\u5728\u5916\u501f\u8bfb";
    private static final String LEGACY_STATUS_OUTGOING_BORROW = "\u5916\u51fa\u501f\u8bfb";
    private static final String MSG_ALREADY_HANDLED = "\u8bf7\u6c42\u5df2\u5904\u7406\uff0c\u65e0\u6cd5\u91cd\u590d\u64cd\u4f5c";
    private static final String MSG_NOT_OWNER = "\u65e0\u6743\u5904\u7406\u4ed6\u4eba\u7684\u7533\u8bf7";
    private static final String MSG_STUDENT_NO_DUPLICATED = "\u5b66\u53f7\u5df2\u5b58\u5728";
    private static final String MSG_NAME_BLANK = "\u5b66\u751f\u59d3\u540d\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_STUDENT_NO_BLANK = "\u5b66\u53f7\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_STUDENT_NO_WHITESPACE = "\u5b66\u53f7\u4e0d\u80fd\u5305\u542b\u7a7a\u683c";
    private static final String MSG_STALE_VERSION = "\u8be5\u5b66\u751f\u5df2\u88ab\u5176\u4ed6\u8bbe\u5907\u4fee\u6539\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5";
    private static final String MSG_CROSS_SCHOOL = "\u65e0\u6743\u6279\u91cf\u4fee\u6539\u5176\u4ed6\u5b66\u6821\u5b66\u751f";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClassService classService;
    @MockBean
    private StudentService studentService;
    @MockBean
    private AttendanceService attendanceService;
    @MockBean
    private PhysicalTestService physicalTestService;
    @MockBean
    private TermGradeService termGradeService;
    @MockBean
    private MessageService messageService;
    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private GradeService gradeService;
    @MockBean
    private TeacherRepository teacherRepository;
    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void approveShouldFailWhenRequestAlreadyHandled() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doThrow(new RuntimeException(MSG_ALREADY_HANDLED))
                .when(messageService).approveRequest(eq(1L), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(MSG_ALREADY_HANDLED));
    }

    @Test
    void approveShouldFailForNonOwnerTeacher() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doThrow(new RuntimeException(MSG_NOT_OWNER))
                .when(messageService).approveRequest(eq(2L), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/2/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(MSG_NOT_OWNER));
    }

    @Test
    void approvalFlowShouldExposeApprovedAndReadStateInDetailApi() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        InternalMessage msg = buildCourseRequestMessage(3L, "PENDING", false);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doAnswer(invocation -> {
            String remark = invocation.getArgument(2, String.class);
            msg.setStatus("APPROVED");
            msg.setIsRead(true);
            msg.setHandledById(teacher.getId());
            msg.setHandledByName(teacher.getName());
            msg.setHandledAt(LocalDateTime.of(2026, 3, 8, 12, 0, 0));
            msg.setHandleRemark(remark);
            return null;
        }).when(messageService).approveRequest(eq(3L), eq(teacher), any());
        when(messageService.getTeacherCourseRequestById(teacher, 3L)).thenReturn(msg);
        when(messageService.getTeacherCourseRequestAudits(teacher, 3L)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/teacher/course-requests/3/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("remark", "ok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/teacher/course-requests/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.handledByName").value("Teacher-A"))
                .andExpect(jsonPath("$.data.handleRemark").value("ok"));
    }

    @Test
    void detailShouldKeepAuditLogsInDescOrder() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        InternalMessage msg = buildCourseRequestMessage(4L, "APPROVED", true);
        CourseRequestAudit newer = buildAudit(11L, "REJECT", LocalDateTime.of(2026, 3, 8, 11, 0, 0));
        CourseRequestAudit older = buildAudit(12L, "APPROVE", LocalDateTime.of(2026, 3, 7, 11, 0, 0));

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(messageService.getTeacherCourseRequestById(teacher, 4L)).thenReturn(msg);
        when(messageService.getTeacherCourseRequestAudits(teacher, 4L)).thenReturn(List.of(newer, older));

        mockMvc.perform(get("/api/teacher/course-requests/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.auditLogs", hasSize(2)))
                .andExpect(jsonPath("$.data.auditLogs[0].action").value("REJECT"))
                .andExpect(jsonPath("$.data.auditLogs[1].action").value("APPROVE"));
    }

    @Test
    void messagesShouldRejectInvalidType() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(messageService.getTeacherInbox(teacher, "NOTICE", false))
                .thenThrow(new IllegalArgumentException("\u6d88\u606f\u7c7b\u578b\u4ec5\u652f\u6301 ALL\u3001GENERAL\u3001COURSE_REQUEST"));

        mockMvc.perform(get("/api/teacher/messages").param("type", "NOTICE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("\u6d88\u606f\u7c7b\u578b\u4ec5\u652f\u6301 ALL\u3001GENERAL\u3001COURSE_REQUEST"));
    }

    @Test
    void messagesShouldKeepUnreadOnlyAndTypeFiltersStable() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        InternalMessage unreadGeneral = buildGeneralMessage(11L, false);
        InternalMessage readGeneral = buildGeneralMessage(12L, true);
        InternalMessage unreadCourseRequest = buildCourseRequestMessage(13L, "PENDING", false);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doAnswer(invocation -> {
            String type = invocation.getArgument(1, String.class);
            boolean unreadOnly = invocation.getArgument(2, Boolean.class);
            return List.of(unreadGeneral, readGeneral, unreadCourseRequest).stream()
                    .filter(msg -> "ALL".equals(type) || type.equals(msg.getType()))
                    .filter(msg -> !unreadOnly || !Boolean.TRUE.equals(msg.getIsRead()))
                    .toList();
        }).when(messageService).getTeacherInbox(eq(teacher), any(), anyBoolean());

        mockMvc.perform(get("/api/teacher/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(get("/api/teacher/messages").param("type", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].type").value("GENERAL"))
                .andExpect(jsonPath("$.data[1].type").value("GENERAL"));

        mockMvc.perform(get("/api/teacher/messages")
                        .param("type", "GENERAL")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].isRead").value(false));

        mockMvc.perform(get("/api/teacher/messages")
                        .param("type", "COURSE_REQUEST")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(13))
                .andExpect(jsonPath("$.data[0].type").value("COURSE_REQUEST"));
    }

    @Test
    void markReadShouldKeepUnreadCountAndInboxStateConsistent() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        List<InternalMessage> inbox = new ArrayList<>(List.of(
                buildGeneralMessage(21L, false),
                buildCourseRequestMessage(22L, "PENDING", true)));

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doAnswer(invocation -> inbox.stream()
                .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()))
                .count()).when(messageService).getUnreadCount("TEACHER", teacher.getId());
        doAnswer(invocation -> {
            Long messageId = invocation.getArgument(0, Long.class);
            inbox.stream()
                    .filter(msg -> messageId.equals(msg.getId()))
                    .findFirst()
                    .ifPresent(msg -> msg.setIsRead(true));
            return null;
        }).when(messageService).markTeacherMessageRead(21L, teacher);
        doAnswer(invocation -> {
            String type = invocation.getArgument(1, String.class);
            boolean unreadOnly = invocation.getArgument(2, Boolean.class);
            return inbox.stream()
                    .filter(msg -> "ALL".equals(type) || type.equals(msg.getType()))
                    .filter(msg -> !unreadOnly || !Boolean.TRUE.equals(msg.getIsRead()))
                    .toList();
        }).when(messageService).getTeacherInbox(eq(teacher), any(), anyBoolean());

        mockMvc.perform(get("/api/teacher/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(post("/api/teacher/messages/21/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/teacher/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/api/teacher/messages")
                        .param("type", "GENERAL")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/teacher/messages")
                        .param("type", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].isRead").value(true));
    }

    @Test
    void updateStudentShouldFailWhenStudentNoDuplicated() throws Exception {
        Student current = buildStudent(100L, "Student-A", "S-100", STATUS_ACTIVE,
                11L, "1-Class", 7L, "Grade 1", null);
        current.setGender("\u7537");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException(MSG_STUDENT_NO_DUPLICATED))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentNo", "S-200"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(MSG_STUDENT_NO_DUPLICATED));
    }

    @Test
    void updateStudentShouldFailWhenNameBlank() throws Exception {
        Student current = buildStudent(100L, "Student-A", "S-100", STATUS_ACTIVE,
                11L, "1-Class", 7L, "Grade 1", null);
        current.setGender("\u7537");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException(MSG_NAME_BLANK))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(MSG_NAME_BLANK));
    }

    @Test
    void updateStudentShouldFailWhenStudentNoBlank() throws Exception {
        Student current = buildStudent(100L, "Student-A", "S-100", STATUS_ACTIVE,
                11L, "1-Class", 7L, "Grade 1", null);
        current.setGender("\u7537");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException(MSG_STUDENT_NO_BLANK))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentNo", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(MSG_STUDENT_NO_BLANK));
    }

    @Test
    void updateStudentShouldFailWhenStudentNoContainsWhitespace() throws Exception {
        Student current = buildStudent(100L, "Student-A", "S-100", STATUS_ACTIVE,
                11L, "1-Class", 7L, "Grade 1", null);
        current.setGender("\u7537");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException(MSG_STUDENT_NO_WHITESPACE))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentNo", "S 200"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(MSG_STUDENT_NO_WHITESPACE));
    }

    @Test
    void updateStudentShouldFailWhenVersionStale() throws Exception {
        Student current = buildStudent(100L, "Student-A", "S-100", STATUS_ACTIVE,
                11L, "1-Class", 7L, "Grade 1", null);
        current.setGender("\u7537");
        current.setVersion(5L);

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalStateException(MSG_STALE_VERSION))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentNo", "S-200",
                                "version", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(MSG_STALE_VERSION));
    }

    @Test
    void rejectShouldAcceptBlankAndOverlongRemark() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doNothing().when(messageService).rejectRequest(anyLong(), eq(teacher), any());

        String blankRemark = "   ";
        String longRemark = "x".repeat(600);

        mockMvc.perform(post("/api/teacher/course-requests/5/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("remark", blankRemark))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/teacher/course-requests/6/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("remark", longRemark))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<String> remarkCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService, times(2)).rejectRequest(anyLong(), eq(teacher), remarkCaptor.capture());
        assertEquals(blankRemark, remarkCaptor.getAllValues().get(0));
        assertEquals(longRemark, remarkCaptor.getAllValues().get(1));
    }

    @Test
    void batchApproveShouldReturnSuccessSummary() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doNothing().when(messageService).approveRequest(anyLong(), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/batch-handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(11, 12),
                                "action", "APPROVE",
                                "remark", "ok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.action").value("APPROVE"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.successIds", hasSize(2)))
                .andExpect(jsonPath("$.data.failedItems", hasSize(0)));

        verify(messageService, times(1)).approveRequest(eq(11L), eq(teacher), eq("ok"));
        verify(messageService, times(1)).approveRequest(eq(12L), eq(teacher), eq("ok"));
    }

    @Test
    void batchRejectShouldReturnSuccessSummary() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doNothing().when(messageService).rejectRequest(anyLong(), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/batch-handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(21, 22),
                                "action", "REJECT",
                                "remark", "deny"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.action").value("REJECT"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.successIds", hasSize(2)))
                .andExpect(jsonPath("$.data.failedItems", hasSize(0)));

        verify(messageService, times(1)).rejectRequest(eq(21L), eq(teacher), eq("deny"));
        verify(messageService, times(1)).rejectRequest(eq(22L), eq(teacher), eq("deny"));
    }

    @Test
    void batchHandleShouldExposePartialFailureReasons() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doAnswer(invocation -> {
            Long messageId = invocation.getArgument(0, Long.class);
            if (messageId.equals(32L)) {
                throw new RuntimeException("\u7533\u8bf7\u5df2\u5904\u7406\uff0c\u65e0\u6cd5\u91cd\u590d\u64cd\u4f5c");
            }
            if (messageId.equals(33L)) {
                throw new RuntimeException("\u65e0\u6743\u5904\u7406\u4ed6\u4eba\u7684\u7533\u8bf7");
            }
            if (messageId.equals(34L)) {
                throw new RuntimeException("\u6d88\u606f\u4e0d\u5b58\u5728");
            }
            if (messageId.equals(35L)) {
                throw new RuntimeException("\u8be5\u6d88\u606f\u4e0d\u662f\u9009\u8bfe\u7533\u8bf7");
            }
            return null;
        }).when(messageService).approveRequest(anyLong(), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/batch-handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(31, 32, 33, 34, 35),
                                "action", "APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(4))
                .andExpect(jsonPath("$.data.failedItems", hasSize(4)))
                .andExpect(jsonPath("$.data.failedItems[0].messageId").value(32))
                .andExpect(jsonPath("$.data.failedItems[0].reason")
                        .value("\u7533\u8bf7\u5df2\u5904\u7406\uff0c\u65e0\u6cd5\u91cd\u590d\u64cd\u4f5c"))
                .andExpect(jsonPath("$.data.failedItems[1].messageId").value(33))
                .andExpect(jsonPath("$.data.failedItems[1].reason")
                        .value("\u65e0\u6743\u5904\u7406\u4ed6\u4eba\u7684\u7533\u8bf7"))
                .andExpect(jsonPath("$.data.failedItems[2].messageId").value(34))
                .andExpect(jsonPath("$.data.failedItems[2].reason").value("\u6d88\u606f\u4e0d\u5b58\u5728"))
                .andExpect(jsonPath("$.data.failedItems[3].messageId").value(35))
                .andExpect(jsonPath("$.data.failedItems[3].reason").value("\u8be5\u6d88\u606f\u4e0d\u662f\u9009\u8bfe\u7533\u8bf7"));
    }

    @Test
    void batchHandleShouldDeduplicateMessageIds() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doNothing().when(messageService).approveRequest(anyLong(), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/batch-handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(41, 41, 42, 42),
                                "action", "APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.successIds", hasSize(2)));

        verify(messageService, times(1)).approveRequest(eq(41L), eq(teacher), any());
        verify(messageService, times(1)).approveRequest(eq(42L), eq(teacher), any());
    }

    @Test
    void batchRejectShouldPassBlankAndOverlongRemark() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doNothing().when(messageService).rejectRequest(anyLong(), eq(teacher), any());

        String blankRemark = "   ";
        String longRemark = "x".repeat(600);

        mockMvc.perform(post("/api/teacher/course-requests/batch-handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(51),
                                "action", "REJECT",
                                "remark", blankRemark))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/teacher/course-requests/batch-handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(52),
                                "action", "REJECT",
                                "remark", longRemark))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<String> remarkCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService, times(2)).rejectRequest(anyLong(), eq(teacher), remarkCaptor.capture());
        assertEquals(blankRemark, remarkCaptor.getAllValues().get(0));
        assertEquals(longRemark, remarkCaptor.getAllValues().get(1));
    }

    @Test
    void batchUpdateStudentStatusShouldDelegateToService() throws Exception {
        School school = new School();
        school.setId(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);

        StudentService.BatchStudentOperationResult result =
                new StudentService.BatchStudentOperationResult(List.of(101L, 102L));
        result.addSuccess();
        result.addSuccess();
        when(studentService.batchUpdateStudentStatus(eq(school), eq(List.of(101L, 102L)), eq(STATUS_ACTIVE)))
                .thenReturn(result);

        mockMvc.perform(post("/api/teacher/students/batch-update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentIds", List.of(101, 102),
                                "studentStatus", STATUS_ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.failedItems", hasSize(0)));
    }

    @Test
    void batchUpdateStudentElectiveClassShouldAllowClearingElectiveClass() throws Exception {
        School school = new School();
        school.setId(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);

        StudentService.BatchStudentOperationResult result =
                new StudentService.BatchStudentOperationResult(List.of(101L));
        result.addSuccess();
        when(studentService.batchUpdateElectiveClass(eq(school), eq(List.of(101L)), eq(null)))
                .thenReturn(result);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("studentIds", List.of(101));
        request.put("electiveClass", null);

        mockMvc.perform(post("/api/teacher/students/batch-update-elective-class")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.failedItems", hasSize(0)));
    }

    @Test
    void batchUpdateStudentStatusShouldValidateEmptySelection() throws Exception {
        mockMvc.perform(post("/api/teacher/students/batch-update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentIds", List.of(),
                                "studentStatus", STATUS_ACTIVE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void batchUpdateStudentStatusShouldReportDeduplicatedIds() throws Exception {
        School school = new School();
        school.setId(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);

        StudentService.BatchStudentOperationResult result =
                new StudentService.BatchStudentOperationResult(List.of(101L, 101L, 102L));
        result.addSuccess();
        result.addFailure(102L, MSG_CROSS_SCHOOL);
        when(studentService.batchUpdateStudentStatus(eq(school), eq(List.of(101L, 101L, 102L)), eq(STATUS_ACTIVE)))
                .thenReturn(result);

        mockMvc.perform(post("/api/teacher/students/batch-update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentIds", List.of(101, 101, 102),
                                "studentStatus", STATUS_ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.studentIds", hasSize(2)))
                .andExpect(jsonPath("$.data.failedItems[0].id").value(102))
                .andExpect(jsonPath("$.data.failedItems[0].reason").value(MSG_CROSS_SCHOOL));
    }

    @Test
    void batchUpdateStudentStatusShouldReportCrossSchoolRejection() throws Exception {
        School school = new School();
        school.setId(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);

        StudentService.BatchStudentOperationResult result =
                new StudentService.BatchStudentOperationResult(List.of(201L));
        result.addFailure(201L, MSG_CROSS_SCHOOL);
        when(studentService.batchUpdateStudentStatus(eq(school), eq(List.of(201L)), eq(STATUS_ACTIVE)))
                .thenReturn(result);

        mockMvc.perform(post("/api/teacher/students/batch-update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentIds", List.of(201),
                                "studentStatus", STATUS_ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failedItems[0].id").value(201))
                .andExpect(jsonPath("$.data.failedItems[0].reason").value(MSG_CROSS_SCHOOL));
    }

    @Test
    void studentsShouldApplyAllSupportedFilters() throws Exception {
        School school = new School();
        school.setId(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);

        SchoolClass requestedClass = new SchoolClass();
        requestedClass.setId(9L);
        requestedClass.setType("ADMIN");
        when(classService.findById(9L)).thenReturn(requestedClass);

        Student matched = buildStudent(101L, "Alice Zhang", "S-001", LEGACY_STATUS_OUTGOING_BORROW,
                11L, "1-Class", 7L, "Grade 1", "Grade 1/Basketball");
        Student nonMatched = buildStudent(102L, "Bob Li", "S-002", STATUS_ACTIVE,
                12L, "2-Class", 7L, "Grade 1", "Grade 1/Football");
        when(studentService.findByClassIdForTeacher(school, 9L)).thenReturn(List.of(matched, nonMatched));

        mockMvc.perform(get("/api/teacher/classes/9/students")
                        .param("name", "alice")
                        .param("studentNo", "001")
                        .param("adminClassId", "11")
                        .param("electiveClass", "Basketball")
                        .param("studentStatus", STATUS_OUTGOING_BORROW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(101));
    }

    private Teacher buildTeacher(Long id, String name) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName(name);
        return teacher;
    }

    private InternalMessage buildCourseRequestMessage(Long id, String status, boolean isRead) {
        InternalMessage msg = new InternalMessage();
        msg.setId(id);
        msg.setType("COURSE_REQUEST");
        msg.setStatus(status);
        msg.setIsRead(isRead);
        msg.setSenderId(99L);
        msg.setSenderName("Student-A");
        msg.setRecipientType("TEACHER");
        msg.setRecipientId(10L);
        msg.setRelatedCourseId(3L);
        msg.setRelatedCourseName("Basketball");
        msg.setSubject("Course Request");
        msg.setContent("Please review");
        msg.setSentAt(LocalDateTime.of(2026, 3, 8, 9, 0, 0));
        return msg;
    }

    private InternalMessage buildGeneralMessage(Long id, boolean isRead) {
        InternalMessage msg = new InternalMessage();
        msg.setId(id);
        msg.setType("GENERAL");
        msg.setIsRead(isRead);
        msg.setSenderType("SYSTEM");
        msg.setSenderId(1L);
        msg.setSenderName("System");
        msg.setRecipientType("TEACHER");
        msg.setRecipientId(10L);
        msg.setSubject("General notice");
        msg.setContent("Please check the message center");
        msg.setSentAt(LocalDateTime.of(2026, 3, 8, 10, 0, 0));
        return msg;
    }

    private CourseRequestAudit buildAudit(Long id, String action, LocalDateTime handledAt) {
        CourseRequestAudit audit = new CourseRequestAudit();
        audit.setId(id);
        audit.setRequestMessageId(1L);
        audit.setAction(action);
        audit.setBeforeStatus("PENDING");
        audit.setAfterStatus("APPROVED");
        audit.setOperatorTeacherId(10L);
        audit.setOperatorTeacherName("Teacher-A");
        audit.setSenderId(99L);
        audit.setSenderName("Student-A");
        audit.setRelatedCourseId(3L);
        audit.setRelatedCourseName("Basketball");
        audit.setHandledAt(handledAt);
        return audit;
    }

    private Student buildStudent(Long id, String name, String studentNo, String studentStatus,
            Long classId, String className, Long gradeId, String gradeName, String electiveClass) {
        Student student = new Student();
        student.setId(id);
        student.setName(name);
        student.setStudentNo(studentNo);
        student.setStudentStatus(studentStatus);
        student.setElectiveClass(electiveClass);

        Grade grade = new Grade();
        grade.setId(gradeId);
        grade.setName(gradeName);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setName(className);
        schoolClass.setGrade(grade);
        student.setSchoolClass(schoolClass);
        return student;
    }
}
