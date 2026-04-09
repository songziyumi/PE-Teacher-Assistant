package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final List<String> AVAILABLE_STATUSES = List.of("在籍", "休学", "长假", "毕业", "在外借读", "借读");
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
    private final StudentAccountRepository studentAccountRepository;
    private final StudentReferenceCleanupService studentReferenceCleanupService;
    private final TeacherPermissionService teacherPermissionService;

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
        return requireStudent(id);
    }

    public Optional<Student> findByIdOptional(Long id) {
        return studentRepository.findById(id);
    }

    /**
     * Resolve a student login account deterministically when legacy data contains
     * duplicate student numbers.
     */
    public Optional<Student> resolveLoginStudent(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return Optional.empty();
        }
        List<Student> candidates = studentRepository.findAllByStudentNoOrderByIdDesc(studentNo.trim());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, StudentAccount> accountByStudentId = studentAccountRepository.findByStudentIn(candidates).stream()
                .filter(account -> account.getStudent() != null && account.getStudent().getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        account -> account.getStudent().getId(),
                        account -> account,
                        (left, right) -> left,
                        HashMap::new));
        return candidates.stream()
                .filter(s -> hasEnabledAccount(accountByStudentId.get(s.getId())))
                .filter(s -> hasUsableAccountPassword(accountByStudentId.get(s.getId())))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(s -> hasEnabledAccount(accountByStudentId.get(s.getId())))
                        .findFirst())
                .or(() -> candidates.stream()
                        .filter(s -> hasUsableAccountPassword(accountByStudentId.get(s.getId())))
                        .findFirst())
                .or(() -> Optional.of(candidates.get(0)));
    }

    public Optional<Student> resolveStudentPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return Optional.empty();
        }
        if (principal.startsWith("student:")) {
            try {
                Long id = Long.parseLong(principal.substring("student:".length()));
                return findByIdOptional(id);
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return resolveLoginStudent(principal);
    }

    public List<Student> findByElectiveClass(String electiveClass) {
        return studentRepository.findByElectiveClassOrderByStudentNo(electiveClass);
    }

    public List<Student> findByElectiveClass(School school, String electiveClass) {
        if (school == null) {
            return findByElectiveClass(electiveClass);
        }
        return studentRepository.findBySchoolAndElectiveClassOrderByStudentNo(school, electiveClass);
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
        return filterVisibleForTeacher(school, findByElectiveClass(school, electiveClass));
    }

    /** 学生名单 xlsx 导出 */
    public byte[] exportStudentsXlsx(List<Student> students) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("学生名单");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] cols = {"学号", "姓名", "性别", "年级", "班级", "选修班", "学籍状态"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            for (Student s : students) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(s.getStudentNo() != null ? s.getStudentNo() : "");
                row.createCell(1).setCellValue(s.getName());
                row.createCell(2).setCellValue(s.getGender() != null ? s.getGender() : "");
                SchoolClass sc = s.getSchoolClass();
                row.createCell(3).setCellValue(sc != null && sc.getGrade() != null ? sc.getGrade().getName() : "");
                row.createCell(4).setCellValue(sc != null ? sc.getName() : "");
                row.createCell(5).setCellValue(s.getElectiveClass() != null ? s.getElectiveClass() : "");
                row.createCell(6).setCellValue(s.getStudentStatus() != null ? s.getStudentStatus() : "");
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        }
    }

    public List<Student> filterVisibleForTeacher(School school, List<Student> students) {
        if (students == null || students.isEmpty()) return students;
        TeacherPermission permission = school != null ? teacherPermissionService.getOrCreate(school) : null;
        boolean showSuspended = permission == null || permission.isShowSuspendedOnTeacherPage();
        boolean showOutgoingBorrow = permission == null || permission.isShowOutgoingBorrowOnTeacherPage();
        boolean showLongLeave = permission == null || permission.isShowLongLeaveOnTeacherPage();
        List<Student> result = new ArrayList<>();
        for (Student student : students) {
            String status = normalizeStatusForDisplay(student.getStudentStatus());
            if (!showSuspended && "\u4f11\u5b66".equals(status)) continue;
            if (!showOutgoingBorrow && "\u5728\u5916\u501f\u8bfb".equals(status)) continue;
            if (!showLongLeave && "\u957f\u5047".equals(status)) continue;
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
            String electiveClass, Long classId, School school, String studentStatus) {
        SchoolClass sc = classRepository.findById(classId).orElseThrow();
        School effectiveSchool = school != null ? school : sc.getSchool();
        String normalizedStudentNo = studentNo == null ? "" : studentNo.trim();
        if (normalizedStudentNo.isBlank()) {
            throw new IllegalArgumentException("学号不能为空");
        }
        String normalizedStatus = normalizeStatusForSave(studentStatus);
        Optional<Student> existing = studentRepository.findByStudentNoAndSchool(normalizedStudentNo, effectiveSchool);
        if (existing.isEmpty()) {
            existing = resolveLoginStudent(normalizedStudentNo)
                    .filter(s -> s.getSchool() == null
                            || (effectiveSchool != null && Objects.equals(s.getSchool().getId(), effectiveSchool.getId())));
        }
        if (existing.isPresent()) {
            Student s = existing.get();
            s.setName(name);
            s.setGender(gender);
            s.setStudentNo(normalizedStudentNo);
            s.setIdCard(idCard);
            s.setElectiveClass(electiveClass);
            s.setStudentStatus(normalizedStatus);
            s.setSchoolClass(sc);
            if (s.getSchool() == null && effectiveSchool != null) {
                s.setSchool(effectiveSchool);
            }
            studentRepository.save(s);
            return false;
        }
        Student s = new Student();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(normalizedStudentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        s.setStudentStatus(normalizedStatus);
        s.setSchoolClass(sc);
        s.setSchool(effectiveSchool);
        studentRepository.save(s);
        return true;
    }
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
        Student s = requireStudent(id);
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
            SchoolClass sc = requireClass(classId);
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
        Student s = resolveLoginStudent(studentNo)
                .orElseThrow(() -> new IllegalArgumentException("找不到学号：" + studentNo));
        s.setElectiveClass(electiveClass);
        studentRepository.save(s);
    }

    @Transactional
    public void assignElectiveClassFromCourse(Student student, Course course) {
        if (student == null || student.getId() == null) {
            return;
        }
        applyElectiveClass(student, buildElectiveClassName(course));
    }

    @Transactional
    public void refreshElectiveClassFromConfirmedSelections(Student student) {
        if (student == null || student.getId() == null) {
            return;
        }
        applyElectiveClass(student, resolveLatestConfirmedElectiveClass(student));
    }

    @Transactional
    public int syncElectiveClassesForEvent(SelectionEvent event) {
        if (event == null) {
            return 0;
        }

        Map<Long, Student> targetStudents = new HashMap<>();
        if (eventStudentRepository.existsByEvent(event)) {
            for (Student student : eventStudentRepository.findStudentsByEvent(event)) {
                if (student != null && student.getId() != null) {
                    targetStudents.put(student.getId(), student);
                }
            }
        }
        for (CourseSelection selection : courseSelectionRepository.findByEvent(event)) {
            Student student = selection.getStudent();
            if (student != null && student.getId() != null) {
                targetStudents.put(student.getId(), student);
            }
        }

        int updated = 0;
        for (Student student : targetStudents.values()) {
            String electiveClass = courseSelectionRepository.findByEventAndStudentAndStatus(event, student, "CONFIRMED")
                    .map(CourseSelection::getCourse)
                    .map(this::buildElectiveClassName)
                    .orElseGet(() -> resolveLatestConfirmedElectiveClass(student));
            if (applyElectiveClass(student, electiveClass)) {
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public int syncElectiveClassesForStudents(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (Student student : students) {
            if (student == null || student.getId() == null) {
                continue;
            }
            String electiveClass = resolveLatestConfirmedElectiveClass(student);
            if (electiveClass == null || electiveClass.isBlank()) {
                continue;
            }
            if (applyElectiveClass(student, electiveClass)) {
                updated++;
            }
        }
        return updated;
    }

    private boolean applyElectiveClass(Student student, String electiveClass) {
        if (Objects.equals(student.getElectiveClass(), electiveClass)) {
            return false;
        }
        student.setElectiveClass(electiveClass);
        studentRepository.save(student);
        return true;
    }

    private String resolveLatestConfirmedElectiveClass(Student student) {
        CourseSelection latestConfirmed = null;
        for (CourseSelection selection : courseSelectionRepository.findByStudent(student)) {
            if (!"CONFIRMED".equals(selection.getStatus()) || selection.getCourse() == null) {
                continue;
            }
            if (latestConfirmed == null || isMoreRecentConfirmed(selection, latestConfirmed)) {
                latestConfirmed = selection;
            }
        }
        return latestConfirmed == null ? null : buildElectiveClassName(latestConfirmed.getCourse());
    }

    private boolean isMoreRecentConfirmed(CourseSelection candidate, CourseSelection current) {
        LocalDateTime candidateTime = candidate.getConfirmedAt() != null ? candidate.getConfirmedAt() : candidate.getSelectedAt();
        LocalDateTime currentTime = current.getConfirmedAt() != null ? current.getConfirmedAt() : current.getSelectedAt();
        if (candidateTime == null && currentTime == null) {
            return Objects.requireNonNullElse(candidate.getId(), Long.MIN_VALUE)
                    > Objects.requireNonNullElse(current.getId(), Long.MIN_VALUE);
        }
        if (candidateTime == null) {
            return false;
        }
        if (currentTime == null) {
            return true;
        }
        if (!candidateTime.equals(currentTime)) {
            return candidateTime.isAfter(currentTime);
        }
        return Objects.requireNonNullElse(candidate.getId(), Long.MIN_VALUE)
                > Objects.requireNonNullElse(current.getId(), Long.MIN_VALUE);
    }

    private String buildElectiveClassName(Course course) {
        if (course == null || course.getName() == null || course.getName().isBlank()) {
            return null;
        }
        return course.getName().trim();
    }

    private boolean hasEnabledAccount(StudentAccount account) {
        return account != null && Boolean.TRUE.equals(account.getEnabled());
    }

    private boolean hasUsableAccountPassword(StudentAccount account) {
        return account != null
                && account.getPasswordHash() != null
                && !account.getPasswordHash().isBlank();
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
        studentReferenceCleanupService.deleteByStudentId(s.getId());

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
    public void deleteAll(School school) {
        courseSelectionRepository.deleteAllBySchool(school);
        eventStudentRepository.deleteAllBySchool(school);
        attendanceRepository.deleteAllBySchool(school);
        termGradeRepository.deleteAllBySchool(school);
        physicalTestRepository.deleteAllBySchool(school);
        healthTestRecordRepository.deleteAllBySchool(school);
        examRecordRepository.deleteAllBySchool(school);
        studentReferenceCleanupService.deleteAllBySchoolId(school.getId());

        courseClassCapacityRepository.resetCountsBySchool(school);
        courseRepository.resetCountsBySchool(school);

        studentRepository.deleteAllBySchool(school);
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

    public static final class BatchStudentFailure {
        private final Long studentId;
        private final String reason;

        public BatchStudentFailure(Long studentId, String reason) {
            this.studentId = studentId;
            this.reason = reason;
        }

        public Long getStudentId() {
            return studentId;
        }

        public String getReason() {
            return reason;
        }
    }

    public static final class BatchStudentOperationResult {
        private final List<Long> studentIds;
        private final List<BatchStudentFailure> failedItems = new ArrayList<>();
        private int successCount;

        public BatchStudentOperationResult(List<Long> studentIds) {
            this.studentIds = studentIds == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(studentIds)));
        }

        public void addSuccess() {
            successCount++;
        }

        public void addFailure(Long studentId, String reason) {
            failedItems.add(new BatchStudentFailure(studentId, reason));
        }

        public List<Long> getStudentIds() {
            return studentIds;
        }

        public int getTotalCount() {
            return studentIds.size();
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailedCount() {
            return failedItems.size();
        }

        public List<BatchStudentFailure> getFailedItems() {
            return Collections.unmodifiableList(failedItems);
        }
    }

    @Transactional
    public BatchStudentOperationResult batchUpdateStudentStatus(School school, List<Long> studentIds, String studentStatus) {
        BatchStudentOperationResult result = new BatchStudentOperationResult(studentIds);
        if (result.getStudentIds().isEmpty()) {
            return result;
        }
        String normalizedStatus = normalizeStatusForSave(studentStatus);
        for (Long studentId : result.getStudentIds()) {
            if (studentId == null) {
                result.addFailure(null, "\u5b66\u751fID\u4e0d\u80fd\u4e3a\u7a7a");
                continue;
            }
            try {
                Student student = findStudentForBatchUpdate(school, studentId);
                student.setStudentStatus(normalizedStatus);
                saveStudentWithDuplicateGuard(student);
                result.addSuccess();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                result.addFailure(studentId, ex.getMessage());
            }
        }
        return result;
    }

    @Transactional
    public BatchStudentOperationResult batchUpdateElectiveClass(School school, List<Long> studentIds, String electiveClass) {
        BatchStudentOperationResult result = new BatchStudentOperationResult(studentIds);
        if (result.getStudentIds().isEmpty()) {
            return result;
        }
        String normalizedElectiveClass = electiveClass == null || electiveClass.isBlank()
                ? null
                : electiveClass.trim();
        for (Long studentId : result.getStudentIds()) {
            if (studentId == null) {
                result.addFailure(null, "\u5b66\u751fID\u4e0d\u80fd\u4e3a\u7a7a");
                continue;
            }
            try {
                Student student = findStudentForBatchUpdate(school, studentId);
                student.setElectiveClass(normalizedElectiveClass);
                saveStudentWithDuplicateGuard(student);
                result.addSuccess();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                result.addFailure(studentId, ex.getMessage());
            }
        }
        return result;
    }

    @Transactional
    public BatchStudentOperationResult batchDelete(School school, List<Long> studentIds) {
        BatchStudentOperationResult result = new BatchStudentOperationResult(studentIds);
        for (Long studentId : result.getStudentIds()) {
            if (studentId == null) {
                result.addFailure(null, "学生ID不能为空");
                continue;
            }
            try {
                Student student = findStudentForBatchUpdate(school, studentId);
                delete(student.getId());
                result.addSuccess();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                result.addFailure(studentId, ex.getMessage());
            }
        }
        return result;
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
            SchoolClass sc = requireClass(classId);
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

    private Student requireStudent(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("\u5b66\u751f\u4e0d\u5b58\u5728"));
    }

    private SchoolClass requireClass(Long classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("\u73ed\u7ea7\u4e0d\u5b58\u5728"));
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
