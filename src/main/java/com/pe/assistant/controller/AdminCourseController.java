package com.pe.assistant.controller;

import com.pe.assistant.controller.support.CourseSelectionPromptHelper;
import com.pe.assistant.dto.CourseEventReviewStats;
import com.pe.assistant.dto.Round1LotterySummary;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CourseOverflowAuditService;
import com.pe.assistant.service.CourseEventReviewService;
import com.pe.assistant.service.CourseService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.GradeService;
import com.pe.assistant.service.SelectionEventService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final SelectionEventService eventService;
    private final CourseService courseService;
    private final CurrentUserService currentUserService;
    private final ClassService classService;
    private final GradeService gradeService;
    private final StudentService studentService;
    private final TeacherService teacherService;
    private final CourseOverflowAuditService courseOverflowAuditService;
    private final CourseEventReviewService courseEventReviewService;
    private final CourseClassCapacityRepository capacityRepo;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    @GetMapping
    public String eventList(Model model) {
        School school = currentUserService.getCurrentSchool();
        model.addAttribute("events", eventService.findBySchool(school));
        return "admin/selection-events";
    }

    @PostMapping("/events/save")
    public String saveEvent(@RequestParam(required = false) Long id,
                            @RequestParam String name,
                            @RequestParam(required = false) String round1Start,
                            @RequestParam(required = false) String round1End,
                            @RequestParam(required = false) String round2Start,
                            @RequestParam(required = false) String round2End,
                            @RequestParam(required = false) String round3Start,
                            @RequestParam(required = false) String round3End,
                            RedirectAttributes ra) {
        try {
            boolean created = (id == null);
            School school = currentUserService.getCurrentSchool();
            SelectionEvent event = (id != null) ? eventService.findById(id) : new SelectionEvent();
            event.setSchool(school);
            event.setName(name);
            if (round1Start != null && !round1Start.isBlank()) {
                event.setRound1Start(LocalDateTime.parse(round1Start));
            }
            if (round1End != null && !round1End.isBlank()) {
                event.setRound1End(LocalDateTime.parse(round1End));
            }
            if (round2Start != null && !round2Start.isBlank()) {
                event.setRound2Start(LocalDateTime.parse(round2Start));
            }
            if (round2End != null && !round2End.isBlank()) {
                event.setRound2End(LocalDateTime.parse(round2End));
            }
            if (round3Start != null && !round3Start.isBlank()) {
                event.setRound3Start(LocalDateTime.parse(round3Start));
            }
            if (round3End != null && !round3End.isBlank()) {
                event.setRound3End(LocalDateTime.parse(round3End));
            }
            event = eventService.save(event);
            ra.addFlashAttribute("success", "活动保存成功");
            if (created) {
                return "redirect:/admin/courses/" + event.getId() + "/detail?tab=students";
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.delete(id);
            ra.addFlashAttribute("success", "活动已删除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    @PostMapping("/events/{id}/start-round1")
    public String startRound1(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.startRound1(id);
            ra.addFlashAttribute("success", "第一轮选课已开启");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + id + "/detail?tab=courses";
    }

    @PostMapping("/events/{id}/process")
    public String processRound1(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.processRound1(id);
            ra.addFlashAttribute("success", "第一轮结算已在后台启动，系统将先结算第一志愿，再结算第二志愿，请稍后刷新查看进度");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + id + "/detail?tab=courses";
    }

    @GetMapping("/events/{id}/lottery-status")
    @ResponseBody
    public ResponseEntity<Map<String, String>> lotteryStatus(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getLotteryProgress(id));
    }

    @PostMapping("/events/{id}/close")
    public String closeEvent(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.closeEvent(id);
            ra.addFlashAttribute("success", "活动已关闭");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + id + "/detail?tab=courses";
    }

    @GetMapping("/{eventId}/detail")
    public String eventDetail(@PathVariable Long eventId,
                              @RequestParam(defaultValue = "students") String tab,
                              Model model) {
        School school = currentUserService.getCurrentSchool();
        SelectionEvent event = eventService.findById(eventId);
        Round1LotterySummary round1Summary = eventService.getRound1LotterySummary(event);
        boolean round1ResultAvailable = "ROUND2".equals(event.getStatus()) || "CLOSED".equals(event.getStatus());
        List<Student> participatingStudents = eventService.findParticipatingStudents(event);
        Set<Long> participatingClassIds = eventService.findParticipatingClassIds(event);
        List<Course> courses = courseService.findByEvent(event);
        CourseEventReviewStats reviewStats = courseEventReviewService.buildReviewStats(event, courses);
        Map<Long, Integer> confirmedCountByCourse = new LinkedHashMap<>();
        for (Course course : courses) {
            confirmedCountByCourse.put(course.getId(), courseService.countConfirmedUniqueEnrollments(course));
        }
        model.addAttribute("event", event);
        model.addAttribute("courses", courses);
        model.addAttribute("reviewStats", reviewStats);
        model.addAttribute("confirmedCountByCourse", confirmedCountByCourse);
        model.addAttribute("round1Summary", round1Summary);
        model.addAttribute("round1ResultAvailable", round1ResultAvailable);
        model.addAttribute("eventStudents", eventService.findEventStudents(event));
        model.addAttribute("participatingStudents", participatingStudents);
        model.addAttribute("participatingClasses", classService.findAll(school).stream()
                .filter(c -> participatingClassIds.contains(c.getId()))
                .toList());
        model.addAttribute("participatingClassIds", participatingClassIds);
        model.addAttribute("activeTab", "courses".equals(tab) ? "courses" : "students");
        model.addAttribute("allStudents", studentService.findBySchool(school));
        model.addAttribute("allClasses", classService.findAll(school));
        model.addAttribute("allGrades", gradeService.findAll(school));
        model.addAttribute("availableTeachers", teacherService.findCourseAssignableTeachers(school));
        return "admin/course-event-detail";
    }

    @PostMapping("/{eventId}/students/save")
    public String saveEventStudents(@PathVariable Long eventId,
                                    @RequestParam(value = "studentIds", required = false) List<Long> studentIds,
                                    RedirectAttributes ra) {
        try {
            SelectionEvent event = eventService.findById(eventId);
            eventService.setEventStudents(event, studentIds == null ? List.of() : studentIds);
            ra.addFlashAttribute("success", "参与学生已更新");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail?tab=students";
    }

    @PostMapping("/{eventId}/courses/save")
    public String saveCourse(@PathVariable Long eventId,
                             @RequestParam(required = false) Long courseId,
                             @RequestParam String name,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) Long teacherId,
                             @RequestParam String capacityMode,
                             @RequestParam(defaultValue = "0") int totalCapacity,
                             @RequestParam(value = "classIds", required = false) List<Long> classIds,
                             @RequestParam(value = "classCapacities", required = false) List<Integer> classCapacities,
                             RedirectAttributes ra) {
        try {
            if (description != null && description.length() > 500) {
                ra.addFlashAttribute("error", "课程简介不能超过500字");
                return "redirect:/admin/courses/" + eventId + "/detail?tab=courses";
            }
            SelectionEvent event = eventService.findById(eventId);
            School school = currentUserService.getCurrentSchool();
            Set<Long> participatingClassIds = eventService.findParticipatingClassIds(event);
            Course course = (courseId != null) ? courseService.findById(courseId) : new Course();
            course.setEvent(event);
            course.setSchool(school);
            course.setName(name);
            course.setDescription(description);
            course.setCapacityMode(capacityMode);
            if (teacherId != null) {
                Teacher teacher = new Teacher();
                teacher.setId(teacherId);
                course.setTeacher(teacher);
            } else {
                course.setTeacher(null);
            }
            if (course.getStatus() == null) {
                course.setStatus("DRAFT");
            }
            if ("GLOBAL".equals(capacityMode)) {
                course.setTotalCapacity(totalCapacity);
                courseService.saveCourse(course, null, null);
            } else {
                courseService.savePerClassCourse(course, classIds, classCapacities, participatingClassIds);
            }
            ra.addFlashAttribute("success", "课程保存成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail?tab=courses";
    }

    @PostMapping("/{eventId}/courses/{courseId}/activate")
    public String activateCourse(@PathVariable Long eventId, @PathVariable Long courseId, RedirectAttributes ra) {
        try {
            Course course = courseService.findById(courseId);
            course.setStatus("ACTIVE");
            courseService.saveCourse(course, null, null);
            ra.addFlashAttribute("success", "课程已发布");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail?tab=courses";
    }

    @PostMapping("/{eventId}/courses/{courseId}/close")
    public String closeCourse(@PathVariable Long eventId, @PathVariable Long courseId, RedirectAttributes ra) {
        try {
            Course course = courseService.findById(courseId);
            course.setStatus("CLOSED");
            courseService.saveCourse(course, null, null);
            ra.addFlashAttribute("success", "课程已下架");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail?tab=courses";
    }

    @PostMapping("/{eventId}/courses/{courseId}/delete")
    public String deleteCourse(@PathVariable Long eventId, @PathVariable Long courseId, RedirectAttributes ra) {
        try {
            courseService.deleteCourse(courseId);
            ra.addFlashAttribute("success", "课程已删除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail?tab=courses";
    }

    @GetMapping("/{eventId}/courses/{courseId}/enrollments")
    public String enrollments(@PathVariable Long eventId, @PathVariable Long courseId, Model model) {
        SelectionEvent event = eventService.findById(eventId);
        Course course = courseService.findById(courseId);
        int confirmedEnrollmentCount = courseService.countConfirmedUniqueEnrollments(course);
        model.addAttribute("event", event);
        model.addAttribute("course", course);
        model.addAttribute("enrollments", courseService.findEnrollments(course));
        model.addAttribute("confirmedEnrollmentCount", confirmedEnrollmentCount);
        model.addAttribute("remainingEnrollmentCapacity", Math.max(0, course.getTotalCapacity() - confirmedEnrollmentCount));
        model.addAttribute("overflowEnrollmentCount", Math.max(0, confirmedEnrollmentCount - course.getTotalCapacity()));
        model.addAttribute("capacities", courseService.findCapacities(course));
        model.addAttribute("allStudents", eventService.findParticipatingStudents(event));
        return "admin/course-enrollments";
    }

    @PostMapping("/{eventId}/courses/{courseId}/enrollments/add")
    public String adminEnroll(@PathVariable Long eventId,
                              @PathVariable Long courseId,
                              @RequestParam Long studentId,
                              @RequestParam(defaultValue = "false") boolean forceOverflow,
                              @RequestParam(defaultValue = "") String forceReason,
                              RedirectAttributes ra) {
        try {
            if (forceOverflow && (forceReason == null || forceReason.isBlank())) {
                throw new RuntimeException("强制超编时必须填写原因");
            }
            courseService.adminEnroll(courseId, studentId, eventId, forceOverflow);
            if (forceOverflow) {
                School school = currentUserService.getCurrentSchool();
                Teacher operator = currentUserService.getCurrentTeacher();
                Course course = courseService.findById(courseId);
                Student student = studentService.findById(studentId);
                courseOverflowAuditService.recordForcedOverflow(school, course, student, operator, forceReason.trim());
            }
            ra.addFlashAttribute("success", forceOverflow ? "已强制超编加入" : "已手动加入");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeAdminPrompt(e.getMessage()));
        }
        return "redirect:/admin/courses/" + eventId + "/courses/" + courseId + "/enrollments";
    }

    @PostMapping("/{eventId}/courses/{courseId}/enrollments/{selectionId}/remove")
    public String adminDrop(@PathVariable Long eventId,
                            @PathVariable Long courseId,
                            @PathVariable Long selectionId,
                            RedirectAttributes ra) {
        try {
            courseService.adminDrop(selectionId);
            ra.addFlashAttribute("success", "已移除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", CourseSelectionPromptHelper.normalizeAdminPrompt(e.getMessage()));
        }
        return "redirect:/admin/courses/" + eventId + "/courses/" + courseId + "/enrollments";
    }
}
