package com.pe.assistant.controller.api;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.service.AttendanceService;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.GradeService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.PhysicalTestService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TeacherPermissionService;
import com.pe.assistant.service.TermGradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminApiControllerDiagnosticsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;
    @MockBean
    private ClassService classService;
    @MockBean
    private GradeService gradeService;
    @MockBean
    private PhysicalTestService physicalTestService;
    @MockBean
    private TermGradeService termGradeService;
    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private AttendanceService attendanceService;
    @MockBean
    private MessageService messageService;
    @MockBean
    private TeacherPermissionService teacherPermissionService;
    @MockBean
    private com.pe.assistant.repository.TeacherOperationLogRepository teacherOperationLogRepository;
    @MockBean
    private com.pe.assistant.repository.CourseRequestAuditRepository courseRequestAuditRepository;
    @MockBean
    private com.pe.assistant.repository.CourseOverflowAuditRepository courseOverflowAuditRepository;
    @MockBean
    private com.pe.assistant.repository.SelectionEventRepository selectionEventRepository;
    @MockBean
    private com.pe.assistant.repository.CourseRepository courseRepository;
    @MockBean
    private com.pe.assistant.repository.CourseSelectionRepository courseSelectionRepository;
    @MockBean
    private com.pe.assistant.repository.CourseClassCapacityRepository courseClassCapacityRepository;

    @Test
    void diagnosticsShouldReturnWarnWhenSchoolHasNoEvent() throws Exception {
        School school = buildSchool(1L);
        when(currentUserService.getCurrentSchool()).thenReturn(school);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(school)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/course-selection-diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.health.severity").value("WARN"))
                .andExpect(jsonPath("$.data.health.issues[0].code").value("NO_EVENT"));
    }

    @Test
    void diagnosticsShouldExposeCountMismatchAndHotCourseFlag() throws Exception {
        School school = buildSchool(2L);
        SelectionEvent event = new SelectionEvent();
        event.setId(10L);
        event.setSchool(school);
        event.setName("2026 春季选课");
        event.setStatus("ROUND2");

        Course course = new Course();
        course.setId(20L);
        course.setEvent(event);
        course.setSchool(school);
        course.setName("足球");
        course.setStatus("ACTIVE");
        course.setCapacityMode("GLOBAL");
        course.setTotalCapacity(40);
        course.setCurrentCount(5);

        Student student = new Student();
        student.setId(30L);

        CourseSelection selection = new CourseSelection();
        selection.setId(40L);
        selection.setEvent(event);
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setStatus("CONFIRMED");

        when(currentUserService.getCurrentSchool()).thenReturn(school);
        when(selectionEventRepository.findBySchoolOrderByCreatedAtDesc(school)).thenReturn(List.of(event));
        when(courseRepository.findByEventOrderByNameAsc(event)).thenReturn(List.of(course));
        when(courseSelectionRepository.findByEvent(event)).thenReturn(List.of(selection));
        when(teacherOperationLogRepository.findTop200BySchool_IdOrderByOperatedAtDesc(school.getId())).thenReturn(Collections.emptyList());
        when(courseRequestAuditRepository.findTop200BySchool_IdOrderByHandledAtDesc(school.getId())).thenReturn(Collections.emptyList());
        when(courseOverflowAuditRepository.findTop200BySchoolIdOrderByCreatedAtDesc(school.getId())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/course-selection-diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.health.severity").value("ERROR"))
                .andExpect(jsonPath("$.data.health.issues[0].code").value("COURSE_COUNT_MISMATCH"))
                .andExpect(jsonPath("$.data.hotCourses[0].courseName").value("足球"))
                .andExpect(jsonPath("$.data.hotCourses[0].countMismatch").value(true));
    }

    private School buildSchool(Long id) {
        School school = new School();
        school.setId(id);
        school.setName("测试学校");
        return school;
    }
}
