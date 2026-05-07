package com.pe.assistant.controller;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.SelectionEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
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
        Map<Long, List<Course>> coursesByEvent = new LinkedHashMap<>();
        Map<Long, Integer> confirmedCountByCourse = new LinkedHashMap<>();
        Map<Long, String> genderLimitLabelMap = new LinkedHashMap<>();

        for (SelectionEvent event : events) {
            List<Course> myCourses = courseService.findByEvent(event).stream()
                    .filter(course -> course.getTeacher() != null && course.getTeacher().getId().equals(teacher.getId()))
                    .toList();
            coursesByEvent.put(event.getId(), myCourses);
            for (Course course : myCourses) {
                confirmedCountByCourse.put(course.getId(), courseService.countConfirmedUniqueEnrollments(course));
                genderLimitLabelMap.put(course.getId(), courseService.getGenderLimitLabel(course.getGenderLimit()));
            }
        }

        model.addAttribute("events", events);
        model.addAttribute("coursesByEvent", coursesByEvent);
        model.addAttribute("confirmedCountByCourse", confirmedCountByCourse);
        model.addAttribute("genderLimitLabelMap", genderLimitLabelMap);
        model.addAttribute("teacher", teacher);
        return "teacher/courses";
    }

    @GetMapping("/{eventId}/courses/{courseId}/enrollments")
    public String enrollments(@PathVariable Long eventId,
                              @PathVariable Long courseId,
                              Model model) {
        SelectionEvent event = eventService.findById(eventId);
        Course course = courseService.findById(courseId);
        int confirmedEnrollmentCount = courseService.countConfirmedUniqueEnrollments(course);
        List<CourseSelection> enrollments = courseService.findEnrollments(course);

        model.addAttribute("event", event);
        model.addAttribute("course", course);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("selectionReasonMap", courseService.buildSelectionReasonMap(enrollments));
        model.addAttribute("selectionStatusLabelMap", courseService.buildTeacherSelectionStatusLabelMap(enrollments));
        model.addAttribute("confirmedEnrollmentCount", confirmedEnrollmentCount);
        model.addAttribute("remainingEnrollmentCapacity", Math.max(0, course.getTotalCapacity() - confirmedEnrollmentCount));
        model.addAttribute("overflowEnrollmentCount", Math.max(0, confirmedEnrollmentCount - course.getTotalCapacity()));
        model.addAttribute("genderLimitLabel", courseService.getGenderLimitLabel(course.getGenderLimit()));
        return "teacher/course-enrollments";
    }
}
