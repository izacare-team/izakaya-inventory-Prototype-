package com.izacare.vision;

import java.util.List;

/** Vision AI 호출 결과: 인식 품목 리스트 + 응답 원본 */
public record VisionResult(List<RecognizedItem> items, String rawResponse) {}
