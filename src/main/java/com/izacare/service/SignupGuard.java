package com.izacare.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회원가입 남용(테러) 방지 — 같은 IP가 1시간 내 3회 이상 가입하면 1시간 동안 차단.
 * 메모리 기반이라 서버 재시작 시 초기화된다(가벼운 방어 목적).
 */
@Component
public class SignupGuard {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, List<Instant>> attemptsByIp = new ConcurrentHashMap<>();

    /** 가입 시도 직전 호출 — 한도 초과면 예외를 던진다. 통과하면 이번 시도를 기록한다. */
    public synchronized void checkAndRecord(String ip) {
        Instant now = Instant.now();
        List<Instant> attempts = attemptsByIp.computeIfAbsent(ip, k -> new ArrayList<>());
        attempts.removeIf(t -> t.isBefore(now.minus(WINDOW)));   // 오래된 기록 정리

        if (attempts.size() >= MAX_ATTEMPTS) {
            Instant oldest = attempts.get(0);
            long minutesLeft = Duration.between(now, oldest.plus(WINDOW)).toMinutes() + 1;
            throw new IllegalStateException(
                    "회원가입 시도가 너무 많습니다. 약 " + minutesLeft + "분 후 다시 시도해 주세요.");
        }
        attempts.add(now);
    }
}
