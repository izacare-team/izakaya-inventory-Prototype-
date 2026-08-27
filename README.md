# IzaCare — 이자카야 매장관리

Figma 디자인을 Spring Boot로 구현한 매장관리 웹앱입니다.
재고(입고 / 폐기 / 실사)를 중심으로 예약·근태·일보·공지까지 한 화면에서 다룹니다.
화면은 순수 HTML + JS + 공용 `app.css` 단일 페이지 구조입니다 (프런트 프레임워크 없음).

## 실행 방법

```bash
./gradlew bootRun        # 또는 gradle bootRun
```

- 접속: http://localhost:8080 (로그인 필요 — 데모 계정: 사장님 boss/1234, 알바생 staff/1234, 승인 대기 pend/1234)
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:izacare`)
- 시연용 초기 데이터 9개 품목이 자동 등록됩니다.

## AI 실사 (핵심 기능)

사진 촬영 → AI 인식 → 검토/보정 → 확정(재고 반영) 4단계 흐름.

- **API 키 입력 위치: `src/main/resources/secret.yml`** — 이 파일의 `api-key` 값에
  Google AI Studio에서 발급받은 키를 붙여넣으면 Gemini(gemini-3.7-flash) 연동이 활성화됩니다.
  (secret.yml은 .gitignore에 등록되어 있어 커밋되지 않습니다)
- secret.yml이 없거나 키가 비어 있으면 **mock 모드**로 동작: API 키 없이 인식 결과를
  흉내내서 전체 플로우를 시연할 수 있음 (수량 오차, 미등록 품목 인식까지 시뮬레이션됨)
- 환경변수 방식도 그대로 지원: `VISION_PROVIDER=gemini GEMINI_API_KEY=발급받은키 ./gradlew bootRun`

## API 요약

| Method | URL | 설명 |
|---|---|---|
| GET | /api/items | 재고 목록 (재고 부족 여부 포함) |
| POST | /api/items | 품목 등록 |
| POST | /api/inbound | 입고 (수량 증가 + 이력) |
| POST | /api/dispose | 폐기 (수량 감소 + 사유 필수) |
| GET | /api/transactions | 최근 변동 이력 |
| POST | /api/audits/scan | 사진 업로드 → AI 인식 → DRAFT 실사 생성 |
| PATCH | /api/audits/{id}/lines/{lineId} | 오인식 수량 수동 보정 |
| POST | /api/audits/{id}/confirm | 실사 확정 → 차이 나는 품목만 재고 조정 |
| POST | /api/audits/{id}/cancel | 실사 취소 |

## 설계 포인트 (발표용)

- **실사 = 제안값 검토 방식**: AI 카운팅은 오차가 있을 수 있으므로 바로 재고에 쓰지 않고
  DRAFT 세션에 담아 사람이 검토·보정 후 확정. 확정 시 차이(diff)가 있는 품목만
  `AUDIT_ADJUST` 이력과 함께 조정 → 폐기/도난/파손으로 인한 장부-실물 불일치 추적 가능.
- **신뢰도 임계값**: `vision.confidence-threshold` (기본 0.5) 미만 인식 결과는 자동 제외.
- **AI 교체 가능 구조**: `VisionAiClient` 인터페이스 뒤에 Gemini/Mock 구현체를 두고
  `vision.provider` 설정으로 스위칭 — OpenAI Vision 등으로 교체 시 서비스 로직 수정 불필요.
- **미등록 품목 자동 등록**: AI가 인식했지만 DB에 없는 품목은 "자동 등록 예정"으로 표시되고,
  실사 확정 시 신규 품목(분류: 미분류)으로 자동 등록되며 인식 수량이 초기 재고로 반영됨.
- **입고 화면에서 신규 품목 등록**: 목록에 없는 품목은 "신규 품목" 모드로
  등록과 입고를 한 번에 처리 (같은 이름이 이미 있으면 그 품목에 입고 — 중복 생성 방지).

## 패키지 구조

```
com.izacare
├── IzaCareApplication  진입점
├── domain      FoodItem, StockTransaction, StockAudit, Reservation, Member ...
├── repository  Spring Data JPA 리포지토리
├── service     InventoryService(입고/폐기), AuditService(AI 실사), NotificationService
├── vision      VisionAiClient 인터페이스 + Gemini/Mock 구현체
├── web         REST 컨트롤러 + 전역 예외 처리
└── dto         요청/응답 record
```

정적 화면은 `src/main/resources/static/` 아래에 있습니다 — `index.html`(SPA), `app.css`(디자인 시스템).
