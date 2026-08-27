package com.izacare.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 무차별 대입(brute force) 방지 — (가게코드+아이디+IP) 기준으로
 * 10분 내 5회 로그인 실패하면 10분 동안 잠근다. 성공하면 카운트 초기화.
 */
@Component
public class LoginGuard {

    private static final int MAX_FAILS = 5;
    private static final Duration LOCK = Duration.ofMinutes(10);

    private record Attempt(int fails, Instant firstFailAt) {}

    private final Map<String, Attempt> byKey = new ConcurrentHashMap<>();

    /** 로그인 시도 직전 — 잠금 중이면 예외 */
    public synchronized void checkLocked(String key) {
        Attempt a = byKey.get(key);
        if (a == null) return;
        Instant now = Instant.now();
        if (now.isAfter(a.firstFailAt().plus(LOCK))) {
            byKey.remove(key);   // 잠금 시간 경과 → 초기화
            return;
        }
        if (a.fails() >= MAX_FAILS) {
            long minutesLeft = Duration.between(now, a.firstFailAt().plus(LOCK)).toMinutes() + 1;
            throw new IllegalStateException(
                    "로그인 시도가 너무 많습니다. 약 " + minutesLeft + "분 후 다시 시도해 주세요.");
        }
    }

    /** 로그인 실패 기록 */
    public synchronized void recordFail(String key) {
        Instant now = Instant.now();
        Attempt a = byKey.get(key);
        if (a == null || now.isAfter(a.firstFailAt().plus(LOCK))) {
            byKey.put(key, new Attempt(1, now));
        } else {
            byKey.put(key, new Attempt(a.fails() + 1, a.firstFailAt()));
        }
    }

    /** 로그인 성공 — 기록 제거 */
    public synchronized void clear(String key) {
        byKey.remove(key);
    }
}
