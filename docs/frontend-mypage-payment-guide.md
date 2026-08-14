# 마이페이지 결제 내역 연동 가이드 (클라이언트 · 프리랜서)

대상 화면 2개

1. **클라이언트 마이페이지 → 결제 내역** — 상단 카드 3개 + 탭 3개 + 표 + 하단 합계
2. **프리랜서 마이페이지 → 결제 내역** — 요약 줄 + 탭 3개 + 목록

**두 화면이 같은 API 2개를 씁니다.** 화면마다 필요한 필드만 골라 쓰면 됩니다.

작성 기준 2026-08-14. 정산 도메인 담당(3번). 로컬에서 실데이터로 검증 완료.

---

## 0. 공통 규약

### Base URL

```
/api/v1
```

### 응답 봉투

성공·실패 모두 이 형태입니다. **실제 값은 항상 `data` 안**에 있습니다.

```json
{
  "timestamp": "2026-08-13T15:51:51.953461Z",
  "status": 200,
  "code": "SETTLEMENTS_FOUND",
  "message": "조회에 성공했습니다.",
  "data": { }
}
```

성공/실패는 **HTTP status** 로 판단하세요. `code` 는 성공 코드도 들어와서 판정에 쓰면 안 됩니다.

실패면 `code` 가 도메인 에러코드(`ST_001` 등)로 바뀌고 `message` 를 그대로 토스트에 띄우면 됩니다.

### 인증

JWT 필요. `accountId` 는 서버가 토큰에서 꺼냅니다. **요청에 절대 담지 마세요.**

두 API 모두 **역할 가드가 없습니다.** 클라이언트·프리랜서 모두 같은 엔드포인트를 부르고, 서버가
`payerAccountId` 로 본인 것만 돌려줍니다. 남의 정산은 나올 수 없습니다.

### 페이지 응답

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### 금액·날짜 형식

```
금액   원 단위 정수. 3720000 → "3,720,000원". 소수점 없음
일시   "2026-08-11T14:45:11.31534"  (LocalDateTime, 타임존 없음. 서버는 KST)
날짜   "2026-08-19"                 (LocalDate)
```

**금액을 화면에서 계산하지 마세요.** 요율·등급 할인 규칙이 정책에 묶여 있어 화면 계산은 거의 틀립니다.

---

## 1. API ① 요약

```
GET /api/v1/settlements/mine/summary
```

**파라미터 없습니다.**

### 응답

```json
{
  "totalAmount": 3720000,
  "depositAmount": 1488000,
  "successFeeAmount": 2232000,
  "depositProjectCount": 1,
  "successFeeProjectCount": 1
}
```

| 필드 | 설명 |
|---|---|
| `totalAmount` | 총 납부 수수료 = 착수금 + 성공보수 |
| `depositAmount` | 착수금 수수료 합계 |
| `successFeeAmount` | 성공보수 수수료 합계 |
| `depositProjectCount` | 착수금을 낸 **프로젝트** 수 |
| `successFeeProjectCount` | 성공보수를 낸 **프로젝트** 수 → 프리랜서 화면 "완료 프로젝트 수" |

### 반드시 알아야 할 것

**① 결제 완료(`PAID`)만 셉니다.** 문구가 "총 납부 수수료"라 실제로 낸 것만 셉니다.
결제 대기·미납·취소 건은 안 들어갑니다.

**② 탭을 바꿔도 값이 안 변합니다.** 화면 진입 시 **1회만** 부르세요. 탭 전환마다 부르면 낭비입니다.

**③ 낸 게 없으면 `null` 이 아니라 전부 0 입니다.** 널 검사 불필요합니다.

```json
{ "totalAmount": 0, "depositAmount": 0, "successFeeAmount": 0,
  "depositProjectCount": 0, "successFeeProjectCount": 0 }
```

**④ 프로젝트 수는 `DISTINCT` 입니다.** 한 프로젝트에 계약이 여러 건이면 수수료도 여러 건인데,
프로젝트 수는 1로 셉니다. **목록 행 수와 다를 수 있는 게 정상입니다.**

