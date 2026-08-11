# 프론트엔드 연동 가이드 — 계약 · 정산 (계약 대기 → 진행 → 완료)

협상이 타결된 뒤부터 프로젝트가 종료될 때까지의 화면입니다.
**계약서 조회 · 전자서명 · PDF · 수수료 결제** 를 다룹니다.

계정·인증 공통 규약은 [frontend-auth-integration.md](frontend-auth-integration.md),
프로젝트 등록·모집은 [frontend-project-integration.md](frontend-project-integration.md) 를 보세요.

- 계약 API 는 **역할 제한이 없습니다.** 클라이언트·프리랜서 모두 같은 엔드포인트를 씁니다.
  대신 **계약 당사자 두 명만** 접근할 수 있고, 아니면 403 `CT_002` 입니다.
- 계약서 생성 API 는 없습니다. 협상이 타결되면 서버가 자동으로 만듭니다.

---

## 1. 공통 규약

응답 봉투·페이지 형태는 프로젝트 가이드와 같습니다. 성공은 `code`, 실패는 `errorCode` 입니다.

### 단위·형식

```
금액      원 단위 정수. 화면에서 toLocaleString()
날짜      "2026-09-01"        LocalDate
시각      "2026-08-11T01:53:49"  LocalDateTime, 타임존 없음
이넘      코드값으로 내려갑니다. 라벨은 /api/v1/codes/* 에서 받으세요
```

### 이넘 — 계약에서 실제로 내려가는 값만

```
status (ContractStatus)
  DRAFT               작성 중       AI가 문구 채우는 중. 서명 불가
  SIGN_PENDING        서명 대기
  SIGNED              체결 완료
  IN_PROGRESS         진행중        전원 착수금 결제 완료
  COMPLETION_PENDING  정산 대기      클라이언트가 완료 처리
  COMPLETED           종료          성공보수 결제 완료
  TERMINATED          중도 종료      (미구현)
  REJECTED            서명 거부      (미사용)

signatures[].status (SignatureStatus)
  PENDING  서명 대기 / SIGNED 서명 완료

partyRole   CLIENT (갑) · FREELANCER (을)
payUnit     MONTHLY 고정.  스웨거에 HOURLY·DAILY 도 보이지만 계약에서는 안 나옵니다
workStyle   REMOTE 재택 · ONSITE 상주 · ANY 무관
workForm    FULL_TIME · PART_TIME · PROJECT
jobRole     BACKEND · FRONTEND · UI_UX …
```

라벨은 하드코딩하지 말고 받아 쓰세요.

```
GET /api/v1/codes/job-roles         [{ code, label, parentCode }]
GET /api/v1/codes/work-conditions   { workStyles, workForms, payUnits, periodUnits, skillLevels }
```

---

## 2. 전체 흐름

```
협상 타결
  └ 서버가 계약서 자동 생성 (status = DRAFT)
      └ [비동기] AI가 업무 범위 문구 작성 → SIGN_PENDING
          └ 알림 CONTRACT_CREATED (갑·을 모두)

양측 서명 → SIGNED
  └ 프리랜서 착수금 수수료 발생 · 1:1 채팅방 개설

전원 착수금 결제 → IN_PROGRESS       프로젝트도 진행중
클라이언트 완료 처리 → COMPLETION_PENDING
성공보수 결제 → COMPLETED            리뷰 작성 가능
```

**프론트가 상태를 바꾸는 API 는 서명 하나뿐입니다.** 나머지는 결제·완료 처리의 결과로 서버가 옮깁니다.

---

## 3. 계약 목록

