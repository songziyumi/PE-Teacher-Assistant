package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 异步抽签执行器。
 * 独立 Bean 是为了避免 @Async 自调用导致代理失效（Spring AOP 限制）。
 * 逻辑：按课程顺序逐项抽签，每项间隔60秒；
 *       处理每门课的 2志愿前，先排除已在本次活动中中签的学生。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryService {

    private final SelectionEventRepository eventRepo;
    private final CourseRepository courseRepo;
    private final CourseClassCapacityRepository capacityRepo;
    private final CourseSelectionRepository selectionRepo;

    @Async
    public void runLottery(Long eventId) {
        try {
            doRunLottery(eventId);
        } catch (Exception e) {
            log.error("抽签过程异常，eventId={}", eventId, e);
            // 即使出错也尝试将状态推进，防止卡在 PROCESSING
            try {
                SelectionEvent event = eventRepo.findById(eventId).orElse(null);
                if (event != null && "PROCESSING".equals(event.getStatus())) {
                    event.setLotteryNote("抽签异常，请检查日志");
                    eventRepo.save(event);
                }
            } catch (Exception ex) { /* ignore */ }
        }
    }

    @Transactional
    protected void doRunLottery(Long eventId) throws InterruptedException {
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        List<Course> courses = courseRepo.findByEventOrderByNameAsc(event);
        int total = courses.size();

        for (int i = 0; i < total; i++) {
            Course course = courses.get(i);

            // 更新进度
            event.setLotteryNote("正在处理：" + course.getName() + " (" + (i + 1) + "/" + total + ")");
            eventRepo.save(event);

            // 查询本活动中已确认的学生 ID 集合（用于排除 2志愿）
            Set<Long> confirmedStudentIds = selectionRepo.findByEventAndStatus(event, "CONFIRMED")
                    .stream()
                    .map(cs -> cs.getStudent().getId())
                    .collect(Collectors.toSet());

            if ("GLOBAL".equals(course.getCapacityMode())) {
                lotteryGlobal(course, confirmedStudentIds);
            } else {
                lotteryPerClass(course, confirmedStudentIds);
            }

            // 每门课之间等待60秒（最后一门不等）
            if (i < total - 1) {
                Thread.sleep(60_000L);
            }
        }

        // 全部处理完毕，推进到第二轮
        event.setStatus("ROUND2");
        event.setLotteryNote("抽签完成，已进入第二轮");
        eventRepo.save(event);
    }

    /**
     * GLOBAL 模式抽签：
     * 1st pref 全量参与；
     * 2nd pref 中排除已确认学生（他们的 1志愿已在前面的课程中中签）。
     */
    private void lotteryGlobal(Course course, Set<Long> confirmedStudentIds) {
        List<CourseSelection> allPending = selectionRepo
                .findByCourseAndStatusOrderBySelectedAtAsc(course, "PENDING");
        if (allPending.isEmpty()) return;

        // 分离 1志愿 和 2志愿
        List<CourseSelection> pref1 = allPending.stream()
                .filter(cs -> cs.getPreference() == 1).collect(Collectors.toList());
        Set<Long> pref1StudentIds = pref1.stream()
                .map(cs -> cs.getStudent().getId())
                .collect(Collectors.toSet());
        List<CourseSelection> pref2 = allPending.stream()
                .filter(cs -> cs.getPreference() == 2
                        && !confirmedStudentIds.contains(cs.getStudent().getId())
                        && !pref1StudentIds.contains(cs.getStudent().getId()))
                .collect(Collectors.toList());

        int capacity = course.getTotalCapacity();
        Collections.shuffle(pref1);
        int confirmed = 0;

        // 先从 1志愿中抽
        for (CourseSelection cs : pref1) {
            if (confirmed < capacity) {
                cs.setStatus("CONFIRMED");
                cs.setConfirmedAt(LocalDateTime.now());
                confirmed++;
            } else {
                cs.setStatus("LOTTERY_FAIL");
            }
            selectionRepo.save(cs);
        }

        // 剩余名额从 2志愿中补
        Collections.shuffle(pref2);
        for (CourseSelection cs : pref2) {
            if (confirmed < capacity) {
                cs.setStatus("CONFIRMED");
                cs.setConfirmedAt(LocalDateTime.now());
                confirmed++;
            } else {
                cs.setStatus("LOTTERY_FAIL");
            }
            selectionRepo.save(cs);
        }

        // 被排除的已确认学生的 2志愿直接标 CANCELLED（他们已有 1志愿）
        allPending.stream()
                .filter(cs -> cs.getPreference() == 2
                        && (confirmedStudentIds.contains(cs.getStudent().getId())
                        || pref1StudentIds.contains(cs.getStudent().getId()))
                        && "PENDING".equals(cs.getStatus()))
                .forEach(cs -> {
                    cs.setStatus("CANCELLED");
                    selectionRepo.save(cs);
                });

        course.setCurrentCount(confirmed);
        courseRepo.save(course);
    }

    /**
     * PER_CLASS 模式抽签：按班分组，每班内单独抽；
     * 2志愿同样排除已确认学生。
     */
    private void lotteryPerClass(Course course, Set<Long> confirmedStudentIds) {
        List<CourseClassCapacity> capacities = capacityRepo.findByCourse(course);
        int totalConfirmed = 0;

        for (CourseClassCapacity cap : capacities) {
            Long classId = cap.getSchoolClass().getId();
            List<CourseSelection> allPending = selectionRepo.findPendingByClassId(course, classId);
            if (allPending.isEmpty()) continue;

            List<CourseSelection> pref1 = allPending.stream()
                    .filter(cs -> cs.getPreference() == 1).collect(Collectors.toList());
            Set<Long> pref1StudentIds = pref1.stream()
                    .map(cs -> cs.getStudent().getId())
                    .collect(Collectors.toSet());
            List<CourseSelection> pref2 = allPending.stream()
                    .filter(cs -> cs.getPreference() == 2
                            && !confirmedStudentIds.contains(cs.getStudent().getId())
                            && !pref1StudentIds.contains(cs.getStudent().getId()))
                    .collect(Collectors.toList());

            Collections.shuffle(pref1);
            int confirmed = 0;
            int max = cap.getMaxCapacity();

            for (CourseSelection cs : pref1) {
                if (confirmed < max) {
                    cs.setStatus("CONFIRMED");
                    cs.setConfirmedAt(LocalDateTime.now());
                    confirmed++;
                } else {
                    cs.setStatus("LOTTERY_FAIL");
                }
                selectionRepo.save(cs);
            }

            Collections.shuffle(pref2);
            for (CourseSelection cs : pref2) {
                if (confirmed < max) {
                    cs.setStatus("CONFIRMED");
                    cs.setConfirmedAt(LocalDateTime.now());
                    confirmed++;
                } else {
                    cs.setStatus("LOTTERY_FAIL");
                }
                selectionRepo.save(cs);
            }

            // 标记被排除的 2志愿
            allPending.stream()
                    .filter(cs -> cs.getPreference() == 2
                            && (confirmedStudentIds.contains(cs.getStudent().getId())
                            || pref1StudentIds.contains(cs.getStudent().getId()))
                            && "PENDING".equals(cs.getStatus()))
                    .forEach(cs -> {
                        cs.setStatus("CANCELLED");
                        selectionRepo.save(cs);
                    });

            cap.setCurrentCount(confirmed);
            capacityRepo.save(cap);
            totalConfirmed += confirmed;
        }

        course.setCurrentCount(totalConfirmed);
        courseRepo.save(course);
    }
}
