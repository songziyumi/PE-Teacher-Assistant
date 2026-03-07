package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SelectionEventService {

    private final SelectionEventRepository eventRepo;
    private final CourseRepository courseRepo;
    private final CourseClassCapacityRepository capacityRepo;
    private final CourseSelectionRepository selectionRepo;
    private final EventStudentRepository eventStudentRepo;
    private final StudentRepository studentRepo;
    private final PasswordEncoder passwordEncoder;
    private final LotteryService lotteryService;

    // ===== 活动管理 =====

    public List<SelectionEvent> findBySchool(School school) {
        return eventRepo.findBySchoolOrderByCreatedAtDesc(school);
    }

    public SelectionEvent findById(Long id) {
        return eventRepo.findById(id).orElseThrow(() -> new RuntimeException("活动不存在"));
    }

    @Transactional
    public SelectionEvent save(SelectionEvent event) {
        return eventRepo.save(event);
    }

    @Transactional
    public void delete(Long id) {
        SelectionEvent event = findById(id);
        if (!"DRAFT".equals(event.getStatus())) {
            throw new RuntimeException("只能删除草稿状态的活动");
        }
        selectionRepo.findByEventAndStatus(event, "PENDING").forEach(s -> selectionRepo.delete(s));
        eventStudentRepo.deleteByEvent(event);
        courseRepo.findByEventOrderByNameAsc(event).forEach(c -> {
            capacityRepo.deleteByCourse(c);
            courseRepo.delete(c);
        });
        eventRepo.delete(event);
    }

    // ===== 参与学生管理 =====

    public List<Student> findEventStudents(SelectionEvent event) {
        return eventStudentRepo.findStudentsByEvent(event);
    }

    @Transactional
    public void setEventStudents(SelectionEvent event, List<Long> studentIds) {
        eventStudentRepo.deleteByEvent(event);
        for (Long sid : studentIds) {
            Student s = studentRepo.findById(sid).orElse(null);
            if (s == null) continue;
            // 初始化学生密码（若未设置，默认密码=学号）
            if (s.getPassword() == null) {
                s.setPassword(passwordEncoder.encode(s.getStudentNo()));
                studentRepo.save(s);
            }
            EventStudent es = new EventStudent();
            es.setEvent(event);
            es.setStudent(s);
            eventStudentRepo.save(es);
        }
    }

    // ===== 第一轮结算（抽签）=====

    /**
     * 管理员手动触发：将活动置为 PROCESSING 状态，然后异步启动抽签。
     * 方法立即返回，抽签在后台逐课程执行（每门课间隔60秒）。
     */
    @Transactional
    public void processRound1(Long eventId) {
        SelectionEvent event = findById(eventId);
        if (!"ROUND1".equals(event.getStatus())) {
            throw new RuntimeException("活动当前状态不允许执行抽签（需为 ROUND1）");
        }
        event.setStatus("PROCESSING");
        event.setLotteryNote("抽签即将开始…");
        eventRepo.save(event);
        // 异步执行，立即返回
        lotteryService.runLottery(eventId);
    }

    /** 获取抽签进度说明（供前端轮询） */
    public Map<String, String> getLotteryProgress(Long eventId) {
        SelectionEvent event = findById(eventId);
        Map<String, String> result = new java.util.HashMap<>();
        result.put("status", event.getStatus());
        result.put("note", event.getLotteryNote() != null ? event.getLotteryNote() : "");
        return result;
    }

    // ===== 活动状态推进 =====

    @Transactional
    public void startRound1(Long eventId) {
        SelectionEvent event = findById(eventId);
        if (!"DRAFT".equals(event.getStatus())) throw new RuntimeException("活动不在草稿状态");
        event.setStatus("ROUND1");
        eventRepo.save(event);
    }

    @Transactional
    public void closeEvent(Long eventId) {
        SelectionEvent event = findById(eventId);
        event.setStatus("CLOSED");
        eventRepo.save(event);
    }

    // ===== 查询辅助 =====

    /**
     * 批量初始化本校所有学生的密码（初始密码=学号），仅对 password=null 的学生生效
     */
    @Transactional
    public int initStudentPasswords(School school) {
        List<Student> students = studentRepo.findBySchoolOrderByStudentNo(school);
        int count = 0;
        for (Student s : students) {
            if (s.getPassword() == null) {
                s.setPassword(passwordEncoder.encode(s.getStudentNo()));
                s.setEnabled(true);
                studentRepo.save(s);
                count++;
            }
        }
        return count;
    }

    /** 判断当前时间是否在第一轮窗口内 */
    public boolean isInRound1(SelectionEvent event) {
        LocalDateTime now = LocalDateTime.now();
        return "ROUND1".equals(event.getStatus())
                && event.getRound1Start() != null && event.getRound1End() != null
                && now.isAfter(event.getRound1Start()) && now.isBefore(event.getRound1End());
    }

    /** 判断当前时间是否在第二轮窗口内 */
    public boolean isInRound2(SelectionEvent event) {
        LocalDateTime now = LocalDateTime.now();
        return "ROUND2".equals(event.getStatus())
                && event.getRound2Start() != null && event.getRound2End() != null
                && now.isAfter(event.getRound2Start()) && now.isBefore(event.getRound2End());
    }
}
