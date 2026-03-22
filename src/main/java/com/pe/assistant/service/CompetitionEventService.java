package com.pe.assistant.service;

import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.CompetitionEvent;
import com.pe.assistant.repository.CompetitionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionEventService {

    private final CompetitionEventRepository competitionEventRepository;

    public List<CompetitionEvent> listByCompetition(Long competitionId) {
        return competitionEventRepository.findByCompetitionIdOrderBySortOrderAscIdAsc(competitionId);
    }

    @Transactional
    public CompetitionEvent create(Competition competition, Map<String, Object> body) {
        String name = text(body.get("name"));
        String eventCode = text(body.get("eventCode"));
        String teamOrIndividual = text(body.get("teamOrIndividual"));
        if (name == null || eventCode == null || teamOrIndividual == null) {
            throw new IllegalArgumentException("项目名称、编码、类型不能为空");
        }
        CompetitionEvent event = new CompetitionEvent();
        event.setCompetition(competition);
        event.setName(name);
        event.setEventCode(eventCode);
        event.setGenderLimit(text(body.get("genderLimit")));
        event.setGroupRule(text(body.get("groupRule")));
        event.setTeamOrIndividual(teamOrIndividual);
        event.setMaxEntriesPerSchool(asInteger(body.get("maxEntriesPerSchool")));
        event.setMaxEntriesPerDistrict(asInteger(body.get("maxEntriesPerDistrict")));
        event.setSortOrder(asInteger(body.get("sortOrder")) != null ? asInteger(body.get("sortOrder")) : 0);
        event.setEnabled(true);
        return competitionEventRepository.save(event);
    }

    public Map<String, Object> toMap(CompetitionEvent event) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", event.getId());
        m.put("competitionId", event.getCompetition() != null ? event.getCompetition().getId() : null);
        m.put("name", event.getName());
        m.put("eventCode", event.getEventCode());
        m.put("genderLimit", event.getGenderLimit());
        m.put("groupRule", event.getGroupRule());
        m.put("teamOrIndividual", event.getTeamOrIndividual());
        m.put("maxEntriesPerSchool", event.getMaxEntriesPerSchool());
        m.put("maxEntriesPerDistrict", event.getMaxEntriesPerDistrict());
        m.put("sortOrder", event.getSortOrder());
        m.put("enabled", event.getEnabled());
        return m;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }
}