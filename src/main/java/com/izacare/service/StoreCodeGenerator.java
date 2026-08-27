package com.izacare.service;

import com.izacare.repository.StoreRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 가게 고유 코드 발급 — 혼동되는 글자(0/O, 1/I) 제외, 중복 없는 6자리 보장 */
@Component
public class StoreCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;
    private final SecureRandom random = new SecureRandom();
    private final StoreRepository storeRepository;

    public StoreCodeGenerator(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /** 기존 코드와 겹치지 않는 새 코드를 생성한다 */
    public String generateUnique() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = random();
            if (!storeRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("가게 코드 생성에 실패했습니다. 다시 시도해 주세요.");
    }

    private String random() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
