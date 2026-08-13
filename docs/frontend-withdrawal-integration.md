# 회원 탈퇴 프론트 연동 가이드

> 담당: 리뷰 · 등급 · 마이페이지 · 이력서 · 결제수단 · 1:1문의 · 챗봇 · 알림 · 홈
> 기준일: 2026-08-12
> 마이페이지 → `frontend-mypage-integration.md` / 고객지원 → `frontend-support-integration.md` / 알림 → `frontend-notification-integration.md`

---

## 0. 한눈에 보기

| 기능 | Method | Path |
|---|---|---|
| **탈퇴 가능 여부 조회** | `GET` | `/api/v1/accounts/me/withdrawal-eligibility` |
| 회원 탈퇴 | `DELETE` | `/api/v1/accounts/me` |

둘 다 **로그인 필요**. HttpOnly 쿠키 `accessToken` 이 실려가므로 `credentials: 'include'`.

**화면 진입 시 조회를 먼저 부르세요.** 눌러 봐야 아는 구조면 사용자는 왜 안 되는지 모릅니다.

---

## 1. 탈퇴 가능 여부 조회

```
GET /api/v1/accounts/me/withdrawal-eligibility
```

### 탈퇴 가능할 때

```json
{
  "code": "WITHDRAWAL_ELIGIBILITY_FOUND",
  "message": "조회에 성공했습니다.",
  "data": { "withdrawable": true, "blockers": [] }
}
```

### 탈퇴 불가할 때

```json
{
  "code": "WITHDRAWAL_ELIGIBILITY_FOUND",
  "data": {
    "withdrawable": false,
    "blockers": [
      { "type": "NEGOTIATION",           "label": "진행 중인 협상",  "count": 1, "linkUrl": "/negotiations" },
      { "type": "SIGN_PENDING_CONTRACT", "label": "서명 대기 계약",  "count": 1, "linkUrl": "/contracts" }
    ]
  }
}
```

| 필드 | 설명 |
|---|---|
| `withdrawable` | `false` 면 **탈퇴 버튼을 열지 마세요** |
| `blockers[].type` | 아이콘 분기용 코드 (아래 목록) |
| `blockers[].label` | **화면에 그대로 쓰는 문구.** 프론트에서 조립하지 마세요 |
| `blockers[].count` | 건수 |
| `blockers[].linkUrl` | 정리하러 갈 경로. 그대로 `navigate()` |

**`"진행 중인 협상이 1건 있습니다"` 는 `label` + `count` 로 만드시면 됩니다.**

### `type` 목록

| type | label | linkUrl |
|---|---|---|
| `NEGOTIATION` | 진행 중인 협상 | `/negotiations` |
| `PROJECT` | 진행 중인 프로젝트 | `/my-projects` |
| `SIGN_PENDING_CONTRACT` | 서명 대기 계약 | `/contracts` |
| `CONTRACT` | 진행 중인 계약 | `/contracts` |
| `UNPAID_SETTLEMENT` | 미납 수수료 | `/mypage/settlements` |

**0건인 사유는 아예 내려가지 않습니다.** `blockers` 를 그대로 순회해서 그리면 됩니다.

### 역할에 따라 보는 대상이 다릅니다

| 역할 | 판정 기준 |
|---|---|
| 클라이언트 | 자기가 등록한 **프로젝트** — 종료·취소가 아니면 진행 중 |
| 프리랜서 | 자기가 맺은 **계약** — 종료·서명거부·파기가 아니면 진행 중 |
| 공통 | **미납 수수료** |

그래서 클라이언트에게는 `NEGOTIATION`/`PROJECT` 가, 프리랜서에게는 `SIGN_PENDING_CONTRACT`/`CONTRACT` 가 나옵니다.

> **완료된 프로젝트·리뷰·협상 채팅은 탈퇴를 막지 않습니다.** 여기 잡히는 건
> 아직 끝나지 않아 상대방이 기다리고 있는 일뿐입니다.

> **`UNPAID_SETTLEMENT` 의 `count` 는 항상 `1`** 입니다. 몇 건인지보다 "결제 화면으로 가라"가
> 필요한 정보라 세지 않습니다. 문구에 건수를 넣지 마세요.

---

## 2. 회원 탈퇴

