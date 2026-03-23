package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.EventStudentRepository;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceRegressionTest {

    @Mock
    private CourseRepository courseRepo;
    @Mock
    private CourseClassCapacityRepository capacityRepo;
    @Mock
    private CourseSelectionRepository selectionRepo;
    @Mock
    private EventStudentRepository eventStudentRepo;
    @Mock
    private SelectionEventRepository eventRepo;
    @Mock
    private StudentRepository studentRepo;

    @InjectMocks
    private CourseService courseService;

    @Test
    void submitPreferenceShouldRejectSameCourseInOtherPreference() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND1");

        Student student = new Student();
        student.setId(2L);

        Course course = new Course();
        course.setId(3L);
        course.setStatus("ACTIVE");

        CourseSelection existing = new CourseSelection();
        existing.setEvent(event);
        existing.setStudent(student);
        existing.setCourse(course);
        existing.setPreference(1);
        existing.setStatus("PENDING");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(courseRepo.findById(3L)).thenReturn(Optional.of(course));
        when(selectionRepo.findByEventAndStudent(event, student)).thenReturn(List.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> courseService.submitPreference(student, 1L, 3L, 2));

        assertNotNull(ex.getMessage());
        verify(selectionRepo, never()).save(any());
    }

    @Test
    void findEnrollmentsShouldReturnAllSelectionsForAdminView() {
        Course course = new Course();
        course.setId(10L);

        CourseSelection first = buildSelection(course, buildStudent(100L), "CONFIRMED");
        CourseSelection second = buildSelection(course, buildStudent(100L), "PENDING");

        when(selectionRepo.findByCourseOrderBySelectedAtAsc(course))
                .thenReturn(List.of(first, second));

        List<CourseSelection> result = courseService.findEnrollments(course);

        assertEquals(2, result.size());
        assertSame(first, result.get(0));
        assertSame(second, result.get(1));
    }

    @Test
    void findConfirmedUniqueEnrollmentsShouldOnlyKeepUniqueConfirmedStudents() {
        Course course = new Course();
        course.setId(10L);

        Student studentA = buildStudent(100L);
        Student studentB = buildStudent(200L);

        CourseSelection firstConfirmed = buildSelection(course, studentA, "CONFIRMED");
        firstConfirmed.setId(1L);
        CourseSelection duplicateConfirmed = buildSelection(course, studentA, "CONFIRMED");
        duplicateConfirmed.setId(2L);
        CourseSelection anotherConfirmed = buildSelection(course, studentB, "CONFIRMED");
        anotherConfirmed.setId(3L);

        when(selectionRepo.findByCourseAndStatusOrderBySelectedAtAsc(course, "CONFIRMED"))
                .thenReturn(List.of(firstConfirmed, duplicateConfirmed, anotherConfirmed));

        List<CourseSelection> result = courseService.findConfirmedUniqueEnrollments(course);

        assertEquals(2, result.size());
        assertSame(firstConfirmed, result.get(0));
        assertSame(anotherConfirmed, result.get(1));
    }

    private Student buildStudent(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }

    private CourseSelection buildSelection(Course course, Student student, String status) {
        CourseSelection selection = new CourseSelection();
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setStatus(status);
        return selection;
    }
}
