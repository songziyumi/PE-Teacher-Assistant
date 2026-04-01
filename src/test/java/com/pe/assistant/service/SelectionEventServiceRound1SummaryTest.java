package com.pe.assistant.service;

import com.pe.assistant.dto.Round1LotterySummary;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectionEventServiceRound1SummaryTest {

    @Mock
    private SelectionEventRepository eventRepo;
    @Mock
    private CourseRepository courseRepo;
    @Mock
    private CourseClassCapacityRepository capacityRepo;
    @Mock
    private CourseSelectionRepository selectionRepo;
    @Mock
    private EventStudentRepository eventStudentRepo;
    @Mock
    private StudentRepository studentRepo;
    @Mock
    private StudentAccountService studentAccountService;
    @Mock
    private LotteryService lotteryService;

    @InjectMocks
    private SelectionEventService selectionEventService;

    @Test
    void shouldSummarizeRound1ResultsByConfirmedPreference() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);

        Course course = new Course();
        course.setId(10L);
        course.setName("Basketball");
        course.setSchool(new School());
        course.setEvent(event);

        Student firstChoiceWinner = buildStudent(101L);
        Student secondChoiceWinner = buildStudent(102L);
        Student unsuccessfulStudent = buildStudent(103L);
        Student draftOnlyStudent = buildStudent(104L);

        CourseSelection pref1Confirmed = buildSelection(event, course, firstChoiceWinner, 1, "CONFIRMED", true);
        CourseSelection pref2CancelledAfterDrop = buildSelection(event, course, secondChoiceWinner, 2, "CANCELLED", true);
        CourseSelection unsuccessfulPref1 = buildSelection(event, course, unsuccessfulStudent, 1, "LOTTERY_FAIL", false);
        CourseSelection unsuccessfulPref2 = buildSelection(event, course, unsuccessfulStudent, 2, "LOTTERY_FAIL", false);
        CourseSelection draftSelection = buildSelection(event, course, draftOnlyStudent, 1, "DRAFT", false);

        when(selectionRepo.findByEvent(event)).thenReturn(List.of(
                pref1Confirmed,
                pref2CancelledAfterDrop,
                unsuccessfulPref1,
                unsuccessfulPref2,
                draftSelection
        ));

        Round1LotterySummary summary = selectionEventService.getRound1LotterySummary(event);

        assertEquals(1, summary.getFirstChoiceConfirmedCount());
        assertEquals(1, summary.getSecondChoiceConfirmedCount());
        assertEquals(1, summary.getUnsuccessfulCount());
        assertEquals(3, summary.getSubmittedStudentCount());
    }

    private Student buildStudent(Long id) {
        Student student = new Student();
        student.setId(id);
        student.setName("student-" + id);
        return student;
    }

    private CourseSelection buildSelection(SelectionEvent event,
                                           Course course,
                                           Student student,
                                           int preference,
                                           String status,
                                           boolean confirmed) {
        CourseSelection selection = new CourseSelection();
        selection.setEvent(event);
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setRound(1);
        selection.setPreference(preference);
        selection.setStatus(status);
        selection.setSelectedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        selection.setConfirmedAt(confirmed ? LocalDateTime.of(2026, 1, 1, 9, 0) : null);
        return selection;
    }
}
