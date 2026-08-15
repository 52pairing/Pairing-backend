# 프론트 연동 가이드 — 모집 기간 만료 / 프로젝트 취소 (정책 P46)

## 한 줄 요약

**대부분 이미 되어 있습니다.** 프로젝트 취소가 계약·정산·매칭·협상으로 번지는데, 이때 바뀌는 상태값 표시는 프론트가 이미 다 처리하고 있습니다. **딱 하나 추가할 게 있습니다 — 알림 타입 `PROJECT_CANCELED`.**

---

## 1. 무슨 일이 일어나나

모집 마감이 지났는데 인원이 확정되지 않으면(또는 클라이언트가 "모집 종료"를 누르면), 프로젝트가 취소되고 걸려 있던 것들이 함께 정리됩니다. **한 번에 여러 도메인의 상태가 바뀝니다.**

| 도메인 | 상태 변화 | 프론트 표시 |
|---|---|---|
| 프로젝트 | → `CANCELED` | "취소됨" ✅ 이미 됨 |
| 계약 | → `TERMINATED` | "중도 종료" ✅ 이미 됨 |
| 정산(미결제) | → `CANCELED` | (아래 참고) |
| 매칭 요청(응답 대기) | → `REJECTED` + `rejectReason=EXPIRED` | "응답 기한 만료" ✅ 이미 됨 |
| 협상(진행 중) | → `NEGOTIATION_FAILED` | "협상 결렬" ✅ 이미 됨 |
| 알림 | 클라이언트에게 `PROJECT_CANCELED` 발송 | **❌ 추가 필요** |

트리거는 둘입니다. 프론트가 구분할 필요는 없습니다:
- **자동**: 매일 00:10 스케줄러가 마감 지난 프로젝트를 취소
- **수동**: 클라이언트가 프로젝트 상세에서 "모집 종료" 클릭 (이미 구현된 `closeRecruitment` 액션)

---

## 2. 해야 할 것 — 알림 타입 추가 (유일한 필수 작업)

백엔드가 프로젝트 취소 시 **클라이언트에게** `PROJECT_CANCELED` 알림을 보냅니다. 지금 프론트 `NotificationType` 에 이 값이 없습니다.

### 2-1. 타입 추가

`src/features/notification/types/notification.ts`

```diff
  export type NotificationType =
    | "MATCHING_RECOMMENDED"
    ...
    | "SETTLEMENT_DUE"
+   | "PROJECT_CANCELED"
    | "INQUIRY_ANSWERED";
```

### 2-2. 아이콘 매핑 추가

`src/features/notification/components/Notifications.tsx` 의 `NOTIFICATION_ICON`

```diff
  SETTLEMENT_DUE: { icon: CreditCard, className: "bg-warning-surface text-theme-warning" },
+ PROJECT_CANCELED: { icon: XCircle, className: "bg-danger-surface text-theme-danger" },
  INQUIRY_ANSWERED: { icon: HelpCircle, className: "bg-surface-muted text-brand" },
```
> 아이콘은 예시입니다(`XCircle` 등 lucide 아이콘). 취소/경고 톤이면 무엇이든 됩니다.

**안 하면?** 앱이 깨지지는 않습니다. `NOTIFICATION_ICON[type] ?? HelpCircle` 폴백이 있어서 **물음표 아이콘**으로 뜰 뿐입니다. 제대로 된 취소 아이콘을 원하면 추가하세요.

### 2-3. 알림 클릭 이동 — 이미 됨

알림 클릭 시 `router.push(notification.linkUrl)` 로 이동하는 로직이 이미 있습니다. 백엔드가 `linkUrl` 을 **`/client/projects/{projectId}`** 로 내려주므로, 클라이언트가 알림을 누르면 해당 프로젝트 상세로 이동합니다. **프론트 추가 작업 없음.**

---

## 3. 이미 되어 있어 손댈 필요 없는 것

확인 결과 아래는 전부 구현돼 있습니다. **바꾸지 마세요.**

- **프로젝트 "취소됨"** — `ClientProjects.tsx`, `ClientProjectDetail.tsx` 에서 `CANCELED: "취소됨"` 매핑됨. 취소된 프로젝트는 액션 메뉴(모집 연장·종료 등)도 `["CLOSED","CANCELED"].includes(status)` 로 이미 숨김.
- **계약 "중도 종료"** — `FreelancerContractCard`, `ProjectProgress`, `ProjectFreelancerStatus` 등에서 `TERMINATED: "중도 종료"` 매핑됨.
- **매칭 "응답 기한 만료"** — `rejectReason === "EXPIRED"` → "응답 기한이 만료되었습니다" 를 `FreelancerProjectDetail`, `FreelancerProjects`, `ClientMatchingRequestDetail`, `CandidateRerollRequest` 에서 이미 표시.
- **협상 "협상 결렬"** — `NEGOTIATION_FAILED: "협상 결렬"` 매핑됨. 협상 도메인이 프리랜서에게 결렬 알림도 별도 발송(기존 `NEGOTIATION_FAILED` 알림 그대로).
- **정산 `CANCELED` 상태** — `SettlementStatus` 타입에 `"CANCELED"` 이미 포함.

---

## 4. 참고 — 착수금 "환불"은 표시하지 않습니다

이미 낸 착수금은 **건드리지 않습니다.** 실제 송금 기능이 없는 모의 단계라, 환불 상태값도 만들지 않았습니다. **"환불되었습니다" 같은 문구를 새로 넣지 마세요** — 낸 적 없는 것처럼 보이거나 사실과 다른 안내가 됩니다. 취소된 프로젝트의 이미 낸 정산은 `PAID` 상태 그대로 결제 내역에 남습니다.

---

## 5. 프리랜서 화면 — 별도 작업 없음

프리랜서 입장에서 취소된 프로젝트는:
- **협상 중이던 건**: `NEGOTIATION_FAILED` + 협상 결렬 알림 (기존 그대로)
- **응답 대기던 건**: `REJECTED` + `rejectReason=EXPIRED` → "응답 기한 만료" (기존 그대로)

둘 다 이미 표시 로직이 있어 **프리랜서 쪽은 추가 작업이 없습니다.**

> 참고: 응답 대기였던 프리랜서에게 "프로젝트가 취소되었습니다" 알림을 따로 보낼지는 백엔드(매칭 도메인) 후속 논의 사항입니다. 현재는 요청이 조용히 만료 처리되고 별도 알림은 없습니다. 확정되면 이 문서를 갱신합니다.

---

## 6. 정리 — 프론트 체크리스트

- [ ] `NotificationType` 에 `"PROJECT_CANCELED"` 추가 (필수)
- [ ] `NOTIFICATION_ICON` 에 `PROJECT_CANCELED` 아이콘 추가 (권장, 안 하면 물음표 아이콘)
- [ ] 알림 클릭 이동 — 확인만 (이미 `linkUrl` 라우팅됨)
- [ ] 그 외 상태 표시 — **손대지 말 것** (전부 구현됨)

실질적으로 **파일 2개, 두 줄**이면 끝납니다. 나머지는 기존 코드가 그대로 처리합니다.
