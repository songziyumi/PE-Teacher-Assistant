package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher/courses")
@RequiredArgsConstructor
public class TeacherCourseController {

    private final CourseService courseService;
    private final SelectionEventService eventService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping
    public String list(Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        School school = teacher.getSchool();
        List<SelectionEvent> events = eventService.findBySchool(school);
        // 每个活动下本教师负责的课程
        Map<Long, List<Course>> coursesByEvent = new java.util.LinkedHashMap<>();
        for (SelectionEvent ev : events) {
            List<Course> myCourses = courseService.findByEvent(ev).stream()
                    .filter(c -> c.getTeacher() != null && c.getTeacher().getId().equals(teacher.getId()))
                    .toList();
            coursesByEvent.put(ev.getId(), myCourses);
        }
        model.addAttribute("events", events);
        model.addAttribute("coursesByEvent", coursesByEvent);
        model.addAttribute("teacher", teacher);
        return "teacher/courses";
    }

    @GetMapping("/{eventId}/courses/{courseId}/enrollments")
    public String enrollments(@PathVariable Long eventId,
                              @PathVariable Long courseId,
                              Model model) {
        SelectionEvent event = eventService.findById(eventId);
        Course course = courseService.findById(courseId);
        model.addAttribute("event", event);
        model.addAttribute("course", course);
        model.addAttribute("enrollments", courseService.findEnrollments(course));
        return "teacher/course-enrollments";
    }
}
