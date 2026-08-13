# 프리랜서 "프로젝트 제안" 연동 가이드 (매칭 · 협상)

대상 화면 3개

1. **프로젝트 제안 목록** — 탭 4개, 카드 리스트
2. **제안 상세보기** — 프로젝트 정보 + 상세 정보 + 수락/거절
3. **수락 → 협상방 진입** — 협상 시작부터 타결까지

작성 기준: `origin/develop` (2026-08-13). 도메인 소유자는 매칭 4번 · 협상 담당이며, 이 문서는
프로젝트/계약/정산 담당(3번)이 **코드를 직접 읽고** 정리한 것이다.

> 이 문서에서 ⚠️ 로 표시한 항목은 **서버가 아직 못 주는 값**이다. 8장에 모아 두었다.
> 프론트가 임의로 계산해서 채우면 안 된다 — 협상방 숫자와 어긋난다.

---

## 0. 공통 규약

### 0.1 Base URL

```
/api/v1
```

### 0.2 응답 봉투

**모든** 성공 응답이 이 형태다. 실제 데이터는 항상 `data` 안에 있다.

```json
{
  "timestamp": "2026-08-13T04:12:33.512Z",
  "status": 200,
  "code": "REQUESTS_FOUND",
  "message": "조회에 성공했습니다.",
  "data": { }
}
```

`code` 는 성공 코드다. 에러 코드(`MT_003` 등)와 다르니 성공/실패 판정에 쓰지 말고 **HTTP status** 로 판단할 것.

### 0.3 페이지 응답

목록 API 의 `data` 는 이 모양이다.

```json
{
  "content": [ ],
  "page": 0,
  "size": 10,
  "totalElements": 35,
  "totalPages": 4,
  "first": true,
  "last": false
}
```

### 0.4 인증

JWT 필요. `accountId` 는 서버가 토큰에서 꺼내므로 **요청에 절대 담지 않는다.**

역할 가드가 걸린 엔드포인트가 있다.

| 엔드포인트 | 역할 |
|---|---|
| `GET /matchings/requests/received` | `FREELANCER` |
| `POST /matchings/requests/{id}/acceptance` | `FREELANCER` |
| `POST /matchings/requests/{id}/rejection` | `FREELANCER` |
| `GET /matchings/requests/{id}` | **없음** (당사자면 통과) |
| `GET /matchings/positions/{id}/candidates` | `CLIENT` |
| `POST /matchings/requests` | `CLIENT` |
| `POST /matchings/positions/{id}/rerecommendations` | `CLIENT` |
| 협상 API 전체 | **없음** (당사자면 통과) |

역할이 안 맞으면 `403 ACCESS_DENIED` 다. 클라이언트 계정으로 제안 목록을 부르면 이 에러가 난다.

### 0.5 에러 응답

```json
{
  "timestamp": "2026-08-13T04:12:33.512Z",
  "status": 400,
  "code": "MT_016",
  "message": "응답 기한이 지나 요청이 자동 만료되었습니다."
}
```

`code` 로 분기하고 `message` 를 그대로 토스트에 띄우면 된다. 전체 목록은 7장.

---

## 1. 화면 ① 프로젝트 제안 목록

### 1.1 API

```
GET /api/v1/matchings/requests/received?tab=ALL&page=0&size=10
```

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `tab` | enum | `ALL` | `ALL` · `REVIEWING` · `NEGOTIATING` · `CLOSED` |
| `page` | int | `0` | 0-base |
| `size` | int | `10` | |

응답: `PageResponse<MatchingRequestResponse>`

### 1.2 탭 매핑

탭 하나가 여러 상태를 묶는다. **프론트가 필터링하지 않는다** — `tab` 만 넘기면 서버가 거른다.

| 탭 | enum | 포함 상태 |
|---|---|---|
| 전체 | `ALL` | 필터 없음 |
| 검토 중 | `REVIEWING` | `REQUEST_PENDING` |
| 협상 중 | `NEGOTIATING` | `ACCEPTED`, `NEGOTIATING`, `CONTRACT_PENDING` |
| 종료됨 | `CLOSED` | `REJECTED`, `NEGOTIATION_FAILED`, `CONTRACTED`, `IN_PROGRESS`, `COMPLETION_PENDING`, `CLOSED`, `TERMINATED` |

> **주의** — `CONTRACTED` / `IN_PROGRESS` / `COMPLETION_PENDING` 은 "계약해서 일하는 중"인데
> **종료됨** 탭에 들어간다. 매칭 요청 관점에서 "이 요청은 처리가 끝났다"는 뜻이다.
> 화면 문구를 "종료"로 쓰면 오해가 생기니 카드 안 상태 칩은 `status.label` 로 따로 표시할 것.

### 1.2.1 탭 이동 — 어떤 액션이 카드를 어디로 옮기나

카드는 **상태가 바뀌면 자동으로 다른 탭으로 옮겨간다.** 프론트가 옮기는 게 아니라, 다음
조회에서 그 탭 결과에 안 잡히고 다른 탭에 잡히는 방식이다.

