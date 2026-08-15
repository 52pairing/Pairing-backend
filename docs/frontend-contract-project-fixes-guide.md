# 프론트 연동 가이드 — 계약·프로젝트 버그 4건

프론트가 읽고 바로 작업할 수 있게 정리했습니다. 각 항목은 독립적이라 순서 상관없이 적용 가능합니다.

| # | 항목 | 백엔드 | 변경 규모 |
|---|---|---|---|
| 1 | 프리랜서 "서명 대기" 탭 — 서명 후 사라지는 문제 | 반영 완료 | 파일 2개, 2줄 |
| 2 | 클라이언트 계약 상세 '← 내 계약' 링크가 프로젝트로 이동 | 변경 없음 | 파일 1개, 1줄 |
| 3 | 종료·취소된 프로젝트 상세에 모집 마감일 배지가 뜸 | 변경 없음 | 파일 1개 |
| 4 | 양측 서명 완료 전 계약서 다운로드 버튼 숨김 | 변경 없음 | 파일 1개, 1줄 |

---

## 1. 프리랜서 "서명 대기" 탭 — 서명 후 사라지는 문제 (백엔드 반영 완료)

### 증상
프리랜서가 서명하면 "서명 대기" 탭에서 계약이 사라지고 전체 탭에만 남음.

### 원인
이 탭이 `AWAITING_ME`(내 서명이 PENDING일 때만)를 써서, 프리랜서가 서명하면 조건에서 빠짐. 서명 대기 대상이 상대가 될 수도 있으므로, `SIGN_PENDING` 단계 내내 남아야 함. → 백엔드에 새 탭 코드 **`SIGNING`** 추가 (반영 완료).

### 수정 — 두 줄

**① `src/features/contract/components/freelancer/FreelancerContracts.tsx` (18번째 줄)**

```diff
  const TAB_CODES: Record<FreelancerContractStatus, ContractListTab> = {
    전체: "ALL",
-   "서명 대기": "AWAITING_ME",
+   "서명 대기": "SIGNING",
    "진행 중": "IN_PROGRESS",
    ...
```
이 한 줄이 목록 조회(`getContracts`)와 탭 배지(`getContractTabCounts`) 둘 다 커버.

**② `src/features/contract/types/contractList.ts` — 타입에 `"SIGNING"` 추가 (TS 컴파일용)**

```diff
  export type ContractListTab =
    | "ALL"
    | "AWAITING_ME"
+   | "SIGNING"
    | "AWAITING_COUNTERPART"
    ...
```

### 안 건드려도 되는 것
`FreelancerContractCard.tsx` 가 이미 `signatureRequired` 로 분기 → 서명 후 자동으로 "상대방의 서명을 기다리고 있습니다" 로 바뀜. 카드 문구 작업 없음.

### 참고
`SIGNING` 은 `AWAITING_ME` 와 라벨이 같지만("서명 대기") **다른 코드**. 프리랜서 화면은 `SIGNING`, 클라이언트 화면은 기존 `AWAITING_ME`/`AWAITING_COUNTERPART` 그대로. **클라이언트 쪽 변경 없음.**

### 동작 확인
SIGN_PENDING 상태에서 프리랜서가 서명해도 "서명 대기" 탭에 남고, 카드 문구가 "상대방의 서명을 기다리고 있습니다" 로 바뀌면 정상.

> ⚠️ 백엔드 `SIGNING` 이 develop 에 머지된 뒤 배포해야 `?tab=SIGNING` 이 정상 응답합니다(그 전에는 서버가 모르는 값이라 실패).

---

## 2. 클라이언트 계약 상세 '← 내 계약' 링크가 프로젝트 상세로 이동

### 증상
클라이언트 계약 관리 → 계약 상세보기에서 좌상단 **"← 내 계약"** 을 누르면, 계약 목록이 아니라 해당 프로젝트 상세로 이동. 라벨은 "내 계약"인데 실제 이동은 프로젝트 (라벨-경로 불일치).

### 원인
`src/features/contract/components/common/ContractOverview.tsx` (55번째 줄)

```tsx
const backHref = role === "client"
  ? `/client/projects/${params.projectId}?tab=계약`   // ← 클라만 프로젝트로 감
  : "/freelancer/contracts";
```
프리랜서는 `/freelancer/contracts`(내 계약)로 정상 이동, 클라이언트만 프로젝트 상세로 감.

### 수정 — 한 줄

```diff
  const backHref = role === "client"
-   ? `/client/projects/${params.projectId}?tab=계약`
+   ? "/client/contracts"
    : "/freelancer/contracts";
```
라벨(`← 내 계약`, 126번째 줄)은 그대로 유지. 이제 라벨과 경로 일치.

### 안전성
- `backHref` 는 126번째 줄 링크 한 곳에서만 사용 → 다른 흐름 영향 없음
- `/client/contracts` 라우트 존재(`ClientContracts` 렌더), 헤더 "계약 관리"와 동일 경로
- `ClientContracts` 는 `tab` 파라미터 없이 진입 시 `"ALL"` 기본 처리(27번째 줄) → 탭 제거해도 정상

### 참고
프로젝트 상세의 계약 탭에서 진입한 경우에도 계약 목록으로 복귀. 계약 목록에서 해당 프로젝트로 다시 갈 수 있으므로 의도된 동작.

---

## 3. 종료·취소된 프로젝트 상세에 모집 마감일 배지가 뜸

