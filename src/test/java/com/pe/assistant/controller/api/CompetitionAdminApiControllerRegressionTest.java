package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.CompetitionEvent;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionEventService;
import com.pe.assistant.service.CompetitionService;
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

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CompetitionAdminApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompetitionAdminApiControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompetitionService competitionService;
    @MockBean
    private CompetitionEventService competitionEventService;
    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void listShouldReturnCompetitionArray() throws Exception {
        Teacher teacher = buildTeacher();
        Competition competition = new Competition();
        competition.setId(12L);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.listVisible(eq(teacher))).thenReturn(List.of(competition));
        when(competitionService.toMap(eq(competition))).thenReturn(Map.of("id", 12L, "name", "city-games"));

        mockMvc.perform(get("/api/admin/competition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(12))
                .andExpect(jsonPath("$.data[0].name").value("city-games"));
    }

    @Test
    void createShouldReturnCreatedCompetition() throws Exception {
        Teacher teacher = buildTeacher();
        Competition competition = new Competition();
        competition.setId(21L);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.create(eq(teacher), anyMap())).thenReturn(competition);
        when(competitionService.toMap(eq(competition))).thenReturn(Map.of("id", 21L, "status", "DRAFT"));

        mockMvc.perform(post("/api/admin/competition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "district-spring-games",
                                "code", "DISTRICT-2026",
                                "level", "DISTRICT"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void updateStatusShouldReturnBadRequestWhenRejected() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.updateStatus(eq(teacher), eq(21L), eq("READY")))
                .thenThrow(new IllegalArgumentException("invalid status transition"));

        mockMvc.perform(put("/api/admin/competition/21/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "READY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("invalid status transition"));
    }

    @Test
    void detailShouldReturnCompetitionMap() throws Exception {
        Teacher teacher = buildTeacher();
        Competition competition = new Competition();
        competition.setId(30L);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.requireVisible(eq(teacher), eq(30L))).thenReturn(competition);
        when(competitionService.toMap(eq(competition))).thenReturn(Map.of("id", 30L, "name", "special-meet"));

        mockMvc.perform(get("/api/admin/competition/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.name").value("special-meet"));
    }

    @Test
    void listEventsShouldReturnArray() throws Exception {
        Teacher teacher = buildTeacher();
        Competition competition = new Competition();
        competition.setId(40L);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.requireVisible(eq(teacher), eq(40L))).thenReturn(competition);
        when(competitionEventService.listByCompetition(eq(40L))).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/competition/40/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createEventShouldReturnCreatedEvent() throws Exception {
        Teacher teacher = buildTeacher();
        Competition competition = new Competition();
        competition.setId(41L);
        CompetitionEvent event = new CompetitionEvent();
        event.setId(9L);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.requireHostManaged(eq(teacher), eq(41L))).thenReturn(competition);
        when(competitionEventService.create(eq(competition), anyMap())).thenReturn(event);
        when(competitionEventService.toMap(eq(event))).thenReturn(Map.of("id", 9L, "name", "run-100m", "maxEntriesPerGrade", 3));

        mockMvc.perform(post("/api/admin/competition/41/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "run-100m",
                                "eventCode", "RUN100",
                                "maxEntriesPerGrade", 3
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.name").value("run-100m"))
                .andExpect(jsonPath("$.data.maxEntriesPerGrade").value(3));
    }

    @Test
    void createEventShouldReturnBadRequestWhenRejected() throws Exception {
        Teacher teacher = buildTeacher();
        Competition competition = new Competition();
        competition.setId(41L);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionService.requireHostManaged(eq(teacher), eq(41L))).thenReturn(competition);
        when(competitionEventService.create(eq(competition), anyMap()))
                .thenThrow(new IllegalArgumentException("invalid event payload"));

        mockMvc.perform(post("/api/admin/competition/41/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "run-100m",
                                "eventCode", "RUN100"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("invalid event payload"));
    }

    private Teacher buildTeacher() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setName("admin");
        return teacher;
    }
}
