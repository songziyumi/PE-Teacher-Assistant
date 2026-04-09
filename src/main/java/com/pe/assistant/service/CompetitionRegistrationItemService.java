package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.CompetitionEventRepository;
import com.pe.assistant.repository.CompetitionRegistrationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionRegistrationItemService {

    private final CompetitionRegistrationItemRepository itemRepository;
    private final CompetitionEventRepository competitionEventRepository;
    private final StudentService studentService;

    public List<CompetitionRegistrationItem> listByRegistration(Long registrationId) {
        return itemRepository.findByRegistrationIdOrderByIdAsc(registrationId);
    }

    @Transactional
    public CompetitionRegistrationItem addItem(Teacher teacher, CompetitionRegistration registration, Map<String, Object> body) {
        ensureDraftEditable(teacher, registration);
        Long studentId = toLong(body.get("studentId"));
        Long eventId = toLong(body.get("competitionEventId"));
        if (studentId == null || eventId == null) {
            throw new IllegalArgumentException("学生和赛事项目不能为空");
        }
        Student student = studentService.findByIdOptional(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在"));
        if (student.getSchool() == null || registration.getApplicantSchool() == null
                || !student.getSchool().getId().equals(registration.getApplicantSchool().getId())) {
            throw new IllegalArgumentException("学生不属于该报名学校");
        }
        CompetitionEvent event = competitionEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("赛事项目不存在"));
        if (event.getCompetition() == null || registration.getCompetition() == null
                || !event.getCompetition().getId().equals(registration.getCompetition().getId())) {
            throw new IllegalArgumentException("赛事项目不属于当前赛事");
        }
        if (itemRepository.findByRegistrationIdAndStudentIdAndCompetitionEventId(registration.getId(), studentId, eventId).isPresent()) {
            throw new IllegalArgumentException("该学生已报名该项目");
        }

        CompetitionRegistrationItem item = new CompetitionRegistrationItem();
        item.setRegistration(registration);
        item.setStudent(student);
        item.setCompetitionEvent(event);
        item.setTeamName(text(body.get("teamName")));
        item.setRoleType(text(body.get("roleType")));
        item.setSeedResult(text(body.get("seedResult")));
        item.setQualificationNote(text(body.get("qualificationNote")));
        item.setStatus("NORMAL");
        return itemRepository.save(item);
    }

    @Transactional
    public void removeItem(Teacher teacher, CompetitionRegistration registration, Long itemId) {
        ensureDraftEditable(teacher, registration);
        CompetitionRegistrationItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("报名条目不存在"));
        if (item.getRegistration() == null || !item.getRegistration().getId().equals(registration.getId())) {
            throw new IllegalArgumentException("报名条目不属于当前报名单");
        }
        itemRepository.delete(item);
    }

    public Map<String, Object> toMap(CompetitionRegistrationItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("registrationId", item.getRegistration() != null ? item.getRegistration().getId() : null);
        m.put("studentId", item.getStudent() != null ? item.getStudent().getId() : null);
        m.put("studentName", item.getStudent() != null ? item.getStudent().getName() : null);
        m.put("studentNo", item.getStudent() != null ? item.getStudent().getStudentNo() : null);
        m.put("competitionEventId", item.getCompetitionEvent() != null ? item.getCompetitionEvent().getId() : null);
        m.put("competitionEventName", item.getCompetitionEvent() != null ? item.getCompetitionEvent().getName() : null);
        m.put("teamName", item.getTeamName());
        m.put("roleType", item.getRoleType());
        m.put("seedResult", item.getSeedResult());
        m.put("qualificationNote", item.getQualificationNote());
        m.put("status", item.getStatus());
        return m;
    }

    private void ensureDraftEditable(Teacher teacher, CompetitionRegistration registration) {
        if (teacher == null || teacher.getSchool() == null || registration.getApplicantSchool() == null
                || !teacher.getSchool().getId().equals(registration.getApplicantSchool().getId())) {
            throw new IllegalArgumentException("仅本校可编辑报名条目");
        }
        if (registration.getStatus() != CompetitionRegistrationStatus.DRAFT
                && registration.getStatus() != CompetitionRegistrationStatus.DISTRICT_REJECTED
                && registration.getStatus() != CompetitionRegistrationStatus.CITY_REJECTED) {
            throw new IllegalArgumentException("当前状态不可编辑报名条目");
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }
}