### 증상
`CLOSED`(종료) / `CANCELED`(취소) 프로젝트 상세인데 **"마감일 D-11"** 배지와 **"0/2회 사용"**(연장 횟수)이 그대로 표시. 모집이 끝난 프로젝트라 카운트다운이 뜨면 안 됨.

### 원인
`src/features/client/myprojects/information/components/ProjectInformation.tsx`

`getDeadlineLabel`(19번째 줄)이 `recruitDeadline` 값만 보고 **상태를 안 봄**. 이 프로젝트는 마감일 전에 인원이 다 차서 완료된 거라 `recruitDeadline` 이 아직 미래 날짜 → 종료됐는데도 "D-11" 계산됨. `extensionCount` 배지(75번째 줄)도 상태 무관하게 항상 표시.

### 수정

**① 마감일 계산에 상태 조건 추가**

```tsx
const getDeadlineLabel = (deadline: string | null, status: string) => {
  if (!deadline) return null;
  if (!["RECRUITING", "NEGOTIATING", "CONTRACT_PENDING"].includes(status)) return null;
  const deadlineDate = new Date(deadline);
  const today = new Date();
  deadlineDate.setHours(0, 0, 0, 0);
  today.setHours(0, 0, 0, 0);
  const days = Math.ceil((deadlineDate.getTime() - today.getTime()) / 86_400_000);
  return days >= 0 ? `마감일 D-${days}` : "모집 마감";
};
```

**② 호출부 + 연장 횟수 배지 게이팅**

```tsx
const isRecruiting = ["RECRUITING", "NEGOTIATING", "CONTRACT_PENDING"].includes(project.status);
const deadlineLabel = getDeadlineLabel(project.recruitDeadline, project.status);
```

```diff
  <div className="flex items-center gap-2">
-   <span className="text-[11px] font-bold text-theme-secondary">
-     {project.extensionCount} / 2회 사용
-   </span>
+   {isRecruiting ? (
+     <span className="text-[11px] font-bold text-theme-secondary">
+       {project.extensionCount} / 2회 사용
+     </span>
+   ) : null}
    {deadlineLabel ? (
      <span className="...">{deadlineLabel}</span>
    ) : null}
  </div>
```

### 표시 규칙
- **표시**: `RECRUITING`, `NEGOTIATING`, `CONTRACT_PENDING` (모집~계약 대기)
- **숨김**: `CLOSED`, `CANCELED` (종료·취소)

### 확인
백엔드 변경 없음 — `recruitDeadline`·`extensionCount`·`status` 이미 다 내려옴. 프론트에서 `status` 조건만 추가.

---

## 4. 양측 서명 완료 전 계약서 다운로드 버튼 숨김

### 배경
서명 전·한쪽만 서명한 계약서도 "PDF 다운로드" 버튼이 떠서 받아짐. **백엔드는 변경 없음** — 서명 화면 미리보기가 같은 API를 써서, 서버에서 막으면 서명 자체가 깨짐. 프론트에서 **버튼 노출 조건만** 조정.

### 수정 위치 — 한 줄
`src/features/contract/components/common/ContractOverview.tsx` (162번째 줄)

현재 — `DRAFT` 만 제외해서 `SIGN_PENDING`(서명 미완료)에도 버튼이 뜸:
```tsx
{contract.status !== "DRAFT" ? <button ...>PDF 다운로드</button> : null}
```

수정 — 양측 서명 완료된 계약에서만 버튼 노출:
```tsx
{["SIGNED", "IN_PROGRESS", "COMPLETION_PENDING", "COMPLETED", "TERMINATED"].includes(contract.status)
  ? <button ...>PDF 다운로드</button> : null}
```

> 상태 대신 서명 플래그(`contract.clientSigned && contract.freelancerSigned`)로 판별해도 됩니다. `TERMINATED`(중도 종료)는 **서명 후 파기면 다운로드 가능**해야 하는데 상태만으론 애매하니, 정확히 하려면 서명 플래그 기준이 더 낫습니다.

### 건드리면 안 되는 것 (중요)
- **`ContractDocument.tsx` 의 PDF 미리보기(iframe)는 그대로 두세요.** `/sign` 서명 페이지가 서명 전에 계약서를 보여주려고 같은 API(`downloadContractPdf`)를 씁니다. 서명 완료 전에도 반드시 동작해야 합니다.
- 서명 완료 후 뜨는 `ContractCompleteModal` 의 "계약서 다운로드" 버튼도 그대로 — 이미 양측 서명 후라 문제없음.

### 참고
백엔드에 차단이 없어 스웨거/API 직접 호출로는 미서명 계약서도 받아집니다. 단 **해당 계약 당사자 본인만** 가능하고(서버의 당사자 검증), 화면에는 버튼이 안 뜨므로 일반 사용자 경로로는 노출되지 않습니다.

---

## 요약 체크리스트

- [ ] **1** `FreelancerContracts.tsx` 탭코드 `AWAITING_ME→SIGNING` + `contractList.ts` 타입 `"SIGNING"` 추가 (백엔드 머지 후 배포)
- [ ] **2** `ContractOverview.tsx` `backHref` 클라이언트 `→ "/client/contracts"`
- [ ] **3** `ProjectInformation.tsx` 마감일·연장 배지 상태 게이팅
- [ ] **4** `ContractOverview.tsx` 다운로드 버튼 노출 조건을 양측 서명 완료로

2·3·4 는 백엔드 무관 — 지금 바로 적용 가능. 1 만 백엔드 `SIGNING` 머지 후 배포.
