package com.pe.assistant.controller;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.controller.support.CourseSelectionPromptHelper;
import com.pe.assistant.entity.*;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/student")
@Slf4j
@RequiredArgsConstructor
public class StudentCourseController {

    private final CourseService courseService;
    private final SelectionEventService eventService;
    private final CurrentUserService currentUserService;
    private final SelectionEventRepository eventRepo;
    private final MessageService messageService;

    private Map<Long, Boolean> buildEligibleCourseMap(List<Course> courses, Student student) {
        Map<Long, Boolean> eligibleCourseMap = new LinkedHashMap<>();
        for (Course course : courses) {
            eligibleCourseMap.put(course.getId(), courseService.isStudentEligibleForCourse(student, course));
        }
        return eligibleCourseMap;
    }

    private Map<Long, String> buildGenderLimitLabelMap(List<Course> courses) {
        Map<Long, String> genderLimitLabelMap = new LinkedHashMap<>();
        for (Course course : courses) {
            genderLimitLabelMap.put(course.getId(), courseService.getGenderLimitLabel(course.getGenderLimit()));
        }
        return genderLimitLabelMap;
    }

    private Map<Long, String> buildIneligibleMessageMap(List<Course> courses, Student student) {
        Map<Long, String> ineligibleMessageMap = new LinkedHashMap<>();
        for (Course course : courses) {
            if (!courseService.isStudentEligibleForCourse(student, course)) {
                ineligibleMessageMap.put(course.getId(), courseService.getIneligibleCourseMessage(course));
            }
        }
        return ineligibleMessageMap;
    }

