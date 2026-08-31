# IzaCare — 이자카야 매장관리

Figma 디자인을 Spring Boot로 구현한 매장관리 웹앱입니다.
재고(입고 / 폐기 / 실사)를 중심으로 예약·근태·일보·공지까지 한 화면에서 다룹니다.
화면은 순수 HTML + JS + 공용 `app.css` 단일 페이지 구조입니다 (프런트 프레임워크 없음).

## 실행 방법

```bash
./gradlew bootRun        # 또는 gradle bootRun
```

- 접속: http://localhost:8080 (로그인 필요 — 데모 계정: 사장님 boss/1234, 알바생 staff/1234, 승인 대기 pend/1234)
- 데이터는 `./data/` 아래에 남습니다 (H2 파일 DB + 실사 사진). 서버를 껐다 켜도 유지되며,
  **초기 상태로 되돌리려면 `data/` 디렉터리를 지우고** 다시 실행하면 됩니다 (.gitignore 등록됨).
- H2 콘솔: 기본 **꺼짐**. 로컬에서 볼 때만 `H2_CONSOLE=true ./gradlew bootRun` 으로 켜면
  http://localhost:8080/h2-console 접속 가능
  (JDBC URL: `jdbc:h2:file:./data/izacare` · 사용자 `sa` · 비밀번호 없음).
  앱이 파일을 잠그므로 외부 DB 툴로는 앱 실행 중에 붙을 수 없다 — 이 콘솔을 쓰거나 앱을 끄고 열 것.
  인증 없이 DB 전체를 열람·수정할 수 있으므로 배포 환경에서는 켜지 말 것.
- 시연용 초기 데이터 9개 품목이 자동 등록됩니다.

## AI 실사 (핵심 기능)

사진 촬영(여러 장) → AI 인식 → 검토/보정 → 확정(재고 반영) 4단계 흐름.

- **사진 여러 장을 한 실사로**: 냉장고·주류고·창고를 나눠 찍어 한 번에 올립니다(최대 8장).
  한 번의 AI 호출로 전체를 보고 품목별 총 개수를 냅니다. 같은 품목이 여러 사진에 나오면
  수량은 합산하고 신뢰도는 낮은 쪽을 남겨 한 줄로 병합합니다 — 중복 라인이 남으면
  확정 시 같은 품목을 두 번 조정해 재고가 틀어지기 때문입니다.
- **업로드 전 축소**: 브라우저에서 긴 변 1280px JPEG로 줄여 보냅니다. 폰 사진 원본(3~5MB)은
  업로드도 AI 호출도 느린데, 병 개수를 세는 데는 이 해상도로 충분합니다.
- **사진 원본 보관**: 실사에 쓰인 사진은 `vision.image-dir`(기본 `./data/audit-images`)에
  남습니다. 수량 조정의 근거이므로 버리지 않습니다 — 나중에 수량 분쟁이 나면 이걸 봅니다.

- **API 키 입력 위치: `src/main/resources/secret.yml`** — 이 파일의 `api-key` 값에
  Google AI Studio에서 발급받은 키를 붙여넣으면 Gemini 연동이 활성화됩니다.
  모델은 기본 `gemini-3.5-flash` — 최신 `gemini-3.7-flash`는 혼잡으로 503이 잦아 기본값에서 뺐습니다.
  바꾸려면 `GEMINI_MODEL` 환경변수를 쓰세요.
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
| POST | /api/audits/scan | 사진 업로드(`images`, 여러 장) → AI 인식 → DRAFT 실사 생성 |
| GET | /api/audits/{id}/images/{index} | 실사에 쓰인 사진 원본 (조정 근거) |
| PATCH | /api/audits/{id}/lines/{lineId} | 오인식 수량 수동 보정 |
| POST | /api/audits/{id}/confirm | 실사 확정 → 차이 나는 품목만 재고 조정 |
| POST | /api/audits/{id}/cancel | 실사 취소 |
| POST | /api/attendance/clock | 출퇴근 기록 (출근 시 `latitude`/`longitude` 선택) |
| PATCH | /api/store/attendance-location | 매장 좌표·반경 설정 (사장님) |
| POST · DELETE | /api/store/attendance-ip | 현재 접속 IP를 매장 Wi-Fi로 등록 / 해제 (사장님) |

## 출근 위치 확인

알바가 집에서 출근을 찍는 걸 막기 위한 장치. **차단이 아니라 기록**한다.