| # | 액션 | 누가 | `status` 변화 | 탭 이동 |
|---|---|---|---|---|
| 1 | 매칭 요청 발송 | 클라이언트 | → `REQUEST_PENDING` | (신규) → **검토 중** |
| 2 | **거절** | 프리랜서 | `REQUEST_PENDING` → `REJECTED` (`DIRECT_REJECT`) | 검토 중 → **종료됨** |
| 3 | **3일 경과** | 시스템 | `REQUEST_PENDING` → `REJECTED` (`EXPIRED`) | 검토 중 → **종료됨** |
| 4 | **수락** | 프리랜서 | `REQUEST_PENDING` → `ACCEPTED` → `NEGOTIATING` | 검토 중 → **협상 중** |
| 5 | 협상 타결 | 협상 도메인 | `NEGOTIATING` → `CONTRACT_PENDING` | 협상 중 유지 |
| 6 | 협상 결렬·포기 | 협상 도메인 | `NEGOTIATING` → `NEGOTIATION_FAILED` | 협상 중 → **종료됨** |
| 7 | 양측 서명 완료 | 계약 도메인 | `CONTRACT_PENDING` → `CONTRACTED` | 협상 중 → **종료됨** |
| 8 | 전원 착수금 결제 | 프로젝트 | `CONTRACTED` → `IN_PROGRESS` | 종료됨 유지 |
| 9 | 클라 완료 처리 | 프로젝트 | `IN_PROGRESS` → `COMPLETION_PENDING` | 종료됨 유지 |
| 10 | 성공보수 결제 | 정산 | `COMPLETION_PENDING` → `CLOSED` | 종료됨 유지 |

**2·3·6 은 되돌릴 수 없다.** 종결 상태(`REJECTED` · `NEGOTIATION_FAILED` · `TERMINATED` · `CLOSED`)로
간 요청은 어떤 경로로도 다시 앞으로 못 간다.

#### ⚠️ `ACCEPTED` 는 화면에 거의 안 나온다

수락 API 한 번에 `REQUEST_PENDING → ACCEPTED → NEGOTIATING` 까지 **같은 트랜잭션에서** 진행된다.
그래서 **수락 응답의 `status` 는 `ACCEPTED` 가 아니라 `NEGOTIATING`** 이다.

조건 불일치가 0개면 협상이 그 안에서 **즉시 타결**되면서 `CONTRACT_PENDING` 까지 올라간다.
이때는 수락 응답이 바로 `CONTRACT_PENDING` 으로 온다.

```js
const { data } = await api.post(`/matchings/requests/${id}/acceptance`);
// data.data.status === 'NEGOTIATING'       (보통)
// data.data.status === 'CONTRACT_PENDING'  (즉시 타결)
```

**`status === 'ACCEPTED'` 를 기대하는 분기를 짜면 안 된다.** 탭 판정은 서버에 맡기고,
화면 분기는 `NEGOTIATING` 과 `CONTRACT_PENDING` 을 기준으로 세운다.

#### 수락·거절 직후 화면 처리

**검토 중** 탭에서 수락하거나 거절하면 **그 카드는 현재 탭에서 사라져야 한다.**

응답으로 갱신된 `MatchingRequestResponse` 가 오므로 두 가지 방법이 있다.

```js
// (a) 현재 탭이 ALL 이면 — 카드만 교체
setItems(prev => prev.map(it =>
  it.requestId === updated.requestId ? updated : it));

// (b) 현재 탭이 REVIEWING 이면 — 카드를 빼고 탭 카운트를 갱신
setItems(prev => prev.filter(it => it.requestId !== updated.requestId));
refetchTabCounts();
```

전체 목록을 다시 부르면 페이지가 앞으로 밀려 스크롤이 튄다. **(a)/(b) 로 로컬 갱신하고
탭 카운트만 따로 다시 받는 것을 권한다.**

#### 협상 중 → 종료됨 (7번)이 특히 헷갈린다

계약서에 **양측이 서명하면** 카드가 **협상 중에서 사라져 종료됨으로 간다.** 사용자 입장에서는
"이제 막 계약했는데 왜 종료?" 로 읽힌다.

`status.label` 이 `계약 완료` 이므로 **종료됨 탭 안에서도 카드 칩은 "계약 완료"로 찍고**,
계약 상세로 가는 링크를 주는 편이 낫다. 계약 이후 흐름은 별도 화면(내 계약)이 담당한다.

#### `TERMINATED` (중도 종료)

enum 에는 있지만 **이 상태로 보내는 코드가 아직 없다.** 중도 파기 기능이 미구현이다.
지금은 화면에 나오지 않으니 대응하지 않아도 되고, `status.label` 만 매핑해 두면 된다.

### 1.3 카드 필드 매핑

`MatchingRequestResponse` 한 건이 카드 하나다.

| 화면 | 필드 | 비고 |
|---|---|---|
| D-1 남음 | `expiresAt` | 아래 1.4 |
| "3일 내 응답 없으면 자동 거절 처리됩니다" | — | 고정 문구. `status === 'REQUEST_PENDING'` 일 때만 |
| B2B 주문 관리 서비스 리뉴얼 | `projectTitle` | |
| **AI 94%** | ⚠️ **없음** | 8장 ① |
| 주식회사 오이랩 | `companyName` | |
| IT/소프트웨어 · 50-100명 | `companyProfile` | **이미 조립된 문자열.** 프론트가 만들지 말 것 |
| 직군 프론트엔드 개발자 | `jobRole` | enum → 라벨 변환 필요 |
| 예산 **월** 6,000,000원 | ⚠️ `budgetAmount` 는 총액 | 8장 ② |
| 기간 4개월 | `periodLabel` | 이미 조립된 문자열 |
| 시작일 2026.09.01 | `startDesiredDate` | `yyyy-MM-dd` |
| 근무 재택 · 풀타임 | `workLabel` | 이미 조립된 문자열 |
| 경력 3년 이상 | `minCareerYears` | 숫자만 옴. `+"년 이상"` 은 프론트 |
| React / Next.js / TypeScript | `skills` | `SkillCode` enum 배열 |
| 수신일 2026.07.20 | `requestedAt` | |
| 라운드 4/15 | `currentRound` / `maxRound` | 협상 전에는 둘 다 `null` |
| 새 AI 제안 1건 | `newProposalCount` | 협상 전에는 `null` |
| 협상방 입장 → 이동 | `negotiationId` | 수락 후에만 값이 있음 |

