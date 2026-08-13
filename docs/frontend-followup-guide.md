# 프론트 연동 가이드 — 후속 4건

2026-08-13 정리. 프로젝트 · 계약 도메인 담당(3번) 작성.

| # | 작업 | 서버 상태 |
|---|---|---|
| 1 | 사전 검수 문구 "예상 후보 수" → "매칭 가능 인원" | **변경 없음** (프론트 라벨) |
| 2 | 프로젝트 상세 첨부자료 다운로드 | **신규 API** (머지 후 사용 가능) |
| 3 | 종료된 프로젝트 상세 우측 리뷰 | 기존 API |
| 4 | 내 프로젝트 · 내 계약 탭 배지 | 프로젝트는 기존, 계약은 **신규** |

기본 규약(응답 봉투, 페이지 형태, 에러)은
[frontend-screen-api-guide.md](frontend-screen-api-guide.md) §0 을 따릅니다.

---

## 1. 사전 검수 문구 변경

### 서버 작업 없음

"예상 후보 수" 라는 문자열은 **API 응답에 없습니다.** Swagger 필드 설명일 뿐이고,
서버가 보내는 건 숫자 하나입니다.

```
POST /api/v1/projects/pre-review          등록 5단계
GET  /api/v1/projects/{projectId}/pre-review   등록 후 재조회
```

```json
{
  "allMatchable": true,
  "items": [
    {
      "positionIndex": 0,
      "jobRole": "BACKEND",
      "headcount": 2,
      "expectedCandidateCount": 5,
      "matchable": true,
      "message": null,
      "suggestions": []
    }
  ],
  "notice": "현재 프리랜서 풀 기준 예상 결과입니다. 실제 후보 수 및 매칭 성사 여부는 달라질 수 있습니다."
}
```

**필드명은 `expectedCandidateCount` 로 그대로 둡니다.** 화면 라벨만 바꾸면 됩니다.

```diff
- 예상 후보 수 {expectedCandidateCount}명
+ 매칭 가능 인원 {expectedCandidateCount}명
```

`positionIndex` 는 요청 `positions` 배열의 순서(0-based)입니다. 카드 매핑에 쓰세요.
응답은 요청과 **같은 순서·같은 개수**로 옵니다.

`message` 와 `suggestions` 는 `matchable` 이 false 일 때만 값이 있습니다.
`suggestions` 는 화면에 불릿으로 그대로 찍으면 됩니다.

### ⚠️ 라벨을 바꾸기 전에 알아두실 것

이 숫자는 **실제로 추천되는 인원이 아닙니다.** "매칭 가능 인원"으로 부르면
사용자가 그만큼 받는다고 읽습니다.

| | 사전 검수 | AI 매칭 |
|---|---|---|
| 요구 스킬 | **전부 보유** | 1개 이상 |
| 매칭 일시정지 | 안 봄 | 봄 |
| 임베딩(벡터) 존재 | 볼 수 없음 | 사실상 필수 |
| 이미 노출된 후보 | 안 봄 | 제외 |
| 실제 노출 수 | — | **모집 인원만큼** |

아래 세 줄은 전부 "안내보다 적게 나오는" 방향입니다.
모집 1명이면 사전 검수가 560명이라 해도 화면에 뜨는 후보는 1명입니다.

서버가 주는 `notice` 문구가 이 간극을 덮는 역할을 하고 있으니 **함께 노출하세요.**
라벨만 단정적으로 바꾸고 notice 를 빼면 클레임 여지가 커집니다.

---

## 2. 프로젝트 첨부자료 다운로드

**핵심: 미리보기와 다운로드는 경로가 다릅니다.**

프로젝트 상세(`GET /api/v1/projects/{projectId}`) 응답의 `files[]` 는 이렇게 옵니다.

```json
"files": [
  { "fileId": 1, "originalName": "그랩.jpg", "sizeBytes": 122700,
    "fileUrl": "https://cdn.../project/9f3c....jpg" }
]
```

