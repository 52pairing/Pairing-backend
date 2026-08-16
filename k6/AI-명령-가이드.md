# k6 실행 명령 만들기 — AI 용 가이드

빌더 GUI(`builder/index.html`) 대신 **말로 시켜서 명령을 받는** 방식이다.
이 문서는 AI 가 읽고 명령 한 줄을 만들어 내기 위한 것이다.

- 실행 절차와 결과 해석은 **`부하테스트-가이드.md`** 에 있다. **고정값의 기준은 그 문서 0절이고, 4절이 그걸 그대로 옮긴 것이다**
- 빌더 GUI 사용법은 `가이드.md` 에 있다

---

## 0. 쓰는 법

AI 에게 이 파일을 읽히고 원하는 걸 말한다.

```
k6/AI-명령-가이드.md 읽고, 프론트 폴링 경로로 한계점 찾는 명령 만들어줘.
계정은 tester1@pairing.com / tester2@pairing.com 둘 다 프리랜서고 비밀번호는 Passw0rd! 야.
```

돌아오는 것은 **복사해서 바로 실행할 수 있는 한 줄**이다. 실행까지 맡기려면 "실행해서 결과도 보여줘" 라고 덧붙인다.

정보가 빠지면 AI 는 추측하지 말고 물어야 한다. 특히 **계정과 BASE_URL 은 추측 금지**다.

---

## 1. 명령 골격

```
k6 run k6/main.js -e KEY=값 -e KEY=값 ...
```

지켜야 할 것:

- **반드시 `Pairing-backend` 폴더에서 실행한다.** 리포트를 `k6/reports/` 상대경로로 쓴다.
- **한 줄로 만든다.** PowerShell 백틱이나 bash 역슬래시로 줄을 잇지 않는다. 복사 과정에서 깨지면 "설정 일부가 빠진 채 실행"이라는 알아채기 어려운 증상이 된다.
- 값은 작은따옴표로 감싼다. 이스케이프 규칙이 셸마다 다르다 — PowerShell 은 `'` → `''`, bash 는 `'` → `'\''`.
- 이 환경의 기본 셸은 PowerShell 이다. 달리 요청받지 않으면 PowerShell 기준으로 만든다.

---

## 2. 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 스프링부트 주소 |
| `ACCOUNTS` | 없음 | `이메일:비밀번호:역할` 쉼표 구분. 역할은 `CLIENT` 또는 `FREELANCER`. 비밀번호에 `:` 를 쓸 수 없다 |
| `APIS` | 공개 API 전부 | 카탈로그 `key` 쉼표 구분. 공백 넣지 않는다 |
| `PROFILE` | `smoke` | `smoke` / `load` / `stress` / `soak` |
| `VUS` | `10` | 목표 동시 접속자 수 |
| `DURATION` | `1m` | 계단 하나를 유지하는 시간 |
| `RAMP` | `30s` | 계단 사이 증감 시간 |
| `THINK_MS` | `500` | 반복 사이 대기. **기본값을 쓰지 말고 4절의 세트별 값을 반드시 명시한다** |
| `TESTID` | `run` | 리포트 파일명. 같으면 덮어쓴다 |
| `AI_BASE_URL` | `http://localhost:8000` | 파이썬 주소. AI 항목을 고를 때만 |
| `INTERNAL_API_KEY` | 없음 | 파이썬 내부 호출 키. AI 항목을 고를 때 필수 |
| `WS` | 없음 | `1` 이면 WebSocket 시나리오도 돈다 |
| `WS_VUS` | `5` | WebSocket 동시 접속 수 |
| `WS_HOLD` | `DURATION` 과 같음 | 연결 유지 시간. `3m` 처럼 단일 단위만 쓴다. `1h30m` 은 조용히 60초로 처리된다 |
| `P95_PUBLIC` | `500` | 비로그인 그룹 지연 기준(ms) |
| `P95_READ` | `1000` | 조회 그룹 기준 |
| `P95_WRITE` | `1500` | 쓰기 그룹 기준 |
| `P95_AI` | `30000` | AI 그룹 기준 |
| `MAX_FAIL_RATE` | `0.01` | 실패율 기준 |
| `CHAT_ROOM_ID` / `NEGOTIATION_ID` / `CONTRACT_ID` | 자동 탐색 | 직접 주면 목록 조회를 건너뛴다 |
| `INSECURE` | 없음 | `1` 이면 TLS 인증서 검증을 건너뛴다 |
| `ALLOW_REAL_AI` | 없음 | `1` 이면 스텁 확인을 무시한다. **웬만하면 쓰지 않는다** |

