package com.izacare.vision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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

    /** 응답을 기다리는 상한. 실사 화면에서 사용자가 대기하므로 짧게 잡는다. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 503 재시도 횟수. 최악 대기 = READ_TIMEOUT × (1 + MAX_RETRY) + RETRY_DELAY = 31초. */
    private static final int MAX_RETRY = 1;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiVisionClient(RestClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${vision.gemini.api-key}") String apiKey,
                              @Value("${vision.gemini.model}") String model,
                              @Value("${vision.gemini.base-url:https://generativelanguage.googleapis.com}")
                              String baseUrl) {
        // 타임아웃이 없으면 구글이 응답을 붙잡고 있을 때 요청이 무한정 대기한다.
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT)))
                .build();
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
            String raw = callWithRetry(body);
            String text = extractText(raw);
            List<RecognizedItem> items = objectMapper.readValue(text, new TypeReference<>() {});
            return new VisionResult(items, raw);
        } catch (Exception e) {
            throw new IllegalStateException("Vision AI 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini 호출. 503처럼 서버가 일시적으로 바쁠 때만 재시도한다.
     * 4xx(키 오류·모델명 오타)는 다시 던져도 결과가 같으므로 즉시 실패시킨다.
     */
    private String callWithRetry(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1beta/models/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey)   // 키는 URL이 아닌 헤더로 전달 (로그 유출 방지)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (HttpServerErrorException e) {
                if (attempt >= MAX_RETRY) throw e;
                try {
                    Thread.sleep(RETRY_DELAY.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
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
