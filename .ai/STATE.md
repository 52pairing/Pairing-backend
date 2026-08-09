# 현재 상태 — AI매칭(4번 파트)

최종 갱신: 2026-08-09

## 담당 범위

AI매칭 전체 파이프라인 (요구사항 R01~R05). 관련 레포 2개:

- `Pairing-backend` (Java/Spring, 이 레포) — `com.pairing.matching` 도메인
- `Pairing-python` (FastAPI, 별도 레포, 로컬 경로 `C:\52_Pairing\Pairing-python`) — 임베딩·LLM 계산 서버

임베딩 생성·저장까지 이 파트가 직접 구현한다. 프로필/프로젝트 도메인 자체(실데이터)는 2번/3번 소유, 이 파트는 그 텍스트를 벡터화·비교하는 부분만 담당.

## 기술 스택 (확정)

- **임베딩**: Gemini `text-embedding-004` (768차원). 로컬 모델 아님, Spring 백엔드와 같은 Gemini API 키 공유(용도별로 모델명만 다르게 설정: `GeminiTask.EMBEDDING/MATCHING/REVIEW`)
- **벡터 저장소**: pgvector — Spring이 쓰는 **같은 PostgreSQL**에 `freelancer_embedding`, `position_embedding` 테이블 (AI 서버 소유, 이미 SQL 스키마 존재: `Pairing-python/db/init/10-create-ai-schema.sql`)
- **LLM**: Gemini `gemini-2.0-flash` (`GeminiTask.MATCHING`), 구조화 출력은 `google-genai` SDK의 `response_schema`로 강제
- **Spring↔Pairing-python 계약**: `Pairing-python/README.md` "3. 스프링 ↔ AI 서버 통신 규약" 절이 계약서. `X-Internal-Api-Key` 헤더 인증, `X-Trace-Id` 전파, 에러코드 `AI_001~AI_030`

## Pairing-python 현황 (이미 구현됨)

- `PUT /api/v1/embeddings/freelancers`, `PUT /api/v1/embeddings/positions` — 임베딩 upsert (해시 비교로 재계산 스킵, 프리랜서 쪽만 구현됨)
- `GET /api/v1/embeddings/positions/{id}/candidates?limit=` — pgvector 코사인 검색
- `POST /api/v1/matchings/recommendations` — 후보풀 조회 + Gemini 재랭킹(`{freelancer_id, score, reason}` 구조화 출력)
- **미구현(TODO)**: `MatchingService._build_prompt`가 스텁 — 지금은 freelancer_id+유사도만 나열, 실제 이력서/포지션 요구조건 텍스트 없음. 하드필터(직군/직무/일정/단가)도 검색에 전혀 없음.

## Pairing-backend `matching` 도메인 현황 (2026-08-08 갱신)

- `MatchingController`의 9개 엔드포인트 **전부 실제 구현**(고정 샘플 응답 없음). PR #34로 develop에 merge 완료.
- `meta` 도메인이 공통 마스터 데이터(직군/직무/스킬/근무조건) 제공 — `/api/v1/meta/*`, 비로그인도 호출 가능.
- Spring → Pairing-python 호출 코드(`MatchingPort`+`PythonMatchingAdapter`, Resilience4j 서킷브레이커, Bucket4j 레이트리밋) 완성.
- **다른 도메인 연동 포트 3개 — 대부분 실제 구현으로 교체 완료** (`feature/matching-directory-adapters` 브랜치, 아직 이슈·PR 안 만듦):
  - `ProjectDirectoryPort` ← `infrastructure/directory/ProjectDirectoryAdapter` — **완전 교체**. `ProjectQueryUseCase`(project 도메인) + `AccountQueryUseCase.findClientProfileById`(회사명/업종/직원수)로 실제 조회. `findPositionSummary`는 project 도메인이 positionId 단독으로 projectId를 역조회할 방법이 없어서 **포트 시그니처를 `findPositionSummary(projectId, positionId)`로 변경**했다 — 매칭이 자신의 `MatchingRound`(project_id 보유)에서 얻어 넘긴다.
  - `NegotiationPort` ← `infrastructure/negotiation/NegotiationAdapter` — **완전 교체**. negotiation의 `NegotiationCommandUseCase`(생성)/`NegotiationProgressUseCase`(진행조회) 위임 호출.
  - `FreelancerDirectoryPort` ← `infrastructure/directory/FreelancerDirectoryAdapter` — **부분 교체**. `findCardSummary`만 실구현(`FreelancerCandidateSummaryUseCase`). `resolveFreelancerId`/`findCondition`은 account_id↔freelancer_profile.id 양방향 조회가 account 도메인에 아직 없어(2번이 1번에게 승인 요청, 대기 중) 스텁 유지 — 로그 경고로 표시해둠.
