package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.SelectionEventService;
import com.pe.assistant.service.StudentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentApiController {

    private final CourseService courseService;
    private final SelectionEventService eventService;
    private final CurrentUserService currentUserService;
    private final SelectionEventRepository eventRepo;
    private final MessageService messageService;
    private final StudentAccountService studentAccountService;

    private SelectionEvent findActiveEvent(Student student) {
        if (student.getSchool() == null) {
            return null;
        }
        List<SelectionEvent> events = eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool());
        SelectionEvent activeEvent = events
                .stream()
                .filter(e -> !"CLOSED".equals(e.getStatus()))
                .findFirst()
                .orElse(null);
        finalizeRound2IfEnded(activeEvent);
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> !"CLOSED".equals(e.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private SelectionEvent findLatestClosedEvent(Student student) {
        if (student.getSchool() == null) {
            return null;
        }
        SelectionEvent activeEvent = eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> !"CLOSED".equals(e.getStatus()))
                .findFirst()
                .orElse(null);
        finalizeRound2IfEnded(activeEvent);
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> "CLOSED".equals(e.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private void finalizeRound2IfEnded(SelectionEvent event) {
        if (event == null || !"ROUND2".equals(event.getStatus()) || event.getRound2End() == null) {
            return;
        }
        if (!LocalDateTime.now().isBefore(event.getRound2End())) {
            courseService.finalizeEndedRound2Event(event.getId());
        }
    }

    @GetMapping("/events/current")
    public ApiResponse<Map<String, Object>> currentEvent() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) {
            return ApiResponse.ok(null);
        }

        List<CourseSelection> mySelections = courseService.findMySelections(student, event);
        boolean hasPref1 = mySelections.stream()
                .anyMatch(s -> s.getRound() == 1 && s.getPreference() == 1 && !"CANCELLED".equals(s.getStatus()));
        boolean hasPref2 = mySelections.stream()
                .anyMatch(s -> s.getRound() == 1 && s.getPreference() == 2 && !"CANCELLED".equals(s.getStatus()));
        boolean round1SubmissionConfirmed = hasPref1 && mySelections.stream()
                .filter(s -> s.getRound() == 1 && (s.getPreference() == 1 || s.getPreference() == 2))
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .allMatch(s -> "PENDING".equals(s.getStatus()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", event.getId());
        result.put("name", event.getName());
        result.put("status", event.getStatus());
        result.put("round1Start", event.getRound1Start());
        result.put("round1End", event.getRound1End());
        result.put("round2Start", event.getRound2Start());
        result.put("round2End", event.getRound2End());
        result.put("inRound1", eventService.isInRound1(event));
        result.put("inRound2", eventService.isInRound2(event));
        result.put("hasPref1", hasPref1);
        result.put("hasPref2", hasPref2);
        result.put("round1SubmissionConfirmed", round1SubmissionConfirmed);
        return ApiResponse.ok(result);
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> courses() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) {
            return ApiResponse.ok(List.of());
        }

        List<CourseSelection> mySelections = courseService.findMySelections(student, event);
        Set<Long> myConfirmedIds = mySelections.stream()
                .filter(s -> "CONFIRMED".equals(s.getStatus()))
                .map(s -> s.getCourse().getId())
                .collect(Collectors.toSet());
        Map<Long, Integer> myPreferenceMap = mySelections.stream()
                .filter(s -> "DRAFT".equals(s.getStatus()) || "PENDING".equals(s.getStatus()) || "CONFIRMED".equals(s.getStatus()))
                .collect(Collectors.toMap(s -> s.getCourse().getId(), CourseSelection::getPreference, (a, b) -> a));

        List<Course> courses = courseService.findActiveCoursesForStudent(event, student);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Course course : courses) {
            Map<String, Object> item = toCourseMap(course, student);
            item.put("confirmed", myConfirmedIds.contains(course.getId()));
            item.put("myPreference", myPreferenceMap.getOrDefault(course.getId(), 0));
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/courses/requestable")
    public ApiResponse<Map<String, Object>> requestableCourses() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent closedEvent = findLatestClosedEvent(student);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canRequest", false);
        result.put("eventName", closedEvent != null ? closedEvent.getName() : null);
        result.put("reason", "");
        result.put("courses", List.of());

        if (closedEvent == null) {
            result.put("reason", "当前没有可申请的选课活动");
            return ApiResponse.ok(result);
        }

        boolean hasConfirmed = courseService.findMySelections(student, closedEvent)
                .stream()
                .anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
        if (hasConfirmed) {
            result.put("reason", "您已有确认的选课，无需申请");
            return ApiResponse.ok(result);
        }

        List<Map<String, Object>> courses = courseService.findByEvent(closedEvent).stream()
                .map(course -> toCourseMap(course, student))
                .collect(Collectors.toList());

        result.put("canRequest", true);
        result.put("courses", courses);
        return ApiResponse.ok(result);
    }

    @PostMapping("/courses/{courseId}/prefer")
    public ApiResponse<String> prefer(@PathVariable Long courseId, @RequestParam int preference) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) {
                return ApiResponse.error(400, "当前没有进行中的选课活动");
            }
            courseService.submitPreference(student, event.getId(), courseId, preference);
            return ApiResponse.ok("志愿提交成功");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/courses/save-draft")
    public ApiResponse<String> saveDraft() {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) {
                return ApiResponse.error(400, "当前没有进行中的选课活动");
            }
            courseService.saveRound1Draft(student, event.getId());
            return ApiResponse.ok("草稿已保存");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/courses/confirm")
    public ApiResponse<String> confirmRound1() {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) {
                return ApiResponse.error(400, "当前没有进行中的选课活动");
            }
            courseService.confirmRound1Selections(student, event.getId());
            return ApiResponse.ok("志愿已确认提交");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/courses/{courseId}/select")
    public ApiResponse<String> select(@PathVariable Long courseId) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) {
                return ApiResponse.error(400, "当前没有进行中的选课活动");
            }
            courseService.selectRound2(student, event.getId(), courseId);
            return ApiResponse.ok("抢课成功");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/selections/{selectionId}")
    public ApiResponse<String> drop(@PathVariable Long selectionId) {
        try {
            Student student = currentUserService.getCurrentStudent();
            courseService.dropCourse(student, selectionId);
            return ApiResponse.ok("退课成功");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/my-selections")
    public ApiResponse<List<Map<String, Object>>> mySelections() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) {
            return ApiResponse.ok(List.of());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseSelection selection : courseService.findMySelections(student, event)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", selection.getId());
            item.put("courseName", selection.getCourse().getName());
            item.put("courseId", selection.getCourse().getId());
            item.put("preference", selection.getPreference());
            item.put("round", selection.getRound());
            item.put("status", selection.getStatus());
            item.put("selectedAt", selection.getSelectedAt());
            item.put("confirmedAt", selection.getConfirmedAt());
            item.put("canDrop", courseService.canDropSelection(selection));
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/courses/{courseId}/request")
    public ApiResponse<String> requestCourse(@PathVariable Long courseId,
                                             @RequestBody(required = false) Map<String, Object> body) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent closedEvent = findLatestClosedEvent(student);
            if (closedEvent == null) {
                return ApiResponse.error(400, "当前没有可申请的选课活动");
            }

            boolean hasConfirmed = courseService.findMySelections(student, closedEvent)
                    .stream()
                    .anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
            if (hasConfirmed) {
                return ApiResponse.error(400, "您已有确认的选课，无需申请");
            }

            Course course = courseService.findById(courseId);
            if (course.getEvent() == null || !course.getEvent().getId().equals(closedEvent.getId())) {
                return ApiResponse.error(400, "该课程不属于当前活动");
            }

            String content = body != null && body.get("content") != null
                    ? String.valueOf(body.get("content"))
                    : "";
            messageService.sendCourseRequest(student, course, content);
            return ApiResponse.ok("申请已发送，请等待教师处理");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/messages/unread-count")
    public ApiResponse<Map<String, Long>> unreadMessageCount() {
        Student student = currentUserService.getCurrentStudent();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("unreadCount", messageService.getUnreadCount("STUDENT", student.getId()));
        return ApiResponse.ok(result);
    }

    @GetMapping("/messages")
    public ApiResponse<List<Map<String, Object>>> messages(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        Student student = currentUserService.getCurrentStudent();
        List<InternalMessage> list = messageService.getStudentInbox(student);
        if (unreadOnly) {
            list = list.stream()
                    .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()))
                    .collect(Collectors.toList());
        }
        return ApiResponse.ok(list.stream().map(this::toMessageMap).collect(Collectors.toList()));
    }

    @GetMapping("/messages/recipients")
    public ApiResponse<List<Map<String, Object>>> messageRecipients() {
        Student student = currentUserService.getCurrentStudent();
        List<Map<String, Object>> result = messageService.findTeachersBySchool(student.getSchool()).stream()
                .map(recipient -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", recipient.getId());
                    item.put("name", recipient.getName());
                    item.put("displayName", recipient.getDisplayName());
                    return item;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @PostMapping("/messages/send")
    public ApiResponse<String> sendMessage(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Student student = currentUserService.getCurrentStudent();
            Long teacherId = parseLong(body != null ? body.get("teacherId") : null);
            String subject = body != null && body.get("subject") != null
                    ? String.valueOf(body.get("subject")).trim()
                    : "";
            String content = body != null && body.get("content") != null
                    ? String.valueOf(body.get("content")).trim()
                    : "";

            if (teacherId == null) {
                return ApiResponse.error(400, "请选择收件教师");
            }
            if (subject.isBlank()) {
                return ApiResponse.error(400, "消息主题不能为空");
            }
            if (content.isBlank()) {
                return ApiResponse.error(400, "消息内容不能为空");
            }

            Teacher teacher = messageService.findTeacherMessageRecipient(student.getSchool(), teacherId);
            messageService.sendMessage(
                    "STUDENT", student.getId(), student.getName(),
                    "TEACHER", teacher.getId(), teacher.getName(),
                    subject, content, student.getSchool());
            return ApiResponse.ok("消息已发送");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/messages/{id}/read")
    public ApiResponse<String> markMessageRead(@PathVariable Long id) {
        try {
            Student student = currentUserService.getCurrentStudent();
            messageService.markStudentMessageRead(id, student);
            return ApiResponse.ok("已标记已读");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/password/change")
    public ApiResponse<String> changePassword(@RequestBody Map<String, Object> body) {
        try {
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            String oldPassword = body.get("oldPassword") != null ? String.valueOf(body.get("oldPassword")) : "";
            String newPassword = body.get("newPassword") != null ? String.valueOf(body.get("newPassword")) : "";
            if (oldPassword.isBlank() || newPassword.isBlank()) {
                return ApiResponse.error(400, "旧密码和新密码不能为空");
            }
            studentAccountService.changePassword(account, oldPassword, newPassword);
            return ApiResponse.ok("密码修改成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(400, "密码修改失败");
        }
    }

    private Map<String, Object> toCourseMap(Course course, Student student) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", course.getId());
        result.put("name", course.getName());
        result.put("description", course.getDescription());
        result.put("teacherName", course.getTeacher() != null ? course.getTeacher().getName() : null);
        result.put("totalCapacity", course.getTotalCapacity());
        result.put("currentCount", course.getCurrentCount());
        result.put("remaining", courseService.getRemainingCapacity(course, student));
        result.put("capacityMode", course.getCapacityMode());
        return result;
    }

    private Map<String, Object> toMessageMap(InternalMessage msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", msg.getId());
        result.put("subject", msg.getSubject());
        result.put("content", msg.getContent());
        result.put("type", msg.getType());
        result.put("status", msg.getStatus());
        result.put("isRead", msg.getIsRead());
        result.put("sentAt", msg.getSentAt());
        result.put("senderType", msg.getSenderType());
        result.put("senderId", msg.getSenderId());
        result.put("senderName", msg.getSenderName());
        result.put("relatedCourseId", msg.getRelatedCourseId());
        result.put("relatedCourseName", msg.getRelatedCourseName());
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
