package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.InternalMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class StudentNotificationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SYSTEM_SENDER_NAME = "系统通知";

    private final InternalMessageRepository messageRepo;

    @Transactional
    public InternalMessage notifyRound1Result(SelectionEvent event, Student student, Course confirmedCourse) {
        String subject;
        String content;
        if (confirmedCourse != null) {
            subject = "第一轮选课结果：已录取《" + confirmedCourse.getName() + "》";
            content = "第一轮选课结果已公布，您已被《" + confirmedCourse.getName() + "》录取。"
                    + buildRound2ScheduleText(event)
                    + "如需调整课程，请在第二轮期间先退掉当前课程，再按时参加第二轮抢课。";
        } else {
            subject = "第一轮选课结果：未中签";
            content = "第一轮选课结果已公布，您在第一轮暂未中签。"
                    + buildRound2ScheduleText(event)
                    + "请按时参加第二轮抢课；若到第二轮结束时您仍未确认课程，系统会从仍有空余名额的课程中随机为您分配。";
        }
        return sendSystemMessage(resolveSchool(student, event), student, subject, content);
    }

    @Transactional
    public InternalMessage notifyRound2AutoAssignment(SelectionEvent event, Student student, Course course) {
        String subject = "第二轮结束提醒：已自动分配课程";
        String content = "第二轮已结束，系统已为您随机分配到《"
                + (course != null ? course.getName() : "课程")
                + "》。请及时查看选课结果。";
        return sendSystemMessage(resolveSchool(student, event), student, subject, content);
    }

    @Transactional
    public InternalMessage notifyRound2ClosedWithoutCourse(SelectionEvent event, Student student) {
        String subject = "第二轮结束提醒：暂未分配课程";
        String content = "第二轮已结束，但当前没有可分配的剩余课程名额，您暂未被分配到课程。"
                + "如需处理，请联系老师。";
        return sendSystemMessage(resolveSchool(student, event), student, subject, content);
    }

    @Transactional
    public InternalMessage notifyDropSuccess(Student student, Course course, SelectionEvent event) {
        String courseName = course != null ? course.getName() : "课程";
        String subject = "退课成功提醒：《" + courseName + "》";
        String content = "您已成功退掉《" + courseName + "》。"
                + buildRound2ScheduleText(event)
                + "如需重新选课，请按时参加第二轮抢课。";
        return sendSystemMessage(resolveSchool(student, event), student, subject, content);
    }

    @Transactional
    public InternalMessage notifyAdminEnrollSuccess(Student student, Course course, SelectionEvent event, boolean forceOverflow) {
        String courseName = course != null ? course.getName() : "课程";
        String subject = forceOverflow
                ? "选课成功提醒：已强制超编加入《" + courseName + "》"
                : "选课成功提醒：已加入《" + courseName + "》";
        String content = forceOverflow
                ? "管理员已为您强制超编加入《" + courseName + "》，请及时查看我的选课。"
                : "管理员已为您加入《" + courseName + "》，请及时查看我的选课。";
        return sendSystemMessage(resolveSchool(student, event), student, subject, content);
    }

    @Transactional
    public InternalMessage sendSystemMessage(School school, Student student, String subject, String content) {
        InternalMessage msg = new InternalMessage();
        msg.setSchool(school);
        msg.setSenderType("SYSTEM");
        msg.setSenderName(SYSTEM_SENDER_NAME);
        msg.setRecipientType("STUDENT");
        msg.setRecipientId(student.getId());
        msg.setRecipientName(student.getName());
        msg.setSubject(subject);
        msg.setContent(content);
        msg.setType("GENERAL");
        msg.setSentAt(LocalDateTime.now());
        return messageRepo.save(msg);
    }

    private School resolveSchool(Student student, SelectionEvent event) {
        if (student != null && student.getSchool() != null) {
            return student.getSchool();
        }
        return event != null ? event.getSchool() : null;
    }

    private String buildRound2ScheduleText(SelectionEvent event) {
        if (event == null || (event.getRound2Start() == null && event.getRound2End() == null)) {
            return "";
        }
        StringBuilder builder = new StringBuilder("第二轮抢课时间：");
        builder.append(formatTime(event.getRound2Start()));
        if (event.getRound2End() != null) {
            builder.append(" 至 ").append(formatTime(event.getRound2End()));
        }
        builder.append("。");
        return builder.toString();
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "待定" : TIME_FORMATTER.format(time);
    }
}