`companyProfile`, `workLabel`, `periodLabel` **세 개는 서버가 이미 문자열로 조립해서 보낸다.**
enum 을 받아서 프론트가 합치는 게 아니다.

### 1.4 D-day 배지

```js
const days = differenceInCalendarDays(new Date(expiresAt), new Date());
// days > 0  → `D-${days} 남음`
// days === 0 → '오늘 마감'
// days < 0  → 만료. 서버가 곧 status 를 REJECTED 로 바꾼다
```

`expiresAt` 은 `requestedAt + 3일`이다. **`status === 'REQUEST_PENDING'` 일 때만 배지를 띄운다.**
수락·거절한 카드에도 `expiresAt` 값은 그대로 남아 있어서, 상태를 안 보면 종료된 카드에 D-day 가 붙는다.

### 1.5 카드 상태별 표시 규칙

목록의 4가지 카드가 각각 이 조합이다.

**① 검토 중** — `status = REQUEST_PENDING`

```
D-day 배지 + "3일 내 응답 없으면..." + [상세보기] [거절] [수락 및 협상 시작]
```

**② 협상 중** — `status = ACCEPTED | NEGOTIATING | CONTRACT_PENDING`

```
"협상중" 칩 + "새 AI 제안 N건"(newProposalCount > 0) + "라운드 4/15"
[상세보기] [협상방 입장]  ← 빨간 점은 newProposalCount > 0
+ "AI 최종 협의 조건" 블록 (아래 1.6)
```

**③ 응답 기한 만료** — `status = REJECTED` **AND** `rejectReason = EXPIRED`

```
"응답 기한 만료" 칩 + "이 프로젝트에는 다시 지원할 수 없습니다"
[상세 보기] 만
```

**④ 거절함** — `status = REJECTED` **AND** `rejectReason = DIRECT_REJECT`

```
"거절함" 칩 + "이 프로젝트에는 다시 지원할 수 없습니다"
[상세 보기] 만
```

> **③과 ④는 `status` 로 구분되지 않는다.** 둘 다 `REJECTED` 다.
> **반드시 `rejectReason` 을 봐야 한다.** 이 필드가 없으면 "거절함"과 "기한 만료"가 섞인다.
>
> 세 번째 값 `NEGOTIATION_FAILED` 도 있는데, 그때는 `status` 자체가 `NEGOTIATION_FAILED` 라
> `rejectReason` 을 안 봐도 된다.

### 1.6 "AI 최종 협의 조건" 블록

협상 중 카드 하단에 붙는 요약이다. **목록 API 에는 없다.**
`negotiationId` 로 협상 상세를 따로 부른다.

```
GET /api/v1/negotiations/{negotiationId}
```

| 화면 | 출처 |
|---|---|
| 월 급여 6,800,000원 | `agreedAmount`, 또는 `conditions[type=AMOUNT].agreedValue` |
| 기간 3개월 | `conditions[type=PERIOD].agreedValue` — 없으면 목록의 `periodLabel` |
| 근무 재택 / 풀타임 | `conditions[type=WORK_STYLE/WORK_FORM].agreedValue` — 없으면 `workLabel` |
| 모집 인원 1명 | ⚠️ **없음** (8장 ③) |
| "수락 완료 · 상대방이 확인했습니다" | `status` + `waitingForMe` 조합 |

**협상 대상이 아닌 조건은 `conditions` 에 아예 없다.** 서로 맞는 항목은 협상하지 않기 때문이다.
그래서 `conditions` 에서 못 찾으면 매칭 요청의 원래 값(`periodLabel`, `workLabel`)으로 떨어뜨려야 한다.

```js
const cond = (type) => detail.conditions.find(c => c.type === type);
const period = cond('PERIOD')?.agreedValue ?? request.periodLabel;
```

카드가 N개면 협상 상세도 N번 부르게 된다. **`status` 가 협상 중인 카드에 한해서만** 호출하고,
카드를 펼쳤을 때 lazy 로 부르는 것을 권한다.

### 1.7 ⚠️ 탭 카운트 배지

화면 ③(검토 중 탭)에 `검토 중 1` `협상 중 1` 처럼 숫자 배지가 있다.

**이 숫자를 한 번에 주는 API 가 없다.** 지금 방법은 두 가지다.

- **(a)** 탭 4개를 `size=1` 로 각각 호출하고 `totalElements` 만 읽는다 — 요청 4번
- **(b)** 매칭 담당에게 카운트 API 를 요청한다 — 협상 쪽 `GET /negotiations/waiting-count` 와 같은 패턴

당장은 (a)로 가되, 탭 전환 때마다 4번 부르지 말고 **화면 진입 시 1회**만 부르고 캐시할 것.

```js
const counts = await Promise.all(
  ['REVIEWING', 'NEGOTIATING', 'CLOSED'].map(tab =>
    api.get('/matchings/requests/received', { params: { tab, size: 1 } })
       .then(r => r.data.data.totalElements))
);
```

`ALL` 은 세 개의 합이 아니다(겹치는 상태는 없지만 향후 상태가 늘면 어긋난다). 필요하면 따로 부를 것.

---

## 2. 화면 ② 제안 상세보기

### 2.1 API

```
GET /api/v1/matchings/requests/{requestId}
```

응답: `MatchingRequestResponse` — **목록과 완전히 같은 타입이다.**

**딱 하나만 다르다.**

| 필드 | 목록 | 상세 |
|---|---|---|
| `mainTask` | `null` | 값 있음 |

줄바꿈이 있는 긴 텍스트라 카드에는 안 싣고 상세에서만 채우기로 정해져 있다(2026-08-09).

