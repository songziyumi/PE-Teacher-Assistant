package com.pe.assistant.security;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制：同一用户名连续失败 5 次后锁定 15 分钟
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_SECONDS = 15 * 60;

    private record AttemptInfo(int count, Instant lockedUntil) {}

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    public void loginFailed(String username) {
        AttemptInfo info = attempts.getOrDefault(username, new AttemptInfo(0, null));
        int newCount = info.count() + 1;
        Instant lockedUntil = newCount >= MAX_ATTEMPTS
                ? Instant.now().plusSeconds(LOCK_DURATION_SECONDS)
                : null;
        attempts.put(username, new AttemptInfo(newCount, lockedUntil));
    }

    public void loginSucceeded(String username) {
        attempts.remove(username);
    }

    public boolean isBlocked(String username) {
        AttemptInfo info = attempts.get(username);
        if (info == null || info.lockedUntil() == null) return false;
        if (Instant.now().isAfter(info.lockedUntil())) {
            attempts.remove(username);
            return false;
        }
        return true;
    }
}