```
GET /api/v1/contracts?projectId={projectId}&status=&page=0&size=10
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `projectId` | 선택 | 그 프로젝트 계약만. 프로젝트 상세의 계약 탭에서 사용 |
| `status` | 선택 | 탭 필터 |
| `page` / `size` | 선택 | 기본 `0` / `10`. **최신순 고정** |

`data.content[]` 한 건이 카드 한 장입니다.

```json
{
  "contractId": 4,
  "contractNo": "CT-2026-000004",
  "projectId": 8,
  "projectTitle": "B2B 주문 관리 서비스 리뉴얼",
  "jobRole": "FRONTEND",
  "counterpartName": "김개발",
  "status": "SIGN_PENDING",
  "totalAmount": 24800000,
  "payUnit": "MONTHLY",
  "payAmount": 6200000,
  "startDate": "2026-09-01",
  "endDate": "2026-12-31",
  "signatureRequired": true,
  "clientSigned": false,
  "freelancerSigned": true,
  "depositPaid": false
}
```

`counterpartName` 은 **보는 사람의 반대편**입니다. 클라이언트가 보면 프리랜서명, 프리랜서가 보면 기업명입니다.

### 배지 판정

위에서부터 먼저 걸리는 것을 씁니다.

```js
status === 'DRAFT'                     → '계약서 준비 중'   // 버튼 비활성
signatureRequired                      → '내 서명 대기'
status === 'SIGN_PENDING'              → '상대방 서명 대기'
status === 'SIGNED' && !depositPaid    → '결제 필요'
status === 'SIGNED'                    → '계약 완료'
status === 'IN_PROGRESS'               → '진행 중'
status === 'COMPLETION_PENDING'        → '정산 대기'
status === 'COMPLETED'                 → '완료'
```

`depositPaid` 는 **프리랜서 착수금 수수료** 결제 여부입니다.
프리랜서 화면에서는 본인이 낼 돈이라 결제 버튼으로 이어지고,
클라이언트 화면에서는 누를 게 없으므로 **"프리랜서 결제 대기"** 처럼 안내 문구로 쓰세요.

### 탭 매핑

```
전체        status 없이 호출
서명 대기    status=SIGN_PENDING
진행 중      status=IN_PROGRESS
정산 대기    status=COMPLETION_PENDING
완료        status=COMPLETED
```

---

## 4. 계약 상세

```
GET /api/v1/contracts/{contractId}
```

주요 필드입니다.

```
contractNo · projectTitle · negotiationId
clientName · freelancerName · jobRole · status
client       { companyName, businessNo, representative, address, phone }
freelancer   { name, phone, jobRole, settlementAccount }
totalAmount · payUnit · payAmount · startDate · endDate
workStyle · workForm · workLocation
inspectionDays · paymentDays · confidentialYears · penaltyRate · specialTerms
clauses[]    { no, title, content }
signatures[] { partyRole, name, status, signedAt, signatureImageUrl }
signedAt · createdAt
```

### 계약서 본문 — `clauses[]`

제1조부터 제15조까지 내려옵니다.

```
1  목적                   9  지식재산권
2  계약 대상 및 업무 범위    10 비밀유지
3  계약 기간               11 계약 변경
4  계약 금액               12 계약 해지 및 위약금
5  대금 지급               13 손해배상
6  플랫폼 이용 수수료        14 분쟁 해결
7  근무 조건               15 특약사항
8  검수 및 완료
```

**`content` 는 값이 모두 채워진 완성 문장입니다.** 파싱하거나 값을 끼워넣지 마세요.

```jsx
{contract.clauses.map(c => (
  <section key={c.no}>
    <h3>제{c.no}조 ({c.title})</h3>
    <p style={{ whiteSpace: 'pre-line' }}>{c.content}</p>
  </section>
))}
```

`pre-line` 이 없으면 줄바꿈이 사라져 한 덩어리로 보입니다.

**항 번호(①②③)는 계약마다 다를 수 있습니다.** AI 문구가 없으면 그 항을 빼고 뒤 번호를 당깁니다.
번호를 프론트에서 다시 매기지 마세요.

### 서명란 표시 규칙

```
화면   로그인한 본인의 서명만 보여줍니다
         클라이언트 → 발주자(서명) 칸만
         프리랜서   → 수주자(서명) 칸만
