package com.pe.assistant.service;

import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.SelectionEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Round2AutoAssignmentSchedulerTest {

    @Mock
    private SelectionEventRepository eventRepo;
    @Mock
    private CourseService courseService;

    @InjectMocks
    private Round2AutoAssignmentScheduler scheduler;

    @Test
    void finalizeEndedRound2EventsShouldContinueWhenTeacherAssignmentIsIncomplete() {
        SelectionEvent blockedEvent = new SelectionEvent();
        blockedEvent.setId(1L);
        blockedEvent.setStatus("ROUND2");

        SelectionEvent nextEvent = new SelectionEvent();
        nextEvent.setId(2L);
        nextEvent.setStatus("ROUND2");

        when(eventRepo.findByStatusOrderByCreatedAtAsc("ROUND2")).thenReturn(List.of(blockedEvent, nextEvent));
        doThrow(new IllegalStateException("以下课程尚未分配授课教师")).when(courseService).finalizeEndedRound2Event(1L);

        scheduler.finalizeEndedRound2Events();

        verify(courseService).finalizeEndedRound2Event(1L);
        verify(courseService).finalizeEndedRound2Event(2L);
    }
}
