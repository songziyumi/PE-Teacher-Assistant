package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.GradeRepository;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassServiceRegressionTest {

    @Mock
    private SchoolClassRepository classRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private CourseClassCapacityRepository courseClassCapacityRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private StudentService studentService;

    @InjectMocks
    private ClassService classService;

    @Test
    void deleteAllShouldRemoveCourseCapacityReferencesBeforeDeletingClasses() {
        School school = new School();
        school.setId(1L);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(10L);
        schoolClass.setSchool(school);

        Course course = new Course();
        course.setId(100L);
        course.setTotalCapacity(30);
        course.setCurrentCount(5);

        CourseClassCapacity capacity = new CourseClassCapacity();
        capacity.setCourse(course);
        capacity.setSchoolClass(schoolClass);
        capacity.setMaxCapacity(12);
        capacity.setCurrentCount(2);

        when(classRepository.findBySchool(school)).thenReturn(List.of(schoolClass));
        when(courseClassCapacityRepository.findBySchoolClass(schoolClass)).thenReturn(List.of(capacity));

        classService.deleteAll(school);

        assertEquals(18, course.getTotalCapacity());
        assertEquals(3, course.getCurrentCount());
        verify(courseRepository).save(course);
        verify(courseClassCapacityRepository).delete(capacity);
        verify(studentService).deleteAll(school);
        verify(classRepository).deleteAll(List.of(schoolClass));
    }

    @Test
    void deleteShouldRemoveCourseCapacityReferencesBeforeDeletingSingleClass() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(10L);

        Course course = new Course();
        course.setId(100L);
        course.setTotalCapacity(20);
        course.setCurrentCount(4);

        CourseClassCapacity capacity = new CourseClassCapacity();
        capacity.setCourse(course);
        capacity.setSchoolClass(schoolClass);
        capacity.setMaxCapacity(8);
        capacity.setCurrentCount(1);

        when(classRepository.findById(10L)).thenReturn(Optional.of(schoolClass));
        when(courseClassCapacityRepository.findBySchoolClass(schoolClass)).thenReturn(List.of(capacity));
        when(attendanceRepository.findByClassId(10L)).thenReturn(List.of());
        when(studentRepository.findBySchoolClassIdOrderByStudentNo(10L)).thenReturn(List.of());

        classService.delete(10L);

        assertEquals(12, course.getTotalCapacity());
        assertEquals(3, course.getCurrentCount());
        verify(courseRepository).save(course);
        verify(courseClassCapacityRepository).delete(capacity);
        verify(classRepository).deleteById(10L);
    }
}
