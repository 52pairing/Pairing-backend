# 알림 프론트 연동 가이드

> 담당: 알림 · 리뷰 · 등급 · 마이페이지 · 이력서 · 결제수단 · 1:1문의 · 챗봇
> 기준일: 2026-08-13 (관리자 서버발 알림 동작 명시)
> 마이페이지 → `frontend-mypage-integration.md` / 고객지원 → `frontend-support-integration.md`
> 회원 탈퇴 → `frontend-withdrawal-integration.md` / 메인 후기 → `frontend-home-review-integration.md`

---

## 0. 한눈에 보기

| 기능 | Method | Path |
|---|---|---|
| 알림 목록 | `GET` | `/api/v1/notifications?unreadOnly=false&page=0&size=20` |
| 안 읽은 개수 (헤더 배지) | `GET` | `/api/v1/notifications/unread-count` |
| 읽음 처리 | `PUT` | `/api/v1/notifications/{notificationId}/read` |
| 모두 읽음 | `PUT` | `/api/v1/notifications/read-all` |
| 삭제 | `DELETE` | `/api/v1/notifications/{notificationId}` |
| 모두 삭제 | `DELETE` | `/api/v1/notifications` |
| **실시간 수신** | STOMP | `/topic/users/{accountId}/notifications` |

전부 **로그인 필요**. HttpOnly 쿠키 `accessToken` 이 실려가므로 `credentials: 'include'`.

---

## 1. 알림 객체

목록·실시간 모두 같은 모양입니다.

```json
{
  "notificationId": 87,
  "type": "MATCHING_REQUESTED",
  "title": "매칭 요청이 도착했습니다",
  "content": "주식회사 오이랩이 B2B 주문 관리 서비스 리뉴얼 프로젝트에 매칭을 요청했습니다.",
  "linkUrl": "/matchings/requests/123",
  "read": false,
  "createdAt": "2026-08-11T17:20:00"
}
```

| 필드 | 설명 |
|---|---|
| `type` | 아이콘 분기용 (아래 목록) |
| `title` · `content` | **서버가 완성된 문구로 내려줍니다.** 프론트에서 조립하지 마세요 |
| `linkUrl` | **알림 클릭 시 이동할 프론트 경로.** 그대로 `navigate()` 하면 됩니다 |
| `read` | 읽음 여부 |

> `linkUrl` 이 `null` 인 알림도 있을 수 있습니다. 그때는 클릭해도 이동하지 않게 처리해주세요.

### `type` 목록

| type | 라벨 | 받는 사람 |
|---|---|---|
| `MATCHING_RECOMMENDED` | 추천 완료 | 클라이언트 |
| `MATCHING_REQUESTED` | 매칭 요청 도착 | 프리랜서 |
| `MATCHING_ACCEPTED` | 매칭 요청 수락 | 클라이언트 |
| `MATCHING_REJECTED` | 매칭 요청 거절 | 클라이언트 |
| `NEGOTIATION_STARTED` | 협상 시작 | 양쪽 |
| `NEGOTIATION_PROPOSED` | 새로운 AI 제안 | 양쪽 |
| `NEGOTIATION_FAILED` | 협상 결렬 | 양쪽 |
| `CONTRACT_CREATED` | 계약서 생성 | 양쪽 |
| `CONTRACT_SIGNED` | 계약서 서명 · **체결 완료** | 한쪽 서명 → 상대방 / 체결 → 양쪽 |
| `SETTLEMENT_DUE` | 수수료 결제 안내 | 납부자 |
| `INQUIRY_ANSWERED` | 1:1 문의 답변 | 문의 작성자 |

> ⚠️ 이 중 **일부는 아직 발행되지 않습니다.** 6번 「현재 상태」 참고.
> 타입 값 자체는 확정이니 아이콘 매핑은 미리 다 만들어두셔도 됩니다.

#### `CONTRACT_SIGNED` 는 두 가지 상황에서 옵니다

명세상 계약 알림은 **서명 시점**과 **체결 시점** 두 번인데, **타입 값은 하나입니다.**
구분은 `title` 로 하시면 됩니다.

| 상황 | 받는 사람 | `title` |
|---|---|---|
| 한쪽만 서명 | 서명 안 한 상대방 | `상대방이 계약서에 서명했습니다` |
| 양측 서명 완료 → 체결 | **양쪽 모두** | `계약이 체결되었습니다` |