| 용도 | 쓸 것 |
|---|---|
| 미리보기 (`<img>`, 새 탭) | `fileUrl` |
| **다운로드 (저장)** | `GET /api/v1/projects/{projectId}/files/{fileId}/download` |

### `fileUrl` 로는 다운로드가 안 됩니다

CDN 직링크라 `Content-Disposition` 이 없어서 브라우저가 이미지·PDF 를 그냥 띄웁니다.
지금 "다운로드를 눌렀는데 사진이 열린다"는 증상이 이것입니다.

`<a href={fileUrl} download>` 도 안 됩니다. **cross-origin URL 에서는 `download` 속성이
무시되는 게 스펙**이고, CDN 은 앱과 다른 도메인입니다.

### 구현

```js
const res = await api.get(
  `/projects/${projectId}/files/${file.fileId}/download`,
  { responseType: 'blob' }
);

const url = URL.createObjectURL(res.data);
const a = document.createElement('a');
a.href = url;
a.download = file.originalName;   // ← 헤더가 아니라 목록의 값
a.click();
URL.revokeObjectURL(url);
```

계약서 PDF 와 같은 패턴입니다. **`responseType: 'blob'` 이 빠지면 파일이 깨집니다.**

### 파일명은 헤더에서 못 읽습니다

서버가 `Content-Disposition` 을 붙여 보내지만 CORS 노출 목록이
`Authorization`, `X-Trace-Id` 둘뿐이라 JS 가 접근할 수 없습니다.

**상세 응답의 `files[].originalName` 을 그대로 쓰세요.** 이미 갖고 있는 값이라
추가 조회가 없습니다. 계약서 PDF 는 이 값이 없어 화면에서 이름을 지어 쓰는데,
첨부는 그럴 필요가 없습니다.

### 응답

`Content-Type` 은 항상 `application/octet-stream` 입니다. 첨부가 jpg·pdf·zip
아무거나 올 수 있어서 브라우저가 타입을 따지지 않고 저장하도록 고정했습니다.
미리보기가 필요하면 `fileUrl` 을 쓰세요.

### 에러

| 상황 | 응답 |
|---|---|
| 남의 프로젝트 | `403 PJ_003` |
| 그 프로젝트에 없는 `fileId` | `404 FI_001` |
| 파일이 지워졌거나 스토리지 오류 | `404 FI_001` |
| 프리랜서 계정 | `403 ACCESS_DENIED` |

열람 범위는 **프로젝트 상세 조회와 동일**합니다 — 본인이 등록한 프로젝트만.
`fileId` 를 임의로 넣어도 그 프로젝트에 달린 첨부인지 서버가 확인합니다.

---

## 3. 종료된 프로젝트 상세 — 우측 리뷰

```
GET /api/v1/reviews/written?page=0&size=10
```

```json
{
  "reviewId": 900,
  "contractId": 600,
  "projectTitle": "B2B 주문 관리 서비스 리뉴얼",
  "reviewerName": "주식회사 페어링",
  "reviewerRole": "CLIENT",
  "score": 5,
  "content": "협업이 원활하고 결과물의 완성도가 높았습니다.",
  "createdAt": "2026-08-13T09:08:13"
}
```

최신순(`createdAt DESC`)입니다.

### ⚠️ 프로젝트별 조회가 없습니다

전체 목록에서 골라야 하는데, **응답에 `projectId` 가 없습니다.**

`projectTitle` 문자열 비교는 동명 프로젝트에서 틀립니다. **`contractId` 로 매칭하세요.**

그 프로젝트의 `contractId` 목록은 이미 있는 API 로 얻습니다.

```
GET /api/v1/contracts?projectId={projectId}
```

```js
const contractIds = new Set(contracts.map(c => c.contractId));
const reviewsOfThisProject = written.filter(r => contractIds.has(r.contractId));
```

### ⚠️ 리뷰가 여러 건입니다

계약 인원이 여러 명이면 리뷰도 그 수만큼입니다. 시안의 프로젝트는 **계약 프리랜서 3명**이라
"작성한 리뷰" 카드도 3개여야 합니다. 1건만 그리면 나머지가 사라집니다.

