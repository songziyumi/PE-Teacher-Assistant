package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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

    // ===== 年级列表 =====

    @GetMapping("/grades")
    public ApiResponse<List<Grade>> grades() {
        School school = currentUserService.getCurrentSchool();
        return ApiResponse.ok(gradeService.findAll(school));
    }

    // ===== 全校行政班（用于学生班级修改） =====

    @GetMapping("/school-classes")
    public ApiResponse<List<Map<String, Object>>> schoolClasses() {
        School school = currentUserService.getCurrentSchool();
        List<Map<String, Object>> result = classService.findAll(school).stream()
                .filter(c -> !"选修课".equals(c.getType()))
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

    // ===== 全校选修班（用于学生选修班修改） =====

    @GetMapping("/elective-classes")
    public ApiResponse<List<Map<String, Object>>> electiveClasses() {
        School school = currentUserService.getCurrentSchool();
        List<Map<String, Object>> result = classService.findAll(school).stream()
                .filter(c -> "选修课".equals(c.getType()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("gradeName", c.getGrade() != null ? c.getGrade().getName() : null);
                    m.put("gradeId", c.getGrade() != null ? c.getGrade().getId() : null);
                    // 存储格式: "年级/班级名" 或 "班级名"
                    String storedName = c.getGrade() != null
                            ? c.getGrade().getName() + "/" + c.getName()
                            : c.getName();
                    m.put("storedName", storedName);
                    return m;
                }).collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    // ===== 班级列表 =====

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

    // ===== 选课审批中心 =====

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
    public ApiResponse<List<Map<String, Object>>> messages(
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        List<InternalMessage> list = messageService.getTeacherInbox(teacher);
        if (unreadOnly) {
            list = list.stream()
                    .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()))
                    .collect(Collectors.toList());
        }
        List<Map<String, Object>> result = list.stream()
                .map(this::toTeacherMessageMap)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
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

    // ===== 学生班级修改（教师权限） =====

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
        String electiveClass = body.containsKey("electiveClass")
                ? (String) body.get("electiveClass")
                : current.getElectiveClass();
        try {
            studentService.update(id, name, gender, studentNo, current.getIdCard(),
                    electiveClass, classId, studentStatus);
            return ResponseEntity.ok(ApiResponse.ok("修改成功", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ===== 班级学生列表（含选修班信息） =====

    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> students(@PathVariable Long classId) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students;
        if ("选修课".equals(sc.getType())) {
            String name = (sc.getGrade() != null ? sc.getGrade().getName() + "/" : "") + sc.getName();
            students = studentService.findByElectiveClassForTeacher(school, name);
        } else {
            students = studentService.findByClassIdForTeacher(school, classId);
        }
        List<Map<String, Object>> result = students.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("studentNo", s.getStudentNo());
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

    // ===== 考勤查询 =====

    @GetMapping("/attendance")
    public ApiResponse<Map<Long, String>> attendance(@RequestParam Long classId,
                                                      @RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        List<Attendance> records = attendanceService.findByClassAndDate(classId, d);
        Map<Long, String> map = records.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Attendance::getStatus));
        return ApiResponse.ok(map);
    }

    // ===== 考勤保存 =====

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
            private String status; // 出勤/缺勤/请假
        }
    }

    // ===== 体测查询 =====

    @GetMapping("/physical-tests")
    public ApiResponse<Map<Long, Object>> physicalTests(@RequestParam Long classId,
                                                         @RequestParam String academicYear,
                                                         @RequestParam String semester) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students;
        if ("选修课".equals(sc.getType())) {
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

    // ===== 体测批量保存 =====

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

    // ===== 成绩查询 =====

    @GetMapping("/term-grades")
    public ApiResponse<Map<Long, Object>> termGrades(@RequestParam Long classId,
                                                      @RequestParam String academicYear,
                                                      @RequestParam String semester) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass sc = classService.findById(classId);
        List<Student> students;
        if ("选修课".equals(sc.getType())) {
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

    // ===== 成绩批量保存 =====

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
}
