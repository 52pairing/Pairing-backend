# 협상 와이어프레임 ↔ API/모달 매핑

> 와이어프레임 프레임 순서대로. 각 화면의 버튼/요소에 어떤 API·STOMP·모달을 붙이는지.
> 표기: 🔵 REST · 📡 STOMP(실시간) · 🪟 모달 · ➡️ 화면이동
> 상세 필드는 api-dto_FINAL 참조.

---

## 0. 헤더 배지 (전역, 모든 화면 공통)
프레임: 상단 헤더 우측 — **말풍선 / 종 두 개**가 따로 있다(와이어 확인, 2026-08-10).

세 가지가 서로 다른 소스다. 하나로 합치면 안 된다.

| 배지 | API | 상태 |
| --- | --- | --- |
| **말풍선** 숫자 | 🔵 `GET /api/v1/chat-rooms/unread-count` → `{ "unreadCount": 3 }` | 구현됨 |
| **종** 숫자 | 🔵 `GET /api/v1/notifications/unread-count` | 구현됨. 단 ⚠️ 아래 참고 |
| 협상 카드 **빨간점** | 매칭 요청 응답의 `newProposalCount > 0` | 구현됨 |

> ⚠️ **종은 협상 관련으로 안 켜진다.** 협상 도메인이 `NotificationCreateUseCase.create()` 를 부르지 않아서다.
> `NotificationType` 에 `NEGOTIATION_STARTED`·`NEGOTIATION_PROPOSED`·`NEGOTIATION_FAILED` 자리는 이미 있으니,
> 알림 담당자가 요청하면 발행 지점만 넣으면 된다. 현재 발행하는 곳은 문의 답변(`InquiryService`) 뿐.

**협상 "내 응답 필요"는 헤더가 아니라 카드 배지로 표현된다.**
`GET /api/v1/negotiations/waiting-count` 는 남아 있지만 와이어의 어느 배지에도 대응하지 않는다(초기 설계 잔재).
목록(`/mine`)은 페이징이라 1페이지만 세면 숫자가 틀리므로, 총계가 필요하면 이 API 를 쓴다.

**빨간점 끄기**: 협상방 진입 시 🔵 `POST /api/v1/negotiations/{id}/read`.
기준은 "클라가 마지막으로 읽은 시점 이후 도착한 AI 제안 수"라, 협상 시작 시 첫 제안이 생기면 켜진다.

---

## 1. 클라 협상 탭 (후보 목록) — "내 프로젝트 > 협상"
프레임: `협상`, `매칭 요청 페이지`

| 요소 | 매핑 |
| --- | --- |
| 화면 진입(후보 카드 목록) | 🔵 `GET /api/v1/negotiations/mine?projectId={id}` → 카드별 status·라운드 X/15·lastProposalBy·lastProposalAt |
| 카드 배지 "수락/요청대기/거절/협상중/내 응답 필요/상대방 응답 대기" | status + `waitingForMe`(내 응답 필요 vs 상대 대기) |
| 카드/탭 **빨간점**(안 읽은 새 제안) | `newProposalCount > 0`. 협상방 진입 시 🔵 `POST /api/v1/negotiations/{id}/read` 로 끔 |
| "마지막 제안: 프리랜서/클라이언트/AI · N분 전" | lastProposalBy / lastProposalAt |
| [협상 시작] (아직 마지노선 전) | ➡️ 협상 상세로 이동 → `GET /api/v1/negotiations/{id}` |
| [협상방 입장] (라운드 진행 중) | ➡️ 협상 상세(로그)로 이동 |
| [프로필] | ➡️ 후보 프로필(매칭 도메인) `GET /matchings/... candidate` |
| 실시간 카드 갱신(새 제안/타결) | 📡 `/topic/negotiations/{id}` 구독 → 배지·라운드 갱신 |

