package com.pe.assistant.service;

import com.pe.assistant.dto.Round1LotterySummary;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.SelectionEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;
import java.util.Set;

@SpringBootTest
class CourseEventDetailIntegrationRenderTest {

    private static final class FakeCsrfToken {
        public String getParameterName() {
            return "_csrf";
        }

        public String getToken() {
            return "token";
        }
    }

    @Autowired
    private SelectionEventRepository eventRepo;
    @Autowired
    private SelectionEventService eventService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private ClassService classService;
    @Autowired
    private GradeService gradeService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void shouldRenderFirstEventDetailWithRealData() {
        SelectionEvent event = eventRepo.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No selection event found in database"));

        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, response));

        Round1LotterySummary round1Summary = eventService.getRound1LotterySummary(event);
        var participatingStudents = eventService.findParticipatingStudents(event);
        Set<Long> participatingClassIds = eventService.findParticipatingClassIds(event);

        context.setVariable("currentSchool", event.getSchool());
        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("event", event);
        context.setVariable("success", null);
        context.setVariable("error", null);
        context.setVariable("courses", courseService.findByEvent(event));
        context.setVariable("round1Summary", round1Summary);
        context.setVariable("round1ResultAvailable",
                "ROUND2".equals(event.getStatus()) || "CLOSED".equals(event.getStatus()));
        context.setVariable("eventStudents", eventService.findEventStudents(event));
        context.setVariable("participatingStudents", participatingStudents);
        context.setVariable("participatingClasses", classService.findAll(event.getSchool()).stream()
                .filter(c -> participatingClassIds.contains(c.getId()))
                .toList());
        context.setVariable("participatingClassIds", participatingClassIds);
        context.setVariable("activeTab", "students");
        context.setVariable("allStudents", studentService.findBySchool(event.getSchool()));
        context.setVariable("allClasses", classService.findAll(event.getSchool()));
        context.setVariable("allGrades", gradeService.findAll(event.getSchool()));
        context.setVariable("availableTeachers", teacherService.findCourseAssignableTeachers(event.getSchool()));

        templateEngine.process("admin/course-event-detail", context);
    }
}
