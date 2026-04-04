package com.pe.assistant.service;

import com.pe.assistant.dto.Round1LotterySummary;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;
import java.util.Set;

class CourseEventDetailTemplateRenderTest {

    private static final class FakeCsrfToken {
        public String getParameterName() {
            return "_csrf";
        }

        public String getToken() {
            return "token";
        }
    }

    @Test
    void courseEventDetailTemplateShouldRender() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setName("Test Event");
        event.setStatus("DRAFT");

        School school = new School();
        school.setName("Test School");

        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, response));

        context.setVariable("currentSchool", school);
        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("event", event);
        context.setVariable("success", null);
        context.setVariable("error", null);
        context.setVariable("courses", List.of());
        context.setVariable("round1Summary", new Round1LotterySummary(0, 0, 0, 0));
        context.setVariable("round1ResultAvailable", false);
        context.setVariable("eventStudents", List.of());
        context.setVariable("participatingStudents", List.of());
        context.setVariable("participatingClasses", List.of());
        context.setVariable("participatingClassIds", Set.of());
        context.setVariable("activeTab", "students");
        context.setVariable("allStudents", List.of());
        context.setVariable("allClasses", List.of());
        context.setVariable("allGrades", List.of());
        context.setVariable("availableTeachers", List.of());

        engine.process("admin/course-event-detail", context);
    }
}
