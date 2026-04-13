package com.pe.assistant.controller;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.CourseRequestAuditRepository;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.AccountEmailService;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeacherProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeacherProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherRepository teacherRepository;
    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private AccountEmailService accountEmailService;
    @MockBean
    private ClassService classService;
    @MockBean
    private AttendanceRepository attendanceRepository;
    @MockBean
    private MessageService messageService;
    @MockBean
    private CourseRequestAuditRepository courseRequestAuditRepository;

    @Test
    void requestEmailBindShouldRedirectBackToProfile() throws Exception {
        Teacher teacher = new Teacher();
        teacher.setId(21L);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);

        mockMvc.perform(post("/teacher/profile/email-bind/request")
                        .param("email", "teacher@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/teacher/profile*"));

        verify(accountEmailService).requestTeacherEmailBind(eq(teacher), eq("teacher@example.com"), any(), any());
    }

    @Test
    void updateEmailNotifyShouldRedirectBackToProfile() throws Exception {
        Teacher teacher = new Teacher();
        teacher.setId(22L);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);

        mockMvc.perform(post("/teacher/profile/email-notify")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/teacher/profile*"));

        verify(accountEmailService).updateTeacherNotifyEnabled(teacher, true);
    }
}
