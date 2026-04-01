package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.SelectionEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotteryServiceRegressionTest {

    @Mock
    private SelectionEventRepository eventRepo;
    @Mock
    private CourseRepository courseRepo;
    @Mock
    private CourseClassCapacityRepository capacityRepo;
    @Mock
    private CourseSelectionRepository selectionRepo;
    @Mock
    private StudentNotificationService studentNotificationService;

    @InjectMocks
    private LotteryService lotteryService;

    @Test
    void shouldPreferFirstChoiceBeforeSettlingSecondChoice() throws Exception {
        SelectionEvent event = buildEvent(1L);

        Course alpha = buildGlobalCourse(11L, event, "Alpha", 1);
        Course beta = buildGlobalCourse(12L, event, "Beta", 1);

        Student student = buildStudent(101L, buildClass(1L, "Class 1"));

        CourseSelection firstChoice = buildSelection(1001L, event, beta, student, 1, "PENDING");
        CourseSelection secondChoice = buildSelection(1002L, event, alpha, student, 2, "PENDING");
        List<CourseSelection> selections = new ArrayList<>(List.of(firstChoice, secondChoice));

        stubCommonEventAndCourseQueries(event, List.of(alpha, beta));
        stubConfirmedSelections(event, selections);
        stubGlobalSelectionQueries(selections);
        stubSavePassthrough();

        lotteryService.doRunLottery(event.getId());

        assertEquals("ROUND2", event.getStatus());
        assertEquals("CONFIRMED", firstChoice.getStatus());
        assertEquals("CANCELLED", secondChoice.getStatus());
        assertEquals(1, beta.getCurrentCount());
        assertEquals(0, alpha.getCurrentCount());
    }

    @Test
    void shouldFillSecondChoiceFromRemainingCapacityAfterFirstChoiceSettlement() throws Exception {
        SelectionEvent event = buildEvent(3L);

        Course alpha = buildGlobalCourse(31L, event, "Alpha", 2);
        Course beta = buildGlobalCourse(32L, event, "Beta", 0);

        SchoolClass schoolClass = buildClass(3L, "Class 3");
        Student firstChoiceWinner = buildStudent(301L, schoolClass);
        Student secondChoiceCandidate = buildStudent(302L, schoolClass);

        CourseSelection alphaPref1 = buildSelection(3001L, event, alpha, firstChoiceWinner, 1, "PENDING");
        CourseSelection betaPref1 = buildSelection(3002L, event, beta, secondChoiceCandidate, 1, "PENDING");
        CourseSelection alphaPref2 = buildSelection(3003L, event, alpha, secondChoiceCandidate, 2, "PENDING");
        List<CourseSelection> selections = new ArrayList<>(List.of(alphaPref1, betaPref1, alphaPref2));

        stubCommonEventAndCourseQueries(event, List.of(alpha, beta));
        stubConfirmedSelections(event, selections);
        stubGlobalSelectionQueries(selections);
        stubSavePassthrough();

        lotteryService.doRunLottery(event.getId());

        assertEquals("CONFIRMED", alphaPref1.getStatus());
        assertEquals("LOTTERY_FAIL", betaPref1.getStatus());
        assertEquals("CONFIRMED", alphaPref2.getStatus());
        assertEquals(2, alpha.getCurrentCount());
        assertEquals(0, beta.getCurrentCount());
    }

    @Test
    void shouldUseRemainingClassCapacityForSecondChoiceOnlyWithinSameClass() throws Exception {
        SelectionEvent event = buildEvent(2L);

        SchoolClass class1 = buildClass(11L, "Class 1");
        SchoolClass class2 = buildClass(12L, "Class 2");

        Course art = buildPerClassCourse(21L, event, "Art", 2);
        Course music = buildPerClassCourse(22L, event, "Music", 0);

        CourseClassCapacity artClass1 = buildCapacity(art, class1, 1);
        CourseClassCapacity artClass2 = buildCapacity(art, class2, 1);
        CourseClassCapacity musicClass1 = buildCapacity(music, class1, 0);
        CourseClassCapacity musicClass2 = buildCapacity(music, class2, 0);

        Student class1FirstWinner = buildStudent(201L, class1);
        Student class1SecondChoice = buildStudent(202L, class1);
        Student class2SecondChoice = buildStudent(203L, class2);

        CourseSelection artPref1Class1 = buildSelection(2001L, event, art, class1FirstWinner, 1, "PENDING");
        CourseSelection musicPref1Class1 = buildSelection(2002L, event, music, class1SecondChoice, 1, "PENDING");
        CourseSelection artPref2Class1 = buildSelection(2003L, event, art, class1SecondChoice, 2, "PENDING");
        CourseSelection musicPref1Class2 = buildSelection(2004L, event, music, class2SecondChoice, 1, "PENDING");
        CourseSelection artPref2Class2 = buildSelection(2005L, event, art, class2SecondChoice, 2, "PENDING");

        List<CourseSelection> selections = new ArrayList<>(List.of(
                artPref1Class1,
                musicPref1Class1,
                artPref2Class1,
                musicPref1Class2,
                artPref2Class2
        ));

        stubCommonEventAndCourseQueries(event, List.of(art, music));
        stubConfirmedSelections(event, selections);
        stubPerClassSelectionQueries(selections);
        when(capacityRepo.findByCourse(art)).thenReturn(List.of(artClass1, artClass2));
        when(capacityRepo.findByCourse(music)).thenReturn(List.of(musicClass1, musicClass2));
        doAnswer(invocation -> invocation.getArgument(0)).when(capacityRepo).save(any(CourseClassCapacity.class));
        stubSavePassthrough();

        lotteryService.doRunLottery(event.getId());

        assertEquals("CONFIRMED", artPref1Class1.getStatus());
        assertEquals("LOTTERY_FAIL", musicPref1Class1.getStatus());
        assertEquals("LOTTERY_FAIL", artPref2Class1.getStatus());
        assertEquals("LOTTERY_FAIL", musicPref1Class2.getStatus());
        assertEquals("CONFIRMED", artPref2Class2.getStatus());
        assertEquals(1, artClass1.getCurrentCount());
        assertEquals(1, artClass2.getCurrentCount());
        assertEquals(2, art.getCurrentCount());
        assertTrue(event.getLotteryNote().contains("第二轮"));
    }

    @Test
    void shouldNotifyStudentsWithRound1ResultAfterLottery() throws Exception {
        SelectionEvent event = buildEvent(4L);

        Course alpha = buildGlobalCourse(41L, event, "Alpha", 1);
        Course beta = buildGlobalCourse(42L, event, "Beta", 0);

        SchoolClass schoolClass = buildClass(4L, "Class 4");
        Student winner = buildStudent(401L, schoolClass);
        Student loser = buildStudent(402L, schoolClass);

        CourseSelection winnerSelection = buildSelection(4001L, event, alpha, winner, 1, "PENDING");
        CourseSelection loserSelection = buildSelection(4002L, event, beta, loser, 1, "PENDING");
        List<CourseSelection> selections = new ArrayList<>(List.of(winnerSelection, loserSelection));

        stubCommonEventAndCourseQueries(event, List.of(alpha, beta));
        stubConfirmedSelections(event, selections);
        stubGlobalSelectionQueries(selections);
        stubSavePassthrough();

        lotteryService.doRunLottery(event.getId());

        verify(studentNotificationService).notifyRound1Result(event, winner, alpha);
        verify(studentNotificationService).notifyRound1Result(event, loser, null);
    }

    private void stubCommonEventAndCourseQueries(SelectionEvent event, List<Course> courses) {
        when(eventRepo.findById(event.getId())).thenReturn(Optional.of(event));
        when(courseRepo.findByEventOrderByNameAsc(event)).thenReturn(courses.stream()
                .sorted(Comparator.comparing(Course::getName))
                .toList());
    }

    private void stubConfirmedSelections(SelectionEvent event, List<CourseSelection> selections) {
        when(selectionRepo.findByEvent(event)).thenReturn(selections);
        when(selectionRepo.findByEventAndStatus(event, "CONFIRMED")).thenAnswer(invocation -> selections.stream()
                .filter(selection -> event.getId().equals(selection.getEvent().getId()))
                .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                .toList());
    }

    private void stubGlobalSelectionQueries(List<CourseSelection> selections) {
        when(selectionRepo.findByCourseAndStatusOrderBySelectedAtAsc(any(Course.class), any(String.class)))
                .thenAnswer(invocation -> {
                    Course course = invocation.getArgument(0);
                    String status = invocation.getArgument(1);
                    return selections.stream()
                            .filter(selection -> course.getId().equals(selection.getCourse().getId()))
                            .filter(selection -> status.equals(selection.getStatus()))
                            .sorted(Comparator.comparing(CourseSelection::getSelectedAt))
                            .toList();
                });
    }

    private void stubPerClassSelectionQueries(List<CourseSelection> selections) {
        when(selectionRepo.findPendingByClassId(any(Course.class), any(Long.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            Long classId = invocation.getArgument(1);
            return selections.stream()
                    .filter(selection -> course.getId().equals(selection.getCourse().getId()))
                    .filter(selection -> "PENDING".equals(selection.getStatus()))
                    .filter(selection -> selection.getRound() == 1)
                    .filter(selection -> classId.equals(selection.getStudent().getSchoolClass().getId()))
                    .sorted(Comparator.comparing(CourseSelection::getSelectedAt))
                    .toList();
        });
    }

    private void stubSavePassthrough() {
        doAnswer(invocation -> invocation.getArgument(0)).when(selectionRepo).save(any(CourseSelection.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(courseRepo).save(any(Course.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(eventRepo).save(any(SelectionEvent.class));
    }

    private SelectionEvent buildEvent(Long id) {
        SelectionEvent event = new SelectionEvent();
        event.setId(id);
        event.setStatus("PROCESSING");
        return event;
    }

    private Course buildGlobalCourse(Long id, SelectionEvent event, String name, int capacity) {
        Course course = new Course();
        course.setId(id);
        course.setEvent(event);
        course.setSchool(new School());
        course.setName(name);
        course.setCapacityMode("GLOBAL");
        course.setTotalCapacity(capacity);
        course.setCurrentCount(0);
        return course;
    }

    private Course buildPerClassCourse(Long id, SelectionEvent event, String name, int totalCapacity) {
        Course course = buildGlobalCourse(id, event, name, totalCapacity);
        course.setCapacityMode("PER_CLASS");
        return course;
    }

    private CourseClassCapacity buildCapacity(Course course, SchoolClass schoolClass, int maxCapacity) {
        CourseClassCapacity capacity = new CourseClassCapacity();
        capacity.setCourse(course);
        capacity.setSchoolClass(schoolClass);
        capacity.setMaxCapacity(maxCapacity);
        capacity.setCurrentCount(0);
        return capacity;
    }

    private Student buildStudent(Long id, SchoolClass schoolClass) {
        Student student = new Student();
        student.setId(id);
        student.setName("student-" + id);
        student.setSchoolClass(schoolClass);
        student.setSchool(new School());
        return student;
    }

    private SchoolClass buildClass(Long id, String name) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setName(name);
        return schoolClass;
    }

    private CourseSelection buildSelection(Long id,
                                           SelectionEvent event,
                                           Course course,
                                           Student student,
                                           int preference,
                                           String status) {
        CourseSelection selection = new CourseSelection();
        selection.setId(id);
        selection.setEvent(event);
        selection.setCourse(course);
        selection.setStudent(student);
        selection.setRound(1);
        selection.setPreference(preference);
        selection.setStatus(status);
        selection.setSelectedAt(LocalDateTime.of(2026, 1, 1, 8, 0).plusSeconds(id));
        return selection;
    }
}
