# 고객지원 프론트 연동 가이드 (1:1 문의 · 챗봇)

> 담당: 리뷰·등급·마이페이지·이력서·결제수단·**1:1문의·챗봇**
> 기준일: 2026-08-11 / 브랜치 `feature/fixFreelancer`
> 마이페이지는 `frontend-mypage-integration.md` 를 봐주세요.

---

## 0. 한눈에 보기

| 화면 | Method | Path | 권한 |
|---|---|---|---|
| 챗봇 — 질문 보내기 | `POST` | `/api/v1/support/chatbot/questions` | 로그인 |
| 챗봇 — 추천 질문 칩 | `GET` | `/api/v1/support/chatbot/suggested-questions` | 로그인 |
| 챗봇 — 남은 횟수 | `GET` | `/api/v1/support/chatbot/quota` | 로그인 |
| 챗봇 — 오늘 대화 이력 | `GET` | `/api/v1/support/chatbot/messages` | 로그인 |
| 문의 — 등록 | `POST` | `/api/v1/support/inquiries` | 로그인 |
| 문의 — 내 목록 | `GET` | `/api/v1/support/inquiries/mine` | 로그인 |
| 문의 — 상세 | `GET` | `/api/v1/support/inquiries/{inquiryId}` | 로그인 |
| 관리자 — 요약 카드 | `GET` | `/api/v1/support/admin/inquiries/summary` | 관리자 |
| 관리자 — 문의 목록 | `GET` | `/api/v1/support/admin/inquiries` | 관리자 |
| 관리자 — 답변 등록 | `POST` | `/api/v1/support/admin/inquiries/{inquiryId}/answer` | 관리자 |

### 공통 규칙

전부 **로그인 필요**입니다. HttpOnly 쿠키 `accessToken` 이 자동으로 실려가므로
`fetch` 에 `credentials: 'include'` 를 넣어주세요.

```json
{ "code": "INQUIRY_CREATED", "message": "문의를 접수했습니다.", "data": { } }
```

에러도 같은 껍데기이고 `errorCode` 가 붙습니다.

```json
{ "code": "ERROR", "message": "오늘의 챗봇 질의 횟수를 모두 사용했습니다.", "errorCode": "CB_003" }
```

| 상태 | 의미 |
|---|---|
| `401 GLOBAL_006` | 로그인 안 됨 / 만료 → 로그인 화면 |
| `403 GLOBAL_005` | 관리자 API를 일반 회원이 호출 |

---

# 1. 챗봇

## 1-1. 질문 보내기

```
POST /api/v1/support/chatbot/questions
```

```json
// 요청 — 첫 질문이면 sessionId 를 비웁니다
{ "sessionId": null, "question": "이력서는 어디서 쓰나요?" }
```

- `question` 필수, **500자 이하**
- `sessionId` 는 첫 질문에서 `null`. 응답으로 받은 값을 **다음 질문에 그대로 넣어** 대화를 이어갑니다

```json
// 응답 — 아래 answer 는 실제 AI 응답을 그대로 옮긴 것입니다
{
  "sessionId": 1200,
  "question": "이력서 어디서 써요?",
  "answer": "이력서 및 포트폴리오는 마이페이지의 프로필 관리 메뉴에서 작성하실 수 있습니다.",
  "actions": [
    { "code": "RESUME_EDIT", "label": "이력서 작성하러 가기", "url": "/mypage/resume" }
  ],
  "remainingQuota": 9,
  "createdAt": "2026-08-11T17:20:00"
}
```

### ⭐ `actions` — 답변 아래 이동 버튼 (신규)

**AI가 답변 내용에 맞는 화면을 골라주고, 서버가 경로로 바꿔 내려줍니다.**
프론트는 이 배열을 받아 버튼만 그리면 됩니다. **답변 텍스트를 파싱할 필요가 전혀 없습니다.**

```jsx
{data.actions.map(a => (
  <Button key={a.code} onClick={() => navigate(a.url)}>{a.label}</Button>
))}
```

| 규칙 | 내용 |
|---|---|
| 항상 배열 | 없으면 `[]`. **null 이 아닙니다** — 길이만 확인하면 됩니다 |
| 최대 1개 | 여러 개 띄우면 무엇을 눌러야 할지 헷갈려서 1개로 제한했습니다 |
| 이력 조회는 항상 `[]` | `GET /chatbot/messages` 에는 버튼이 없습니다 (1-4 참고) |