기본값과 같은 값은 넣지 않는다. 명령이 길수록 읽기 어렵고, 어디가 다른지 안 보인다.

---

## 3. API 카탈로그

`APIS` 에는 아래 `key` 만 쓸 수 있다. **없는 key 를 지어내면 k6 가 시작하자마자 예외로 죽는다.**

카탈로그가 바뀌었을 수 있으니, 확신이 없으면 먼저 실제 목록을 뽑아 확인한다.

```bash
node -e "global.window=global;require('./k6/builder/catalog.js');console.log(window.K6_CATALOG.map(e=>e.key).join(','))"
```

### 비로그인 13개 — 계정이 필요 없다

| key | 무엇 | 메모 |
| --- | --- | --- |
| `home_summary` | 메인 지표 | |
| `home_reviews` | 메인 노출 후기 | |
| `home_faqs` | 메인 FAQ | |
| `meta_fields` | 업종 목록 | 불변 마스터 데이터. 캐시 개선 효과를 보여주기 좋다 |
| `meta_banks` | 은행 목록 | 불변 마스터 데이터 |
| `meta_employees` | 사원수 구간 | |
| `meta_jobcats` | 직군 목록 | 불변 마스터 데이터 |
| `meta_jobroles` | 직무 목록 | 불변 마스터 데이터 |
| `meta_skills` | 스킬 목록 | 목록이 커서 직렬화 비용이 드러난다 |
| `meta_workcond` | 근무 조건 목록 | |
| `terms_list` | 약관 목록 | |
| `terms_docs` | 약관 본문 | |
| `grades_table` | 등급 기준표 | |

### 조회 36개 — 로그인 필요

역할 표기: `any` 아무 계정, `CLIENT`/`FREELANCER` 그 역할 계정이 최소 1개 있어야 한다.

| key | 무엇 | 역할 | 필요한 ID |
| --- | --- | --- | --- |
| `me` | 내 정보 | any | |
| `grade_me` | 내 등급 | any | |
| `notifs` | 알림 목록 | any | |
| `notifs_unread` | 안 읽은 알림 수 | any | 프론트 폴링 경로. 실제 트래픽 비중이 가장 높다 |
| `chatrooms` | 채팅방 목록 | any | |
| `chat_unread` | 안 읽은 메시지 수 | any | 프론트 폴링 경로 |
| `reviews_recv` | 받은 평가 | any | |
| `reviews_written` | 작성한 평가 | any | |
| `reviews_summary` | 평가 요약 | any | |
| `reviews_pending` | 작성 대기 평가 | any | |
| `contracts` | 계약 목록 | any | |
| `negos_mine` | 내 협상 목록 | any | |
| `negos_waiting` | 대기 협상 수 | any | |
| `settlements` | 내 정산 목록 | any | |
| `penalties` | 내 위약금 | any | |
| `pay_methods` | 결제수단 | any | |
| `withdraw_check` | 탈퇴 가능 여부 | any | |
| `cb_suggested` | 챗봇 추천 질문 | any | 고정 목록 반환. 순수 오버헤드 기준선으로 쓴다 |
| `cb_quota` | 챗봇 잔여 한도 | any | |
| `cb_messages` | 챗봇 오늘 이력 | any | |
| `inquiries_mine` | 내 1:1 문의 | any | |
| `fl_me` | 프리랜서 내 정보 | FREELANCER | |
| `fl_condition` | 희망 근무조건 | FREELANCER | |
| `fl_resume` | 이력서 | FREELANCER | 조인이 많다. N+1 이 있으면 여기서 드러난다 |
| `fl_resume_draft` | 이력서 임시저장 | FREELANCER | |
| `fl_matching_set` | 매칭 설정 | FREELANCER | |
| `fl_match_recv` | 받은 매칭 요청 | FREELANCER | |
| `cl_me` | 클라이언트 내 정보 | CLIENT | |
| `cl_projects` | 내 프로젝트 목록 | CLIENT | |
| `cl_proj_tabs` | 프로젝트 탭 카운트 | CLIENT | |
| `cl_match_reqs` | 보낸 매칭 요청 | CLIENT | |
| `chatroom_detail` | 채팅방 상세 | any | chatRoomId |
| `chat_messages` | 채팅 메시지 목록 | any | chatRoomId |
| `nego_detail` | 협상 상세 | any | negotiationId |
| `nego_messages` | 협상 대화 목록 | any | negotiationId |
| `contract_detail` | 계약 상세 | any | contractId |

