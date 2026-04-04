package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.SchoolClass;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock
    private StudentNotificationService studentNotificationService;
    @Mock
    private StudentService studentService;

    @InjectMocks
    private CourseService courseService;

    @Test
    void submitPreferenceShouldMoveCourseFromSecondToFirstAndClearSecond() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND1");

        Student student = new Student();
        student.setId(2L);

        Course firstCourse = new Course();
        firstCourse.setId(3L);
        firstCourse.setStatus("ACTIVE");

        Course secondCourse = new Course();
        secondCourse.setId(4L);
        secondCourse.setStatus("ACTIVE");

        CourseSelection firstPreference = new CourseSelection();
        firstPreference.setId(11L);
        firstPreference.setEvent(event);
        firstPreference.setStudent(student);
        firstPreference.setCourse(firstCourse);
        firstPreference.setPreference(1);
        firstPreference.setStatus("PENDING");

        CourseSelection secondPreference = new CourseSelection();
        secondPreference.setId(12L);
        secondPreference.setEvent(event);
        secondPreference.setStudent(student);
        secondPreference.setCourse(secondCourse);
        secondPreference.setPreference(2);
        secondPreference.setStatus("PENDING");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(courseRepo.findById(4L)).thenReturn(Optional.of(secondCourse));
        when(selectionRepo.findByEventAndStudent(event, student)).thenReturn(List.of(firstPreference, secondPreference));
        when(selectionRepo.saveAndFlush(firstPreference)).thenReturn(firstPreference);

        CourseSelection result = courseService.submitPreference(student, 1L, 4L, 1);

        assertSame(firstPreference, result);
        assertSame(secondCourse, result.getCourse());
        assertEquals(1, result.getPreference());
        assertEquals("DRAFT", result.getStatus());
        verify(selectionRepo).delete(secondPreference);
        verify(selectionRepo).saveAndFlush(firstPreference);
        verify(selectionRepo, never()).save(any());
    }

    @Test
    void submitPreferenceShouldUpdateExistingPreferenceInsteadOfInsert() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND1");

        Student student = new Student();
        student.setId(2L);

        Course oldCourse = new Course();
        oldCourse.setId(3L);
        oldCourse.setStatus("ACTIVE");

        Course newCourse = new Course();
        newCourse.setId(4L);
        newCourse.setStatus("ACTIVE");

        CourseSelection existing = new CourseSelection();
        existing.setId(10L);
        existing.setEvent(event);
        existing.setStudent(student);
        existing.setCourse(oldCourse);
        existing.setPreference(1);
        existing.setRound(1);
        existing.setStatus("PENDING");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(courseRepo.findById(4L)).thenReturn(Optional.of(newCourse));
        when(selectionRepo.findByEventAndStudent(event, student)).thenReturn(List.of(existing));
        when(selectionRepo.saveAndFlush(existing)).thenReturn(existing);

        CourseSelection result = courseService.submitPreference(student, 1L, 4L, 1);

        assertSame(existing, result);
        assertSame(newCourse, result.getCourse());
        assertEquals(1, result.getPreference());
        assertEquals("DRAFT", result.getStatus());
        assertTrue(result.getSelectedAt() != null);
        verify(selectionRepo).saveAndFlush(existing);
        verify(selectionRepo, never()).save(any());
    }

    @Test
    void confirmRound1SelectionsShouldRequireFirstPreference() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND1");

        Student student = new Student();
        student.setId(2L);

        CourseSelection pref2 = new CourseSelection();
        pref2.setEvent(event);
        pref2.setStudent(student);
        pref2.setPreference(2);
        pref2.setRound(1);
        pref2.setStatus("DRAFT");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(selectionRepo.findByEventAndStudent(event, student)).thenReturn(List.of(pref2));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> courseService.confirmRound1Selections(student, 1L));

        assertNotNull(ex.getMessage());
        verify(selectionRepo, never()).saveAllAndFlush(any());
    }

    @Test
    void confirmRound1SelectionsShouldAllowOnlyFirstPreference() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND1");

        Student student = new Student();
        student.setId(2L);

        CourseSelection pref1 = new CourseSelection();
        pref1.setEvent(event);
        pref1.setStudent(student);
        pref1.setPreference(1);
        pref1.setRound(1);
        pref1.setStatus("DRAFT");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(selectionRepo.findByEventAndStudent(event, student)).thenReturn(List.of(pref1));

        int updated = courseService.confirmRound1Selections(student, 1L);

        assertEquals(1, updated);
        assertEquals("PENDING", pref1.getStatus());
        verify(selectionRepo).saveAllAndFlush(List.of(pref1));
    }

    @Test
    void confirmRound1SelectionsShouldPromoteDraftsToPending() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND1");

        Student student = new Student();
        student.setId(2L);

        CourseSelection pref1 = new CourseSelection();
        pref1.setEvent(event);
        pref1.setStudent(student);
        pref1.setPreference(1);
        pref1.setRound(1);
        pref1.setStatus("DRAFT");

        CourseSelection pref2 = new CourseSelection();
        pref2.setEvent(event);
        pref2.setStudent(student);
        pref2.setPreference(2);
        pref2.setRound(1);
        pref2.setStatus("DRAFT");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(selectionRepo.findByEventAndStudent(event, student)).thenReturn(List.of(pref1, pref2));

        int updated = courseService.confirmRound1Selections(student, 1L);

        assertEquals(2, updated);
        assertEquals("PENDING", pref1.getStatus());
        assertEquals("PENDING", pref2.getStatus());
        verify(selectionRepo).saveAllAndFlush(List.of(pref1, pref2));
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

    @Test
    void selectRound2ShouldFailWhenGlobalCapacityAtomicReserveFails() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND2");

        Student student = buildStudent(100L);

        Course course = new Course();
        course.setId(10L);
        course.setStatus("ACTIVE");
        course.setCapacityMode("GLOBAL");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")).thenReturn(false);
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepo.incrementCurrentCountIfAvailable(10L)).thenReturn(0);

        assertThrows(RuntimeException.class, () -> courseService.selectRound2(student, 1L, 10L));

        verify(selectionRepo, never()).saveAndFlush(any(CourseSelection.class));
    }

    @Test
    void selectRound2ShouldUseAtomicGlobalReserveBeforeSavingSelection() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND2");

        Student student = buildStudent(100L);

        Course course = new Course();
        course.setId(10L);
        course.setStatus("ACTIVE");
        course.setCapacityMode("GLOBAL");
        course.setCurrentCount(0);

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")).thenReturn(false);
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepo.incrementCurrentCountIfAvailable(10L)).thenReturn(1);
        when(selectionRepo.saveAndFlush(any(CourseSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseSelection result = courseService.selectRound2(student, 1L, 10L);

        assertSame(course, result.getCourse());
        assertEquals(0, course.getCurrentCount());
        verify(courseRepo).incrementCurrentCountIfAvailable(10L);
        verify(courseRepo, never()).findByIdForUpdate(10L);
        verify(studentService).assignElectiveClassFromCourse(student, course);
    }

    @Test
    void selectRound2ShouldUseAtomicPerClassReserveBeforeSavingSelection() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND2");

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(20L);

        Student student = buildStudent(100L);
        student.setSchoolClass(schoolClass);

        Course course = new Course();
        course.setId(10L);
        course.setStatus("ACTIVE");
        course.setCapacityMode("PER_CLASS");
        course.setCurrentCount(0);

        CourseClassCapacity capacity = new CourseClassCapacity();
        capacity.setCourse(course);
        capacity.setSchoolClass(schoolClass);

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(false);
        when(selectionRepo.existsByEventAndStudentAndStatus(event, student, "CONFIRMED")).thenReturn(false);
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));
        when(capacityRepo.findByCourseAndSchoolClass(course, schoolClass)).thenReturn(Optional.of(capacity));
        when(capacityRepo.incrementCurrentCountIfAvailable(10L, 20L)).thenReturn(1);
        when(courseRepo.incrementCurrentCount(10L)).thenReturn(1);
        when(selectionRepo.saveAndFlush(any(CourseSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseSelection result = courseService.selectRound2(student, 1L, 10L);

        assertSame(course, result.getCourse());
        assertEquals(0, course.getCurrentCount());
        verify(capacityRepo).incrementCurrentCountIfAvailable(10L, 20L);
        verify(courseRepo).incrementCurrentCount(10L);
        verify(capacityRepo, never()).findByCourseIdAndClassIdForUpdate(10L, 20L);
        verify(studentService).assignElectiveClassFromCourse(student, course);
    }

    @Test
    void finalizeEndedRound2EventShouldAutoAssignLotteryFailStudent() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND2");
        event.setRound2End(java.time.LocalDateTime.now().minusMinutes(1));

        Student student = buildStudent(100L);

        Course course = new Course();
        course.setId(10L);
        course.setStatus("ACTIVE");
        course.setCapacityMode("GLOBAL");
        course.setCurrentCount(0);
        course.setTotalCapacity(2);

        CourseSelection failSelection = new CourseSelection();
        failSelection.setEvent(event);
        failSelection.setStudent(student);
        failSelection.setCourse(course);
        failSelection.setRound(1);
        failSelection.setStatus("LOTTERY_FAIL");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(true);
        when(eventStudentRepo.findStudentsByEvent(event)).thenReturn(List.of(student));
        when(selectionRepo.findByEvent(event)).thenReturn(List.of(failSelection));
        when(courseRepo.findByEventAndStatusOrderByNameAsc(event, "ACTIVE")).thenReturn(List.of(course));
        when(courseRepo.incrementCurrentCountIfAvailable(10L)).thenReturn(1);
        when(selectionRepo.saveAndFlush(any(CourseSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int assigned = courseService.finalizeEndedRound2Event(1L);

        assertEquals(1, assigned);
        assertEquals("CLOSED", event.getStatus());
        assertTrue(event.getLotteryNote().contains("auto-assigned 1"));
        assertEquals(0, course.getCurrentCount());
        verify(studentService).assignElectiveClassFromCourse(student, course);
        verify(studentService).syncElectiveClassesForEvent(event);
        verify(selectionRepo).saveAndFlush(argThat(selection ->
                selection.getEvent() == event
                        && selection.getStudent() == student
                        && selection.getCourse() == course
                        && selection.getRound() == 2
                        && "CONFIRMED".equals(selection.getStatus())));
        verify(studentNotificationService).notifyRound2AutoAssignment(event, student, course);
    }

    @Test
    void finalizeEndedRound2EventShouldNotifyWhenNoCourseCanBeAssigned() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);
        event.setStatus("ROUND2");
        event.setRound2End(java.time.LocalDateTime.now().minusMinutes(1));

        Student student = buildStudent(100L);

        CourseSelection failSelection = new CourseSelection();
        failSelection.setEvent(event);
        failSelection.setStudent(student);
        failSelection.setRound(1);
        failSelection.setStatus("LOTTERY_FAIL");

        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventStudentRepo.existsByEvent(event)).thenReturn(true);
        when(eventStudentRepo.findStudentsByEvent(event)).thenReturn(List.of(student));
        when(selectionRepo.findByEvent(event)).thenReturn(List.of(failSelection));
        when(courseRepo.findByEventAndStatusOrderByNameAsc(event, "ACTIVE")).thenReturn(List.of());

        int assigned = courseService.finalizeEndedRound2Event(1L);

        assertEquals(0, assigned);
        assertEquals("CLOSED", event.getStatus());
        verify(studentService).syncElectiveClassesForEvent(event);
        verify(studentNotificationService).notifyRound2ClosedWithoutCourse(event, student);
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