- `budgetCap`(수수료율 구간×클라이언트등급)과 `grade_weight`(0/1/2%) 계산은 `ClientGradeResolver`/`BudgetCapCalculator`가 실제 리포지토리를 조회해서 처리한다.
- Stage F 가드(직무/스킬·예산 조합 재검증)는 지금 항상 통과 처리(placeholder). 규칙 기반 실제 검증은 아직 남음.
- fitReason은 DB에 `"|"`로 이어붙인 문자열로 저장하고 API 응답에서 다시 나눠 태그 리스트로 돌려준다(`CandidateResponseAssembler`). Pairing-python이 이 구분자로 합친 문자열을 내려주도록 `_build_prompt`/응답 스키마를 맞춰야 한다(아직 안 함).
- **로컬 개발 환경에서 발견·수정한 버그 2건** (코드 정상, 인프라/설정 문제였음):
  - `global/ratelimit/RedisRateLimitConfig`가 빈 생성 시 즉시 Redis에 연결해서 Redis 없는 환경(CI)에서 전체 컨텍스트 로딩이 실패 → `@Lazy`(빈 + 생성자 주입 지점 둘 다)로 지연 연결하도록 수정.
  - `matching_candidate`/`matching_round`의 NUMERIC 컬럼(similarity/base_score/grade_weight/fit_score/cost_amount)에 JPA 엔티티가 `columnDefinition`을 안 줘서 스키마 검증 실패 → 명시해서 해결.
- **협상(5번) 연동 — 양쪽 다 완료**: 협상 타결(AGREED)/결렬(FAILED) 통보용 `MatchingNegotiationOutcomeUseCase`를 2026-08-09 매칭 쪽에서 구현(`MatchingRequestService`가 구현). `markNegotiationAgreed(requestId)`는 신규 도메인 메서드 `MatchingRequest.agreeNegotiation()`(NEGOTIATING일 때만 허용, 기존 `failNegotiation()`과 대칭 설계)을 호출해 `CONTRACT_PENDING`으로 전환하고, `markNegotiationFailed(requestId)`는 기존 `failNegotiation()`을 그대로 쓴다. **negotiation 쪽(5번)도 같은 날 완료**: `NegotiationLoopService`가 타결/결렬 시점에 이 인바운드 포트를 호출하도록 배선 완료, develop에 merge됨.
- `newProposalCount`(협상 진행조회 응답)는 "클라가 마지막으로 읽은 시점 이후 온 새 제안 수"로 정의 확정. 실제 반영은 5번의 `feature/negotiation-unread-proposals` 브랜치가 develop에 merge된 뒤 자동 적용됨(우리 코드 수정 불필요).

## 2026-08-09 갱신 — 3번 연동 버그 수정 + 결제→매칭 이벤트 리스너

3번이 project 쪽에 매칭 연동용 포트 3개(`findPositionSummaries`/`findProjectPositionSummary(positionId)`/`findStatus`, `ProjectPositionSummary.totalHeadcount` 추가, `RecruitingStartedEvent` 발행)를 올리면서 버그 리포트도 같이 줬다. 코드로 하나씩 확인한 결과:

