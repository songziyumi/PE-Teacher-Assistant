package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.service.ClassService;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.GradeService;
import com.pe.assistant.service.StudentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentControllerRegressionTest {

    @Mock
    private StudentService studentService;
    @Mock
    private ClassService classService;
    @Mock
    private GradeService gradeService;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private StudentController studentController;

    @Test
    void listStudentsShouldExposeLongLeaveStatusForTeacherEdit() {
        School school = new School();
        school.setId(1L);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        schoolClass.setName("高一1班");
        schoolClass.setType("行政班");
        schoolClass.setSchool(school);

        when(currentUserService.getCurrentSchool()).thenReturn(school);
        when(classService.findById(1L)).thenReturn(schoolClass);
        when(studentService.findByClassIdForTeacher(school, 1L)).thenReturn(List.of());
        when(classService.findAll(school)).thenReturn(List.of(schoolClass));
        when(gradeService.findAll(school)).thenReturn(List.of());
        when(studentService.getAvailableStatuses()).thenReturn(List.of("在籍", "休学", "长假", "毕业", "在外借读", "借读"));

        Model model = new ExtendedModelMap();
        String view = studentController.listStudents(1L, model);

        assertEquals("teacher/students", view);
        @SuppressWarnings("unchecked")
        List<String> statuses = (List<String>) model.getAttribute("studentStatuses");
        assertTrue(statuses.contains("长假"));
    }
}
