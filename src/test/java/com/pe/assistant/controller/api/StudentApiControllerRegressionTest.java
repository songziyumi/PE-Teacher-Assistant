package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.SelectionEventService;
import com.pe.assistant.service.StudentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentApiControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseService courseService;
    @MockBean
    private SelectionEventService eventService;
    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private SelectionEventRepository selectionEventRepository;
    @MockBean
    private MessageService messageService;
    @MockBean
    private StudentAccountService studentAccountService;

    @Test
    void requestCourseShouldNormalizeDuplicatePrompt() throws Exception {
        Student student = buildStudent();
        SelectionEvent closedEvent = buildClosedEvent(student.getSchool(), 11L);
        Course course = buildCourse(closedEvent, 21L, "羽毛球");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(closedEvent));
        when(eventService.canStudentAccessEvent(closedEvent, student)).thenReturn(true);
        when(courseService.findMySelections(student, closedEvent)).thenReturn(List.of());
        when(courseService.findById(21L)).thenReturn(course);
        doThrow(new RuntimeException("您已经对该课程发送过申请，请等待教师处理"))
                .when(messageService).sendCourseRequest(student, course, "想补报羽毛球");

        mockMvc.perform(post("/api/student/courses/21/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "想补报羽毛球"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("您已提交过该课程申请，请等待教师处理"));
    }

    @Test
    void requestableCoursesShouldExposePendingRequestState() throws Exception {
        Student student = buildStudent();
        SelectionEvent closedEvent = buildClosedEvent(student.getSchool(), 12L);
        Course course = buildCourse(closedEvent, 22L, "排球");
        InternalMessage request = new InternalMessage();
        request.setStatus("PENDING");
        request.setContent("希望调剂到排球");
        request.setHandleRemark(null);

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(closedEvent));
        when(eventService.canStudentAccessEvent(closedEvent, student)).thenReturn(true);
        when(courseService.findMySelections(student, closedEvent)).thenReturn(List.of());
        when(messageService.getLatestStudentCourseRequests(student, closedEvent)).thenReturn(Map.of(22L, request));
        when(courseService.findByEvent(closedEvent)).thenReturn(List.of(course));
        when(courseService.countConfirmedUniqueEnrollments(course)).thenReturn(18);
        when(courseService.getRemainingCapacity(course, student)).thenReturn(2);

        mockMvc.perform(get("/api/student/courses/requestable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.canRequest").value(true))
                .andExpect(jsonPath("$.data.courses[0].name").value("排球"))
                .andExpect(jsonPath("$.data.courses[0].requestStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.courses[0].requestContent").value("希望调剂到排球"))
                .andExpect(jsonPath("$.data.courses[0].hasPendingRequest").value(true));
    }

    @Test
    void requestCourseShouldNormalizeTeacherMissingPrompt() throws Exception {
        Student student = buildStudent();
        SelectionEvent closedEvent = buildClosedEvent(student.getSchool(), 13L);
        Course course = buildCourse(closedEvent, 23L, "飞盘");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(closedEvent));
        when(eventService.canStudentAccessEvent(closedEvent, student)).thenReturn(true);
        when(courseService.findMySelections(student, closedEvent)).thenReturn(List.of());
        when(courseService.findById(23L)).thenReturn(course);
        doThrow(new RuntimeException("该课程暂未指定授课教师，无法发送申请"))
                .when(messageService).sendCourseRequest(student, course, "");

        mockMvc.perform(post("/api/student/courses/23/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("当前课程暂未分配教师，暂时无法提交申请"));
    }

    @Test
    void mySelectionsShouldReturnEmptyWhenStudentCannotAccessActiveEvent() throws Exception {
        Student student = buildStudent();
        SelectionEvent activeEvent = new SelectionEvent();
        activeEvent.setId(14L);
        activeEvent.setSchool(student.getSchool());
        activeEvent.setName("2026 鏄ュ閫夎");
        activeEvent.setStatus("ROUND2");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(activeEvent));
        when(eventService.canStudentAccessEvent(activeEvent, student)).thenReturn(false);

        mockMvc.perform(get("/api/student/my-selections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void currentEventShouldIgnoreFinalizeBlockWhenTeachersAreUnassigned() throws Exception {
        Student student = buildStudent();
        SelectionEvent activeEvent = new SelectionEvent();
        activeEvent.setId(15L);
        activeEvent.setSchool(student.getSchool());
        activeEvent.setName("2026 鏄ュ閫夎");
        activeEvent.setStatus("ROUND2");
        activeEvent.setRound2End(java.time.LocalDateTime.now().minusMinutes(1));

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(activeEvent));
        when(eventService.canStudentAccessEvent(activeEvent, student)).thenReturn(true);
        doThrow(new IllegalStateException("以下课程尚未分配授课教师"))
                .when(courseService).finalizeEndedRound2Event(15L);
        when(courseService.findMySelections(student, activeEvent)).thenReturn(List.of());
        when(eventService.isInRound1(activeEvent)).thenReturn(false);
        when(eventService.isInRound2(activeEvent)).thenReturn(false);

        mockMvc.perform(get("/api/student/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(15))
                .andExpect(jsonPath("$.data.status").value("ROUND2"));
    }

    @Test
    void mySelectionsShouldUseLatestEventThatHasSelections() throws Exception {
        Student student = buildStudent();
        SelectionEvent activeEvent = new SelectionEvent();
        activeEvent.setId(16L);
        activeEvent.setSchool(student.getSchool());
        activeEvent.setName("2026 秋季选课");
        activeEvent.setStatus("ROUND1");

        SelectionEvent closedEvent = buildClosedEvent(student.getSchool(), 17L);
        Course course = buildCourse(closedEvent, 24L, "篮球");
        CourseSelection selection = new CourseSelection();
        selection.setId(25L);
        selection.setEvent(closedEvent);
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setPreference(0);
        selection.setRound(0);
        selection.setStatus("CONFIRMED");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(activeEvent, closedEvent));
        when(eventService.canStudentAccessEvent(activeEvent, student)).thenReturn(true);
        when(eventService.canStudentAccessEvent(closedEvent, student)).thenReturn(true);
        when(courseService.findMySelections(student, activeEvent)).thenReturn(List.of());
        when(courseService.findMySelections(student, closedEvent)).thenReturn(List.of(selection));
        when(courseService.canDropSelection(selection)).thenReturn(false);

        mockMvc.perform(get("/api/student/my-selections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].courseId").value(24))
                .andExpect(jsonPath("$.data[0].courseName").value("篮球"))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));
    }

    private Student buildStudent() {
        School school = new School();
        school.setId(1L);
        school.setName("测试学校");

        Student student = new Student();
        student.setId(101L);
        student.setName("张三");
        student.setSchool(school);
        return student;
    }

    private SelectionEvent buildClosedEvent(School school, Long eventId) {
        SelectionEvent event = new SelectionEvent();
        event.setId(eventId);
        event.setSchool(school);
        event.setName("2026 春季选课");
        event.setStatus("CLOSED");
        return event;
    }

    private Course buildCourse(SelectionEvent event, Long courseId, String courseName) {
        Teacher teacher = new Teacher();
        teacher.setId(201L);
        teacher.setName("李老师");

        Course course = new Course();
        course.setId(courseId);
        course.setEvent(event);
        course.setName(courseName);
        course.setDescription(courseName + "课程");
        course.setTeacher(teacher);
        course.setTotalCapacity(20);
        course.setCapacityMode("GLOBAL");
        return course;
    }
}
