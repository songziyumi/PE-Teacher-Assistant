package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.CourseRequestAudit;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        doThrow(new RuntimeException("申请已处理，无法重复操作"))
                .when(messageService).approveRequest(eq(1L), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("申请已处理，无法重复操作"));
    }

    @Test
    void approveShouldFailForNonOwnerTeacher() throws Exception {
        Teacher teacher = buildTeacher(10L, "Teacher-A");
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        doThrow(new RuntimeException("无权处理他人的申请"))
                .when(messageService).approveRequest(eq(2L), eq(teacher), any());

        mockMvc.perform(post("/api/teacher/course-requests/2/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无权处理他人的申请"));
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
        when(messageService.getTeacherCourseRequestAudits(teacher, 4L))
                .thenReturn(List.of(newer, older));

        mockMvc.perform(get("/api/teacher/course-requests/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.auditLogs", hasSize(2)))
                .andExpect(jsonPath("$.data.auditLogs[0].action").value("REJECT"))
                .andExpect(jsonPath("$.data.auditLogs[1].action").value("APPROVE"));
    }

    @Test
    void updateStudentShouldFailWhenStudentNoDuplicated() throws Exception {
        Student current = new Student();
        current.setId(100L);
        current.setName("Student-A");
        current.setGender("男");
        current.setStudentNo("S-100");
        current.setStudentStatus("在籍");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException("学号已存在"))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentNo", "S-200"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("学号已存在"));
    }

    @Test
    void updateStudentShouldFailWhenNameBlank() throws Exception {
        Student current = new Student();
        current.setId(100L);
        current.setName("Student-A");
        current.setGender("男");
        current.setStudentNo("S-100");
        current.setStudentStatus("在籍");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException("学生姓名不能为空"))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("学生姓名不能为空"));
    }

    @Test
    void updateStudentShouldFailWhenStudentNoBlank() throws Exception {
        Student current = new Student();
        current.setId(100L);
        current.setName("Student-A");
        current.setGender("男");
        current.setStudentNo("S-100");
        current.setStudentStatus("在籍");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException("学号不能为空"))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentNo", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("学号不能为空"));
    }

    @Test
    void updateStudentShouldFailWhenStudentNoContainsWhitespace() throws Exception {
        Student current = new Student();
        current.setId(100L);
        current.setName("Student-A");
        current.setGender("男");
        current.setStudentNo("S-100");
        current.setStudentStatus("在籍");

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalArgumentException("学号不能包含空格"))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentNo", "S 200"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("学号不能包含空格"));
    }

    @Test
    void updateStudentShouldFailWhenVersionStale() throws Exception {
        Student current = new Student();
        current.setId(100L);
        current.setName("Student-A");
        current.setGender("男");
        current.setStudentNo("S-100");
        current.setStudentStatus("在籍");
        current.setVersion(5L);

        when(studentService.findById(100L)).thenReturn(current);
        doThrow(new IllegalStateException("该学生已被其他设备修改，请刷新后重试"))
                .when(studentService).update(eq(100L), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/teacher/students/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentNo", "S-200",
                                "version", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该学生已被其他设备修改，请刷新后重试"));
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
        when(studentService.batchUpdateStudentStatus(eq(school), eq(List.of(101L, 102L)), eq("在籍")))
                .thenReturn(2);

        mockMvc.perform(post("/api/teacher/students/batch-update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentIds", List.of(101, 102),
                                "studentStatus", "在籍"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }

    @Test
    void batchUpdateStudentElectiveClassShouldAllowClearingElectiveClass() throws Exception {
        School school = new School();
        school.setId(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);
        when(studentService.batchUpdateElectiveClass(eq(school), eq(List.of(101L)), eq(null)))
                .thenReturn(1);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("studentIds", List.of(101));
        request.put("electiveClass", null);

        mockMvc.perform(post("/api/teacher/students/batch-update-elective-class")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1));
    }

    @Test
    void batchUpdateStudentStatusShouldValidateEmptySelection() throws Exception {
        mockMvc.perform(post("/api/teacher/students/batch-update-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentIds", List.of(),
                                "studentStatus", "在籍"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
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
        msg.setSubject("选课申请");
        msg.setContent("请审批");
        msg.setSentAt(LocalDateTime.of(2026, 3, 8, 9, 0, 0));
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
}
