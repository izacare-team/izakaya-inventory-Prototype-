package com.izacare.vision;

import java.util.List;

/**
 * 사진 → 식재료 인식 추상화.
 * 구현체를 갈아끼우면(Gemini, OpenAI Vision, 자체 모델 등) 서비스 로직은 그대로 재사용된다.
 */
public interface VisionAiClient {

    /**
     * @param images      촬영된 냉장고/창고 사진들 — 냉장고·주류고·창고처럼 서로 다른 구역을
     *                    여러 장 찍어 한 번에 인식시킨다 (같은 품목은 합산)
     * @param contentType 이미지 MIME 타입 (image/jpeg 등)
     * @param knownItemNames 등록된 품목명 목록 — 프롬프트에 넣어 매칭 정확도를 올리는 힌트
     */
    VisionResult recognize(List<byte[]> images, String contentType, List<String> knownItemNames);
}