그래서 상세 화면은 **목록에서 받은 객체를 그대로 재사용**하고 `mainTask` 만 채워 넣어도 된다.
목록에서 넘어온 게 아니라 URL 직접 진입이면 이 API 만 부르면 전부 나온다.

### 2.2 필드 매핑

**프로젝트 정보 카드**

| 화면 | 필드 |
|---|---|
| 모바일 앱 백엔드 API 개발 | `projectTitle` |
| 카카오 · 국내 대표 IT 기업 | `companyName` · `companyProfile` |
| 역할 백엔드 개발자 | `jobRole` |
| 예산 월 4,500,000원 | ⚠️ `budgetAmount` (총액) — 8장 ② |
| 기간 4개월 | `periodLabel` |
| 시작일 2026.08.20 | `startDesiredDate` |
| 근무 형태 재택 / 풀타임 | `workLabel` |
| 경력 요건 4년 이상 | `minCareerYears` |

> 시안의 "국내 대표 IT 기업"은 회사 소개 문구처럼 보이는데, `companyProfile` 은
> `"IT/소프트웨어 · 50-100명"` (업종 · 인원수) 형태로 내려온다. **회사 소개 문구는 별도 필드가 없다.**
> 목록 카드와 같은 값을 쓰거나, 기획에 확인이 필요하다.

**상세 정보 카드**

| 화면 | 필드 |
|---|---|
| 프로젝트 상황 | ⚠️ **없음** — 8장 ④ |
| 담당 업무 | `mainTask` ✅ 상세에서만 채워짐 |
| 요구 기술 | `skills` |

### 2.3 버튼

`status === 'REQUEST_PENDING'` 일 때만 [거절] [수락 및 협상 시작] 을 띄운다.
그 외 상태는 읽기 전용이다.

---

## 3. 화면 ③ 수락 → 협상방

### 3.1 거절

```
POST /api/v1/matchings/requests/{requestId}/rejection
Content-Type: application/json

{ "reason": "일정이 맞지 않습니다." }
```

- `reason` 은 **선택**이다. 255자 이하. 비우려면 `{ "reason": null }` 또는 `{}`
- 응답: `MatchingRequestResponse` (갱신된 상태) → 목록 새로고침 없이 카드만 교체 가능
- **되돌릴 수 없다.** 거절하면 그 프로젝트의 재추천 대상에서도 제외된다. 확인 모달 필수

### 3.2 수락

```
POST /api/v1/matchings/requests/{requestId}/acceptance
```

바디 없음. 응답: `MatchingRequestResponse`

**응답의 `negotiationId` 가 이때 처음 채워진다.** 이 값으로 협상방으로 이동한다.

```js
const { data } = await api.post(`/matchings/requests/${requestId}/acceptance`);
router.push(`/negotiations/${data.data.negotiationId}`);
```

- 전원 수락을 기다리지 않는다. 내가 수락하는 즉시 협상방이 생긴다
- `status` 는 `ACCEPTED` 가 된다

### 3.3 협상방 최초 진입 — 상태 판정

```
GET /api/v1/negotiations/{negotiationId}
```

응답 `NegotiationResponse` 에서 **화면 모드를 정하는 필드가 3개**다.

```
agentState · waitingForMe · totalRound
```

판정 순서는 **`agentState` 가 먼저**다.

| `agentState` | 화면 |
|---|---|
| `RUNNING` | "AI 대리인이 협상 중" 진행 표시. **입력 막기** |
| `FAILED` | "대리인 호출 실패" + 재시도 안내 |
| `IDLE` | 아래 표로 다시 판정 |

`IDLE` 일 때:

| `waitingForMe` | `totalRound` | 화면 |
|---|---|---|
| `true` | — | **내 차례.** 승인/재지시 패널 |
| `false` | `> 0` | 상대 응답 대기 |
| `false` | `0` | 상대가 아직 마지노선을 안 냈음 |

**내가 마지노선을 냈는지**는 `conditions[].myFloor` 로 본다. `null` 이면 아직 안 낸 것이다.
→ 마지노선 입력 폼을 띄운다.

```js
const needFloorInput = detail.conditions.some(c => c.myFloor == null);
```

`RUNNING` 동안에는 STOMP 이벤트를 기다리거나 상세를 폴링한다(4장).

### 3.4 마지노선 제출 (협상 시작)

```
POST /api/v1/negotiations/{negotiationId}/start

{
  "conditions": [
    { "conditionType": "AMOUNT",     "value": "3500000" },
    { "conditionType": "PERIOD",     "value": "4 MONTH" },
    { "conditionType": "WORK_STYLE", "value": "REMOTE" },
    { "conditionType": "START_DATE", "value": "2026-09-01" }
  ]
}
```

**값 형식이 쟁점마다 다르다. 전부 문자열이다.**

| `conditionType` | `value` 형식 | 예 |
|---|---|---|
| `AMOUNT` | **원 단위 숫자** | `"3500000"` |
| `PERIOD` | `"{숫자} {단위}"` — 공백 구분 | `"4 MONTH"` |
| `START_DATE` | `yyyy-MM-dd` | `"2026-09-01"` |
| `WORK_STYLE` | enum 코드 | `"REMOTE"` |
| `WORK_FORM` | enum 코드 | `"FULL_TIME"` |
| `SCOPE` / `OTHER` | 자유 텍스트 | |

> **금액 단위 함정** — 화면은 "만 원" 단위로 입력받는 경우가 많은데 서버는 **원 단위**를 기대한다.
> `350` 을 그대로 보내면 마지노선이 350원이 된다. `× 10000` 을 잊지 말 것.
>
> **기간 형식 함정** — `"4"` 나 `"4개월"` 이 아니라 **`"4 MONTH"`** 다. 공백이 들어간다.

