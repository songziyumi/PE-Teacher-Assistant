package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.PageDto;
import com.pe.assistant.entity.*;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseOverflowAuditRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.CourseRequestAuditRepository;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.TeacherOperationLogRepository;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final StudentService studentService;
    private final ClassService classService;
    private final GradeService gradeService;
    private final PhysicalTestService physicalTestService;
    private final TermGradeService termGradeService;
    private final CurrentUserService currentUserService;
    private final AttendanceService attendanceService;
    private final MessageService messageService;
    private final TeacherPermissionService teacherPermissionService;
    private final TeacherOperationLogRepository teacherOperationLogRepository;
    private final CourseRequestAuditRepository courseRequestAuditRepository;
    private final CourseOverflowAuditRepository courseOverflowAuditRepository;
    private final SelectionEventRepository selectionEventRepository;
    private final CourseRepository courseRepository;
    private final CourseSelectionRepository courseSelectionRepository;
    private final CourseClassCapacityRepository courseClassCapacityRepository;

    // ===== 仪表盘统计 =====

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        School school = currentUserService.getCurrentSchool();
        long studentCount = studentService.findWithFilters(school, null, null, null, null, null, null, 0, 1)
                .getTotalElements();
        long classCount = classService.findAll(school).size();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentCount", studentCount);
        data.put("classCount", classCount);
        return ApiResponse.ok(data);
    }

    @GetMapping("/course-selection-diagnostics")
    public ApiResponse<Map<String, Object>> courseSelectionDiagnostics(
            @RequestParam(required = false) Long eventId) {
        School school = currentUserService.getCurrentSchool();
        SelectionEvent event = resolveDiagnosticEvent(school, eventId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        if (event == null) {
            result.put("event", null);
            result.put("overview", Map.of(
                    "courseCount", 0,
                    "activeCourseCount", 0,
                    "selectionCount", 0,
                    "confirmedCount", 0));
            result.put("health", Map.of(
                    "severity", "WARN",
                    "issueCount", 1,
                    "issues", List.of(Map.of(
                            "level", "WARN",
                            "code", "NO_EVENT",
                            "message", "当前学校暂无可诊断的选课活动"))));
            result.put("hotCourses", List.of());
            result.put("recentActivity", Map.of(
                    "operationLogCount24h", 0,
                    "requestAuditCount24h", 0,
                    "overflowAuditCount24h", 0,
                    "entries", List.of()));
            return ApiResponse.ok(result);
        }

        List<Course> courses = courseRepository.findByEventOrderByNameAsc(event);
        List<CourseSelection> selections = courseSelectionRepository.findByEvent(event);
        List<TeacherOperationLog> opLogs =
                teacherOperationLogRepository.findTop200BySchool_IdOrderByOperatedAtDesc(school.getId());
        List<CourseRequestAudit> requestAudits =
                courseRequestAuditRepository.findTop200BySchool_IdOrderByHandledAtDesc(school.getId());
        List<CourseOverflowAudit> overflowAudits =
                courseOverflowAuditRepository.findTop200BySchoolIdOrderByCreatedAtDesc(school.getId());

        result.put("event", buildDiagnosticEventMap(event));
        result.put("overview", buildDiagnosticOverview(event, courses, selections, opLogs, requestAudits, overflowAudits));
        result.put("health", buildDiagnosticHealth(event, courses, selections, overflowAudits));
        result.put("hotCourses", buildHotCourseList(courses, selections));
        result.put("recentActivity", buildRecentActivity(event, courses, opLogs, requestAudits, overflowAudits));
        return ApiResponse.ok(result);
    }

    // ===== 年级/班级 =====

    @GetMapping("/grades")
    public ApiResponse<List<Grade>> grades() {
        School school = currentUserService.getCurrentSchool();
        return ApiResponse.ok(gradeService.findAll(school));
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes(
            @RequestParam(required = false) Long gradeId) {
        School school = currentUserService.getCurrentSchool();
        List<SchoolClass> list = classService.findAll(school);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SchoolClass c : list) {
            if (gradeId != null && (c.getGrade() == null || !gradeId.equals(c.getGrade().getId()))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("gradeName", c.getGrade() != null ? c.getGrade().getName() : null);
            m.put("gradeId", c.getGrade() != null ? c.getGrade().getId() : null);
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/academic-years")
    public ApiResponse<List<String>> academicYears() {
        School school = currentUserService.getCurrentSchool();
        return ApiResponse.ok(termGradeService.findDistinctAcademicYears(school));
    }

    @GetMapping("/elective-classes")
    public ApiResponse<List<Map<String, Object>>> electiveClasses() {
        School school = currentUserService.getCurrentSchool();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SchoolClass c : classService.findAll(school)) {
            if (!"选修课".equals(c.getType())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("gradeName", c.getGrade() != null ? c.getGrade().getName() : null);
            m.put("gradeId", c.getGrade() != null ? c.getGrade().getId() : null);
            String storedName = c.getGrade() != null
                    ? c.getGrade().getName() + "/" + c.getName()
                    : c.getName();
            m.put("storedName", storedName);
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    // ===== 学生 CRUD =====

    @GetMapping("/students")
    public ApiResponse<PageDto<Student>> students(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        School school = currentUserService.getCurrentSchool();
        String kw = keyword.isBlank() ? null : keyword;
        Page<Student> studentPage = studentService.findWithFilters(school, classId, gradeId, kw, kw, null, null, page, size);
        studentService.syncElectiveClassesForStudents(studentPage.getContent());
        return ApiResponse.ok(PageDto.of(studentPage));
    }

    @PostMapping("/students/save")
    public ApiResponse<String> saveStudent(@RequestBody Map<String, Object> body) {
        School school = currentUserService.getCurrentSchool();
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        String name = (String) body.get("name");
        String gender = (String) body.get("gender");
        String studentNo = (String) body.get("studentNo");
        String idCard = (String) body.get("idCard");
        String electiveClass = (String) body.get("electiveClass");
        String studentStatus = (String) body.get("studentStatus");
        Long classId = body.get("classId") != null ? Long.valueOf(body.get("classId").toString()) : null;
        if (id == null) {
            studentService.create(name, gender, studentNo, idCard, electiveClass, classId, school, studentStatus);
        } else {
            studentService.update(id, name, gender, studentNo, idCard, electiveClass, classId, studentStatus);
        }
        return ApiResponse.ok("保存成功", null);
    }

    @PostMapping("/students/batch-update-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchUpdateStudentStatus(
            @RequestBody Map<String, Object> body) {
        try {
            School school = currentUserService.getCurrentSchool();
            List<Long> studentIds = toStudentIds(body.get("studentIds"));
            if (studentIds.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要批量修改的学生"));
            }
            String studentStatus = (String) body.get("studentStatus");
            StudentService.BatchStudentOperationResult result =
                    studentService.batchUpdateStudentStatus(school, studentIds, studentStatus);
            return ResponseEntity.ok(ApiResponse.ok(buildBatchStudentResult(result)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/students/batch-update-elective-class")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchUpdateStudentElectiveClass(
            @RequestBody Map<String, Object> body) {
        try {
            School school = currentUserService.getCurrentSchool();
            List<Long> studentIds = toStudentIds(body.get("studentIds"));
            if (studentIds.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要批量修改的学生"));
            }
            String electiveClass = (String) body.get("electiveClass");
            StudentService.BatchStudentOperationResult result =
                    studentService.batchUpdateElectiveClass(school, studentIds, electiveClass);
            return ResponseEntity.ok(ApiResponse.ok(buildBatchStudentResult(result)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/students/batch-delete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchDeleteStudents(
            @RequestBody Map<String, Object> body) {
        School school = currentUserService.getCurrentSchool();
        List<Long> studentIds = toStudentIds(body.get("studentIds"));
        if (studentIds.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要删除的学生"));
        }
        StudentService.BatchStudentOperationResult result =
                studentService.batchDelete(school, studentIds);
        return ResponseEntity.ok(ApiResponse.ok(buildBatchStudentResult(result)));
    }

    @SuppressWarnings("unchecked")
    private List<Long> toStudentIds(Object raw) {
        if (!(raw instanceof List)) return List.of();
        return ((List<?>) raw).stream()
                .filter(v -> v instanceof Number)
                .map(v -> ((Number) v).longValue())
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildBatchStudentResult(StudentService.BatchStudentOperationResult batchResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", batchResult.getTotalCount());
        result.put("successCount", batchResult.getSuccessCount());
        result.put("failedCount", batchResult.getFailedCount());
        result.put("studentIds", batchResult.getStudentIds());
        result.put("failedItems", batchResult.getFailedItems().stream().map(item -> {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("id", item.getStudentId());
            failure.put("reason", item.getReason());
            return failure;
        }).collect(Collectors.toList()));
        return result;
    }

    @GetMapping("/students/check-student-no")
    public ApiResponse<Map<String, Object>> checkStudentNo(
            @RequestParam String studentNo,
            @RequestParam(required = false) Long excludeId) {
        School school = currentUserService.getCurrentSchool();
        boolean available = studentService.isStudentNoAvailable(school, studentNo, excludeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available);
        m.put("message", available ? "学号可用" : "学号已存在");
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/students/{id}")
    public ApiResponse<String> deleteStudent(@PathVariable Long id) {
        studentService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    // ===== 考勤导出 =====

    @GetMapping(value = "/attendance/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String status) throws IOException {
        School school = currentUserService.getCurrentSchool();
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate) : start;
        String statusFilter = (status != null && !status.isBlank()) ? status : null;
        List<com.pe.assistant.entity.Attendance> records =
                attendanceService.findBySchoolAndFilters(school, start, end, gradeId, classId, statusFilter);
        byte[] bytes = attendanceService.exportXlsx(records);
        String suffix = (endDate != null && !endDate.equals(startDate)) ? "_" + endDate : "";
        String filename = URLEncoder.encode("考勤记录_" + startDate + suffix + ".xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .body(bytes);
    }

    // ===== 体测管理 =====

    @GetMapping("/physical-tests")
    public ApiResponse<PageDto<PhysicalTest>> physicalTests(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(defaultValue = "") String academicYear,
            @RequestParam(defaultValue = "") String semester,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        School school = currentUserService.getCurrentSchool();
        return ApiResponse.ok(PageDto.of(
                physicalTestService.findWithFilters(school, classId, gradeId,
                        academicYear, semester, keyword, page, size)));
    }

    @PostMapping("/physical-tests/save")
    public ApiResponse<String> savePhysicalTest(@RequestBody PhysicalTest body) {
        School school = currentUserService.getCurrentSchool();
        PhysicalTest pt = (body.getId() != null)
                ? physicalTestService.findById(body.getId())
                : new PhysicalTest();
        pt.setStudent(body.getStudent());
        pt.setSchool(school);
        pt.setAcademicYear(body.getAcademicYear());
        pt.setSemester(body.getSemester());
        pt.setTestDate(body.getTestDate());
        pt.setHeight(body.getHeight());
        pt.setWeight(body.getWeight());
        pt.setLungCapacity(body.getLungCapacity());
        pt.setSprint50m(body.getSprint50m());
        pt.setSitReach(body.getSitReach());
        pt.setStandingJump(body.getStandingJump());
        pt.setPullUps(body.getPullUps());
        pt.setSitUps(body.getSitUps());
        pt.setRun800m(body.getRun800m());
        pt.setRun1000m(body.getRun1000m());
        pt.setRemark(body.getRemark());
        physicalTestService.save(pt);
        return ApiResponse.ok("保存成功", null);
    }

    @DeleteMapping("/physical-tests/{id}")
    public ApiResponse<String> deletePhysicalTest(@PathVariable Long id) {
        School school = currentUserService.getCurrentSchool();
        physicalTestService.deleteById(id, school);
        return ApiResponse.ok("删除成功", null);
    }

    // ===== 成绩管理 =====

    @GetMapping("/term-grades")
    public ApiResponse<PageDto<TermGrade>> termGrades(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(defaultValue = "") String academicYear,
            @RequestParam(defaultValue = "") String semester,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        School school = currentUserService.getCurrentSchool();
        return ApiResponse.ok(PageDto.of(
                termGradeService.findWithFilters(school, classId, gradeId,
                        academicYear, semester, keyword, page, size)));
    }

    @PostMapping("/term-grades/save")
    public ApiResponse<String> saveTermGrade(@RequestBody TermGrade body) {
        School school = currentUserService.getCurrentSchool();
        TermGrade g = (body.getId() != null)
                ? termGradeService.findById(body.getId()).orElse(new TermGrade())
                : new TermGrade();
        g.setStudent(body.getStudent());
        g.setSchool(school);
        g.setAcademicYear(body.getAcademicYear());
        g.setSemester(body.getSemester());
        g.setAttendanceScore(body.getAttendanceScore());
        g.setSkillScore(body.getSkillScore());
        g.setTheoryScore(body.getTheoryScore());
        g.setRemark(body.getRemark());
        termGradeService.save(g);
        return ApiResponse.ok("保存成功", null);
    }

    @DeleteMapping("/term-grades/{id}")
    public ApiResponse<String> deleteTermGrade(@PathVariable Long id) {
        termGradeService.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }

    // ===== 教师功能权限管理 =====

    @GetMapping("/teacher-permissions")
    public ApiResponse<Map<String, Object>> getTeacherPermissions() {
        School school = currentUserService.getCurrentSchool();
        TeacherPermission p = teacherPermissionService.getOrCreate(school);
        return ApiResponse.ok(toPermissionMap(p));
    }

    @PutMapping("/teacher-permissions")
    public ApiResponse<Map<String, Object>> updateTeacherPermissions(
            @RequestBody Map<String, Boolean> config) {
        School school = currentUserService.getCurrentSchool();
        TeacherPermission p = teacherPermissionService.update(school, config);
        return ApiResponse.ok(toPermissionMap(p));
    }

    // ===== 审批记录导出 =====

    @GetMapping(value = "/course-requests/export",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportCourseRequests() throws IOException {
        School school = currentUserService.getCurrentSchool();
        List<InternalMessage> messages = messageService.getSchoolCourseRequests(school);
        byte[] bytes = messageService.exportCourseRequestsXlsx(messages);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = URLEncoder.encode("审批记录_" + date + ".xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .body(bytes);
    }

    // ===== 学生名单导出 =====

    @GetMapping(value = "/students/export",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportStudents(
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long classId) throws IOException {
        School school = currentUserService.getCurrentSchool();
        Long effectiveClassId = classId;
        String electiveClass = null;
        if (classId != null) {
            SchoolClass selectedClass = classService.findById(classId);
            if (selectedClass.getSchool() != null && school != null
                    && !Objects.equals(selectedClass.getSchool().getId(), school.getId())) {
                effectiveClassId = null;
            } else if ("选修课".equals(selectedClass.getType())) {
                effectiveClassId = null;
                electiveClass = selectedClass.getGrade() != null
                        ? selectedClass.getGrade().getName() + "/" + selectedClass.getName()
                        : selectedClass.getName();
            }
        }
        List<Student> students = studentService.findListWithFilters(school, effectiveClassId, gradeId,
                null, null, null, electiveClass, null);
        byte[] bytes = studentService.exportStudentsXlsx(students);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = URLEncoder.encode("学生名单_" + date + ".xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .body(bytes);
    }

    private static Map<String, Object> toPermissionMap(TeacherPermission p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("editStudentName", p.isEditStudentName());
        m.put("editStudentGender", p.isEditStudentGender());
        m.put("editStudentNo", p.isEditStudentNo());
        m.put("editStudentStatus", p.isEditStudentStatus());
        m.put("editStudentClass", p.isEditStudentClass());
        m.put("editStudentElectiveClass", p.isEditStudentElectiveClass());
        m.put("attendanceEdit", p.isAttendanceEdit());
        m.put("physicalTestEdit", p.isPhysicalTestEdit());
        m.put("termGradeEdit", p.isTermGradeEdit());
        m.put("batchOperation", p.isBatchOperation());
        return m;
    }

    // ===== 操作日志时间线 =====

    @GetMapping("/operation-timeline")
    public ApiResponse<Map<String, Object>> operationTimeline(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long teacherId) {
        School school = currentUserService.getCurrentSchool();
        Long schoolId = school.getId();

        List<TeacherOperationLog> opLogs = teacherId != null
                ? teacherOperationLogRepository.findTop100ByTeacherIdAndSchool_IdOrderByOperatedAtDesc(teacherId, schoolId)
                : teacherOperationLogRepository.findTop200BySchool_IdOrderByOperatedAtDesc(schoolId);

        List<CourseRequestAudit> audits = teacherId != null
                ? courseRequestAuditRepository.findTop100ByOperatorTeacherIdAndSchool_IdOrderByHandledAtDesc(teacherId, schoolId)
                : courseRequestAuditRepository.findTop200BySchool_IdOrderByHandledAtDesc(schoolId);
        List<CourseOverflowAudit> overflowAudits = teacherId != null
                ? courseOverflowAuditRepository.findTop100ByOperatorTeacherIdAndSchoolIdOrderByCreatedAtDesc(teacherId, schoolId)
                : courseOverflowAuditRepository.findTop200BySchoolIdOrderByCreatedAtDesc(schoolId);

        List<Map<String, Object>> entries = new ArrayList<>();

        for (TeacherOperationLog log : opLogs) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", "op_" + log.getId());
            e.put("source", "teacher_operation_log");
            e.put("action", log.getAction());
            e.put("title", actionToTitle(log.getAction()));
            e.put("description", log.getDescription());
            e.put("targetCount", log.getTargetCount());
            e.put("teacherName", log.getTeacherName());
            e.put("operatedAt", log.getOperatedAt().toString());
            entries.add(e);
        }

        for (CourseRequestAudit audit : audits) {
            boolean approved = "APPROVE".equals(audit.getAction());
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", "audit_" + audit.getId());
            e.put("source", "course_request_audit");
            e.put("action", approved ? "APPROVE" : "REJECT");
            e.put("title", approved ? "同意选课申请" : "拒绝选课申请");
            String coursePart = audit.getRelatedCourseName() != null ? audit.getRelatedCourseName() : "未知课程";
            String senderPart = audit.getSenderName() != null ? "，申请人：" + audit.getSenderName() : "";
            e.put("description", coursePart + senderPart);
            e.put("targetCount", 1);
            e.put("teacherName", audit.getOperatorTeacherName());
            e.put("operatedAt", audit.getHandledAt().toString());
            entries.add(e);
        }

        for (CourseOverflowAudit audit : overflowAudits) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", "overflow_" + audit.getId());
            e.put("source", "course_overflow_audit");
            e.put("action", "FORCED_OVERFLOW");
            e.put("title", actionToTitle("FORCED_OVERFLOW"));
            String coursePart = audit.getCourseName() != null ? audit.getCourseName() : "未知课程";
            String studentPart = audit.getStudentName() != null ? "，学生：" + audit.getStudentName() : "";
            String reasonPart = audit.getReason() != null && !audit.getReason().isBlank() ? "，原因：" + audit.getReason() : "";
            e.put("description", coursePart + studentPart + reasonPart);
            e.put("targetCount", 1);
            e.put("teacherName", audit.getOperatorTeacherName());
            e.put("operatedAt", audit.getCreatedAt().toString());
            entries.add(e);
        }

        entries.sort((a, b) -> ((String) b.get("operatedAt")).compareTo((String) a.get("operatedAt")));

        int total = entries.size();
        int start = page * size;
        List<Map<String, Object>> paged = start >= total
                ? Collections.emptyList()
                : entries.subList(start, Math.min(start + size, total));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", paged);
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        result.put("page", page);
        result.put("size", size);
        return ApiResponse.ok(result);
    }

    private static String actionToTitle(String action) {
        if (action == null) return "操作";
        return switch (action) {
            case "ATTENDANCE_SAVE"        -> "提交考勤";
            case "PHYSICAL_TEST_SAVE"     -> "录入体测数据";
            case "TERM_GRADE_SAVE"        -> "录入期末成绩";
            case "STUDENT_UPDATE"         -> "修改学生信息";
            case "STUDENT_BATCH_STATUS"   -> "批量修改学籍状态";
            case "STUDENT_BATCH_ELECTIVE" -> "批量修改选修班";
            case "BATCH_APPROVE"          -> "批量同意申请";
            case "BATCH_REJECT"           -> "批量拒绝申请";
            case "FORCED_OVERFLOW"        -> "强制超编录入";
            default -> action;
        };
    }

    private SelectionEvent resolveDiagnosticEvent(School school, Long eventId) {
        if (eventId != null) {
            return selectionEventRepository.findById(eventId)
                    .filter(event -> event.getSchool() != null && Objects.equals(event.getSchool().getId(), school.getId()))
                    .orElseThrow(() -> new RuntimeException("选课活动不存在"));
        }
        List<SelectionEvent> events = selectionEventRepository.findBySchoolOrderByCreatedAtDesc(school);
        if (events.isEmpty()) {
            return null;
        }
        return events.stream()
                .filter(event -> !"CLOSED".equals(event.getStatus()))
                .findFirst()
                .orElse(events.get(0));
    }

    private Map<String, Object> buildDiagnosticEventMap(SelectionEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("name", event.getName());
        map.put("status", event.getStatus());
        map.put("statusText", event.getAdminDisplayStatusText());
        map.put("round1Start", event.getRound1Start());
        map.put("round1End", event.getRound1End());
        map.put("round2Start", event.getRound2Start());
        map.put("round2End", event.getRound2End());
        map.put("round3Start", event.getRound3Start());
        map.put("round3End", event.getRound3End());
        map.put("lotteryNote", event.getLotteryNote());
        map.put("createdAt", event.getCreatedAt());
        map.put("detailUrl", buildEventDetailUrl(event.getId(), "courses"));
        map.put("studentUrl", buildEventDetailUrl(event.getId(), "students"));
        return map;
    }

    private Map<String, Object> buildDiagnosticOverview(SelectionEvent event,
                                                        List<Course> courses,
                                                        List<CourseSelection> selections,
                                                        List<TeacherOperationLog> opLogs,
                                                        List<CourseRequestAudit> requestAudits,
                                                        List<CourseOverflowAudit> overflowAudits) {
        Map<String, Object> map = new LinkedHashMap<>();
        long activeCourseCount = courses.stream().filter(course -> "ACTIVE".equals(course.getStatus())).count();
        long globalCourseCount = courses.stream().filter(course -> "GLOBAL".equals(course.getCapacityMode())).count();
        long perClassCourseCount = courses.stream().filter(course -> "PER_CLASS".equals(course.getCapacityMode())).count();
        long confirmedCount = selections.stream().filter(selection -> "CONFIRMED".equals(selection.getStatus())).count();
        long pendingCount = selections.stream().filter(selection -> "PENDING".equals(selection.getStatus())).count();
        long lotteryFailCount = selections.stream().filter(selection -> "LOTTERY_FAIL".equals(selection.getStatus())).count();
        long cancelledCount = selections.stream().filter(selection -> "CANCELLED".equals(selection.getStatus())).count();
        long draftCount = selections.stream().filter(selection -> "DRAFT".equals(selection.getStatus())).count();

        map.put("eventId", event.getId());
        map.put("courseCount", courses.size());
        map.put("activeCourseCount", activeCourseCount);
        map.put("globalCourseCount", globalCourseCount);
        map.put("perClassCourseCount", perClassCourseCount);
        map.put("selectionCount", selections.size());
        map.put("confirmedCount", confirmedCount);
        map.put("pendingCount", pendingCount);
        map.put("lotteryFailCount", lotteryFailCount);
        map.put("cancelledCount", cancelledCount);
        map.put("draftCount", draftCount);
        map.put("operationLogCount", opLogs.size());
        map.put("requestAuditCount", requestAudits.size());
        map.put("overflowAuditCount", overflowAudits.stream()
                .filter(audit -> Objects.equals(audit.getEventId(), event.getId()))
                .count());
        return map;
    }

    private Map<String, Object> buildDiagnosticHealth(SelectionEvent event,
                                                      List<Course> courses,
                                                      List<CourseSelection> selections,
                                                      List<CourseOverflowAudit> overflowAudits) {
        List<Map<String, Object>> issues = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if ("ROUND2".equals(event.getStatus()) && event.getRound2End() != null && now.isAfter(event.getRound2End())) {
            issues.add(buildIssue("WARN", "ROUND2_TIMEOUT", "活动仍处于第二轮，但结束时间已过，请检查自动收尾是否执行", event.getId(), null, null));
        }
        if ("PROCESSING".equals(event.getStatus()) && event.getCreatedAt() != null
                && event.getCreatedAt().isBefore(now.minusMinutes(10))) {
            issues.add(buildIssue("WARN", "PROCESSING_STALE", "活动长时间停留在抽签结算中，请检查抽签日志与任务执行情况", event.getId(), null, null));
        }

        Map<Long, List<CourseSelection>> selectionsByCourse = selections.stream()
                .filter(selection -> selection.getCourse() != null && selection.getCourse().getId() != null)
                .collect(Collectors.groupingBy(selection -> selection.getCourse().getId()));

        for (Course course : courses) {
            List<CourseSelection> courseSelections = selectionsByCourse.getOrDefault(course.getId(), List.of());
            long confirmedUniqueCount = courseSelections.stream()
                    .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                    .map(CourseSelection::getStudent)
                    .filter(Objects::nonNull)
                    .map(Student::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            if (course.getCurrentCount() != confirmedUniqueCount) {
                issues.add(buildIssue("ERROR",
                        "COURSE_COUNT_MISMATCH",
                        "课程“" + course.getName() + "”计数字段与确认人数不一致（字段=" + course.getCurrentCount()
                                + "，确认=" + confirmedUniqueCount + "）",
                        event.getId(),
                        course.getId(),
                        course.getName()));
            }

            long overflowCount = Math.max(0L, confirmedUniqueCount - course.getTotalCapacity());
            if (overflowCount > 0) {
                long overflowAuditCount = overflowAudits.stream()
                        .filter(audit -> Objects.equals(audit.getEventId(), event.getId()))
                        .filter(audit -> Objects.equals(audit.getCourseId(), course.getId()))
                        .count();
                String message = "课程“" + course.getName() + "”当前超编 " + overflowCount + " 人";
                if (overflowAuditCount == 0) {
                    issues.add(buildIssue("ERROR", "OVERFLOW_WITHOUT_AUDIT", message + "，但未发现强制超编审计记录", event.getId(), course.getId(), course.getName()));
                } else {
                    issues.add(buildIssue("WARN", "OVERFLOW_WITH_AUDIT", message + "，已记录强制超编审计 " + overflowAuditCount + " 条", event.getId(), course.getId(), course.getName()));
                }
            }

            if ("PER_CLASS".equals(course.getCapacityMode())) {
                List<CourseClassCapacity> capacities = courseClassCapacityRepository.findByCourse(course);
                int capacityCurrentTotal = capacities.stream().mapToInt(CourseClassCapacity::getCurrentCount).sum();
                if (capacityCurrentTotal != course.getCurrentCount()) {
                    issues.add(buildIssue("ERROR",
                            "PER_CLASS_TOTAL_MISMATCH",
                            "课程“" + course.getName() + "”总计数字段与班级名额计数和不一致（字段=" + course.getCurrentCount()
                                    + "，班级合计=" + capacityCurrentTotal + "）",
                            event.getId(),
                            course.getId(),
                            course.getName()));
                }

                Map<Long, Long> confirmedByClass = courseSelections.stream()
                        .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                        .map(CourseSelection::getStudent)
                        .filter(Objects::nonNull)
                        .filter(student -> student.getSchoolClass() != null && student.getSchoolClass().getId() != null)
                        .collect(Collectors.groupingBy(student -> student.getSchoolClass().getId(), Collectors.mapping(Student::getId, Collectors.collectingAndThen(Collectors.toSet(), set -> (long) set.size()))));

                for (CourseClassCapacity capacity : capacities) {
                    Long classId = capacity.getSchoolClass() != null ? capacity.getSchoolClass().getId() : null;
                    long confirmedClassCount = confirmedByClass.getOrDefault(classId, 0L);
                    if (capacity.getCurrentCount() != confirmedClassCount) {
                        issues.add(buildIssue("ERROR",
                                "PER_CLASS_COUNT_MISMATCH",
                                "课程“" + course.getName() + "”在班级“"
                                        + (capacity.getSchoolClass() != null ? capacity.getSchoolClass().getName() : "未知班级")
                                        + "”的计数字段与确认人数不一致（字段=" + capacity.getCurrentCount()
                                        + "，确认=" + confirmedClassCount + "）",
                                event.getId(),
                                course.getId(),
                                course.getName()));
                    }
                }
            }
        }

        String severity = issues.stream().anyMatch(issue -> "ERROR".equals(issue.get("level"))) ? "ERROR"
                : issues.isEmpty() ? "OK" : "WARN";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", severity);
        map.put("issueCount", issues.size());
        map.put("issues", issues);
        return map;
    }

    private List<Map<String, Object>> buildHotCourseList(List<Course> courses, List<CourseSelection> selections) {
        Map<Long, List<CourseSelection>> selectionsByCourse = selections.stream()
                .filter(selection -> selection.getCourse() != null && selection.getCourse().getId() != null)
                .collect(Collectors.groupingBy(selection -> selection.getCourse().getId()));

        return courses.stream()
                .map(course -> {
                    List<CourseSelection> courseSelections = selectionsByCourse.getOrDefault(course.getId(), List.of());
                    long confirmedUniqueCount = courseSelections.stream()
                            .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                            .map(CourseSelection::getStudent)
                            .filter(Objects::nonNull)
                            .map(Student::getId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();
                    long pendingCount = courseSelections.stream().filter(selection -> "PENDING".equals(selection.getStatus())).count();
                    int remainingCapacity = Math.max(0, course.getTotalCapacity() - course.getCurrentCount());
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("courseId", course.getId());
                    map.put("courseName", course.getName());
                    map.put("status", course.getStatus());
                    map.put("capacityMode", course.getCapacityMode());
                    map.put("totalCapacity", course.getTotalCapacity());
                    map.put("currentCount", course.getCurrentCount());
                    map.put("confirmedCount", confirmedUniqueCount);
                    map.put("pendingCount", pendingCount);
                    map.put("remainingCapacity", remainingCapacity);
                    map.put("overflowCount", Math.max(0L, confirmedUniqueCount - course.getTotalCapacity()));
                    map.put("countMismatch", course.getCurrentCount() != confirmedUniqueCount);
                    map.put("detailUrl", buildCourseEnrollmentsUrl(course.getEvent() != null ? course.getEvent().getId() : null, course.getId()));
                    map.put("eventUrl", buildEventDetailUrl(course.getEvent() != null ? course.getEvent().getId() : null, "courses"));
                    return map;
                })
                .sorted(Comparator
                        .comparing((Map<String, Object> map) -> (Boolean) map.get("countMismatch")).reversed()
                        .thenComparing(map -> ((Number) map.get("overflowCount")).longValue(), Comparator.reverseOrder())
                        .thenComparing(map -> ((Number) map.get("confirmedCount")).longValue(), Comparator.reverseOrder()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildRecentActivity(SelectionEvent event,
                                                    List<Course> courses,
                                                    List<TeacherOperationLog> opLogs,
                                                    List<CourseRequestAudit> requestAudits,
                                                    List<CourseOverflowAudit> overflowAudits) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<Long, Course> courseById = courses.stream()
                .filter(course -> course.getId() != null)
                .collect(Collectors.toMap(Course::getId, course -> course, (left, right) -> left, LinkedHashMap::new));

        for (TeacherOperationLog log : opLogs) {
            if (log.getOperatedAt() == null || log.getOperatedAt().isBefore(since)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "op_" + log.getId());
            entry.put("source", "teacher_operation_log");
            entry.put("action", log.getAction());
            entry.put("title", actionToTitle(log.getAction()));
            entry.put("description", log.getDescription());
            entry.put("teacherName", log.getTeacherName());
            entry.put("operatedAt", log.getOperatedAt());
            entry.put("jumpUrl", buildEventDetailUrl(event.getId(), "courses"));
            entry.put("jumpLabel", "查看活动");
            entries.add(entry);
        }

        for (CourseRequestAudit audit : requestAudits) {
            if (audit.getHandledAt() == null || audit.getHandledAt().isBefore(since)) {
                continue;
            }
            boolean approved = "APPROVE".equals(audit.getAction());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "audit_" + audit.getId());
            entry.put("source", "course_request_audit");
            entry.put("action", approved ? "APPROVE" : "REJECT");
            entry.put("title", approved ? "同意选课申请" : "拒绝选课申请");
            entry.put("description", (audit.getRelatedCourseName() != null ? audit.getRelatedCourseName() : "未知课程")
                    + (audit.getSenderName() != null ? "，申请人：" + audit.getSenderName() : ""));
            entry.put("teacherName", audit.getOperatorTeacherName());
            entry.put("operatedAt", audit.getHandledAt());
            if (audit.getRelatedCourseId() != null && courseById.containsKey(audit.getRelatedCourseId())) {
                entry.put("jumpUrl", buildCourseEnrollmentsUrl(event.getId(), audit.getRelatedCourseId()));
                entry.put("jumpLabel", "查看课程明细");
            } else {
                entry.put("jumpUrl", buildEventDetailUrl(event.getId(), "courses"));
                entry.put("jumpLabel", "查看活动");
            }
            entries.add(entry);
        }

        for (CourseOverflowAudit audit : overflowAudits) {
            if (audit.getCreatedAt() == null || audit.getCreatedAt().isBefore(since)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "overflow_" + audit.getId());
            entry.put("source", "course_overflow_audit");
            entry.put("action", "FORCED_OVERFLOW");
            entry.put("title", actionToTitle("FORCED_OVERFLOW"));
            entry.put("description", (audit.getCourseName() != null ? audit.getCourseName() : "未知课程")
                    + (audit.getStudentName() != null ? "，学生：" + audit.getStudentName() : "")
                    + (audit.getReason() != null && !audit.getReason().isBlank() ? "，原因：" + audit.getReason() : ""));
            entry.put("teacherName", audit.getOperatorTeacherName());
            entry.put("operatedAt", audit.getCreatedAt());
            entry.put("jumpUrl", buildCourseEnrollmentsUrl(audit.getEventId(), audit.getCourseId()));
            entry.put("jumpLabel", "查看课程明细");
            entries.add(entry);
        }

        entries.sort((left, right) -> ((LocalDateTime) right.get("operatedAt")).compareTo((LocalDateTime) left.get("operatedAt")));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operationLogCount24h", entries.stream().filter(entry -> "teacher_operation_log".equals(entry.get("source"))).count());
        map.put("requestAuditCount24h", entries.stream().filter(entry -> "course_request_audit".equals(entry.get("source"))).count());
        map.put("overflowAuditCount24h", entries.stream().filter(entry -> "course_overflow_audit".equals(entry.get("source"))).count());
        map.put("entries", entries.stream().limit(20).collect(Collectors.toList()));
        return map;
    }

    private Map<String, Object> buildIssue(String level, String code, String message) {
        return buildIssue(level, code, message, null, null, null);
    }

    private Map<String, Object> buildIssue(String level, String code, String message, Long eventId, Long courseId, String courseName) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("level", level);
        issue.put("code", code);
        issue.put("message", message);
        issue.put("eventId", eventId);
        issue.put("courseId", courseId);
        issue.put("courseName", courseName);
        issue.put("jumpUrl", courseId != null ? buildCourseEnrollmentsUrl(eventId, courseId) : buildEventDetailUrl(eventId, "courses"));
        issue.put("jumpLabel", courseId != null ? "查看课程明细" : "查看活动");
        return issue;
    }

    private String buildEventDetailUrl(Long eventId, String tab) {
        if (eventId == null) {
            return null;
        }
        String resolvedTab = (tab == null || tab.isBlank()) ? "courses" : tab;
        return "/admin/courses/" + eventId + "/detail?tab=" + resolvedTab;
    }

    private String buildCourseEnrollmentsUrl(Long eventId, Long courseId) {
        if (eventId == null || courseId == null) {
            return null;
        }
        return "/admin/courses/" + eventId + "/courses/" + courseId + "/enrollments";
    }
}