**현재 매핑 (전체 목록 8종)**

| `code` | `label` | `url` | 노출 대상 |
|---|---|---|---|
| `RESUME_EDIT` | 이력서 작성하러 가기 | `/mypage/resume` | **프리랜서만** |
| `PROJECT_CREATE` | 프로젝트 등록하기 | `/projects/new` | **클라이언트만** |
| `PAYMENT_METHOD` | 결제수단 관리 | `/mypage/payment-methods` | 공통 |
| `SETTLEMENTS` | 수수료 결제 내역 | `/mypage/settlements` | 공통 |
| `MY_PROJECTS` | 내 프로젝트 보기 | `/my-projects` | 공통 |
| `NEGOTIATION_LIST` | 협상 목록 보기 | `/negotiations` | 공통 |
| `CONTRACTS` | 계약 관리 보기 | `/contracts` | 공통 |
| `INQUIRY_NEW` | 1:1 문의하기 | `/support/inquiries/new` | 공통 |

> ⚠️ **경로가 실제 라우트와 다르면 알려주세요.** 서버 enum 한 줄만 고치면 되고,
> AI 쪽은 건드리지 않습니다. 프론트에서 `url` 을 하드코딩으로 갈아치우지 말아주세요 —
> 다음에 서버가 고쳐도 반영이 안 됩니다.

**추천 질문 6개가 전부 이 버튼들로 연결됩니다.** 칩을 아무거나 눌러도 버튼이 뜹니다.

### 역할에 맞지 않으면 버튼이 빠집니다

프리랜서가 "프로젝트 등록은 어떻게 하나요?" 라고 물으면 AI 는 `PROJECT_CREATE` 를 고르지만,
프리랜서는 프로젝트를 등록할 수 없습니다. 이런 경우 **서버가 버튼만 빼고 답변은 그대로**
내려보냅니다. 눌러도 막히는 버튼을 띄우면 안내가 아니라 오답이 되기 때문입니다.

```json
// 프리랜서 계정으로 "프로젝트 등록 어떻게 해요?" 질문 시
{ "answer": "프로젝트 등록은 클라이언트가 진행합니다...", "actions": [] }
```

**프론트에서 역할 분기를 하지 않아도 됩니다.** 서버가 로그인 계정의 역할로 이미 걸러서 줍니다.

**AI가 목록 밖 값을 뱉어도** 서버가 걸러서 `actions: []` 로 만듭니다.
**답변은 항상 정상 출력됩니다.** 화면이 깨지는 경우는 없습니다.

### 질문 표현이 달라도 동작합니다

키워드 매칭이 아니라 **의도 분류**입니다. 추천 질문을 그대로 입력하지 않아도 됩니다.
실제 AI(`gemini-3.6-flash`)로 확인한 결과입니다.

| 실제 질문 | 결과 |
|---|---|
| 착수금 수수료가 뭐예요? | `SETTLEMENTS` |
| 처음에 내는 돈 얼마임? | `SETTLEMENTS` ← 단어가 전혀 달라도 잡힘 |
| 협상은 몇 번까지 가능해? | `NEGOTIATION_LIST` |
| 계약서는 어떻게 써요? | `CONTRACTS` |
| 프로젝트 등록 어떻게 해? | `PROJECT_CREATE` |
| 이력서 어디서 써요? | `RESUME_EDIT` |
| 카드 어떻게 등록해요? | `PAYMENT_METHOD` |
| 내 프로젝트 진행 상황 알려줘 | `MY_PROJECTS` |
| 안녕하세요 | `NONE` ← 인사에는 버튼 없음 |
| 오늘 날씨 어때? | `NONE` ← 서비스와 무관하면 버튼 없음 |
| 점심 뭐 먹을까? | `NONE` |
| 이 프리랜서에게 500만원 제안해줘 | `NEGOTIATION_LIST` ← 아래 참고 |

**챗봇이 대신 해줄 수 없는 요청**(금액 제안, 조건 변경 등)은 "챗봇은 안내만 한다"고
답하면서 협상 화면으로 보냅니다.

```json
{
  "question": "이 프리랜서에게 500만원 제안해줘",
  "answer": "금액 제안이나 조건 변경 등의 협상 행위는 챗봇이 직접 진행해 드릴 수 없습니다...",
  "actions": [
    { "code": "NEGOTIATION_LIST", "label": "협상 목록 보기", "url": "/negotiations" }
  ]
}
```

