# 화면별 연동 가이드 — 계약 · 정산 · 프로젝트

피그마 화면 하나하나에 **어떤 API 를 붙이고, 어떤 필드를 어디에 찍고, 무엇을 화면에서 계산해야
하는지**를 정리했습니다. 화면을 만들 때 이 문서부터 보고, 더 깊은 내용은 아래 두 문서로 넘어가세요.

| 이 문서 | 화면 → API → 필드. 파생 규칙과 함정 |
| [frontend-contract-integration.md](frontend-contract-integration.md) | 계약서 AI 동작, 서명 캔버스 원리, PDF 처리, 알림 |
| [frontend-project-integration.md](frontend-project-integration.md) | 프로젝트 등록 위저드 6단계, 사전 검수, 결제 모달 |

마지막 갱신 2026-08-12.

---

## 0. 공통 규약

### 응답 봉투

성공·실패 모두 같은 껍데기입니다. **실제 값은 항상 `data` 안에** 있습니다.

```json
{
  "timestamp": "2026-08-12T05:47:58Z",
  "status": 200,
  "code": "CONTRACTS_FOUND",
  "message": "조회에 성공했습니다.",
  "data": { }
}
```

실패면 `code` 가 도메인 에러코드(`CT_003`, `PJ_007` …)로 바뀝니다. **`message` 를 그대로 토스트에
띄우면 됩니다.** 문구는 서버가 사람이 읽을 수 있게 씁니다.

### 페이지 응답

목록은 `data` 안이 이 모양입니다.

```json
{
  "content": [],
  "page": 0, "size": 10,
  "totalElements": 8, "totalPages": 1,
  "first": true, "last": true
}
```

### 단위·형식

```
금액   원 단위 정수. 42000000 → "42,000,000원". 소수점 없음
날짜   "2026-09-01"        (LocalDate)
일시   "2026-08-12T14:23:11" (LocalDateTime, 타임존 없음. 서버는 KST)
요율   "4.00" 문자열 숫자. 퍼센트 값 그대로
```

**금액과 요율은 절대 화면에서 계산하지 마세요.** 등급 할인·기준 금액 규칙이 정책에 묶여 있어서
화면 계산은 거의 틀립니다. 서버가 준 `feeAmount` 를 그대로 찍습니다.

### 자주 쓰는 이넘

```
ContractStatus   DRAFT 작성 중 / SIGN_PENDING 서명 대기 / SIGNED 계약 체결 완료
                 IN_PROGRESS 진행 중 / COMPLETION_PENDING 정산 대기 / COMPLETED 완료
SignatureStatus  PENDING 서명 대기 / SIGNED 서명 완료
PartyRole        CLIENT 클라이언트(갑) / FREELANCER 프리랜서(을)
WorkStyle        REMOTE 재택 / ONSITE 상주 / ANY 모두 가능
WorkForm         FULL_TIME 풀타임 / PART_TIME 파트타임 / ANY 모두 가능
PayUnit          MONTHLY (현재 계약은 전부 이 값)
SettlementPhase  DEPOSIT 착수금 수수료 / SUCCESS_FEE 성공보수 수수료
SettlementStatus PENDING 결제 대기 / PAID 결제 완료 / OVERDUE 미납
                 FAILED 결제 실패 / CANCELED 취소됨
```

`ContractStatus` 에 `TERMINATED`(중도 종료)·`REJECTED`(서명 거부)도 정의는 있지만
**그 상태가 되는 경로가 없어 실데이터는 0건**입니다. 화면에 넣지 마세요.
다만 뱃지 매핑에 없는 값이 오면 `status` 문자열을 그대로 찍는 폴백은 두세요.

---

## 1. 클라이언트 — 계약관리

### 목록

```
GET /api/v1/contracts?tab={탭}&page=0&size=10
```

| 탭 | `tab` 값 |
|---|---|
| 전체 | `ALL` |
| 서명 대기 | `AWAITING_ME` |
| 상대방 서명 대기 | `AWAITING_COUNTERPART` |
| 체결 완료 | `CONCLUDED` |

**서명 대기 / 상대방 서명 대기는 계약 상태가 둘 다 `SIGN_PENDING`** 이라 `status` 로 못 가릅니다.
`tab` 을 쓰세요.

