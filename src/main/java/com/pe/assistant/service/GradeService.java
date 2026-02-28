package com.pe.assistant.service;

import com.pe.assistant.entity.Grade;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.GradeRepository;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;
    private final SchoolClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

    public List<Grade> findAll(School school) {
        return gradeRepository.findBySchool(school);
    }

    public Grade findById(Long id) {
        return gradeRepository.findById(id).orElseThrow();
    }

    public Grade findByName(String name, School school) {
        return gradeRepository.findByNameAndSchool(name, school).orElse(null);
    }

    @Transactional
    public Grade create(String name, School school) {
        if (gradeRepository.existsByNameAndSchool(name, school)) throw new IllegalArgumentException("年级已存在");
        Grade g = new Grade();
        g.setName(name);
        g.setSchool(school);
        return gradeRepository.save(g);
    }

    @Transactional
    public Grade update(Long id, String name) {
        Grade g = gradeRepository.findById(id).orElseThrow();
        g.setName(name);
        return gradeRepository.save(g);
    }

    @Transactional
    public void delete(Long id) {
        List<SchoolClass> classes = classRepository.findByGradeId(id);
        for (SchoolClass sc : classes) {
            attendanceRepository.deleteAll(attendanceRepository.findByClassId(sc.getId()));
            studentRepository.deleteAll(studentRepository.findBySchoolClassIdOrderByStudentNo(sc.getId()));
        }
        classRepository.deleteAll(classes);
        gradeRepository.deleteById(id);
    }
}