**필요한 ID 칸이 채워진 5개는 5절의 제약을 먼저 읽는다.**

### 쓰기 4개 — 데이터가 실제로 남는다

| key | 무엇 | 필요한 ID | 메모 |
| --- | --- | --- | --- |
| `chat_send` | 채팅 메시지 전송 | chatRoomId | 메시지가 영구히 쌓인다. 협상이 끝난 방에서만 열린다(그 전이면 400) |
| `chat_read` | 채팅 읽음 처리 | chatRoomId | 멱등하지만 UPDATE 가 나간다 |
| `notifs_read_all` | 알림 전체 읽음 | | 첫 호출 뒤에는 갱신할 행이 없어 비용이 급감한다. 처리량 그래프를 오해하기 쉽다 |
| `nego_read` | 협상 읽음 처리 | negotiationId | |

### AI 4개 — 파이썬에 직접 붙는다

`INTERNAL_API_KEY` 와 `AI_BASE_URL` 이 필요하고, 파이썬이 스텁 모드여야 한다.

| key | 무엇 | 메모 |
| --- | --- | --- |
| `ai_chatbot` | 챗봇 답변 생성 | 가장 단순한 AI 경로. AI 부하의 기본 |
| `ai_negotiate` | 협상 제안 생성 | 프롬프트가 가장 길고 응답도 가장 크다 |
| `ai_contract` | 계약 문구 생성 | |
| `ai_embed_fl` | 프리랜서 임베딩 갱신 | **freelancer_id 1 의 벡터를 덮어쓴다.** 5절 참고 |

---

## 4. 요청을 설정으로 옮기는 규칙

### 목적 → 프로파일

| 사용자가 말한 것 | PROFILE | 비고 |
| --- | --- | --- |
| "설정 맞는지 확인", "일단 돌려보고 싶다" | `smoke` | VU 1 · 30초 고정. **VUS·DURATION 을 줘도 무시된다** |
| "몇 명까지 버티나", "한계점", "무릎" | `stress` | 목표의 25 → 50 → 75 → 100 → 150% 계단 |
| "개선 전/후 비교", "이 인원에서 기준 지키나" | `load` | 목표까지 올려 유지 |
| "오래 돌려보자", "누수" | `soak` | 고정 인원 장시간 |

### VU · 시간 — 고정값이다

**`부하테스트-가이드.md` 0절이 기준이고, 이 값들을 임의로 바꾸지 않는다.** 바꾸면 다른 사람 결과와 비교할 수 없다.

| 항목 | 고정값 |
| --- | --- |
| 목표 VU | **150** (S3 AI 세트만 30) |
| `stress` (한계점) | `DURATION=2m` `RAMP=30s` — 약 13분 |
| `load` (전/후 비교) | `DURATION=3m` `RAMP=30s` — 약 4분 |
| 계정 | 15개 (프리랜서 8 / 클라이언트 7) |
| 합격 기준 | 기본값 그대로 두고 **명령에 넣지 않는다** |

