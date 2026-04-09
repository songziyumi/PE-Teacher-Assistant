package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.StudentRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminStudentPageRegressionTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;

    @Test
    void editStudentShouldNotReturn500() throws Exception {
        Teacher operator = resolveOperatorWithStudents();
        MockHttpSession session = authenticatedSession(operator);

        MvcResult page = mockMvc.perform(get("/admin/students").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = extractCsrf(page.getResponse().getContentAsString());
        assertNotNull(csrfToken);

        List<Student> students = studentRepository.findBySchoolOrderByStudentNo(operator.getSchool());
        assertFalse(students.isEmpty(), "No students found for school " + operator.getSchool().getId());

        for (Student student : students.stream().limit(20).toList()) {
            MvcResult result = mockMvc.perform(post("/admin/students/edit/{id}", student.getId())
                            .session(session)
                            .param("_csrf", csrfToken)
                            .param("name", blankToEmpty(student.getName()))
                            .param("gender", blankToEmpty(student.getGender()))
                            .param("studentNo", blankToEmpty(student.getStudentNo()))
                            .param("idCard", blankToEmpty(student.getIdCard()))
                            .param("electiveClass", blankToEmpty(student.getElectiveClass()))
                            .param("classId", String.valueOf(student.getSchoolClass().getId()))
                            .param("studentStatus", blankToEmpty(student.getStudentStatus())))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();
            assertTrue(status >= 300 && status < 400,
                    "Student " + student.getId() + " edit returned status " + status + ", body=" + body);
            String redirectedUrl = result.getResponse().getRedirectedUrl();
            assertTrue(redirectedUrl != null && redirectedUrl.startsWith("/admin/students"),
                    "Student " + student.getId() + " redirected to unexpected url " + redirectedUrl);

            mockMvc.perform(get(redirectedUrl).session(session))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void editStudentShouldKeepCurrentClassWhenClassIdMissing() throws Exception {
        Teacher operator = resolveOperatorWithStudents();
        MockHttpSession session = authenticatedSession(operator);

        MvcResult page = mockMvc.perform(get("/admin/students").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = extractCsrf(page.getResponse().getContentAsString());

        Student student = studentRepository.findBySchoolOrderByStudentNo(operator.getSchool()).stream()
                .findFirst()
                .orElseThrow();
        Long originalClassId = student.getSchoolClass().getId();

        MvcResult result = mockMvc.perform(post("/admin/students/edit/{id}", student.getId())
                        .session(session)
                        .param("_csrf", csrfToken)
                        .param("name", blankToEmpty(student.getName()))
                        .param("gender", blankToEmpty(student.getGender()))
                        .param("studentNo", blankToEmpty(student.getStudentNo()))
                        .param("idCard", blankToEmpty(student.getIdCard()))
                        .param("electiveClass", blankToEmpty(student.getElectiveClass()))
                        .param("studentStatus", blankToEmpty(student.getStudentStatus())))
                .andReturn();

        assertEquals(302, result.getResponse().getStatus());
        Student updated = studentRepository.findById(student.getId()).orElseThrow();
        assertEquals(originalClassId, updated.getSchoolClass().getId());
    }

    private Teacher resolveOperatorWithStudents() {
        return teacherRepository.findAll().stream()
                .filter(t -> t.getSchool() != null)
                .filter(t -> !studentRepository.findBySchoolOrderByStudentNo(t.getSchool()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No teacher with students found in database"));
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

    private String extractCsrf(String html) {
        Matcher matcher = CSRF_PATTERN.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