- 응답: `NegotiationResponse` (갱신된 상세)
- 두 번 호출하면 `409 NG_003 FLOOR_ALREADY_SUBMITTED`
- 마지노선이 예산 상한을 넘으면 `400 NG_005 FLOOR_EXCEEDS_BUDGET`
- **마지노선은 상대에게 절대 노출되지 않는다.** 내 에이전트의 하한으로만 쓰인다

### 3.5 조건 응답

AI 가 여러 조건을 묶어 제안하므로 응답도 한 번에 보낸다.

```
POST /api/v1/negotiations/{negotiationId}/answers

{
  "roundNo": 3,
  "answers": [
    { "conditionId": 401, "accepted": true },
    { "conditionId": 402, "accepted": false, "proposedValue": "2200000" }
  ]
}
```

- `roundNo` 는 **필수**다. 서버가 이전 라운드에 대한 늦은 응답을 걸러낸다.
  상세의 `totalRound` 를 그대로 넣는다
- `accepted: false` 면 `proposedValue` 가 필요하다 (255자 이하)
- `proposedValue` 형식은 3.4 표와 같다

**`NG_011 ACCEPT_BREAKS_FLOOR`** 가 자주 난다.
내가 그은 마지노선 밖의 제안을 수락하려 할 때다. 이때는 **마지노선을 먼저 넓혀야 한다.**

```js
try {
  await api.post(`/negotiations/${id}/answers`, body);
} catch (e) {
  if (e.response?.data?.code === 'NG_011') {
    // "내 마지노선을 벗어난 제안입니다" 안내 + [마지노선 수정] 버튼 노출
  }
}
```

### 3.6 마지노선 재설정

```
PATCH /api/v1/negotiations/{negotiationId}/floors

{ "conditions": [ { "conditionType": "AMOUNT", "value": "4800000" } ] }
```

- **보낸 쟁점만 갱신된다.** 한 건만 고치려면 한 건만 담는다
- 라운드가 오르지 않고 대리인도 돌지 않는다
- 이미 합의된 쟁점은 `409 NG_006 CONDITION_ALREADY_LOCKED`

### 3.7 마지노선 안내 문구 — `floorComparison`

각 조건에 `floorComparison` 이 실려 온다. **문구를 이 값으로 갈라야 한다.**

| 값 | 문구 |
|---|---|
| `RANGE` | 크기 비교. 프리랜서는 하한 → `"480만 원 이상이어야 합니다"` |
| `CHOICE` | 허용값 집합 → `"재택을 허용해야 합니다"` |
| `NONE` | 비교 기준 없음 → **안내를 띄우지 않는다** |

> 과거에 근무 방식(선택형)에 `"재택 이상이어야 합니다"` 라고 나간 적이 있다.
> 선택형에는 크기 관계가 없어 성립하지 않는 문장이다. **프론트에 조건별 표를 다시 두지 말고
> 반드시 서버가 준 `floorComparison` 을 쓸 것.**

`RANGE` 의 이상/이하 방향은 `viewerRole` 로 판단한다 — **클라이언트는 상한, 프리랜서는 하한**이다.

### 3.8 협상 로그

```
GET /api/v1/negotiations/{negotiationId}/messages
```

응답: `NegotiationMessageResponse[]` (시간순, 페이징 없음)

| 필드 | 용도 |
|---|---|
| `senderType` | 말풍선 좌/우. `CLIENT_AGENT` `FREELANCER_AGENT` `CLIENT` `FREELANCER` `SYSTEM` |
| `messageType` | `PROPOSAL` · `RESPONSE` · `SYSTEM` |
| `conditionType` | 대상 쟁점. 조건과 무관한 안내는 `null` |
| `content` | 본문 |
| `reason` | 근거. **모든 AI 제안에 붙는다** |
| `proposedValue` | 제안값 |
| `response` | `YES` / `NO` / 직접 입력값 |
| `roundNo` | 라운드 묶음 헤더용 |

`viewerRole` 과 `senderType` 을 비교해 내 쪽/상대 쪽을 가른다.

```js
const isMine = m.senderType === viewerRole
            || m.senderType === `${viewerRole}_AGENT`;
```

### 3.9 읽음 처리

```
POST /api/v1/negotiations/{negotiationId}/read
```

목록 카드의 **"새 AI 제안 N건" 배지 기준선**을 지금으로 옮긴다.
**상세 조회와 분리된 명시적 호출이다** — `GET` 만 해서는 배지가 안 사라진다.

협상방에 진입해 내용을 실제로 본 시점에 호출한다.

### 3.10 포기

```
POST /api/v1/negotiations/{negotiationId}/give-up

{ "reason": "예산이 맞지 않습니다." }
```

- `reason` 선택, 255자 이하
- **즉시 결렬 종료.** 되돌릴 수 없다. 확인 모달 필수
- 매칭 요청 상태가 `NEGOTIATION_FAILED` 가 되어 카드가 **종료됨** 탭으로 이동한다

### 3.11 헤더 배지

```
GET /api/v1/negotiations/waiting-count
→ data: { "waitingCount": 3 }
```

`waitingCount` 가 `0` 이면 배지를 숨긴다. 클라이언트·프리랜서 양쪽인 계정은 합산된다.

내가 답해야 하는 협상 건수. 목록(`/mine`)은 페이징이라 1페이지만 받으면 숫자가 실제보다 작아진다.
**반드시 이 API 를 따로 쓸 것.**

### 3.12 종료 문구

`status === 'FAILED'` 일 때만 `endReason` 에 값이 있다. 타결(`AGREED`)은 `null` 이다.

