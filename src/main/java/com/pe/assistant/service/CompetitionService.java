package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        List<Long> orgIds = organizationScopeService.listOrgIdsInSubtree(managedOrg, null).stream().toList();
        return competitionRepository.findByHostOrgIdInOrderByCreatedAtDesc(orgIds);
    }

    public Competition requireVisible(Teacher teacher, Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("赛事不存在"));
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        if (managedOrg == null || !organizationScopeService.containsOrg(managedOrg, competition.getHostOrg())) {
            throw new IllegalArgumentException("无权查看该赛事");
        }
        return competition;
    }

    @Transactional
    public Competition create(Teacher teacher, Map<String, Object> body) {
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        if (managedOrg == null) {
            throw new IllegalArgumentException("当前管理员未绑定组织范围");
        }

        String name = text(body.get("name"));
        String code = text(body.get("code"));
        String level = text(body.get("level"));
        if (name == null || code == null || level == null) {
            throw new IllegalArgumentException("赛事名称、编码、级别不能为空");
        }
        if (competitionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("赛事编码已存在");
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
        return competitionRepository.save(competition);
    }

    @Transactional
    public Competition updateStatus(Teacher teacher, Long competitionId, String targetStatus) {
        Competition competition = requireVisible(teacher, competitionId);
        CompetitionStatus status = CompetitionStatus.valueOf(targetStatus);
        competition.setStatus(status);
        return competitionRepository.save(competition);
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
        return text == null ? null : LocalDateTime.parse(text);
    }
}