    /**
     * 找学生所在学校最近的一个活动（非 CLOSED）。
     * 第三轮页面用：找最近的 CLOSED 活动（若学生无确认选课）。
     */
    private SelectionEvent findActiveEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> !"CLOSED".equals(e.getStatus()))
                .filter(e -> eventService.canStudentAccessEvent(e, student))
                .findFirst().orElse(null);
    }

    /** 找最新的关闭活动（用于第三轮） */
    private SelectionEvent findLatestClosedEvent(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> "CLOSED".equals(e.getStatus()))
                .filter(e -> eventService.canStudentAccessEvent(e, student))
                .findFirst().orElse(null);
    }

    private SelectionEvent findLatestEventWithSelections(Student student) {
        if (student.getSchool() == null) return null;
        return eventRepo.findBySchoolOrderByCreatedAtDesc(student.getSchool())
                .stream()
                .filter(e -> eventService.canStudentAccessEvent(e, student))
                .filter(e -> !courseService.findMySelections(student, e).isEmpty())
                .findFirst().orElse(null);
    }

    private boolean isRound3NotStarted(SelectionEvent event) {
        return event != null
                && "CLOSED".equals(event.getStatus())
                && event.getRound3Start() != null
                && LocalDateTime.now().isBefore(event.getRound3Start());
    }

    private boolean isRound3Ended(SelectionEvent event) {
        return event != null
                && "CLOSED".equals(event.getStatus())
                && event.getRound3End() != null
                && !LocalDateTime.now().isBefore(event.getRound3End());
    }

    private String validateRound3Window(SelectionEvent event) {
        if (event == null) {
            return "\u5f53\u524d\u6ca1\u6709\u53ef\u7533\u8bf7\u7684\u9009\u8bfe\u6d3b\u52a8";
        }
        LocalDateTime now = LocalDateTime.now();
        if (event.getRound3Start() == null || now.isBefore(event.getRound3Start())) {
            return "\u7b2c\u4e09\u8f6e\u9009\u8bfe\u7533\u8bf7\u5c1a\u672a\u5f00\u59cb";
        }
        if (event.getRound3End() == null || !now.isBefore(event.getRound3End())) {
            return "\u7b2c\u4e09\u8f6e\u9009\u8bfe\u7533\u8bf7\u5df2\u7ed3\u675f";
        }
        return null;
    }

    // ===== 选课主页 =====

    @GetMapping("/courses")
    public String courses(Model model) {
        Student student = currentUserService.getCurrentStudent();
        model.addAttribute("student", student);
        model.addAttribute("inRound1", false);
        model.addAttribute("inRound2", false);
        model.addAttribute("inRound3", false);
        model.addAttribute("round3NotStarted", false);
        model.addAttribute("round3Ended", false);
        model.addAttribute("hasConfirmed", false);
        model.addAttribute("hasPref1", false);
        model.addAttribute("hasPref2", false);
        model.addAttribute("round1SubmissionConfirmed", false);
        model.addAttribute("mySelections", List.of());
        model.addAttribute("droppableSelectionIds", List.of());
        model.addAttribute("confirmedSelectionCount", 0L);
        model.addAttribute("pendingSelectionCount", 0L);
        model.addAttribute("lotteryFailSelectionCount", 0L);
        model.addAttribute("courses", List.of());
        try {
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
                    List<Course> requestableCourses = allCourses.stream()
                            .filter(course -> course.getTeacher() != null)
                            .toList();
                    Map<Long, Integer> confirmedCountMap = buildConfirmedCountMap(requestableCourses);
                    Map<Long, Boolean> eligibleCourseMap = buildEligibleCourseMap(requestableCourses, student);
                    Map<Long, String> genderLimitLabelMap = buildGenderLimitLabelMap(requestableCourses);
                    Map<Long, String> ineligibleMessageMap = buildIneligibleMessageMap(requestableCourses, student);
                    Map<Long, InternalMessage> round3RequestMap = messageService.getLatestStudentCourseRequests(student, closedEvent);
                    boolean inRound3 = eventService.isInRound3(closedEvent);
                    model.addAttribute("event", closedEvent);
                    model.addAttribute("courses", requestableCourses);
                    model.addAttribute("mySelections", mySelections);
                    model.addAttribute("droppableSelectionIds", mySelections.stream()
                            .filter(courseService::canDropSelection)
                            .map(CourseSelection::getId)
                            .collect(Collectors.toList()));
                    model.addAttribute("confirmedSelectionCount",
                            mySelections.stream().filter(s -> "CONFIRMED".equals(s.getStatus())).count());
                    model.addAttribute("pendingSelectionCount",
                            mySelections.stream().filter(s -> "PENDING".equals(s.getStatus())).count());
                    model.addAttribute("lotteryFailSelectionCount",
                            mySelections.stream().filter(s -> "LOTTERY_FAIL".equals(s.getStatus())).count());
                    model.addAttribute("inRound3", inRound3);
                    model.addAttribute("round3NotStarted", !inRound3 && isRound3NotStarted(closedEvent));
                    model.addAttribute("round3Ended", !inRound3 && isRound3Ended(closedEvent));
                    model.addAttribute("confirmedCountMap", confirmedCountMap);
                    model.addAttribute("eligibleCourseMap", eligibleCourseMap);
                    model.addAttribute("genderLimitLabelMap", genderLimitLabelMap);
                    model.addAttribute("ineligibleMessageMap", ineligibleMessageMap);
                    model.addAttribute("round3RequestMap", round3RequestMap);
                    model.addAttribute("unreadCount",
                            messageService.getUnreadCount("STUDENT", student.getId()));
                    model.addAttribute("remainingMap",
                            requestableCourses.stream().collect(java.util.stream.Collectors.toMap(
                                    Course::getId,
                                    c -> courseService.getRemainingCapacity(c, student))));
                    return "student/courses";
                }
            }
            model.addAttribute("noEvent", true);
            return "student/courses";
        }

        List<Course> courses = courseService.findActiveCoursesForStudent(event, student);
        Map<Long, Integer> confirmedCountMap = buildConfirmedCountMap(courses);
        Map<Long, Boolean> eligibleCourseMap = buildEligibleCourseMap(courses, student);
        Map<Long, String> genderLimitLabelMap = buildGenderLimitLabelMap(courses);
        Map<Long, String> ineligibleMessageMap = buildIneligibleMessageMap(courses, student);
        List<CourseSelection> mySelections = courseService.findMySelections(student, event);
        boolean hasConfirmed = mySelections.stream().anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
        boolean hasPref1 = mySelections.stream()
                .anyMatch(s -> s.getRound() == 1 && s.getPreference() == 1 && !"CANCELLED".equals(s.getStatus()));
        boolean hasPref2 = mySelections.stream()
                .anyMatch(s -> s.getRound() == 1 && s.getPreference() == 2 && !"CANCELLED".equals(s.getStatus()));
        boolean round1SubmissionConfirmed = hasPref1 && mySelections.stream()
                .filter(s -> s.getRound() == 1 && (s.getPreference() == 1 || s.getPreference() == 2))
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .allMatch(s -> "PENDING".equals(s.getStatus()));

        model.addAttribute("event", event);
        model.addAttribute("courses", courses);
        model.addAttribute("mySelections", mySelections);
        model.addAttribute("droppableSelectionIds", mySelections.stream()
                .filter(courseService::canDropSelection)
                .map(CourseSelection::getId)
                .collect(Collectors.toList()));
        model.addAttribute("confirmedSelectionCount",
                mySelections.stream().filter(s -> "CONFIRMED".equals(s.getStatus())).count());
        model.addAttribute("pendingSelectionCount",
                mySelections.stream().filter(s -> "PENDING".equals(s.getStatus())).count());
        model.addAttribute("lotteryFailSelectionCount",
                mySelections.stream().filter(s -> "LOTTERY_FAIL".equals(s.getStatus())).count());
        model.addAttribute("inRound1", eventService.isInRound1(event));
        model.addAttribute("inRound2", eventService.isInRound2(event));
        model.addAttribute("inRound3", false);
        model.addAttribute("round3NotStarted", false);
        model.addAttribute("round3Ended", false);
        model.addAttribute("hasConfirmed", hasConfirmed);
        model.addAttribute("hasPref1", hasPref1);
        model.addAttribute("hasPref2", hasPref2);
        model.addAttribute("round1SubmissionConfirmed", round1SubmissionConfirmed);
        model.addAttribute("unreadCount", messageService.getUnreadCount("STUDENT", student.getId()));
        model.addAttribute("confirmedCountMap", confirmedCountMap);
        model.addAttribute("eligibleCourseMap", eligibleCourseMap);
        model.addAttribute("genderLimitLabelMap", genderLimitLabelMap);
        model.addAttribute("ineligibleMessageMap", ineligibleMessageMap);
        model.addAttribute("remainingMap",
                courses.stream().collect(java.util.stream.Collectors.toMap(
                        Course::getId,
                        c -> courseService.getRemainingCapacity(c, student))));
        return "student/courses";
        } catch (Exception ex) {
            log.error("student.courses.render.failed studentId={} schoolId={} message={}",
                    student != null ? student.getId() : null,
                    student != null && student.getSchool() != null ? student.getSchool().getId() : null,
                    ex.getMessage(),
                    ex);
            model.addAttribute("noEvent", true);
            model.addAttribute("error", "课程页加载失败，请刷新后重试");
            return "student/courses";
        }
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
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
        return "redirect:/student/courses";
    }

    // ===== 第二轮抢课 =====

    @PostMapping("/courses/save-draft")
    public String saveDraft(RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) throw new RuntimeException("当前没有进行中的选课活动");
            courseService.saveRound1Draft(student, event.getId());
            ra.addFlashAttribute("success", "草稿已保存");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
        return "redirect:/student/courses";
    }

    @PostMapping("/courses/confirm")
    public String confirmRound1(RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) throw new RuntimeException("当前没有进行中的选课活动");
            courseService.confirmRound1Selections(student, event.getId());
            ra.addFlashAttribute("success", "志愿已确认提交，系统将按已确认志愿参与第一轮抽签");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
        return "redirect:/student/courses";
    }

    @PostMapping("/courses/{courseId}/select")
    public String select(@PathVariable Long courseId, RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) throw new RuntimeException("\u5f53\u524d\u6ca1\u6709\u8fdb\u884c\u4e2d\u7684\u9009\u8bfe\u6d3b\u52a8");
            courseService.selectRound2(student, event.getId(), courseId);
            ra.addFlashAttribute("success", "\u62a2\u8bfe\u6210\u529f\uff01");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
        return "redirect:/student/courses";
    }

    @PostMapping("/courses/{courseId}/select-ajax")
    @ResponseBody
    public ApiResponse<String> selectAjax(@PathVariable Long courseId) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent event = findActiveEvent(student);
            if (event == null) {
                return ApiResponse.error(400, "\u5f53\u524d\u6ca1\u6709\u8fdb\u884c\u4e2d\u7684\u9009\u8bfe\u6d3b\u52a8");
            }
            courseService.selectRound2(student, event.getId(), courseId);
            return ApiResponse.ok("\u62a2\u8bfe\u6210\u529f\uff01");
        } catch (Exception e) {
            return ApiResponse.error(400, CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
    }

    // ===== 我的选课 =====

    @GetMapping("/my-courses")
    public String myCourses(Model model) {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findLatestEventWithSelections(student);
        if (event == null) {
            event = findActiveEvent(student);
        }
        // 若无进行中活动，尝试取最近的关闭活动（展示历史选课结果）
        if (event == null) {
            event = findLatestClosedEvent(student);
        }
        model.addAttribute("student", student);
        if (event != null) {
            List<CourseSelection> mySelections = courseService.findMySelections(student, event);
            model.addAttribute("event", event);
            model.addAttribute("mySelections", mySelections);
            model.addAttribute("droppableSelectionIds", mySelections.stream()
                    .filter(courseService::canDropSelection)
                    .map(CourseSelection::getId)
                    .collect(Collectors.toList()));
        }
        return "student/my-courses";
    }

    // ===== 退课 =====

    @PostMapping("/selections/{selectionId}/drop")
    public String drop(@PathVariable Long selectionId,
                       @RequestParam(defaultValue = "/student/my-courses") String returnTo,
                       RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            courseService.dropCourse(student, selectionId);
            ra.addFlashAttribute("success", "\u9000\u8bfe\u6210\u529f");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
        if (returnTo == null || returnTo.isBlank() || !returnTo.startsWith("/student")) {
            returnTo = "/student/my-courses";
        }
        return "redirect:" + returnTo;
    }

    // ===== 第三轮：向教师发选课申请 =====

    @PostMapping("/courses/{courseId}/request")
    public String sendCourseRequest(@PathVariable Long courseId,
                                    @RequestParam(defaultValue = "") String content,
                                    RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent closedEvent = findLatestClosedEvent(student);
            if (closedEvent == null) {
                throw new RuntimeException("\u5f53\u524d\u6ca1\u6709\u53ef\u7533\u8bf7\u7684\u9009\u8bfe\u6d3b\u52a8");
            }
            String round3WindowError = validateRound3Window(closedEvent);
            if (round3WindowError != null) {
                throw new RuntimeException(round3WindowError);
            }
            boolean hasConfirmed = courseService.findMySelections(student, closedEvent)
                    .stream()
                    .anyMatch(s -> "CONFIRMED".equals(s.getStatus()));
            if (hasConfirmed) {
                throw new RuntimeException("\u60a8\u5df2\u6709\u786e\u8ba4\u8bfe\u7a0b\uff0c\u65e0\u9700\u518d\u7533\u8bf7");
            }
            Course course = courseService.findById(courseId);
            if (!course.getEvent().getId().equals(closedEvent.getId())) {
                throw new RuntimeException("\u8be5\u8bfe\u7a0b\u4e0d\u5c5e\u4e8e\u5f53\u524d\u6d3b\u52a8");
            }
            messageService.sendCourseRequest(student, course, content);
            ra.addFlashAttribute("success", "\u7533\u8bf7\u5df2\u53d1\u9001\uff0c\u8bf7\u7b49\u5f85\u6559\u5e08\u5904\u7406");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
        return "redirect:/student/courses";
    }

    @PostMapping("/courses/{courseId}/request-ajax")
    @ResponseBody
    public ApiResponse<String> sendCourseRequestAjax(@PathVariable Long courseId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        try {
            Student student = currentUserService.getCurrentStudent();
            SelectionEvent closedEvent = findLatestClosedEvent(student);
            if (closedEvent == null) {
                return ApiResponse.error(400, "\u5f53\u524d\u6ca1\u6709\u53ef\u7533\u8bf7\u7684\u9009\u8bfe\u6d3b\u52a8");
            }
            String round3WindowError = validateRound3Window(closedEvent);
            if (round3WindowError != null) {
                return ApiResponse.error(400, round3WindowError);
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
            return ApiResponse.error(400, CourseSelectionPromptHelper.normalizeStudentPrompt(e.getMessage()));
        }
    }

    private Map<Long, Integer> buildConfirmedCountMap(List<Course> courses) {
        Map<Long, Integer> confirmedCountMap = new LinkedHashMap<>();
        for (Course course : courses) {
            confirmedCountMap.put(course.getId(), courseService.countConfirmedUniqueEnrollments(course));
        }
        return confirmedCountMap;
    }
}
