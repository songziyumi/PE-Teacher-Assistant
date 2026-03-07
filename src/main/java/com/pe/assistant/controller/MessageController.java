package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final CurrentUserService currentUserService;
    private final TeacherRepository teacherRepository;

    // ===== 教师收件箱 =====

    @GetMapping("/teacher/messages")
    public String teacherInbox(Model model) {
        Teacher teacher = currentUserService.getCurrentTeacher();
        model.addAttribute("teacher", teacher);
        model.addAttribute("messages", messageService.getTeacherInbox(teacher));
        model.addAttribute("unreadCount", messageService.getUnreadCount("TEACHER", teacher.getId()));
        return "teacher/messages";
    }

    @PostMapping("/teacher/messages/{id}/read")
    public String teacherMarkRead(@PathVariable Long id, RedirectAttributes ra) {
        messageService.markRead(id);
        return "redirect:/teacher/messages";
    }

    @PostMapping("/teacher/messages/{id}/approve")
    public String approveRequest(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            messageService.approveRequest(id, teacher);
            ra.addFlashAttribute("success", "已同意申请，学生已加入课程");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/messages";
    }

    @PostMapping("/teacher/messages/{id}/reject")
    public String rejectRequest(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            messageService.rejectRequest(id, teacher);
            ra.addFlashAttribute("success", "已拒绝申请");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/messages";
    }

    // ===== 学生收件箱 =====

    @GetMapping("/student/messages")
    public String studentInbox(Model model) {
        Student student = currentUserService.getCurrentStudent();
        model.addAttribute("student", student);
        model.addAttribute("messages", messageService.getStudentInbox(student));
        model.addAttribute("unreadCount", messageService.getUnreadCount("STUDENT", student.getId()));
        // 教师列表供发消息使用
        model.addAttribute("teachers", messageService.findTeachersBySchool(student.getSchool()));
        return "student/messages";
    }

    @PostMapping("/student/messages/send")
    public String studentSend(@RequestParam Long teacherId,
                              @RequestParam String subject,
                              @RequestParam String content,
                              RedirectAttributes ra) {
        try {
            Student student = currentUserService.getCurrentStudent();
            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("教师不存在"));
            messageService.sendMessage(
                    "STUDENT", student.getId(), student.getName(),
                    "TEACHER", teacher.getId(), teacher.getName(),
                    subject, content, student.getSchool());
            ra.addFlashAttribute("success", "消息已发送");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/messages";
    }

    @PostMapping("/student/messages/{id}/read")
    public String studentMarkRead(@PathVariable Long id, RedirectAttributes ra) {
        messageService.markRead(id);
        return "redirect:/student/messages";
    }
}
