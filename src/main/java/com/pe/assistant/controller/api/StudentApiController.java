package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MessageService;
import com.pe.assistant.service.SelectionEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    private SelectionEvent findActiveEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream().filter(e -> !"CLOSED".equals(e.getStatus()))
                .findFirst().orElse(null);
    }

    private SelectionEvent findLatestClosedEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream().filter(e -> "CLOSED".equals(e.getStatus()))
                .findFirst().orElse(null);
    }

    @GetMapping("/events/current")
    public ApiResponse<Map<String, Object>> currentEvent() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) return ApiResponse.ok(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", event.getId());
        m.put("name", event.getName());
        m.put("status", event.getStatus());
        m.put("round1Start", event.getRound1Start());
        m.put("round1End", event.getRound1End());
        m.put("round2Start", event.getRound2Start());
        m.put("round2End", event.getRound2End());
        m.put("inRound1", eventService.isInRound1(event));
        m.put("inRound2", eventService.isInRound2(event));
        return ApiResponse.ok(m);
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> courses() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) return ApiResponse.ok(List.of());

        List<CourseSelection> mySelections = courseService.findMySelections(student, event);
        Set<Long> myConfirmedIds = mySelections.stream()
                .filter(s -> "CONFIRMED".equals(s.getStatus()))
                .map(s -> s.getCourse().getId()).collect(Collectors.toSet());
        Map<Long, Integer> myPreferenceMap = mySelections.stream()
                .filter(s -> "PENDING".equals(s.getStatus()) || "CONFIRMED".equals(s.getStatus()))
                .collect(Collectors.toMap(s -> s.getCourse().getId(), CourseSelection::getPreference, (a, b) -> a));

        List<Course> courses = courseService.findActiveCoursesForStudent(event, student);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) {
            Map<String, Object> m = toCourseMap(c, student);
            m.put("confirmed", myConfirmedIds.contains(c.getId()));
            m.put("myPreference", myPreferenceMap.getOrDefault(c.getId(), 0));
            result.add(m);
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
                .stream().anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
        if (hasConfirmed) {
            result.put("reason", "您已有确认的选课，无需申请");
            return ApiResponse.ok(result);
        }

        List<Course> allCourses = courseService.findByEvent(closedEvent);
        List<Map<String, Object>> courseList = allCourses.stream()
                .map(c -> toCourseMap(c, student))
                .collect(Collectors.toList());

        result.put("canRequest", true);
        result.put("courses", courseList);
        return ApiResponse.ok(result);
    }

    @PostMapping("/courses/{courseId}/prefer")
    public ApiResponse<String> prefer(@PathVariable Long courseId,
                                      @RequestParam int preference) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) return ApiResponse.error(400, "当前没有进行中的选课活动");
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
            if (event == null) return ApiResponse.error(400, "当前没有进行中的选课活动");
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
        if (event == null) return ApiResponse.ok(List.of());

        List<CourseSelection> list = courseService.findMySelections(student, event);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseSelection sel : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sel.getId());
            m.put("courseName", sel.getCourse().getName());
            m.put("courseId", sel.getCourse().getId());
            m.put("preference", sel.getPreference());
            m.put("round", sel.getRound());
            m.put("status", sel.getStatus());
            m.put("selectedAt", sel.getSelectedAt());
            m.put("confirmedAt", sel.getConfirmedAt());
            result.add(m);
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
                    .stream().anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
            if (hasConfirmed) {
                return ApiResponse.error(400, "您已有确认的选课，无需申请");
            }

            Course course = courseService.findById(courseId);
            if (course.getEvent() == null || !course.getEvent().getId().equals(closedEvent.getId())) {
                return ApiResponse.error(400, "该课程不属于当前活动");
            }

            String content = "";
            if (body != null && body.get("content") != null) {
                content = String.valueOf(body.get("content"));
            }
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
    public ApiResponse<List<Map<String, Object>>> messages(
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Student student = currentUserService.getCurrentStudent();
        List<InternalMessage> list = messageService.getStudentInbox(student);
        if (unreadOnly) {
            list = list.stream()
                    .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()))
                    .collect(Collectors.toList());
        }
        List<Map<String, Object>> result = list.stream()
                .map(this::toMessageMap)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
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
            Student student = currentUserService.getCurrentStudent();
            String oldPassword = body.get("oldPassword") != null
                    ? String.valueOf(body.get("oldPassword"))
                    : "";
            String newPassword = body.get("newPassword") != null
                    ? String.valueOf(body.get("newPassword"))
                    : "";

            if (oldPassword.isBlank() || newPassword.isBlank()) {
                return ApiResponse.error(400, "旧密码和新密码不能为空");
            }
            if (!passwordEncoder.matches(oldPassword, student.getPassword())) {
                return ApiResponse.error(400, "旧密码不正确");
            }
            validateStudentPassword(newPassword);
            if (passwordEncoder.matches(newPassword, student.getPassword())) {
                return ApiResponse.error(400, "新密码不能与旧密码相同");
            }

            student.setPassword(passwordEncoder.encode(newPassword));
            studentRepository.save(student);
            return ApiResponse.ok("密码修改成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(400, "密码修改失败");
        }
    }

    private void validateStudentPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度不能少于 8 位");
        }
        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }

    private Map<String, Object> toCourseMap(Course course, Student student) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", course.getId());
        m.put("name", course.getName());
        m.put("description", course.getDescription());
        m.put("teacherName", course.getTeacher() != null ? course.getTeacher().getName() : null);
        m.put("totalCapacity", course.getTotalCapacity());
        m.put("currentCount", course.getCurrentCount());
        m.put("remaining", courseService.getRemainingCapacity(course, student));
        m.put("capacityMode", course.getCapacityMode());
        return m;
    }

    private Map<String, Object> toMessageMap(InternalMessage msg) {
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
        return m;
    }
}