## 2. 클라 협상 탭 — 프리랜서 모두 거절 시
프레임: `협상_프리랜서 모두 거절 시`

| 요소 | 매핑 |
| --- | --- |
| 안내 배너 "모든 추천 프리랜서가 거절…" | 클라 화면 조건부 표시(전원 거절 시) |
| [무료 재추천] | 🔵 `POST /matchings/positions/{positionId}/rerecommendations` (type=FREE) — 매칭 도메인 |
| [재추천 요청] (유료) | 🔵 동 endpoint (type=PAID, 1인당 1만원) → 🪟 결제 모달 |

## 3. 협상 상세 진입 + 마지노선 입력 (프리/클라 공통)
프레임: `FLHeader`(연봉 320만/근무 상시 초기 카드 + 우측 floor 입력)

| 요소 | 매핑 |
| --- | --- |
| 초기 제안 조건 카드(연봉/근무형태/기간) | `GET /api/v1/negotiations/{id}` → conditions[].clientValue(=클라 희망) |
| floor 입력 필드 "최소 연봉/허용 근무형태/최소 기간" | 입력값 = 내 마지노선 |
| 힌트 "상대 희망 XXX / 클라이언트 희망 XXX" | conditions[].clientValue/freelancerValue (상대 희망값, 공개) |
| "상대가 적은 값은 보이지 않아요" | 상대 floor는 응답에 없음(비공개) |
| **[협상 시작]** | 🔵 `POST /api/v1/negotiations/{id}/start` body `{conditions:[{conditionType, value}]}` → 내 마지노선 저장. **양측이 모두 낸 뒤에야** 두 대리인 A2A 왕복이 실행된다(4번 로그 생성) |

### ⚠️ 협상은 양측 마지노선이 다 모여야 시작된다 (2026-08-10 변경)

`/start` 는 **내 마지노선을 저장할 뿐**이다. 먼저 낸 쪽은 상대를 기다린다 — `totalRound` 는 계속 **0**이고 대화도 생기지 않는다.
두 번째 제출이 들어오는 순간 라운드 1이 돌면서 A2A 대화가 만들어진다.

> 한쪽 마지노선만으로 돌리면 선을 안 그은 쪽 대리인이 지킬 게 없어 그대로 양보한다. 그러면 상대는
> 의사를 한 번도 밝히지 않았는데 계약 조건이 확정된다(먼저 누른 쪽이 이기는 협상). 그래서 기다린다.

**화면 분기 — `totalRound` 와 `conditions[].myFloor` 두 개로 판정한다:**

| 상태 | 판정 | 보여줄 것 |
| --- | --- | --- |
| 아직 내 마지노선 미입력 | `totalRound === 0` && `myFloor == null` | 마지노선 입력 폼 + [협상 시작] |
| 냈고 상대 대기 중 | `totalRound === 0` && `myFloor != null` | "상대방이 조건을 입력하면 협상이 시작됩니다" 안내. **입력 폼 다시 띄우지 말 것** |
| 협상 진행 중 | `totalRound >= 1` | A2A 로그 + 승인/재지시 패널 |

- 같은 당사자가 두 번 제출하면 **NG_003**(409, "마지노선을 이미 제출했습니다"). 버튼을 잠가서 예방할 것.
- 대기 단계에서도 SYSTEM 메시지 1건("마지노선이 저장되었습니다…")이 로그에 남으므로, 그걸 안내 문구로 써도 된다.
- ⚠️ 대기 단계에서는 목록 카드의 "내 응답 필요" 배지가 **안 켜진다**(`waitingForMe` 는 라운드 1 이상에서만 판정). 상대가 마지노선을 내야 한다는 사실은 아직 배지로 안 알려준다 — 별도 알림/배지가 필요하면 후속 과제.

### ⚠️ 값 변환 규칙 — 여기서 사고가 제일 많이 난다

화면 표기와 API 값의 **단위가 다르다.** 그대로 보내면 350원짜리 협상이 시작된다.

