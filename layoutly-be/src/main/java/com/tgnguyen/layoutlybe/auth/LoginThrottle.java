package com.tgnguyen.layoutlybe.auth;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Chan do mat khau: toi da 5 lan sai trong 15 phut cho moi cap (IP, email). Luu trong bo nho. */
@Component
public class LoginThrottle {
    static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Deque<Long> attempts = failures.get(key);
        if (attempts == null) return false;
        synchronized (attempts) {
            prune(attempts);
            return attempts.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > MAX_KEYS) failures.clear();
        Deque<Long> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(System.currentTimeMillis());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Long> attempts) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!attempts.isEmpty() && attempts.peekFirst() < cutoff) attempts.pollFirst();
    }
}
