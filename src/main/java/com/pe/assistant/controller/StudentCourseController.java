package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentCourseController {

    private final CourseService courseService;
    private final SelectionEventService eventService;
    private final CurrentUserService currentUserService;
    private final SelectionEventRepository eventRepo;
    private final MessageService messageService;

    /**
     * 找学生所在学校最近的一个活动（非 CLOSED）。
     * 第三轮页面用：找最近的 CLOSED 活动（若学生无确认选课）。
     */
    private SelectionEvent findActiveEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> !"CLOSED".equals(e.getStatus()))
                .findFirst().orElse(null);
    }

    /** 找最新的关闭活动（用于第三轮） */
    private SelectionEvent findLatestClosedEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> "CLOSED".equals(e.getStatus()))
                .findFirst().orElse(null);
    }

    // ===== 选课主页 =====

    @GetMapping("/courses")
    public String courses(Model model) {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);

        // 如果无进行中活动，检查是否有关闭的活动（用于第三轮）
        if (event == null) {
            SelectionEvent closedEvent = findLatestClosedEvent(student);
            if (closedEvent != null) {
                List<CourseSelection> mySelections = courseService.findMySelections(student, closedEvent);
                boolean hasConfirmed = mySelections.stream()
                        .anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
                if (!hasConfirmed) {
                    // 展示第三轮申请页面
                    List<Course> allCourses = courseService.findByEvent(closedEvent);
                    model.addAttribute("event", closedEvent);
                    model.addAttribute("courses", allCourses);
                    model.addAttribute("mySelections", mySelections);
                    model.addAttribute("student", student);
                    model.addAttribute("inRound3", true);
                    model.addAttribute("unreadCount",
                            messageService.getUnreadCount("STUDENT", student.getId()));
                    model.addAttribute("remainingMap",
                            allCourses.stream().collect(java.util.stream.Collectors.toMap(
                                    Course::getId,
                                    c -> courseService.getRemainingCapacity(c, student))));
                    return "student/courses";
                }
            }
            model.addAttribute("noEvent", true);
            return "student/courses";
        }

        List<Course> courses = courseService.findActiveCoursesForStudent(event, student);
        List<CourseSelection> mySelections = courseService.findMySelections(student, event);
        boolean hasConfirmed = mySelections.stream().anyMatch(s -> "CONFIRMED".equals(s.getStatus()));

        model.addAttribute("event", event);
        model.addAttribute("courses", courses);
        model.addAttribute("mySelections", mySelections);
        model.addAttribute("inRound1", eventService.isInRound1(event));
        model.addAttribute("inRound2", eventService.isInRound2(event));
        model.addAttribute("inRound3", false);
        model.addAttribute("hasConfirmed", hasConfirmed);
        model.addAttribute("student", student);
        model.addAttribute("unreadCount", messageService.getUnreadCount("STUDENT", student.getId()));
        model.addAttribute("remainingMap",
                courses.stream().collect(java.util.stream.Collectors.toMap(
                        Course::getId,
                        c -> courseService.getRemainingCapacity(c, student))));
        return "student/courses";
    }

    // ===== 第一轮提交志愿 =====

    @PostMapping("/courses/{courseId}/prefer")
    public String prefer(@PathVariable Long courseId,
                         @RequestParam int preference,
                         RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) throw new RuntimeException("当前没有进行中的选课活动");
            courseService.submitPreference(student, event.getId(), courseId, preference);
            ra.addFlashAttribute("success", "志愿提交成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/courses";
    }

    // ===== 第二轮抢课 =====

    @PostMapping("/courses/{courseId}/select")
    public String select(@PathVariable Long courseId, RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) throw new RuntimeException("当前没有进行中的选课活动");
            courseService.selectRound2(student, event.getId(), courseId);
            ra.addFlashAttribute("success", "抢课成功！");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/courses";
    }

    // ===== 我的选课 =====

    @GetMapping("/my-courses")
    public String myCourses(Model model) {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        // 若无进行中活动，尝试取最近的关闭活动（展示历史选课结果）
        if (event == null) {
            event = findLatestClosedEvent(student);
        }
        model.addAttribute("student", student);
        if (event != null) {
            model.addAttribute("event", event);
            model.addAttribute("mySelections", courseService.findMySelections(student, event));
        }
        return "student/my-courses";
    }

    // ===== 退课 =====

    @PostMapping("/selections/{selectionId}/drop")
    public String drop(@PathVariable Long selectionId, RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            courseService.dropCourse(student, selectionId);
            ra.addFlashAttribute("success", "退课成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/my-courses";
    }

    // ===== 第三轮：向教师发选课申请 =====

    @PostMapping("/courses/{courseId}/request")
    public String sendCourseRequest(@PathVariable Long courseId,
                                    @RequestParam(defaultValue = "") String content,
                                    RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            // 校验：只能对最新关闭活动的课程申请，且学生尚无确认选课
            SelectionEvent closedEvent = findLatestClosedEvent(student);
            if (closedEvent == null) throw new RuntimeException("当前没有可申请的选课活动");
            boolean hasConfirmed = courseService.findMySelections(student, closedEvent)
                    .stream().anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
            if (hasConfirmed) throw new RuntimeException("您已有确认的选课，无需申请");
            Course course = courseService.findById(courseId);
            if (!course.getEvent().getId().equals(closedEvent.getId())) {
                throw new RuntimeException("该课程不属于当前活动");
            }
            messageService.sendCourseRequest(student, course, content);
            ra.addFlashAttribute("success", "申请已发送，请等待教师处理");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/courses";
    }
}