`linkUrl` 은 둘 다 `/contracts/{contractId}` 로 같습니다. 문구가 명확해서 **아이콘을
따로 나눌 필요가 없다고 보고 타입을 늘리지 않았습니다.** 아이콘 분기가 꼭 필요하시면
말씀해주세요. 타입을 하나 추가하겠습니다.

> `CONTRACT_REJECTED`(계약서 거절)는 **요구사항 명세에서 빠졌습니다.**(2026-08-12 개정)
> enum 값은 남아 있지만 발행되지 않으니 화면을 만들지 않으셔도 됩니다.

#### `INQUIRY_ANSWERED` 만 실시간 push 가 안 됩니다

1:1 문의 답변은 **관리자 서버**가 등록합니다. 그런데 WebSocket 세션을 들고 있는 건
사용자 서버라, 관리자 서버가 넣은 알림은 **STOMP 로 밀어줄 수가 없습니다.**

| 알림 | 만드는 곳 | 실시간 push |
|---|---|---|
| `INQUIRY_ANSWERED` | 관리자 서버 | ❌ |
| 나머지 전부 | 사용자 서버 | ✅ |

**알림 자체는 정상적으로 저장됩니다.** 목록 조회·읽음·배지 다 똑같이 동작하고,
토스트만 안 뜹니다. 사용자가 알림함을 열거나 새로고침하면 보입니다.

문의 답변은 사용자가 기다리는 상태라 놓칠 성격이 아니어서 지금은 이대로 둡니다.
관리자발 알림이 늘어나면 서버 간 내부 API 를 두는 방식으로 바꿀 예정이고,
**그때도 프론트 수정은 없습니다.**

---

## 2. REST API

### 2-1. 목록

```
GET /api/v1/notifications?unreadOnly=false&page=0&size=20
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `unreadOnly` | `false` | `true` 면 안 읽은 것만 |
| `page` / `size` | 0 / 20 | |

최신순 고정입니다.

```json
{
  "content": [ /* 알림 객체 */ ],
  "page": 0, "size": 20, "totalElements": 42, "totalPages": 3
}
```

### 2-2. 안 읽은 개수 (헤더 배지)

```
GET /api/v1/notifications/unread-count
```

```json
{ "unreadCount": 3 }
```

헤더의 빨간 숫자 배지에 씁니다. **실시간 알림을 받으면 이 값을 다시 부르지 말고
프론트에서 +1 하세요.** (STOMP 로 이미 알림이 왔으므로)

### 2-3. 읽음 / 삭제

```
PUT    /api/v1/notifications/{notificationId}/read   읽음
PUT    /api/v1/notifications/read-all                모두 읽음
DELETE /api/v1/notifications/{notificationId}        삭제
DELETE /api/v1/notifications                         모두 삭제
```

- **알림을 클릭하면 읽음 처리 + `linkUrl` 이동**을 같이 해주세요
- 읽음/삭제 API는 응답 본문이 없습니다. 성공하면 프론트 상태만 갱신하면 됩니다
- 남의 알림을 건드리면 `403`

---

## 3. 실시간 수신 (STOMP)

**REST만 붙이면 새로고침해야 알림이 보입니다.** 실시간은 STOMP 구독이 필요합니다.

### 연결

```
엔드포인트   /api/ws          (SockJS 아님, 순수 WebSocket)
브로커       /topic, /queue
구독 경로    /topic/users/{accountId}/notifications
```

`accountId` 는 로그인 응답이나 `GET /api/v1/auth/me` 로 알 수 있습니다.

### 인증

핸드셰이크 시 **쿠키의 `accessToken` 으로 인증**합니다.
별도 헤더를 넣을 필요 없이, 같은 도메인이면 브라우저가 알아서 실어 보냅니다.

### 예시

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: `${WS_BASE}/api/ws`,
  reconnectDelay: 5000,          // 끊기면 자동 재연결
  onConnect: () => {
    client.subscribe(`/topic/users/${accountId}/notifications`, (message) => {
      const noti = JSON.parse(message.body);
      pushToast(noti);           // 토스트
      prependToList(noti);       // 목록 맨 위에 추가
      incrementBadge();          // 배지 +1
    });
  },
});
client.activate();
```

수신 payload는 1번의 알림 객체와 같습니다. 다만 **`read` 필드가 없습니다** — 방금 온 알림은 당연히 안 읽음이라서요. 프론트에서 `read: false` 로 채워 넣으세요.