다만 **100%는 아닙니다.** 애매한 질문은 `NONE`(버튼 없음)으로 빠질 수 있고,
그때도 답변은 정상입니다. 잘못된 버튼이 뜨는 것보다 안 뜨는 쪽으로 설계했습니다.

### 에러

| 코드 | 상태 | 의미 | 화면 처리 |
|---|---|---|---|
| `CB_003` | `429` | 오늘 10회 다 씀 | 입력창 비활성 + "내일 다시 이용해 주세요" |
| `CB_004` | `502` | AI 서버 장애 / 서킷 오픈 | "일시적인 오류입니다. 1:1 문의를 이용해 주세요" + 문의 버튼 |
| `CB_001` | `404` | 없는 `sessionId` | 세션 버리고 `null` 로 재시도 |
| `CB_002` | `403` | 남의 세션 | 동일 |
| `400` | | 질문이 비었거나 500자 초과 | 입력 검증 |

> **`CB_004` 는 정상 상황에서도 납니다.** AI 서버가 죽으면 서킷 브레이커가 열려서
> 잠시 동안 즉시 실패로 응답합니다(연쇄 장애 방지). 이때 **1:1 문의로 유도**해주세요.

## 1-2. 추천 질문 칩

```
GET /api/v1/support/chatbot/suggested-questions
```

```json
["프로젝트는 어떻게 등록하나요?", "프리랜서 매칭은 어떻게 진행되나요?",
 "무료 재추천은 언제 사용할 수 있나요?", "착수금 수수료가 무엇인가요?",
 "협상은 최대 몇 회까지 가능한가요?", "계약서는 어떻게 작성되나요?"]
```

**문자열 배열**입니다. 칩을 누르면 그 문자열을 그대로 `question` 으로 보내면 됩니다.

> 현재는 **고정 목록**입니다. 조회수 기반 추천은 넣지 않았습니다.
> 목록을 바꾸고 싶으면 알려주세요 — 서버에서 수정합니다.

## 1-3. 남은 횟수

```
GET /api/v1/support/chatbot/quota
```

```json
{ "quotaDate": "2026-08-11", "dailyLimit": 10, "usedCount": 1, "remainingCount": 9 }
```

- **하루 10회**, 자정 초기화
- 질문 응답에도 `remainingQuota` 가 같이 오므로, **이 API는 화면 진입 시 한 번만** 부르면 됩니다
- `remainingCount == 0` 이면 입력창을 비활성화해주세요. 안 그러면 `CB_003` 만 계속 받습니다

## 1-4. 오늘 대화 이력

```
GET /api/v1/support/chatbot/messages
```

```json
[
  { "sessionId": 1200, "question": "...", "answer": "...",
    "actions": [], "remainingQuota": 9, "createdAt": "2026-08-11T17:20:00" }
]
```

- **오늘 것 전체**입니다. 페이징도, 세션 구분도 없습니다. 하루 10개 제한이라 나눌 이유가 없어서요
- 시간 오름차순(오래된 것 → 최신)입니다. 그대로 위에서 아래로 그리면 됩니다
- **`actions` 는 항상 `[]`** 입니다. 아래 참고

> **왜 이력에는 버튼이 없나요?**
> `intent` 를 DB에 저장하지 않습니다. 컬럼을 하나 늘리는 대신, 방금 받은 답변에만
> 버튼을 띄우기로 했습니다. 하루 10질문이라 이력을 되짚어 볼 일이 적어서 내린 판단입니다.
> 이력에도 버튼이 필요하면 말씀해주세요 — DB 컬럼 추가로 가능합니다.

---

# 2. 1:1 문의 (사용자)

## 2-1. 문의 등록

```
POST /api/v1/support/inquiries    성공 시 201
```

```json
{
  "title": "정산 관련 문의드립니다",
  "content": "성공보수 수수료 결제일이 언제인지 확인 부탁드립니다.",
  "fileIds": [42]
}
```

| 필드 | 필수 | 제한 |
|---|---|---|
| `title` | ✅ | 200자 |
| `content` | ✅ | 2,000자 |
| `fileIds` | — | 첨부파일 ID 배열 |

**문의 유형(카테고리)은 없습니다.** 제목 + 내용 + 첨부만 받습니다.