| 조건 | 화면 | API 값(문자열) | 변환 |
| --- | --- | --- | --- |
| **AMOUNT** | `350` + "만 원 이상" | `"3500000"` | **화면값 × 10,000** (원 단위) |
| **PERIOD** | `4` + "개월 이상" | `"4 MONTH"` | 숫자 + 공백 + `MONTH`/`WEEK` |
| **WORK_STYLE** | 상주 / 혼합 / 재택 | `"ONSITE"` / `"ANY"` / `"REMOTE"` | ⚠️ **혼합 = `ANY`** |
| **WORK_FORM** | 풀타임 / 파트타임 / 모두 가능 | `"FULL_TIME"` / `"PART_TIME"` / `"ANY"` | enum 코드 |
| **START_DATE** | 날짜 선택 | `"2026-09-01"` | ISO `yyyy-MM-dd` |

### ⚠️⚠️ 화면 라벨 "근무 형태" 는 `WORK_FORM` 이 아니라 `WORK_STYLE` 이다

와이어의 "근무 형태" 카드 값과 "허용 근무형태" 토글(`상주 | 혼합 | 재택`)은 전부 **`WORK_STYLE`** 이다.
**`WORK_FORM`(풀타임/파트타임)은 협상 와이어 어디에도 나오지 않는다.** 라벨만 보고 `WORK_FORM` 으로 붙이면
`FULL_TIME` 같은 값을 `WORK_STYLE` 조건에 보내게 되어 거부된다. (2026-08-10 와이어 재확인)

- 초기 카드에 찍힌 **"상시" 는 어느 enum 에도 없는 값**이다(초안 잔재). 뒤쪽 프레임은 전부 "혼합".
- 라벨은 하드코딩하지 말고 🔵 `GET /api/v1/meta/work-conditions` 에서 받는다
  (`workStyles`/`workForms`/`payUnits`/`periodUnits`/`skillLevels` 를 `{code,label}` 로 한 번에 내려준다. 비로그인 호출 가능).

- **금액은 전부 원 단위**다. 힌트의 "클라이언트 희망 320만 원"도 서버에선 `"3200000"` 으로 내려온다 →
  화면에 만 원으로 보이려면 **÷ 10,000** 해서 표시할 것.
- **금액은 월 단가**다(연봉 아님). 라벨이 "연봉"이지만 값의 의미는 **월 용역대금**이다.
  계약 총액은 계약 도메인이 개월 수로 곱해 만든다.
- **"혼합" 은 `HYBRID` 가 아니라 `ANY`** 다. `WorkStyle` enum 은 `REMOTE`/`ONSITE`/`ANY` 세 개뿐이라
  `HYBRID` 를 보내면 거부된다. "허용 가능한 근무형태를 모두 선택" UI에서 둘 이상 고르면 `ANY` 로 보낸다.
- 응답의 `agreedValue`·`clientValue`·`freelancerValue` 도 모두 위 표기를 따른다(타결 화면 칩도 동일).

## 4. AI 협상 로그 (진행 중) — A2A 대화 (두 대리인)
프레임: `FLHeader`(조건 배지 + 라운드 3/15 + 메시지들)

> **A2A 확정**: 로그는 **클라이언트 AI(CLIENT_AGENT) ↔ 프리랜서 AI(FREELANCER_AGENT)** 두 대리인이 제안·역제안·수락을 주고받는 대화다(중재자 1개 아님). 각 메시지에 근거(reason) 필수. 대리인끼리 합의한 조건은 자동 락🔒, 나머지는 사람 승인/재지시(5·6번).