### 언제 오나

**트랜잭션이 커밋된 뒤에만 발송됩니다.** 예를 들어 매칭 수락 처리가 중간에 실패해 롤백되면
알림도 나가지 않습니다. "알림은 왔는데 실제로는 안 바뀐" 상태가 생기지 않습니다.

### 끊겼을 때

`reconnectDelay` 로 자동 재연결하되, **재연결 시 목록을 다시 조회**해주세요.
끊긴 동안 온 알림은 STOMP 로 다시 오지 않습니다. (브로커가 메시지를 보관하지 않습니다)

---

## 4. 화면 구현 흐름

```
로그인 직후
 ├── GET /notifications/unread-count      배지 초기값
 └── STOMP 연결 + 구독

알림 아이콘 클릭
 └── GET /notifications?page=0&size=20    드롭다운/페이지

알림 항목 클릭
 ├── PUT /notifications/{id}/read
 └── navigate(noti.linkUrl)

실시간 수신
 ├── 토스트 표시
 ├── 목록 맨 위에 추가
 └── 배지 +1  (unread-count 재호출 불필요)

"모두 읽음"
 ├── PUT /notifications/read-all
 └── 배지 0, 목록 전체 read: true
```

---

## 5. 주의사항

- **문구를 프론트에서 만들지 마세요.** `title`·`content` 가 완성된 문장으로 옵니다.
  `type` 으로 문구를 조립하면 서버가 문구를 바꿔도 반영되지 않습니다
- **`linkUrl` 을 프론트에서 재구성하지 마세요.** 경로 규칙이 도메인마다 다릅니다
- **배지는 STOMP 수신 시 직접 증가**시키세요. 매번 `unread-count` 를 부르면 낭비입니다
- 실시간이 안 붙어 있어도 REST 만으로 동작합니다. **STOMP 는 UX 향상이지 필수 의존이 아닙니다**

---

## 6. 현재 상태 — 발행되는 알림 / 안 되는 알림

요구사항 명세와 코드를 대조한 결과입니다. **명세가 요구하는 알림 중 안 오는 건 협상 3종뿐입니다.**

### ✅ 지금 실제로 오는 것

| type | 명세 항목 |
|---|---|
| `MATCHING_RECOMMENDED` | 클라 1 — 추천 완료 |
| `MATCHING_REQUESTED` | 프리 1 — 매칭 요청 |
| `MATCHING_ACCEPTED` | 클라 2 — 수락 |
| `MATCHING_REJECTED` | 클라 2 — 거절 |
| `CONTRACT_CREATED` | 클라 5 / 프리 4 — 계약서 생성 |
| `CONTRACT_SIGNED` | 클라 6 / 프리 5 — **서명** |
| `CONTRACT_SIGNED` | 클라 6 / 프리 5 — **체결 완료** (양측 서명 완료 시, 양쪽에) |
| `INQUIRY_ANSWERED` | (명세 외) 1:1 문의 답변 |

### ❌ 아직 안 오는 것

| type | 명세 항목 | 담당 |
|---|---|---|
| `NEGOTIATION_STARTED` | 클라 3 / 프리 2 — 협상 시작 | 협상 |
| `NEGOTIATION_PROPOSED` | 클라 3 / 프리 2 — 새 AI 제안 | 협상 |
| `NEGOTIATION_FAILED` | 클라 4 / 프리 3 — 협상 결렬 | 협상 |

알림 저장·조회·읽음·삭제·실시간 push 는 전부 준비돼 있고, **"언제 알림을 만들지"를
각 도메인이 호출**해야 하는 구조입니다. 협상 도메인에서 호출이 빠져 있어 담당께
요청해둔 상태입니다.

**프론트는 이 3종도 온다고 가정하고 만드셔도 됩니다.** 서버 쪽 호출만 붙으면
프론트 수정 없이 바로 동작합니다. 응답 형태가 같으니까요.

### 발행되지 않는 타입

| type | 사유 |
|---|---|
| `CONTRACT_REJECTED` | **요구사항 명세에서 제외됨** (2026-08-12 개정). enum 값만 남아 있고 발행 경로 없음 |
| `SETTLEMENT_DUE` | 정산 담당께 요청해둔 상태. **성공보수 청구 시에만 발행될 예정**입니다 (착수금은 프로젝트 등록·계약 체결 화면에서 바로 이어지므로 알림을 보내지 않습니다) |

---

문의는 편하게 주세요.
