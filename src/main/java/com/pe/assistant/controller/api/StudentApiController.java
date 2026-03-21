package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.SelectionEventService;
import com.pe.assistant.service.StudentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> "CLOSED".equals(e.getStatus()))
                .findFirst()
                .orElse(null);
    }

    @GetMapping("/events/current")
    public ApiResponse<Map<String, Object>> currentEvent() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) {
            return ApiResponse.ok(null);
        }

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
                .filter(s -> "PENDING".equals(s.getStatus()) || "CONFIRMED".equals(s.getStatus()))
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
}
