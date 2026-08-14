# k6 부하 테스트

스프링부트와 AI 서버에 부하를 걸고, 결과를 HTML 리포트로 남긴다.
**k6 는 로컬 PC 에서 실행한다.** 결과는 파일로만 남고 모니터링 서버에는 아무것도 보내지 않는다.

```
k6/
├── builder/index.html    ← 여기서 시작한다. 실행 명령을 만들어 준다
├── compare.html          개선 전/후 비교
├── endpoints.js          API 카탈로그 (단일 진실 원본)
├── main.js               시나리오 진입점
├── lib/                  로그인·ID 조회·STOMP·리포트
├── tools/                카탈로그 생성기와 검사기
└── reports/              실행 결과가 여기에 쌓인다
```

## 시작하기

1. **k6 설치** — `winget install k6` 또는 https://k6.io/docs/get-started/installation/
2. **`k6/builder/index.html` 을 브라우저로 열기** (그냥 더블클릭. 서버 필요 없음)
3. 대상 주소·테스트 계정·API·부하를 고른다
4. 만들어진 명령을 복사해서 **`Pairing-backend` 폴더에서** 실행
5. `k6/reports/<실행이름>.html` 을 열어 결과를 본다

리포트 경로가 상대경로라서 반드시 `Pairing-backend` 폴더에서 실행해야 한다.

---

## 반드시 알아야 할 것 세 가지

### 1. 계정은 여러 개 준비한다

이 서비스는 **로그인하면 그 계정의 이전 세션을 즉시 끊는다**
([AuthController](../src/main/java/com/pairing/auth/presentation/api/AuthController.java) —
*"성공 시 ... 이전 기기의 세션은 즉시 끊깁니다"*).

그래서 VU 루프 안에서 로그인하면 VU 들이 서로의 토큰을 죽여서 401 폭풍이 난다. 그래프만 보면
"부하를 받으니 인증이 실패한다"처럼 보이는데, 실제로는 스크립트가 스스로를 로그아웃시킨 것이다.

**이 스크립트는 `setup()` 에서 계정당 정확히 한 번만 로그인하고 토큰을 VU 에 나눠 준다.**
따라서 동작은 계정 1개로도 되지만, 그러면 모든 VU 가 같은 사용자 데이터를 보게 된다.
계정 단위 캐시나 락이 있으면 실제와 다른 숫자가 나온다 — **VU 의 1/5 이상**을 권한다.

역할이 필요한 API(`CLIENT 전용`, `FREELANCER 전용`)를 고르면 그 역할 계정이 최소 하나는 있어야 한다.
없으면 그 API 는 실행에서 빠지고 리포트의 "제외된 항목"에 남는다.

### 2. 비밀번호를 틀리면 IP 가 2시간 막힌다

[AuthSettings](../src/main/java/com/pairing/auth/settings/AuthSettings.java) 기준값:

| 항목 | 값 |
| --- | --- |
| 계정별 연속 실패 잠금 | 5회 |
| IP 기준 실패 상한 | 20회 / 1시간 |
| IP 차단 유지 | **2시간** |

k6 는 단일 IP 다. 틀린 비밀번호로 VU 20 을 돌리면 몇 초 만에 한도를 넘기고 그날 테스트가 끝난다.
그래서 이 스크립트는 **로그인 실패 3번이면 즉시 중단**한다. 재시도도 하지 않는다 —
로그인 실패는 부하 문제가 아니라 설정 문제다.

### 3. AI 는 파이썬을 직접 때린다

스프링 경유로는 AI 부하 테스트가 **안 된다.** 스프링이 쿼터로 막는다.

| 경로 | 막는 것 |
| --- | --- |
| 챗봇 | 계정당 **하루 10회** (`SupportController`) |
| 매칭 재추천 | 무료 횟수 제한 |
| 협상 응답 | 라운드 상태 기계에 묶여 반복 호출 불가 |

쿼터는 스프링에 있고 LLM 호출은 파이썬에 있다. **AI 스텁을 켜도 스프링 쿼터는 그대로다.**
그래서 AI 용량은 파이썬(`AI_BASE_URL`)에 직접 붙어야 측정된다. 계측을 넣은 서버도 이쪽이다.

인증은 `X-Internal-Api-Key` 다. 파이썬은 VPC 내부 전용이라 로컬에서 부하를 걸려면
파이썬을 로컬에 띄우거나 포트포워딩이 필요하다.

