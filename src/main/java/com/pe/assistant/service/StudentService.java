package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final List<String> AVAILABLE_STATUSES = List.of("在籍", "休学", "毕业", "在外借读", "借读");
    private static final Set<String> AVAILABLE_STATUS_SET = Set.copyOf(AVAILABLE_STATUSES);
    private static final int STUDENT_NAME_MAX_LENGTH = 50;
    private static final int STUDENT_NO_MAX_LENGTH = 50;
    private static final Map<String, String> LEGACY_STATUS_ALIASES = Map.of(
            "外出借读", "在外借读",
            "外校借读", "借读");

    private final StudentRepository studentRepository;
    private final SchoolClassRepository classRepository;
    private final AttendanceRepository attendanceRepository;
    private final TermGradeRepository termGradeRepository;
    private final PhysicalTestRepository physicalTestRepository;
    private final HealthTestRecordRepository healthTestRecordRepository;
    private final ExamRecordRepository examRecordRepository;
    private final CourseSelectionRepository courseSelectionRepository;
    private final EventStudentRepository eventStudentRepository;
    private final CourseRepository courseRepository;
    private final CourseClassCapacityRepository courseClassCapacityRepository;

    public List<Student> findByClassId(Long classId) {
        return studentRepository.findBySchoolClassIdOrderByStudentNo(classId);
    }

    public Page<Student> findWithFilters(School school, Long classId, Long gradeId, String name,
            String studentNo, String idCard, String electiveClass, int page, int size) {
        return findWithFilters(school, classId, gradeId, name, studentNo, idCard, electiveClass, null, page, size);
    }

    public Page<Student> findWithFilters(School school, Long classId, Long gradeId, String name,
            String studentNo, String idCard, String electiveClass, String studentStatus,
            int page, int size) {
        return studentRepository.findWithFilters(school, classId, gradeId, name, studentNo, idCard,
                electiveClass, studentStatus, PageRequest.of(page, size, Sort.by("studentNo")));
    }

    public List<Student> findListWithFilters(School school, Long classId, Long gradeId, String name,
            String studentNo, String idCard, String electiveClass, String studentStatus) {
        return studentRepository.findListWithFilters(school, classId, gradeId, name, studentNo, idCard,
                electiveClass, studentStatus);
    }

    public Student findById(Long id) {
        return studentRepository.findById(id).orElseThrow();
    }

    public Optional<Student> findByIdOptional(Long id) {
        return studentRepository.findById(id);
    }

    public List<Student> findByElectiveClass(String electiveClass) {
        return studentRepository.findByElectiveClassOrderByStudentNo(electiveClass);
    }

    public List<Student> findByElectiveClassIn(List<String> names) {
        return studentRepository.findByElectiveClassInOrderByStudentNo(names);
    }

    public List<String> findElectiveClassNamesByTeacher(School school, Teacher teacher) {
        return studentRepository.findElectiveClassNamesByTeacher(school, teacher);
    }

    public List<String> findAllElectiveClassNames(School school) {
        return studentRepository.findAllElectiveClassNames(school);
    }

    public List<Student> findBySchool(School school) {
        return studentRepository.findBySchoolOrderByStudentNo(school);
    }

    public List<String> getAvailableStatuses() {
        return AVAILABLE_STATUSES;
    }

    public List<Student> findByClassIdForTeacher(School school, Long classId) {
        return filterVisibleForTeacher(school, findByClassId(classId));
    }

    public List<Student> findByElectiveClassForTeacher(School school, String electiveClass) {
        return filterVisibleForTeacher(school, findByElectiveClass(electiveClass));
    }

    public List<Student> filterVisibleForTeacher(School school, List<Student> students) {
        if (students == null || students.isEmpty()) return students;
        boolean showSuspended = school == null || !Boolean.FALSE.equals(school.getShowSuspendedOnTeacherPage());
        boolean showOutgoingBorrow = school == null || !Boolean.FALSE.equals(school.getShowOutgoingBorrowOnTeacherPage());
        List<Student> result = new ArrayList<>();
        for (Student student : students) {
            String status = normalizeStatusForDisplay(student.getStudentStatus());
            if (!showSuspended && "休学".equals(status)) continue;
            if (!showOutgoingBorrow && "在外借读".equals(status)) continue;
            result.add(student);
        }
        return result;
    }

    @Transactional
    public Student create(String name, String gender, String studentNo, String idCard,
            String electiveClass, Long classId, School school) {
        return create(name, gender, studentNo, idCard, electiveClass, classId, school, null);
    }

    @Transactional
    public Student create(String name, String gender, String studentNo, String idCard,
            String electiveClass, Long classId, School school, String studentStatus) {
        SchoolClass sc = classRepository.findById(classId).orElseThrow();
        String normalizedName = normalizeStudentName(name);
        String normalizedStudentNo = normalizeStudentNo(studentNo);
        School effectiveSchool = school != null ? school : sc.getSchool();
        ensureStudentNoUniqueBySchool(effectiveSchool, normalizedStudentNo, null);
        Student s = new Student();
        s.setName(normalizedName);
        s.setGender(gender);
        s.setStudentNo(normalizedStudentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        s.setStudentStatus(normalizeStatusForSave(studentStatus));
        s.setSchoolClass(sc);
        s.setSchool(effectiveSchool);
        return saveStudentWithDuplicateGuard(s);
    }

    /**
     * 导入时：学号已存在则更新（含选修班），不存在则新建。
     * 
     * @return true=新建，false=更新
     */
    @Transactional
    public boolean importCreateOrUpdate(String name, String gender, String studentNo, String idCard,
            String electiveClass, Long classId, School school) {
        SchoolClass sc = classRepository.findById(classId).orElseThrow();
        return studentRepository.findByStudentNoAndSchool(studentNo, school)
                .map(s -> {
                    s.setName(name);
                    s.setGender(gender);
                    s.setIdCard(idCard);
                    s.setElectiveClass(electiveClass);
                    if (s.getStudentStatus() == null || s.getStudentStatus().isBlank()) {
                        s.setStudentStatus("在籍");
                    }
                    s.setSchoolClass(sc);
                    studentRepository.save(s);
                    return false;
                })
                .orElseGet(() -> {
                    Student s = new Student();
                    s.setName(name);
                    s.setGender(gender);
                    s.setStudentNo(studentNo);
                    s.setIdCard(idCard);
                    s.setElectiveClass(electiveClass);
                    s.setStudentStatus("在籍");
                    s.setSchoolClass(sc);
                    s.setSchool(school);
                    studentRepository.save(s);
                    return true;
                });
    }

    @Transactional
    public Student update(Long id, String name, String gender, String studentNo,
            String idCard, String electiveClass, Long classId) {
        return update(id, name, gender, studentNo, idCard, electiveClass, classId, null, null);
    }

    @Transactional
    public Student update(Long id, String name, String gender, String studentNo,
            String idCard, String electiveClass, Long classId, String studentStatus) {
        return update(id, name, gender, studentNo, idCard, electiveClass, classId, studentStatus, null);
    }

    @Transactional
    public Student update(Long id, String name, String gender, String studentNo,
            String idCard, String electiveClass, Long classId, String studentStatus, Long expectedVersion) {
        Student s = studentRepository.findById(id).orElseThrow();
        ensureVersionMatch(s, expectedVersion);
        String normalizedName = normalizeStudentName(name);
        String normalizedStudentNo = normalizeStudentNo(studentNo);
        School effectiveSchool = resolveStudentSchool(s, classId);
        ensureStudentNoUniqueBySchool(effectiveSchool, normalizedStudentNo, s.getId());

        s.setName(normalizedName);
        s.setGender(gender);
        s.setStudentNo(normalizedStudentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        if (studentStatus != null) {
            s.setStudentStatus(normalizeStatusForSave(studentStatus));
        } else if (s.getStudentStatus() == null || s.getStudentStatus().isBlank()) {
            s.setStudentStatus("在籍");
        }
        if (classId != null) {
            SchoolClass sc = classRepository.findById(classId).orElseThrow();
            s.setSchoolClass(sc);
            if (s.getSchool() == null) {
                s.setSchool(sc.getSchool());
            }
        } else if (s.getSchool() == null) {
            s.setSchool(effectiveSchool);
        }
        return saveStudentWithDuplicateGuard(s);
    }

    @Transactional
    public void updateClass(Long studentId, Long newClassId) {
        Student s = studentRepository.findById(studentId).orElseThrow();
        SchoolClass sc = classRepository.findById(newClassId).orElseThrow();
        s.setSchoolClass(sc);
        studentRepository.save(s);
    }

    @Transactional
    public void updateElective(Long id, String electiveClass) {
        Student s = studentRepository.findById(id).orElseThrow();
        s.setElectiveClass((electiveClass == null || electiveClass.isBlank()) ? null : electiveClass);
        studentRepository.save(s);
    }

    @Transactional
    public void updateElectiveByStudentNo(String studentNo, String electiveClass) {
        Student s = studentRepository.findByStudentNo(studentNo)
                .orElseThrow(() -> new IllegalArgumentException("找不到学号：" + studentNo));
        s.setElectiveClass(electiveClass);
        studentRepository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        Student s = studentRepository.findById(id).orElseThrow();

        Map<Long, Integer> confirmedSelectionsPerCourse = new HashMap<>();
        for (CourseSelection selection : courseSelectionRepository.findByStudent(s)) {
            if (!"CONFIRMED".equals(selection.getStatus()) || selection.getCourse() == null || selection.getCourse().getId() == null) {
                continue;
            }
            confirmedSelectionsPerCourse.merge(selection.getCourse().getId(), 1, Integer::sum);
        }

        courseSelectionRepository.deleteByStudent(s);
        eventStudentRepository.deleteByStudent(s);
        attendanceRepository.deleteByStudent(s);
        termGradeRepository.deleteByStudent(s);
        physicalTestRepository.deleteByStudent(s);
        healthTestRecordRepository.deleteByStudent(s);
        examRecordRepository.deleteByStudent(s);

        SchoolClass studentClass = s.getSchoolClass();
        confirmedSelectionsPerCourse.forEach((courseId, count) ->
            courseRepository.findById(courseId).ifPresent(course -> {
                course.setCurrentCount(Math.max(0, course.getCurrentCount() - count));
                courseRepository.save(course);

                if ("PER_CLASS".equals(course.getCapacityMode()) && studentClass != null) {
                    courseClassCapacityRepository.findByCourseAndSchoolClass(course, studentClass).ifPresent(capacity -> {
                        capacity.setCurrentCount(Math.max(0, capacity.getCurrentCount() - count));
                        courseClassCapacityRepository.save(capacity);
                    });
                }
            })
        );

        studentRepository.delete(s);
    }

    @Transactional
    public void deleteAll() {
        courseSelectionRepository.deleteAll();
        eventStudentRepository.deleteAll();
        attendanceRepository.deleteAll();
        termGradeRepository.deleteAll();
        physicalTestRepository.deleteAll();
        healthTestRecordRepository.deleteAll();
        examRecordRepository.deleteAll();

        courseClassCapacityRepository.findAll().forEach(capacity -> {
            capacity.setCurrentCount(0);
            courseClassCapacityRepository.save(capacity);
        });
        courseRepository.findAll().forEach(course -> {
            course.setCurrentCount(0);
            courseRepository.save(course);
        });

        studentRepository.deleteAll();
    }

    @Transactional
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int deleted = 0;
        for (Long id : new LinkedHashSet<>(ids)) {
            if (id == null) continue;
            if (studentRepository.existsById(id)) {
                delete(id);
                deleted++;
            }
        }
        return deleted;
    }

    // 统计教师相关的学生数量
    public Long countByTeacher(Teacher teacher) {
        // 这里简化实现：获取教师所在学校的所有学生
        // 实际应用中应该根据教师管理的班级来统计
        if (teacher.getSchool() != null) {
            return studentRepository.countBySchool(teacher.getSchool());
        }
        return studentRepository.count();
    }

    public boolean isStudentNoAvailable(School school, String studentNo, Long excludeId) {
        if (school == null || studentNo == null || studentNo.isBlank()) {
            return false;
        }
        String normalized = studentNo.trim();
        Long effectiveExcludeId = excludeId == null ? -1L : excludeId;
        return !studentRepository.existsByStudentNoAndSchoolAndIdNot(
                normalized, school, effectiveExcludeId);
    }

    @Transactional
    public int batchUpdateStudentStatus(School school, List<Long> studentIds, String studentStatus) {
        if (studentIds == null || studentIds.isEmpty()) return 0;
        String normalizedStatus = normalizeStatusForSave(studentStatus);
        int updated = 0;
        for (Long studentId : new LinkedHashSet<>(studentIds)) {
            if (studentId == null) continue;
            Student student = findStudentForBatchUpdate(school, studentId);
            student.setStudentStatus(normalizedStatus);
            saveStudentWithDuplicateGuard(student);
            updated++;
        }
        return updated;
    }

    @Transactional
    public int batchUpdateElectiveClass(School school, List<Long> studentIds, String electiveClass) {
        if (studentIds == null || studentIds.isEmpty()) return 0;
        String normalizedElectiveClass = electiveClass == null || electiveClass.isBlank()
                ? null
                : electiveClass.trim();
        int updated = 0;
        for (Long studentId : new LinkedHashSet<>(studentIds)) {
            if (studentId == null) continue;
            Student student = findStudentForBatchUpdate(school, studentId);
            student.setElectiveClass(normalizedElectiveClass);
            saveStudentWithDuplicateGuard(student);
            updated++;
        }
        return updated;
    }

    private String normalizeStatusForSave(String status) {
        if (status == null || status.isBlank()) return "在籍";
        String normalized = status.trim();
        normalized = LEGACY_STATUS_ALIASES.getOrDefault(normalized, normalized);
        if (!AVAILABLE_STATUS_SET.contains(normalized)) {
            throw new IllegalArgumentException("学籍状态不合法");
        }
        return normalized;
    }

    private String normalizeStudentName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("学生姓名不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > STUDENT_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("学生姓名不能超过" + STUDENT_NAME_MAX_LENGTH + "个字符");
        }
        return normalized;
    }

    private String normalizeStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new IllegalArgumentException("学号不能为空");
        }
        String normalized = studentNo.trim();
        if (normalized.length() > STUDENT_NO_MAX_LENGTH) {
            throw new IllegalArgumentException("学号不能超过" + STUDENT_NO_MAX_LENGTH + "个字符");
        }
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("学号不能包含空格");
        }
        return normalized;
    }

    private String normalizeStatusForDisplay(String status) {
        if (status == null || status.isBlank()) return "在籍";
        String normalized = status.trim();
        return LEGACY_STATUS_ALIASES.getOrDefault(normalized, normalized);
    }

    private Student findStudentForBatchUpdate(School school, Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在: " + studentId));
        if (school != null && !Objects.equals(student.getSchool(), school)) {
            throw new IllegalArgumentException("无权批量修改其他学校学生");
        }
        return student;
    }

    private School resolveStudentSchool(Student student, Long classId) {
        if (classId != null) {
            SchoolClass sc = classRepository.findById(classId).orElseThrow();
            return sc.getSchool();
        }
        if (student.getSchool() != null) {
            return student.getSchool();
        }
        if (student.getSchoolClass() != null) {
            return student.getSchoolClass().getSchool();
        }
        return null;
    }

    private void ensureStudentNoUniqueBySchool(School school, String studentNo, Long currentId) {
        if (school == null || studentNo == null || studentNo.isBlank()) return;
        Long excludeId = currentId == null ? -1L : currentId;
        if (studentRepository.existsByStudentNoAndSchoolAndIdNot(studentNo, school, excludeId)) {
            throw new IllegalArgumentException("\u5b66\u53f7\u5df2\u5b58\u5728");
        }
    }

    private void ensureStudentNoUnique(Student current, String studentNo) {
        if (studentNo == null || studentNo.isBlank()) return;
        if (current.getSchool() == null) return;
        if (studentRepository.existsByStudentNoAndSchoolAndIdNot(studentNo, current.getSchool(), current.getId())) {
            throw new IllegalArgumentException("学号已存在");
        }
    }

    private void ensureVersionMatch(Student current, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        Long currentVersion = current.getVersion() == null ? -1L : current.getVersion();
        if (!Objects.equals(currentVersion, expectedVersion)) {
            throw new IllegalStateException("该学生已被其他设备修改，请刷新后重试");
        }
    }

    private Student saveStudentWithDuplicateGuard(Student student) {
        try {
            return studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("学号已存在", ex);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new IllegalStateException("该学生已被其他设备修改，请刷新后重试", ex);
        }
    }
}
