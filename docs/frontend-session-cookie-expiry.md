# 프론트엔드 연동 — 세션 종료 시 인증 쿠키 자동 만료 (백엔드 변경 안내)

**대상**: 프론트엔드 담당자
**변경일**: 2026-08-13
**적용 범위**: 인증 쿠키(`accessToken` / `refreshToken`)를 쓰는 모든 요청
**기존 문서**: [frontend-auth-integration.md](frontend-auth-integration.md) — 1-3절(401 처리), 5-2절(세션 유지)이 이 문서로 갱신됩니다.

---

## 0. 한 줄 요약

**세션이 끊긴 401 응답에 서버가 쿠키 만료 헤더를 함께 실어 보냅니다.** 프론트는 쿠키를 지우려고 아무것도 하지 않아도 됩니다. 그리고 **탭을 두 개 열었을 때 "다른 기기에서 로그인되었습니다"가 잘못 뜨던 문제가 함께 고쳐졌습니다.**

프론트 필수 작업은 **없습니다.** 코드를 그대로 둬도 동작이 개선됩니다. 다만 3절에 **권장 변경 1건**과 4절에 **삭제해도 되는 코드**가 있습니다.

---

## 1. 무엇이 문제였나

토큰 쿠키는 `HttpOnly`라 프론트가 지울 수 없습니다. 그런데 서버도 세션 종료 401을 내려보낼 때 쿠키를 만료시키지 않았습니다. 그래서 이런 고리가 만들어졌습니다.

```
다른 기기에서 로그인
  → 이 기기의 다음 요청이 401 GLOBAL_011
  → "다른 기기에서 로그인되었습니다" 모달 → 확인 → /login 이동
  → 죽은 쿠키가 그대로 남아 있음 (프론트는 지울 수단이 없음)
  → 로그인 페이지에서 GET /auth/me 호출 → 또 401 GLOBAL_011
  → 같은 모달이 다시 뜸 → 확인 → /login → ...무한 반복
```

모달이 전체 화면을 덮고 포커스 트랩까지 걸려 있어서, 사용자는 **로그인 폼에 도달조차 못 합니다.** 쿠키를 덮어쓸 유일한 수단이 "성공한 로그인"인데 그 로그인 폼이 가려져 있는 상태였습니다. 브라우저 쿠키를 수동으로 지워야 탈출됐습니다.

이제 서버가 첫 401에서 쿠키를 만료시키므로, 모달 확인 후의 `/auth/me`는 쿠키가 없어 `GLOBAL_006`(비로그인)을 받습니다. **고리가 끊깁니다.**

---

## 2. 새 계약 — 어떤 응답이 쿠키를 지우는가

| 지점 | errorCode | 상황 | 쿠키 | 프론트 대응 |
| --- | --- | --- | --- | --- |
| 인증 필요한 모든 API | `GLOBAL_011` | 다른 기기 로그인으로 세션 종료 | **만료됨** | "다른 기기에서 로그인" 모달 → 로그인 페이지 |
| 인증 필요한 모든 API | `GLOBAL_010` | 토큰 위조·손상 | **만료됨** | 로그인 페이지 |
| 인증 필요한 모든 API | `GLOBAL_009` | Access 토큰 만료 | **유지** | `POST /auth/refresh` 후 원래 요청 1회 재시도 |
| 인증 필요한 모든 API | `GLOBAL_006` | 토큰 없음 (비로그인) | 해당 없음 | 로그인 페이지 (모달 없이) |
| `POST /auth/refresh` | `AU_015` | 다른 기기 로그인으로 세션 종료 | **만료됨** | "다른 기기에서 로그인" 모달 → 로그인 페이지 |
| `POST /auth/refresh` | `AU_016` | 리프레시 토큰 무효 **또는 중복 재발급** | **유지** | 3절 참고 — 원래 요청 1회 재시도 권장 |

### 왜 `GLOBAL_009`(만료)는 지우지 않는가

액세스 토큰 만료는 리프레시 토큰으로 복구되는 **정상 흐름**입니다. 여기서 쿠키를 지우면 재발급에 쓸 `refreshToken`까지 날아가 멀쩡한 로그인이 끊깁니다.

### 왜 `AU_016`은 지우지 않는가

3절에서 설명하는 "중복 재발급"이 이 코드로 내려오는데, 그 경우 쿠키에는 **이미 갱신된 유효한 토큰**이 들어 있습니다. 지우면 안 됩니다.

### 참고 — 헤더 토큰은 쿠키를 건드리지 않습니다

`Authorization: Bearer ...`로 들어온 토큰이 무효여도 쿠키는 그대로 둡니다. Swagger나 스크립트에서 낡은 Bearer 토큰을 한 번 잘못 보낸 것 때문에 같은 브라우저의 로그인이 날아가지 않게 하기 위함입니다. 프론트는 쿠키만 쓰므로 영향 없습니다.

### 실제 응답 헤더 (예시)