> ⚠️ **`endReason` 은 사람이 직접 쓴 문장이 그대로 온다.**
> 화면에 노출하기 전에 **반드시 이스케이프**할 것. XSS 경로다.

---

## 4. 실시간 (STOMP)

### 4.1 연결

```
엔드포인트 : /ws  또는  /api/ws
프로토콜   : STOMP over WebSocket (SockJS 폴백 없음)
인증       : 핸드셰이크 시 JWT
```

> 배포 환경에서는 **`/api/ws` 를 쓴다.** ALB 가 `/api/*` 만 백엔드로 보내기 때문에
> `/ws` 는 프론트가 404 를 돌려준다. 로컬은 둘 다 열려 있다.

SockJS 클라이언트를 쓰면 안 된다. 순수 STOMP 클라이언트(`@stomp/stompjs`)를 쓴다.

하트비트는 서버가 켜 두었다. 클라이언트도 `heartbeatIncoming` / `heartbeatOutgoing` 을 설정할 것.

### 4.2 구독 경로

| 경로 | 언제 |
|---|---|
| `/topic/negotiations/{negotiationId}` | 협상방에 있는 동안 |
| `/topic/users/{accountId}/notifications` | 로그인 내내 |

### 4.3 협상 이벤트 페이로드

```json
{
  "negotiationId": 300,
  "type": "ANSWERED",
  "status": "IN_PROGRESS",
  "totalRound": 4
}
```

| `type` | 의미 |
|---|---|
| `STARTED` | 마지노선 설정 + 초기 제안 |
| `ANSWERED` | 응답 처리 + 다음 제안 |
| `AGREED` | 전 조건 합의 타결 |
| `FAILED` | 포기 / 자동 결렬 |
| `AGENT_RUNNING` | 대리인이 돌기 시작 → 진행 표시로 전환 |
| `AGENT_FAILED` | 대리인 호출 실패. **라운드가 오르지 않았다** → 실패·재시도 노출 |

> **이 페이로드에는 마지노선 같은 민감정보가 없다. 일부러 뺐다.**
> 이벤트는 "바뀌었다"는 신호일 뿐이므로, 받으면 **`GET /negotiations/{id}` 를 다시 호출해서
> 각자 마스킹된 상세를 받아야 한다.** 이벤트 값으로 화면을 직접 갱신하면 안 된다.

```js
client.subscribe(`/topic/negotiations/${id}`, async () => {
  const { data } = await api.get(`/negotiations/${id}`);
  setDetail(data.data);
});
```

### 4.4 알림

`/topic/users/{accountId}/notifications` 로 온다. 이 화면과 관련된 타입:

| 타입 | 라벨 | 대상 |
|---|---|---|
| `MATCHING_REQUESTED` | 매칭 요청 도착 | 프리랜서 — 목록 새로고침 |
| `MATCHING_ACCEPTED` | 매칭 요청 수락 | 클라이언트 |
| `MATCHING_REJECTED` | 매칭 요청 거절 | 클라이언트 |
| `MATCHING_RECOMMENDED` | 추천 완료 | 클라이언트 — 재추천 완료 신호 |
| `NEGOTIATION_STARTED` | 협상 시작 | 양측 |
| `NEGOTIATION_PROPOSED` | 새로운 AI 제안 | 양측 — 카드 배지 갱신 |
| `NEGOTIATION_FAILED` | 협상 결렬 | 양측 |

---

## 5. enum 사전

### `MatchingStatus`

| 값 | 라벨 |
|---|---|
| `REQUEST_PENDING` | 요청 대기 |
| `REJECTED` | 거절 |
| `ACCEPTED` | 수락 |
| `NEGOTIATING` | 협상중 |
| `NEGOTIATION_FAILED` | 협상 결렬 |
| `CONTRACT_PENDING` | 계약 대기 |
| `CONTRACTED` | 계약 완료 |
| `IN_PROGRESS` | 진행중 |
| `COMPLETION_PENDING` | 완료 대기 |
| `CLOSED` | 종료 |
| `TERMINATED` | 중도 종료 |

`REJECTED` / `NEGOTIATION_FAILED` / `TERMINATED` / `CLOSED` 는 **되돌아가지 않는 종결 상태**다.

### `RejectReason`

| 값 | 라벨 |
|---|---|
| `DIRECT_REJECT` | 직접 거절 |
| `EXPIRED` | 응답 기한 만료 |
| `NEGOTIATION_FAILED` | 협상 결렬 |

### `NegotiationStatus`

`IN_PROGRESS` 협상중 · `AGREED` 타결 · `FAILED` 협상 결렬

### `ConditionStatus`

`PENDING` 협상중 · `AGREED` 합의 · `REJECTED` 재협상 필요 · `FAILED` 미합의

### `ConditionType`

| 값 | 라벨 | `floorComparison` |
|---|---|---|
| `AMOUNT` | 금액 | `RANGE` |
| `PERIOD` | 기간 | `RANGE` |
| `START_DATE` | 시작일 | `RANGE` |
| `WORK_STYLE` | 근무 방식 | `CHOICE` |
| `WORK_FORM` | 근무 형태 | `CHOICE` |
| `SCOPE` | 업무 범위 | `NONE` |
| `OTHER` | 기타 | `NONE` |

`floorComparison` 은 **응답에 실려 오므로 이 표를 프론트에 하드코딩하지 말 것.**

### `NegotiationAgentState`

`IDLE` 대기 · `RUNNING` 대리인 협상 중 · `FAILED` 대리인 호출 실패

### `SenderType`

`CLIENT_AGENT` · `FREELANCER_AGENT` · `CLIENT` · `FREELANCER` · `SYSTEM`

---

## 6. 전체 호출 흐름