PDF    양측 서명란이 모두 그려집니다 (종이 계약서와 동일)
```

`signatures[]` 에는 **항상 갑·을 2건**이 들어옵니다. 상단 진행 카드에 상대 서명 여부를
표시해야 하므로 배열은 그대로 두고, 본문 하단 서명란만 걸러 그리세요.

```js
const mine = contract.signatures.find(s => s.partyRole === myRole);
```

`signatureImageUrl` 은 CDN 절대 URL 이라 `<img src>` 에 바로 넣습니다.
동의만 하고 그림을 안 그렸으면 `null` 이므로 이름 텍스트만 표시하세요.

---

## 5. 계약서 AI — 프론트가 부르는 API 는 없습니다

가장 오해하기 쉬운 부분입니다. **프론트는 AI 를 호출하지 않습니다.** 호출할 엔드포인트도 없습니다.

### 구조

```
[협상 타결]
   │
   ├─ 계약 생성 (status = DRAFT)
   └─ 커밋 → 협상 API 응답 반환      ← 여기서 바로 응답이 나갑니다. AI를 기다리지 않습니다
   ╎
   ╎  (커밋 직후, 서버가 별도 스레드에서 자동 실행)
   ▼
   ├─ 스프링 ──POST──▶ 파이썬 AI 서버
   │            ◀────  { main_task_summary, detail_scope_summary, special_terms }
   ├─ contract.content_json 에 저장
   ├─ status = DRAFT → SIGN_PENDING
   └─ 알림 CONTRACT_CREATED (갑·을 모두)
```

서버↔서버 호출이고 내부 인증 키로 막혀 있어 브라우저에서 부를 수 없습니다.

**응답이 먼저 나가므로 계약은 `DRAFT` 로 먼저 보입니다.** 협상 타결 응답을 받은 직후
계약 목록을 조회하면 아직 `DRAFT` 인 것이 정상입니다.

### AI 결과는 별도 필드가 아닙니다

**이미 `clauses[].content` 안에 들어와 있습니다.**

```
AI가 만드는 곳
  제2조  ③ 을이 수행할 주요 업무는 …
         ⑤ 세부 업무 범위는 … 로 한다
  제15조 특약사항

나머지는 전부 DB 값 (금액 · 기간 · 당사자 · 수수료율 · 근무조건)
```

계약은 5년 보관하는 문서라 **생성 시점에 한 번 받아 굳혀 둡니다.**
조회할 때마다 AI 를 부르면 문장이 매번 달라져 같은 계약서가 두 번 다르게 보입니다.

### 프론트가 해야 하는 것 — `DRAFT` 처리 하나 (필수)

AI 가 도는 동안(보통 2~5초, 최대 20초) 계약이 `DRAFT` 로 존재합니다.
**협상 타결 직후 계약을 열면 반드시 이 상태를 거칩니다.** 예외 상황이 아니라 정상 경로입니다.

```jsx
if (contract.status === 'DRAFT') {
  return <Placeholder>계약서를 준비하고 있습니다…</Placeholder>;  // 서명 버튼 비활성
}
```

`DRAFT` 에서 서명하면 400 `CT_003` 입니다.

### `DRAFT` 를 벗어나는 걸 아는 방법

```
① 알림 (권장)   STOMP  /topic/users/{accountId}/notifications
                type === 'CONTRACT_CREATED'  →  GET /contracts/{id} 재조회
