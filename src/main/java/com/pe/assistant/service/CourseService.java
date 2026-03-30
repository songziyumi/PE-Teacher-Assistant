package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepo;
    private final CourseClassCapacityRepository capacityRepo;
    private final CourseSelectionRepository selectionRepo;
    private final EventStudentRepository eventStudentRepo;
    private final SelectionEventRepository eventRepo;
    private final StudentRepository studentRepo;

    // ===== 课程 CRUD =====

    public List<Course> findByEvent(SelectionEvent event) {
        return courseRepo.findByEventOrderByNameAsc(event);
    }

    public Course findById(Long id) {
        return courseRepo.findById(id).orElseThrow(() -> new RuntimeException("课程不存在"));
    }

    @Transactional
    public Course saveCourse(Course course, List<Long> classIds, List<Integer> classCapacities) {
        Course saved = courseRepo.save(course);
        if ("PER_CLASS".equals(course.getCapacityMode()) && classIds != null) {
            Map<Long, Integer> existingCounts = new HashMap<>();
            for (CourseClassCapacity existing : capacityRepo.findByCourse(saved)) {
                if (existing.getSchoolClass() != null && existing.getSchoolClass().getId() != null) {
                    existingCounts.put(existing.getSchoolClass().getId(), existing.getCurrentCount());
                }
            }
            capacityRepo.deleteByCourse(saved);
            int total = 0;
            for (int i = 0; i < classIds.size(); i++) {
                SchoolClass sc = new SchoolClass();
                sc.setId(classIds.get(i));
                CourseClassCapacity cap = new CourseClassCapacity();
                cap.setCourse(saved);
                cap.setSchoolClass(sc);
                int max = (classCapacities != null && i < classCapacities.size())
                        ? classCapacities.get(i) : 0;
                cap.setMaxCapacity(max);
                cap.setCurrentCount(existingCounts.getOrDefault(classIds.get(i), 0));
                capacityRepo.save(cap);
                total += max;
            }
            saved.setTotalCapacity(total);
            courseRepo.save(saved);
        }
        return saved;
    }

    @Transactional
    public void deleteCourse(Long id) {
        Course course = findById(id);
        if (!selectionRepo.findByCourseOrderBySelectedAtAsc(course).isEmpty()) {
            throw new RuntimeException("课程已有报名记录，不能直接删除");
        }
        capacityRepo.deleteByCourse(course);
        courseRepo.delete(course);
    }

    public List<CourseClassCapacity> findCapacities(Course course) {
        return capacityRepo.findByCourse(course);
    }

    // ===== 第一轮：提交志愿 =====

    @Transactional
    public CourseSelection submitPreference(Student student, Long eventId, Long courseId, int preference) {
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (!"ROUND1".equals(event.getStatus())) {
            throw new RuntimeException("当前不在第一轮选课阶段");
        }
        LocalDateTime now = LocalDateTime.now();
        if (event.getRound1Start() != null && now.isBefore(event.getRound1Start())) {
            throw new RuntimeException("第一轮选课尚未开始");
        }
        if (event.getRound1End() != null && now.isAfter(event.getRound1End())) {
            throw new RuntimeException("第一轮选课已结束");
        }
        if (eventStudentRepo.existsByEvent(event)
                && !eventStudentRepo.existsByEventAndStudent(event, student)) {
            throw new RuntimeException("您不在本次选课活动的参与名单中");
        }
        if (preference != 1 && preference != 2) {
            throw new RuntimeException("志愿序号必须为 1 或 2");
        }

        Course course = findById(courseId);
        boolean duplicatedInOtherPreference = selectionRepo.findByEventAndStudent(event, student).stream()
                .filter(existing -> existing.getPreference() != preference)
                .anyMatch(existing -> existing.getCourse() != null
                        && courseId.equals(existing.getCourse().getId())
                        && !"CANCELLED".equals(existing.getStatus()));
        if (duplicatedInOtherPreference) {
            throw new RuntimeException("同一课程不能同时填报为第一志愿和第二志愿");
        }
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new RuntimeException("该课程当前不可选");
        }

        // 同一志愿位已有记录则覆盖（先删再建，实现修改志愿功能）
        selectionRepo.findByEventAndStudentAndPreference(event, student, preference)
                .ifPresent(old -> selectionRepo.delete(old));

        CourseSelection cs = new CourseSelection();
        cs.setEvent(event);
        cs.setCourse(course);
        cs.setStudent(student);
        cs.setPreference(preference);
        cs.setRound(1);
        cs.setStatus("PENDING");
        cs.setSelectedAt(LocalDateTime.now());
        return selectionRepo.save(cs);
    }

    // ===== 第二轮：先到先得抢课 =====

    @Transactional
    public CourseSelection selectRound2(Student student, Long eventId, Long courseId) {
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));

        if (!"ROUND2".equals(event.getStatus())) {
            throw new RuntimeException("当前不在第二轮选课阶段");
        }
        LocalDateTime now = LocalDateTime.now();
        if (event.getRound2Start() != null && now.isBefore(event.getRound2Start())) {
            throw new RuntimeException("第二轮选课尚未开始");
        }
        if (event.getRound2End() != null && now.isAfter(event.getRound2End())) {
            throw new RuntimeException("第二轮选课已结束");
        }
        // 校验是否在参与名单（未配置名单时全校均可）
        if (eventStudentRepo.existsByEvent(event)
                && !eventStudentRepo.existsByEventAndStudent(event, student)) {
            throw new RuntimeException("您不在本次选课活动的参与名单中");
        }
        // 校验学生是否有第二轮资格（无 CONFIRMED 记录）
        if (selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")) {
            throw new RuntimeException("您已成功选课，无需再次抢课");
        }

        Course course = findById(courseId);
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new RuntimeException("该课程当前不可选");
        }

        // 按名额模式加锁检查
        if ("GLOBAL".equals(course.getCapacityMode())) {
            Course locked = courseRepo.findByIdForUpdate(courseId)
                    .orElseThrow(() -> new RuntimeException("课程不存在"));
            if (locked.getCurrentCount() >= locked.getTotalCapacity()) {
                throw new RuntimeException("该课程名额已满，请选择其他课程");
            }
            locked.setCurrentCount(locked.getCurrentCount() + 1);
            courseRepo.save(locked);
        } else {
            // PER_CLASS：按学生所在班级找对应名额
            SchoolClass sc = student.getSchoolClass();
            if (sc == null) throw new RuntimeException("您未分配行政班，无法参与按班名额的课程");
            CourseClassCapacity cap = capacityRepo
                    .findByCourseIdAndClassIdForUpdate(courseId, sc.getId())
                    .orElseThrow(() -> new RuntimeException("您的班级没有该课程的名额配置"));
            if (cap.getCurrentCount() >= cap.getMaxCapacity()) {
                throw new RuntimeException("您班级的该课程名额已满，请选择其他课程");
            }
            cap.setCurrentCount(cap.getCurrentCount() + 1);
            capacityRepo.save(cap);
            course.setCurrentCount(course.getCurrentCount() + 1);
            courseRepo.save(course);
        }

        CourseSelection cs = new CourseSelection();
        cs.setEvent(event);
        cs.setCourse(course);
        cs.setStudent(student);
        cs.setPreference(0);
        cs.setRound(2);
        cs.setStatus("CONFIRMED");
        cs.setSelectedAt(LocalDateTime.now());
        cs.setConfirmedAt(LocalDateTime.now());
        return selectionRepo.save(cs);
    }

    // ===== 退课 =====

    @Transactional
    public void dropCourse(Student student, Long selectionId) {
        CourseSelection cs = selectionRepo.findById(selectionId)
                .orElseThrow(() -> new RuntimeException("选课记录不存在"));
        if (!cs.getStudent().getId().equals(student.getId())) {
            throw new RuntimeException("无权操作他人选课记录");
        }
        if (!"CONFIRMED".equals(cs.getStatus())) {
            throw new RuntimeException("只能退已确认的课程");
        }
        cs.setStatus("CANCELLED");
        selectionRepo.save(cs);

        // 归还名额
        Course course = cs.getCourse();
        if ("GLOBAL".equals(course.getCapacityMode())) {
            course.setCurrentCount(Math.max(0, course.getCurrentCount() - 1));
            courseRepo.save(course);
        } else {
            SchoolClass sc = student.getSchoolClass();
            if (sc != null) {
                capacityRepo.findByCourseAndSchoolClass(course, sc).ifPresent(cap -> {
                    cap.setCurrentCount(Math.max(0, cap.getCurrentCount() - 1));
                    capacityRepo.save(cap);
                });
            }
            course.setCurrentCount(Math.max(0, course.getCurrentCount() - 1));
            courseRepo.save(course);
        }
    }

    // ===== 管理员手动调整 =====

    @Transactional
    public void adminEnroll(Long courseId, Long studentId, Long eventId) {
        adminEnroll(courseId, studentId, eventId, false);
    }

    @Transactional
    public void adminEnroll(Long courseId, Long studentId, Long eventId, boolean forceOverflow) {
        Course course = findById(courseId);
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("活动不存在"));
        if (course.getEvent() == null || !course.getEvent().getId().equals(event.getId())) {
            throw new RuntimeException("课程不属于该选课活动");
        }
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        // 检查是否已有确认记录
        if (selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")) {
            throw new RuntimeException("该学生已有确认的选课记录");
        }
        CourseSelection cs = new CourseSelection();
        cs.setEvent(event);
        cs.setCourse(course);
        cs.setStudent(student);
        cs.setPreference(0);
        cs.setRound(0);
        cs.setStatus("CONFIRMED");
        cs.setSelectedAt(LocalDateTime.now());
        cs.setConfirmedAt(LocalDateTime.now());
        reserveCapacityForAdminEnroll(course, student, forceOverflow);
        selectionRepo.save(cs);
    }

    private void reserveCapacityForAdminEnroll(Course course, Student student, boolean forceOverflow) {
        if ("GLOBAL".equals(course.getCapacityMode())) {
            Course locked = courseRepo.findByIdForUpdate(course.getId())
                    .orElseThrow(() -> new RuntimeException("课程不存在"));
            if (!forceOverflow && locked.getCurrentCount() >= locked.getTotalCapacity()) {
                throw new RuntimeException("课程总名额已满");
            }
            locked.setCurrentCount(locked.getCurrentCount() + 1);
            courseRepo.save(locked);
            return;
        }

        SchoolClass sc = student.getSchoolClass();
        if (sc == null) {
            throw new RuntimeException("学生未分配行政班");
        }
        CourseClassCapacity cap = capacityRepo.findByCourseIdAndClassIdForUpdate(course.getId(), sc.getId())
                .orElseThrow(() -> new RuntimeException("该学生所在班级未配置课程名额"));
        if (!forceOverflow && cap.getCurrentCount() >= cap.getMaxCapacity()) {
            throw new RuntimeException("该班级课程名额已满");
        }
        cap.setCurrentCount(cap.getCurrentCount() + 1);
        capacityRepo.save(cap);

        Course locked = courseRepo.findByIdForUpdate(course.getId())
                .orElseThrow(() -> new RuntimeException("课程不存在"));
        if (!forceOverflow && locked.getCurrentCount() >= locked.getTotalCapacity()) {
            throw new RuntimeException("课程总名额已满");
        }
        locked.setCurrentCount(locked.getCurrentCount() + 1);
        courseRepo.save(locked);
    }

    @Transactional
    public void adminDrop(Long selectionId) {
        CourseSelection cs = selectionRepo.findById(selectionId)
                .orElseThrow(() -> new RuntimeException("选课记录不存在"));
        cs.setStatus("CANCELLED");
        selectionRepo.save(cs);
        Course course = cs.getCourse();
        course.setCurrentCount(Math.max(0, course.getCurrentCount() - 1));
        courseRepo.save(course);
    }

    // ===== 报名名单 =====

    public List<CourseSelection> findEnrollments(Course course) {
        return selectionRepo.findByCourseOrderBySelectedAtAsc(course);
    }

    public List<CourseSelection> findConfirmedUniqueEnrollments(Course course) {
        LinkedHashMap<Long, CourseSelection> uniqueSelections = new LinkedHashMap<>();
        for (CourseSelection selection : selectionRepo.findByCourseAndStatusOrderBySelectedAtAsc(course, "CONFIRMED")) {
            uniqueSelections.putIfAbsent(selection.getStudent().getId(), selection);
        }
        return List.copyOf(uniqueSelections.values());
    }

    public List<CourseSelection> findMySelections(Student student, SelectionEvent event) {
        return selectionRepo.findByEventAndStudent(event, student);
    }

    @Transactional
    public Course savePerClassCourse(Course course,
                                     List<Long> classIds,
                                     List<Integer> classCapacities,
                                     Set<Long> allowedClassIds) {
        if (allowedClassIds == null || allowedClassIds.isEmpty()) {
            throw new RuntimeException("当前参与学生没有可用班级，无法保存按班名额课程");
        }
        if (classIds == null || classIds.isEmpty()) {
            throw new RuntimeException("请至少为一个参与班级设置名额");
        }

        LinkedHashMap<Long, Integer> capacityMap = new LinkedHashMap<>();
        for (int i = 0; i < classIds.size(); i++) {
            Long classId = classIds.get(i);
            if (classId == null || !allowedClassIds.contains(classId)) {
                continue;
            }
            int capacity = (classCapacities != null && i < classCapacities.size() && classCapacities.get(i) != null)
                    ? classCapacities.get(i) : 0;
            if (capacity < 0) {
                throw new RuntimeException("班级名额不能小于 0");
            }
            capacityMap.put(classId, capacity);
        }

        if (capacityMap.isEmpty()) {
            throw new RuntimeException("请为参与班级设置有效名额");
        }
        if (capacityMap.values().stream().noneMatch(capacity -> capacity != null && capacity > 0)) {
            throw new RuntimeException("按班名额至少需要一个班级名额大于 0");
        }

        return saveCourse(course, List.copyOf(capacityMap.keySet()), List.copyOf(capacityMap.values()));
    }

    // ===== 学生可见的课程列表（第二轮仅看有余量的） =====

    public List<Course> findActiveCoursesForStudent(SelectionEvent event, Student student) {
        if ("ROUND2".equals(event.getStatus())) {
            return courseRepo.findByEventAndStatusOrderByNameAsc(event, "ACTIVE")
                    .stream()
                    .filter(c -> getRemainingCapacity(c, student) > 0)
                    .toList();
        }
        return courseRepo.findByEventAndStatusOrderByNameAsc(event, "ACTIVE");
    }

    /** 计算该课程对该学生可见的剩余名额 */
    public int getRemainingCapacity(Course course, Student student) {
        if ("GLOBAL".equals(course.getCapacityMode())) {
            return Math.max(0, course.getTotalCapacity() - course.getCurrentCount());
        }
        if (student.getSchoolClass() == null) return 0;
        Optional<CourseClassCapacity> cap = capacityRepo
                .findByCourseAndSchoolClass(course, student.getSchoolClass());
        return cap.map(c -> Math.max(0, c.getMaxCapacity() - c.getCurrentCount())).orElse(0);
    }
}