`AWAITING_ME` 는 **상대가 서명했든 안 했든** 내 서명만 안 됐으면 들어옵니다.
프리랜서가 먼저 서명한 계약도 여기 떠야 합니다. 놓치기 쉬운 지점입니다.

프로젝트 상세의 계약 탭도 같은 API 입니다. `&projectId=` 만 붙이세요.

### 카드 필드

```
프로젝트명        projectTitle
상대방           counterpartName        클라이언트가 보면 프리랜서명
상태 뱃지         status
계약 금액         payUnit + payAmount    "월 6,200,000원"
계약 기간         startDate ~ endDate
계약서 생성일      createdAt
근무             workStyle
양측 서명 표시     clientSigned · freelancerSigned   (뷰어와 무관, 항상 사실)
```

`clientBusinessField` 와 `payableSettlementId` 는 클라이언트 화면에서 **항상 `null`** 입니다.
전자는 자기 회사 업종이라 안 채우고, 후자는 계약에 걸린 정산이 전부 프리랜서 몫이라 낼 게 없습니다.

`depositPaid` 는 **프리랜서의 착수금 수수료** 납부 여부입니다. 클라이언트는 누를 게 없으므로
"프리랜서 결제 대기" 같은 안내 문구로만 쓰세요.

### 버튼

```
계약 상세      GET /api/v1/contracts/{contractId}
계약서 보기     GET /api/v1/contracts/{contractId}/pdf
서명하기       POST /api/v1/contracts/{contractId}/signature
```