**⑤ 단계를 합친 프로젝트 수는 없습니다.** `depositProjectCount + successFeeProjectCount` 를
더하지 마세요 — 두 단계를 다 낸 프로젝트가 두 번 세어집니다. 실제로 그런 데이터가 있습니다.

---

## 2. API ② 목록

```
GET /api/v1/settlements/mine?phase={}&status=PAID&page=0&size=10
```

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `phase` | enum | — | 비움 = 전체 / `DEPOSIT` / `SUCCESS_FEE` |
| `status` | enum | — | **`PAID` 고정** (아래 참고) |
| `projectId` | Long | — | 이 화면에서는 안 씁니다 |
| `page` | int | — | 기본 0 |
| `size` | int | — | 기본 10 |

### `status=PAID` 를 반드시 거세요

화면 이름이 "결제 **내역**" 입니다. 안 걸면 결제 대기(`PENDING`)·취소(`CANCELED`) 건까지 나오고,
그 행들은 **결제일과 결제수단이 빈칸**이라 표가 깨져 보입니다.

### 응답 (실제 검증된 값)

```json
{
  "content": [
    {
      "settlementId": 13,
      "settlementNo": "ST-2026-000013",
      "projectId": 2,
      "projectTitle": "페어링 웹 리뉴얼",
      "contractId": 14,
      "payerRole": "FREELANCER",
      "payerName": null,
      "clientName": "주식회사 페어링",
      "phase": "SUCCESS_FEE",
      "baseAmount": 37200000,
      "feeRate": 6,
      "gradeDiscount": 0,
      "feeAmount": 2232000,
      "status": "PAID",
      "paymentMethodLabel": "신한카드 **** 1234",
      "approvalNo": "AP-20260811-0013",
      "failReason": null,
      "overdueReason": null,
      "dueDate": "2026-08-19",
      "payable": false,
      "paidAt": "2026-08-11T14:45:11.31534"
    }
  ],
  "page": 0, "size": 10, "totalElements": 2, "totalPages": 1,
  "first": true, "last": true
}
```

최신순(`settlementId DESC`)입니다.

### 필드 설명

| 필드 | 화면에서 |
|---|---|
| `settlementNo` | 결제번호. **`ST-YYYY-NNNNNN`** 형식 |
| `projectTitle` | 프로젝트명 |
| `clientName` | **발주 기업명** — 프리랜서 화면에서 씀 |
| `payerName` | 납부자 이름. **현재 항상 `null`** — 쓰지 마세요 |
| `phase` | 구분 배지 (착수금 / 성공보수) |
| `feeAmount` | 표에 찍는 금액 |
| `paymentMethodLabel` | 결제수단 표기 |
| `paidAt` | 결제일 |
| `status` | 상태 배지 |
| `baseAmount` · `feeRate` · `gradeDiscount` | 이 화면에서는 안 씀 |
| `approvalNo` · `dueDate` · `payable` | 이 화면에서는 안 씀 |

> **`clientName` vs `payerName`**
> `payerName` 은 "이 수수료를 낸 사람"이라 프리랜서가 조회하면 자기 이름이 나옵니다(지금은 `null`).
> 화면의 "주식회사 오이랩" 자리는 **`clientName`** 입니다.
> 클라이언트가 조회하면 `clientName` 은 자기 회사라 안 씁니다.

> **`feeRate` 는 `6.00` 이 아니라 `6` 으로 옵니다.** BigDecimal 이 JSON 으로 나가면서 소수점이
> 떨어집니다. 이 화면에서는 안 쓰지만, 다른 곳에서 쓸 때 `"6.00%"` 를 기대하면 안 됩니다.

---

## 3. 클라이언트 화면

### 레이아웃

```
┌─ 총 납부 수수료 ─┬─ 착수금 수수료 ─┬─ 성공보수 수수료 ─┐
│  11,100,000원   │  3,750,000원   │  7,350,000원    │
└────────────────┴───────────────┴─────────────────┘

결제 내역                    [전체] [착수금 수수료] [성공보수 수수료]
┌──────────────────────────────────────────────────────────┐
│ 결제일  │ 프로젝트           │ 구분   │ 금액      │ 결제수단  │
├──────────────────────────────────────────────────────────┤
│2026.06.18│B2B 주문 관리...   │성공보수│4,200,000원│신한카드1234│
│         │ST-2026-000042    │        │          │          │
└──────────────────────────────────────────────────────────┘
                                    조회 기간 합계  11,100,000원
```

