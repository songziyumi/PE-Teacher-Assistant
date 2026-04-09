package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.School;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceDropCourseTest {

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
    @Mock
    private StudentNotificationService studentNotificationService;
    @Mock
    private StudentService studentService;

    @InjectMocks
    private CourseService courseService;

    @Test
    void shouldAllowDroppingConfirmedFirstRoundSelectionDuringRound2() {
        Student student = buildStudent(1L);
        Course course = buildCourse(11L, 2);
        SelectionEvent event = buildRound2Event();
        CourseSelection selection = buildSelection(101L, student, course, event, 1);

        when(selectionRepo.findById(selection.getId())).thenReturn(Optional.of(selection));
        doAnswer(invocation -> invocation.getArgument(0)).when(selectionRepo).save(any(CourseSelection.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(courseRepo).save(any(Course.class));

        courseService.dropCourse(student, selection.getId());

        assertEquals("CANCELLED", selection.getStatus());
        assertEquals(1, course.getCurrentCount());
        verify(studentNotificationService).notifyDropSuccess(student, course, event);
        verify(studentService).refreshElectiveClassFromConfirmedSelections(student);
    }

    @Test
    void shouldRejectDroppingRound2Selection() {
        Student student = buildStudent(2L);
        Course course = buildCourse(12L, 1);
        SelectionEvent event = buildRound2Event();
        CourseSelection selection = buildSelection(102L, student, course, event, 0);
        selection.setRound(2);

        when(selectionRepo.findById(selection.getId())).thenReturn(Optional.of(selection));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> courseService.dropCourse(student, selection.getId()));

        assertEquals("\u5f53\u524d\u4ec5\u652f\u6301\u7b2c\u4e00\u8f6e\u5df2\u786e\u8ba4\u8bfe\u7a0b\u5728\u7b2c\u4e8c\u8f6e\u671f\u95f4\u9000\u8bfe", exception.getMessage());
        verify(selectionRepo, never()).save(any(CourseSelection.class));
        verify(courseRepo, never()).save(any(Course.class));
        verify(studentNotificationService, never()).notifyDropSuccess(any(Student.class), any(Course.class), any(SelectionEvent.class));
    }

    private Student buildStudent(Long id) {
        Student student = new Student();
        student.setId(id);
        student.setSchool(new School());
        return student;
    }

    private Course buildCourse(Long id, int currentCount) {
        Course course = new Course();
        course.setId(id);
        course.setSchool(new School());
        course.setCapacityMode("GLOBAL");
        course.setTotalCapacity(10);
        course.setCurrentCount(currentCount);
        return course;
    }

    private SelectionEvent buildRound2Event() {
        SelectionEvent event = new SelectionEvent();
        event.setId(20L);
        event.setStatus("ROUND2");
        event.setRound2Start(LocalDateTime.now().minusHours(1));
        event.setRound2End(LocalDateTime.now().plusHours(1));
        return event;
    }

    private CourseSelection buildSelection(Long id,
                                           Student student,
                                           Course course,
                                           SelectionEvent event,
                                           int preference) {
        CourseSelection selection = new CourseSelection();
        selection.setId(id);
        selection.setStudent(student);
        selection.setCourse(course);
        selection.setEvent(event);
        selection.setRound(1);
        selection.setPreference(preference);
        selection.setStatus("CONFIRMED");
        selection.setSelectedAt(LocalDateTime.now().minusDays(1));
        selection.setConfirmedAt(LocalDateTime.now().minusHours(2));
        return selection;
    }
}