**첨부파일은 먼저 업로드**해서 `fileId` 를 받아 넣습니다.

```
POST /api/v1/files?purpose=INQUIRY_ATTACHMENT
     multipart, 10MB, pdf/jpg/jpeg/png
→ { "fileId": 42, ... }
```

**챗봇을 쓰지 않아도 언제든 접수할 수 있습니다.** 챗봇 → 문의로 이어지는 흐름을
강제하지 않습니다.

## 2-2. 내 문의 목록

```
GET /api/v1/support/inquiries/mine?status=PENDING&page=0&size=10
```

| 파라미터 | 기본값 | 값 |
|---|---|---|
| `status` | 전체 | `PENDING`(대기중) / `ANSWERED`(답변완료) |
| `page` | 0 | |
| `size` | 10 | |

정렬은 **최신순 고정**입니다.

```json
{
  "content": [ /* InquiryResponse */ ],
  "page": 0, "size": 10, "totalElements": 3, "totalPages": 1
}
```

## 2-3. 문의 상세

```
GET /api/v1/support/inquiries/{inquiryId}
```

### `InquiryResponse` — 목록·상세 공통

```json
{
  "inquiryId": 501,
  "inquiryNo": "IQ-2026-000501",
  "writerName": "김개발",
  "writerRole": "FREELANCER",
  "writerEmail": "kimgaebal@dev.kr",
  "title": "정산 관련 문의드립니다",
  "content": "성공보수 수수료 결제일이 언제인지 확인 부탁드립니다.",
  "status": "ANSWERED",
  "answer": "성공보수는 프로젝트 완료 후 정산됩니다.",
  "answererName": "관리자",
  "answeredAt": "2026-08-11T18:00:00",
  "files": [
    { "fileId": 42, "originalName": "계약서.pdf", "fileUrl": "https://cdn.../..." }
  ],
  "createdAt": "2026-08-11T17:00:00"
}
```

| 필드 | 비고 |
|---|---|
| `inquiryNo` | 화면 표시용 문의번호. **관리자 검색에도 이 값으로 검색됩니다** |
| `status` | `PENDING` / `ANSWERED` |
| `answer` · `answererName` · `answeredAt` | **`PENDING` 이면 전부 `null`** |
| `writerName` · `writerRole` · `writerEmail` | 접수 당시 스냅샷 (아래 참고) |
| `files[].fileUrl` | CDN 절대 URL. 그대로 링크에 쓰면 됩니다 |

> **작성자 정보는 접수 시점 스냅샷입니다.** 회원이 이름을 바꾸거나 탈퇴해도 문의에는
> 접수 당시 값이 남습니다. 계정을 실시간 조회하지 않으므로 **탈퇴 회원의 문의도 안전하게 조회됩니다.**

### 에러

| 코드 | 상태 | 의미 |
|---|---|---|
| `IQ_001` | `404` | 없는 문의 |
| `IQ_002` | `403` | 남의 문의 조회 시도 |

---

# 3. 1:1 문의 (관리자)

관리자 권한 필요. 일반 회원이 호출하면 `403 GLOBAL_005`.

## 3-1. 요약 카드

```
GET /api/v1/support/admin/inquiries/summary
```

```json
{ "totalCount": 128, "pendingCount": 7, "answeredCount": 121, "todayCount": 4 }
```

목록 상단 카드 4개(전체 / 답변 대기 / 답변 완료 / 오늘 접수)에 그대로 대응합니다.

## 3-2. 문의 목록 (검색·필터)

```
GET /api/v1/support/admin/inquiries?keyword=김개발&writerRole=FREELANCER&status=PENDING&page=0&size=10
```

| 파라미터 | 설명 |
|---|---|
| `keyword` | **회원명 · 제목 · 문의번호를 한 번에** 검색합니다. 필드 선택 불필요 |
| `writerRole` | `CLIENT` / `FREELANCER` / `ADMIN` |
| `status` | `PENDING` / `ANSWERED` |
| `page`, `size` | 기본 0 / 10 |

전부 **선택**입니다. 아무것도 안 주면 전체 최신순입니다.
응답은 사용자 목록과 같은 `PageResponse<InquiryResponse>` 입니다.

> 검색창 하나로 세 가지가 다 걸립니다. **검색 대상 선택 드롭다운을 만들 필요가 없습니다.**

