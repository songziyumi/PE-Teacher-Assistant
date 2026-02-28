package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final SchoolClassRepository classRepository;
    private final GradeRepository gradeRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

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
    public void delete(Long id) {
        attendanceRepository.deleteAll(attendanceRepository.findByClassId(id));
        studentRepository.deleteAll(studentRepository.findBySchoolClassIdOrderByStudentNo(id));
        classRepository.deleteById(id);
    }

    @Transactional
    public void deleteAll() {
        attendanceRepository.deleteAll();
        studentRepository.deleteAll();
        classRepository.deleteAll();
    }
}
