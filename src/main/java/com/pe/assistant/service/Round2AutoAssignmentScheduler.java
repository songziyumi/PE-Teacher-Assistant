package com.pe.assistant.service;

import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.SelectionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Round2AutoAssignmentScheduler {

    private final SelectionEventRepository eventRepo;
    private final CourseService courseService;

    @Scheduled(fixedDelay = 60000)
    public void finalizeEndedRound2Events() {
        for (SelectionEvent event : eventRepo.findByStatusOrderByCreatedAtAsc("ROUND2")) {
            try {
                courseService.finalizeEndedRound2Event(event.getId());
            } catch (IllegalStateException ex) {
                log.warn("第二轮自动分配暂未完成，eventId={} reason={}", event.getId(), ex.getMessage());
            } catch (Exception ex) {
                log.error("第二轮自动分配失败，eventId={}", event.getId(), ex);
            }
        }
    }
}
