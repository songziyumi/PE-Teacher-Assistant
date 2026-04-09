package com.pe.assistant.controller;

import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.CompetitionLevel;
import com.pe.assistant.entity.CompetitionRegistration;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/competitions")
@RequiredArgsConstructor
public class AdminCompetitionPageController {

    private final CurrentUserService currentUserService;
    private final CompetitionService competitionService;
    private final CompetitionEventService competitionEventService;
    private final CompetitionRegistrationService competitionRegistrationService;
    private final CompetitionRegistrationItemService competitionRegistrationItemService;
    private final CompetitionResultService competitionResultService;
    private final TeacherService teacherService;
    private final StudentService studentService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping
    public String competitions(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String level,
                               @RequestParam(required = false) String status,
                               Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Organization managedOrg = currentUserService.getCurrentManagedOrg();
        List<Competition> competitions = competitionService.listVisible(teacher).stream()
                .filter(c -> isKeywordMatch(c, keyword))
                .filter(c -> level == null || level.isBlank() || c.getLevel().name().equals(level))
                .filter(c -> status == null || status.isBlank() || c.getStatus().name().equals(status))
                .toList();

        model.addAttribute("teacher", teacher);
        model.addAttribute("managedOrg", managedOrg);
        model.addAttribute("managedOrgType", managedOrg != null ? managedOrg.getType() : null);
        model.addAttribute("competitions", competitions);
        model.addAttribute("levels", CompetitionLevel.values());
        model.addAttribute("statuses", CompetitionStatus.values());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedLevel", level);
        model.addAttribute("selectedStatus", status);
        return "admin/competitions";
    }

    @GetMapping("/{competitionId}")
    public String competitionDetail(@PathVariable Long competitionId,
                                    @RequestParam(required = false) Long registrationId,
                                    @RequestParam(required = false) Long eventId,
                                    @RequestParam(required = false, defaultValue = "events") String tab,
                                    Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Competition competition = competitionService.requireVisible(teacher, competitionId);
        Organization managedOrg = currentUserService.getCurrentManagedOrg();
        OrganizationType managedOrgType = managedOrg != null ? managedOrg.getType() : null;

        model.addAttribute("teacher", teacher);
        model.addAttribute("managedOrg", managedOrg);
        model.addAttribute("managedOrgType", managedOrgType);
        model.addAttribute("competition", competition);
        model.addAttribute("competitionStatuses", CompetitionStatus.values());
        model.addAttribute("events", competitionEventService.listByCompetition(competitionId));
        model.addAttribute("registrations", competitionRegistrationService.listVisibleByCompetition(teacher, competitionId));
        model.addAttribute("results", competitionResultService.listResults(teacher, competitionId, eventId));
        model.addAttribute("students", currentUserService.getCurrentSchool() != null
                ? studentService.findBySchool(currentUserService.getCurrentSchool())
                : List.of());
        model.addAttribute("teachers", currentUserService.getCurrentSchool() != null
                ? teacherService.findAll(currentUserService.getCurrentSchool())
                : List.of());

        if (registrationId != null) {
            CompetitionRegistration registration = competitionRegistrationService.requireVisible(teacher, registrationId);
            model.addAttribute("selectedRegistration", registration);
            model.addAttribute("registrationItems", competitionRegistrationItemService.listByRegistration(registrationId));
            model.addAttribute("approvalRecords", competitionRegistrationService.listApprovalRecords(registrationId));
        }

        model.addAttribute("activeTab", normalizeTab(tab));
        model.addAttribute("selectedEventId", eventId);
        model.addAttribute("canManageCompetition", competitionService.canManageCompetition(teacher, competition));
        model.addAttribute("canCreateRegistration", currentUserService.getCurrentSchool() != null);
        model.addAttribute("canReviewAsDistrict", managedOrgType == OrganizationType.DISTRICT);
        model.addAttribute("canReviewAsCity", managedOrgType == OrganizationType.CITY);
        model.addAttribute("canEnterResults", currentUserService.getCurrentSchool() != null);
        return "admin/competition-detail";
    }

    private boolean isKeywordMatch(Competition competition, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase();
        return containsIgnoreCase(competition.getName(), value)
                || containsIgnoreCase(competition.getCode(), value);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && keyword != null && source.toLowerCase().contains(keyword);
    }

    private String normalizeTab(String tab) {
        return switch (tab) {
            case "registrations", "results" -> tab;
            default -> "events";
        };
    }
}