```
[제안 목록]
  GET /matchings/requests/received?tab=ALL
        │
        ├── 협상 중 카드 → GET /negotiations/{negotiationId}   (AI 최종 협의 조건)
        │
        ├── [상세보기] → GET /matchings/requests/{requestId}   (mainTask 채워짐)
        │
        ├── [거절]     → POST /matchings/requests/{id}/rejection
        │                    { reason? }  → 목록 갱신
        │
        └── [수락 및 협상 시작]
                  POST /matchings/requests/{id}/acceptance
                        → negotiationId 획득
                        ↓
              [협상방]  GET /negotiations/{negotiationId}
                        SUBSCRIBE /topic/negotiations/{id}
                        POST /negotiations/{id}/read
                        ↓
                  myFloor == null ?
                    → POST /negotiations/{id}/start   (마지노선)
                        ↓
                  agentState = RUNNING → 이벤트 대기
                        ↓
                  waitingForMe = true
                    → POST /negotiations/{id}/answers
                       └ NG_011 → PATCH /negotiations/{id}/floors 후 재시도
                        ↓
                  status = AGREED → 계약 단계로
                  status = FAILED → endReason 표시 (이스케이프!)
```

---

## 7. 에러 코드

### 매칭 (`MT_*`)

| 코드 | HTTP | 메시지 | 이 화면에서 언제 |
|---|---|---|---|
| `MT_003` | 404 | 매칭 요청을 찾을 수 없습니다 | 잘못된 `requestId` |
| `MT_006` | 400 | 이미 응답한 매칭 요청입니다 | **수락/거절 더블클릭** |
| `MT_007` | 400 | 현재 상태에서는 처리할 수 없습니다 | 종결된 요청에 응답 시도 |
| `MT_016` | 400 | 응답 기한이 지나 자동 만료되었습니다 | **D-day 지난 카드에서 수락** |
| `MT_014` | 400 | 모집이 종료되었거나 취소된 프로젝트입니다 | 클라가 프로젝트를 내린 경우 |
| `MT_015` | 404 | 프리랜서를 찾을 수 없습니다 | |
| `MT_010` | 502 | AI 매칭 서버 호출에 실패했습니다 | 재시도 안내 |
| `MT_011` | 429 | 잠시 후 다시 시도해 주세요 | |

`MT_006` 과 `MT_016` 이 실사용에서 제일 자주 뜬다. **둘 다 목록을 새로고침해서 최신 상태를 보여주는 게
맞는 처리다.** 에러만 토스트로 띄우고 화면을 그대로 두면 사용자가 계속 같은 버튼을 누른다.

```js
if (['MT_006', 'MT_016', 'MT_007'].includes(code)) {
  toast(message);
  refetchList();
}
```

### 협상 (`NG_*`)

| 코드 | HTTP | 메시지 | 언제 |
|---|---|---|---|
| `NG_001` | 404 | 협상을 찾을 수 없습니다 | |
| `NG_002` | 403 | 협상 당사자가 아닙니다 | |
| `NG_003` | 409 | 마지노선을 이미 제출했습니다 | `start` 중복 호출 |
| `NG_004` | 400 | 조건 타입이 일치하지 않거나 누락되었습니다 | 쟁점 누락 |
| `NG_005` | 400 | 마지노선이 예산 상한을 초과했습니다 | |
| `NG_006` | 409 | 이미 합의된 조건입니다 | 합의된 쟁점 수정 |
| `NG_007` | 400 | 응답할 제안이 없는 조건입니다 | |
| `NG_008` | 409 | 진행 중인 협상이 아닙니다 | 종료된 협상에 응답 |
| `NG_010` | 400 | 라운드 상한(15회)을 소진했습니다 | |
| `NG_011` | 400 | 마지노선을 벗어난 제안입니다 | **→ 마지노선 수정 유도** |
| `NG_020` | 403 | 아직 채팅 입력이 활성화되지 않았습니다 | AI Out 전 채팅 시도 |

### 공통

| 코드 | HTTP |
|---|---|
| `ACCESS_DENIED` | 403 |
| `INVALID_REQUEST` | 400 |

---

## 8. ⚠️ 서버가 아직 못 주는 값

**프론트가 임의로 계산해 채우지 말 것.** 매칭 담당에게 요청이 필요한 항목이다.

### ① AI 적합도 (`AI 94%`)

`MatchingRequestResponse` 에 점수 필드가 없다. 매칭 presentation 패키지 전체에 점수라 부를 만한 건
`CandidateListResponse.lowScoreWarned` (boolean) 하나뿐이다.

참고로 **클라이언트가 보는 후보 카드에서는 점수를 일부러 숨기고** `fitReasons`
(`["요구 스킬 97% 일치", "경력 조건 충족"]`) 태그로 대체하기로 정해져 있다. 프리랜서 카드에
퍼센트를 띄우는 건 그 결정과 방향이 다르므로 **기획 확인이 먼저 필요하다.**

### ② 예산 "월 6,000,000원"

`budgetAmount` 는 **계약 기간 전체 총액**이다. 카드가 원하는 건 월 단가다.

**프론트가 나눠서 쓰면 안 된다.**

- `periodLabel` 이 `"4개월"` 문자열이라 숫자를 안정적으로 뽑을 수 없다
- 나눠도 값이 다르다. 서버가 쓰는 월 단가(`budgetCap`)는
  **`(총예산 − 플랫폼 수수료) ÷ 전체 인원 ÷ 개월`** 이다
- 이 값은 **협상 상한과 같은 값**이다. 카드 숫자와 협상방 숫자가 어긋나면 클레임이 된다

→ `budgetCap` 을 응답에 추가해 달라고 요청할 것.

