package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final CourseClassCapacityRepository capacityRepo;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    // ===== 批量初始化学生登录密码 =====

    @PostMapping("/init-passwords")
    public String initPasswords(RedirectAttributes ra) {
        try {
            School school = currentUserService.getCurrentSchool();
            int count = eventService.initStudentPasswords(school);
            ra.addFlashAttribute("success",
                    count > 0 ? "已为 " + count + " 名学生初始化密码（初始密码=学号）" : "所有学生密码均已设置，无需重新初始化");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "初始化失败：" + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    // ===== 活动列表 =====

    @GetMapping
    public String eventList(Model model) {
        School school = currentUserService.getCurrentSchool();
        model.addAttribute("events", eventService.findBySchool(school));
        return "admin/selection-events";
    }

    // ===== 活动保存 =====

    @PostMapping("/events/save")
    public String saveEvent(@RequestParam(required = false) Long id,
                            @RequestParam String name,
                            @RequestParam(required = false) String round1Start,
                            @RequestParam(required = false) String round1End,
                            @RequestParam(required = false) String round2Start,
                            @RequestParam(required = false) String round2End,
                            RedirectAttributes ra) {
        try {
            School school = currentUserService.getCurrentSchool();
            SelectionEvent event = (id != null) ? eventService.findById(id) : new SelectionEvent();
            event.setSchool(school);
            event.setName(name);
            if (round1Start != null && !round1Start.isBlank())
                event.setRound1Start(LocalDateTime.parse(round1Start));
            if (round1End != null && !round1End.isBlank())
                event.setRound1End(LocalDateTime.parse(round1End));
            if (round2Start != null && !round2Start.isBlank())
                event.setRound2Start(LocalDateTime.parse(round2Start));
            if (round2End != null && !round2End.isBlank())
                event.setRound2End(LocalDateTime.parse(round2End));
            eventService.save(event);
            ra.addFlashAttribute("success", "活动保存成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    // ===== 活动删除 =====

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

    // ===== 活动状态推进 =====

    @PostMapping("/events/{id}/start-round1")
    public String startRound1(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.startRound1(id);
            ra.addFlashAttribute("success", "第一轮选课已开启");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + id + "/detail";
    }

    @PostMapping("/events/{id}/process")
    public String processRound1(@PathVariable Long id, RedirectAttributes ra) {
        try {
            eventService.processRound1(id);
            ra.addFlashAttribute("success", "抽签已在后台启动，每门课程间隔1分钟，请稍后刷新查看进度");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + id + "/detail";
    }

    /** 前端轮询：获取抽签进度 */
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
        return "redirect:/admin/courses/" + id + "/detail";
    }

    // ===== 活动详情（课程管理 + 参与学生） =====

    @GetMapping("/{eventId}/detail")
    public String eventDetail(@PathVariable Long eventId, Model model) {
        School school = currentUserService.getCurrentSchool();
        SelectionEvent event = eventService.findById(eventId);
        model.addAttribute("event", event);
        model.addAttribute("courses", courseService.findByEvent(event));
        model.addAttribute("eventStudents", eventService.findEventStudents(event));
        model.addAttribute("allStudents", studentService.findBySchool(school));
        model.addAttribute("allClasses", classService.findAll(school));
        model.addAttribute("allGrades", gradeService.findAll(school));
        return "admin/course-event-detail";
    }

    // ===== 参与学生设置 =====

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
        return "redirect:/admin/courses/" + eventId + "/detail";
    }

    // ===== 课程保存 =====

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
            SelectionEvent event = eventService.findById(eventId);
            School school = currentUserService.getCurrentSchool();
            Course course = (courseId != null) ? courseService.findById(courseId) : new Course();
            course.setEvent(event);
            course.setSchool(school);
            course.setName(name);
            course.setDescription(description);
            course.setCapacityMode(capacityMode);
            if ("GLOBAL".equals(capacityMode)) {
                course.setTotalCapacity(totalCapacity);
            }
            if (teacherId != null) {
                Teacher t = new Teacher();
                t.setId(teacherId);
                course.setTeacher(t);
            } else {
                course.setTeacher(null);
            }
            if (course.getStatus() == null) course.setStatus("DRAFT");
            courseService.saveCourse(course, classIds, classCapacities);
            ra.addFlashAttribute("success", "课程保存成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail";
    }

    // ===== 课程状态（发布/下架） =====

    @PostMapping("/{eventId}/courses/{courseId}/activate")
    public String activateCourse(@PathVariable Long eventId, @PathVariable Long courseId,
                                 RedirectAttributes ra) {
        try {
            Course course = courseService.findById(courseId);
            course.setStatus("ACTIVE");
            courseService.saveCourse(course, null, null);
            ra.addFlashAttribute("success", "课程已发布");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail";
    }

    @PostMapping("/{eventId}/courses/{courseId}/close")
    public String closeCourse(@PathVariable Long eventId, @PathVariable Long courseId,
                              RedirectAttributes ra) {
        try {
            Course course = courseService.findById(courseId);
            course.setStatus("CLOSED");
            courseService.saveCourse(course, null, null);
            ra.addFlashAttribute("success", "课程已下架");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail";
    }

    @PostMapping("/{eventId}/courses/{courseId}/delete")
    public String deleteCourse(@PathVariable Long eventId, @PathVariable Long courseId,
                               RedirectAttributes ra) {
        try {
            courseService.deleteCourse(courseId);
            ra.addFlashAttribute("success", "课程已删除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/detail";
    }

    // ===== 报名名单 =====

    @GetMapping("/{eventId}/courses/{courseId}/enrollments")
    public String enrollments(@PathVariable Long eventId, @PathVariable Long courseId, Model model) {
        SelectionEvent event = eventService.findById(eventId);
        Course course = courseService.findById(courseId);
        model.addAttribute("event", event);
        model.addAttribute("course", course);
        model.addAttribute("enrollments", courseService.findEnrollments(course));
        model.addAttribute("capacities", courseService.findCapacities(course));
        model.addAttribute("allStudents", eventService.findEventStudents(event));
        return "admin/course-enrollments";
    }

    // ===== 手动加人 / 移除 =====

    @PostMapping("/{eventId}/courses/{courseId}/enrollments/add")
    public String adminEnroll(@PathVariable Long eventId, @PathVariable Long courseId,
                              @RequestParam Long studentId, RedirectAttributes ra) {
        try {
            courseService.adminEnroll(courseId, studentId, eventId);
            ra.addFlashAttribute("success", "已手动加入");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/courses/" + courseId + "/enrollments";
    }

    @PostMapping("/{eventId}/courses/{courseId}/enrollments/{selectionId}/remove")
    public String adminDrop(@PathVariable Long eventId, @PathVariable Long courseId,
                            @PathVariable Long selectionId, RedirectAttributes ra) {
        try {
            courseService.adminDrop(selectionId);
            ra.addFlashAttribute("success", "已移除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/courses/" + eventId + "/courses/" + courseId + "/enrollments";
    }
}