| 요소 | 매핑 |
| --- | --- |
| 로그 최초 로드 | 🔵 `GET /api/v1/negotiations/{id}/messages` → message.senderType = `CLIENT_AGENT`/`FREELANCER_AGENT`(대리인) · `CLIENT`/`FREELANCER`(사람 응답) · `SYSTEM`(안내) |
| 발신자 좌/우 배치 | senderType 이 내 편 대리인이면 우측, 상대 대리인이면 좌측 |
| 실시간 새 제안/역제안 도착 | 📡 `/topic/negotiations/{id}` (NegotiationEvent type=NEW_PROPOSAL·ANSWERED) |
| 상단 조건 배지 "진행중🟡 / 합의🔒" | conditions[].status (PENDING/AGREED) — 대리인 자동 합의 시 AGREED |
| 라운드 3/15 | totalRound / maxRound(15). 라운드 = start·재지시(다시 협상) 1회당 1증가 |
| 메시지 근거 텍스트 | message.reason (필수) + message.proposedValue(제시값) |
| "응답 중…" 타이핑 표시 | (FE 연출용, 백엔드 이벤트 없음) |
| 헤더 [협상 포기] | 🪟 포기 모달(→ 8번) |

## 5. 조건 승인 패널 (사람 개입)
프레임: `FLHeader`(연봉[수락][거절] / 기간[수락][거절] / 근무형태[이미 합의])

| 요소 | 매핑 |
| --- | --- |
| 조건별 [수락] | answers[].accepted=true |
| 조건별 [거절] | answers[].accepted=false (→ 재지시 필요) |
| [이미 합의🔒] (락된 조건) | conditions[].status=AGREED (버튼 비활성) |
| **[확인]** | 🔵 `POST /api/v1/negotiations/{id}/answers` body `{roundNo, answers:[{conditionId, accepted, proposedValue}]}` |

## 6. 재지시 패널 (거절 조건 마지노선 재조정)
프레임: `FLHeader`(연봉/기간 "재협상 필요" + "직전 마지노선: XXX" + 새 최소값 입력)

| 요소 | 매핑 |
| --- | --- |
| "재협상 필요"/"대립적 필요" 배지 | conditions[].status = **REJECTED** |
| "직전 마지노선: 350만원 이상" | conditions[].**myFloor** (내 것만) |
| "새 최소 금액/개월 수" 입력 | 새 마지노선 |
| "합의 완료🔒" (다른 조건) | status=AGREED |
| **[다시 협상]** | 🔵 `POST /api/v1/negotiations/{id}/answers` — 거절 조건 proposedValue=새 마지노선 (재지시 흡수) |
| [협상 포기] | 🪟 포기 모달(→ 8번) |

## 7. 조건 승인/재지시 — 클라 측 (대칭)
프레임: `FLHeader`(클라뷰, 동일 UI). 5·6번과 동일 API, viewerRole만 다름.

## 8. 협상 포기 모달 🪟
프레임: `FreelancerDashboardPage`("협상을 포기하시겠어요?")

| 요소 | 매핑 |
| --- | --- |
| 모달 문구(NEGOTIATION_FAILED, 재추천 제외, 무료 리롤 미포함, 유료 리롤 1만원) | 정적 안내 |
| [취소] | 🪟 닫기 |
| **[협상 포기]** | 🔵 `POST /api/v1/negotiations/{id}/give-up` body `{reason}` → status=FAILED, 상대 알림 |

## 9. 결렬 결과 화면
프레임: `FLHeader`("협상이 성립되지 않았어요. 15회 소진 또는 협상 포기로 종료")

| 요소 | 매핑 |
| --- | --- |
| 결렬 카드 | `GET /api/v1/negotiations/{id}` status=FAILED. **15회 소진 시 자동 결렬**(별도 승인 없음) |
| 실시간 결렬 도달 | 📡 `/topic/negotiations/{id}` (NegotiationEvent type=FAILED) |
| [돌아가기] | ➡️ 협상 목록 |

## 10. 타결 화면 (모든 조건 합의)
프레임: `FLHeader`("모든 조건 합의" + 락된 조건 칩 + [채팅으로 이어가기])