### ③ 모집 인원 (AI 최종 협의 조건)

매칭 내부에는 있다 — `ProjectPositionSummary.headcount`. 응답에만 안 실려 있다.

### ④ 프로젝트 상황 (상세 화면)

`currentSituation` 필드다. 프로젝트가 매칭에 넘겨주고는 있지만
**"임베딩 전용, 요청 카드에는 안 나간다"** 로 결정돼 있어서(2026-08-09)
`MatchingRequestResponse` 에 없다.

상세 화면에 필요해졌으므로 결정을 되돌려야 한다. **목록이 아니라 상세에만 실으면 된다** —
`mainTask` 가 이미 그 방식이다.

### 요청 문구 (그대로 복사해서 쓸 것)

```
프리랜서 "프로젝트 제안" 화면(목록·상세·협상) 연동 중입니다.
MatchingRequestResponse 에 네 개가 없어서 요청드립니다.

1. AI 적합도 (카드 우상단 "AI 94%")
   점수 필드가 없습니다. 다만 클라이언트 후보 카드는 점수를 숨기고
   fitReasons 로 대체하기로 돼 있어서, 프리랜서 쪽에 퍼센트를 띄우는 게
   맞는지 기획 확인이 먼저일 것 같습니다.

2. budgetCap (카드의 "예산 월 6,000,000원")
   budgetAmount 가 기간 전체 총액이라 단위가 다릅니다.
   periodLabel 이 문자열이라 프론트가 나눌 수도 없고, 나눠도
   순예산÷인원÷개월 과 달라져서 협상 상한과 어긋납니다.

3. headcount (AI 최종 협의 조건의 "모집 인원 1명")
   ProjectPositionSummary.headcount 로 이미 넘어가 있는 값입니다.

4. currentSituation (상세 화면의 "프로젝트 상황")
   2026-08-09 에 "임베딩 전용, 카드엔 mainTask만" 으로 정했었는데
   상세 시안에 들어왔습니다. mainTask 와 같이 상세 응답에만
   실어주시면 됩니다.

나머지(companyProfile, workLabel, periodLabel, currentRound/maxRound,
newProposalCount, rejectReason, floorComparison)는 전부 잘 나오고 있어서
그대로 쓰고 있습니다.

그리고 탭 카운트 배지("검토 중 1", "협상 중 1")용 API 가 있으면 좋겠습니다.
지금은 탭 4개를 size=1 로 각각 불러 totalElements 를 읽고 있습니다.
협상 쪽 /negotiations/waiting-count 와 같은 형태면 됩니다.
```

---

## 9. 함정 모음

1. **`rejectReason` 없이는 "거절함"과 "기한 만료"를 구분할 수 없다.** 둘 다 `status = REJECTED` 다.

2. **D-day 배지는 `status === 'REQUEST_PENDING'` 일 때만.** 종료된 카드에도 `expiresAt` 은 남아 있다.

3. **`CONTRACTED` · `IN_PROGRESS` · `COMPLETION_PENDING` 이 "종료됨" 탭에 들어간다.** 탭 라벨과 카드 상태 칩을 따로 그릴 것.

3-1. **`status === 'ACCEPTED'` 분기를 만들지 말 것.** 수락 한 번에 `NEGOTIATING` 까지 가고,
     조건이 다 맞으면 `CONTRACT_PENDING` 까지 간다. `ACCEPTED` 는 화면에 안 나온다.

3-2. **수락·거절 후 카드는 현재 탭에서 사라진다.** 전체 재조회 대신 로컬에서 빼고 탭 카운트만 갱신할 것.

4. **금액은 원 단위.** 화면이 "만 원"이면 `× 10000`. `350` 을 보내면 350원이 된다.

5. **기간은 `"4 MONTH"`** — 공백 포함. `"4"` 도 `"4개월"` 도 아니다.

6. **`answers` 의 `roundNo` 는 필수.** 안 보내면 400, 옛 값을 보내면 늦은 응답으로 걸러진다.

7. **`floorComparison` 을 프론트에 하드코딩하지 말 것.** 선택형에 "이상이어야 합니다"가 나갔던 전례가 있다.

8. **STOMP 이벤트로 화면을 직접 갱신하지 말 것.** 민감정보가 빠져 있다. 받으면 상세를 다시 부른다.

9. **읽음 처리는 명시적 호출.** `GET` 만으로는 "새 AI 제안" 배지가 안 사라진다.

10. **`endReason` 은 사용자 입력이다.** 노출 전 이스케이프.

11. **배포에서는 `/api/ws`.** `/ws` 는 ALB 를 못 넘는다.

12. **`agentState` 를 `waitingForMe` 보다 먼저 본다.** 순서를 바꾸면 대리인이 도는 동안 입력 패널이 열린다.

13. **협상 상세는 협상 중 카드만.** 목록 카드 전부에 대해 부르면 페이지당 요청이 그만큼 늘어난다.

14. **거절·포기는 되돌릴 수 없다.** 둘 다 확인 모달 필수.

15. **`companyProfile` · `workLabel` · `periodLabel` 은 조립된 문자열.** 프론트가 다시 만들지 말 것.

---

## 10. 이 문서의 범위 밖

- 클라이언트 쪽 후보 추천·매칭 요청 발송·재추천 (`GET /positions/{id}/candidates`,
  `POST /requests`, `POST /positions/{id}/rerecommendations`) — 같은 컨트롤러에 있지만 화면이 다르다
- AI Out 이후 사람 채팅 (`chatRoomId` 로 채팅 도메인 진입)
- 협상 타결 이후 계약·착수금 — [프론트 화면·API 가이드](frontend-screen-api-guide.md) 참고
- 관리자 화면 (`/negotiations/admin/**`)
