package com.pe.assistant.service;

import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.SelectionEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Round1AutoLotterySchedulerTest {

    @Mock
    private SelectionEventRepository eventRepo;
    @Mock
    private SelectionEventService selectionEventService;

    @InjectMocks
    private Round1AutoLotteryScheduler scheduler;

    @Test
    void processEndedRound1EventsShouldTriggerOnlyWhenRound1EndedFiveMinutesAgo() {
        SelectionEvent shouldStart = new SelectionEvent();
        shouldStart.setId(1L);
        shouldStart.setStatus("ROUND1");
        shouldStart.setRound1End(LocalDateTime.now().minusMinutes(6));

        SelectionEvent shouldWait = new SelectionEvent();
        shouldWait.setId(2L);
        shouldWait.setStatus("ROUND1");
        shouldWait.setRound1End(LocalDateTime.now().minusMinutes(4));

        when(eventRepo.findByStatusOrderByCreatedAtAsc("ROUND1")).thenReturn(List.of(shouldStart, shouldWait));
        when(selectionEventService.processRound1Automatically(1L)).thenReturn(true);

        scheduler.processEndedRound1Events();

        verify(selectionEventService).processRound1Automatically(1L);
        verify(selectionEventService, never()).processRound1Automatically(2L);
    }
}
