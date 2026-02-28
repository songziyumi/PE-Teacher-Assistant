package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        return studentRepository.findBySchoolClassIdOrderByStudentNo(classId);
    }

    public Page<Student> findWithFilters(School school, Long classId, Long gradeId, String name,
                                         String studentNo, String idCard, String electiveClass,
                                         int page, int size) {
        return studentRepository.findWithFilters(school, classId, gradeId, name, studentNo, idCard,
                electiveClass, PageRequest.of(page, size, Sort.by("studentNo")));
    }

    public Student findById(Long id) {
        return studentRepository.findById(id).orElseThrow();
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

    /**
     * 导入时：学号已存在则更新（含选修班），不存在则新建。
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
                s.setSchoolClass(sc);
                s.setSchool(school);
                studentRepository.save(s);
                return true;
            });
    }

    @Transactional
    public Student update(Long id, String name, String gender, String studentNo,
                          String idCard, String electiveClass, Long classId) {
        Student s = studentRepository.findById(id).orElseThrow();
        s.setName(name);
        s.setGender(gender);
        s.setStudentNo(studentNo);
        s.setIdCard(idCard);
        s.setElectiveClass(electiveClass);
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
        attendanceRepository.deleteAll(attendanceRepository.findByStudentOrderByDateDesc(s));
        studentRepository.delete(s);
    }

    @Transactional
    public void deleteAll() {
        attendanceRepository.deleteAll();
        studentRepository.deleteAll();
    }
}
