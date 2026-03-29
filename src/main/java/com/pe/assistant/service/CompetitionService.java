package com.pe.assistant.service;

import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.CompetitionLevel;
import com.pe.assistant.entity.CompetitionStatus;
import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final OrganizationScopeService organizationScopeService;

    public List<Competition> listVisible(Teacher teacher) {
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        if (managedOrg == null || managedOrg.getId() == null) {
            return List.of();
        }
        return competitionRepository.findAll().stream()
                .filter(c -> isVisibleToOrg(managedOrg, c.getHostOrg()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    public Competition requireVisible(Teacher teacher, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        if (managedOrg == null || !isVisibleToOrg(managedOrg, competition.getHostOrg())) {
            throw new IllegalArgumentException("No permission to view this competition");
        }
        return competition;
    }

    public Competition requireHostManaged(Teacher teacher, Long competitionId) {
        Competition competition = requireVisible(teacher, competitionId);
        if (!canManageCompetition(teacher, competition)) {
            throw new IllegalArgumentException("Only host organization can maintain this competition");
        }
        return competition;
    }

    @Transactional
    public Competition create(Teacher teacher, Map<String, Object> body) {
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        if (managedOrg == null) {
            throw new IllegalArgumentException("Current manager is not bound to any organization");
        }

        String name = text(body.get("name"));
        String code = text(body.get("code"));
        String level = text(body.get("level"));
        if (name == null || code == null || level == null) {
            throw new IllegalArgumentException("Competition name, code and level are required");
        }
        if (competitionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Competition code already exists");
        }

        Competition competition = new Competition();
        competition.setName(name);
        competition.setCode(code);
        competition.setLevel(CompetitionLevel.valueOf(level));
        competition.setHostOrg(managedOrg);
        competition.setUndertakeOrg(managedOrg);
        competition.setSchoolYear(text(body.get("schoolYear")));
        competition.setTerm(text(body.get("term")));
        competition.setDescription(text(body.get("description")));
        competition.setStatus(CompetitionStatus.DRAFT);
        competition.setRegistrationStartAt(parseDateTime(body.get("registrationStartAt")));
        competition.setRegistrationEndAt(parseDateTime(body.get("registrationEndAt")));
        competition.setCompetitionStartAt(parseDateTime(body.get("competitionStartAt")));
        competition.setCompetitionEndAt(parseDateTime(body.get("competitionEndAt")));
        competition.setCreatedBy(teacher);
        return competitionRepository.saveAndFlush(competition);
    }

    @Transactional
    public Competition updateStatus(Teacher teacher, Long competitionId, String targetStatus) {
        Competition competition = requireHostManaged(teacher, competitionId);
        CompetitionStatus status = CompetitionStatus.valueOf(targetStatus);
        competition.setStatus(status);
        return competitionRepository.save(competition);
    }

    public boolean canManageCompetition(Teacher teacher, Competition competition) {
        if (competition == null || competition.getHostOrg() == null || competition.getHostOrg().getId() == null) {
            return false;
        }
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        return managedOrg != null
                && managedOrg.getId() != null
                && managedOrg.getId().equals(competition.getHostOrg().getId());
    }

    private boolean isVisibleToOrg(Organization managedOrg, Organization hostOrg) {
        if (managedOrg == null || hostOrg == null) {
            return false;
        }
        return organizationScopeService.containsOrg(managedOrg, hostOrg)
                || organizationScopeService.containsOrg(hostOrg, managedOrg);
    }

    public Map<String, Object> toMap(Competition competition) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", competition.getId());
        m.put("name", competition.getName());
        m.put("code", competition.getCode());
        m.put("level", competition.getLevel());
        m.put("status", competition.getStatus());
        m.put("hostOrgId", competition.getHostOrg() != null ? competition.getHostOrg().getId() : null);
        m.put("hostOrgName", competition.getHostOrg() != null ? competition.getHostOrg().getName() : null);
        m.put("schoolYear", competition.getSchoolYear());
        m.put("term", competition.getTerm());
        m.put("registrationStartAt", competition.getRegistrationStartAt());
        m.put("registrationEndAt", competition.getRegistrationEndAt());
        m.put("competitionStartAt", competition.getCompetitionStartAt());
        m.put("competitionEndAt", competition.getCompetitionEndAt());
        m.put("description", competition.getDescription());
        return m;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private LocalDateTime parseDateTime(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return LocalDate.parse(text).atStartOfDay();
        }
    }
}
