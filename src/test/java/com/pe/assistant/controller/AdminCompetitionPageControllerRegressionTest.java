package com.pe.assistant.controller;

import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.CompetitionLevel;
import com.pe.assistant.entity.CompetitionStatus;
import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.OrganizationType;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionEventService;
import com.pe.assistant.service.CompetitionRegistrationItemService;
import com.pe.assistant.service.CompetitionRegistrationService;
import com.pe.assistant.service.CompetitionResultService;
import com.pe.assistant.service.CompetitionService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TeacherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AdminCompetitionPageController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCompetitionPageControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private CompetitionService competitionService;
    @MockBean
    private CompetitionEventService competitionEventService;
    @MockBean
    private CompetitionRegistrationService competitionRegistrationService;
    @MockBean
    private CompetitionRegistrationItemService competitionRegistrationItemService;
    @MockBean
    private CompetitionResultService competitionResultService;
    @MockBean
    private TeacherService teacherService;
    @MockBean
    private StudentService studentService;

    @Test
    void competitionsShouldFilterByKeywordAndExposeScopeModel() throws Exception {
        Teacher teacher = buildTeacher();
        Organization managedOrg = buildOrg(OrganizationType.DISTRICT, "district");
        Competition match = buildCompetition(1L, "nanshan-games", "NS-2026", CompetitionLevel.DISTRICT, CompetitionStatus.DRAFT);
        Competition skip = buildCompetition(2L, "futian-games", "FT-2026", CompetitionLevel.DISTRICT, CompetitionStatus.READY);

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(currentUserService.getCurrentManagedOrg()).thenReturn(managedOrg);
        when(competitionService.listVisible(eq(teacher))).thenReturn(List.of(match, skip));

        mockMvc.perform(get("/admin/competitions")
                        .param("keyword", "nanshan")
                        .param("level", "DISTRICT")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competitions"))
                .andExpect(model().attributeExists("managedOrgType"))
                .andExpect(model().attribute("selectedLevel", "DISTRICT"))
                .andExpect(model().attribute("selectedStatus", "DRAFT"))
                .andExpect(model().attribute("keyword", "nanshan"))
                .andExpect(model().attribute("competitions", List.of(match)));
    }

    @Test
    void competitionDetailShouldExposeTabsAndPermissions() throws Exception {
        Teacher teacher = buildTeacher();
        Organization managedOrg = buildOrg(OrganizationType.CITY, "city");
        Competition competition = buildCompetition(8L, "city-games", "CITY-2026", CompetitionLevel.CITY, CompetitionStatus.UNDER_REVIEW);
        School school = teacher.getSchool();

        when(currentUserService.getCurrentTeacher()).thenReturn(teacher);
        when(currentUserService.getCurrentManagedOrg()).thenReturn(managedOrg);
        when(currentUserService.getCurrentSchool()).thenReturn(school);
        when(competitionService.requireVisible(eq(teacher), eq(8L))).thenReturn(competition);
        when(competitionEventService.listByCompetition(8L)).thenReturn(List.of());
        when(competitionRegistrationService.listVisibleByCompetition(eq(teacher), eq(8L))).thenReturn(List.of());
        when(competitionResultService.listResults(eq(teacher), eq(8L), eq(6L))).thenReturn(List.of());
        when(studentService.findBySchool(eq(school))).thenReturn(List.of());
        when(teacherService.findAll(eq(school))).thenReturn(List.of());

        mockMvc.perform(get("/admin/competitions/8")
                        .param("tab", "results")
                        .param("eventId", "6"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competition-detail"))
                .andExpect(model().attribute("activeTab", "results"))
                .andExpect(model().attribute("selectedEventId", 6L))
                .andExpect(model().attribute("canReviewAsCity", true))
                .andExpect(model().attribute("canReviewAsDistrict", false))
                .andExpect(model().attribute("canCreateRegistration", true))
                .andExpect(model().attribute("canEnterResults", true));
    }

    private Teacher buildTeacher() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setName("admin");
        School school = new School();
        school.setId(2L);
        school.setName("demo-school");
        teacher.setSchool(school);
        return teacher;
    }

    private Organization buildOrg(OrganizationType type, String name) {
        Organization organization = new Organization();
        organization.setId(10L);
        organization.setType(type);
        organization.setName(name);
        return organization;
    }

    private Competition buildCompetition(Long id, String name, String code, CompetitionLevel level, CompetitionStatus status) {
        Competition competition = new Competition();
        competition.setId(id);
        competition.setName(name);
        competition.setCode(code);
        competition.setLevel(level);
        competition.setStatus(status);
        return competition;
    }
}