`stress` 총 실행 시간은 `(RAMP + DURATION) × 5 + RAMP` 다. 사용자가 "짧게"라고 하면 `DURATION=1m`(약 8분)까지만 줄인다.
그보다 짧으면 계단마다 표본이 모자라 무릎이 안 보인다.

### 생각 시간 — 고른 API 개수에서 나온다

한 반복에서 고른 API 를 **전부** 호출하므로, 개수가 바뀌면 같은 값이라도 부하가 몇 배씩 달라진다.
사용자 1명이 초당 0.85건을 만들도록 맞춘 값이 아래다. **API 를 바꾸면 생각 시간도 같이 바꾼다.**

| 고른 API 수 | THINK_MS |
| --- | --- |
| 1개 | `1000` |
| 4개 (S1) | `4500` |
| 6개 (S2) | `7000` |

`0` 은 쓰지 않는다. 서버가 아니라 내 PC 한계를 재게 된다. 사용자가 "최대 처리량"을 원해서 굳이 0 을 쓸 때는 그 사실을 말해 준다.

### 표준 세트 — 이 조합으로 만든다

| 세트 | APIS | THINK_MS | VU |
| --- | --- | --- | --- |
| **S1 공개** | `meta_skills,meta_jobroles,terms_list,home_summary` | `4500` | 150 |
| **S2 폴링 (대표)** | `me,notifs_unread,chat_unread,negos_waiting,chatrooms,notifs` | `7000` | 150 |
| **S3 AI** | `ai_chatbot` | `1000` | 30 |

사용자가 다른 조합을 원하면 만들어 주되, **표준 전/후 비교와 섞어서 해석할 수 없다**는 점을 알린다.

### 실행 이름

```
<세트>-<프로파일>-<상태>        예: s2-load-before, s2-load-after, s2-stress-before
```

같은 이름이면 리포트를 덮어쓴다.

---

## 5. 하드 룰 — 어기면 숫자가 틀리거나 데이터가 망가진다

**1. 운영·공용 서버를 대상으로 `write` 4개와 `ai_embed_fl` 을 돌리지 않는다.**
초당 수천 iteration 이 돈다. `chat_send` 는 실제 메시지를 수만 건 쌓고 되돌리려면 수동 DELETE 뿐이다.
`ai_embed_fl` 은 body 의 `freelancer_id` 가 1로 고정돼 있어 **프리랜서 1번의 이력서 벡터를 더미로 덮어쓴다.** 에러 없이 그 계정의 매칭 결과만 조용히 망가진다.
→ 버려도 되는 로컬 DB 에서만 쓴다. `BASE_URL` 이 localhost 가 아니면 이 5개는 넣지 않고, 사용자가 요청했더라도 먼저 확인한다.

**2. ID 가 필요한 8개는 계정 1개일 때만 쓴다.**
대상: `chatroom_detail`, `chat_messages`, `nego_detail`, `nego_messages`, `contract_detail`, `chat_send`, `chat_read`, `nego_read`.
`setup()` 이 **첫 번째 계정으로만** ID 를 찾아 모든 VU 에게 준다. 계정이 2개 이상이면 나머지 계정은 그 방의 참여자가 아니라 `NOT_PARTICIPANT` 로 막힌다. 403 은 DB 를 거의 안 타서 빠르므로 **성능이 좋아 보이는 가짜 숫자**가 나온다.
→ 이 8개를 넣으려면 `ACCOUNTS` 를 1개로 하거나, 두 계정이 같은 방의 당사자 쌍임을 사용자에게 확인한다. 아니면 이 8개를 빼고 만든다.

**3. `stress` 와 `WS=1` 을 같이 쓰지 않는다.**
VU 구간 통계가 인스턴스 전체 VU 수를 보기 때문에 WebSocket VU 가 더해져 계단이 밀리고 무릎 지점이 틀어진다.
→ WebSocket 은 `load` 나 `soak` 로 따로 돌린다.

