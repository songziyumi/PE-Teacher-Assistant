package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.PageDto;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentAccountScopeStatsService;
import com.pe.assistant.service.StudentAccountService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TeacherOperationLogService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/student-accounts")
@RequiredArgsConstructor
public class AdminStudentAccountApiController {

    private final StudentService studentService;
    private final StudentAccountService studentAccountService;
    private final CurrentUserService currentUserService;
    private final TeacherOperationLogService teacherOperationLogService;
    private final StudentAccountScopeStatsService studentAccountScopeStatsService;

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(defaultValue = "school") String groupBy) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        return ApiResponse.ok(studentAccountScopeStatsService.buildStats(teacher, groupBy));
    }

    @GetMapping
    public ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(defaultValue = "") String accountStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        School school = currentUserService.getCurrentSchool();
        int safeSize = size > 0 ? Math.min(size, 100) : 20;
        int safePage = Math.max(page, 0);

        List<Map<String, Object>> rows = buildRows(school, gradeId, classId, keyword, accountStatus);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        PageDto<Map<String, Object>> dto = new PageDto<>();
        dto.setContent(rows.subList(fromIndex, toIndex));
        dto.setTotalElements(totalElements);
        dto.setTotalPages(totalPages);
        dto.setPage(safePage);
        dto.setSize(safeSize);
        return ApiResponse.ok(dto);
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        School school = currentUserService.getCurrentSchool();
        List<Student> targets = resolveTargets(school, body);
        int createdCount = 0;
        for (Student student : targets) {
            if (studentAccountService.findByStudent(student).isEmpty()) {
                studentAccountService.generateAccount(student);
                createdCount++;
            }
        }
        logAccountAction(school, "STUDENT_ACCOUNT_GENERATE", "批量生成学生账号", createdCount);
        return ApiResponse.ok(buildBatchResult(targets.size(), createdCount, 0));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> body) {
        School school = currentUserService.getCurrentSchool();
        List<Student> targets = resolveTargets(school, body);
        int resetCount = 0;
        for (Student student : targets) {
            if (studentAccountService.findByStudent(student).isPresent()) {
                studentAccountService.resetPassword(student);
            } else {
                studentAccountService.generateAccount(student);
            }
            resetCount++;
        }
        logAccountAction(school, "STUDENT_ACCOUNT_RESET", "批量重置学生账号初始密码", resetCount);
        return ApiResponse.ok(buildBatchResult(targets.size(), resetCount, 0));
    }

    @PostMapping("/enable")
    public ApiResponse<Map<String, Object>> enable(@RequestBody Map<String, Object> body) {
        return updateEnabled(body, true);
    }

    @PostMapping("/disable")
    public ApiResponse<Map<String, Object>> disable(@RequestBody Map<String, Object> body) {
        return updateEnabled(body, false);
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) List<Long> studentIds,
                                         @RequestParam(required = false) Long gradeId,
                                         @RequestParam(required = false) Long classId,
                                         @RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(defaultValue = "") String accountStatus) throws IOException {
        School school = currentUserService.getCurrentSchool();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("studentIds", studentIds);
        params.put("gradeId", gradeId);
        params.put("classId", classId);
        params.put("keyword", keyword);
        params.put("accountStatus", accountStatus);
        List<Student> targets = resolveTargets(school, params);
        Map<Long, StudentAccount> accounts = studentAccountService.mapByStudents(targets);
        byte[] bytes = exportAccountsXlsx(targets, accounts);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String filename = URLEncoder.encode("学生账号_" + date + ".xlsx", StandardCharsets.UTF_8);
        logAccountAction(school, "STUDENT_ACCOUNT_EXPORT", "导出学生账号清单", targets.size());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .body(bytes);
    }

    private ApiResponse<Map<String, Object>> updateEnabled(Map<String, Object> body, boolean enabled) {
        School school = currentUserService.getCurrentSchool();
        List<Student> targets = resolveTargets(school, body);
        int changedCount = 0;
        for (Student student : targets) {
            if (studentAccountService.findByStudent(student).isPresent()) {
                studentAccountService.setEnabled(student, enabled);
                changedCount++;
            }
        }
        logAccountAction(
                school,
                enabled ? "STUDENT_ACCOUNT_ENABLE" : "STUDENT_ACCOUNT_DISABLE",
                enabled ? "批量启用学生账号" : "批量禁用学生账号",
                changedCount);
        return ApiResponse.ok(buildBatchResult(targets.size(), changedCount, 0));
    }

    private List<Map<String, Object>> buildRows(School school,
                                                Long gradeId,
                                                Long classId,
                                                String keyword,
                                                String accountStatus) {
        List<Student> students = findStudents(school, gradeId, classId, keyword);
        Map<Long, StudentAccount> accounts = studentAccountService.mapByStudents(students);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Student student : students) {
            StudentAccount account = accounts.get(student.getId());
            Map<String, Object> row = toRow(student, account);
            if (matchesAccountStatus(row, accountStatus)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.getOrDefault("gradeName", "")))
                .thenComparing(row -> String.valueOf(row.getOrDefault("className", "")))
                .thenComparing(row -> String.valueOf(row.getOrDefault("studentNo", "")))
                .thenComparing(row -> String.valueOf(row.getOrDefault("name", ""))));
        return rows;
    }

    private List<Student> resolveTargets(School school, Map<String, Object> params) {
        List<Long> studentIds = toStudentIds(params.get("studentIds"));
        if (!studentIds.isEmpty()) {
            List<Student> targets = new ArrayList<>();
            for (Long studentId : studentIds) {
                studentService.findByIdOptional(studentId)
                        .filter(student -> student.getSchool() != null
                                && school != null
                                && Objects.equals(student.getSchool().getId(), school.getId()))
                        .ifPresent(targets::add);
            }
            return targets;
        }
        Long gradeId = toLong(params.get("gradeId"));
        Long classId = toLong(params.get("classId"));
        String keyword = params.get("keyword") != null ? String.valueOf(params.get("keyword")) : "";
        String accountStatus = params.get("accountStatus") != null ? String.valueOf(params.get("accountStatus")) : "";
        List<Map<String, Object>> rows = buildRows(school, gradeId, classId, keyword, accountStatus);
        return rows.stream()
                .map(row -> toLong(row.get("studentId")))
                .filter(Objects::nonNull)
                .map(studentService::findByIdOptional)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private List<Student> findStudents(School school, Long gradeId, Long classId, String keyword) {
        List<Student> students = studentService.findListWithFilters(school, classId, gradeId, null, null, null, null, null);
        String value = keyword != null ? keyword.trim() : "";
        if (value.isEmpty()) {
            return students;
        }
        return students.stream()
                .filter(student -> containsIgnoreCase(student.getName(), value)
                        || containsIgnoreCase(student.getStudentNo(), value))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toRow(Student student, StudentAccount account) {
        String gradeName = student.getSchoolClass() != null && student.getSchoolClass().getGrade() != null
                ? student.getSchoolClass().getGrade().getName()
                : "";
        String className = student.getSchoolClass() != null ? student.getSchoolClass().getName() : "";
        boolean passwordChanged = account != null && !Boolean.TRUE.equals(account.getPasswordResetRequired());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("studentId", student.getId());
        row.put("name", student.getName());
        row.put("studentNo", student.getStudentNo());
        row.put("gradeName", gradeName);
        row.put("className", className);
        row.put("loginId", account != null ? account.getLoginId() : "");
        row.put("loginAlias", account != null ? account.getLoginAlias() : "");
        row.put("status", studentAccountService.resolveStatus(account));
        row.put("activated", account != null && Boolean.TRUE.equals(account.getActivated()));
        row.put("passwordChanged", passwordChanged);
        row.put("lastLoginAt", account != null && account.getLastLoginAt() != null ? account.getLastLoginAt().toString() : null);
        row.put("lastPasswordResetAt", account != null && account.getLastPasswordResetAt() != null
                ? account.getLastPasswordResetAt().toString()
                : null);
        row.put("enabled", account != null && Boolean.TRUE.equals(account.getEnabled()));
        return row;
    }

    private boolean matchesAccountStatus(Map<String, Object> row, String accountStatus) {
        if (accountStatus == null || accountStatus.isBlank()) {
            return true;
        }
        String status = String.valueOf(row.get("status"));
        return switch (accountStatus) {
            case "UNGENERATED" -> "未生成".equals(status);
            case "PENDING" -> "未激活".equals(status);
            case "ACTIVE" -> "正常".equals(status);
            case "DISABLED" -> "已禁用".equals(status);
            case "LOCKED" -> "已锁定".equals(status);
            default -> true;
        };
    }

    private Map<String, Object> buildBatchResult(int totalCount, int successCount, int failedCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("failedItems", List.of());
        return result;
    }

    private void logAccountAction(School school, String action, String description, int targetCount) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), school, action, description, targetCount);
    }

    private List<Long> toStudentIds(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::toLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private byte[] exportAccountsXlsx(List<Student> students, Map<Long, StudentAccount> accounts) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("学生账号");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] headers = {"姓名", "学号", "年级", "班级", "系统账号", "便捷账号", "初始密码", "账号状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Student student : students) {
                StudentAccount account = accounts.get(student.getId());
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(student.getName() != null ? student.getName() : "");
                row.createCell(1).setCellValue(student.getStudentNo() != null ? student.getStudentNo() : "");
                row.createCell(2).setCellValue(student.getSchoolClass() != null && student.getSchoolClass().getGrade() != null
                        ? student.getSchoolClass().getGrade().getName()
                        : "");
                row.createCell(3).setCellValue(student.getSchoolClass() != null ? student.getSchoolClass().getName() : "");
                row.createCell(4).setCellValue(account != null && account.getLoginId() != null ? account.getLoginId() : "");
                row.createCell(5).setCellValue(account != null && account.getLoginAlias() != null ? account.getLoginAlias() : "");
                row.createCell(6).setCellValue(account != null && account.getIssuedPassword() != null ? account.getIssuedPassword() : "");
                row.createCell(7).setCellValue(studentAccountService.resolveStatus(account));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
