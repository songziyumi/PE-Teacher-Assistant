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

    public Page<Student> findWithFilters(Long classId, Long gradeId, String name, String studentNo, String idCard, int page, int size) {
        return studentRepository.findWithFilters(classId, gradeId, name, studentNo, idCard, PageRequest.of(page, size));
    }

    public Student findById(Long id) {
        return studentRepository.findById(id).orElseThrow();
    }

    public List<Student> findByElectiveClass(String electiveClass) {
        return studentRepository.findByElectiveClass(electiveClass);
    }

    public List<String> findElectiveClassNamesByTeacher(Teacher teacher) {
        return studentRepository.findElectiveClassNamesByTeacher(teacher);
    }

    public List<String> findAllElectiveClassNames() {
        return studentRepository.findAllElectiveClassNames();
    }

    @Transactional
    public Student create(String name, String gender, String studentNo, String idCard,
                          String electiveClass, Long classId) {
        SchoolClass sc = classRepository.findById(classId).orElseThrow();
        Student s = new Student();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(studentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
        s.setSchoolClass(sc);
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
