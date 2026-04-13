package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.GradeService;
import com.pe.assistant.service.StudentAccountService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TeacherOperationLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

@Controller
@RequestMapping("/admin/student-accounts")
@RequiredArgsConstructor
public class AdminStudentAccountController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final StudentService studentService;
    private final StudentAccountService studentAccountService;
    private final GradeService gradeService;
    private final ClassService classService;
    private final CurrentUserService currentUserService;
    private final TeacherOperationLogService teacherOperationLogService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long gradeId,
                        @RequestParam(required = false) Long classId,
                        @RequestParam(defaultValue = "") String keyword,
                        @RequestParam(defaultValue = "") String accountStatus,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        School school = currentUserService.getCurrentSchool();
        int safeSize = size > 0 ? Math.min(size, 100) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);

        List<AccountRow> rows = buildRows(school, gradeId, classId, keyword, accountStatus);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        model.addAttribute("rows", rows.subList(fromIndex, toIndex));
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("gradeId", gradeId);
        model.addAttribute("classId", classId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("accountStatus", accountStatus);
        model.addAttribute("grades", gradeService.findAll(school));
        model.addAttribute("classes", classService.findAll(school));
        return "admin/student-accounts";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam(required = false) List<Long> studentIds,
                           @RequestParam(required = false) Long gradeId,
                           @RequestParam(required = false) Long classId,
                           @RequestParam(defaultValue = "") String keyword,
                           @RequestParam(defaultValue = "") String accountStatus,
                           RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        List<Student> targets = resolveTargets(school, studentIds, gradeId, classId, keyword, accountStatus);
        int createdCount = 0;
        for (Student student : targets) {
            if (studentAccountService.findByStudent(student).isEmpty()) {
                studentAccountService.generateAccount(student);
                createdCount++;
            }
        }
        Teacher teacher = currentUserService.getCurrentTeacher();
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), school,
                "STUDENT_ACCOUNT_GENERATE", "\u6279\u91cf\u751f\u6210\u5b66\u751f\u8d26\u53f7", createdCount);
        ra.addFlashAttribute("success", createdCount > 0
                ? "\u5df2\u751f\u6210 " + createdCount + " \u4e2a\u5b66\u751f\u8d26\u53f7\uff0c\u8bf7\u53ca\u65f6\u5bfc\u51fa\u8d26\u53f7\u6e05\u5355"
                : "\u6240\u9009\u5b66\u751f\u90fd\u5df2\u6709\u8d26\u53f7\uff0c\u65e0\u9700\u91cd\u590d\u751f\u6210");
        return redirectToList(gradeId, classId, keyword, accountStatus);
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) List<Long> studentIds,
                                @RequestParam(required = false) Long gradeId,
                                @RequestParam(required = false) Long classId,
                                @RequestParam(defaultValue = "") String keyword,
                                @RequestParam(defaultValue = "") String accountStatus,
                                RedirectAttributes ra) {
        try {
            School school = currentUserService.getCurrentSchool();
            List<Student> targets = resolveTargets(school, studentIds, gradeId, classId, keyword, accountStatus);
            int resetCount = 0;
            for (Student student : targets) {
                if (studentAccountService.findByStudent(student).isPresent()) {
                    studentAccountService.resetPassword(student);
                } else {
                    studentAccountService.generateAccount(student);
                }
                resetCount++;
            }
            Teacher teacher = currentUserService.getCurrentTeacher();
            teacherOperationLogService.log(teacher.getId(), teacher.getName(), school,
                    "STUDENT_ACCOUNT_RESET", "\u6279\u91cf\u91cd\u7f6e\u5b66\u751f\u8d26\u53f7\u521d\u59cb\u5bc6\u7801", resetCount);
            ra.addFlashAttribute("success", resetCount > 0
                    ? "\u5df2\u4e3a " + resetCount + " \u540d\u5b66\u751f\u91cd\u7f6e\u521d\u59cb\u5bc6\u7801\uff0c\u8bf7\u91cd\u65b0\u5bfc\u51fa\u8d26\u53f7\u6e05\u5355"
                    : "\u672a\u627e\u5230\u53ef\u91cd\u7f6e\u7684\u5b66\u751f\u8d26\u53f7");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "\u91cd\u7f6e\u5bc6\u7801\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u5b66\u751f\u8d26\u53f7\u6570\u636e");
        }
        return redirectToList(gradeId, classId, keyword, accountStatus);
    }

    @PostMapping("/enable")
    public String enable(@RequestParam(required = false) List<Long> studentIds,
                         @RequestParam(required = false) Long gradeId,
                         @RequestParam(required = false) Long classId,
                         @RequestParam(defaultValue = "") String keyword,
                         @RequestParam(defaultValue = "") String accountStatus,
                         RedirectAttributes ra) {
        return updateEnabled(studentIds, gradeId, classId, keyword, accountStatus, true, ra);
    }

    @PostMapping("/disable")
    public String disable(@RequestParam(required = false) List<Long> studentIds,
                          @RequestParam(required = false) Long gradeId,
                          @RequestParam(required = false) Long classId,
                          @RequestParam(defaultValue = "") String keyword,
                          @RequestParam(defaultValue = "") String accountStatus,
                          RedirectAttributes ra) {
        return updateEnabled(studentIds, gradeId, classId, keyword, accountStatus, false, ra);
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) List<Long> studentIds,
                       @RequestParam(required = false) Long gradeId,
                       @RequestParam(required = false) Long classId,
                       @RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "") String accountStatus,
                       HttpServletResponse response) throws IOException {
        School school = currentUserService.getCurrentSchool();
        List<Student> targets = resolveTargets(school, studentIds, gradeId, classId, keyword, accountStatus);
        Map<Long, StudentAccount> accounts = studentAccountService.mapByStudents(targets);
        byte[] bytes = exportAccountsXlsx(targets, accounts);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String filename = URLEncoder.encode("\u5b66\u751f\u8d26\u53f7_" + date + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(bytes);

        Teacher teacher = currentUserService.getCurrentTeacher();
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), school,
                "STUDENT_ACCOUNT_EXPORT", "\u5bfc\u51fa\u5b66\u751f\u8d26\u53f7\u6e05\u5355", targets.size());
    }

    private String updateEnabled(List<Long> studentIds,
                                 Long gradeId,
                                 Long classId,
                                 String keyword,
                                 String accountStatus,
                                 boolean enabled,
                                 RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        List<Student> targets = resolveTargets(school, studentIds, gradeId, classId, keyword, accountStatus);
        int changedCount = 0;
        for (Student student : targets) {
            if (studentAccountService.findByStudent(student).isPresent()) {
                studentAccountService.setEnabled(student, enabled);
                changedCount++;
            }
        }
        Teacher teacher = currentUserService.getCurrentTeacher();
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), school,
                enabled ? "STUDENT_ACCOUNT_ENABLE" : "STUDENT_ACCOUNT_DISABLE",
                enabled ? "\u6279\u91cf\u542f\u7528\u5b66\u751f\u8d26\u53f7" : "\u6279\u91cf\u7981\u7528\u5b66\u751f\u8d26\u53f7",
                changedCount);
        ra.addFlashAttribute("success", changedCount > 0
                ? (enabled ? "\u5df2\u542f\u7528 " : "\u5df2\u7981\u7528 ") + changedCount + " \u4e2a\u5b66\u751f\u8d26\u53f7"
                : "\u6240\u9009\u5b66\u751f\u4e2d\u6ca1\u6709\u53ef\u64cd\u4f5c\u7684\u8d26\u53f7");
        return redirectToList(gradeId, classId, keyword, accountStatus);
    }

    private List<AccountRow> buildRows(School school, Long gradeId, Long classId, String keyword, String accountStatus) {
        List<Student> students = findStudents(school, gradeId, classId, keyword);
        Map<Long, StudentAccount> accounts = studentAccountService.mapByStudents(students);
        List<AccountRow> rows = new ArrayList<>();
        for (Student student : students) {
            StudentAccount account = accounts.get(student.getId());
            AccountRow row = toRow(student, account);
            if (matchesAccountStatus(row, accountStatus)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(AccountRow::gradeName, Comparator.nullsLast(String::compareTo))
                .thenComparing(AccountRow::className, Comparator.nullsLast(String::compareTo))
                .thenComparing(AccountRow::studentNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(AccountRow::name));
        return rows;
    }

    private List<Student> resolveTargets(School school,
                                         List<Long> studentIds,
                                         Long gradeId,
                                         Long classId,
                                         String keyword,
                                         String accountStatus) {
        if (studentIds != null && !studentIds.isEmpty()) {
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
        List<AccountRow> rows = buildRows(school, gradeId, classId, keyword, accountStatus);
        List<Student> targets = new ArrayList<>();
        for (AccountRow row : rows) {
            studentService.findByIdOptional(row.studentId()).ifPresent(targets::add);
        }
        return targets;
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
                .toList();
    }

    private AccountRow toRow(Student student, StudentAccount account) {
        SchoolClass schoolClass = student.getSchoolClass();
        String gradeName = schoolClass != null && schoolClass.getGrade() != null ? schoolClass.getGrade().getName() : "";
        String className = schoolClass != null ? schoolClass.getName() : "";
        boolean passwordChanged = account != null && !Boolean.TRUE.equals(account.getPasswordResetRequired());
        return new AccountRow(
                student.getId(),
                student.getName(),
                student.getStudentNo(),
                gradeName,
                className,
                account != null ? account.getLoginId() : "",
                account != null ? account.getLoginAlias() : "",
                studentAccountService.resolveStatus(account),
                account != null && Boolean.TRUE.equals(account.getActivated()),
                passwordChanged,
                account != null ? account.getLastLoginAt() : null,
                account != null ? account.getLastPasswordResetAt() : null,
                account != null && Boolean.TRUE.equals(account.getEnabled()),
                account != null ? account.getIssuedPassword() : ""
        );
    }

    private boolean matchesAccountStatus(AccountRow row, String accountStatus) {
        if (accountStatus == null || accountStatus.isBlank()) {
            return true;
        }
        return switch (accountStatus) {
            case "UNGENERATED" -> "\u672a\u751f\u6210".equals(row.status());
            case "PENDING" -> "\u672a\u6fc0\u6d3b".equals(row.status());
            case "ACTIVE" -> "\u6b63\u5e38".equals(row.status());
            case "DISABLED" -> "\u5df2\u7981\u7528".equals(row.status());
            case "LOCKED" -> "\u5df2\u9501\u5b9a".equals(row.status());
            default -> true;
        };
    }

    private String redirectToList(Long gradeId, Long classId, String keyword, String accountStatus) {
        StringBuilder builder = new StringBuilder("redirect:/admin/student-accounts");
        Map<String, String> query = new LinkedHashMap<>();
        if (gradeId != null) {
            query.put("gradeId", String.valueOf(gradeId));
        }
        if (classId != null) {
            query.put("classId", String.valueOf(classId));
        }
        if (keyword != null && !keyword.isBlank()) {
            query.put("keyword", keyword.trim());
        }
        if (accountStatus != null && !accountStatus.isBlank()) {
            query.put("accountStatus", accountStatus);
        }
        if (!query.isEmpty()) {
            builder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) {
                    builder.append("&");
                }
                builder.append(entry.getKey())
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return builder.toString();
    }

    private byte[] exportAccountsXlsx(List<Student> students, Map<Long, StudentAccount> accounts) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("\u5b66\u751f\u8d26\u53f7");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] headers = {"\u59d3\u540d", "\u5b66\u53f7", "\u5e74\u7ea7", "\u73ed\u7ea7", "\u7cfb\u7edf\u8d26\u53f7", "\u4fbf\u6377\u8d26\u53f7", "\u521d\u59cb\u5bc6\u7801", "\u8d26\u53f7\u72b6\u6001"};
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
                        ? student.getSchoolClass().getGrade().getName() : "");
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

    public record AccountRow(
            Long studentId,
            String name,
            String studentNo,
            String gradeName,
            String className,
            String loginId,
            String loginAlias,
            String status,
            boolean activated,
            boolean passwordChanged,
            LocalDateTime lastLoginAt,
            LocalDateTime lastPasswordResetAt,
            boolean enabled,
            String issuedPassword
    ) {
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }
}
