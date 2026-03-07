package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final List<String> AVAILABLE_STATUSES = List.of("在籍", "休学", "毕业", "外出借读", "外校借读");
    private static final Set<String> AVAILABLE_STATUS_SET = Set.copyOf(AVAILABLE_STATUSES);

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
            if (!showOutgoingBorrow && "外出借读".equals(status)) continue;
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
        Student s = new Student();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(studentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        s.setStudentStatus(normalizeStatusForSave(studentStatus));
        s.setSchoolClass(sc);
        s.setSchool(school);
        return studentRepository.save(s);
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
        return update(id, name, gender, studentNo, idCard, electiveClass, classId, null);
    }

    @Transactional
    public Student update(Long id, String name, String gender, String studentNo,
            String idCard, String electiveClass, Long classId, String studentStatus) {
        Student s = studentRepository.findById(id).orElseThrow();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(studentNo);
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
        }
        return studentRepository.save(s);
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

    private String normalizeStatusForSave(String status) {
        if (status == null || status.isBlank()) return "在籍";
        String normalized = status.trim();
        if (!AVAILABLE_STATUS_SET.contains(normalized)) {
            throw new IllegalArgumentException("学籍状态不合法");
        }
        return normalized;
    }

    private String normalizeStatusForDisplay(String status) {
        return (status == null || status.isBlank()) ? "在籍" : status.trim();
    }
}
