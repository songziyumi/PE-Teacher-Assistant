package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String competitions(Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        model.addAttribute("teacher", teacher);
        model.addAttribute("managedOrg", currentUserService.getCurrentManagedOrg());
        model.addAttribute("competitions", competitionService.listVisible(teacher));
        model.addAttribute("levels", CompetitionLevel.values());
        model.addAttribute("statuses", CompetitionStatus.values());
        return "admin/competitions";
    }

    @GetMapping("/{competitionId}")
    public String competitionDetail(@PathVariable Long competitionId,
                                    @RequestParam(required = false) Long registrationId,
                                    @RequestParam(required = false) Long eventId,
                                    Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Competition competition = competitionService.requireVisible(teacher, competitionId);
        model.addAttribute("teacher", teacher);
        model.addAttribute("competition", competition);
        model.addAttribute("events", competitionEventService.listByCompetition(competitionId));
        model.addAttribute("registrations", competitionRegistrationService.listBySchool(teacher).stream()
                .filter(r -> r.getCompetition() != null && competitionId.equals(r.getCompetition().getId()))
                .toList());
        model.addAttribute("results", competitionResultService.listResults(teacher, competitionId, eventId));
        model.addAttribute("students", currentUserService.getCurrentSchool() != null
                ? studentService.findBySchool(currentUserService.getCurrentSchool())
                : java.util.List.of());
        model.addAttribute("teachers", currentUserService.getCurrentSchool() != null
                ? teacherService.findAll(currentUserService.getCurrentSchool())
                : java.util.List.of());

        if (registrationId != null) {
            CompetitionRegistration registration = competitionRegistrationService.requireVisible(teacher, registrationId);
            model.addAttribute("selectedRegistration", registration);
            model.addAttribute("registrationItems", competitionRegistrationItemService.listByRegistration(registrationId));
            model.addAttribute("approvalRecords", competitionRegistrationService.listApprovalRecords(registrationId));
        }
        model.addAttribute("selectedEventId", eventId);
        return "admin/competition-detail";
    }
}