```
DELETE /api/v1/accounts/me
Content-Type: application/json

{
  "agreed": true,
  "confirmText": "탈퇴하겠습니다",
  "reason": "서비스를 더 이용하지 않습니다."
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `agreed` | ✅ | 안내 확인 체크박스. **`true` 여야 합니다** |
| `confirmText` | ✅ | **`"탈퇴하겠습니다"` 정확히 일치** |
| `reason` | — | 탈퇴 사유. 500자 |

```json
{ "code": "ACCOUNT_WITHDRAWN", "message": "탈퇴가 완료되었습니다." }
```

**본문 데이터는 없습니다.** 성공하면 완료 모달을 띄우고 메인으로 보내세요.

### 확인 문구는 서버도 검사합니다

화면에서 이미 막지만 **서버가 다시 봅니다.** 앞뒤 공백만 정리하고 그 외에는 정확히 일치해야 합니다.
`"탈퇴 하겠습니다"`(띄어쓰기) 는 **`400 AC_009`** 입니다.

### 탈퇴 후

```
로그아웃 상태가 됩니다. 세션·토큰이 서버에서 파기됩니다.
남아 있던 화면에서 API를 호출하면 401 이 나갑니다.
```

**성공 응답을 받으면 즉시 로그인 상태를 비우고 메인으로 이동시켜주세요.**

---

## 3. 에러

| 응답 | 의미 | 화면 처리 |
|---|---|---|
| `400 GLOBAL_002` | `agreed` 가 false / `confirmText` 누락 | 폼 검증 메시지 |
| `400 AC_009` | 확인 문구 불일치 | 입력칸에 에러 표시 |
| `409 AC_008` | 이미 탈퇴한 계정 | 로그아웃 후 메인 |
| `409 AC_010` | 진행 중인 프로젝트·계약 있음 | **조회 API 를 다시 불러 안내 갱신** |
| `409 AC_011` | 미납 수수료 있음 | 결제 화면 안내 |
| `401 GLOBAL_006` | 로그인 안 됨 / 만료 | 로그인 화면 |

**`AC_010`·`AC_011` 은 조회 API 를 먼저 부르면 거의 안 만납니다.** 화면을 열어둔 사이에 상태가
바뀐 경우라, 이 에러를 받으면 조회 API 를 다시 불러 안내를 갱신하는 게 자연스럽습니다.

---

## 4. 화면 구현 흐름

```
회원 탈퇴 탭 진입
 └── GET /accounts/me/withdrawal-eligibility

    withdrawable = false
     ├── blockers 를 안내 박스에 나열 (label + count + linkUrl)
     └── 탈퇴 버튼 비활성

    withdrawable = true
     ├── 안내 사항 + 동의 체크박스
     ├── "탈퇴하겠습니다" 입력칸
     └── [회원 탈퇴] → DELETE /accounts/me
                       → 완료 모달 → 로그인 상태 비우고 메인으로
```

---

## 5. 탈퇴하면 데이터가 어떻게 되나

화면 안내 문구와 서버 동작을 맞춰뒀습니다.

| 대상 | 처리 |
|---|---|
| **개인정보**(이메일·휴대폰) | 더미값으로 치환. 원본은 **해시로만 1년 보관** 후 파기 |
| 비밀번호 | 즉시 삭제 |
| 계정 행 | **지우지 않음** — 상태만 `WITHDRAWN` |
| 완료된 프로젝트·계약 | 그대로 남음 |
| 작성한 리뷰 | 그대로 남음 |
| 협상 채팅 | 그대로 남음 |

**계정을 지우지 않는 이유**는 리뷰·계약·정산이 그 계정을 참조하고 있어서입니다.
지우면 **상대방 화면에서 거래 이력이 사라집니다.**

해시를 1년 남기는 건 **재가입 제한 판정** 때문입니다. 원본을 그대로 두면 탈퇴했는데
개인정보가 살아 있는 것이고, 아예 지우면 같은 사람인지 알 수 없습니다.

### 재가입 제한

**탈퇴 후 30일간** 같은 이메일·휴대폰으로 **같은 역할**로는 재가입할 수 없습니다.
가입 시도 시 **`403 AU_021`** 이 나갑니다.

> 역할이 다르면 가입됩니다. 클라이언트로 탈퇴한 사람이 프리랜서로 가입하는 건 제한 대상이 아닙니다.

---

## 6. 주의사항

- **`label`·`linkUrl` 을 프론트에서 만들지 마세요.** 사유가 늘어나면 서버만 고치면 됩니다
- **조회 API 없이 탈퇴 버튼을 열지 마세요.** 눌러야 알 수 있는 구조가 됩니다
- 탈퇴는 **되돌릴 수 없습니다.** 확인 문구 입력을 건너뛰는 경로를 만들지 마세요
- `reason` 은 선택입니다. 안 받아도 탈퇴됩니다

---

문의는 편하게 주세요.
