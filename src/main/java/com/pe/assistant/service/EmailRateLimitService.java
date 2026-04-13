package com.pe.assistant.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailRateLimitService {

    private final Map<String, Deque<Instant>> requestWindows = new ConcurrentHashMap<>();

    public void checkLimit(String key, int maxCount, Duration window, String message) {
        if (key == null || key.isBlank() || maxCount <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> history = requestWindows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.pollFirst();
            }
            if (history.size() >= maxCount) {
                throw new IllegalArgumentException(message);
            }
            history.addLast(now);
        }
    }
}