```http
HTTP/1.1 401
Set-Cookie: accessToken=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax
Set-Cookie: refreshToken=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax
Content-Type: application/json

{"timestamp":"2026-08-13T01:20:11.402Z","status":401,
 "errorCode":"GLOBAL_011","message":"다른 기기에서 로그인되어 로그아웃되었습니다.","traceId":"72a6691a"}
```

운영(HTTPS)에서는 `Secure; SameSite=None`이 붙습니다. 속성 순서는 보장하지 않으니 파싱하지 말고, 브라우저가 알아서 처리하게 두세요.

> **주의 — `credentials: 'include'`가 없으면 이 헤더가 무시됩니다.**
> 브라우저는 크로스 오리진 응답의 `Set-Cookie`를 `credentials: 'include'`인 요청에서만 적용합니다. 지금 `lib/api.ts`의 `apiCall`·`apiBlob`·`requestRefresh`는 모두 넣고 있으니 그대로 두면 됩니다. **새로 `fetch`를 직접 쓰는 코드를 추가할 때 빠뜨리면, 그 요청만 쿠키가 안 지워져 무한 모달이 재발합니다.**

---

## 3. 함께 고친 것 — 탭 2개일 때의 "다른 기기 로그인" 오탐

### 증상

기기는 하나인데, 탭을 두 개 열어두면 "다른 기기에서 로그인되었습니다" 모달이 뜨는 일이 있었습니다.

### 원인

`lib/api.ts`의 `refreshRequest` 중복 방지는 **모듈 스코프 = 탭 단위**입니다. 탭 A·B가 동시에 액세스 토큰 만료를 만나면 둘 다 같은 리프레시 토큰으로 재발급을 요청합니다. 서버는 늦게 도착한 쪽에서 "저장된 토큰과 값이 다르다"를 보고 **다른 기기 로그인으로 오판**해 `AU_015`를 내려줬습니다.

### 서버 수정 내용

값 불일치를 세션 ID(`sid`)로 한 번 더 갈라냅니다.

| 조건 | 판정 | 응답 |
| --- | --- | --- |
| `sid`가 이미 교체됨 | 다른 기기가 세션을 가져갔다 | `AU_015` + 쿠키 만료 |
| `sid`는 그대로 | 같은 세션의 중복 재발급이다 | `AU_016` + 쿠키 유지 |

새 로그인은 항상 새 `sid`를 발급하고, 재발급은 `sid`를 유지합니다. 그래서 `sid`가 살아 있다면 세션이 끊긴 게 아닙니다.

### 프론트 권장 변경 (1건)

`AU_016`을 받았을 때 **원래 요청을 1회만 재시도**하면, 중복 재발급으로 밀린 탭이 아무 모달 없이 정상 복구됩니다. 이긴 탭이 갱신해 둔 유효한 쿠키가 이미 브라우저에 들어 있기 때문입니다.

현재 `requestRefresh`는 실패 시 무조건 `notifySessionEnd`를 호출합니다.

```ts
// src/lib/api.ts — 현재
const error = (await response.json()) as ApiErrorBody;
notifySessionEnd(error.errorCode === "AU_015" ? "duplicate" : "expired");
throw new ApiException(error.errorCode, error.message, response.status);
```

`AU_016`은 모달을 띄우지 않고 그냥 throw 하도록 바꾸고, 호출부에서 한 번 재시도하는 형태를 권장합니다.

```ts
// 제안
const error = (await response.json()) as ApiErrorBody;

// AU_016 = 리프레시 토큰 무효 또는 같은 세션의 중복 재발급.
// 후자는 브라우저에 이미 갱신된 쿠키가 있어 원래 요청을 한 번 더 보내면 성공한다.
// 모달을 띄우면 멀쩡히 로그인된 사용자를 로그인 화면으로 보내게 된다.
if (error.errorCode === "AU_015") notifySessionEnd("duplicate");
else if (error.errorCode !== "AU_016") notifySessionEnd("expired");

throw new ApiException(error.errorCode, error.message, response.status);
```

재시도는 **반드시 1회로 제한**하세요. 리프레시 토큰이 정말 죽은 경우에도 `AU_016`이 오므로, 무제한 재시도는 루프가 됩니다. `apiCall`의 기존 구조(1회 재시도)를 그대로 쓰면 됩니다.

이 변경을 하지 않아도 무한 모달은 발생하지 않습니다. 드물게 "세션이 만료되었습니다" 모달이 한 번 뜨는 정도이니 우선순위는 낮습니다.

---

## 4. 프론트에서 이제 안 해도 되는 것

### 4-1. 세션 종료 후 쿠키 정리 시도

서버가 지워 주므로 `logout()`을 부를 필요가 없습니다. `AuthSessionGuard`의 `goToLogin`은 현재 코드 그대로 두면 됩니다.

```ts
// src/features/auth/components/AuthSessionGuard.tsx — 그대로 둬도 정상 동작
const goToLogin = () => {
  setSessionEndReason(null);
  window.location.replace("/login");   // 쿠키는 서버가 이미 만료시켰다
};
```