### 매핑

**상단 카드 3개** — `summary` 응답

```
총 납부 수수료    summary.totalAmount
착수금 수수료     summary.depositAmount
성공보수 수수료    summary.successFeeAmount
```

**탭**

| 탭 | `phase` |
|---|---|
| 전체 | 비움 |
| 착수금 수수료 | `DEPOSIT` |
| 성공보수 수수료 | `SUCCESS_FEE` |

**표** — 목록 응답

```
결제일        paidAt          → "2026.06.18"
프로젝트       projectTitle
(작은 글씨)    settlementNo    → "ST-2026-000042"
구분 배지      phase
금액          feeAmount       → "4,200,000원"
결제수단       paymentMethodLabel
```

**하단 "조회 기간 합계"** — 별도 조회 없습니다. `summary` 에서 탭에 맞는 값을 고릅니다.

```js
const bottomTotal = {
  ALL: summary.totalAmount,
  DEPOSIT: summary.depositAmount,
  SUCCESS_FEE: summary.successFeeAmount,
}[currentTab];
```

> **목록의 `feeAmount` 를 더하면 안 됩니다.** 페이징이라 2페이지부터 틀립니다.

**영수증 버튼은 제거 확정입니다.** PG 미연동이라 `approvalNo` 가 모의값이고, 영수증 발급 기능이
서버에 없습니다.

---

## 4. 프리랜서 화면

### 레이아웃

```
수수료 결제 내역
[전체] [착수금 수수료] [성공보수 수수료]

┌────────────────────────────────────────────────┐
│ 총 성공보수 납부 744,000원   완료 프로젝트 수 1건 │
└────────────────────────────────────────────────┘

B2B 주문 관리 서비스 리뉴얼                744,000원
주식회사 오이랩 · 신한카드 1234 · 2027.01.02  [결제 완료]
```

### ⚠️ 탭이 시안과 다릅니다

시안에는 `전체 / 결제 완료 / 결제 실패 / 환불` 로 그려져 있는데 **확정안은 아래입니다.**

| 탭 | `phase` |
|---|---|
| 전체 | 비움 |
| 착수금 수수료 | `DEPOSIT` |
| 성공보수 수수료 | `SUCCESS_FEE` |

이유

- **결제 실패** — 서버에 `FAILED` 를 만드는 코드가 **없습니다.** PG 미연동이라 결제가 실패할 수
  없습니다. 탭을 만들면 영원히 0건입니다
- **환불** — `SettlementStatus` 에 환불 상태 자체가 없습니다
- 클라이언트 화면과 탭 구성을 맞추기로 했습니다

### 매핑

**요약 줄** — `summary` 응답

```
총 성공보수 납부 744,000원   →  summary.successFeeAmount
완료 프로젝트 수 1건         →  summary.successFeeProjectCount
```

**고정 문구입니다.** 착수금 탭을 눌러도 "총 성공보수 납부" 그대로 둡니다.

**목록 카드**

```
B2B 주문 관리 서비스 리뉴얼    projectTitle
744,000원                   feeAmount
주식회사 오이랩               clientName        ← payerName 아님
신한카드 1234                paymentMethodLabel
2027.01.02                  paidAt
[결제 완료]                  status
```

---

## 5. 구현 예시

