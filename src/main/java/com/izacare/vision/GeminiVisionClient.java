package com.izacare.vision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini Vision 연동 구현체.
 * application.yml에서 vision.provider=gemini + GEMINI_API_KEY 환경변수 설정 시 활성화.
 */
@Component
@ConditionalOnProperty(name = "vision.provider", havingValue = "gemini")
public class GeminiVisionClient implements VisionAiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiVisionClient(RestClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${vision.gemini.api-key}") String apiKey,
                              @Value("${vision.gemini.model}") String model) {
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public VisionResult recognize(byte[] imageBytes, String contentType, List<String> knownItemNames) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("여기에")) {
            throw new IllegalStateException(
                    "Gemini API 키가 설정되지 않았습니다. src/main/resources/secret.yml 의 api-key 에 발급받은 키를 입력하세요.");
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = """
                이 사진은 이자카야 매장의 냉장고/창고 내부입니다.
                사진에 보이는 식재료와 그 개수를 세어주세요.

                등록된 품목 목록: %s
                가능하면 위 목록의 품목명과 동일한 이름으로 답하세요.

                반드시 아래 형식의 JSON 배열로만 답하세요. 다른 텍스트는 넣지 마세요.
                [{"name":"품목명","quantity":개수,"confidence":0.0~1.0}]
                """.formatted(String.join(", ", knownItemNames));

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of(
                                        "mime_type", contentType != null ? contentType : "image/jpeg",
                                        "data", base64))))),
                "generationConfig", Map.of("response_mime_type", "application/json"));

        try {
            String raw = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)   // 키는 URL이 아닌 헤더로 전달 (로그 유출 방지)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String text = extractText(raw);
            List<RecognizedItem> items = objectMapper.readValue(text, new TypeReference<>() {});
            return new VisionResult(items, raw);
        } catch (Exception e) {
            throw new IllegalStateException("Vision AI 호출 실패: " + e.getMessage(), e);
        }
    }

    /** Gemini 응답 JSON에서 모델이 생성한 text 부분만 추출 */
    private String extractText(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        return root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText();
    }
}
