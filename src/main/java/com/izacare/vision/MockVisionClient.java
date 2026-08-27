package com.izacare.vision;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * API 키 없이 개발/시연할 때 쓰는 가짜 Vision 구현체.
 * 등록된 품목 중 일부를 "인식했다"고 흉내내며, 일부러 수량 오차와
 * 미등록 품목 인식(레몬사와 캔)도 섞어서 실사 검토 UX를 테스트할 수 있게 한다.
 */
@Component
@ConditionalOnProperty(name = "vision.provider", havingValue = "mock", matchIfMissing = true)
public class MockVisionClient implements VisionAiClient {

    @Override
    public VisionResult recognize(byte[] imageBytes, String contentType, List<String> knownItemNames) {
        // 이미지 바이트로 시드를 만들어 같은 사진 = 같은 결과가 나오게 함
        Random random = new Random(imageBytes.length);

        List<RecognizedItem> items = new ArrayList<>();
        for (String name : knownItemNames) {
            if (random.nextDouble() < 0.25) continue; // 일부 품목은 사진에 안 찍혔다고 가정

            int base = 5 + random.nextInt(20);
            int noise = random.nextInt(5) - 2;        // -2 ~ +2 카운팅 오차
            double confidence = 0.55 + random.nextDouble() * 0.45;
            items.add(new RecognizedItem(name, Math.max(0, base + noise),
                    Math.round(confidence * 100) / 100.0));
        }

        // 등록 안 된 품목을 인식한 경우 시뮬레이션
        if (random.nextBoolean()) {
            items.add(new RecognizedItem("레몬사와 캔", 3 + random.nextInt(5), 0.72));
        }

        return new VisionResult(items,
                "{\"mock\":true,\"imageSize\":" + imageBytes.length + "}");
    }
}
