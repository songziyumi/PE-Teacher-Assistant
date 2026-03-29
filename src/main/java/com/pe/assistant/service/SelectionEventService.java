package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.EventStudent;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.EventStudentRepository;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SelectionEventService {

    private final SelectionEventRepository eventRepo;
    private final CourseRepository courseRepo;
    private final CourseClassCapacityRepository capacityRepo;
    private final CourseSelectionRepository selectionRepo;
    private final EventStudentRepository eventStudentRepo;
    private final StudentRepository studentRepo;
    private final StudentAccountService studentAccountService;
    private final LotteryService lotteryService;

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
        selectionRepo.findByEventAndStatus(event, "PENDING").forEach(selectionRepo::delete);
        eventStudentRepo.deleteByEvent(event);
        courseRepo.findByEventOrderByNameAsc(event).forEach(course -> {
            capacityRepo.deleteByCourse(course);
            courseRepo.delete(course);
        });
        eventRepo.delete(event);
    }

    public List<Student> findEventStudents(SelectionEvent event) {
        return eventStudentRepo.findStudentsByEvent(event);
    }

    @Transactional
    public void setEventStudents(SelectionEvent event, List<Long> studentIds) {
        eventStudentRepo.deleteByEvent(event);
        eventStudentRepo.flush();

        Set<Long> uniqueStudentIds = new LinkedHashSet<>();
        if (studentIds != null) {
            for (Long studentId : studentIds) {
                if (studentId != null) {
                    uniqueStudentIds.add(studentId);
                }
            }
        }

        for (Long studentId : uniqueStudentIds) {
            Student student = studentRepo.findById(studentId).orElse(null);
            if (student == null) {
                continue;
            }
            studentAccountService.initializeAccount(student);
            EventStudent relation = new EventStudent();
            relation.setEvent(event);
            relation.setStudent(student);
            eventStudentRepo.save(relation);
        }
    }

    @Transactional
    public void processRound1(Long eventId) {
        SelectionEvent event = findById(eventId);
        if (!"ROUND1".equals(event.getStatus())) {
            throw new RuntimeException("活动当前状态不允许执行抽签");
        }
        event.setStatus("PROCESSING");
        event.setLotteryNote("抽签即将开始");
        eventRepo.save(event);
        lotteryService.runLottery(eventId);
    }

    public Map<String, String> getLotteryProgress(Long eventId) {
        SelectionEvent event = findById(eventId);
        Map<String, String> result = new HashMap<>();
        result.put("status", event.getStatus());
        result.put("note", event.getLotteryNote() != null ? event.getLotteryNote() : "");
        return result;
    }

    @Transactional
    public void startRound1(Long eventId) {
        SelectionEvent event = findById(eventId);
        if (!"DRAFT".equals(event.getStatus())) {
            throw new RuntimeException("活动不在草稿状态");
        }
        event.setStatus("ROUND1");
        eventRepo.save(event);
    }

    @Transactional
    public void closeEvent(Long eventId) {
        SelectionEvent event = findById(eventId);
        event.setStatus("CLOSED");
        eventRepo.save(event);
    }

    @Transactional
    public int initStudentPasswords(School school) {
        int count = 0;
        for (Student student : studentRepo.findBySchoolOrderByStudentNo(school)) {
            if (studentAccountService.initializeAccount(student).isPresent()) {
                count++;
            }
        }
        return count;
    }

    public boolean isInRound1(SelectionEvent event) {
        LocalDateTime now = LocalDateTime.now();
        return "ROUND1".equals(event.getStatus())
                && event.getRound1Start() != null
                && event.getRound1End() != null
                && now.isAfter(event.getRound1Start())
                && now.isBefore(event.getRound1End());
    }

    public boolean isInRound2(SelectionEvent event) {
        LocalDateTime now = LocalDateTime.now();
        return "ROUND2".equals(event.getStatus())
                && event.getRound2Start() != null
                && event.getRound2End() != null
                && now.isAfter(event.getRound2Start())
                && now.isBefore(event.getRound2End());
    }
}