---

## AI 를 테스트할 때: 스텁 모드를 켠다

파이썬을 `AI_STUB_MODE=true` 로 띄운다. 그러면 Gemini 를 부르지 않고 스키마에 맞는 더미를 만들어
돌려준다. 재시도·키 전환·메트릭 경로는 그대로 지나가므로 Grafana 대시보드도 평소처럼 채워진다.

```bash
AI_STUB_MODE=true AI_STUB_DELAY_MS=20000 AI_STUB_JITTER_MS=3000 uvicorn app.main:app
```

| 변수 | 의미 |
| --- | --- |
| `AI_STUB_DELAY_MS` | 더미가 응답하기까지 기다리는 시간. **스프링 스레드 점유를 재현하는 다이얼이다** |
| `AI_STUB_JITTER_MS` | 지연 흔들림. 0 이면 지연이 한 버킷에만 쌓여 p50/p95/p99 가 구분되지 않는다 |
| `AI_STUB_FAIL_RATE` | 시도마다 판정하는 실패율. 재시도·서킷브레이커를 볼 때만 쓴다 |

지연 값을 이렇게 쓴다.

| 값 | 무엇을 보는가 |
| --- | --- |
| `0` | AI 대기가 없을 때의 순수 인프라 상한 |
| `3000` | 빠른 LLM |
| `20000` | 느린 LLM. **톰캣 스레드 고갈이 이 근처에서 드러난다** |
| `125000` | 스프링 `AI_TIMEOUT_MS`(120초) 초과 → 타임아웃·취소 경로 검증 |

**실제 Gemini 로 돌리면 안 되는 이유**: 무료 티어 쿼터가 소진되고 키가 전부 쿨다운에 들어가면
그 시점부터 전부 실패한다. 그러면 측정하려던 성능 대신 "쿼터가 언제 떨어지는가"를 재게 되고,
비용도 든다. **k6 가 시작할 때 `gemini_stub_mode` 를 확인하고, 스텁이 아니면 멈춘다.**
의도적으로 실제 호출을 하려면 `-e ALLOW_REAL_AI=1` 을 준다.

스텁을 켜 둔 채 잊는 사고는 네 겹으로 막혀 있다 — prod 기동 거부 / 기동 로그 경고 /
`gemini_stub_mode` 게이지 / 더미 문자열에 박힌 `[더미]` 표시.

---

## 부하 프로파일

| 프로파일 | 형태 | 무엇을 보는가 |
| --- | --- | --- |
| `smoke` | 1 VU · 30초 | 스크립트가 맞는지. 부하가 아니다 |
| `load` | 목표 VU 까지 올려 유지 | **개선 전/후 숫자를 뽑는 기본** |
| `stress` | 목표의 25→50→75→100→150% 계단 | **무릎 지점.** "몇 VU 까지 버티는가"가 개선을 가장 잘 보여준다 |
| `soak` | 낮은 부하 장시간 | 커넥션 누수·힙 증가 |

`stress` 의 총 실행 시간은 `(RAMP + DURATION) × 5 + RAMP` 다. `DURATION=1m` 이면 약 6분.

### 합격 기준

그룹별로 나눠 둔다. AI 를 조회와 같은 기준으로 묶으면 **AI 하나가 전체 p95 를 끌어올려서
조회 성능이 나빠져도 그래프에 안 보인다.**

| 기본값 | 환경변수 |
| --- | --- |
| 비로그인 p95 < 500ms | `P95_PUBLIC` |
| 조회 p95 < 1000ms | `P95_READ` |
| 쓰기 p95 < 1500ms | `P95_WRITE` |
| AI p95 < 30000ms | `P95_AI` |
| 실패율 < 1% | `MAX_FAIL_RATE` |

기준을 넘기면 k6 가 종료 코드 99 로 끝난다.

---

## 개선 전/후 비교

1. 개선 전에 `-e TESTID=before` 로 한 번 돌린다
2. 코드를 고친다
3. **같은 설정으로** `-e TESTID=after` 로 다시 돌린다
4. `k6/compare.html` 을 열고 `reports/before.json` 과 `reports/after.json` 을 넣는다

조건(VU·유지 시간·고른 API·계정 수)이 다르면 비교 페이지가 경고를 띄운다. 조건이 다르면
표에 보이는 차이는 성능 개선이 아니라 조건 차이다.