### 작성 여부는 상태로 판정하지 마세요

```
GET /api/v1/reviews/pending
```

> 성공보수 수수료까지 결제되어 프로젝트가 종료된 계약 중 아직 리뷰를 쓰지 않은 건

P51 판정을 서버가 이미 해 줍니다. 프로젝트 상태를 보고 프론트가 계산하지 마세요.

```
pending 에 있음   → "리뷰 작성" 버튼
pending 에 없음   → written 에서 찾아 별점·내용 표시
```

`contractId` 를 그대로 작성 요청에 넣으면 됩니다.

### 작성

```
POST /api/v1/reviews
```

**작성 후 수정·삭제할 수 없습니다.** 확인 모달이 필요합니다.
이미 쓴 계약이면 `ALREADY_REVIEWED` 입니다.

프리랜서가 받은 리뷰는 `GET /api/v1/reviews/received` 로 같은 형태입니다.

---

## 4. 탭 숫자 배지

```
클라이언트 내 프로젝트   GET /api/v1/projects/mine/tab-counts
계약관리 · 내 계약      GET /api/v1/contracts/mine/tab-counts
프로젝트 상세의 계약 탭   GET /api/v1/contracts/mine/tab-counts?projectId=17
```

파라미터 없이 부르면 됩니다. `accountId` 는 토큰에서 꺼냅니다.

### 응답 — 필드가 하나 다릅니다

**프로젝트**

```json
"data": [
  { "tab": "REGISTERED",         "status": null, "label": "등록 완료", "count": 0 },
  { "tab": "MATCHING",           "status": null, "label": "매칭중",   "count": 2 },
  { "tab": "IN_PROGRESS",        "status": null, "label": "진행 중",  "count": 0 },
  { "tab": "COMPLETION_PENDING", "status": null, "label": "완료 대기", "count": 0 },
  { "tab": "CLOSED",             "status": null, "label": "종료",     "count": 1 },
  { "tab": "CANCELED",           "status": null, "label": "취소됨",   "count": 0 }
]
```

`status` 는 **관리자 화면 전용**입니다. 클라이언트 응답에서는 항상 `null` 이니 무시하세요.

**계약** — `status` 필드가 없습니다.

```json
"data": [
  { "tab": "ALL",                  "label": "전체",           "count": 5 },
  { "tab": "AWAITING_ME",          "label": "서명 대기",       "count": 1 },
  { "tab": "AWAITING_COUNTERPART", "label": "상대방 서명 대기", "count": 1 },
  { "tab": "CONCLUDED",            "label": "체결 완료",       "count": 3 },
  { "tab": "IN_PROGRESS",          "label": "진행 중",         "count": 2 },
  { "tab": "SETTLEMENT_PENDING",   "label": "정산 대기",       "count": 1 },
  { "tab": "COMPLETED",            "label": "완료",           "count": 0 }
]
```

### 계약은 7개가 오는데 화면은 4~5개만 그립니다

두 화면이 탭을 나눠 씁니다. **서버가 역할로 가르지 않습니다** — 한 계정이 클라이언트이면서
프리랜서일 수 있어서입니다.

```js
const CLIENT_TABS     = ['ALL', 'AWAITING_ME', 'AWAITING_COUNTERPART', 'CONCLUDED'];
const FREELANCER_TABS = ['ALL', 'AWAITING_ME', 'IN_PROGRESS', 'SETTLEMENT_PENDING', 'COMPLETED'];
```

### ⚠️ 계약은 탭 합계가 `ALL` 과 다릅니다

범위가 겹칩니다.

```
CONCLUDED          = SIGNED · IN_PROGRESS · COMPLETION_PENDING · COMPLETED
SETTLEMENT_PENDING = COMPLETION_PENDING          ← CONCLUDED 안에 포함
```

같은 계약 1건을 클라이언트는 "체결 완료", 프리랜서는 "정산 대기"로 부릅니다.
**합계 검증 로직을 넣지 마세요.** 프로젝트 쪽은 상태가 겹치지 않아 합계가 맞습니다.