```jsx
const PHASE_TABS = [
  { key: 'ALL',         label: '전체',          phase: undefined },
  { key: 'DEPOSIT',     label: '착수금 수수료',   phase: 'DEPOSIT' },
  { key: 'SUCCESS_FEE', label: '성공보수 수수료', phase: 'SUCCESS_FEE' },
];

function PaymentHistory() {
  const [summary, setSummary] = useState(null);
  const [tab, setTab] = useState('ALL');
  const [page, setPage] = useState(0);
  const [list, setList] = useState({ content: [], totalElements: 0 });

  // 진입 시 1회. 탭 바꿔도 안 부름
  useEffect(() => {
    api.get('/settlements/mine/summary')
       .then(res => setSummary(res.data.data));
  }, []);

  // 탭·페이지 바뀔 때만
  useEffect(() => {
    const phase = PHASE_TABS.find(t => t.key === tab).phase;
    api.get('/settlements/mine', {
      params: { phase, status: 'PAID', page, size: 10 },
    }).then(res => setList(res.data.data));
  }, [tab, page]);

  useEffect(() => { setPage(0); }, [tab]);   // 탭 바꾸면 1페이지로

  if (!summary) return <Skeleton />;

  const bottomTotal = {
    ALL: summary.totalAmount,
    DEPOSIT: summary.depositAmount,
    SUCCESS_FEE: summary.successFeeAmount,
  }[tab];

  return (
    <>
      {/* 클라이언트: 카드 3개 */}
      <Cards>
        <Card label="총 납부 수수료"   value={won(summary.totalAmount)} />
        <Card label="착수금 수수료"    value={won(summary.depositAmount)} />
        <Card label="성공보수 수수료"   value={won(summary.successFeeAmount)} />
      </Cards>

      {/* 프리랜서: 요약 줄 (문구 고정) */}
      <SummaryBar>
        총 성공보수 납부 <b>{won(summary.successFeeAmount)}</b>
        완료 프로젝트 수 <b>{summary.successFeeProjectCount}건</b>
      </SummaryBar>

      <Tabs>
        {PHASE_TABS.map(t => (
          <Tab key={t.key} active={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
          </Tab>
        ))}
      </Tabs>

      {list.content.length === 0
        ? <Empty>결제 내역이 없습니다.</Empty>
        : list.content.map(row => <Row key={row.settlementId} {...row} />)}

      <BottomTotal>조회 기간 합계 <b>{won(bottomTotal)}</b></BottomTotal>
    </>
  );
}

const won = (n) => `${n.toLocaleString('ko-KR')}원`;
const ymd = (iso) => iso?.slice(0, 10).replaceAll('-', '.');   // "2026.06.18"
```

---

## 6. enum 사전

### `SettlementPhase`

| 값 | 라벨 |
|---|---|
| `DEPOSIT` | 착수금 수수료 |
| `SUCCESS_FEE` | 성공보수 수수료 |

### `SettlementStatus`

| 값 | 라벨 | 이 화면에서 |
|---|---|---|
| `PENDING` | 결제 대기 | 안 나옴 (`status=PAID` 로 걸러짐) |
| `PAID` | 결제 완료 | **이것만 나옴** |
| `OVERDUE` | 미납 | 안 나옴. 실데이터도 없음 |
| `FAILED` | 결제 실패 | 안 나옴. **만드는 코드 자체가 없음** |
| `CANCELED` | 취소됨 | 안 나옴 |

`status=PAID` 로 고정하므로 배지는 항상 "결제 완료" 입니다. 그래도 `status` 로 그리세요 —
나중에 필터가 바뀌어도 화면이 따라갑니다.

### `PartyRole`

`CLIENT` / `FREELANCER` — `payerRole` 에 담깁니다. 이 화면에서는 안 씁니다.

---

## 7. 에러 코드

| 코드 | HTTP | 언제 |
|---|---|---|
| `ST_001` | 404 | 정산을 찾을 수 없음 (상세 조회 시) |
| `ST_002` | 403 | 납부자가 아님 (상세 조회 시) |
| `ACCESS_DENIED` | 403 | 인증 실패 |
| `INVALID_REQUEST` | 400 | 파라미터 형식 오류 |

**이 화면의 두 API 는 목록·집계라 비즈니스 에러가 거의 없습니다.** 데이터가 없으면 에러가 아니라
빈 목록 / 0 입니다.

`phase` 나 `status` 에 없는 값을 보내면 400 입니다. 오타에 주의하세요 (`SUCCESS_FEE`,
언더스코어 하나).