**4. AI 를 넣으면 스텁을 확인시킨다.**
스크립트가 시작할 때 `AI_BASE_URL/metrics` 의 `gemini_stub_mode` 를 보고 0 이면 멈춘다. 이 안전장치를 `ALLOW_REAL_AI=1` 로 끄지 않는다.
또한 `AI_STUB_DELAY_MS` 를 파이썬에 설정하지 않으면 지연이 0 이라 `P95_AI=30000` 기준이 무의미하다. AI 명령을 만들 때는 파이썬을 어떤 지연으로 띄웠는지 함께 안내한다.

**5. 로그인 실패 후 같은 명령을 반복 실행하지 않는다.**
스크립트는 한 실행에서 3회 실패로 멈추지만, 서버는 IP 당 1시간에 20회를 센다. 넘기면 그 IP 가 **2시간 차단**되어 그날 테스트가 끝난다.
→ 실패가 나오면 재실행 전에 계정·비밀번호·역할·BASE_URL 부터 확인한다.

**6. 실제 사용자 계정을 쓰지 않는다.**
비밀번호가 명령줄과 셸 히스토리에 평문으로 남는다. 테스트 전용 계정만 쓰고, 문서나 코드에 값을 적지 않는다.

---

## 6. 만들고 나서 확인할 것

명령을 내놓기 전에 스스로 점검한다.

- [ ] `APIS` 의 key 가 전부 3절 목록에 있는가 (없으면 시작 즉시 예외)
- [ ] 로그인이 필요한 key 가 있는데 `ACCOUNTS` 가 비어 있지 않은가
- [ ] `CLIENT`/`FREELANCER` 전용 key 를 넣었다면 그 역할 계정이 있는가
- [ ] 5절 1~3번에 걸리지 않는가
- [ ] AI key 가 있으면 `INTERNAL_API_KEY` 와 `AI_BASE_URL` 이 들어갔는가
- [ ] 한 줄인가, 값이 따옴표로 감싸였는가
- [ ] `TESTID` 가 이전 실행과 겹치지 않는가 (겹치면 리포트를 덮어쓴다)
- [ ] `VUS`·`DURATION`·`RAMP`·`THINK_MS` 가 4절 고정값과 같은가. 특히 **고른 API 개수에 맞는 `THINK_MS`** 인가
- [ ] 전/후 비교라면 앞 실행과 `TESTID` 외에 다른 값이 하나도 다르지 않은가

처음 돌리는 설정이라면 **같은 명령의 `PROFILE=smoke` 판을 먼저 제안한다.** 30초면 계정과 주소가 맞는지 확인된다.

---

## 7. 레시피

값만 바꿔 쓰면 된다. 전부 PowerShell 기준 한 줄이다.

**설정 확인 (30초)**

```bash
k6 run k6/main.js -e BASE_URL=http://localhost:8080 -e ACCOUNTS='tester1@pairing.com:Passw0rd!:FREELANCER' -e APIS='me,notifs_unread' -e PROFILE=smoke -e TESTID=smoke
```

**S2 한계점 (13분) — 개선 전에 한 번. 여기서 고정 VU 를 확정한다**

```bash
k6 run k6/main.js -e BASE_URL=http://localhost:8080 -e ACCOUNTS='<15개 계정>' -e APIS='me,notifs_unread,chat_unread,negos_waiting,chatrooms,notifs' -e PROFILE=stress -e VUS=150 -e DURATION=2m -e RAMP=30s -e THINK_MS=7000 -e TESTID=s2-stress-before
```

**S2 개선 전/후 (4분) — VU 150 고정. 본 측정이다**

```bash
k6 run k6/main.js -e BASE_URL=http://localhost:8080 -e ACCOUNTS='<15개 계정>' -e APIS='me,notifs_unread,chat_unread,negos_waiting,chatrooms,notifs' -e PROFILE=load -e VUS=150 -e DURATION=3m -e RAMP=30s -e THINK_MS=7000 -e TESTID=s2-load-before
```

개선 후에는 **`TESTID` 만** `s2-load-after` 로 바꿔 다시 돌린다. 다른 값은 하나도 건드리지 않는다.
끝나면 `k6/compare.html` 에 두 json 을 넣는다.

**S1 공개 API — 계정이 필요 없다. 캐시 개선 효과가 가장 선명하다**

