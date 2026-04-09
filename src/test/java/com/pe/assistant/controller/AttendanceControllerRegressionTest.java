package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Student;
import com.pe.assistant.service.AttendanceService;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerRegressionTest {

    @Mock
    private AttendanceService attendanceService;
    @Mock
    private ClassService classService;
    @Mock
    private StudentService studentService;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AttendanceController attendanceController;

    @Test
    void attendancePageShouldAlternateBandsByAdminClassForElectiveStudents() {
        School school = new School();
        school.setId(1L);

        SchoolClass electiveClass = new SchoolClass();
        electiveClass.setId(10L);
        electiveClass.setName("篮球");
        electiveClass.setType("选修课");

        Student studentB = buildStudent(2L, "李四", "20240002", "高一", "2班");
        Student studentA = buildStudent(1L, "张三", "20240001", "高一", "1班");
        Student studentC = buildStudent(3L, "王五", "20240003", "高一", "2班");
        List<Student> students = new ArrayList<>(List.of(studentB, studentA, studentC));

        when(currentUserService.getCurrentSchool()).thenReturn(school);
        when(classService.findById(10L)).thenReturn(electiveClass);
        when(studentService.findByElectiveClassForTeacher(school, "篮球")).thenReturn(students);
        when(attendanceService.findByElectiveClassAndDate(school, "篮球", LocalDate.of(2026, 4, 9))).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = attendanceController.attendancePage(10L, LocalDate.of(2026, 4, 9), model);

        assertEquals("teacher/attendance", view);
        @SuppressWarnings("unchecked")
        List<Student> renderedStudents = (List<Student>) model.getAttribute("students");
        assertIterableEquals(List.of(studentA, studentB, studentC), renderedStudents);
        @SuppressWarnings("unchecked")
        Map<Long, Integer> bands = (Map<Long, Integer>) model.getAttribute("studentClassBands");
        assertEquals(0, bands.get(studentA.getId()));
        assertEquals(1, bands.get(studentB.getId()));
        assertEquals(1, bands.get(studentC.getId()));
    }

    private Student buildStudent(Long id, String name, String studentNo, String gradeName, String className) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(className);
        com.pe.assistant.entity.Grade grade = new com.pe.assistant.entity.Grade();
        grade.setName(gradeName);
        schoolClass.setGrade(grade);

        Student student = new Student();
        student.setId(id);
        student.setName(name);
        student.setStudentNo(studentNo);
        student.setSchoolClass(schoolClass);
        return student;
    }
}
