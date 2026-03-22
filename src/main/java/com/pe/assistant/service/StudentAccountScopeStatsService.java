package com.pe.assistant.service;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentAccountScopeStatsService {

    private final OrganizationScopeService organizationScopeService;
    private final SchoolRepository schoolRepository;
    private final StudentService studentService;
    private final StudentAccountService studentAccountService;

    public Map<String, Object> buildStats(Teacher teacher, String groupBy) {
        String effectiveGroupBy = normalizeGroupBy(groupBy);
        if (teacher == null) {
            return emptyResult(effectiveGroupBy);
        }
        Set<Long> schoolIds = organizationScopeService.listSchoolIdsInScope(teacher);
        if (schoolIds.isEmpty() && teacher.getSchool() != null && teacher.getSchool().getId() != null) {
            schoolIds = Set.of(teacher.getSchool().getId());
        }

        List<School> schools = schoolRepository.findAllById(schoolIds).stream()
                .filter(Objects::nonNull)
                .toList();

        List<Map<String, Object>> items = switch (effectiveGroupBy) {
            case "district" -> buildDistrictItems(schools);
            case "school" -> buildSchoolItems(schools);
            default -> buildSchoolItems(schools);
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupBy", effectiveGroupBy);
        result.put("summary", summarize(items));
        result.put("items", items);
        return result;
    }

    private List<Map<String, Object>> buildSchoolItems(List<School> schools) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (School school : schools) {
            items.add(buildSchoolStatsItem(school));
        }
        return items;
    }

    private List<Map<String, Object>> buildDistrictItems(List<School> schools) {
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (School school : schools) {
            Map<String, Object> schoolItem = buildSchoolStatsItem(school);
            Organization schoolOrg = school.getOrganization();
            Organization districtOrg = schoolOrg != null ? schoolOrg.getParent() : null;
            Long districtId = districtOrg != null ? districtOrg.getId() : -1L;
            String districtName = districtOrg != null ? districtOrg.getName() : "未分配区县";

            Map<String, Object> bucket = grouped.computeIfAbsent(districtId, id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orgId", districtId > 0 ? districtId : null);
                m.put("orgName", districtName);
                m.put("schoolCount", 0);
                m.put("studentCount", 0);
                m.put("accountCount", 0);
                m.put("activatedCount", 0);
                m.put("disabledCount", 0);
                m.put("lockedCount", 0);
                m.put("missingCount", 0);
                return m;
            });

            bucket.put("schoolCount", asInt(bucket.get("schoolCount")) + 1);
            bucket.put("studentCount", asInt(bucket.get("studentCount")) + asInt(schoolItem.get("studentCount")));
            bucket.put("accountCount", asInt(bucket.get("accountCount")) + asInt(schoolItem.get("accountCount")));
            bucket.put("activatedCount", asInt(bucket.get("activatedCount")) + asInt(schoolItem.get("activatedCount")));
            bucket.put("disabledCount", asInt(bucket.get("disabledCount")) + asInt(schoolItem.get("disabledCount")));
            bucket.put("lockedCount", asInt(bucket.get("lockedCount")) + asInt(schoolItem.get("lockedCount")));
            bucket.put("missingCount", asInt(bucket.get("missingCount")) + asInt(schoolItem.get("missingCount")));
        }

        for (Map<String, Object> item : grouped.values()) {
            item.put("activationRate", calcRate(asInt(item.get("activatedCount")), asInt(item.get("studentCount"))));
        }
        return new ArrayList<>(grouped.values());
    }

    private Map<String, Object> buildSchoolStatsItem(School school) {
        List<Student> students = studentService.findBySchool(school);
        Map<Long, StudentAccount> accountMap = studentAccountService.mapByStudents(students);

        int studentCount = students.size();
        int accountCount = 0;
        int activatedCount = 0;
        int disabledCount = 0;
        int lockedCount = 0;
        int missingCount = 0;

        for (Student student : students) {
            StudentAccount account = accountMap.get(student.getId());
            if (account == null) {
                missingCount++;
                continue;
            }
            accountCount++;
            if (Boolean.FALSE.equals(account.getEnabled())) {
                disabledCount++;
            }
            if (studentAccountService.isLocked(account)) {
                lockedCount++;
            }
            if (Boolean.TRUE.equals(account.getActivated()) && !studentAccountService.requiresPasswordChange(account)) {
                activatedCount++;
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("schoolId", school.getId());
        item.put("schoolName", school.getName());
        item.put("orgId", school.getOrganization() != null ? school.getOrganization().getId() : null);
        item.put("orgName", school.getOrganization() != null ? school.getOrganization().getName() : null);
        item.put("studentCount", studentCount);
        item.put("accountCount", accountCount);
        item.put("activatedCount", activatedCount);
        item.put("disabledCount", disabledCount);
        item.put("lockedCount", lockedCount);
        item.put("missingCount", missingCount);
        item.put("activationRate", calcRate(activatedCount, studentCount));
        return item;
    }

    private Map<String, Object> summarize(List<Map<String, Object>> items) {
        int schoolCount = 0;
        int studentCount = 0;
        int accountCount = 0;
        int activatedCount = 0;
        int disabledCount = 0;
        int lockedCount = 0;
        int missingCount = 0;

        for (Map<String, Object> item : items) {
            if (item.containsKey("schoolId")) {
                schoolCount++;
            } else {
                schoolCount += asInt(item.get("schoolCount"));
            }
            studentCount += asInt(item.get("studentCount"));
            accountCount += asInt(item.get("accountCount"));
            activatedCount += asInt(item.get("activatedCount"));
            disabledCount += asInt(item.get("disabledCount"));
            lockedCount += asInt(item.get("lockedCount"));
            missingCount += asInt(item.get("missingCount"));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schoolCount", schoolCount);
        summary.put("studentCount", studentCount);
        summary.put("accountCount", accountCount);
        summary.put("activatedCount", activatedCount);
        summary.put("disabledCount", disabledCount);
        summary.put("lockedCount", lockedCount);
        summary.put("missingCount", missingCount);
        summary.put("activationRate", calcRate(activatedCount, studentCount));
        return summary;
    }

    private Map<String, Object> emptyResult(String groupBy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupBy", groupBy);
        result.put("summary", summarize(List.of()));
        result.put("items", List.of());
        return result;
    }

    private String normalizeGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return "school";
        }
        return "district".equalsIgnoreCase(groupBy.trim()) ? "district" : "school";
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double calcRate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 10000D) / denominator) / 10000D;
    }
}