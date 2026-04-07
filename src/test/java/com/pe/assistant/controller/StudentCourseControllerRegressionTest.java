package com.pe.assistant.controller;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.SelectionEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = StudentCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentCourseControllerRegressionTest {

    private static final class FakeCsrfToken {
        public String getParameterName() {
            return "_csrf";
        }

        public String getToken() {
            return "token";
        }
    }

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void coursesShouldRenderNoEventWhenStudentCannotAccessAnyEvent() throws Exception {
        Student student = buildStudent();
        SelectionEvent activeEvent = buildEvent(11L, student.getSchool(), "ROUND2");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(activeEvent));
        when(eventService.canStudentAccessEvent(activeEvent, student)).thenReturn(false);

        mockMvc.perform(get("/student/courses").requestAttr("_csrf", new FakeCsrfToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("student/courses"))
                .andExpect(model().attribute("noEvent", true));
    }

    @Test
    void myCoursesShouldPreferLatestEventWithSelections() throws Exception {
        Student student = buildStudent();
        SelectionEvent activeEvent = buildEvent(12L, student.getSchool(), "ROUND1");
        SelectionEvent closedEvent = buildEvent(10L, student.getSchool(), "CLOSED");

        Course course = new Course();
        course.setId(31L);
        course.setName("篮球");

        CourseSelection selection = new CourseSelection();
        selection.setId(41L);
        selection.setEvent(closedEvent);
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setStatus("CONFIRMED");
        selection.setRound(0);

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(activeEvent, closedEvent));
        when(eventService.canStudentAccessEvent(activeEvent, student)).thenReturn(true);
        when(eventService.canStudentAccessEvent(closedEvent, student)).thenReturn(true);
        when(courseService.findMySelections(student, activeEvent)).thenReturn(List.of());
        when(courseService.findMySelections(student, closedEvent)).thenReturn(List.of(selection));
        when(courseService.canDropSelection(selection)).thenReturn(false);

        mockMvc.perform(get("/student/my-courses").requestAttr("_csrf", new FakeCsrfToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("student/my-courses"))
                .andExpect(model().attribute("event", closedEvent))
                .andExpect(model().attribute("mySelections", List.of(selection)));
    }

    @Test
    void coursesShouldFallbackInsteadOfReturning500WhenRenderingContextFails() throws Exception {
        Student student = buildStudent();
        SelectionEvent activeEvent = buildEvent(13L, student.getSchool(), "ROUND2");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(activeEvent));
        when(eventService.canStudentAccessEvent(activeEvent, student)).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(courseService).findActiveCoursesForStudent(activeEvent, student);

        mockMvc.perform(get("/student/courses").requestAttr("_csrf", new FakeCsrfToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("student/courses"))
                .andExpect(model().attribute("noEvent", true))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void coursesShouldRenderRound3PageWithoutNullBooleanFlags() throws Exception {
        Student student = buildStudent();
        SelectionEvent closedEvent = buildEvent(14L, student.getSchool(), "CLOSED");
        Course course = new Course();
        course.setId(51L);
        course.setName("羽毛球");

        when(currentUserService.getCurrentStudent()).thenReturn(student);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool())).thenReturn(List.of(closedEvent));
        when(eventService.canStudentAccessEvent(closedEvent, student)).thenReturn(true);
        when(courseService.findMySelections(student, closedEvent)).thenReturn(List.of());
        when(courseService.findByEvent(closedEvent)).thenReturn(List.of(course));
        when(courseService.countConfirmedUniqueEnrollments(course)).thenReturn(0);
        when(courseService.getRemainingCapacity(course, student)).thenReturn(1);
        when(messageService.getLatestStudentCourseRequests(student, closedEvent)).thenReturn(java.util.Map.of());
        when(messageService.getUnreadCount("STUDENT", student.getId())).thenReturn(0L);

        mockMvc.perform(get("/student/courses").requestAttr("_csrf", new FakeCsrfToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("student/courses"))
                .andExpect(model().attribute("inRound3", true));
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

    private SelectionEvent buildEvent(Long id, School school, String status) {
        SelectionEvent event = new SelectionEvent();
        event.setId(id);
        event.setSchool(school);
        event.setName("2026 春季选课");
        event.setStatus(status);
        return event;
    }
}