| 요소 | 매핑 |
| --- | --- |
| "모든 조건 합의" 카드 + 조건 칩(연봉/근무형태/기간🔒) | status=AGREED, conditions[].agreedValue |
| 실시간 타결 도달 | 📡 `/topic/negotiations/{id}` (type=AGREED) |
| **[채팅으로 이어가기]** | ➡️ 사람 채팅방 → `chatRoomId` / `GET /api/v1/chat-rooms/{chatRoomId}/messages` (13. Chat) |
| 채팅 실시간 | 📡 `/topic/chat-rooms/{chatRoomId}` |

> ⚠️ **채팅방은 타결이 아니라 계약 체결 시 생긴다** (2026-08-10 변경). 타결 직후에는 `chatRoomId` 가 **null** 이므로
> **[채팅으로 이어가기] 버튼은 `chatRoomId != null` 일 때만 노출**할 것. 그 전에는 "계약 체결 후 대화를 시작할 수 있어요" 안내로 대체.
> 계약이 체결되면 방이 생기면서 입력창까지 바로 열린다(잠긴 방 단계 없음).

---

## 모달(🪟) 정리 — 어디에 다냐
| 모달 | 트리거 위치 | 확정 버튼 API |
| --- | --- | --- |
| 협상 포기 확인 | 협상 상세 헤더 [협상 포기] (진행 중 상시) | `POST /negotiations/{id}/give-up` |
| 유료 재추천 결제 | 협상 탭 [재추천 요청] (전원 거절 시) | `POST /matchings/positions/{id}/rerecommendations` (매칭) |

> 승인/재지시는 모달이 아니라 **로그 하단 인라인 패널**(대화창 스레드)로 붙는다 — 독립 페이지 X.
> 조건 diff(안 맞는 조건 추림)는 negotiation 생성 시 백엔드가 자동 → 화면은 이미 추려진 conditions만 렌더.

---

## 와이어프레임 자체 오류 — 그대로 만들면 안 되는 것 (2026-08-10 확인)

| 화면 | 와이어 | 실제로 해야 할 것 |
| --- | --- | --- |
| 9. 결렬 / 10. 타결 | 우측 상단 상태 배지가 **"협상 중"** 으로 남아 있음 | `status` 에 따라 "결렬"/"타결" 로 바꿔야 한다 |
| 3. 초기 제안 카드 | 근무 형태 값이 **"상시"** | 존재하지 않는 값. `WorkStyle` 라벨(상주/혼합/재택)로 |
| 6. 재지시 패널 | 배지 문구가 프레임마다 "재입력 필요"/"재협상 필요" 혼재 | 하나로 통일. 판정은 `conditions[].status === 'REJECTED'` |

## 서버가 안 내려주는 값 (프론트 계산)

| 화면 표시 | 계산 근거 |
| --- | --- |
| "남은 시간 1일 12시간"(응답 기한 카운트다운) | 매칭 요청 응답의 `expiresAt` 로 프론트가 계산. 서버는 남은 시간을 따로 안 준다 |
| "마지막 제안: 프리랜서 · 30분 전" | `lastProposalBy` + `lastProposalAt` 를 상대 시간으로 포맷 |
| 라운드 "3 / 15" 의 분모 | `maxRound` 는 목록에 없다 — 상수 15 |

## 말풍선 근거에 `(stub)` 이 보이면

파이썬 A2A 호출 실패로 **stub 폴백**이 돌고 있다는 뜻이다(프론트 버그 아님).
이때는 상대 제시값을 무조건 수락해서 1라운드에 전 조건이 합의되고, 문구도 `6 MONTH 를 제안합니다` 처럼
원본 코드값이 그대로 노출된다. 파이썬이 정상이면 그 자리에 AI 가 쓴 자연어 문장이 들어간다.
서버 로그에서 `협상 A2A 파이썬 호출 실패 → stub 폴백` 확인.
