package com.pe.assistant.service;

import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.SelectionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class Round1AutoLotteryScheduler {

    private static final long AUTO_LOTTERY_DELAY_MINUTES = 5L;

    private final SelectionEventRepository eventRepo;
    private final SelectionEventService selectionEventService;

    @Scheduled(fixedDelay = 60000)
    public void processEndedRound1Events() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(AUTO_LOTTERY_DELAY_MINUTES);
        for (SelectionEvent event : eventRepo.findByStatusOrderByCreatedAtAsc("ROUND1")) {
            if (event.getRound1End() == null || event.getRound1End().isAfter(cutoff)) {
                continue;
            }
            try {
                boolean started = selectionEventService.processRound1Automatically(event.getId());
                if (started) {
                    log.info("第一轮自动抽签已启动，eventId={} round1End={} delayMinutes={}",
                            event.getId(),
                            event.getRound1End(),
                            AUTO_LOTTERY_DELAY_MINUTES);
                }
            } catch (Exception ex) {
                log.error("第一轮自动抽签失败，eventId={}", event.getId(), ex);
            }
        }
    }
}
