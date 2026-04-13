package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.security.JwtUtil;
import com.pe.assistant.security.LoginAttemptService;
import com.pe.assistant.security.LoginPrincipalResolver;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthApiControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private StudentAccountService studentAccountService;
    @MockBean
    private LoginAttemptService loginAttemptService;
    @MockBean
    private LoginPrincipalResolver loginPrincipalResolver;

    @Test
    void loginShouldReturnStudentLoginAlias() throws Exception {
        StudentAccount account = buildStudentAccount();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("student-account:11", "N/A");

        when(loginPrincipalResolver.resolveAttemptKey("easy001")).thenReturn("student-account:11");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(loginPrincipalResolver.findTeacher("student-account:11")).thenReturn(Optional.empty());
        when(studentAccountService.resolvePrincipal("student-account:11")).thenReturn(Optional.of(account));
        when(jwtUtil.generateToken("student-account:11", "STUDENT", 1L)).thenReturn("jwt");
        when(studentAccountService.requiresPasswordChange(account)).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "easy001",
                                "password", "NewPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("S123456"))
                .andExpect(jsonPath("$.data.loginAlias").value("easy001"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void loginShouldKeepTeacherAccountResponseUnchanged() throws Exception {
        Teacher teacher = buildTeacher("t001", "王老师", "TEACHER");
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("t001", "N/A");

        when(loginPrincipalResolver.resolveAttemptKey("t001")).thenReturn("t001");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(loginPrincipalResolver.findTeacher("t001")).thenReturn(Optional.of(teacher));
        when(jwtUtil.generateToken("t001", "TEACHER", 1L)).thenReturn("teacher-jwt");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "t001",
                                "password", "TeacherPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("teacher-jwt"))
                .andExpect(jsonPath("$.data.username").value("t001"))
                .andExpect(jsonPath("$.data.loginAlias").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("王老师"))
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void loginShouldKeepAdminAccountResponseUnchanged() throws Exception {
        Teacher admin = buildTeacher("admin001", "管理员", "ADMIN");
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("admin001", "N/A");

        when(loginPrincipalResolver.resolveAttemptKey("admin001")).thenReturn("admin001");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(loginPrincipalResolver.findTeacher("admin001")).thenReturn(Optional.of(admin));
        when(jwtUtil.generateToken("admin001", "ADMIN", 1L)).thenReturn("admin-jwt");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin001",
                                "password", "AdminPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("admin-jwt"))
                .andExpect(jsonPath("$.data.username").value("admin001"))
                .andExpect(jsonPath("$.data.loginAlias").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void meShouldExposeStudentLoginAlias() throws Exception {
        StudentAccount account = buildStudentAccount();

        when(currentUserService.getCurrentTeacher()).thenThrow(new IllegalStateException("not teacher"));
        when(currentUserService.getCurrentStudent()).thenReturn(account.getStudent());
        when(currentUserService.getCurrentStudentAccount()).thenReturn(account);
        when(studentAccountService.requiresPasswordChange(account)).thenReturn(false);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("S123456"))
                .andExpect(jsonPath("$.data.loginAlias").value("easy001"))
                .andExpect(jsonPath("$.data.name").value("张三"));
    }

    @Test
    void meShouldKeepTeacherAccountResponseUnchanged() throws Exception {
        Teacher teacher = buildTeacher("t001", "王老师", "TEACHER");

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("t001"))
                .andExpect(jsonPath("$.data.loginAlias").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("王老师"))
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void meShouldKeepAdminAccountResponseUnchanged() throws Exception {
        Teacher admin = buildTeacher("admin001", "管理员", "ADMIN");

        when(currentUserService.getCurrentTeacher()).thenReturn(admin);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin001"))
                .andExpect(jsonPath("$.data.loginAlias").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    private StudentAccount buildStudentAccount() {
        School school = new School();
        school.setId(1L);
        school.setName("测试学校");

        Student student = new Student();
        student.setId(101L);
        student.setName("张三");
        student.setSchool(school);

        StudentAccount account = new StudentAccount();
        account.setId(11L);
        account.setStudent(student);
        account.setLoginId("S123456");
        account.setLoginAlias("easy001");
        return account;
    }

    private Teacher buildTeacher(String username, String name, String role) {
        School school = new School();
        school.setId(1L);
        school.setName("测试学校");

        Teacher teacher = new Teacher();
        teacher.setId(201L);
        teacher.setUsername(username);
        teacher.setName(name);
        teacher.setRole(role);
        teacher.setSchool(school);
        return teacher;
    }
}