## 3-3. 답변 등록

```
POST /api/v1/support/admin/inquiries/{inquiryId}/answer
```

```json
{ "answer": "성공보수는 프로젝트 완료 후 정산됩니다." }
```

- `answer` 필수, **2,000자 이하**
- 등록하면 `status` 가 `ANSWERED` 로 바뀌고 **작성자에게 알림이 발송됩니다** (`INQUIRY_ANSWERED`)
- 응답으로 갱신된 `InquiryResponse` 가 오니 **목록을 다시 조회할 필요 없습니다**
- 에러: `IQ_001`(없는 문의), `400`(답변 비었거나 2,000자 초과)

> **답변 수정·삭제 API는 없습니다.** 같은 문의에 다시 `POST` 하면 답변이 덮어써집니다.

---

# 4. 화면 구현 팁

## 챗봇 화면

```
진입
 ├── GET /chatbot/quota              남은 횟수 (1회)
 ├── GET /chatbot/suggested-questions 칩 (1회)
 └── GET /chatbot/messages           오늘 대화 복원 (1회)

질문 전송
 └── POST /chatbot/questions
      ├── 응답의 sessionId 를 보관 → 다음 질문에 사용
      ├── 응답의 remainingQuota 로 카운터 갱신 (quota 재호출 불필요)
      └── actions 있으면 버튼 렌더
```

- `remainingQuota` 가 0이 되면 **즉시 입력창 비활성화**. 서버 응답을 기다리지 마세요
- `CB_004`(AI 장애)는 채팅 버블 안에 에러 메시지로 표시하고 **1:1 문의 버튼**을 같이 띄우면 자연스럽습니다

## 문의 등록 화면

```
첨부 선택  → POST /api/v1/files?purpose=INQUIRY_ATTACHMENT  (파일마다 1회)
"등록"    → POST /api/v1/support/inquiries  { title, content, fileIds: [...] }
```

- 업로드만 하고 문의 등록을 안 하면 **파일이 어디에도 연결되지 않습니다.** 임시 파일로 남습니다
- 사용자가 첨부를 취소하면 `DELETE /api/v1/files/{fileId}` 로 지워주세요

## 문의 상세 화면

`status` 로 답변 영역을 분기하면 됩니다.

```jsx
{data.status === 'ANSWERED'
  ? <AnswerBox answer={data.answer} by={data.answererName} at={data.answeredAt} />
  : <PendingNotice />}
```

`PENDING` 이면 `answer` 관련 3필드가 모두 `null` 이니 옵셔널 체이닝 없이 `status` 만 보면 됩니다.

---

# 5. 체크리스트

## 챗봇

- [ ] `actions` 배열로 버튼 렌더 (답변 텍스트 파싱 ❌)
- [ ] `url` 을 프론트에서 하드코딩하지 말고 응답 값 사용
- [ ] `actions` 경로 **8개**가 실제 라우트와 맞는지 확인 → 다르면 서버에 알려주기
- [ ] 역할 분기 직접 하지 말 것 — 서버가 이미 걸러서 줌
- [ ] `remainingQuota` 0이면 입력창 비활성
- [ ] `CB_003`(한도) / `CB_004`(AI 장애) 문구 분리
- [ ] `CB_004` 에 1:1 문의 유도 버튼
- [ ] 이력 조회는 버튼 없음 (`actions: []`)

## 1:1 문의

- [ ] 문의 유형(카테고리) 입력 제거 — 서버에 없음
- [ ] 첨부는 파일 업로드 → `fileIds` 로 전달
- [ ] `PENDING` / `ANSWERED` 분기
- [ ] `inquiryNo` 표시

## 관리자

- [ ] 요약 카드 4개
- [ ] 검색창 하나로 회원명·제목·문의번호 (드롭다운 불필요)
- [ ] `writerRole` · `status` 필터
- [ ] 답변 등록 후 응답으로 화면 갱신 (목록 재조회 불필요)

---

# 6. 알려진 제약

| 항목 | 상태 |
|---|---|
| 추천 질문 | 고정 목록 6개. 조회수 기반 추천은 미구현 |
| 대화 이력의 버튼 | 저장하지 않아 항상 `[]` |
| 답변 수정·삭제 | API 없음. 재등록으로 덮어씀 |
| 문의 삭제 | API 없음 |

---

문의는 편하게 주세요.
