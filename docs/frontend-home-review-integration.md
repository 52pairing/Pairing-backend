# 메인 노출 리뷰 프론트 연동 가이드

> 담당: 리뷰 · 등급 · 마이페이지 · 이력서 · 결제수단 · 1:1문의 · 챗봇 · 알림 · 홈
> 기준일: 2026-08-13
> 마이페이지 → `frontend-mypage-integration.md` / 고객지원 → `frontend-support-integration.md`
> 알림 → `frontend-notification-integration.md` / 회원 탈퇴 → `frontend-withdrawal-integration.md`

---

## 0. 한눈에 보기

```
GET /api/v1/home/site-reviews?size=6
```

**로그인 불필요.** 비로그인 방문자에게 보여주는 영역이라 토큰 없이 호출합니다.
`credentials: 'include'` 도 필요 없습니다.

관리자가 **홍보 활용**으로 켠 **4점 이상** 후기만 내려갑니다. 작성자명은 마스킹됩니다.

---

## 1. 요청

| 파라미터 | 필수 | 기본값 | 범위 | 설명 |
|---|---|---|---|---|
| `size` | — | `6` | **1 ~ 20** | 가져올 개수 |

```
GET /api/v1/home/site-reviews          → 6개
GET /api/v1/home/site-reviews?size=3   → 3개
```

**범위를 벗어나면 `400 GLOBAL_002`** 입니다. 비로그인 API 라 상한을 걸어뒀습니다 —
후기마다 프로젝트·계정을 함께 읽어서 개수가 커지면 조회가 그만큼 늘어납니다.

정렬은 **최신순 고정**입니다(`createdAt DESC`). 별점순·랜덤은 아직 없습니다.

---

## 2. 응답

```json
{
  "code": "SITE_REVIEWS_FOUND",
  "message": "조회에 성공했습니다.",
  "data": [
    {
      "siteReviewId": 950,
      "writerRole": "FREELANCER",
      "writerName": "이**",
      "score": 5,
      "content": "매칭 속도가 빠르고 AI 협상 기능이 정말 유용했습니다.",
      "projectTitle": "페어링 웹 리뉴얼",
      "promoted": true,
      "createdAt": "2026-08-01T09:30:00"
    }
  ]
}
```

**페이징이 아닙니다.** `data` 가 배열 그대로입니다. `content` / `totalElements` 같은 껍데기가 없습니다.

| 필드 | 설명 |
|---|---|
| `siteReviewId` | 후기 ID. 리스트 `key` 로 쓰세요 |
| `writerRole` | `CLIENT` / `FREELANCER` — 뱃지 문구 분기용 |
| `writerName` | **마스킹된 이름.** 아래 3번 참고 |
| `score` | 1~5. **여기 내려오는 건 항상 4 또는 5** 입니다 |
| `content` | 후기 본문. **`null` 일 수 있습니다** |
| `projectTitle` | 대상 프로젝트명. **`null` 일 수 있습니다** |
| `promoted` | 항상 `true` (조건상 다른 값이 올 수 없음) |
| `createdAt` | 작성 시각 |

> **`visibility` 필드는 없어졌습니다.** (2026-08-13) 공개/비공개 개념 자체를 없앴습니다.
> 응답에서 이 필드를 읽고 계셨다면 지워주세요. 나머지는 그대로입니다.

### null 이 오는 경우

- **`content`** — 별점만 주고 글은 안 쓴 후기입니다. 카드에 빈 영역이 생기지 않게 처리해주세요
- **`projectTitle`** — 프로젝트가 삭제된 경우입니다. 후기 자체는 그대로 보여줍니다

`promoted` 는 이 API 에서는 항상 `true` 라 **화면에서 쓸 일이 없습니다.**
관리자 화면과 응답 형태를 맞추느라 남아 있는 필드입니다.

---

## 3. 작성자명 마스킹

**서버가 마스킹해서 내려줍니다. 프론트에서 가공하지 마세요.**

| 원본 | 응답 | 규칙 |
|---|---|---|
| 이프리 | `이**` | 첫 글자만 남기고 `*` |
| 주식회사 페어링 | `주*******` | 클라이언트는 **회사명** 기준 |
| (이름 없음) | `프리랜서` / `클라이언트` | 탈퇴 등으로 이름을 못 찾으면 역할명 |

클라이언트는 계정 이름이 아니라 **회사명**을 씁니다. 프리랜서는 계정 이름입니다.

---

## 4. 어떤 후기가 여기 나오나

두 조건을 **모두** 만족해야 합니다.

| 조건 | 값 | 정하는 사람 |
|---|---|---|
| 홍보 활용 | `true` | **관리자가 직접 켬** |
| 별점 | **4점 이상** | 서버 고정값 |

> 예전에는 `공개 여부` 조건이 하나 더 있었는데 없앴습니다. **후기 원문은 관리자 화면 밖으로
> 나가지 않아서**, 홍보를 끄면 이미 안 보이는 상태였습니다. 아무것도 바꾸지 않는 스위치였습니다.

**홍보 활용은 관리자가 켜야 합니다.** 후기를 아무리 많이 써도 관리자가 켜지 않으면
이 API 는 **빈 배열**을 돌려줍니다.

```json
{ "code": "SITE_REVIEWS_FOUND", "message": "조회에 성공했습니다.", "data": [] }
```

**빈 배열이 정상 응답입니다.** 초기에는 홍보로 켠 후기가 없어 자주 볼 수 있으니,
**후기 영역을 통째로 숨기거나 안내 문구를 넣는 처리**를 꼭 넣어주세요. 에러가 아닙니다.

> 홍보 지정 개수에는 제한이 없습니다. 관리자가 50건을 켜도 이 API 는 `size` 만큼(최신순) 내려줍니다.

---

## 5. 화면 구현 예시

```js
async function loadHomeReviews() {
  const res = await fetch(`${API_BASE}/api/v1/home/site-reviews?size=6`);
  if (!res.ok) return [];              // 메인은 후기 없이도 떠야 한다
  const { data } = await res.json();
  return data;
}
```

**후기 조회가 실패해도 메인 전체가 깨지지 않게 해주세요.** 이 영역은 부가 정보입니다.

```jsx
{reviews.length > 0 && (
  <section>
    {reviews.map(r => (
      <ReviewCard
        key={r.siteReviewId}
        stars={r.score}
        name={r.writerName}
        role={r.writerRole === 'CLIENT' ? '클라이언트' : '프리랜서'}
        text={r.content ?? ''}
        project={r.projectTitle}
      />
    ))}
  </section>
)}
```

---

## 6. 에러

| 응답 | 언제 |
|---|---|
| `400 GLOBAL_002` | `size` 가 1~20 범위를 벗어남 |
| `500 GLOBAL_001` | 서버 오류 — **`traceId` 와 함께 알려주세요** |

인증이 필요 없으므로 `401` / `403` 은 나오지 않습니다.

---

## 7. 같은 화면의 다른 API (아직 스켈레톤)

메인에는 이 두 개도 있는데 **고정 응답을 돌려주는 상태**입니다.
프로젝트·협상 등 다른 도메인 집계가 필요해서 아직 실제 값이 아닙니다.

```
GET /api/v1/home/summary   메인 상단 지표 (완수율·누적 프로젝트 등)
GET /api/v1/home/faqs      자주 찾는 질문
```

**응답 형태는 바뀌지 않으니 붙여두셔도 됩니다.** 값만 나중에 실제 집계로 교체됩니다.

---

문의는 편하게 주세요.