---

## 8. 알려진 데이터 이슈

개발 DB 에서 확인된 것입니다. **코드 문제가 아니라 더미 데이터 문제**입니다.

### `paymentMethodLabel` 이 `null` 로 나올 수 있습니다

```json
"paymentMethodLabel": null,
"approvalNo": "AP-20260811-0007"
```

SQL 로 넣은 더미 정산이 존재하지 않는 `payment_method_id` 를 들고 있어서입니다. 결제 이력
(`approvalNo`, `paidAt`)은 남아 있습니다.

실제 결제 후 카드를 삭제한 경우에도 `null` 이 됩니다. **`null` 이면 그 칸만 감추세요.**
전체 행을 숨기면 안 됩니다.

```jsx
{row.paymentMethodLabel && <span>{row.paymentMethodLabel}</span>}
```

### `settlementNo` 형식

시안에 `PAY-2026-0042` 로 그려져 있는데 **실제 채번은 `ST-YYYY-NNNNNN`** 입니다.

```
ST-2026-000042
```

화면 문구를 `ST-` 에 맞추기로 확정했습니다. 개발 DB 에는 손으로 넣은 `TAB-08-S` 같은 값도
섞여 있는데 그건 테스트 데이터입니다.

### `clientName` 이 `null` 일 수 있습니다

프로젝트가 삭제됐거나 클라이언트 프로필이 없는 경우입니다. 정산 이력은 프로젝트보다 오래
남아야 해서 서버가 예외를 던지지 않고 비웁니다. `projectTitle` 도 함께 `null` 이 됩니다.

---

## 9. 함정 모음

1. **`status=PAID` 를 반드시 거세요.** 안 걸면 결제 대기·취소 건이 섞여 결제일·결제수단이 빈칸으로 뜹니다.

2. **요약은 진입 시 1회만.** 탭 전환마다 부르지 마세요. 값이 안 바뀝니다.

3. **하단 합계를 목록에서 계산하지 마세요.** 페이징이라 2페이지부터 틀립니다. `summary` 에서 고르세요.

4. **`payerName` 을 쓰지 마세요.** 현재 항상 `null` 입니다. 회사명은 `clientName` 입니다.

5. **프로젝트 수 ≠ 목록 행 수.** 한 프로젝트에 두 단계를 다 냈으면 행은 2개, 프로젝트 수는 각 1입니다.

6. **`depositProjectCount + successFeeProjectCount` 를 더하지 마세요.** 두 단계를 다 낸 프로젝트가 중복됩니다.

7. **탭을 바꾸면 페이지를 0으로 리셋하세요.** 3페이지에서 탭을 바꾸면 빈 화면이 나옵니다.

8. **`paymentMethodLabel` 이 `null` 이어도 행은 보여주세요.** 그 칸만 감춥니다.

9. **결제번호는 `ST-` 입니다.** `PAY-` 가 아닙니다.

10. **프리랜서 요약 문구는 고정입니다.** 착수금 탭에서도 "총 성공보수 납부" 그대로입니다.

11. **빈 목록은 정상입니다.** 성공보수를 아직 안 낸 계정은 그 탭이 빕니다. 에러 처리하지 마세요.

12. **`feeRate` 는 `6.00` 이 아니라 `6`.** 이 화면에서는 안 쓰지만 알아두세요.

---

## 10. 이 문서의 범위 밖

| 화면 | 담당 |
|---|---|
| 기본 정보 · 결제수단 · 회원 탈퇴 | account |
| 기업 정보 · 등급 및 혜택 | client |
| 내 이력서 | freelancer |
| 리뷰 관리 / 작성한 리뷰 | review |
| 수수료 결제(납부) 자체 | `POST /settlements/{id}/payment` — [frontend-screen-api-guide.md](frontend-screen-api-guide.md) §6 |
| 위약금 | `GET /settlements/penalties/mine` — 이 화면에 포함 안 함 |

> 좌측 메뉴 이름이 시안마다 `리뷰 관리` / `작성한 리뷰` 로 다릅니다. 서버와 무관하지만 통일이 필요합니다.
