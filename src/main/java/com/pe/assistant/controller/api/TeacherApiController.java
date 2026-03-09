package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.*;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherApiController {

    private final ClassService classService;
    private final StudentService studentService;
    private final AttendanceService attendanceService;
    private final PhysicalTestService physicalTestService;
    private final TermGradeService termGradeService;
    private final MessageService messageService;
    private final CurrentUserService currentUserService;
    private final GradeService gradeService;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload-dir:${user.home}/.pe-teacher-assistant/uploads}")
    private String uploadDir;

    // ===== 骞寸骇鍒楄〃 =====

    @GetMapping("/grades")
    public ApiResponse<List<Grade>> grades() {
        School school = currentUserService.getCurrentSchool();
        return ApiResponse.ok(gradeService.findAll(school));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", teacher.getId());
        m.put("username", teacher.getUsername());
        m.put("name", teacher.getName());
        m.put("role", teacher.getRole());
        m.put("phone", teacher.getPhone());
        m.put("gender", teacher.getGender());
        m.put("birthDate", teacher.getBirthDate());
        m.put("specialty", teacher.getSpecialty());
        m.put("email", teacher.getEmail());
        m.put("photoUrl", teacher.getPhotoUrl());
        m.put("bio", teacher.getBio());
        m.put("schoolName", teacher.getSchool() != null ? teacher.getSchool().getName() : null);
        return ApiResponse.ok(m);
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<String>> updateProfile(@RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            if (body.containsKey("gender")) {
                teacher.setGender(body.get("gender") == null ? null : String.valueOf(body.get("gender")));
            }
            if (body.containsKey("specialty")) {
                teacher.setSpecialty(body.get("specialty") == null ? null : String.valueOf(body.get("specialty")));
            }
            if (body.containsKey("email")) {
                teacher.setEmail(body.get("email") == null ? null : String.valueOf(body.get("email")));
            }
            if (body.containsKey("bio")) {
                teacher.setBio(body.get("bio") == null ? null : String.valueOf(body.get("bio")));
            }
            if (body.containsKey("birthDate")) {
                Object value = body.get("birthDate");
                if (value == null || String.valueOf(value).isBlank()) {
                    teacher.setBirthDate(null);
                } else {
                    teacher.setBirthDate(LocalDate.parse(String.valueOf(value)));
                }
            }
            teacherRepository.save(teacher);
            return ResponseEntity.ok(ApiResponse.ok("个人资料已更新", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping(value = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProfilePhoto(
            @RequestPart("photo") MultipartFile photo) {
        try {
            if (photo == null || photo.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "请上传图片"));
            }
            Teacher teacher = currentUserService.getCurrentTeacher();
            String photoUrl = savePhoto(teacher.getId(), photo);
            teacher.setPhotoUrl(photoUrl);
            teacherRepository.save(teacher);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("photoUrl", photoUrl);
            return ResponseEntity.ok(ApiResponse.ok(m));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody Map<String, String> body) {
        try {
            String oldPassword = body.getOrDefault("oldPassword", "");
            String newPassword = body.getOrDefault("newPassword", "");
            Teacher teacher = currentUserService.getCurrentTeacher();
            if (!passwordEncoder.matches(oldPassword, teacher.getPassword())) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "原密码错误"));
            }
            validatePassword(newPassword);
            teacher.setPassword(passwordEncoder.encode(newPassword));
            teacherRepository.save(teacher);
            return ResponseEntity.ok(ApiResponse.ok("密码修改成功", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "修改失败"));
        }
    }

    // ===== 鍏ㄦ牎琛屾斂鐝紙鐢ㄤ簬瀛︾敓鐝骇淇敼锛?=====

    @GetMapping("/school-classes")
    public ApiResponse<List<Map<String, Object>>> schoolClasses() {
        School school = currentUserService.getCurrentSchool();
        List<Map<String, Object>> result = classService.findAll(school).stream()
                .filter(c -> !isElectiveType(c.getType()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("type", c.getType());
                    m.put("gradeName", c.getGrade() != null ? c.getGrade().getName() : null);
                    m.put("gradeId", c.getGrade() != null ? c.getGrade().getId() : null);
                    return m;
                }).collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    // ===== 鍏ㄦ牎閫変慨鐝紙鐢ㄤ簬瀛︾敓閫変慨鐝慨鏀癸級 =====

    @GetMapping("/elective-classes")
    public ApiResponse<List<Map<String, Object>>> electiveClasses() {
        School school = currentUserService.getCurrentSchool();
        List<Map<String, Object>> result = classService.findAll(school).stream()
                .filter(c -> isElectiveType(c.getType()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("gradeName", c.getGrade() != null ? c.getGrade().getName() : null);
                    m.put("gradeId", c.getGrade() != null ? c.getGrade().getId() : null);
                    String storedName = c.getGrade() != null
                            ? c.getGrade().getName() + "/" + c.getName()
                            : c.getName();
                    m.put("storedName", storedName);
                    return m;
                }).collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    // ===== 鐝骇鍒楄〃 =====

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        List<SchoolClass> list = classService.findByTeacher(teacher);
        List<Map<String, Object>> result = list.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("gradeName", c.getGrade() != null ? c.getGrade().getName() : null);
            return m;
        }).collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    // ===== 閫夎瀹℃壒涓績 =====

    @GetMapping("/course-requests")
    public ApiResponse<List<Map<String, Object>>> courseRequests(
            @RequestParam(defaultValue = "PENDING") String status) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        List<InternalMessage> messages = messageService.getTeacherCourseRequests(teacher, status);
        List<Map<String, Object>> result = messages.stream()
                .map(this::toCourseRequestMap)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @GetMapping("/course-requests/{id:\\d+}")
    public ApiResponse<Map<String, Object>> courseRequestDetail(@PathVariable Long id) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        InternalMessage msg = messageService.getTeacherCourseRequestById(teacher, id);
        List<CourseRequestAudit> audits = messageService.getTeacherCourseRequestAudits(teacher, id);
        Map<String, Object> detail = toCourseRequestMap(msg);
        detail.put("auditLogs", audits.stream()
                .map(this::toCourseRequestAuditMap)
                .collect(Collectors.toList()));
        return ApiResponse.ok(detail);
    }

    @GetMapping("/course-requests/summary")
    public ApiResponse<Map<String, Long>> courseRequestSummary() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("pending", messageService.countTeacherCourseRequests(teacher, "PENDING"));
        m.put("approved", messageService.countTeacherCourseRequests(teacher, "APPROVED"));
        m.put("rejected", messageService.countTeacherCourseRequests(teacher, "REJECTED"));
        return ApiResponse.ok(m);
    }

    @GetMapping("/messages/unread-count")
    public ApiResponse<Map<String, Long>> unreadMessageCount() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("unreadCount", messageService.getUnreadCount("TEACHER", teacher.getId()));
        return ApiResponse.ok(m);
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> messages(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "ALL") String type) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            List<Map<String, Object>> result = messageService.getTeacherInbox(teacher, type, unreadOnly).stream()
                    .map(this::toTeacherMessageMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/messages/{id:\\d+}/read")
    public ResponseEntity<ApiResponse<String>> markMessageRead(@PathVariable Long id) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            messageService.markTeacherMessageRead(id, teacher);
            return ResponseEntity.ok(ApiResponse.ok("已标记已读", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/course-requests/{id}/approve")
    public ResponseEntity<ApiResponse<String>> approveCourseRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            String remark = body != null && body.get("remark") != null
                    ? String.valueOf(body.get("remark"))
                    : null;
            messageService.approveRequest(id, teacher, remark);
            return ResponseEntity.ok(ApiResponse.ok("已同意申请", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/course-requests/{id}/reject")
    public ResponseEntity<ApiResponse<String>> rejectCourseRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            String remark = body != null && body.get("remark") != null
                    ? String.valueOf(body.get("remark"))
                    : null;
            messageService.rejectRequest(id, teacher, remark);
            return ResponseEntity.ok(ApiResponse.ok("已拒绝申请", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/course-requests/batch-handle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchHandleCourseRequests(
            @RequestBody BatchHandleRequest body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            if (body == null || body.getMessageIds() == null || body.getMessageIds().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要处理的审批记录"));
            }
            String action = body.getAction() == null ? "" : body.getAction().trim().toUpperCase();
            boolean approve;
            if ("APPROVE".equals(action)) {
                approve = true;
            } else if ("REJECT".equals(action)) {
                approve = false;
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "批量操作类型仅支持 APPROVE 或 REJECT"));
            }

            List<Long> deduplicatedIds = new ArrayList<>(new LinkedHashSet<>(body.getMessageIds()));
            List<Long> successIds = new ArrayList<>();
            List<Map<String, Object>> failedItems = new ArrayList<>();
            for (Long messageId : deduplicatedIds) {
                if (messageId == null) {
                    continue;
                }
                try {
                    if (approve) {
                        messageService.approveRequest(messageId, teacher, body.getRemark());
                    } else {
                        messageService.rejectRequest(messageId, teacher, body.getRemark());
                    }
                    successIds.add(messageId);
                } catch (Exception ex) {
                    Map<String, Object> failed = new LinkedHashMap<>();
                    failed.put("messageId", messageId);
                    failed.put("reason", ex.getMessage() == null ? "处理失败" : ex.getMessage());
                    failedItems.add(failed);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", action);
            result.put("totalCount", deduplicatedIds.size());
            result.put("successCount", successIds.size());
            result.put("failedCount", failedItems.size());
            result.put("successIds", successIds);
            result.put("failedItems", failedItems);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @Data
    static class BatchHandleRequest {
        private List<Long> messageIds;
        private String action;
        private String remark;
    }

    @Data
    static class BatchStudentStatusRequest {
        private List<Long> studentIds;
        private String studentStatus;
    }

    @Data
    static class BatchStudentElectiveClassRequest {
        private List<Long> studentIds;
        private String electiveClass;
    }

    private Map<String, Object> toCourseRequestMap(InternalMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", msg.getId());
        m.put("subject", msg.getSubject());
        m.put("content", msg.getContent());
        m.put("status", msg.getStatus());
        m.put("type", msg.getType());
        m.put("senderId", msg.getSenderId());
        m.put("senderName", msg.getSenderName());
        m.put("relatedCourseId", msg.getRelatedCourseId());
        m.put("relatedCourseName", msg.getRelatedCourseName());
        m.put("isRead", msg.getIsRead());
        m.put("sentAt", msg.getSentAt());
        m.put("handledById", msg.getHandledById());
        m.put("handledByName", msg.getHandledByName());
        m.put("handledAt", msg.getHandledAt());
        m.put("handleRemark", msg.getHandleRemark());
        return m;
    }

    private Map<String, Object> buildBatchStudentResult(List<Long> studentIds, int successCount) {
        List<Long> deduplicatedIds = new ArrayList<>(new LinkedHashSet<>(studentIds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", deduplicatedIds.size());
        result.put("successCount", successCount);
        result.put("failedCount", Math.max(0, deduplicatedIds.size() - successCount));
        result.put("studentIds", deduplicatedIds);
        return result;
    }

    private Map<String, Object> toTeacherMessageMap(InternalMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", msg.getId());
        m.put("subject", msg.getSubject());
        m.put("content", msg.getContent());
        m.put("type", msg.getType());
        m.put("status", msg.getStatus());
        m.put("isRead", msg.getIsRead());
        m.put("sentAt", msg.getSentAt());
        m.put("senderType", msg.getSenderType());
        m.put("senderId", msg.getSenderId());
        m.put("senderName", msg.getSenderName());
        m.put("relatedCourseId", msg.getRelatedCourseId());
        m.put("relatedCourseName", msg.getRelatedCourseName());

        if ("COURSE_REQUEST".equals(msg.getType())) {
            m.put("businessTargetType", "COURSE_REQUEST");
            m.put("businessTargetId", msg.getId());
        } else {
            m.put("businessTargetType", null);
            m.put("businessTargetId", null);
        }
        return m;
    }

    private Map<String, Object> toCourseRequestAuditMap(CourseRequestAudit audit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", audit.getId());
        m.put("requestMessageId", audit.getRequestMessageId());
        m.put("action", audit.getAction());
        m.put("beforeStatus", audit.getBeforeStatus());
        m.put("afterStatus", audit.getAfterStatus());
        m.put("operatorTeacherId", audit.getOperatorTeacherId());
        m.put("operatorTeacherName", audit.getOperatorTeacherName());
        m.put("senderId", audit.getSenderId());
        m.put("senderName", audit.getSenderName());
        m.put("relatedCourseId", audit.getRelatedCourseId());
        m.put("relatedCourseName", audit.getRelatedCourseName());
        m.put("remark", audit.getRemark());
        m.put("handledAt", audit.getHandledAt());
        return m;
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

    // ===== 瀛︾敓鐝骇淇敼锛堟暀甯堟潈闄愶級 =====

    @PutMapping("/students/{id}")
    public ResponseEntity<ApiResponse<String>> updateStudentClass(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Student current = studentService.findById(id);
        String name = body.get("name") != null ? String.valueOf(body.get("name")) : current.getName();
        String gender = body.get("gender") != null ? String.valueOf(body.get("gender")) : current.getGender();
        String studentNo = body.get("studentNo") != null ? String.valueOf(body.get("studentNo")) : current.getStudentNo();
        String studentStatus = body.get("studentStatus") != null ? String.valueOf(body.get("studentStatus")) : current.getStudentStatus();
        Long classId = body.get("classId") != null ? Long.valueOf(body.get("classId").toString()) : null;
        Long version = body.get("version") != null ? Long.valueOf(body.get("version").toString()) : null;
        String electiveClass = body.containsKey("electiveClass")
                ? (String) body.get("electiveClass")
                : current.getElectiveClass();
        try {
            studentService.update(id, name, gender, studentNo, current.getIdCard(),
                    electiveClass, classId, studentStatus, version);
            return ResponseEntity.ok(ApiResponse.ok("修改成功", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(ApiResponse.error(409, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ===== 鐝骇瀛︾敓鍒楄〃锛堝惈閫変慨鐝俊鎭級 =====

    @PostMapping("/students/batch-update-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchUpdateStudentStatus(
            @RequestBody BatchStudentStatusRequest body) {
        try {
            if (body == null || body.getStudentIds() == null || body.getStudentIds().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要批量修改的学生"));
            }
            School school = currentUserService.getCurrentSchool();
            int updated = studentService.batchUpdateStudentStatus(school, body.getStudentIds(), body.getStudentStatus());
            return ResponseEntity.ok(ApiResponse.ok(buildBatchStudentResult(body.getStudentIds(), updated)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/students/batch-update-elective-class")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchUpdateStudentElectiveClass(
            @RequestBody BatchStudentElectiveClassRequest body) {
        try {
            if (body == null || body.getStudentIds() == null || body.getStudentIds().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要批量修改的学生"));
            }
            School school = currentUserService.getCurrentSchool();
            int updated = studentService.batchUpdateElectiveClass(school, body.getStudentIds(), body.getElectiveClass());
            return ResponseEntity.ok(ApiResponse.ok(buildBatchStudentResult(body.getStudentIds(), updated)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> students(
            @PathVariable Long classId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) Long adminClassId,
            @RequestParam(required = false) String electiveClass,
            @RequestParam(required = false) String studentStatus) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students;
        if (isElectiveType(sc.getType())) {
            String electiveClassName = (sc.getGrade() != null ? sc.getGrade().getName() + "/" : "") + sc.getName();
            students = studentService.findByElectiveClassForTeacher(school, electiveClassName);
        } else {
            students = studentService.findByClassIdForTeacher(school, classId);
        }
        students = students.stream()
                .filter(s -> containsIgnoreCase(s.getName(), name))
                .filter(s -> containsIgnoreCase(s.getStudentNo(), studentNo))
                .filter(s -> adminClassId == null
                        || (s.getSchoolClass() != null && Objects.equals(s.getSchoolClass().getId(), adminClassId)))
                .filter(s -> containsIgnoreCase(s.getElectiveClass(), electiveClass))
                .filter(s -> isBlank(studentStatus)
                        || normalizeText(studentStatus).equals(normalizeText(s.getStudentStatus())))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = students.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("studentNo", s.getStudentNo());
            m.put("version", s.getVersion() == null ? -1L : s.getVersion());
            m.put("gender", s.getGender());
            m.put("electiveClass", s.getElectiveClass());
            m.put("studentStatus", s.getStudentStatus());
            if (s.getSchoolClass() != null) {
                Map<String, Object> classMap = new LinkedHashMap<>();
                classMap.put("id", s.getSchoolClass().getId());
                classMap.put("name", s.getSchoolClass().getName());
                if (s.getSchoolClass().getGrade() != null) {
                    classMap.put("grade", Map.of("id", s.getSchoolClass().getGrade().getId(),
                            "name", s.getSchoolClass().getGrade().getName()));
                }
                m.put("schoolClass", classMap);
            }
            return m;
        }).collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    // ===== 鑰冨嫟鏌ヨ =====

    @GetMapping("/students/{id}/attendance-history")
    public ApiResponse<Map<String, Object>> studentAttendanceHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "60") int days) {
        Student student = studentService.findById(id);
        School school = currentUserService.getCurrentSchool();
        if (student.getSchool() == null || !Objects.equals(student.getSchool().getId(), school.getId())) {
            throw new IllegalArgumentException("无权限查看该学生");
        }

        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDate cutoff = LocalDate.now().minusDays(safeDays - 1L);
        List<Attendance> records = attendanceService.findByStudent(id).stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(cutoff))
                .sorted(Comparator.comparing(Attendance::getDate).reversed())
                .collect(Collectors.toList());

        long present = records.stream().filter(a -> "出勤".equals(normalizeAttendanceStatus(a.getStatus()))).count();
        long absent = records.stream().filter(a -> "缺勤".equals(normalizeAttendanceStatus(a.getStatus()))).count();
        long leave = records.stream().filter(a -> "请假".equals(normalizeAttendanceStatus(a.getStatus()))).count();
        long total = records.size();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("leave", leave);
        stats.put("rate", total > 0 ? String.format("%.1f", present * 100.0 / total) : "0.0");

        List<Map<String, Object>> list = records.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("date", a.getDate());
            m.put("status", normalizeAttendanceStatus(a.getStatus()));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", student.getId());
        result.put("studentName", student.getName());
        result.put("stats", stats);
        result.put("records", list);
        return ApiResponse.ok(result);
    }

    @GetMapping("/attendance")
    public ApiResponse<Map<Long, String>> attendance(@RequestParam Long classId,
                                                      @RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        List<Attendance> records = attendanceService.findByClassAndDate(classId, d);
        Map<Long, String> map = records.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Attendance::getStatus));
        return ApiResponse.ok(map);
    }

    // ===== 鑰冨嫟淇濆瓨 =====

    @PostMapping("/attendance/save-batch")
    public ApiResponse<String> saveAttendance(@RequestBody AttendanceBatchRequest req) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        LocalDate date = LocalDate.parse(req.getDate());
        Map<Long, String> statusMap = req.getRecords().stream()
                .collect(Collectors.toMap(AttendanceBatchRequest.Record::getStudentId,
                        AttendanceBatchRequest.Record::getStatus));
        attendanceService.saveAttendance(req.getClassId(), date, statusMap, username);
        return ApiResponse.ok("保存成功，共 " + statusMap.size() + " 条", null);
    }

    @Data
    static class AttendanceBatchRequest {
        private Long classId;
        private String date; // yyyy-MM-dd
        private List<Record> records;

        @Data
        static class Record {
            private Long studentId;
            private String status; // 鍑哄嫟/缂哄嫟/璇峰亣
        }
    }

    // ===== 浣撴祴鏌ヨ =====

    @GetMapping("/physical-tests")
    public ApiResponse<Map<Long, Object>> physicalTests(@RequestParam Long classId,
                                                         @RequestParam String academicYear,
                                                         @RequestParam String semester) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students;
        if (isElectiveType(sc.getType())) {
            String name = (sc.getGrade() != null ? sc.getGrade().getName() + "/" : "") + sc.getName();
            students = studentService.findByElectiveClassForTeacher(school, name);
        } else {
            students = studentService.findByClassIdForTeacher(school, classId);
        }
        Map<Long, Object> map = new HashMap<>();
        for (Student s : students) {
            physicalTestService.findExisting(s, academicYear, semester)
                    .ifPresent(pt -> map.put(s.getId(), pt));
        }
        return ApiResponse.ok(map);
    }

    // ===== 浣撴祴鎵归噺淇濆瓨 =====

    @PostMapping("/physical-tests/save-batch")
    public ApiResponse<String> savePhysicalTests(@RequestBody List<PhysicalTestItem> items) {
        School school = currentUserService.getCurrentSchool();
        String academicYear = items.isEmpty() ? "" : items.get(0).getAcademicYear();
        String semester = items.isEmpty() ? "" : items.get(0).getSemester();

        List<Student> students = new ArrayList<>();
        List<PhysicalTest> records = new ArrayList<>();
        for (PhysicalTestItem item : items) {
            studentService.findByIdOptional(item.getStudentId()).ifPresent(s -> {
                students.add(s);
                PhysicalTest pt = new PhysicalTest();
                pt.setHeight(item.getHeight());
                pt.setWeight(item.getWeight());
                pt.setLungCapacity(item.getLungCapacity());
                pt.setSprint50m(item.getSprint50m());
                pt.setSitReach(item.getSitReach());
                pt.setStandingJump(item.getStandingJump());
                pt.setPullUps(item.getPullUps());
                pt.setSitUps(item.getSitUps());
                pt.setRun800m(item.getRun800m());
                pt.setRun1000m(item.getRun1000m());
                pt.setTestDate(item.getTestDate() != null ? LocalDate.parse(item.getTestDate()) : LocalDate.now());
                pt.setRemark(item.getRemark());
                records.add(pt);
            });
        }
        int saved = physicalTestService.saveBatch(students, records, school, academicYear, semester);
        return ApiResponse.ok("保存成功，共 " + saved + " 条", null);
    }

    @Data
    static class PhysicalTestItem {
        private Long studentId;
        private String academicYear;
        private String semester;
        private String testDate;
        private Double height;
        private Double weight;
        private Integer lungCapacity;
        private Double sprint50m;
        private Double sitReach;
        private Double standingJump;
        private Integer pullUps;
        private Integer sitUps;
        private Double run800m;
        private Double run1000m;
        private String remark;
    }

    // ===== 鎴愮哗鏌ヨ =====

    @GetMapping("/term-grades")
    public ApiResponse<Map<Long, Object>> termGrades(@RequestParam Long classId,
                                                      @RequestParam String academicYear,
                                                      @RequestParam String semester) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students;
        if (isElectiveType(sc.getType())) {
            String name = (sc.getGrade() != null ? sc.getGrade().getName() + "/" : "") + sc.getName();
            students = studentService.findByElectiveClassForTeacher(school, name);
        } else {
            students = studentService.findByClassIdForTeacher(school, classId);
        }
        Map<Long, Object> map = new HashMap<>();
        for (Student s : students) {
            termGradeService.findExisting(s, academicYear, semester)
                    .ifPresent(tg -> map.put(s.getId(), tg));
        }
        return ApiResponse.ok(map);
    }

    // ===== 鎴愮哗鎵归噺淇濆瓨 =====

    @PostMapping("/term-grades/save-batch")
    public ApiResponse<String> saveTermGrades(@RequestBody TermGradeBatchRequest req) {
        School school = currentUserService.getCurrentSchool();
        List<Student> students = new ArrayList<>();
        List<TermGrade> records = new ArrayList<>();
        for (TermGradeBatchRequest.Item item : req.getItems()) {
            studentService.findByIdOptional(item.getStudentId()).ifPresent(s -> {
                students.add(s);
                TermGrade g = new TermGrade();
                g.setAttendanceScore(item.getAttendanceScore());
                g.setSkillScore(item.getSkillScore());
                g.setTheoryScore(item.getTheoryScore());
                g.setRemark(item.getRemark());
                records.add(g);
            });
        }
        int saved = termGradeService.saveBatch(students, records, school,
                req.getAcademicYear(), req.getSemester());
        return ApiResponse.ok("保存成功，共 " + saved + " 条", null);
    }

    @Data
    static class TermGradeBatchRequest {
        private String academicYear;
        private String semester;
        private List<Item> items;

        @Data
        static class Item {
            private Long studentId;
            private Double attendanceScore;
            private Double skillScore;
            private Double theoryScore;
            private String remark;
        }
    }

    private boolean isElectiveType(String type) {
        if (type == null) return false;
        String v = type.trim();
        return "选修课".equals(v) || v.contains("閫変慨") || v.contains("选修");
    }

    private String normalizeAttendanceStatus(String raw) {
        if (raw == null || raw.isBlank()) return "出勤";
        String status = raw.trim();
        if ("鍑哄嫟".equals(status) || "出勤".equals(status)) return "出勤";
        if ("缂哄嫟".equals(status) || "缺勤".equals(status)) return "缺勤";
        if ("璇峰亣".equals(status) || "请假".equals(status)) return "请假";
        return status;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (isBlank(keyword)) return true;
        if (source == null) return false;
        return normalizeText(source).contains(normalizeText(keyword));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String savePhoto(Long teacherId, MultipartFile photo) throws IOException {
        Path dir = Paths.get(uploadDir, "teachers").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String original = photo.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : ".jpg";
        if (!ext.matches("\\.(jpg|jpeg|png|webp|gif)")) {
            ext = ".jpg";
        }
        String filename = teacherId + ext;
        Path dest = dir.resolve(filename);
        photo.transferTo(dest.toFile());
        return "/uploads/teachers/" + filename;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度不能少于8位");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }
}