② 폴링          DRAFT 일 때만 2초 간격 10회 정도
```

**둘 다 붙이세요.** 알림만 믿으면 소켓이 끊겼을 때 화면이 영영 "준비 중"에 멈춥니다.
폴링만 쓰면 최대 2초 늦게 반영됩니다.

협상 타결 화면에서 곧바로 계약으로 넘어가는 동선이면 `DRAFT` 를 거의 항상 보게 됩니다.
계약 목록의 카드도 같습니다 — `status === 'DRAFT'` 면 배지를 "계약서 준비 중"으로 두고
서명 버튼을 막으세요.

### AI 가 실패해도 계약은 열립니다

```
파이썬 다운 · 타임아웃(20초)  →  원문을 80자/200자로 잘라 대체  →  그래도 SIGN_PENDING
```

프론트에서는 성공/실패가 **구분되지 않습니다.** 어느 쪽이든 계약서가 정상으로 내려오므로
별도 에러 처리가 필요 없습니다.

---

## 6. 전자서명

### 화면 흐름

```
계약 상세  [계약서 서명하기]      → 미리보기 화면으로 이동. API 호출 없음
미리보기   서명란 "전자서명" 클릭   → 캔버스 열기. API 호출 없음
          [전자 서명 및 계약 체결] → 여기서만 API 호출
```

**확인 단계는 한 번입니다.** 계약 상세와 미리보기 양쪽에 확인 버튼을 두지 마세요.

미리보기 화면은 **프론트가 그립니다.** 별도 API 가 없고, 계약 상세에서 받은 데이터를
다른 레이아웃으로 다시 그리는 것뿐입니다. 라우트만 나누면 됩니다.

### 호출 순서

```
1. GET  /api/v1/contracts/{id}             화면 진입 시 1회
2. POST /api/v1/files?purpose=SIGNATURE    캔버스 그림 업로드 → { fileId }   (선택)
3. POST /api/v1/contracts/{id}/signature   { agreed: true, signatureFileId }
```

2번은 건너뛸 수 있습니다. **`signatureFileId` 없이 보내면 동의 클릭만으로 서명됩니다.**
3번 응답이 계약 상세와 같은 형태라 재조회가 필요 없습니다.

### 캔버스 원리

브라우저 `<canvas>` 에 마우스·터치 궤적을 선으로 그린 뒤, 그 그림을 PNG 파일로 뽑아
**일반 파일 업로드와 똑같이** 보냅니다. 서버는 그냥 이미지 파일로 받습니다.
서명 전용 프로토콜이나 외부 전자서명 서비스는 쓰지 않습니다.

```
pointerdown  → beginPath() · moveTo(시작점)
pointermove  → lineTo(현재점) · stroke()
pointerup    → 획 종료
저장          → canvas.toBlob(cb, 'image/png')
```

```js
// 1) 그림을 PNG Blob 으로
const blob = await new Promise(r => canvasRef.current.toBlob(r, 'image/png'));

// 2) 파일 업로드
const form = new FormData();
form.append('file', blob, 'signature.png');
const { data } = await api.post('/api/v1/files?purpose=SIGNATURE', form);
const fileId = data.data.fileId;

// 3) 서명
await api.post(`/api/v1/contracts/${contractId}/signature`, {
  agreed: true,
  signatureFileId: fileId,
});
```

`react-signature-canvas` 를 쓰면 위 이벤트 처리를 대신해 줍니다
(`getTrimmedCanvas()`, `clear()`, `isEmpty()` 제공). 직접 구현해도 30줄 정도입니다.

### 캔버스 주의 3가지

```
투명 배경           배경을 칠하면 PDF 서명란에 흰 사각형이 계약서를 덮습니다.
                   지우기는 clearRect 로, fillRect 로 흰색을 칠하지 마세요
devicePixelRatio    레티나에서 흐려집니다. canvas 를 배율만큼 키우고 CSS 로 줄이세요
                     canvas.width = w * dpr; canvas.style.width = w + 'px'; ctx.scale(dpr, dpr)
