package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClassService {

    private static final String ELECTIVE_CLASS_TYPE = "\u9009\u4fee\u8bfe";

    private final SchoolClassRepository classRepository;
    private final GradeRepository gradeRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final CourseClassCapacityRepository courseClassCapacityRepository;
    private final CourseRepository courseRepository;
    private final StudentService studentService;
    private final ElectiveClassResolver electiveClassResolver;

    public List<SchoolClass> findAll(School school) {
        return classRepository.findBySchool(school);
    }

    public Page<SchoolClass> findByKeyword(School school, String keyword, int page, int size) {
        return classRepository.findByKeyword(school, keyword, PageRequest.of(page, size));
    }

    public Page<SchoolClass> findByFilters(School school, String type, Long gradeId, String name, int page, int size) {
        return classRepository.findByFilters(school, type, gradeId, name, PageRequest.of(page, size));
    }

    public List<SchoolClass> findByTeacher(Teacher teacher) {
        return classRepository.findByTeacher(teacher);
    }

    public List<SchoolClass> findAdminClassesByTeacher(Teacher teacher) {
        return classRepository.findByTeacherAndType(teacher, "行政班");
    }

    public List<SchoolClass> findElectiveClassesByTeacher(Teacher teacher) {
        return classRepository.findByTeacherAndType(teacher, "选修课");
    }

    public SchoolClass findById(Long id) {
        return classRepository.findById(id).orElseThrow();
    }

    public boolean existsByNameAndGrade(String name, Long gradeId, School school) {
        return classRepository.existsByNameAndGradeIdAndSchool(name, gradeId, school);
    }

    public boolean existsByNameAndType(String name, String type, School school) {
        return classRepository.existsByNameAndTypeAndSchool(name, type, school);
    }

    @Transactional
    public SchoolClass create(String name, Long gradeId, School school) {
        Grade grade = gradeRepository.findById(gradeId).orElseThrow();
        SchoolClass sc = new SchoolClass();
        sc.setName(name);
        sc.setGrade(grade);
        sc.setType("行政班");
        sc.setSchool(school);
        return classRepository.save(sc);
    }

    @Transactional
    public SchoolClass createElective(String name, Long gradeId, School school) {
        SchoolClass sc = new SchoolClass();
        sc.setName(name);
        sc.setType("选修课");
        sc.setSchool(school);
        if (gradeId != null) sc.setGrade(gradeRepository.findById(gradeId).orElse(null));
        return classRepository.save(sc);
    }

    @Transactional
    public void assignTeacher(Long classId, Long teacherId) {
        SchoolClass sc = classRepository.findById(classId).orElseThrow();
        Teacher teacher = teacherRepository.findById(teacherId).orElseThrow();
        sc.setTeacher(teacher);
        classRepository.save(sc);
    }

    @Transactional
    public int syncElectiveClassesFromEvent(SelectionEvent event) {
        if (event == null || event.getSchool() == null) {
            return 0;
        }

        List<SchoolClass> electiveClasses = new ArrayList<>();
        for (SchoolClass schoolClass : classRepository.findBySchool(event.getSchool())) {
            if (!isElectiveType(schoolClass.getType())) {
                continue;
            }
            electiveClasses.add(schoolClass);
        }
        electiveClasses.sort((left, right) -> {
            String leftName = normalizeName(left.getName());
            String rightName = normalizeName(right.getName());
            if (leftName == null && rightName != null) {
                return 1;
            }
            if (leftName != null && rightName == null) {
                return -1;
            }
            if (leftName != null && rightName != null) {
                int nameCompare = leftName.compareTo(rightName);
                if (nameCompare != 0) {
                    return nameCompare;
                }
            }
            Long leftGradeId = left.getGrade() != null && left.getGrade().getId() != null
                    ? left.getGrade().getId() : Long.MIN_VALUE;
            Long rightGradeId = right.getGrade() != null && right.getGrade().getId() != null
                    ? right.getGrade().getId() : Long.MIN_VALUE;
            return leftGradeId.compareTo(rightGradeId);
        });

        int updated = 0;
        for (Course course : courseRepository.findByEventOrderByNameAsc(event)) {
            ElectiveClassResolver.ElectiveClassResolution resolution = electiveClassResolver.resolve(course);
            String electiveClassName = resolution.baseName();
            Teacher teacher = course != null ? course.getTeacher() : null;
            if (electiveClassName == null || teacher == null || teacher.getId() == null) {
                continue;
            }

            SchoolClass schoolClass = findMatchingElectiveClass(electiveClasses, resolution, teacher);
            if (schoolClass == null) {
                SchoolClass created = new SchoolClass();
                created.setName(electiveClassName);
                created.setType(ELECTIVE_CLASS_TYPE);
                created.setSchool(event.getSchool());
                created.setTeacher(teacher);
                if (resolution.hasSingleGrade()) {
                    created.setGrade(resolution.grade());
                }
                classRepository.save(created);
                electiveClasses.add(created);
                updated++;
                continue;
            }

            boolean changed = false;
            if (!sameTeacher(schoolClass.getTeacher(), teacher)) {
                schoolClass.setTeacher(teacher);
                changed = true;
            }
            if (!Objects.equals(normalizeName(schoolClass.getName()), electiveClassName)) {
                schoolClass.setName(electiveClassName);
                changed = true;
            }
            if (!isElectiveType(schoolClass.getType())) {
                schoolClass.setType(ELECTIVE_CLASS_TYPE);
                changed = true;
            }
            if (resolution.gradeResolved() && !sameGrade(schoolClass.getGrade(), resolution.grade())) {
                schoolClass.setGrade(resolution.grade());
                changed = true;
            }
            if (changed) {
                classRepository.save(schoolClass);
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public void delete(Long id) {
        SchoolClass schoolClass = classRepository.findById(id).orElseThrow();
        cleanupClassCapacityReferences(schoolClass);
        attendanceRepository.deleteAll(attendanceRepository.findByClassId(id));
        studentRepository.deleteAll(studentRepository.findBySchoolClassIdOrderByStudentNo(id));
        classRepository.deleteById(id);
    }

    @Transactional
    public void deleteAll(School school) {
        List<SchoolClass> classes = classRepository.findBySchool(school);
        classes.forEach(this::cleanupClassCapacityReferences);
        studentService.deleteAll(school);
        classRepository.deleteAll(classes);
    }

    private void cleanupClassCapacityReferences(SchoolClass schoolClass) {
        for (CourseClassCapacity capacity : courseClassCapacityRepository.findBySchoolClass(schoolClass)) {
            Course course = capacity.getCourse();
            if (course != null) {
                course.setTotalCapacity(Math.max(0, course.getTotalCapacity() - capacity.getMaxCapacity()));
                course.setCurrentCount(Math.max(0, course.getCurrentCount() - capacity.getCurrentCount()));
                courseRepository.save(course);
            }
            courseClassCapacityRepository.delete(capacity);
        }
    }

    private boolean sameTeacher(Teacher current, Teacher target) {
        if (current == target) {
            return true;
        }
        if (current == null || target == null) {
            return false;
        }
        return Objects.equals(current.getId(), target.getId());
    }

    private boolean sameGrade(Grade current, Grade target) {
        if (current == target) {
            return true;
        }
        if (current == null || target == null) {
            return current == null && target == null;
        }
        return Objects.equals(current.getId(), target.getId());
    }

    private SchoolClass findMatchingElectiveClass(List<SchoolClass> electiveClasses,
                                                  ElectiveClassResolver.ElectiveClassResolution resolution,
                                                  Teacher teacher) {
        List<SchoolClass> sameNameCandidates = new ArrayList<>();
        for (SchoolClass schoolClass : electiveClasses) {
            if (Objects.equals(normalizeName(schoolClass.getName()), resolution.baseName())) {
                sameNameCandidates.add(schoolClass);
            }
        }
        if (sameNameCandidates.isEmpty()) {
            return null;
        }

        if (resolution.hasSingleGrade()) {
            SchoolClass exactGradeMatch = pickCandidate(sameNameCandidates, teacher, resolution.grade(), false);
            if (exactGradeMatch != null) {
                return exactGradeMatch;
            }
            SchoolClass emptyGradeMatch = pickCandidate(sameNameCandidates, teacher, null, true);
            if (emptyGradeMatch != null) {
                return emptyGradeMatch;
            }
        } else if (resolution.crossGrade()) {
            SchoolClass emptyGradeMatch = pickCandidate(sameNameCandidates, teacher, null, true);
            if (emptyGradeMatch != null) {
                return emptyGradeMatch;
            }
        }

        SchoolClass teacherMatch = pickCandidate(sameNameCandidates, teacher, null, false);
        if (teacherMatch != null) {
            return teacherMatch;
        }
        if (sameNameCandidates.size() == 1) {
            return sameNameCandidates.get(0);
        }

        if (resolution.hasSingleGrade()) {
            for (SchoolClass candidate : sameNameCandidates) {
                if (sameGrade(candidate.getGrade(), resolution.grade())) {
                    return candidate;
                }
            }
        }
        for (SchoolClass candidate : sameNameCandidates) {
            if (candidate.getGrade() == null) {
                return candidate;
            }
        }
        return sameNameCandidates.get(0);
    }

    private SchoolClass pickCandidate(List<SchoolClass> candidates, Teacher teacher, Grade grade, boolean requireGradeMatchOnly) {
        for (SchoolClass candidate : candidates) {
            if (!sameTeacher(candidate.getTeacher(), teacher)) {
                continue;
            }
            if (requireGradeMatchOnly) {
                if (grade == null && candidate.getGrade() == null) {
                    return candidate;
                }
                if (grade != null && sameGrade(candidate.getGrade(), grade)) {
                    return candidate;
                }
                continue;
            }
            if (grade == null || sameGrade(candidate.getGrade(), grade)) {
                return candidate;
            }
        }
        if (requireGradeMatchOnly) {
            for (SchoolClass candidate : candidates) {
                if (grade == null && candidate.getGrade() == null) {
                    return candidate;
                }
                if (grade != null && sameGrade(candidate.getGrade(), grade)) {
                    return candidate;
                }
            }
            return null;
        }
        return null;
    }

    private boolean isElectiveType(String type) {
        if (type == null) {
            return false;
        }
        String value = type.trim();
        return ELECTIVE_CLASS_TYPE.equals(value)
                || value.contains("\u9009\u4fee")
                || value.contains("elective");
    }

    private String storedElectiveClassName(SchoolClass schoolClass) {
        return electiveClassResolver.buildStoredName(schoolClass);
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
