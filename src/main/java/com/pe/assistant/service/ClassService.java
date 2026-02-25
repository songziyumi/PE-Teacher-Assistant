package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final SchoolClassRepository classRepository;
    private final GradeRepository gradeRepository;
    private final TeacherRepository teacherRepository;

    public List<SchoolClass> findAll() {
        return classRepository.findAll();
    }

    public List<SchoolClass> findByTeacher(Teacher teacher) {
        return classRepository.findByTeacher(teacher);
    }

    public SchoolClass findById(Long id) {
        return classRepository.findById(id).orElseThrow();
    }

    @Transactional
    public SchoolClass create(String name, Long gradeId) {
        Grade grade = gradeRepository.findById(gradeId).orElseThrow();
        SchoolClass sc = new SchoolClass();
        sc.setName(name);
        sc.setGrade(grade);
        sc.setType("行政班");
        return classRepository.save(sc);
    }

    @Transactional
    public SchoolClass createElective(String name, Long gradeId) {
        SchoolClass sc = new SchoolClass();
        sc.setName(name);
        sc.setType("选修课");
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
        classRepository.deleteById(id);
    }
}