`TESTID` 가 같으면 리포트를 덮어쓴다. 여러 번 돌릴 때는 `before-1`, `before-2` 처럼 구분한다.

### 서버 쪽은 Grafana 로 본다

리포트 숫자는 **클라이언트가 기다린 시간**이다. 네트워크 왕복과 대기열이 다 들어 있다.
서버가 처리한 시간, JVM 힙, 톰캣 스레드, HikariCP 대기는 Grafana 의 스프링부트 성능
대시보드를 같은 시간대로 맞춰 봐야 한다. **두 값의 차이가 네트워크·큐 대기 시간이다.**

부하 테스트로 드러날 가능성이 높은 것들:

- **AI 응답 대기가 톰캣 스레드를 점유** — `AI_STUB_DELAY_MS=20000` 으로 돌리면서
  `tomcat_threads_busy` 를 본다. AI 와 무관한 API 까지 같이 느려지면 이게 원인이다
- **HikariCP 풀 대기** — `hikaricp_connections_pending` 이 0 을 넘으면 풀이 병목이다
- **불변 마스터 데이터에 캐시 없음** — `meta_*`, `terms_*` 는 값이 바뀌지 않는다
- **WebSocket 이벤트 유실** — 스프링 태스크가 2대 이상이면 재현된다.
  브로커가 인메모리 `SimpleBroker` 라서 인스턴스 간 push 가 전달되지 않는다
  ([StompWebSocketConfig](../src/main/java/com/pairing/global/config/StompWebSocketConfig.java) 주석)

---

## API 를 추가·수정할 때

1. [`endpoints.js`](endpoints.js) 에 항목을 추가한다 (단일 진실 원본)
2. 빌더 GUI 용 목록을 다시 만든다:

```bash
k6 run k6/tools/dump-catalog.js
```

3. 검사한다:

```bash
node k6/tools/check-catalog.mjs && node k6/tools/test-builder.mjs
```

`check-catalog.mjs` 는 두 가지를 본다 — `catalog.js` 가 `endpoints.js` 와 맞는지, 그리고
**카탈로그의 경로가 실제 컨트롤러에 존재하는지.** 두 번째가 더 중요하다: 경로에 오타가 있으면
404 를 받는데 404 는 DB 를 안 타서 아주 빠르다. 그래서 성능이 좋아진 것처럼 보인다.

CI 가 PR 에서 이 두 검사를 돌린다.

---

## 직접 실행할 때 쓰는 변수

빌더 GUI 가 알아서 넣어 주지만, 손으로 돌릴 때 참고용.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 스프링부트 주소 |
| `AI_BASE_URL` | `http://localhost:8000` | 파이썬 주소 (AI 항목을 고를 때) |
| `INTERNAL_API_KEY` | — | 파이썬 내부 호출 키 |
| `ACCOUNTS` | — | `이메일:비밀번호:역할` 쉼표 구분. 비밀번호에 `:` 는 못 쓴다 |
| `APIS` | 비로그인 전부 | 카탈로그의 `key` 쉼표 구분 |
| `PROFILE` | `smoke` | `smoke` / `load` / `stress` / `soak` |
| `VUS` | `10` | 목표 동시 사용자 |
| `DURATION` | `1m` | 유지 시간 |
| `RAMP` | `30s` | 증감 구간 |
| `THINK_MS` | `500` | 반복 사이 대기. 0 이면 서버가 아니라 k6 한계를 재게 된다 |
| `TESTID` | `run` | 리포트 파일명 |
| `WS` | — | `1` 이면 WebSocket 시나리오도 돌린다 |
| `WS_VUS` | `5` | WebSocket 동시 접속 수 |
| `WS_HOLD` | `DURATION` | 연결 유지 시간 |
| `CHAT_ROOM_ID` 등 | 자동 탐색 | 직접 지정하면 목록 조회를 건너뛴다 |
| `INSECURE` | — | `1` 이면 TLS 인증서 검증을 건너뛴다 |

```bash
k6 run k6/main.js -e BASE_URL=http://localhost:8080 -e ACCOUNTS='a@b.com:pw:FREELANCER' -e APIS='me,notifs_unread' -e PROFILE=load -e VUS=20 -e DURATION=3m -e TESTID=before
```