```bash
k6 run k6/main.js -e BASE_URL=http://localhost:8080 -e APIS='meta_skills,meta_jobroles,terms_list,home_summary' -e PROFILE=load -e VUS=150 -e DURATION=3m -e RAMP=30s -e THINK_MS=4500 -e TESTID=s1-load-before
```

**S3 AI — 파이썬 AI 서버의 동시 처리 한계.** 파이썬을 `AI_STUB_MODE=true AI_STUB_DELAY_MS=10000 AI_STUB_JITTER_MS=2000` 으로 띄운 뒤

```bash
k6 run k6/main.js -e AI_BASE_URL=http://localhost:8000 -e INTERNAL_API_KEY='<파이썬 .env 의 INTERNAL_API_KEY 값>' -e APIS='ai_chatbot' -e PROFILE=load -e VUS=30 -e DURATION=3m -e RAMP=30s -e THINK_MS=1000 -e TESTID=s3-load-before
```

지연을 10000 으로 고정한다. 챗봇 1회는 스텁을 2번 호출하므로(관련성 임베딩 + 답변 생성) 실제 응답이 20~24초가 되는데,
20000 으로 두면 40초가 되어 기본 기준 `P95_AI=30000` 을 무조건 넘긴다.

**WebSocket 동시 접속 — 표준 세트 밖이다.** `stress` 와 섞지 않는다

```bash
k6 run k6/main.js -e BASE_URL=http://localhost:8080 -e ACCOUNTS='<15개 계정>' -e APIS='me,notifs_unread' -e PROFILE=load -e VUS=20 -e DURATION=5m -e THINK_MS=2000 -e WS=1 -e WS_VUS=50 -e WS_HOLD=5m -e TESTID=ws-load-50
```

---

## 8. 결과 읽기

실행이 끝나면 세 개가 남는다.

| 출력 | 위치 |
| --- | --- |
| 텍스트 요약 | 터미널 |
| HTML 리포트 | `k6/reports/<TESTID>.html` |
| JSON | `k6/reports/<TESTID>.json` — `compare.html` 입력용 |

- **종료 코드 99 는 기준 미달**이지 실행 실패가 아니다. 결과 파일은 정상적으로 남아 있다.
- `stress` 로 돌렸으면 터미널의 **동시접속 구간별 표**에서 `← 한계` 줄을 본다. 실제로 감당하는 인원은 그 **앞 계단**이다.
- 리포트 헤더의 초록색 "기준 통과" 를 그대로 믿지 않는다. **요청이 0건인 그룹도 통과로 표시된다.** 그룹별 호출 건수를 같이 확인한다.
- "제외된 항목"에 뭐가 들어 있는지 반드시 읽는다. 데이터가 없어서 빠진 API 는 측정되지 않은 것이다.
- 서버 쪽 원인(톰캣 스레드, HikariCP 대기, GC)은 Grafana 를 같은 시간대로 맞춰 본다.

---

## 9. 자주 틀리는 것

| 증상 | 원인 |
| --- | --- |
| `알 수 없는 API key` 로 즉시 종료 | 3절에 없는 key. 카탈로그를 다시 뽑아 확인한다 |
| 리포트가 저장되지 않는다 | `Pairing-backend` 폴더가 아닌 곳에서 실행했다 |
| 로그인 실패로 중단 | 역할까지 맞아야 한다. 같은 이메일이라도 역할이 다르면 다른 계정이다 |
| p95 가 비현실적으로 좋다 | 403/404 를 재고 있을 수 있다. 실패율과 "제외된 항목"을 먼저 본다 |
| VU·시간 설정이 안 먹는다 | `PROFILE=smoke` 는 VU 1 · 30초 고정이다 |
| AI 가 스텁이 아니라며 멈춘다 | 파이썬을 `AI_STUB_MODE=true` 로 다시 띄운다 |

카탈로그를 고쳤다면 검사를 돌린다. CI 도 PR 에서 같은 것을 본다.

```bash
node k6/tools/check-catalog.mjs
```
