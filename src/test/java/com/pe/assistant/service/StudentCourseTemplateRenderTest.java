package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.CourseSelection;
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

class StudentCourseTemplateRenderTest {

    private static final class FakeCsrfToken {
        public String getParameterName() {
            return "_csrf";
        }

        public String getToken() {
            return "token";
        }
    }

    @Test
    void studentCoursesTemplateShouldRenderWithConfirmedCountMap() {
        SpringTemplateEngine engine = buildTemplateEngine();
        WebContext context = buildContext();

        School school = new School();
        school.setName("测试学校");

        Student student = new Student();
        student.setName("张三");
        student.setSchool(school);

        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setName("2026 春季选课");
        event.setStatus("ROUND2");

        Course course = new Course();
        course.setId(10L);
        course.setName("篮球");
        course.setTotalCapacity(120);
        course.setCurrentCount(120);
        course.setStatus("ACTIVE");

        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("event", event);
        context.setVariable("student", student);
        context.setVariable("courses", List.of(course));
        context.setVariable("mySelections", List.of());
        context.setVariable("remainingMap", Map.of(course.getId(), 2));
        context.setVariable("confirmedCountMap", Map.of(course.getId(), 118));
        context.setVariable("unreadCount", 0L);
        context.setVariable("inRound1", false);
        context.setVariable("inRound2", true);
        context.setVariable("inRound3", false);
        context.setVariable("hasConfirmed", false);
        context.setVariable("hasPref1", false);
        context.setVariable("hasPref2", false);
        context.setVariable("round1SubmissionConfirmed", false);

        engine.process("student/courses", context);
    }

    @Test
    void studentCoursesTemplateShouldRenderRound3RequestState() {
        SpringTemplateEngine engine = buildTemplateEngine();
        WebContext context = buildContext();

        School school = new School();
        school.setName("测试学校");

        Student student = new Student();
        student.setName("张三");
        student.setSchool(school);

        SelectionEvent event = new SelectionEvent();
        event.setId(2L);
        event.setName("2026 春季选课");
        event.setStatus("CLOSED");

        Course course = new Course();
        course.setId(20L);
        course.setName("羽毛球");
        course.setTotalCapacity(40);
        course.setStatus("ACTIVE");
        Teacher teacher = new Teacher();
        teacher.setId(3L);
        teacher.setName("李老师");
        course.setTeacher(teacher);

        InternalMessage request = new InternalMessage();
        request.setStatus("REJECTED");
        request.setContent("我想补报羽毛球");
        request.setHandleRemark("请补充申请说明");

        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("event", event);
        context.setVariable("student", student);
        context.setVariable("courses", List.of(course));
        context.setVariable("mySelections", List.of());
        context.setVariable("remainingMap", Map.of(course.getId(), 2));
        context.setVariable("confirmedCountMap", Map.of(course.getId(), 38));
        context.setVariable("round3RequestMap", Map.of(course.getId(), request));
        context.setVariable("unreadCount", 0L);
        context.setVariable("inRound1", false);
        context.setVariable("inRound2", false);
        context.setVariable("inRound3", true);
        context.setVariable("hasConfirmed", false);
        context.setVariable("hasPref1", false);
        context.setVariable("hasPref2", false);
        context.setVariable("round1SubmissionConfirmed", false);

        String html = engine.process("student/courses", context);

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("重新申请"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("教师备注"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("request-textarea"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("request-counter"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("request-status-badge"));
    }

    @Test
    void studentMyCoursesTemplateShouldRenderDropFeedbackState() {
        SpringTemplateEngine engine = buildTemplateEngine();
        WebContext context = buildContext();

        School school = new School();
        school.setName("测试学校");

        Student student = new Student();
        student.setName("张三");
        student.setSchool(school);

        SelectionEvent event = new SelectionEvent();
        event.setId(3L);
        event.setName("2026 春季选课");
        event.setStatus("ROUND2");

        Course course = new Course();
        course.setId(30L);
        course.setName("足球");

        CourseSelection selection = new CourseSelection();
        selection.setId(40L);
        selection.setCourse(course);
        selection.setStatus("CONFIRMED");
        selection.setRound(1);
        selection.setPreference(1);

        context.setVariable("_csrf", new FakeCsrfToken());
        context.setVariable("student", student);
        context.setVariable("event", event);
        context.setVariable("mySelections", List.of(selection));
        context.setVariable("droppableSelectionIds", List.of(40L));

        String html = engine.process("student/my-courses", context);

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("dropAjaxFeedback"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("drop-selection-button"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("退课处理中..."));
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
