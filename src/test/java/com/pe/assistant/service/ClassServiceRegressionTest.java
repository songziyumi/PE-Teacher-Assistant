package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.GradeRepository;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void syncElectiveClassesFromEventShouldCreateMissingElectiveClassAndAssignTeacher() {
        School school = new School();
        school.setId(1L);

        SelectionEvent event = new SelectionEvent();
        event.setId(2L);
        event.setSchool(school);

        Teacher teacher = new Teacher();
        teacher.setId(9L);

        Course course = new Course();
        course.setId(20L);
        course.setName("篮球");
        course.setTeacher(teacher);

        when(classRepository.findBySchool(school)).thenReturn(List.of());
        when(courseRepository.findByEventOrderByNameAsc(event)).thenReturn(List.of(course));
        when(classRepository.save(org.mockito.ArgumentMatchers.any(SchoolClass.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int updated = classService.syncElectiveClassesFromEvent(event);

        assertEquals(1, updated);
        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(classRepository).save(captor.capture());
        SchoolClass saved = captor.getValue();
        assertEquals("篮球", saved.getName());
        assertEquals("\u9009\u4fee\u8bfe", saved.getType());
        assertSame(school, saved.getSchool());
        assertSame(teacher, saved.getTeacher());
    }

    @Test
    void syncElectiveClassesFromEventShouldReuseExistingElectiveClassAndRefreshTeacher() {
        School school = new School();
        school.setId(1L);

        SelectionEvent event = new SelectionEvent();
        event.setId(2L);
        event.setSchool(school);

        Teacher oldTeacher = new Teacher();
        oldTeacher.setId(3L);
        Teacher newTeacher = new Teacher();
        newTeacher.setId(4L);

        SchoolClass existing = new SchoolClass();
        existing.setId(30L);
        existing.setName("足球");
        existing.setType("\u9009\u4fee\u8bfe");
        existing.setSchool(school);
        existing.setTeacher(oldTeacher);

        Course course = new Course();
        course.setId(21L);
        course.setName("足球");
        course.setTeacher(newTeacher);

        when(classRepository.findBySchool(school)).thenReturn(List.of(existing));
        when(courseRepository.findByEventOrderByNameAsc(event)).thenReturn(List.of(course));

        int updated = classService.syncElectiveClassesFromEvent(event);

        assertEquals(1, updated);
        assertSame(newTeacher, existing.getTeacher());
        verify(classRepository).save(existing);
    }
}
