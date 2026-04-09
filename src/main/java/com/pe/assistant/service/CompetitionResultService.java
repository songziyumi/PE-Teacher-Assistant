package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.CompetitionEventRepository;
import com.pe.assistant.repository.CompetitionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionResultService {

    private final CompetitionResultRepository resultRepository;
    private final CompetitionEventRepository competitionEventRepository;
    private final CompetitionService competitionService;
    private final StudentService studentService;
    private final TeacherOperationLogService teacherOperationLogService;
    private final OrganizationScopeService organizationScopeService;

    public List<CompetitionResult> listResults(Teacher teacher, Long competitionId, Long eventId) {
        Competition competition = competitionService.requireVisible(teacher, competitionId);
        if (eventId == null) {
            return resultRepository.findByCompetitionIdOrderByCompetitionEventIdAscRankNoAscIdAsc(competition.getId());
        }
        return resultRepository.findByCompetitionIdAndCompetitionEventIdOrderByRankNoAscIdAsc(competition.getId(), eventId);
    }

    @Transactional
    public CompetitionResult saveResult(Teacher teacher, Long competitionId, Map<String, Object> body) {
        Competition competition = competitionService.requireVisible(teacher, competitionId);
        Long eventId = toLong(body.get("competitionEventId"));
        Long studentId = toLong(body.get("studentId"));
        String resultValue = text(body.get("resultValue"));
        if (eventId == null || studentId == null || resultValue == null) {
            throw new IllegalArgumentException("项目、学生、成绩不能为空");
        }
        CompetitionEvent event = competitionEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("赛事项目不存在"));
        if (event.getCompetition() == null || !event.getCompetition().getId().equals(competition.getId())) {
            throw new IllegalArgumentException("赛事项目不属于当前赛事");
        }
        Student student = studentService.findByIdOptional(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在"));
        CompetitionResult result = resultRepository.findByCompetitionIdAndCompetitionEventIdAndStudentId(competition.getId(), eventId, studentId)
                .orElseGet(CompetitionResult::new);
        result.setCompetition(competition);
        result.setCompetitionEvent(event);
        result.setStudent(student);
        result.setSchool(student.getSchool());
        result.setDistrictOrg(student.getSchool() != null && student.getSchool().getOrganization() != null
                ? student.getSchool().getOrganization().getParent()
                : null);
        result.setResultValue(resultValue);
        result.setRankNo(toInteger(body.get("rankNo")));
        result.setScorePoints(toDecimal(body.get("scorePoints")));
        result.setRecordStatus(text(body.get("recordStatus")) != null ? text(body.get("recordStatus")) : "ENTERED");
        result.setEnteredBy(teacher);
        result.setEnteredAt(LocalDateTime.now());
        CompetitionResult saved = resultRepository.save(result);
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), teacher.getSchool(),
                "COMPETITION_RESULT_SAVE",
                "录入赛事成绩：" + competition.getName(),
                1);
        return saved;
    }

    @Transactional
    public int saveBatch(Teacher teacher, Long competitionId, List<Map<String, Object>> items) {
        int count = 0;
        for (Map<String, Object> item : items) {
            saveResult(teacher, competitionId, item);
            count++;
        }
        return count;
    }

    @Transactional
    public int publish(Teacher teacher, Long competitionId, Long eventId) {
        Competition competition = competitionService.requireVisible(teacher, competitionId);
        List<CompetitionResult> results = eventId == null
                ? resultRepository.findByCompetitionIdOrderByCompetitionEventIdAscRankNoAscIdAsc(competition.getId())
                : resultRepository.findByCompetitionIdAndCompetitionEventIdOrderByRankNoAscIdAsc(competition.getId(), eventId);
        for (CompetitionResult result : results) {
            result.setRecordStatus("PUBLISHED");
            result.setVerifiedBy(teacher);
            result.setVerifiedAt(LocalDateTime.now());
        }
        resultRepository.saveAll(results);
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), teacher.getSchool(),
                "COMPETITION_RESULT_PUBLISH",
                "发布赛事成绩：" + competition.getName(),
                results.size());
        return results.size();
    }

    public Map<String, Object> toMap(CompetitionResult result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", result.getId());
        m.put("competitionId", result.getCompetition() != null ? result.getCompetition().getId() : null);
        m.put("competitionEventId", result.getCompetitionEvent() != null ? result.getCompetitionEvent().getId() : null);
        m.put("competitionEventName", result.getCompetitionEvent() != null ? result.getCompetitionEvent().getName() : null);
        m.put("studentId", result.getStudent() != null ? result.getStudent().getId() : null);
        m.put("studentName", result.getStudent() != null ? result.getStudent().getName() : null);
        m.put("studentNo", result.getStudent() != null ? result.getStudent().getStudentNo() : null);
        m.put("schoolId", result.getSchool() != null ? result.getSchool().getId() : null);
        m.put("schoolName", result.getSchool() != null ? result.getSchool().getName() : null);
        m.put("resultValue", result.getResultValue());
        m.put("rankNo", result.getRankNo());
        m.put("scorePoints", result.getScorePoints());
        m.put("recordStatus", result.getRecordStatus());
        m.put("enteredBy", result.getEnteredBy() != null ? result.getEnteredBy().getName() : null);
        m.put("enteredAt", result.getEnteredAt());
        m.put("verifiedBy", result.getVerifiedBy() != null ? result.getVerifiedBy().getName() : null);
        m.put("verifiedAt", result.getVerifiedAt());
        return m;
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

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text.trim());
        }
        return null;
    }
}