- **budgetCap이 포지션 인원으로 나뉘던 버그(실제 버그, 수정함)**: `MatchingRequestService.accept()`가 `position.headcount()`(이 포지션만의 인원)를 넘기고 있었는데, `BudgetCapCalculator`는 프로젝트 전체 순예산을 나누는 거라 `totalHeadcount`(프로젝트 전체 포지션 인원 합)로 나눠야 맞다. 매칭 쪽 `ProjectPositionSummary`(application.result)에 `totalHeadcount` 필드 추가하고 `ProjectDirectoryAdapter`가 채워주도록 수정.
- **budgetAmount 단위 불일치 버그(실제 버그, 수정함)**: `budgetAmount`는 계약 기간 "전체 총액"인데 `BudgetCapCalculator`가 개월 수로 안 나누고 있어서, 협상 쪽이 월급과 비교할 때 상한이 실제보다 수배 크게 잡히는 문제가 있었다. `periodValue`/`periodUnit`을 매칭 쪽 `ProjectPositionSummary`에 raw 필드로 추가(기존 `periodLabel`은 화면 표기용이라 계산에 못 씀)하고, `BudgetCapCalculator.calculate()`가 개월 수로 한 번 더 나누도록 수정. **WEEK 단위 기간의 주→개월 환산 규칙은 2026-08-09 4주=1개월로 확정**(사용자 확인, 코드의 임시값 표기 제거). `negotiation` 도메인의 `NegotiationConditionCalculator`도 동일하게 4주=1개월을 쓰고 있어서 두 도메인 계산 기준이 원래부터 일치했음을 확인.
- **findClientAccountId 관련 버그 리포트(확인 결과 문제 없음)**: 3번은 이 포트가 client_profile.id를 그대로 반환하는 걸로 오해했으나, 우리 `ProjectDirectoryAdapter.findClientAccountId`는 이미 account 도메인의 `findClientProfileById`로 한 번 더 조회해서 진짜 accountId로 변환해 돌려주고 있다(`ClientGradeResolver`가 정상 동작). 포트 이름 변경 불필요.
- **결제→매칭 이벤트 리스너 신규 구현**: `RecruitingStartedEvent(projectId)`(project 도메인이 착수금 결제 커밋 후 발행)를 받아 포지션별로 ①스냅샷 동결(`MatchingSnapshot` PROJECT/POSITION 타입, 기존에 정의만 되고 안 쓰이던 것을 처음 사용) → ②임베딩 upsert(`MatchingPort.upsertPositionEmbedding`, Pairing-python의 `PUT /embeddings/positions` 신규 연동) → ③최초 추천 라운드 생성(`MatchingRoundCreationService.createRound` 재사용) 순서로 처리.
  - `RecruitingStartedEventListener`(포지션 목록 조회 + 포지션 단위 예외 격리) / `RecruitingStartedPositionHandler`(포지션 1건 처리, `@Transactional(REQUIRES_NEW)`)로 클래스를 분리했다 — 같은 빈 안에서 `this.method()`로 자기 자신을 호출하면 스프링 프록시를 안 거쳐서 `@Transactional`이 조용히 무시되는 문제(자체 호출 self-invocation)가 실제로 발생해서(테스트로 재현·확인함), 별도 빈으로 쪼개 진짜 프록시 호출이 되게 했다. `@TransactionalEventListener(AFTER_COMMIT)` 쓸 때 이 문제를 특히 조심해야 한다.
  - 멱등 가드(`countByPositionId > 0`)와 포지션 단위 예외 격리(한 포지션 실패해도 나머지 포지션은 계속 처리) 적용.
  - 임베딩 텍스트는 지금 매칭 쪽 요약에 있는 필드(제목/직무/스킬/경력/근무조건/기간)로만 구성한다. mainTask/currentSituation/업무범위/우대사항은 아직 매칭 쪽 요약에 없어서(Task #4 결정 대기) 못 넣었다 — 결정되면 `RecruitingStartedPositionHandler.buildEmbeddingText`만 채우면 됨.
- `com.pairing.matching.presentation.api.MatchingIntegrationTest`(H2 통합테스트, 9개)와 `com.pairing.matching.application.service.RecruitingStartedEventListenerTest`(2개) 신규 작성 — 스텁 어댑터 교체·오늘 버그 수정·이벤트 리스너를 전부 실제 요청/이벤트로 검증. 이 과정에서 `MatchingRoundCreationService.persistCandidates`의 `applyGuard`/`applyGradeWeight` 호출 순서가 뒤바뀌어 있던 것도 별도로 발견해 수정함(실제 추천 라운드 생성 시 매번 예외가 나는 상태였음).
- **PR #51 오픈** (`feature/matching-directory-adapters` → `develop`). 첫 push 때 CI가 재추천 테스트에서만 500으로 실패 — 원인은 `/rerecommendations`에 걸린 레이트리밋(`RateLimitProvider`)이 Redis 없는 CI에서 실제 연결을 시도해서였음(로컬은 Redis가 떠 있어 안 드러남). 테스트에서 `RateLimitProvider`를 목 처리해서 해결, 커밋·push 완료. 상세는 `.ai/WORKLOG.md` 2026-08-09 참고.
- **참고**: 이 개발 환경에 GitHub CLI(`gh`)가 없어서 이슈/PR 생성은 AI가 텍스트(제목/본문)만 만들어주고 사용자가 GitHub 웹에서 직접 생성하는 방식으로 진행 중.

## 2026-08-09 갱신 — PR #51/#53/협상 인바운드 merge + 매칭 요청 카드 라이브 조회 버그(R32)

- PR #51/PR #53(`fix/rerecommend-quantity-validation`)/`feature/matching-negotiation-outcome` 전부 develop에 merge. 5번이 `NegotiationLoopService`에 `MatchingNegotiationOutcomeUseCase` 호출 배선까지 완료해 협상↔매칭 양방향 연동이 다 이어졌다.
- **3번이 프로젝트 수정 API를 구현하면서 발견**: `MatchingRequestResponseAssembler`가 매칭 요청 카드를 조립할 때 `ProjectDirectoryPort.findPositionSummary`로 프로젝트 정보를 매번 라이브 조회하고 있었다. project는 결제 후 등록 정보 수정을 허용하도록 바뀌는데(프로젝트 수정 API), 그러면 이미 수락된 매칭 요청 카드(제목/직무/스킬/경력/근무조건/기간/시작일)가 클라이언트의 수정 내용으로 통째로 바뀌어버린다 — R32("매칭 중에는 정보 수정 가능하지만 AI 매칭 시 활용하는 정보는 수정 전 정보")를 어기는 상태였다.
  - 확인해보니 모집 시작 이벤트(`RecruitingStartedPositionHandler.freezeSnapshot`)가 이미 필요한 필드를 전부 `MatchingSnapshot`(PROJECT/POSITION)에 얼려두고 있어서, 새 필드 추가 없이 **읽는 쪽만** 스냅샷을 보도록 바꾸면 해결됐다.
  - `companyProfile`(업종·직원수)은 account 도메인 값이라(프로젝트 수정 범위 밖) 계속 라이브로 읽는다 — `ProjectDirectoryPort.findCompanyProfile(projectId)` 신규 추가.
  - **3번과 확인한 것**: 스냅샷은 "최초 모집 시작 시점"에 딱 1번만 얼리고 재추천마다 다시 얼리지 않는 지금 설계가 맞다(재추천마다 다시 얼리면 "수정 전" 기준이 계속 밀리고, 먼저 수락한 프리랜서와 나중 프리랜서가 서로 다른 조건을 보게 됨). 3번 쪽에서도 결제 후 인원·포지션 추가삭제·예산을 이미 잠가둬서(재추천 계산에 쓰이는 값들이라) 다시 얼릴 이유가 없다고 확인.
  - `MatchingIntegrationTest`가 `RecruitingStartedEvent` 플로우를 안 타고 라운드를 직접 심어서(seedRound) 스냅샷이 없었던 것도 같이 시딩하도록 수정(`seedMatchingSnapshots`).
  - `fix/matching-request-card-snapshot-read` 브랜치, `./gradlew clean build` 통과 확인, push 완료 — **PR #59로 develop에 merge됨**.

## 2026-08-09 갱신 — 매칭↔프로젝트 상태 연동 3건 (3번 코드 리뷰로 발견)

3번이 PR #59(위 스냅샷 수정) 리뷰 중에 매칭이 project 도메인의 상태 전이 API를 전혀 안 쓰고 있다는 걸
지적함. 확인해보니 project 쪽엔 이미 `ProjectQueryUseCase.findStatus`/`ProjectCommandUseCase.
startNegotiating`/`.syncStage`가 다 구현돼 있었는데(2026-08-08부터 존재) 매칭이 한 번도 호출한 적이
없었다(`grep`으로 참조 0건 확인). 3번이 준 계산 기준·javadoc이 project 쪽 실제 구현과 100% 일치해서
그대로 반영:

- **① 프로젝트 상태 체크**: `ProjectDirectoryPort.findStatus(projectId)` 신규(project의 기존 메서드 위임).
  `MatchingRequestService.sendOneRequest()`(요청 발송)와 `MatchingRerecommendService.rerecommend()`
  (재추천) 양쪽 진입부에 `assertRecruiting()` 추가. **CANCELED/CLOSED만 막고 RECRUITING 엄격 강제는
  안 함** — 같은 프로젝트의 다른 포지션이 협상중/계약대기로 앞서가도(프로젝트 대표 상태는 "가장 앞선
  단계"라서) 이 포지션 자체는 여전히 열려있을 수 있기 때문(3번이 판단 위임, 이 방향으로 결정).
  기존엔 `isOwnedByAccount`만 검사해서, 모집 종료로 강제 마감된(인원 미충족 CLOSED) 포지션에도 재추천이
  돌고 새 요청이 나갈 수 있었음(실제 버그).
- **② 수락 시 프로젝트를 협상중으로**: `MatchingRequestService.accept()` 끝에
  `projectCommandUseCase.startNegotiating(projectId)` 추가. 같은 트랜잭션이라 프로젝트가 이미
  취소·종료(PJ_012)면 수락도 함께 롤백됨 — matching 쪽에서 별도로 안 잡음(3번이 이미 그렇게 설계).
  여러 번 불러도 안전(`Project.advanceTo`가 이미 지난 단계면 무시).
- **③ 협상 결렬/거절 시 프로젝트 단계 재계산**: `MatchingNegotiationOutcomeUseCase.markNegotiationFailed()`
  와 `MatchingRequestCommandUseCase.reject()`(직접 거절) 양쪽에서 `projectCommandUseCase.syncStage(
  projectId, hasContractPending, hasNegotiating)` 호출. 두 값은 그 프로젝트의 매칭 요청 전체를 상태
  변경 직후 다시 세서 계산(`MatchingRequestRepository.existsByProjectIdAndStatusIn` 신규) — 기존
  `existsActiveByProjectId()`는 P41 무료 재추천 판정용이라 REQUEST_PENDING도 "있음"으로 세서 기준이
  다르다고 3번이 명시적으로 경고, 재사용 안 함. `markNegotiationAgreed()`(타결)는 안 건드림 — 계약
  대기 전이는 계약 도메인이 계약서 생성 시점에 `awaitContract()`로 직접 넘기기로 함(요구사항 1233).
  `expire()`(응답기한 만료)는 실제로 호출하는 곳이 아직 없어서(자동 만료 스케줄러 자체가 미구현) 지금은
  훅 지점이 없음 — 나중에 만들 때 같이 syncStage 넣을 것.
- 회귀 테스트: `MatchingIntegrationTest`에 CLOSED/CANCELED 프로젝트 차단 2건 + accept 시 프로젝트
  상태가 실제로 NEGOTIATING으로 바뀌는지 검증 추가. `MatchingNegotiationOutcomeServiceTest`에 결렬 후
  프로젝트가 RECRUITING으로 되돌아가는지 검증 추가(프로젝트 row를 실제로 심어야 함 — `syncStage`/
  `startNegotiating`이 project row가 없으면 PJ_001을 던지므로, 기존에 `projectId=1L`처럼 실존하지 않는
  더미 값을 쓰던 테스트는 실제 프로젝트 row 시딩으로 다 바꿔야 했음).
- `feature/matching-project-stage-sync` 브랜치, `./gradlew clean build` 통과 확인.

## 확정된 설계 결정 (요약, 상세 근거는 각 요구사항 R01~R05/정책 P02~P09 참고)

1. **파이프라인**: 하드필터(AI매칭 동의, 직군/직무) → 조건필터(일정/근무조건/단가, 느슨하게) → 임베딩 유사도(자기소개+경력사항 ↔ 프로젝트설명+담당업무+업무범위+우대사항) → 상위 (모집인원×3) 추림 → LLM 1회 호출(포지션당, 전체 포지션 후보 한번에, 프로젝트당 1회 원칙) → 규칙기반 가드(직무/스킬 재검증 + 예산 조합 재검증)
2. **점수**: 사람이 가중치 배점 안 함. LLM이 최종 점수·근거 산출. 0~100 스케일 가정(팀 정책 회의 "월요일 확정" 대기 중). 50점 미만이면 경고 — **최초 추천화면에서 미리 보여줌**(대기 순번 N+1~3N도 이미 한 번의 LLM 호출로 채점되어 있어서 가능). 점수 숫자 자체는 화면·API 응답에 노출 안 함.
3. **예산**: 순예산 = 입력예산×(1-수수료율), 수수료율 = 금액구간(1억)×클라등급(다이아 -2%). 프리랜서 단가는 월단가로 통일(일급×20일, 시급×160시간). 조합 총액 ≤ 순예산×1.2 허용.
4. **임베딩 쓰기**: 프리랜서는 조건 저장 시마다 PUT 호출(해시로 중복 스킵). 프로젝트(포지션)는 **검수 통과 시점에 딱 1번**(프로젝트는 등록 후 수정 불가능이라 재계산 불필요). 매칭 실행(추천)은 착수금 결제 완료 시점에 트리거.
4-1. **등급 가중치(grade_weight, 2026-08-07 확정)**: 명세/정책/원본 엑셀 다 확인해도 숫자 없음(정성적 "매칭 확률 증가"만 있음, 수수료 할인 1%/2%는 별개) — 그 패턴을 그대로 재사용해 **클라이언트 등급: 실버 0% / 골드 +1% / 다이아 +2%**로 확정. `MatchingCandidate.applyGradeWeight(double)`에 이미 구현됨(퍼센트 값은 서비스 계층에서 등급 조회해 전달). **클라이언트 간 자원배분(등급 높은 클라한테 좋은 프리 우선배정)은 하지 않음** — R02.6("같은 프리랜서가 여러 클라이언트에게 동시 추천 가능")과 충돌해서 폐기. 대신 **프리랜서 등급 타이브레이커**를 추가: base_score가 완전 동점일 때만 프리랜서 등급(마스터>시니어>주니어)으로 2차 정렬. 이건 아직 구현 전 — 노출 순위(rank_no) 계산하는 서비스 로직 만들 때 반영해야 함(엔티티 레벨 변경 불필요, 정렬 비교자만 추가하면 됨).
5. **협상(5번) 연동 계약**: `POST /requests/{requestId}/acceptance` 처리 시 아래를 동기 호출로 넘김(같은 트랜잭션, 실패 시 롤백)
   - **스냅샷 diff 대상 4개**: `payUnit`+`payAmount`, `workStyle`, `workForm`, `availableFrom` (전부 `FreelancerConditionResponse` 필드 재사용)
   - **보조 2개**: `minAcceptAmount`(금액 가드 하한), `startNegotiable`
   - **PERIOD**: `periodValue`가 null이면 그 협상에서 제외, 값 있으면 포함(블랑켓 제외 아님)
   - 경력연차·스킬은 매칭 필터로만 쓰고 diff 대상 아님
   - `budgetCap`: 3일 마감 때문에 1단계는 "순예산÷확정인원"으로 단순화, 나중에 Stage F 정확한 배분값으로 교체(필드명 동일 유지)
   - ~~블로커: negotiation 도메인에 application 계층이 아직 없음~~ — 2026-08-08 해소. `NegotiationCommandUseCase`/`NegotiationProgressUseCase` 실구현 완료, `NegotiationAdapter`로 연동함.

## 아직 팀 확인 대기 중인 것

| 항목 | 상태 |
|---|---|
| 적합도 점수 스케일이 진짜 0~100인지 | policy.md "정의 필요, 월요일 확정" 회의 결과 대기 |
| 골드 등급 수수료 할인 여부 | 다이아만 정책에 명시됨, 확인 필요 |
| 프로젝트 등록에 인원별 예산 배분 필드 존재 여부 | 없으면 지금처럼 순예산 전체 조합으로만 판단 |
| `currentSituation`(프로젝트 현재 상황)/`mainTask`(주요 담당 업무)를 프리랜서의 "받은 매칭 요청" 카드에 노출할지 | 2026-08-08 팀에 질문 전달, 답 대기 중. 데이터는 이미 `ProjectQueryUseCase`에서 옴, 노출하려면 `MatchingRequestResponse`에 필드 2개만 추가하면 됨 |
| ~~budgetCap 계산 시 기간이 WEEK 단위면 몇 주를 1개월로 칠지~~ | **2026-08-09 확정: 4주=1개월.** `BudgetCapCalculator` 코드 정리 완료 |

## 프론트 공유 문서 (레포 밖)

`C:\Users\user\Desktop\AI매칭_API_화면매핑_최신본.md` — 사용자가 관리하는 프론트 전달용 API-화면 매핑 문서(이 레포에는 없음). 2026-08-09에 실제 코드와 대조 검증 완료, 후속 작업 없음:

- **실제 오류 1건 발견·반영 완료**: 재추천 API 에러표의 `MT_009`(추천 후보 없음)는 코드에서 실제로 던지는 곳이 없었음(enum 정의만 있고 미사용, dead code) — 사용자가 에러표에서 제거하고 "후보 없으면 201+빈 배열" 문구를 추가함.
- **참고 안내 반영 완료**: `RerecommendRequest.quantity`는 PAID일 때 필수인데 코드에 `@NotNull` 검증이 없어서 빠뜨리면 500(NPE) 위험 — 사용자가 경고 문구와 "아직 확정/수정 필요" 표 행을 추가함.
- 나머지(공통 래퍼, PageResponse, 엔드포인트별 요청/응답 필드, enum 값, 에러코드 전체 목록, meta API 경로)는 전부 코드와 일치 확인함. 백엔드 쪽 `RerecommendRequest.quantity` `@NotNull` 검증 자체는 아직 코드로 안 고침(문서에만 위험 안내 — 실제 수정은 백로그, 아래 참고).
