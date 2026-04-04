package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCourseControllerPageRegressionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SelectionEventRepository eventRepository;
    @Autowired
    private TeacherRepository teacherRepository;

    @Test
    void adminPagesShouldRenderForEveryEventInSchool() throws Exception {
        SelectionEvent seedEvent = eventRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No selection event found in database"));

        Teacher operator = resolveOperator(seedEvent.getSchool());
        MockHttpSession session = authenticatedSession(operator);

            mockMvc.perform(get("/admin/courses").session(session))
                    .andExpect(status().isOk());

            List<SelectionEvent> events = eventRepository.findBySchoolOrderByCreatedAtDesc(operator.getSchool());
            for (SelectionEvent event : events) {
                mockMvc.perform(get("/admin/courses/{eventId}/detail", event.getId()).session(session))
                        .andExpect(status().isOk());
                mockMvc.perform(get("/admin/courses/{eventId}/detail", event.getId())
                                .param("tab", "courses")
                                .session(session))
                        .andExpect(status().isOk());
            }
    }

    private Teacher resolveOperator(School school) {
        return teacherRepository.findBySchoolAndRole(school, "ADMIN").stream()
                .findFirst()
                .or(() -> teacherRepository.findBySchool(school).stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No teacher found for school " + school.getId()));
    }

    private MockHttpSession authenticatedSession(Teacher teacher) {
        SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                teacher.getUsername(),
                "N/A",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