`window.location.replace`(하드 내비게이션)는 그대로 유지해 주세요. 서버가 내려준 `Set-Cookie`가 브라우저에 적용된 뒤 새 문서를 받으므로 상태가 확실히 초기화됩니다.

### 4-2. 로그인 페이지에서의 `/auth/me` 예외 처리

무한 모달이 서버에서 끊기므로 지금 구조로도 문제 없습니다. 다만 **선택 개선**으로, 공개 페이지(`/login`, `/signup`, `/`)에서는 `/auth/me` 호출을 건너뛰면 비로그인 방문자마다 나가는 불필요한 401 요청이 줄어듭니다. 현재는 `/login/findpassword/reset` 한 경로만 제외되어 있습니다.

---

## 5. 확인 방법

### 5-1. 브라우저에서

1. 브라우저 A에서 로그인한다.
2. 브라우저 B(또는 시크릿 창)에서 **같은 계정**으로 로그인한다.
3. 브라우저 A로 돌아가 아무 페이지나 이동한다.
4. "다른 기기에서 로그인되었습니다" 모달 → 확인.
5. **DevTools → Application → Cookies 에서 `accessToken` / `refreshToken` 이 사라져 있어야 한다.**
6. 로그인 화면에 정상적으로 머무르고, **모달이 다시 뜨지 않아야 한다.** 로그인 폼에 바로 입력할 수 있다.

### 5-2. curl 로

```bash
curl -i -X GET http://localhost:8080/api/v1/auth/me -H "Cookie: accessToken=<끊긴세션의토큰>"
```

`401` + `errorCode: GLOBAL_011` + `Set-Cookie` 2줄(`Max-Age=0`)이 함께 나오면 정상입니다.

### 5-3. 백엔드 테스트

동작을 고정한 테스트가 있습니다. 계약이 깨지면 여기서 먼저 실패합니다.

```bash
./gradlew test --tests "com.pairing.auth.presentation.api.AuthFlowIntegrationTest" --tests "com.pairing.global.security.SecurityErrorResponseTest"
```

- `다른 기기가 로그인하면 GLOBAL_011과 함께 인증 쿠키가 만료된다`
- `재발급도 다른 기기 로그인이면 AU_015와 함께 쿠키를 만료시킨다`
- `같은 세션의 중복 재발급은 AU_016으로 거절하고 쿠키는 건드리지 않는다`
- `쿠키에 담긴 토큰이 유효하지 않으면 그 쿠키를 만료시킨다`
- `잘못된 토큰도 같은 ErrorResponse 형식으로 401을 반환한다` (헤더 토큰은 쿠키를 건드리지 않음)

---

## 6. 알려진 제약

- **`COOKIE_DOMAIN`을 바꾼 직후에는 옛 쿠키가 안 지워집니다.** 삭제 쿠키는 생성 때와 같은 Domain·Path여야 브라우저가 같은 쿠키로 인식합니다. 환경변수를 변경했다면 그 전에 발급된 쿠키는 만료를 기다리거나 수동 삭제가 필요합니다. 평상시에는 해당 없습니다.
- **Redis 장애 중에는 중복 로그인 차단 자체가 느슨해집니다.** 세션 판정을 못 하면 인증이 필요한 모든 API가 동시에 죽으므로, 서버는 통과시키는 쪽을 택합니다(최대 30분, 액세스 토큰 수명). 이 구간에서는 `GLOBAL_011`이 나가지 않으므로 쿠키도 지워지지 않습니다. 기존과 동일한 동작입니다.

---

## 7. 변경된 백엔드 파일

| 파일 | 내용 |
| --- | --- |
| `global/security/GlobalJwtProvider.java` | `expireAuthCookies(HttpServletResponse)` 추가 — 쿠키 만료 헤더의 단일 구현 |
| `global/security/GlobalJwtAuthenticationFilter.java` | `GLOBAL_011` / `GLOBAL_010` 응답에 쿠키 만료 첨부. 토큰 출처(쿠키/헤더)를 구분해 헤더 토큰은 제외 |
| `auth/application/service/TokenService.java` | 재발급 값 불일치를 `sid`로 갈라 `AU_015`(세션 종료) / `AU_016`(중복 재발급) 구분 |
| `auth/presentation/api/AuthController.java` | `/auth/refresh`가 `AU_015`로 실패하면 쿠키 만료 |
| `auth/presentation/api/AuthCookieWriter.java` | `clear()`가 `GlobalJwtProvider`에 위임 (구현 중복 제거) |

**API 스펙 변경은 없습니다.** 경로·요청 본문·성공 응답 형태가 모두 그대로이고, `errorCode` 값도 새로 생기지 않았습니다. 달라진 것은 (1) 401 응답에 `Set-Cookie`가 붙는 것과 (2) `AU_015`/`AU_016` 판정 기준입니다.