### 구현

```js
const [counts, setCounts] = useState({});

const loadCounts = useCallback(async () => {
  const { data } = await api.get('/contracts/mine/tab-counts');
  setCounts(Object.fromEntries(data.data.map(r => [r.tab, r.count])));
}, []);

useEffect(() => { loadCounts(); }, [loadCounts]);
```

렌더링은 **서버가 준 `label`** 을 쓰세요. 탭 이름을 프론트에 하드코딩하면 서버에서
라벨을 바꿀 때 목록과 탭 이름이 어긋납니다.

```jsx
{FREELANCER_TABS.map(tab => {
  const row = data.find(r => r.tab === tab);
  return (
    <Tab key={tab} active={current === tab} onClick={() => setCurrent(tab)}>
      {row.label}
      {row.count > 0 && <Badge>{row.count}</Badge>}
    </Tab>
  );
})}
```

**건수가 0인 탭도 내려옵니다.** 키가 항상 있어 `undefined` 방어는 필요 없지만,
배지는 **0이면 숨기는 게** 자연스럽습니다. 시안도 `검토 중 1`, `협상 중 1` 만 붙어 있습니다.

### 언제 다시 부르나

**탭을 전환할 때는 부르지 마세요.** 숫자가 안 바뀝니다. 목록만 다시 받으면 됩니다.

건수가 변하는 액션 직후에만 다시 부릅니다.

| 화면 | 다시 부를 때 |
|---|---|
| 내 프로젝트 | 등록 취소, 모집 종료, 완료 처리, 착수금 결제 |
| 계약관리 · 내 계약 | **서명**, 착수금·성공보수 결제 |

서명이 가장 중요합니다. `서명 대기` → `상대방 서명 대기` 로 옮겨가 배지 두 개가 동시에 바뀝니다.

```js
await api.post(`/contracts/${contractId}/signature`, body);
await Promise.all([refetchList(), loadCounts()]);
```

---

## 배포 상태

| 항목 | 지금 |
|---|---|
| 1. 사전 검수 문구 | 서버 변경 없음 → **바로 작업 가능** |
| 2. 첨부 다운로드 | 신규 API, 미머지 → **머지 후** |
| 3. 리뷰 | 기존 API → **바로 작업 가능** |
| 4-a. 프로젝트 탭 배지 | 기존 API → **바로 작업 가능** |
| 4-b. 계약 탭 배지 | 신규 API, 미머지 → **머지 후** |

2번과 4-b 는 develop 머지 전이라 지금 호출하면 404 입니다.
Swagger 에 안 보이면 서버 재시작 후 **강력 새로고침**(`Ctrl+Shift+R`)하세요 —
Swagger UI 는 페이지 로드 시점의 스펙을 그대로 씁니다.

---

## 함정 요약

1. **첨부는 `fileUrl` 로 다운로드가 안 됩니다.** cross-origin 이라 `download` 속성이 무시됩니다.
2. **`responseType: 'blob'`** 이 빠지면 파일이 깨집니다.
3. **파일명은 `originalName`** 에서 가져오세요. 헤더는 CORS 로 막혀 있습니다.
4. **리뷰는 `contractId` 로 매칭**하세요. `projectId` 가 응답에 없고 제목 비교는 틀립니다.
5. **리뷰는 인원수만큼** 있습니다. 1건만 그리면 안 됩니다.
6. **리뷰 작성 가능 여부는 `/reviews/pending`** 이 판정합니다. 상태로 계산하지 마세요.
7. **계약 탭 합계 ≠ 전체.** 범위가 겹칩니다.
8. **탭 전환 시 배지를 다시 부르지 마세요.** 서명·결제 직후에만 부릅니다.
9. **탭 라벨은 서버가 줍니다.** 하드코딩하면 목록과 어긋납니다.
10. **사전 검수 숫자는 실제 추천 인원이 아닙니다.** `notice` 를 함께 노출하세요.
