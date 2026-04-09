package com.pe.assistant.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionResultService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CompetitionResultAdminApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompetitionResultAdminApiControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompetitionResultService competitionResultService;
    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void listShouldReturnResults() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.listResults(eq(teacher), eq(20L), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/competition/20/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listShouldForwardEventFilter() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.listResults(eq(teacher), eq(20L), eq(8L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/competition/20/results").param("eventId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void saveShouldReturnBadRequestWhenServiceRejects() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.saveResult(eq(teacher), eq(20L), org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new IllegalArgumentException("missing required result fields"));

        mockMvc.perform(post("/api/admin/competition/20/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("missing required result fields"));
    }

    @Test
    void saveBatchShouldReturnSavedCount() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.saveBatch(eq(teacher), eq(20L), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(2);

        mockMvc.perform(post("/api/admin/competition/20/results/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("competitionEventId", 1, "studentId", 2, "resultValue", "12.10"),
                                Map.of("competitionEventId", 1, "studentId", 3, "resultValue", "12.30")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.savedCount").value(2));
    }

    @Test
    void publishShouldReturnCount() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.publish(eq(teacher), eq(20L), eq(null))).thenReturn(5);

        mockMvc.perform(put("/api/admin/competition/20/results/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.publishedCount").value(5));
    }

    @Test
    void publishShouldRespectEventFilter() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.publish(eq(teacher), eq(20L), eq(6L))).thenReturn(3);

        mockMvc.perform(put("/api/admin/competition/20/results/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("eventId", 6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.publishedCount").value(3));
    }

    @Test
    void saveBatchShouldReturnBadRequestWhenRejected() throws Exception {
        Teacher teacher = buildTeacher();
        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(competitionResultService.saveBatch(eq(teacher), eq(20L), org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new IllegalArgumentException("invalid batch payload"));

        mockMvc.perform(post("/api/admin/competition/20/results/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("invalid batch payload"));
    }

    private Teacher buildTeacher() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setName("admin");
        return teacher;
    }
}