touch-action: none  없으면 모바일에서 손가락으로 그릴 때 페이지가 같이 스크롤됩니다
```

파일 제한은 **png · jpg, 5MB 이하**입니다.

### 서명 요청

```json
POST /api/v1/contracts/{contractId}/signature
{ "agreed": true, "signatureFileId": 123 }
```

```
agreed            필수. false 면 400
signatureFileId   선택. 없는 fileId 를 보내면 400 CT_005
```

### 서명 후

```
한쪽만 서명   status 그대로 SIGN_PENDING. 상대에게 알림 CONTRACT_SIGNED
양쪽 서명     status = SIGNED. 착수금 수수료 발생 · 채팅방 개설 · 양쪽에 알림
```

응답의 `signatures[]` 로 화면을 갱신하면 됩니다.

### 서명 거부는 없습니다

`POST /{contractId}/rejection` 이 스웨거에 보이지만 **제품에 없는 기능**입니다. 붙이지 마세요.

---

## 7. PDF 다운로드

```
GET /api/v1/contracts/{contractId}/pdf
```

**서버가 URL 이 아니라 PDF 바이트를 직접 응답합니다.**

```js
const res = await api.get(`/api/v1/contracts/${id}/pdf`, { responseType: 'blob' });

const url = URL.createObjectURL(res.data);
const a = document.createElement('a');
a.href = url;
a.download = `${contractNo}.pdf`;
document.body.appendChild(a);
a.click();
a.remove();
URL.revokeObjectURL(url);
```

### 함정 3가지

**① `responseType: 'blob'` 을 빼면 열리지 않는 파일이 나옵니다.**
axios 가 응답을 문자열로 파싱해 바이너리가 깨집니다. 에러도 안 나고 망가진 파일만 받아집니다.

**② 파일명을 헤더에서 읽으려 하지 마세요.**
서버가 `Content-Disposition` 을 붙이지만 CORS 노출 헤더에 없어 JS 에서 못 읽습니다.
응답의 `contractNo` 로 만드세요.

**③ 에러 응답도 Blob 으로 옵니다.**

```js
catch (e) {
  const body = JSON.parse(await e.response.data.text());
  alert(body.message);
}
```

### 프론트가 안 해도 되는 것

```
PDF 생성        jsPDF · html2canvas 불필요
계약서 레이아웃   서버 템플릿이 따로 있고 브라우저에 오지 않습니다
한글 폰트        서버에서 임베딩
서명 이미지 합성  서버가 그립니다
```

위변조 방지·재생성·브라우저별 렌더링 차이 때문에 서버에서 굽습니다.
화면과 PDF 가 같은 렌더러를 쓰므로 문장이 100% 같습니다.

버튼은 `DRAFT` 일 때만 숨기고, 그 외에는 언제든 받을 수 있습니다.

---

## 8. 수수료 결제

플랫폼이 다루는 돈은 **수수료뿐**입니다. 용역비는 클라이언트가 프리랜서에게 직접 지급하며
플랫폼을 거치지 않습니다(P29). 계약서 제5조에도 그렇게 적혀 있습니다.

### 결제할 건 찾기

```
GET /api/v1/settlements/mine?projectId={projectId}&phase=&status=&page=0&size=10
```

```
phase    DEPOSIT (착수금) · SUCCESS_FEE (성공보수)
status   PENDING · PAID · OVERDUE · FAILED · CANCELED
```

프로젝트 상세에서는 `ProjectResponse.payableSettlementId` 로 바로 받을 수도 있습니다
(결제할 게 없으면 `null`).

### 결제 화면 필드

```
GET /api/v1/settlements/{settlementId}

