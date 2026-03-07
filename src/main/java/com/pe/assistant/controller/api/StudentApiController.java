package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.*;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.SelectionEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentApiController {

    private final CourseService courseService;
    private final SelectionEventService eventService;
    private final CurrentUserService currentUserService;
    private final SelectionEventRepository eventRepo;

    private SelectionEvent findActiveEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream().filter(e -> !"CLOSED".equals(e.getStatus()))
                .findFirst().orElse(null);
    }

    /** 当前活动信息 */
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
        m.put("round1End",   event.getRound1End());
        m.put("round2Start", event.getRound2Start());
        m.put("round2End",   event.getRound2End());
        m.put("inRound1", eventService.isInRound1(event));
        m.put("inRound2", eventService.isInRound2(event));
        return ApiResponse.ok(m);
    }

    /** 可选课程列表（含剩余名额、已选状态） */
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("description", c.getDescription());
            m.put("teacherName", c.getTeacher() != null ? c.getTeacher().getName() : null);
            m.put("totalCapacity", c.getTotalCapacity());
            m.put("currentCount", c.getCurrentCount());
            m.put("remaining", courseService.getRemainingCapacity(c, student));
            m.put("capacityMode", c.getCapacityMode());
            m.put("confirmed", myConfirmedIds.contains(c.getId()));
            m.put("myPreference", myPreferenceMap.getOrDefault(c.getId(), 0));
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 第一轮提交志愿 */
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

    /** 第二轮抢课 */
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

    /** 退课 */
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

    /** 我的选课记录 */
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
}
