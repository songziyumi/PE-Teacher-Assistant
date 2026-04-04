package com.pe.assistant.service;

import com.pe.assistant.entity.EventStudent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectionEventServiceRegressionTest {

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
    @Mock
    private StudentService studentService;

    @InjectMocks
    private SelectionEventService selectionEventService;

    @Test
    void setEventStudentsShouldDeduplicateStudentIdsBeforeInsert() {
        SelectionEvent event = new SelectionEvent();
        event.setId(1L);

        Student first = buildStudent(101L);
        Student second = buildStudent(102L);

        when(studentRepo.findById(101L)).thenReturn(Optional.of(first));
        when(studentRepo.findById(102L)).thenReturn(Optional.of(second));
        when(studentAccountService.initializeAccount(first)).thenReturn(Optional.empty());
        when(studentAccountService.initializeAccount(second)).thenReturn(Optional.empty());

        selectionEventService.setEventStudents(event, List.of(101L, 101L, 102L, 102L));

        verify(eventStudentRepo).deleteByEvent(event);
        verify(eventStudentRepo).flush();
        verify(studentRepo, times(1)).findById(101L);
        verify(studentRepo, times(1)).findById(102L);
        verify(studentAccountService, times(1)).initializeAccount(first);
        verify(studentAccountService, times(1)).initializeAccount(second);

        ArgumentCaptor<EventStudent> relationCaptor = ArgumentCaptor.forClass(EventStudent.class);
        verify(eventStudentRepo, times(2)).save(relationCaptor.capture());
        List<Long> savedStudentIds = relationCaptor.getAllValues().stream()
                .map(relation -> relation.getStudent().getId())
                .toList();
        assertEquals(List.of(101L, 102L), savedStudentIds);
    }

    @Test
    void closeEventShouldSyncStudentElectiveClasses() {
        SelectionEvent event = new SelectionEvent();
        event.setId(2L);
        event.setStatus("ROUND2");

        when(eventRepo.findById(2L)).thenReturn(Optional.of(event));

        selectionEventService.closeEvent(2L);

        assertEquals("CLOSED", event.getStatus());
        verify(eventRepo).save(event);
        verify(studentService).syncElectiveClassesForEvent(event);
    }

    @Test
    void processRound1AutomaticallyShouldStartLotteryWhenEventStillInRound1() {
        when(eventRepo.markProcessingIfRound1(3L, "第一轮结束满5分钟，系统自动启动抽签")).thenReturn(1);

        boolean started = selectionEventService.processRound1Automatically(3L);

        assertTrue(started);
        verify(lotteryService).runLottery(3L);
    }

    @Test
    void processRound1AutomaticallyShouldSkipWhenEventAlreadyMovedOutOfRound1() {
        when(eventRepo.markProcessingIfRound1(4L, "第一轮结束满5分钟，系统自动启动抽签")).thenReturn(0);

        boolean started = selectionEventService.processRound1Automatically(4L);

        assertFalse(started);
        verify(lotteryService, never()).runLottery(4L);
    }

    @Test
    void processRound1ShouldThrowWhenEventCannotEnterProcessing() {
        when(eventRepo.markProcessingIfRound1(5L, "抽签即将开始")).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> selectionEventService.processRound1(5L));

        assertEquals("活动当前状态不允许执行抽签", ex.getMessage());
        verify(lotteryService, never()).runLottery(5L);
    }

    private Student buildStudent(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }
}