- **GPS와 매장 Wi-Fi IP 중 하나만 맞아도 정상**으로 본다. 실내·지하는 GPS가 안 잡히고,
  LTE로 접속하면 IP가 안 맞으므로 서로의 빈틈을 메운다.
- 둘 다 못 맞춰도 **출근은 그대로 처리**하고 `⚠ 매장 밖에서 출근 · 1.2km` 처럼 기록에 남겨
  사장님 대시보드 "근무 중" 목록에 표시한다. GPS 오차로 진짜 출근한 직원이 못 찍으면
  급여 문제가 되기 때문 — 억지력은 "못 찍게 막는다"가 아니라 "찍히면 남는다"에서 나온다.
- **좌표는 저장하지 않는다.** 매장까지의 거리(m)와 판정 결과만 남긴다 — 판단에 필요한 건
  거리뿐이고, 좌표를 보관하면 근로자 개인위치정보를 다루는 셈이 되기 때문.
- 설정: 가게 설정 화면에서 **매장 안에서, 매장 Wi-Fi에 연결한 채** "현재 위치로 설정" +
  "현재 IP로 설정"을 누른다. 기본 반경 200m — 실내·지하는 GPS 오차가 커서 넉넉히 잡았으니
  매장에서 실측해 조이는 게 좋다.
- **한계**: 브라우저 위치 API는 https(또는 localhost)에서만 동작하므로 배포 시 HTTPS가 필요하다.
  GPS는 mock location 앱으로 위조할 수 있어 "무심코 집에서 찍기"를 막을 뿐, 작정한 위조는 못 막는다.

## 설계 포인트 (발표용)

- **실사 = 제안값 검토 방식**: AI 카운팅은 오차가 있을 수 있으므로 바로 재고에 쓰지 않고
  DRAFT 세션에 담아 사람이 검토·보정 후 확정. 확정 시 차이(diff)가 있는 품목만
  `AUDIT_ADJUST` 이력과 함께 조정 → 폐기/도난/파손으로 인한 장부-실물 불일치 추적 가능.
- **신뢰도 임계값**: `vision.confidence-threshold` (기본 0.5) 미만 인식 결과는 자동 제외.
- **AI 교체 가능 구조**: `VisionAiClient` 인터페이스 뒤에 Gemini/Mock 구현체를 두고
  `vision.provider` 설정으로 스위칭 — OpenAI Vision 등으로 교체 시 서비스 로직 수정 불필요.
- **미등록 품목 자동 등록**: AI가 인식했지만 DB에 없는 품목은 "자동 등록 예정"으로 표시되고,
  실사 확정 시 신규 품목(분류: 미분류)으로 자동 등록되며 인식 수량이 초기 재고로 반영됨.
- **스키마 변경 주의**: 파일 DB로 바꾼 뒤로는 `ddl-auto=update`가 기존 행이 있는 테이블에
  기본값 없는 `NOT NULL` 컬럼을 붙이지 못한다. 새 필수 컬럼은 `columnDefinition`으로
  DB 기본값을 주거나(현재 방식), 컬럼이 늘어나면 Flyway를 도입할 것.
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

## 화면을 새로 만들 때 (중요)

`index.html`에서 HTML을 만들 때는 **반드시 ``html`...` `` 태그드 템플릿**을 쓰세요.
삽입되는 `${값}`을 자동으로 이스케이프해 XSS를 막습니다. 그냥 백틱으로 만들어
`innerHTML`에 넣으면 사용자가 입력한 이름·공지·품목명으로 스크립트가 실행됩니다.

```js
el.innerHTML = html`<span>${item.name}</span>`;   // O
el.innerHTML = `<span>${item.name}</span>`;       // X — XSS
```

`onclick="fn('${값}')"` 처럼 **속성 안의 JS 문자열에는 사용자 입력을 넣지 마세요.**
브라우저가 속성을 먼저 디코드하므로 이스케이프로 막히지 않습니다.
`data-*` 속성에 담고 `this.dataset`으로 읽으세요.

리버스 프록시 뒤에 배포할 때만 `app.trust-proxy=true`를 켜세요.
켜면 `X-Forwarded-For`를 클라이언트 IP로 신뢰하는데, 프록시가 없으면
누구나 헤더를 위조해 로그인 시도 횟수 제한을 무력화할 수 있습니다.
