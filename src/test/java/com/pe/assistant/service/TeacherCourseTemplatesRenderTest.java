package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;
import java.util.Map;

class TeacherCourseTemplatesRenderTest {

    private static final class FakeCsrfToken {
        public String getParameterName() {
            return "_csrf";
        }

        public String getToken() {
            return "token";
        }
    }

    @Test
    void teacherCoursesTemplateShouldRenderWithConfirmedCountMap() {
        SpringTemplateEngine engine = buildTemplateEngine();
        WebContext context = buildContext();

        School school = new School();
        school.setName("测试学校");

        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setName("2026 春季选课");
        event.setStatus("ROUND2");

        Course course = new Course();
        course.setId(10L);
        course.setName("篮球");
        course.setTotalCapacity(120);

        context.setVariable("currentSchool", school);
        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("events", List.of(event));
        context.setVariable("coursesByEvent", Map.of(event.getId(), List.of(course)));
        context.setVariable("confirmedCountByCourse", Map.of(course.getId(), 118));
        context.setVariable("teacher", null);

        engine.process("teacher/courses", context);
    }

    @Test
    void teacherCourseEnrollmentsTemplateShouldRenderWithConfirmedStats() {
        SpringTemplateEngine engine = buildTemplateEngine();
        WebContext context = buildContext();

        School school = new School();
        school.setName("测试学校");

        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setName("2026 春季选课");
        event.setStatus("ROUND2");

        Course course = new Course();
        course.setId(10L);
        course.setName("篮球");
        course.setTotalCapacity(120);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName("高一1班");

        Student student = new Student();
        student.setName("张三");
        student.setStudentNo("20260001");
        student.setSchoolClass(schoolClass);

        CourseSelection selection = new CourseSelection();
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setRound(2);
        selection.setPreference(0);
        selection.setStatus("CONFIRMED");

        context.setVariable("currentSchool", school);
        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("event", event);
        context.setVariable("course", course);
        context.setVariable("enrollments", List.of(selection));
        context.setVariable("confirmedEnrollmentCount", 1);
        context.setVariable("remainingEnrollmentCapacity", 119);
        context.setVariable("overflowEnrollmentCount", 0);

        engine.process("teacher/course-enrollments", context);
    }

    private SpringTemplateEngine buildTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private WebContext buildContext() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        return new WebContext(application.buildExchange(request, response));
    }
}
