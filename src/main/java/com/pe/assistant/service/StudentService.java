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
public class StudentService {

    private final StudentRepository studentRepository;
    private final SchoolClassRepository classRepository;
    private final AttendanceRepository attendanceRepository;

    public List<Student> findByClassId(Long classId) {
        return studentRepository.findBySchoolClassId(classId);
    }

    public Page<Student> findWithFilters(School school, Long classId, Long gradeId, String name,
                                         String studentNo, String idCard, String electiveClass,
                                         int page, int size) {
        return studentRepository.findWithFilters(school, classId, gradeId, name, studentNo, idCard,
                electiveClass, PageRequest.of(page, size));
    }

    public Student findById(Long id) {
        return studentRepository.findById(id).orElseThrow();
    }

    public List<Student> findByElectiveClass(String electiveClass) {
        return studentRepository.findByElectiveClass(electiveClass);
    }

    public List<String> findElectiveClassNamesByTeacher(School school, Teacher teacher) {
        return studentRepository.findElectiveClassNamesByTeacher(school, teacher);
    }

    public List<String> findAllElectiveClassNames(School school) {
        return studentRepository.findAllElectiveClassNames(school);
    }

    @Transactional
    public Student create(String name, String gender, String studentNo, String idCard,
                          String electiveClass, Long classId, School school) {
        SchoolClass sc = classRepository.findById(classId).orElseThrow();
        Student s = new Student();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(studentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        s.setSchoolClass(sc);
        s.setSchool(school);
        return studentRepository.save(s);
    }

    @Transactional
    public Student update(Long id, String name, String gender, String studentNo,
                          String idCard, String electiveClass) {
        Student s = studentRepository.findById(id).orElseThrow();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(studentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        return studentRepository.save(s);
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
        studentRepository.deleteById(id);
    }

    @Transactional
    public void deleteAll() {
        attendanceRepository.deleteAll();
        studentRepository.deleteAll();
    }
}
