package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepo;
    private final CourseClassCapacityRepository capacityRepo;
    private final CourseSelectionRepository selectionRepo;
    private final EventStudentRepository eventStudentRepo;
    private final SelectionEventRepository eventRepo;
    private final StudentRepository studentRepo;
    private final StudentNotificationService studentNotificationService;
    private final StudentService studentService;
    private final ClassService classService;

    // ===== 课程 CRUD =====

    public List<Course> findByEvent(SelectionEvent event) {
        return courseRepo.findByEventOrderByNameAsc(event);
    }

    public void validateThirdRoundTeacherAssignments(SelectionEvent event) {
        if (event == null) {
            return;
        }
        List<String> unassignedCourseNames = findByEvent(event).stream()
                .filter(course -> !"CLOSED".equals(course.getStatus()))
                .filter(course -> course.getTeacher() == null || course.getTeacher().getId() == null)
                .map(Course::getName)
                .toList();
        if (!unassignedCourseNames.isEmpty()) {
            throw new IllegalStateException("以下课程尚未分配授课教师，暂不能进入第三轮：" + String.join("、", unassignedCourseNames));
        }
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
        List<CourseSelection> selections = selectionRepo.findByEventAndStudent(event, student);
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new RuntimeException("该课程当前不可选");
        }

        // 同一志愿位已有记录则覆盖，先转回草稿再保存，支持修改志愿。
        try {
            markRound1SelectionsAsDraft(selections);
            Optional<CourseSelection> existingPreference = selections.stream()
                    .filter(existing -> existing.getRound() == 1)
                    .filter(existing -> existing.getPreference() == preference)
                    .findFirst();
            Optional<CourseSelection> sameCourseInOtherPreference = selections.stream()
                    .filter(existing -> existing.getRound() == 1)
                    .filter(existing -> existing.getPreference() != preference)
                    .filter(existing -> existing.getCourse() != null
                            && courseId.equals(existing.getCourse().getId()))
                    .filter(existing -> !"CANCELLED".equals(existing.getStatus()))
                    .findFirst();
            if (sameCourseInOtherPreference.isPresent()) {
                CourseSelection source = sameCourseInOtherPreference.get();
                if (existingPreference.isPresent()) {
                    CourseSelection target = existingPreference.get();
                    target.setCourse(course);
                    target.setRound(1);
                    target.setStatus("DRAFT");
                    target.setSelectedAt(now);
                    target.setConfirmedAt(null);
                    selectionRepo.delete(source);
                    return selectionRepo.saveAndFlush(target);
                }
                source.setPreference(preference);
                source.setRound(1);
                source.setStatus("DRAFT");
                source.setSelectedAt(now);
                source.setConfirmedAt(null);
                return selectionRepo.saveAndFlush(source);
            }
            if (existingPreference.isPresent()) {
                CourseSelection selection = existingPreference.get();
                if (selection.getCourse() != null
                        && courseId.equals(selection.getCourse().getId())
                        && !"CANCELLED".equals(selection.getStatus())) {
                    throw new RuntimeException(preference == 1
                            ? "该课程已经是您的第一志愿，请勿重复提交"
                            : "该课程已经是您的第二志愿，请勿重复提交");
                }
                selection.setCourse(course);
                selection.setRound(1);
                selection.setStatus("DRAFT");
                selection.setSelectedAt(now);
                selection.setConfirmedAt(null);
                return selectionRepo.saveAndFlush(selection);
            }

            CourseSelection created = new CourseSelection();
            created.setEvent(event);
            created.setCourse(course);
            created.setStudent(student);
            created.setPreference(preference);
            created.setRound(1);
            created.setStatus("DRAFT");
            created.setSelectedAt(now);
            return selectionRepo.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            throw resolveSubmitPreferenceConflict(student, event, courseId, preference);
        }
    }

    // ===== 第二轮：先到先得抢课 =====

    @Transactional
    public int saveRound1Draft(Student student, Long eventId) {
        SelectionEvent event = loadRound1EventForEdit(student, eventId);
        List<CourseSelection> activeSelections = findActiveRound1Selections(event, student);
        if (activeSelections.isEmpty()) {
            throw new RuntimeException("请至少选择一个志愿后再保存草稿");
        }
        markRound1SelectionsAsDraft(activeSelections);
        selectionRepo.saveAllAndFlush(activeSelections);
        return activeSelections.size();
    }

    @Transactional
    public int confirmRound1Selections(Student student, Long eventId) {
        SelectionEvent event = loadRound1EventForEdit(student, eventId);
        List<CourseSelection> activeSelections = findActiveRound1Selections(event, student);
        Optional<CourseSelection> pref1 = activeSelections.stream()
                .filter(selection -> selection.getPreference() == 1)
                .findFirst();
        if (pref1.isEmpty()) {
            throw new RuntimeException("请先选择第一志愿");
        }

        LocalDateTime now = LocalDateTime.now();
        activeSelections.forEach(selection -> {
            selection.setStatus("PENDING");
            selection.setSelectedAt(now);
            selection.setConfirmedAt(null);
        });
        selectionRepo.saveAllAndFlush(activeSelections);
        return activeSelections.size();
    }

    @Transactional
    public CourseSelection selectRound2(Student student, Long eventId, Long courseId) {
        long startedAtNanos = System.nanoTime();
        SelectionEvent event = null;
        Course course = null;
        try {
            event = eventRepo.findById(eventId)
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
            if (eventStudentRepo.existsByEvent(event)
                    && !eventStudentRepo.existsByEventAndStudent(event, student)) {
                throw new RuntimeException("您不在本次选课活动的参与名单中");
            }
            if (selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")) {
                throw new RuntimeException("您已成功选课，无需再次抢课");
            }

            course = findById(courseId);
            if (!"ACTIVE".equals(course.getStatus())) {
                throw new RuntimeException("该课程当前不可选");
            }

            if ("GLOBAL".equals(course.getCapacityMode())) {
                if (courseRepo.incrementCurrentCountIfAvailable(courseId) == 0) {
                    throw new RuntimeException("该课程名额已满，请选择其他课程");
                }
            } else {
                SchoolClass schoolClass = student.getSchoolClass();
                if (schoolClass == null) {
                    throw new RuntimeException("您未分配行政班，无法参与按班名额的课程");
                }
                if (capacityRepo.findByCourseAndSchoolClass(course, schoolClass).isEmpty()) {
                    throw new RuntimeException("您的班级没有该课程的名额配置");
                }
                if (capacityRepo.incrementCurrentCountIfAvailable(courseId, schoolClass.getId()) == 0) {
                    throw new RuntimeException("您班级的该课程名额已满，请选择其他课程");
                }
                courseRepo.incrementCurrentCount(courseId);
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
            CourseSelection saved = selectionRepo.saveAndFlush(cs);
            studentService.assignElectiveClassFromCourse(student, course);
            logRound2Attempt("SUCCESS", student, event, course, saved.getId(), null, startedAtNanos);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            RuntimeException resolved = resolveRound2Conflict(student, event, course);
            logRound2Attempt("REJECTED", student, event, course, null, resolved, startedAtNanos);
            throw resolved;
        } catch (RuntimeException ex) {
            logRound2Attempt("REJECTED", student, event, course, null, ex, startedAtNanos);
            throw ex;
        }
    }

    // ===== 退课 =====

    @Transactional
    public int finalizeEndedRound2Event(Long eventId) {
        long startedAtNanos = System.nanoTime();
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Selection event not found"));
        if (!"ROUND2".equals(event.getStatus())) {
            return 0;
        }
        if (event.getRound2End() == null || LocalDateTime.now().isBefore(event.getRound2End())) {
            return 0;
        }

        log.info("courseSelection.round2.finalize.started eventId={} round2End={}", event.getId(), event.getRound2End());
        int assignedCount = autoAssignRound2Fallback(event);
        validateThirdRoundTeacherAssignments(event);
        event.setStatus("CLOSED");
        event.setLotteryNote(assignedCount > 0
                ? "Round2 closed, auto-assigned " + assignedCount + " students"
                : "Round2 closed");
        eventRepo.save(event);
        classService.syncElectiveClassesFromEvent(event);
        studentService.syncElectiveClassesForEvent(event);
        int confirmedCount = selectionRepo.findByEventAndStatus(event, "CONFIRMED").size();
        log.info("courseSelection.round2.finalize.completed eventId={} assignedCount={} confirmedCount={} latencyMs={}",
                event.getId(),
                assignedCount,
                confirmedCount,
                elapsedMillis(startedAtNanos));
        return assignedCount;
    }

    private int autoAssignRound2Fallback(SelectionEvent event) {
        List<Student> candidates = findRound2AutoAssignCandidates(event);
        if (candidates.isEmpty()) {
            log.info("courseSelection.round2.autoAssign.skipped eventId={} reason=no_candidates", event.getId());
            return 0;
        }

        List<Course> activeCourses = courseRepo.findByEventAndStatusOrderByNameAsc(event, "ACTIVE");
        if (activeCourses.isEmpty()) {
            log.warn("courseSelection.round2.autoAssign.noActiveCourses eventId={} candidates={}",
                    event.getId(),
                    candidates.size());
            for (Student student : candidates) {
                studentNotificationService.notifyRound2ClosedWithoutCourse(event, student);
            }
            return 0;
        }

        Collections.shuffle(candidates);
        int assignedCount = 0;
        for (Student student : candidates) {
            if (selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")) {
                continue;
            }
            CourseSelection selection = tryAutoAssignStudent(event, student, activeCourses);
            if (selection != null) {
                assignedCount++;
                studentNotificationService.notifyRound2AutoAssignment(event, student, selection.getCourse());
            } else {
                studentNotificationService.notifyRound2ClosedWithoutCourse(event, student);
            }
        }
        log.info("courseSelection.round2.autoAssign.completed eventId={} candidates={} activeCourses={} assignedCount={} unassignedCount={}",
                event.getId(),
                candidates.size(),
                activeCourses.size(),
                assignedCount,
                Math.max(0, candidates.size() - assignedCount));
        return assignedCount;
    }

    private List<Student> findRound2AutoAssignCandidates(SelectionEvent event) {
        List<Student> participatingStudents = eventStudentRepo.existsByEvent(event)
                ? eventStudentRepo.findStudentsByEvent(event)
                : studentRepo.findBySchoolOrderByStudentNo(event.getSchool());
        if (participatingStudents.isEmpty()) {
            return List.of();
        }

        Set<Long> failedStudentIds = selectionRepo.findByEvent(event).stream()
                .filter(selection -> selection.getRound() == 1)
                .filter(selection -> "LOTTERY_FAIL".equals(selection.getStatus()))
                .map(CourseSelection::getStudent)
                .filter(student -> student != null && student.getId() != null)
                .map(Student::getId)
                .collect(java.util.stream.Collectors.toSet());

        if (failedStudentIds.isEmpty()) {
            return List.of();
        }

        List<Student> candidates = new ArrayList<>();
        Set<Long> seenStudentIds = new HashSet<>();
        for (Student student : participatingStudents) {
            if (student == null || student.getId() == null) {
                continue;
            }
            if (!failedStudentIds.contains(student.getId())) {
                continue;
            }
            if (selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")) {
                continue;
            }
            if (seenStudentIds.add(student.getId())) {
                candidates.add(student);
            }
        }
        return candidates;
    }

    private CourseSelection tryAutoAssignStudent(SelectionEvent event, Student student, List<Course> activeCourses) {
        List<Course> shuffledCourses = new ArrayList<>(activeCourses);
        Collections.shuffle(shuffledCourses);
        for (Course course : shuffledCourses) {
            if (!tryReserveRound2Capacity(course, student)) {
                continue;
            }

            CourseSelection selection = new CourseSelection();
            selection.setEvent(event);
            selection.setCourse(course);
            selection.setStudent(student);
            selection.setPreference(0);
            selection.setRound(2);
            selection.setStatus("CONFIRMED");
            selection.setSelectedAt(LocalDateTime.now());
            selection.setConfirmedAt(LocalDateTime.now());
            CourseSelection saved = selectionRepo.saveAndFlush(selection);
            studentService.assignElectiveClassFromCourse(student, course);
            return saved;
        }
        return null;
    }

    private boolean tryReserveRound2Capacity(Course course, Student student) {
        if ("GLOBAL".equals(course.getCapacityMode())) {
            if (courseRepo.incrementCurrentCountIfAvailable(course.getId()) == 0) {
                return false;
            }
            return true;
        }

        SchoolClass schoolClass = student.getSchoolClass();
        if (schoolClass == null || schoolClass.getId() == null) {
            return false;
        }

        if (capacityRepo.findByCourseAndSchoolClass(course, schoolClass).isEmpty()) {
            return false;
        }
        if (capacityRepo.incrementCurrentCountIfAvailable(course.getId(), schoolClass.getId()) == 0) {
            return false;
        }

        courseRepo.incrementCurrentCount(course.getId());
        return true;
    }

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
        validateStudentDropEligibility(cs);
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
        studentNotificationService.notifyDropSuccess(student, course, cs.getEvent());
        studentService.refreshElectiveClassFromConfirmedSelections(student);
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
        reserveCapacityForAdminEnroll(course, student, forceOverflow);
        CourseSelection cs = selectionRepo.findByEventAndStudentAndPreference(event, student, 0)
                .orElseGet(CourseSelection::new);
        cs.setEvent(event);
        cs.setCourse(course);
        cs.setStudent(student);
        cs.setPreference(0);
        cs.setRound(0);
        cs.setStatus("CONFIRMED");
        cs.setSelectedAt(LocalDateTime.now());
        cs.setConfirmedAt(LocalDateTime.now());
        selectionRepo.save(cs);
        studentService.assignElectiveClassFromCourse(student, course);
        studentNotificationService.notifyAdminEnrollSuccess(student, course, event, forceOverflow);
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
        cs.setConfirmedAt(null);
        selectionRepo.save(cs);
        releaseCapacityForAdminDrop(cs);
        studentService.refreshElectiveClassFromConfirmedSelections(cs.getStudent());
    }

    private void releaseCapacityForAdminDrop(CourseSelection selection) {
        Course course = courseRepo.findByIdForUpdate(selection.getCourse().getId())
                .orElseThrow(() -> new RuntimeException("课程不存在"));
        course.setCurrentCount(Math.max(0, course.getCurrentCount() - 1));
        courseRepo.save(course);

        if (!"PER_CLASS".equals(course.getCapacityMode())) {
            return;
        }

        Student student = selection.getStudent();
        SchoolClass schoolClass = student.getSchoolClass();
        if (schoolClass == null) {
            return;
        }
        capacityRepo.findByCourseIdAndClassIdForUpdate(course.getId(), schoolClass.getId())
                .ifPresent(capacity -> {
                    capacity.setCurrentCount(Math.max(0, capacity.getCurrentCount() - 1));
                    capacityRepo.save(capacity);
                });
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

    public int countConfirmedUniqueEnrollments(Course course) {
        return findConfirmedUniqueEnrollments(course).size();
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

    private SelectionEvent loadRound1EventForEdit(Student student, Long eventId) {
        SelectionEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("当前没有进行中的选课活动"));
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
        return event;
    }

    private List<CourseSelection> findActiveRound1Selections(SelectionEvent event, Student student) {
        return selectionRepo.findByEventAndStudent(event, student).stream()
                .filter(selection -> selection.getRound() == 1)
                .filter(selection -> selection.getPreference() == 1 || selection.getPreference() == 2)
                .filter(selection -> !"CANCELLED".equals(selection.getStatus()))
                .toList();
    }

    private void markRound1SelectionsAsDraft(List<CourseSelection> selections) {
        selections.stream()
                .filter(selection -> selection.getRound() == 1)
                .filter(selection -> selection.getPreference() == 1 || selection.getPreference() == 2)
                .filter(selection -> !"CANCELLED".equals(selection.getStatus()))
                .forEach(selection -> {
                    selection.setStatus("DRAFT");
                    selection.setConfirmedAt(null);
                });
    }

    private RuntimeException resolveSubmitPreferenceConflict(Student student,
                                                            SelectionEvent event,
                                                            Long courseId,
                                                            int preference) {
        boolean sameCourseInOtherPreference = selectionRepo.findByEventAndStudent(event, student).stream()
                .filter(existing -> existing.getPreference() != preference)
                .anyMatch(existing -> existing.getCourse() != null
                        && courseId.equals(existing.getCourse().getId())
                        && !"CANCELLED".equals(existing.getStatus()));
        if (sameCourseInOtherPreference) {
            return new RuntimeException("同一课程不能同时填报为第一志愿和第二志愿");
        }

        Optional<CourseSelection> existingPreference = selectionRepo.findByEventAndStudentAndPreference(event, student, preference);
        if (existingPreference.isPresent()) {
            CourseSelection selection = existingPreference.get();
            if (selection.getCourse() != null
                    && courseId.equals(selection.getCourse().getId())
                    && !"CANCELLED".equals(selection.getStatus())) {
                return new RuntimeException(preference == 1
                        ? "该课程已经是您的第一志愿，请勿重复提交"
                        : "该课程已经是您的第二志愿，请勿重复提交");
            }
            return new RuntimeException(preference == 1
                    ? "您已选择过一个第一志愿，请刷新页面后重试"
                    : "您已选择过一个第二志愿，请刷新页面后重试");
        }

        return new RuntimeException("志愿提交失败，请刷新页面后重试");
    }

    private RuntimeException resolveRound2Conflict(Student student, SelectionEvent event, Course course) {
        if (selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")) {
            return new RuntimeException("您已成功选课，无需再次抢课");
        }
        Course refreshedCourse = courseRepo.findById(course.getId()).orElse(course);
        if (getRemainingCapacity(refreshedCourse, student) <= 0) {
            if ("PER_CLASS".equals(refreshedCourse.getCapacityMode())) {
                return new RuntimeException("您班级的该课程名额已满，请选择其他课程");
            }
            return new RuntimeException("该课程名额已满，请选择其他课程");
        }
        return new RuntimeException("抢课失败，请刷新页面后重试");
    }

    private void logRound2Attempt(String outcome,
                                  Student student,
                                  SelectionEvent event,
                                  Course course,
                                  Long selectionId,
                                  RuntimeException exception,
                                  long startedAtNanos) {
        Long studentId = student != null ? student.getId() : null;
        Long classId = student != null && student.getSchoolClass() != null ? student.getSchoolClass().getId() : null;
        Long resolvedEventId = event != null ? event.getId() : null;
        String eventStatus = event != null ? event.getStatus() : null;
        Long resolvedCourseId = course != null ? course.getId() : null;
        String capacityMode = course != null ? course.getCapacityMode() : null;
        Integer remainingCapacity = resolveRemainingCapacity(course, student);
        long latencyMs = elapsedMillis(startedAtNanos);
        if (exception == null) {
            log.info("courseSelection.round2.submit outcome={} eventId={} eventStatus={} courseId={} studentId={} classId={} capacityMode={} selectionId={} remainingCapacity={} latencyMs={}",
                    outcome,
                    resolvedEventId,
                    eventStatus,
                    resolvedCourseId,
                    studentId,
                    classId,
                    capacityMode,
                    selectionId,
                    remainingCapacity,
                    latencyMs);
            return;
        }
        log.warn("courseSelection.round2.submit outcome={} reason={} eventId={} eventStatus={} courseId={} studentId={} classId={} capacityMode={} remainingCapacity={} latencyMs={} message={}",
                outcome,
                classifyRound2Failure(exception.getMessage()),
                resolvedEventId,
                eventStatus,
                resolvedCourseId,
                studentId,
                classId,
                capacityMode,
                remainingCapacity,
                latencyMs,
                exception.getMessage());
    }

    private Integer resolveRemainingCapacity(Course course, Student student) {
        if (course == null || student == null) {
            return null;
        }
        try {
            Course refreshedCourse = courseRepo.findById(course.getId()).orElse(course);
            return getRemainingCapacity(refreshedCourse, student);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String classifyRound2Failure(String message) {
        if (message == null || message.isBlank()) {
            return "UNKNOWN";
        }
        if (message.contains("活动不存在")) {
            return "EVENT_NOT_FOUND";
        }
        if (message.contains("当前不在第二轮")) {
            return "NOT_IN_ROUND2";
        }
        if (message.contains("尚未开始")) {
            return "ROUND2_NOT_STARTED";
        }
        if (message.contains("已结束")) {
            return "ROUND2_ENDED";
        }
        if (message.contains("参与名单")) {
            return "NOT_IN_EVENT_LIST";
        }
        if (message.contains("已成功选课")) {
            return "ALREADY_CONFIRMED";
        }
        if (message.contains("当前不可选")) {
            return "COURSE_INACTIVE";
        }
        if (message.contains("未分配行政班")) {
            return "CLASS_NOT_ASSIGNED";
        }
        if (message.contains("没有该课程的名额配置")) {
            return "CLASS_CAPACITY_NOT_CONFIGURED";
        }
        if (message.contains("名额已满")) {
            return "CAPACITY_FULL";
        }
        if (message.contains("刷新页面后重试")) {
            return "CONCURRENT_CONFLICT";
        }
        return "UNKNOWN";
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
    public boolean canDropSelection(CourseSelection selection) {
        if (selection == null
                || !"CONFIRMED".equals(selection.getStatus())
                || selection.getRound() != 1) {
            return false;
        }

        SelectionEvent event = selection.getEvent();
        if (event == null || !"ROUND2".equals(event.getStatus())) {
            return false;
        }
        return event.getRound2End() == null || !LocalDateTime.now().isAfter(event.getRound2End());
    }

    private void validateStudentDropEligibility(CourseSelection selection) {
        if (!canDropSelection(selection)) {
            throw new RuntimeException("当前仅支持第一轮已确认课程在第二轮期间退课");
        }
    }
}