서명 버튼 노출 조건은 아래 [5. 서명](#5-서명)과 같습니다.

---

## 2. 클라이언트 — 프로젝트 상세 · 수정

등록 위저드와 결제 모달은 [frontend-project-integration.md](frontend-project-integration.md)
§3·§5 에 있습니다. 여기서는 상세·수정만 다룹니다.

### 상세

```
GET /api/v1/projects/{projectId}
```

기본정보(제목·기간·예산·근무조건)와 상세정보(현재 상황·주요 업무·세부 범위·기타)가 한 번에
나옵니다. 모집 포지션은 `positions[]` 입니다.

프리랜서 현황(누가 매칭됐고 어느 단계인지)은 **매칭 API** 입니다. 이 응답에 없습니다.

### 수정

```
PUT /api/v1/projects/{projectId}
```

**부분 수정이 아니라 전체 교체입니다.** 한 항목만 바꿔도 나머지를 전부 실어 보내야 합니다.
`GET` 으로 현재 값을 받아 폼을 채운 뒤 변경분만 덮어쓰고 통째로 보내세요.

포지션은 배열 안 `positionId` 로 판단합니다.

```
positionId 있음   기존 포지션 수정
positionId null   새 포지션 추가
배열에서 빠짐      삭제
```

착수금 결제 후에는 세 항목이 잠깁니다.

```
인원 변경        PJ_007
포지션 추가·삭제  PJ_008
예산 변경        PJ_011
```

제목 · 직군 · 직무 · 경력 · 스킬 · 근무조건 · 기간 · 시작일 · 텍스트 · 첨부는 계속 수정됩니다.

**결제 전에 예산을 바꾸면 착수금 정산이 자동 재계산됩니다.** 결제 모달을 다시 열면 새 금액입니다.

---

## 3. 프리랜서 — 내 계약 목록

### 탭

```
전체       tab=ALL
서명 대기   tab=AWAITING_ME
진행 중     tab=IN_PROGRESS
정산 대기   tab=SETTLEMENT_PENDING
완료       tab=COMPLETED
```

`IN_PROGRESS` 탭은 **`SIGNED`(착수금 미납) + `IN_PROGRESS` 두 상태**를 담습니다.
"결제하면 시작됩니다" 카드가 진행 중 탭에 놓이기 때문입니다. `status` 로는 못 만듭니다.

`DRAFT` 는 `ALL` 에만 나옵니다.

### 카드 필드

```
프로젝트명           projectTitle
회사명 · 업종         counterpartName · clientBusinessField
급여 "월 6,200,000원"  payUnit + payAmount
기간                startDate ~ endDate
근무                workStyle
왼쪽 뱃지            status
```

`payAmount` 가 `null` 이면 `"-"` 로 찍으세요. 컬럼이 나중에 생겨서 옛 계약은 비어 있습니다.

### 오른쪽 수수료 뱃지 — 파생 규칙

전용 필드가 없습니다. **두 값을 조합해서 만드세요.**

```js
status === 'SIGNED'             && !depositPaid          → '착수금 수수료 결제 대기'
status === 'IN_PROGRESS'        &&  depositPaid          → '착수금 수수료 결제 완료'
status === 'COMPLETION_PENDING' &&  payableSettlementId  → '성공보수 수수료 결제 대기'
status === 'COMPLETION_PENDING' && !payableSettlementId  → '성공보수 수수료 결제 완료'
status === 'COMPLETED'                                   → '성공보수 수수료 결제 완료'
```

네 번째 줄이 헷갈립니다. **프리랜서가 성공보수를 내도 클라이언트가 낼 때까지 계약은
`COMPLETION_PENDING` 에 머뭅니다.** 프리랜서 결제로 프로젝트를 닫으면 플랫폼이 클라이언트
수수료를 못 받은 채 끝나기 때문입니다.

### 버튼

```
계약 상세      GET  /api/v1/contracts/{contractId}
계약서 보기     GET  /api/v1/contracts/{contractId}/pdf
착수금 결제     POST /api/v1/settlements/{payableSettlementId}/payment
성공보수 결제    POST /api/v1/settlements/{payableSettlementId}/payment
정산 내역      GET  /api/v1/settlements/mine?projectId={projectId}
```

**결제 버튼 둘은 같은 엔드포인트입니다.** 착수금인지 성공보수인지는 서버가 고릅니다.
`payableSettlementId` 가 `null` 이면 **버튼을 숨기세요.**

`진행 현황` 은 프로젝트 도메인, `채팅` 은 메시지 도메인, `리뷰 작성` 은 리뷰 도메인입니다.

---

## 4. 프리랜서 — 계약 상세

```
GET /api/v1/contracts/{contractId}
```

**서명 전이든 진행 중이든 같은 API 입니다.** `status` 로 UI 만 갈립니다. 추가 호출 없습니다.

### 서명 현황 3칸

`signatures[]` 배열입니다. 원소는 `partyRole · name · status · signedAt · signatureImageUrl`.

```js
const mine    = signatures.find(s => s.partyRole === 'FREELANCER')
const partner = signatures.find(s => s.partyRole === 'CLIENT')
```

**세 번째 "계약 확정" 칸은 배열에 없습니다.** 계약 필드로 만드세요.

```js
status === 'SIGN_PENDING'
  ? { title: '서명 대기 중', sub: '양측 서명 완료 시 확정' }
  : { title: '계약 최종 확정', sub: `확정일: ${signedAt}` }
```

`signedAt` 은 **계약 최상위 필드**(체결 시각)입니다. `signatures[].signedAt`(개별 서명 시각)과
다릅니다. 확정일에는 앞의 것을 쓰세요.

### 계약 조건

```
프로젝트    projectTitle
역할       jobRole
기간       startDate ~ endDate
서명일      signedAt
클라이언트   clientName
금액       payUnit + payAmount
근무 형태    workStyle + workForm   "재택 / 풀타임"
```

### 계약서 내용

```
제1조~       clauses[]  →  no(조 번호) · title(제목) · content(본문)
특약사항      specialTerms  (없으면 null)
```

**조 번호와 제목을 하드코딩하지 마세요.** AI 가 프로젝트 내용으로 본문을 채워서 계약마다
문장이 다릅니다. 배열을 순서대로 나열하면 됩니다.

### PDF

```
GET /api/v1/contracts/{contractId}/pdf
```

**`responseType: 'blob'` 필수.** JSON 으로 받으면 파일이 깨집니다.
서명 전에도 받을 수 있고, 서명이 끝나면 하단 서명란에 서명 이미지와 시각이 함께 찍힙니다.

파일명은 응답 헤더로 못 읽습니다(CORS 노출 설정에 없음). 화면에서 지어 쓰세요.
자세한 처리는 [frontend-contract-integration.md](frontend-contract-integration.md) §7 참고.

---

## 5. 서명

### 버튼 노출 조건

```js
if (contract.signatureRequired && contract.status === 'SIGN_PENDING') {
  // 서명 버튼
}
```

**`signatureRequired` 만 보면 안 됩니다.** 이 값은 "내 서명이 아직 안 됐다"만 뜻해서
`DRAFT` 계약에서도 `true` 입니다. `DRAFT` 는 AI 가 문구를 채우는 2~5초 구간이라
누르면 `CT_003 INVALID_CONTRACT_STATUS` 로 400 납니다.

### 요청

```
POST /api/v1/contracts/{contractId}/signature
{ "agreed": true, "signatureFileId": 123 }
```

`signatureFileId` 는 **선택**입니다. 캔버스에 그린 서명을 파일 업로드해서 받은 id 입니다.
생략하면 동의만으로 서명되고, PDF 서명란에는 이름만 찍히고 손글씨 그림은 안 들어갑니다.

캔버스 구현은 [frontend-contract-integration.md](frontend-contract-integration.md) §6 에 있습니다.

### 응답 분기

응답은 **계약 상세와 같은 형태**입니다. `status` 로 다음 화면이 갈립니다.

```
SIGNED        내가 마지막 서명자   →  "계약서가 체결되었습니다" 화면
SIGN_PENDING  상대가 아직         →  "서명 완료. 상대방 서명을 기다립니다"
```

체결되면 그 자리에서 **프리랜서 착수금 수수료 정산이 생기고** 해당 포지션 인원이 확정됩니다.

### 주의

- **되돌릴 수 없습니다.** 서명 취소 API 가 없습니다. 확인 모달의 경고 문구는 정확합니다.
- **중복 클릭을 막으세요.** 두 번 누르면 `ALREADY_SIGNED` 로 400 입니다.
- 서명 기한은 **없습니다.** 기한 표시나 자동 취소 안내를 넣지 마세요.

### 체결 완료 화면

별도 조회 없이 서명 응답을 그대로 그립니다.

```
계약 번호     contractNo
프로젝트      projectTitle
프리랜서      freelancerName
계약 기간     startDate ~ endDate
급여         payUnit + payAmount
계약서 다운로드  GET /api/v1/contracts/{contractId}/pdf
```

"지급 방식: 월별 지급" 은 별도 필드가 없습니다. `payUnit` 에서 파생하거나 고정 텍스트로 두세요.
용역비 지급 시점은 계약서 제3조에 "매월 말일" 로 박혀 있습니다.

---

## 6. 수수료 결제 (착수금 · 성공보수 공용)

착수금과 성공보수는 **화면 문구만 다르고 API 는 같습니다.**

### ① 결제할 건 열기

목록 카드의 `payableSettlementId` 로 상세를 부릅니다.

```
GET /api/v1/settlements/{payableSettlementId}
```

```
프로젝트명       projectTitle
총 계약 금액     baseAmount
수수료율        feeRate · gradeDiscount
결제 금액       feeAmount
```

**기준 금액이 월 급여가 아니라 계약 총액입니다.** 라벨을 "총 계약 금액" 으로 쓰세요.

```
프리랜서 착수금    계약 총액 × 4%
프리랜서 성공보수   계약 총액 × 6%
등급 할인        프리랜서는 MASTER 등급만 각 1%p
```

요율 표기는 `feeRate` 와 `gradeDiscount` 로 만듭니다.

```js
gradeDiscount > 0
  ? `${feeRate}% → 마스터 할인 ${gradeDiscount}% = ${feeRate - gradeDiscount}%`
  : `${feeRate}%`
```

`gradeDiscount` 가 `0.00` 이면 할인 문구를 통째로 숨기세요.

"계약 기간 3개월" 같은 표시는 정산 응답에 없습니다. 목록 카드의 `startDate ~ endDate` 로
개월 수를 계산하세요.

### ② 결제수단

```
GET /api/v1/accounts/me/payment-methods
```

**수수료 결제는 `methodType === 'CARD'` 인 것만** 쓰세요. 목록에 계좌(`BANK_ACCOUNT`)도 같이
오는데 그건 용역비 수령용입니다. "(기본)" 표시는 `isDefault` 로 판단합니다.

### ③ 결제

```
POST /api/v1/settlements/{settlementId}/payment
{ "paymentMethodId": 선택한 결제수단 id }
```

PG 연동 전이라 승인 절차 없이 즉시 완료됩니다. **응답이 정산 상세와 같은 형태라 완료 화면을
바로 그릴 수 있습니다.**

중복 클릭 방지로 요청 중에는 버튼을 비활성화하세요.

### ④ 완료 화면

```
프로젝트명      projectTitle
결제 금액       feeAmount
결제수단        paymentMethodLabel     "신한카드 **** 1234"
결제일시        paidAt
결제 승인번호    approvalNo
결제 상태       phase + status 조합
```

`paymentMethodLabel` 이 `null` 로 올 수 있습니다. **결제 후 카드를 삭제한 경우**입니다.
승인번호와 결제일시는 그대로 남으니 그 줄만 감추세요.

### ⑤ 결제 후 안내 문구 — 주의

착수금 완료 화면에 "프로젝트를 시작할 수 있습니다" 라고 쓰면 **여러 명 뽑는 프로젝트에서 틀립니다.**

```
모집 인원 전원이 계약을 체결하고
전원이 착수금 수수료를 내야  →  프로젝트가 "진행 중" 으로 전환 (정책 P47)
```

내가 냈다고 시작되지 않습니다. 내 계약은 여전히 `SIGNED` 이고, 나머지가 다 내야
`IN_PROGRESS` 가 됩니다. 문구를 이렇게 쓰세요.

> 착수 수수료 결제가 완료되었습니다.
> 모집 인원이 모두 결제를 마치면 프로젝트가 시작됩니다.

같은 이유로 완료 화면의 "진행 중인 프로젝트 보기" 버튼도 위험합니다. 방금 결제한 건이
아직 `IN_PROGRESS` 가 아니면 그 탭에 안 보입니다. **계약 상세로 돌려보내는 쪽**을 권합니다.

성공보수 결제 모달의 "계약이 종료됩니다" 도 같습니다. 프리랜서 결제만으로는 안 끝납니다.

---

## 7. 최종 정산 완료 화면

```
GET /api/v1/settlements/mine?projectId={projectId}
```

이 프로젝트에서 내가 낸 정산 2건(`DEPOSIT` · `SUCCESS_FEE`)이 나옵니다. **한 번 호출로 끝납니다.**

```
계약 금액           baseAmount              두 정산이 같은 값. 아무 쪽이나
착수 수수료 (4%)     phase='DEPOSIT'         feeRate · feeAmount
성공보수 수수료 (6%)  phase='SUCCESS_FEE'     feeRate · feeAmount
전체 플랫폼 수수료    두 feeAmount 의 합. 화면에서 더하세요
착수 수수료 결제일    DEPOSIT.paidAt
성공보수 결제일      SUCCESS_FEE.paidAt
```

"프로젝트 최종 종료일" 은 아직 못 줍니다. 임시로 성공보수 결제일을 쓰되, 프리랜서가 마지막
납부자가 아니면 며칠 차이날 수 있습니다.

---

## 8. 아직 못 주는 것

**화면에서 빼거나 임시 처리해야 하는 항목**입니다. 여기 없는 건 다 됩니다.

| 항목 | 상황 | 지금 어떻게 |
|---|---|---|
| 탭 옆 건수 배지 | 전용 API 없음 | 숫자를 빼세요 |
| 목록의 서명일 | `signedAt` 이 상세에만 | 상세에서만 표시 |
| 목록의 기술스택 태그 | 목록에 `skills` 없음 | 상세·PDF 에는 있음 |
| 서명 전 예상 수수료 금액 | 정산이 체결 후 생김 | 문구에서 금액을 빼세요 |
| 프로젝트 최종 종료일 | `completedAt` 미노출 | 성공보수 결제일로 대체 |
| **서명 기한 · 자동 취소** | **정책 자체가 없음** | **두 줄 다 제거** |
| 중도 종료 · 위약금 | 파기 API 501, Penalty 도메인 없음 | 화면에서 제외 |
| 대금 분할(착수금/잔금) | 분할 안 하기로 확정. 값이 0 | 총 계약금액만 표시 |
| "수정하기"(계약서) | 대응 API 없음 | 버튼 제거 |
| 지급 방식 표기 | 필드 없음 | `payUnit` 파생 또는 고정 텍스트 |

계약번호 접두사는 코드가 `CT-2026-000127`, 일부 시안이 `CNT-2026-00127` 입니다.
한쪽으로 정해주시면 서버를 맞춥니다.

---

## 9. 에러 코드

### 계약

| 코드 | 상태 | 의미 | 화면 처리 |
|---|---|---|---|
| `CT_001` | 404 | 계약 없음 | 목록으로 |
| `CT_002` | 403 | 당사자 아님 | 목록으로 |
| `CT_003` | 400 | 서명할 수 없는 상태 | `DRAFT` 인 경우가 대부분. 잠시 후 재시도 |
| `CT_004` | 400 | 이미 서명함 | 상세를 다시 불러 갱신 |
| `CT_005` | 404 | 서명 대상 없음 | 상세를 다시 불러 갱신 |
| `CT_007` | 500 | PDF 생성 실패 | 재시도 안내 |
| `CT_008` | 501 | 중도 파기 미구현 | 호출하지 마세요 |

### 정산

| 코드 | 상태 | 의미 | 화면 처리 |
|---|---|---|---|
| `ST_001` | 404 | 정산 없음 | 목록 새로고침 |
| `ST_002` | 403 | 납부자 아님 | 목록으로 |
| `ST_003` | 400 | 결제 가능 상태 아님 | 이미 결제됐거나 취소됨. 새로고침 |

### 프로젝트

| 코드 | 상태 | 의미 |
|---|---|---|
| `PJ_001` | 404 | 프로젝트 없음 |
| `PJ_003` | 403 | 본인 프로젝트 아님 |
| `PJ_007` | 400 | 결제 후 인원 변경 불가 |
| `PJ_008` | 400 | 결제 후 포지션 추가·삭제 불가 |
| `PJ_011` | 400 | 결제 후 예산 변경 불가 |

> 이 도메인들은 상태 충돌도 **400** 으로 내려갑니다. `409` 로 분기하는 코드를 두지 마세요.
> HTTP 상태 대신 `code` 로 판단하는 편이 안전합니다.

---

## 10. 자주 겪는 문제

**서명 버튼을 눌렀는데 400 `CT_003` 이 납니다.**
계약이 `DRAFT` 입니다. AI 가 계약서 문구를 채우는 중이라 2~5초 뒤 `SIGN_PENDING` 이 됩니다.
버튼 조건에 `status === 'SIGN_PENDING'` 을 넣으세요.

**상대가 서명한 계약이 "서명 대기" 탭에서 사라집니다.**
`tab=AWAITING_ME` 를 쓰지 않고 "양쪽 다 미서명" 으로 직접 걸렀을 때 생깁니다.
판정 기준은 **내 서명이 `PENDING`** 입니다.

**진행 중 탭에 결제 대기 계약이 안 보입니다.**
`status=IN_PROGRESS` 로 부르면 `SIGNED`(착수금 미납)가 빠집니다. `tab=IN_PROGRESS` 를 쓰세요.

**PDF 가 깨진 파일로 받아집니다.**
`responseType: 'blob'` 을 안 줬습니다.

**결제 버튼을 눌렀는데 대상이 없습니다.**
`payableSettlementId` 가 `null` 인데 버튼을 노출했습니다. 착수금 정산은 **양측 서명이 끝난 뒤**
생깁니다. 서명 대기 상태에는 낼 게 없습니다.

**클라이언트 화면인데 업종·결제 id 가 계속 `null` 입니다.**
정상입니다. 둘 다 프리랜서 뷰어 전용입니다.

**"월 null원" 이 찍힙니다.**
`payAmount` 가 `null` 인 옛 계약입니다. `"-"` 폴백을 넣으세요.

**결제수단이 빈 칸으로 나옵니다.**
결제 후 카드를 삭제한 경우입니다. 승인번호는 남아 있으니 그 줄만 감추세요.
