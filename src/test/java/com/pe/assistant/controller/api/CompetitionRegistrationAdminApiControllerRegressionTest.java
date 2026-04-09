package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.CompetitionRegistration;
import com.pe.assistant.entity.CompetitionRegistrationStatus;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionRegistrationItemService;
import com.pe.assistant.service.CompetitionRegistrationService;
import com.pe.assistant.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CompetitionRegistrationAdminApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompetitionRegistrationAdminApiControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompetitionRegistrationService registrationService;
    @MockBean
    private CompetitionRegistrationItemService registrationItemService;
    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void createDraftShouldReturnRegistration() throws Exception {
        Teacher teacher = buildTeacher();
        CompetitionRegistration registration = buildRegistration(11L, CompetitionRegistrationStatus.DRAFT);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.createDraft(eq(teacher), eq(100L), eq("????"))).thenReturn(registration);
        when(registrationService.toMap(registration)).thenReturn(Map.of("id", 11L, "status", "DRAFT"));

        mockMvc.perform(post("/api/admin/competition/100/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("remark", "????"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void submitShouldReturnBadRequestWhenServiceRejects() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.submit(eq(teacher), eq(15L)))
                .thenThrow(new IllegalArgumentException("??????????"));

        mockMvc.perform(put("/api/admin/competition/registrations/15/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("??????????"));
    }

    @Test
    void detailShouldExposeItemsAndApprovalRecords() throws Exception {
        Teacher teacher = buildTeacher();
        CompetitionRegistration registration = buildRegistration(18L, CompetitionRegistrationStatus.SUBMITTED);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.requireVisible(eq(teacher), eq(18L))).thenReturn(registration);
        when(registrationService.toMap(registration)).thenReturn(Map.of("id", 18L, "status", "SUBMITTED"));
        when(registrationItemService.listByRegistration(18L)).thenReturn(List.of());
        when(registrationService.listApprovalRecords(18L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/competition/registrations/18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.approvalRecords").isArray());
    }

    @Test
    void detailShouldReturnBadRequestWhenRegistrationIsInvisible() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.requireVisible(eq(teacher), eq(99L)))
                .thenThrow(new IllegalArgumentException("?????????????"));

        mockMvc.perform(get("/api/admin/competition/registrations/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("?????????????"));
    }

    @Test
    void itemsShouldReturnBadRequestWhenRegistrationIsInvisible() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.requireVisible(eq(teacher), eq(88L)))
                .thenThrow(new IllegalArgumentException("?????????????"));

        mockMvc.perform(get("/api/admin/competition/registrations/88/items"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("?????????????"));
    }

    @Test
    void removeItemShouldReturnSuccess() throws Exception {
        Teacher teacher = buildTeacher();
        CompetitionRegistration registration = buildRegistration(18L, CompetitionRegistrationStatus.DRAFT);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.requireVisible(eq(teacher), eq(18L))).thenReturn(registration);

        mockMvc.perform(delete("/api/admin/competition/registrations/18/items/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("????"));
    }

    @Test
    void districtReviewShouldReturnUpdatedStatus() throws Exception {
        Teacher teacher = buildTeacher();
        CompetitionRegistration registration = buildRegistration(20L, CompetitionRegistrationStatus.DISTRICT_APPROVED);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.districtReview(eq(teacher), eq(20L), eq("APPROVE"), eq("????"))).thenReturn(registration);
        when(registrationService.toMap(registration)).thenReturn(Map.of("id", 20L, "status", "DISTRICT_APPROVED"));

        mockMvc.perform(put("/api/admin/competition/registrations/20/district-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE", "comment", "????"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DISTRICT_APPROVED"));
    }

    @Test
    void cityReviewShouldReturnUpdatedStatus() throws Exception {
        Teacher teacher = buildTeacher();
        CompetitionRegistration registration = buildRegistration(21L, CompetitionRegistrationStatus.CITY_APPROVED);
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(registrationService.cityReview(eq(teacher), eq(21L), eq("APPROVE"), eq("????"))).thenReturn(registration);
        when(registrationService.toMap(registration)).thenReturn(Map.of("id", 21L, "status", "CITY_APPROVED"));

        mockMvc.perform(put("/api/admin/competition/registrations/21/city-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE", "comment", "????"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CITY_APPROVED"));
    }

    private Teacher buildTeacher() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setName("???");
        School school = new School();
        school.setId(2L);
        school.setName("????");
        teacher.setSchool(school);
        return teacher;
    }

    private CompetitionRegistration buildRegistration(Long id, CompetitionRegistrationStatus status) {
        CompetitionRegistration registration = new CompetitionRegistration();
        registration.setId(id);
        registration.setStatus(status);
        return registration;
    }
}