projectTitle   프로젝트명
baseAmount     기준 금액 (클라 성공보수는 프로젝트 예산, 프리 착수금은 계약 총액)
feeRate        수수료율(%)
gradeDiscount  등급 할인율(%p)
feeAmount      실제 결제 금액       ← 화면 금액은 이 값을 그대로 쓰세요
dueDate        납부 기한
```

**요율을 프론트에서 계산하지 마세요.** 등급 할인이 붙어 값이 달라집니다.

```
착수금    1억 미만 클라 3% · 프리 4%     1억 이상 클라 2% · 프리 4%
성공보수  1억 미만 클라 7% · 프리 6%     1억 이상 클라 6% · 프리 6%
등급 할인  클라이언트 DIAMOND · 프리랜서 MASTER 만 각 1%p 인하
```

### 결제 실행

```
GET  /api/v1/accounts/me/payment-methods       등록된 결제수단
POST /api/v1/settlements/{settlementId}/payment  { "paymentMethodId": 300 }
```

결제수단 목록에서 **`methodType === 'CARD'` 만** 보여주세요.
`BANK_ACCOUNT` 는 용역비 수령용이라 수수료 결제에 쓸 수 없습니다.
`displayName`("신한카드 **** 1234")이 이미 조립돼 내려옵니다.

"결제 약관 동의" 체크박스는 프론트 전용입니다. 서버로 보내는 값이 아닙니다.

### 결제 후 자동으로 일어나는 일

```
클라 착수금 결제     → 프로젝트 모집중
전원 프리 착수금 결제 → 프로젝트·계약 진행중 (P47)
클라 성공보수 결제    → 프로젝트·계약 종료. 리뷰 작성 가능 (P51)
```

**추가 호출이 필요 없습니다.** 결제 응답 후 화면을 재조회하면 상태가 바뀌어 있습니다.

---

## 9. 알림

```
STOMP  /topic/users/{accountId}/notifications

CONTRACT_CREATED   계약서 생성 완료(서명 대기 전환)  → 갑·을 모두
CONTRACT_SIGNED    한쪽 서명 → 상대에게만 / 체결 → 양쪽 모두
linkUrl            /contracts/{contractId}
```

---

## 10. 에러 코드

```
CT_001  404  CONTRACT_NOT_FOUND         계약 없음
CT_002  403  NOT_CONTRACT_PARTY         당사자 아님
CT_003  400  INVALID_CONTRACT_STATUS    DRAFT 인데 서명 시도 등
CT_004  400  ALREADY_SIGNED             이미 서명함
CT_005  400  SIGNATURE_NOT_FOUND        없는 signatureFileId
CT_008  501  TERMINATION_NOT_SUPPORTED  중도 파기 미구현

ST_001  404  SETTLEMENT_NOT_FOUND
ST_002  403  NOT_PAYER                  납부자 본인이 아님
ST_003  400  NOT_PAYABLE                이미 결제됨 · 결제 가능 상태 아님
```

---

## 11. 자주 겪는 문제

**PDF 를 받았는데 안 열립니다**
→ `responseType: 'blob'` 이 빠졌습니다.

**서명 버튼을 눌렀는데 400 `CT_003` 입니다**
→ 계약이 아직 `DRAFT` 입니다. AI 가 문구를 채우는 중이니 몇 초 뒤 재조회하세요.

**400 `CT_005` 가 납니다**
→ 스웨거 예시값 `123` 을 그대로 보냈을 가능성이 큽니다.
   `signatureFileId` 를 빼거나 실제 업로드한 `fileId` 를 넣으세요.

**PDF 서명란이 흰 사각형으로 덮입니다**
→ 캔버스 배경을 칠했습니다. 투명 PNG 로 저장하세요.

**계약서에 "string" 이 찍힙니다**
→ AI 문제가 아니라 프로젝트 등록 시 `mainTask`/`detailScope` 에 스웨거 예시값이 들어간 것입니다.

**계약 목록이 비어 있습니다**
→ 로그인 계정이 그 계약의 당사자가 아닙니다. 목록은 서명 주체 기준으로 걸러집니다.

---

## 12. 이 문서에 없는 것

```
계약서 수정        API 가 없습니다. 협상 합의값으로 서버가 생성합니다
서명 기한          없습니다(무기한). 자동 취소도 하지 않습니다
중도 파기          501 반환. 위약금 "수행분" 산정 기준 미정
검수 화면          기능이 없습니다. 완료 처리는 클라이언트 버튼 한 번입니다(P32)
프리랜서 성공보수    정산 생성 미구현. 추가 예정
당사자 이메일       계약 응답에 없습니다
지급일 · 지급 주기   계약서에서 의도적으로 뺐습니다(제5조 ②)
```
