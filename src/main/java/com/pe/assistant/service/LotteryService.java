package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.SelectionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 异步抽签执行器。
 * 独立 Bean 是为了避免 @Async 自调用导致代理失效（Spring AOP 限制）。
 *
 * 第一轮抽签按两个阶段结算：
 * 1. 先统一结算全部第一志愿
 * 2. 再用剩余名额结算第一志愿未录取学生的第二志愿
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryService {

    private final SelectionEventRepository eventRepo;
    private final CourseRepository courseRepo;
    private final CourseClassCapacityRepository capacityRepo;
    private final CourseSelectionRepository selectionRepo;
    private final StudentNotificationService studentNotificationService;
    private final StudentService studentService;

    @Async
    public void runLottery(Long eventId) {
        long startedAtNanos = System.nanoTime();
        log.info("courseSelection.round1.lottery.started eventId={}", eventId);
        try {
            doRunLottery(eventId);
            log.info("courseSelection.round1.lottery.completed eventId={} latencyMs={}",
                    eventId,
                    Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L));
        } catch (Exception e) {
            log.error("courseSelection.round1.lottery.failed eventId={} latencyMs={}",
                    eventId,
                    Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L),
                    e);
            try {
                SelectionEvent event = eventRepo.findById(eventId).orElse(null);
                if (event != null && "PROCESSING".equals(event.getStatus())) {
                    event.setLotteryNote("抽签异常，请检查日志");
                    eventRepo.save(event);
                }
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    @Transactional
    protected void doRunLottery(Long eventId) throws InterruptedException {
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        List<Course> courses = courseRepo.findByEventOrderByNameAsc(event);
        Set<Long> confirmedStudentIds = selectionRepo.findByEventAndStatus(event, "CONFIRMED")
                .stream()
                .map(cs -> cs.getStudent().getId())
                .collect(Collectors.toCollection(HashSet::new));

        settlePreferencesByPhase(event, courses, 1, confirmedStudentIds);
        settlePreferencesByPhase(event, courses, 2, confirmedStudentIds);

        event.setStatus("ROUND2");
        event.setLotteryNote("抽签完成，已进入第二轮");
        eventRepo.save(event);
        notifyRound1Results(event);
    }

    private void settlePreferencesByPhase(SelectionEvent event,
                                          List<Course> courses,
                                          int preference,
                                          Set<Long> confirmedStudentIds) {
        int total = courses.size();
        String phaseLabel = preference == 1 ? "第一志愿" : "第二志愿";
        int confirmedBeforePhase = confirmedStudentIds.size();

        for (int i = 0; i < total; i++) {
            Course course = courses.get(i);
            event.setLotteryNote("正在结算" + phaseLabel + "：" + course.getName() + " (" + (i + 1) + "/" + total + ")");
            eventRepo.save(event);

            Set<Long> newlyConfirmedStudentIds = "GLOBAL".equals(course.getCapacityMode())
                    ? settleGlobalPreference(course, preference, confirmedStudentIds)
                    : settlePerClassPreference(course, preference, confirmedStudentIds);
            confirmedStudentIds.addAll(newlyConfirmedStudentIds);
            log.info("courseSelection.round1.phase.course eventId={} preference={} courseId={} courseName={} capacityMode={} confirmedAdded={} confirmedTotal={}",
                    event.getId(),
                    preference,
                    course.getId(),
                    course.getName(),
                    course.getCapacityMode(),
                    newlyConfirmedStudentIds.size(),
                    confirmedStudentIds.size());
        }
        log.info("courseSelection.round1.phase.completed eventId={} preference={} phaseLabel={} courses={} confirmedAdded={} confirmedTotal={}",
                event.getId(),
                preference,
                phaseLabel,
                total,
                Math.max(0, confirmedStudentIds.size() - confirmedBeforePhase),
                confirmedStudentIds.size());
    }

    private Set<Long> settleGlobalPreference(Course course,
                                             int preference,
                                             Set<Long> excludedStudentIds) {
        List<CourseSelection> allPending = selectionRepo.findByCourseAndStatusOrderBySelectedAtAsc(course, "PENDING");
        if (allPending.isEmpty()) {
            return Set.of();
        }

        List<CourseSelection> targetSelections = allPending.stream()
                .filter(cs -> cs.getRound() == 1)
                .filter(cs -> cs.getPreference() == preference)
                .collect(Collectors.toCollection(ArrayList::new));
        if (targetSelections.isEmpty()) {
            return Set.of();
        }

        cancelExcludedSelections(targetSelections, excludedStudentIds);

        List<CourseSelection> eligibleSelections = targetSelections.stream()
                .filter(cs -> !excludedStudentIds.contains(cs.getStudent().getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        int confirmed = course.getCurrentCount();
        Set<Long> newlyConfirmedStudentIds = confirmSelections(eligibleSelections, confirmed, course.getTotalCapacity());
        course.setCurrentCount(confirmed + newlyConfirmedStudentIds.size());
        courseRepo.save(course);
        return newlyConfirmedStudentIds;
    }

    private Set<Long> settlePerClassPreference(Course course,
                                               int preference,
                                               Set<Long> excludedStudentIds) {
        List<CourseClassCapacity> capacities = capacityRepo.findByCourse(course);
        int totalConfirmed = 0;
        Set<Long> newlyConfirmedStudentIds = new HashSet<>();

        for (CourseClassCapacity cap : capacities) {
            Long classId = cap.getSchoolClass().getId();
            List<CourseSelection> allPending = selectionRepo.findPendingByClassId(course, classId);
            if (allPending.isEmpty()) {
                totalConfirmed += cap.getCurrentCount();
                continue;
            }

            List<CourseSelection> targetSelections = allPending.stream()
                    .filter(cs -> cs.getPreference() == preference)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (targetSelections.isEmpty()) {
                totalConfirmed += cap.getCurrentCount();
                continue;
            }

            cancelExcludedSelections(targetSelections, excludedStudentIds);

            List<CourseSelection> eligibleSelections = targetSelections.stream()
                    .filter(cs -> !excludedStudentIds.contains(cs.getStudent().getId()))
                    .collect(Collectors.toCollection(ArrayList::new));

            int beforeConfirmed = cap.getCurrentCount();
            Set<Long> classConfirmedStudentIds = confirmSelections(eligibleSelections, beforeConfirmed, cap.getMaxCapacity());
            cap.setCurrentCount(beforeConfirmed + classConfirmedStudentIds.size());
            capacityRepo.save(cap);

            totalConfirmed += cap.getCurrentCount();
            newlyConfirmedStudentIds.addAll(classConfirmedStudentIds);
        }

        course.setCurrentCount(totalConfirmed);
        courseRepo.save(course);
        return newlyConfirmedStudentIds;
    }

    private void cancelExcludedSelections(List<CourseSelection> selections, Set<Long> excludedStudentIds) {
        selections.stream()
                .filter(cs -> excludedStudentIds.contains(cs.getStudent().getId()))
                .forEach(cs -> {
                    cs.setStatus("CANCELLED");
                    selectionRepo.save(cs);
                });
    }

    private Set<Long> confirmSelections(List<CourseSelection> selections, int confirmedBefore, int maxCapacity) {
        Collections.shuffle(selections);

        int confirmed = confirmedBefore;
        Set<Long> newlyConfirmedStudentIds = new HashSet<>();
        for (CourseSelection cs : selections) {
            if (confirmed < maxCapacity) {
                cs.setStatus("CONFIRMED");
                cs.setConfirmedAt(LocalDateTime.now());
                confirmed++;
                newlyConfirmedStudentIds.add(cs.getStudent().getId());
            } else {
                cs.setStatus("LOTTERY_FAIL");
            }
            selectionRepo.save(cs);
            if ("CONFIRMED".equals(cs.getStatus())) {
                studentService.assignElectiveClassFromCourse(cs.getStudent(), cs.getCourse());
            }
        }
        return newlyConfirmedStudentIds;
    }

    private void notifyRound1Results(SelectionEvent event) {
        List<CourseSelection> round1Selections = selectionRepo.findByEvent(event).stream()
                .filter(selection -> selection.getRound() == 1)
                .filter(selection -> !"DRAFT".equals(selection.getStatus()))
                .toList();

        Map<Long, List<CourseSelection>> selectionsByStudent = new LinkedHashMap<>();
        for (CourseSelection selection : round1Selections) {
            if (selection.getStudent() == null || selection.getStudent().getId() == null) {
                continue;
            }
            selectionsByStudent
                    .computeIfAbsent(selection.getStudent().getId(), key -> new ArrayList<>())
                    .add(selection);
        }

        for (List<CourseSelection> studentSelections : selectionsByStudent.values()) {
            CourseSelection sample = studentSelections.get(0);
            Course confirmedCourse = studentSelections.stream()
                    .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                    .map(CourseSelection::getCourse)
                    .findFirst()
                    .orElse(null);
            studentNotificationService.notifyRound1Result(event, sample.getStudent(), confirmedCourse);
        }
    }
}
