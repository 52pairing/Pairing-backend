# 현재 상태 — AI매칭(4번 파트)

최종 갱신: 2026-08-10

## 담당 범위

AI매칭 전체 파이프라인 (요구사항 R01~R05). 관련 레포 2개:

- `Pairing-backend` (Java/Spring, 이 레포) — `com.pairing.matching` 도메인
- `Pairing-python` (FastAPI, 별도 레포, 로컬 경로 `C:\52_Pairing\Pairing-python`) — 임베딩·LLM 계산 서버

임베딩 생성·저장까지 이 파트가 직접 구현한다. 프로필/프로젝트 도메인 자체(실데이터)는 2번/3번 소유, 이 파트는 그 텍스트를 벡터화·비교하는 부분만 담당.

## 기술 스택 (확정)

- **임베딩**: Gemini `gemini-embedding-001` (768차원, 축소 지정). 로컬 모델 아님, Spring 백엔드와 같은 Gemini API 키 공유(용도별로 모델명만 다르게 설정: `GeminiTask.EMBEDDING/MATCHING/REVIEW`). **(2026-08-10)** 원래 `text-embedding-004`였는데 신규 키에서 404가 나서 교체(Pairing-python PR #19). 차원은 그대로 768이라 DB 스키마 변경은 없지만, 모델이 바뀌면 벡터 공간 자체가 달라져 기존 벡터와 안 섞여야 한다 — 그래서 `POST /api/v1/matchings/admin/embeddings/reindex`(관리자 전용)를 추가해 기존 벡터를 새 모델로 일괄 재생성할 수 있게 함.
- **벡터 저장소**: pgvector — Spring이 쓰는 **같은 PostgreSQL**에 `freelancer_embedding`, `position_embedding` 테이블 (AI 서버 소유, 이미 SQL 스키마 존재: `Pairing-python/db/init/10-create-ai-schema.sql`)
- **LLM**: Gemini `gemini-2.0-flash` (`GeminiTask.MATCHING`), 구조화 출력은 `google-genai` SDK의 `response_schema`로 강제
- **Spring↔Pairing-python 계약**: `Pairing-python/README.md` "3. 스프링 ↔ AI 서버 통신 규약" 절이 계약서. `X-Internal-Api-Key` 헤더 인증, `X-Trace-Id` 전파, 에러코드 `AI_001~AI_030`

## Pairing-python 현황

- `PUT /api/v1/embeddings/freelancers`, `PUT /api/v1/embeddings/positions` — 임베딩 upsert (해시 비교로 재계산 스킵)
- `GET /api/v1/embeddings/positions/{id}/candidates?limit=` — pgvector 코사인 검색
- `POST /api/v1/matchings/recommendations` — 후보풀 조회 + Gemini 재랭킹(`{freelancer_id, score, reason}` 구조화 출력). `_build_prompt`는 실제 포지션 요구조건+프리랜서 이력서 원문을 채움(2026-08-09 완료).
- **(2026-08-09 완료, 2026-08-10 matchingPaused 추가)** 하드필터(직군/직무/AI매칭 동의/매칭 일시중지 아님)를 벡터 검색에 추가(HANDOFF 10번). `EmbeddingRepository.search_similar_freelancers`가 `freelancer_profile`(ai_matching_agreed, matching_paused=false)+`account`(status=ACTIVE)+`freelancer_condition`(job_category/job_role)을 조인해서 필터링. `MatchingService.recommend()`는 포지션 조회를 벡터 검색보다 먼저 하도록 순서를 바꿔 job_category/job_role을 넘긴다. 이전에 노출된 프리랜서 제외(`excludePreviouslySurfaced`)도 Java 후처리에서 Python 사전필터로 이동(`MatchingRequest.excluded_freelancer_ids`). **일정/근무조건/단가는 하드필터 대상에서 제외 확정** — 아래 "Stage B 조건필터 폐기" 참고, Stage E 감점으로 이동(**2026-08-10 구현 완료**, HANDOFF 10-2번).
- **(2026-08-10 완료) Stage E 조건 감점**: `DirectoryRepository`가 포지션 쪽 `budget_amount`/`period_value`/`period_unit`/`start_desired_date`/`start_negotiable`과 프리랜서 쪽 `pay_unit`/`pay_amount`/`work_style`/`work_form`/`available_from`/`start_negotiable`/`period_value`/`period_unit`을 함께 읽고, `_build_prompt`가 이 값들을 프롬프트에 넣으면서 "어긋나도 후보에서 제외하지 말고 감점만 하고 사유에 적어라"를 지시한다. `start_negotiable`이 켜진 항목은 감점하지 말라고 명시. 총예산은 프로젝트 전체 인원·기간 합계라 1인 월단가와 직접 비교하면 안 된다는 주의도 프롬프트에 넣음(안 넣으면 LLM이 4800만 vs 620만을 그대로 비교해 과도하게 감점함). 값이 없는 항목은 줄 자체를 빼서 LLM이 `None`을 조건 값으로 오해하지 않게 함. 감점 공식은 우리가 정하지 않고 LLM 판단에 맡김(설계 결정대로). 응답 스키마(`RankedCandidate`)는 안 바뀜 — 기존 `reason`(파이프 구분 문자열)에 감점 사유가 항목으로 추가되는 형태.
- **(2026-08-09 발견·수정) `directory_repository.py` 컬럼명 버그 2건**: `freelancer_condition`/`resume`을 `freelancer_id`로 조회하고 있었는데 실제 컬럼은 `account_id`(2번 확인 완료) — `freelancer_profile` 경유해서 다리 놓게 수정. `project_position.preferred_note`도 존재하지 않는 컬럼이라 제거(우대사항은 `project.extra_note`로 통일돼 있음). `fix/directory-repository-column-names` 브랜치, PR 리뷰 대기.
- **(신규 발견·해결, 2026-08-09) 치명적 결함이었던 것**: 스프링 쪽에서 `PUT /embeddings/freelancers`를 부르는 코드가 어디에도 없었다. 프로젝트 포지션 임베딩(`upsertPositionEmbedding`)은 잘 연결돼 있었는데 프리랜서 쪽은 `MatchingPort`에 메서드 자체가 없었음 — `freelancer_embedding` 테이블이 실환경에서 영원히 비어 있었다는 뜻(Stage C가 검색할 대상이 없음). 테스트가 통과했던 건 `MatchingPort.recommend()`를 목 처리해서 실제 벡터 검색을 건너뛰었기 때문. `MatchingPort.upsertFreelancerEmbedding` 신규 추가 + 이력서 저장(`ResumeService.upsert`) 시 `ResumeUpdatedEvent` 발행 + 매칭의 `ResumeUpdatedEventListener`가 받아서 자기소개+경력사항으로 임베딩 재생성하도록 연결. `feature/matching-freelancer-embedding-trigger` 브랜치.

## Pairing-backend `matching` 도메인 현황 (2026-08-08 갱신)

- `MatchingController`의 9개 엔드포인트 **전부 실제 구현**(고정 샘플 응답 없음). PR #34로 develop에 merge 완료.
- `meta` 도메인이 공통 마스터 데이터(직군/직무/스킬/근무조건) 제공 — `/api/v1/meta/*`, 비로그인도 호출 가능.
- Spring → Pairing-python 호출 코드(`MatchingPort`+`PythonMatchingAdapter`, Resilience4j 서킷브레이커, Bucket4j 레이트리밋) 완성.
- **다른 도메인 연동 포트 3개 — 대부분 실제 구현으로 교체 완료** (`feature/matching-directory-adapters` 브랜치, 아직 이슈·PR 안 만듦):
  - `ProjectDirectoryPort` ← `infrastructure/directory/ProjectDirectoryAdapter` — **완전 교체**. `ProjectQueryUseCase`(project 도메인) + `AccountQueryUseCase.findClientProfileById`(회사명/업종/직원수)로 실제 조회. `findPositionSummary`는 project 도메인이 positionId 단독으로 projectId를 역조회할 방법이 없어서 **포트 시그니처를 `findPositionSummary(projectId, positionId)`로 변경**했다 — 매칭이 자신의 `MatchingRound`(project_id 보유)에서 얻어 넘긴다.
  - `NegotiationPort` ← `infrastructure/negotiation/NegotiationAdapter` — **완전 교체**. negotiation의 `NegotiationCommandUseCase`(생성)/`NegotiationProgressUseCase`(진행조회) 위임 호출.
  - `FreelancerDirectoryPort` ← `infrastructure/directory/FreelancerDirectoryAdapter` — **완전 교체**(2026-08-09). `findCardSummary`(`FreelancerCandidateSummaryUseCase`)는 기존대로, `resolveFreelancerId`/`findCondition`도 `AccountQueryUseCase.findFreelancerProfileByAccountId/ById` + `FreelancerConditionUseCase.findMyCondition`으로 실구현 교체 완료 — 2번 확인 결과 account 도메인 승인·포트는 이미 다 있었고 매칭 쪽 어댑터만 안 바꿔놨던 상태였음. 못 찾으면 `MatchingErrorCode.FREELANCER_NOT_FOUND`(MT_015).
- `budgetCap`(수수료율 구간×클라이언트등급)과 `grade_weight`(0/1/2%) 계산은 `ClientGradeResolver`/`BudgetCapCalculator`가 실제 리포지토리를 조회해서 처리한다.
- budgetCap 배분은 **2026-08-09 A안(현재 공식 유지)으로 확정** — 3번이 정책·요구사항 전수 확인한 결과 "포지션별 1순위 조합" 같은 배분 알고리즘 규칙은 원래 확정된 적이 없었고(HANDOFF 11번의 전제 자체가 틀렸음), 포지션별 예산 입력란도 없어 재료가 없음. `BudgetCapCalculator`는 코드 변경 없이 그대로 유지.
- **(2026-08-09 완료) Stage F 가드 실제 구현.** `MatchingRoundCreationService.applyGuard(true, null)` placeholder를 R02.3 요구사항("가드 AI로 마지막 검증 (직무, 스킬 검증)") 그대로 **직무+스킬만** 재검증하도록 교체. 예산 조합 재검증은 이번에도 요구사항에 근거가 없어서(Stage B 조건필터 폐기와 같은 사유) 가드에 넣지 않음 — budgetCap은 협상 단계(`NegotiationConditionCalculator`)에서 이미 따로 재검증됨. `findCondition(freelancerId)` 실조회 + `ProjectDirectoryPort.findPositionSummary`(실시간)로 jobRole/requiredSkills를 비교, 가드 탈락 후보는 기록은 남기되(`guardPassed=false`) 노출 안 하고 다음 순위 후보가 노출 자리를 채움. `feature/matching-stage-f-guard` 브랜치.
- fitReason은 DB에 `"|"`로 이어붙인 문자열로 저장하고 API 응답에서 다시 나눠 태그 리스트로 돌려준다(`CandidateResponseAssembler`). ~~Pairing-python이 이 구분자로 합친 문자열을 내려주도록 `_build_prompt`/응답 스키마를 맞춰야 한다~~ — 2026-08-09 완료(Pairing-python `feature/matching-prompt-real-implementation`).
- **로컬 개발 환경에서 발견·수정한 버그 2건** (코드 정상, 인프라/설정 문제였음):
  - `global/ratelimit/RedisRateLimitConfig`가 빈 생성 시 즉시 Redis에 연결해서 Redis 없는 환경(CI)에서 전체 컨텍스트 로딩이 실패 → `@Lazy`(빈 + 생성자 주입 지점 둘 다)로 지연 연결하도록 수정.
  - `matching_candidate`/`matching_round`의 NUMERIC 컬럼(similarity/base_score/grade_weight/fit_score/cost_amount)에 JPA 엔티티가 `columnDefinition`을 안 줘서 스키마 검증 실패 → 명시해서 해결.
- **협상(5번) 연동 — 양쪽 다 완료**: 협상 타결(AGREED)/결렬(FAILED) 통보용 `MatchingNegotiationOutcomeUseCase`를 2026-08-09 매칭 쪽에서 구현(`MatchingRequestService`가 구현). `markNegotiationAgreed(requestId)`는 신규 도메인 메서드 `MatchingRequest.agreeNegotiation()`(NEGOTIATING일 때만 허용, 기존 `failNegotiation()`과 대칭 설계)을 호출해 `CONTRACT_PENDING`으로 전환하고, `markNegotiationFailed(requestId)`는 기존 `failNegotiation()`을 그대로 쓴다. **negotiation 쪽(5번)도 같은 날 완료**: `NegotiationLoopService`가 타결/결렬 시점에 이 인바운드 포트를 호출하도록 배선 완료, develop에 merge됨.
- `newProposalCount`(협상 진행조회 응답)는 "클라가 마지막으로 읽은 시점 이후 온 새 제안 수"로 정의 확정. 실제 반영은 5번의 `feature/negotiation-unread-proposals` 브랜치가 develop에 merge된 뒤 자동 적용됨(우리 코드 수정 불필요).
- **모집 시작 후 프로젝트 수정 → 포지션 임베딩 재생성 (2026-08-09 신규)**: 3번이 프로젝트 수정 API(PR #57)에서 `ProjectUpdatedEvent(projectId)`를 새로 만들어 발행하는데(수정 커밋 후, headcount 잠긴 이후=결제 완료 후에만), 매칭 쪽에 받는 리스너가 없던 걸 develop 재동기화 중 발견. `ProjectUpdatedEventListener` 신규 추가 — 프로젝트의 포지션마다 최신 정보로 임베딩 재생성. 매칭 요청 카드용 `MatchingSnapshot`(위 R32 스냅샷 항목)은 여기서 절대 안 건드린다 — 그건 최초 모집 시작 시점에 고정해야 하는 값이고, 이건 반대로 AI 검색용 임베딩을 최신 상태로 유지하는 것이라 서로 목적이 다르다. 임베딩 텍스트 조립 로직(`buildEmbeddingText`)을 `RecruitingStartedPositionHandler`에서 `PositionEmbeddingTextBuilder`(공유 클래스)로 추출해 양쪽에서 같이 씀. `feature/project-updated-embedding-refresh` 브랜치.
- **알림은 `MatchingNotifier` 한곳에서만 보낸다 (2026-08-10)**: 매칭 요청 발송/수락/거절/만료/재추천 완료 5종. 문구·링크 경로가 흩어지면 같은 상황에 화면마다 다른 말이 나가서 모아놨다. 만료 알림은 `MatchingRequestExpirer.expireNow()`에 있다 — 스케줄러 경로와 수락/거절 중 발견되는 경로가 모두 거기를 지나므로 한 번만 나간다. **알림 실패는 본 기능을 막지 않는다**(예외를 삼키고 로그만).
- **LLM 호출은 전부 비동기다 (2026-08-10)**: 이벤트 리스너 3개(`RecruitingStartedEventListener`/`ResumeUpdatedEventListener`/`ProjectUpdatedEventListener`)에 `@Async`가 붙어 있다. `@TransactionalEventListener(AFTER_COMMIT)`는 기본적으로 커밋한 스레드에서 그대로 이어 실행되기 때문에, 안 붙이면 **매칭이 남의 도메인 API 응답을 붙잡는다**(결제·이력서 저장·프로젝트 수정). LLM 읽기 타임아웃이 60초라 체감이 크다. 관리자 재색인 API도 `startReindexAll()`(`@Async`)로 202 즉시 응답한다. **재추천 API도 202다** — 검증·회차 생성까지만 동기로 하고(회차가 저장돼야 한도 검증이 중복 요청을 막는다) 후보 채우기는 `RerecommendRequestedEventListener`가 비동기로 처리한 뒤 알림을 보낸다. 그래서 `MatchingRoundCreationService`가 `openRound`(회차만)/`fillCandidates`(AI 호출)로 나뉘어 있다. **새 리스너를 추가할 때도 같은 이유로 `@Async`를 붙일 것.** 비동기라 예외가 호출자에게 안 가므로 리스너 안에서 반드시 로그를 남겨야 한다. 테스트는 `SyncTaskExecutorTestConfig`를 `@Import`해서 동기로 돌린다(안 그러면 검증이 백그라운드보다 먼저 실행돼 실패).

- **계약 이후 인원별 상태 전이 리스너 (2026-08-10 신규)**: `ContractStageEventListener`가 계약·정산·프로젝트 이벤트 4개를 받아 `matching_request.status`를 CONTRACTED → IN_PROGRESS → COMPLETION_PENDING → CLOSED로 옮긴다(3번 요청). **이 리스너만 `@Async`를 안 붙인다** — AI 호출 없이 UPDATE만 하고 발행 도메인 트랜잭션과 원자적으로 묶이는 게 맞아서다(계약은 됐는데 매칭만 안 따라오는 걸 막는 게 목적). 같은 이유로 여기에 알림·외부 호출을 넣으면 안 된다. **프로젝트 단위 이벤트는 반드시 직전 단계 상태로 좁혀서 조회할 것**(`findByProjectIdAndStatus`) — 거절·만료된 요청까지 `advanceStatus`에 넣으면 종결 상태라 예외가 나고, 발행 도메인 트랜잭션이라 착수금 결제까지 롤백된다.
## 2026-08-09 갱신 — 3번 연동 버그 수정 + 결제→매칭 이벤트 리스너

3번이 project 쪽에 매칭 연동용 포트 3개(`findPositionSummaries`/`findProjectPositionSummary(positionId)`/`findStatus`, `ProjectPositionSummary.totalHeadcount` 추가, `RecruitingStartedEvent` 발행)를 올리면서 버그 리포트도 같이 줬다. 코드로 하나씩 확인한 결과:

- **budgetCap이 포지션 인원으로 나뉘던 버그(실제 버그, 수정함)**: `MatchingRequestService.accept()`가 `position.headcount()`(이 포지션만의 인원)를 넘기고 있었는데, `BudgetCapCalculator`는 프로젝트 전체 순예산을 나누는 거라 `totalHeadcount`(프로젝트 전체 포지션 인원 합)로 나눠야 맞다. 매칭 쪽 `ProjectPositionSummary`(application.result)에 `totalHeadcount` 필드 추가하고 `ProjectDirectoryAdapter`가 채워주도록 수정.
- **budgetAmount 단위 불일치 버그(실제 버그, 수정함)**: `budgetAmount`는 계약 기간 "전체 총액"인데 `BudgetCapCalculator`가 개월 수로 안 나누고 있어서, 협상 쪽이 월급과 비교할 때 상한이 실제보다 수배 크게 잡히는 문제가 있었다. `periodValue`/`periodUnit`을 매칭 쪽 `ProjectPositionSummary`에 raw 필드로 추가(기존 `periodLabel`은 화면 표기용이라 계산에 못 씀)하고, `BudgetCapCalculator.calculate()`가 개월 수로 한 번 더 나누도록 수정. **WEEK 단위 기간의 주→개월 환산 규칙은 2026-08-09 4주=1개월로 확정**(사용자 확인, 코드의 임시값 표기 제거). `negotiation` 도메인의 `NegotiationConditionCalculator`도 동일하게 4주=1개월을 쓰고 있어서 두 도메인 계산 기준이 원래부터 일치했음을 확인.
- **findClientAccountId 관련 버그 리포트(확인 결과 문제 없음)**: 3번은 이 포트가 client_profile.id를 그대로 반환하는 걸로 오해했으나, 우리 `ProjectDirectoryAdapter.findClientAccountId`는 이미 account 도메인의 `findClientProfileById`로 한 번 더 조회해서 진짜 accountId로 변환해 돌려주고 있다(`ClientGradeResolver`가 정상 동작). 포트 이름 변경 불필요.
- **결제→매칭 이벤트 리스너 신규 구현**: `RecruitingStartedEvent(projectId)`(project 도메인이 착수금 결제 커밋 후 발행)를 받아 포지션별로 ①스냅샷 동결(`MatchingSnapshot` PROJECT/POSITION 타입, 기존에 정의만 되고 안 쓰이던 것을 처음 사용) → ②임베딩 upsert(`MatchingPort.upsertPositionEmbedding`, Pairing-python의 `PUT /embeddings/positions` 신규 연동) → ③최초 추천 라운드 생성(`MatchingRoundCreationService.createRound` 재사용) 순서로 처리.
  - `RecruitingStartedEventListener`(포지션 목록 조회 + 포지션 단위 예외 격리) / `RecruitingStartedPositionHandler`(포지션 1건 처리, `@Transactional(REQUIRES_NEW)`)로 클래스를 분리했다 — 같은 빈 안에서 `this.method()`로 자기 자신을 호출하면 스프링 프록시를 안 거쳐서 `@Transactional`이 조용히 무시되는 문제(자체 호출 self-invocation)가 실제로 발생해서(테스트로 재현·확인함), 별도 빈으로 쪼개 진짜 프록시 호출이 되게 했다. `@TransactionalEventListener(AFTER_COMMIT)` 쓸 때 이 문제를 특히 조심해야 한다.
  - 멱등 가드(`countByPositionId > 0`)와 포지션 단위 예외 격리(한 포지션 실패해도 나머지 포지션은 계속 처리) 적용.
  - 임베딩 텍스트는 지금 매칭 쪽 요약에 있는 필드(제목/직무/스킬/경력/근무조건/기간)로만 구성한다. **(2026-08-09 갱신)** `mainTask`는 매칭 요청 상세 노출 작업으로 매칭 쪽 요약(`ProjectPositionSummary`)에 이미 들어왔지만 임베딩 텍스트에는 아직 안 넣음(따로 결정 필요). `currentSituation`은 매칭 쪽 요약에 여전히 없음(노출 결정이 안 나서 안 가져옴). 업무범위/우대사항(`detailScope`/`extraNote`)은 이미 포함됨.
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
  **(2026-08-10 완료)** `expire()`(응답기한 만료)는 `MatchingRequestExpiryScheduler`(10분 주기,
  `application.scheduler` 패키지)가 `MatchingRequestService.expireOverdueRequests()`를 호출해서
  처리한다 — `syncProjectStage`도 함께 호출. 스케줄러 주기 사이의 창구(만료됐는데 아직 처리 전)는
  `accept()`/`reject()` 진입 시 `isExpired()`를 먼저 검사해서 그 자리에서 만료 처리하고 `MT_016`으로
  막도록 같이 처리함(리뷰에서 발견된 구멍).
- 회귀 테스트: `MatchingIntegrationTest`에 CLOSED/CANCELED 프로젝트 차단 2건 + accept 시 프로젝트
  상태가 실제로 NEGOTIATING으로 바뀌는지 검증 추가. `MatchingNegotiationOutcomeServiceTest`에 결렬 후
  프로젝트가 RECRUITING으로 되돌아가는지 검증 추가(프로젝트 row를 실제로 심어야 함 — `syncStage`/
  `startNegotiating`이 project row가 없으면 PJ_001을 던지므로, 기존에 `projectId=1L`처럼 실존하지 않는
  더미 값을 쓰던 테스트는 실제 프로젝트 row 시딩으로 다 바꿔야 했음).
- `feature/matching-project-stage-sync` 브랜치, `./gradlew clean build` 통과 확인.

## 확정된 설계 결정 (요약, 상세 근거는 각 요구사항 R01~R05/정책 P02~P09 참고)

1. **파이프라인 (2026-08-11 팀 확정으로 전면 개정)**: 하드필터 → **임베딩 30 + DB 조건점수 70 합산** → 상위 (모집인원×3) → LLM 최종선정 → 가드. 상세는 아래 **"2026-08-11 갱신 — 매칭 파이프라인 재설계(팀 확정)"** 절이 최종본이다. 이 줄 아래의 옛 서술(Stage B 폐기, 임베딩에 구조화 필드 포함 등)은 **전부 그 절로 대체됐다.**
   - 한 줄 요약: **임베딩 = 문장 맥락 / DB 점수 = 조건·스킬 / LLM = 순위·근거 / 가드 = 예산·이상응답 차단**
2. **점수**: 사람이 가중치 배점 안 함. LLM이 최종 점수·근거 산출. 0~100 스케일 가정(팀 정책 회의 "월요일 확정" 대기 중). 50점 미만이면 경고 — **최초 추천화면에서 미리 보여줌**(대기 순번 N+1~3N도 이미 한 번의 LLM 호출로 채점되어 있어서 가능). 점수 숫자 자체는 화면·API 응답에 노출 안 함.
3. **예산**: 순예산 = 입력예산×(1-수수료율), 수수료율 = 금액구간(1억)×클라등급(다이아 -2%). 프리랜서 단가는 월단가로 통일(일급×20일, 시급×160시간). 조합 총액 ≤ 순예산×1.2 허용.
4. **임베딩 쓰기 (2026-08-11 팀 확정으로 개정)**: 프리랜서는 **이력서 정식 저장/수정 시**(임시저장 제외), 포지션은 **착수금 결제 완료(모집 시작) 시점** + 결제 후 프로젝트 수정 시. ~~검수 통과 시점~~은 폐기. 정책 P03의 "등록 시점"과도 다르다 — 팀이 결제 완료로 확정했다. 상세는 위 "매칭 파이프라인 재설계" [0] 참고.
4-1. **등급 가중치(grade_weight, 2026-08-07 확정)**: 명세/정책/원본 엑셀 다 확인해도 숫자 없음(정성적 "매칭 확률 증가"만 있음, 수수료 할인 1%/2%는 별개) — 그 패턴을 그대로 재사용해 **클라이언트 등급: 실버 0% / 골드 +1% / 다이아 +2%**로 확정. `MatchingCandidate.applyGradeWeight(double)`에 이미 구현됨(퍼센트 값은 서비스 계층에서 등급 조회해 전달). **클라이언트 간 자원배분(등급 높은 클라한테 좋은 프리 우선배정)은 하지 않음** — R02.6("같은 프리랜서가 여러 클라이언트에게 동시 추천 가능")과 충돌해서 폐기. 대신 **프리랜서 등급 타이브레이커**를 추가: base_score가 완전 동점일 때만 프리랜서 등급(마스터>시니어>주니어)으로 2차 정렬. 이건 아직 구현 전 — 노출 순위(rank_no) 계산하는 서비스 로직 만들 때 반영해야 함(엔티티 레벨 변경 불필요, 정렬 비교자만 추가하면 됨).
5. **협상(5번) 연동 계약**: `POST /requests/{requestId}/acceptance` 처리 시 아래를 동기 호출로 넘김(같은 트랜잭션, 실패 시 롤백)
   - **스냅샷 diff 대상 4개**: `payUnit`+`payAmount`, `workStyle`, `workForm`, `availableFrom` (전부 `FreelancerConditionResponse` 필드 재사용)
   - **보조 2개**: `minAcceptAmount`(금액 가드 하한), `startNegotiable`
   - **PERIOD**: `periodValue`가 null이면 그 협상에서 제외, 값 있으면 포함(블랑켓 제외 아님)
   - 경력연차·스킬은 매칭 필터로만 쓰고 diff 대상 아님
   - `budgetCap`: 3일 마감 때문에 1단계는 "순예산÷확정인원"으로 단순화, 나중에 Stage F 정확한 배분값으로 교체(필드명 동일 유지)
   - ~~블로커: negotiation 도메인에 application 계층이 아직 없음~~ — 2026-08-08 해소. `NegotiationCommandUseCase`/`NegotiationProgressUseCase` 실구현 완료, `NegotiationAdapter`로 연동함.

## ~~2026-08-09 갱신 — Stage B 조건필터 폐기~~ (2026-08-11 뒤집힘, 기록 보존용)

> ⚠️ **이 절의 결론은 2026-08-11 팀 결정으로 뒤집혔다.** 위 "매칭 파이프라인 재설계" 참고.
> 폐기 사유("사전검수 후보수와 어긋나 착수금 클레임")는 **여전히 유효**하지만, 해법이 틀렸다 —
> 배제하는 대신 **점수만 매기면** 아무도 풀에서 빠지지 않아 그 문제가 아예 생기지 않는다.
> 아래는 당시 판단 근거를 남겨두는 기록이다.

### (원문) Stage B 조건필터(일정/근무조건/단가) 폐기, Stage E 감점으로 이동

3번에게 재확인 요청을 보낸 결과, 애초에 이 필터가 정책/명세에 근거가 없던 우리 자체 가정이었음이 드러났다. 3번 논거: 사전검수(P02)가 안내하는 후보 수는 직무일치+요구스킬 전부보유+계정ACTIVE+AI매칭동의만 본 값인데, 매칭이 여기에 일정/근무조건/단가 필터를 더 얹으면 "사전검수 예상 후보 5명 → 착수금 결제 → 실제 추천 1명" 같은 상황이 생길 수 있고, 착수금은 환불이 없어서 클레임 구조가 된다는 것.

코드·문서로 직접 재확인함:
- `policy.md` P03/P04: 매칭 파이프라인은 "임베딩 1차 추림 → LLM 최종 선정"이 전부. 조건필터 단계는 정책에 원래 없었다.
- `requirements.md` R02.3: 가드 AI도 "직무, 스킬"만 검증한다고 명시. 일정/단가는 가드 대상도 아니다.
- `ProjectPreReviewService`/`FreelancerCandidateCountService` 코드: 사전검수 후보 수 계산이 정확히 3번이 말한 4개 조건(직무+스킬AND+ACTIVE+AI매칭동의)만 쓴다. 일정/근무조건/단가는 전혀 안 봄 — 3번 말이 코드로도 확인됨. **(2026-08-10 추가)** 이후 `matchingPaused=false` 조건이 하나 더 생겼다 — 사전검수와 Stage B 하드필터 양쪽에 동시에 반영해서 후보수 불일치가 다시 안 생기게 함(둘 다 5개 조건: 직무+스킬AND+ACTIVE+AI매칭동의+매칭일시중지아님).
- 사용자가 팀 회의에서 기억하던 "단가 20% 오차" 규칙은 별개인 budgetCap 조합 총액 규칙(순예산×1.2 허용, 위 결정 3번)이었음을 재확인 — Stage B/E용으로 따로 정해진 수치는 없었음.

**결론**: Stage B에서 일정/근무조건/단가는 필터링(하드/느슨하게 불문)하지 않는다. 하드필터는 AI매칭 동의+직군/직무만 남긴다(사전검수와 동일 기준이라 후보수 불일치 위험 없음). 일정/근무조건/단가 불일치는 Stage E(LLM 최종선정) 프롬프트에서 감점 요인+추천 사유로만 반영 — 후보를 풀에서 배제하지 않는다. **(2026-08-10 구현 완료)** Stage E 감점은 `Pairing-python`의 `MatchingService._build_prompt`에 반영됨 — 아래 "Pairing-python 현황" 참고. 이건 정책 P09("적합도 낮은 후보도 부족하면 노출될 수 있다")와 정확히 같은 패턴이라 새 정책 근거가 필요 없다. 계산식(퍼센트 등)은 우리가 직접 만들지 않고 LLM이 원본 데이터를 보고 판단하게 한다.

이 필터(HANDOFF #10)는 착수 시점부터 이 방향으로 구현한다:
- Python `FreelancerProfile`/`PositionRequirement`(`app/domains/matching/repository.py`)에 조건 필드 추가 — 프리랜서 쪽 `freelancer_condition.pay_unit/pay_amount/work_style/work_form/available_from`, 포지션 쪽 `project_position`/`project`의 예산·기간·희망시작일(Java `ProjectPositionSummary`가 이미 `workStyle/workForm/periodValue/periodUnit/startDesiredDate/budgetAmount`로 들고 있는 것과 대응).
- `_build_prompt`/`_describe_candidate`/`_describe_position`에 이 필드들 채우고, "조건 불일치해도 제외하지 말고 감점+사유로 반영하라"는 프롬프트 지시 추가.
- Stage F 가드는 원래도 직무/스킬만 검증하는 설계였으니 변경 불필요.

## 2026-08-09 갱신 — 매칭 요청 상세에 `mainTask` 노출

3번 답변: `mainTask`(주요 담당 업무)만 노출, `currentSituation`(현재 상황)은 배경 설명이라 안 노출. 노출 위치도 목록/카드가 아니라 **요청 상세**(`GET /requests/{requestId}`)로 한정 — 줄바꿈 있는 최대 1500자 텍스트라 카드에는 안 맞음.

- `ProjectPositionSummary`(매칭 로컬, `application.result`)에 `mainTask` 필드 추가, `ProjectDirectoryAdapter.findPositionSummary`가 project 도메인 응답에서 그대로 매핑(이미 나오고 있던 값, 매칭 쪽만 안 옮기고 있었음).
- `RecruitingStartedPositionHandler.freezeSnapshot`이 PROJECT 스냅샷 payload에 `mainTask`도 얼려둠(R32 대상 — 프로젝트 수정으로 바뀔 수 있는 필드라 라이브로 안 읽고 모집 시작 시점 값 고정). **이미 모집 시작한 프로젝트의 스냅샷에는 이 필드가 없어서 null로 나옴** — 새로 모집 시작하는 프로젝트부터 채워짐.
- `MatchingRequestResponseAssembler`에 `buildDetail()` 신규 추가(기존 `build()`는 유지, `mainTask` 항상 null) — `MatchingRequestService.findRequest()`만 `buildDetail()`을 쓰도록 교체. `MatchingRequestResponse`에 `mainTask` 필드 추가.
- **버그 발견·수정**: 이번에 처음으로 `GET /requests/{requestId}`에 실제 테스트를 붙이다가 발견 — `findRequest()`가 `resolveFreelancerId(accountId)`를 클라이언트 소유 여부 확인보다 먼저 무조건 불러서, **클라이언트가 자기가 보낸 요청의 상세를 조회할 때마다 항상 404(FREELANCER_NOT_FOUND)가 나던 버그**였다. 이 엔드포인트를 실제로 검증하는 테스트가 지금까지 하나도 없어서 안 드러났음. 클라이언트 소유 여부를 먼저 확인하고, 아닐 때만 `resolveFreelancerId`를 부르도록 순서 변경.
- `docs/api-dto.csv`/`.ai/API.md` 동기화. `MatchingIntegrationTest`에 "상세에서만 보이고 목록/받은요청에서는 안 보인다" 검증 테스트 추가.

## 2026-08-11 갱신 — 매칭 파이프라인 재설계 (팀 확정, 이 절이 최종본)

> 팀 논의로 확정. **"확정된 설계 결정 1"의 옛 서술과 그 아래 "Stage B 조건필터 폐기" 절을 대체한다.**
> 아직 코드 미반영 — 작업 목록은 `.ai/HANDOFF.md` 참고.

### 왜 바꿨나

리뷰에서 "스킬·연차가 1차 추림에 반영되지 않는다"는 지적이 나왔고, 확인해보니 사실이었다.
원인은 **Stage B(조건 필터)를 2026-08-09에 폐기한 것**이었다. 폐기 사유였던 "사전검수 후보수와
어긋나 착수금 클레임"은 타당했지만, **배제하지 않고 점수만 매기면 그 문제가 생기지 않는다** —
아무도 풀에서 빠지지 않고 순위만 밀리기 때문이다. 그래서 Stage B를 점수 방식으로 되살린다.

**데이터 종류마다 맞는 도구가 다르다는 게 이 설계의 뼈대다:**

| 종류 | 예 | 도구 |
|---|---|---|
| 자유 서술 | 자기소개, 담당업무, 우대사항 | **임베딩** (이것만 "주문 시스템 개발 ↔ 결제 API 구축"의 유사함을 잡는다) |
| 집합 | 스킬 | **SQL 교집합** (5개 중 3개 = 60%) |
| 수치 | 연차, 단가, 기간 | **SQL 부등호** (5 ≥ 3 판정) |
| 범주 | 근무방식/형태 | **SQL 같음 비교** |

임베딩에 숫자를 넣으면 안 되는 이유: `"월 500만원"`과 `"월 5000만원"`이 벡터상 거의 같고,
연차는 **방향이 반대로** 작동한다 — `"경력 3년 이상"`에 `"경력 3년"`이 `"경력 10년"`보다
가깝게 나와서 더 자격 있는 사람이 뒤로 밀린다.

### [0] 임베딩 저장 시점 (쓰기 경로)

| 대상 | 언제 | 트리거 |
|---|---|---|
| 프리랜서 | **이력서 정식 저장/수정** | `ResumeUpdatedEvent`. 학과·경력·자기소개가 전부 `resume` 계열이라 이 하나로 다 커버된다. **임시저장(`saveDraft`)은 이벤트를 안 낸다** — 미완성 이력서로 벡터가 생기지 않는다 |
| 프리랜서 | 관리자 일괄 재색인 | `POST /admin/embeddings/reindex` |
| 포지션 | **착수금 결제 완료(모집 시작)** | `RecruitingStartedEvent`. **팀 확정 — 정책 P03의 "등록 시점"과 다르지만 결제 완료로 간다** |
| 포지션 | 결제 후 프로젝트 수정 | `ProjectUpdatedEvent` (결제 전 수정은 발행 안 함 — 벡터가 아직 없어서 맞다) |

**조건 저장은 임베딩과 무관하다.** 스킬·숙련도·단가·근무조건이 전부 DB 점수로 가므로 조건을
바꿔도 벡터가 똑같이 나온다. 조건 변경은 파이썬이 `freelancer_condition`을 매번 직접 읽으므로
**저장 즉시 다음 추천부터 반영**된다. (2026-08-11에 잠깐 만들었던 `ConditionUpdatedEvent`는
이 결정으로 불필요해져 삭제 대상이다.)

### [1] 하드필터 — 여기만 후보를 배제한다

- AI매칭 동의 / 매칭 일시중지 아님 / 계정 ACTIVE
- 직군·직무 일치
- **요구 스킬 1개 이상 보유** ← 신규. 0개는 진짜 무관한 사람이라 LLM 비용만 쓴다
- 같은 프로젝트에서 이미 후보로 나온 프리랜서 제외 (R02.5)

**"전부 보유"가 아니라 "1개 이상"인 이유**: 5개 중 4개 가진 좋은 사람을 놓치지 않기 위해서다.
느슨해 보이지만 **노출은 모집 인원만큼만** 하므로, 5/5인 사람이 충분하면 3/5는 화면에 안 뜬다.
5/5가 부족할 때만 차선이 올라오는데, 그건 정책 P09("조건에 맞는 후보가 부족하여 적합도가
낮은 후보가 포함될 수 있습니다")가 이미 허용하는 상황이다.

**사전검수(P02)는 안 건드린다(팀 확정).** 지금처럼 "전부 보유" 기준으로 안내한다. 매칭이 더
느슨해서 후보가 안내보다 **많이** 나오는 건 클레임이 안 된다(적게 나올 때가 문제다).

### [2][3] 임베딩 30 + 조건점수 70 = 합산

**합산이지 순차가 아니다.** 유사도로 먼저 3N을 뽑고 그 안에서 조건 정렬하면(순차), 누가 후보가
되는지를 유사도가 100% 정하게 되어 **70이라는 비중이 무의미해진다.** 조건이 완벽한데 자기소개가
짧아 유사도가 낮은 사람이 아예 안 나오면 안 된다.

```
점수 = 정규화된_유사도 × 30  +  조건점수 × 70   →  이 값 순으로 상위 (모집인원 × 3)
```

**임베딩 대상 (30점)**

| 프로젝트 | 프리랜서 |
|---|---|
| 진행 상황 `project.current_situation` | 학력의 **학과** `resume_education.major` |
| 주요 담당업무 `project.main_task` | 경력 **세부 입력내용** `resume_career.job_description` |
| 세부 업무범위 `project.detail_scope` | 자기소개 `resume.self_introduction` |
| 우대사항 `project.extra_note` | |
| 포지션별 우대사항 `project_position.preferred_note` | |

- **학과를 넣는 이유**: 클라이언트가 우대사항에 `"○○학과 우대"`라고 쓸 수 있어서다. 그래서
  **우대사항이 프로젝트 쪽에 반드시 같이 들어가야** 이 항목이 의미를 갖는다(둘은 세트다).
- 경력의 **회사명·부서/직급은 제외**(팀 확정). 업무 내용만 본다.
- `preferred_note`는 포지션 전용이라 `extra_note`보다 정확하다. **지금 매칭까지 안 내려오고
  있어서 `ProjectPositionSummary`에 필드 추가 + 어댑터 매핑이 필요하다.**
- **유사도 정규화 필요**: 텍스트 코사인 유사도는 0.55~0.85 같은 좁은 구간에 몰려서, 그냥 ×30
  하면 실질 9점 폭이 되어 70이 다 결정해버린다. 풀 안에서 0~30으로 펴야 한다. 정규화 상수는
  **재색인 후 실제 분포를 측정해서** 정한다(지금 추측으로 박지 않는다).

**조건점수 (70점)**

| 항목 | 점수 | 채점 |
|---|---|---|
| 스킬 일치율 + 숙련도 | 25 | `25 × 일치율 × (0.8 + 0.2 × 평균숙련도)` |
| 경력연차 | 15 | 충족 만점 / 미달 시 부족분만큼 차감 |
| 단가 | 15 | `budgetCap` 이내 만점 / 초과율만큼 차감 |
| 근무방식 | 8 | 일치 또는 `ANY` 만점 |
| 근무형태 | 7 | 일치 또는 `ANY` 만점 |
| 시작일 | 5 | 시작희망일 전 가능 또는 협의가능 만점 |
| 기간 | 5 | 차이만큼 차감 |

- **숙련도(초급1/중급2/고급3)는 보너스이지 필수 조건이 아니다.** `position_skill`에 요구 숙련도
  컬럼이 없어서 프로젝트가 `"Java 고급 필요"`를 표현할 수 없기 때문이다. 위 식이면
  **5/5 초급(20점) > 3/5 고급(15점)** — 개수가 이긴다.
- **단가는 SQL 혼자 계산 못 한다.** 프리랜서는 시급/일급/월급 환산이 필요하고, 포지션은
  총예산 → 순예산(수수료율에 **클라이언트 등급** 필요, account 도메인) ÷ 인원 ÷ 개월이다.
  **Java가 `BudgetCapCalculator`로 계산해 파이썬 요청 파라미터로 넘긴다.**

### [4] LLM 최종선정 — 변경 없음

포지션당 1회 호출. 원문(포지션 요구조건 + 후보 프로필/이력서)을 그대로 넘기고 순위·근거를 받는다.
**임베딩 유사도도 조건점수도 LLM에 넘기지 않는다** — LLM은 원문을 직접 읽고 자기 판단으로 점수를
새로 매긴다(기존 원칙 유지).

### [5] 가드 — G3·G4만 (팀 확정)

| # | 검증 | 실패 시 |
|---|---|---|
| G3 | **예산 초과 여부** — 후보 희망 월단가 ≤ `budgetCap`(순예산 ÷ 전체 확정인원) | **탈락 아님. 경고만 기록** |
| G4 | **LLM 응답 이상** — 중복 ID / 요청 인원 초과 / `reason` 누락 | 중복·초과분은 버림, `reason` 없으면 그 후보 탈락 |

**G3는 "조합"이 아니라 "개별" 검증이다 (2026-08-11 확정, 옛 U1).** 메모 §4의 원안은 프로젝트
전체 포지션의 1순위 조합 총액을 계산하는 것이었는데, **현재 추천이 포지션별로 따로 돌기
때문에** 포지션 A가 끝난 시점에 B는 아직 추천 전이라 전체 조합을 계산할 수 없다. 구조를
바꾸는 대신 포지션별 `budgetCap` 이내인지만 본다(`BudgetCapCalculator`의 현행 공식 그대로).

**G3는 후보를 탈락시키지 않는다.** 메모 §4 5단계가 "그래도 안 맞으면 예산 안내 경고 노출하고
진행(P09 저품질 경고와 같은 패턴)"이라 배제가 아니고, [3] 조건점수의 단가 15점도 초과율만큼
감점만 하기로 했다. 여기서 배제하면 **Stage B 폐기 사유(단가로 배제하면 사전검수 후보수와
어긋남)가 그대로 되살아난다.** `guardPassed=true`로 통과시키되 `guardReason`에 사유를 남긴다.

→ 결과적으로 **가드에서 실제로 후보를 거르는 건 G4뿐이고, G3는 기록·경고용**이다.
화면에 예산 경고 배너가 필요하면 회차 단위 플래그(`lowScoreWarned`와 같은 방식)를 추가해야
하는데, 그건 프론트 전달 문서 갱신이 따르므로 필요해질 때 별도로 정한다.

**직무·스킬 재검증은 뺀다(팀 확정).** 하드필터를 통과한 사람만 LLM에 가고 LLM은 그 풀 안에서만
고르므로, 가드에 도착한 후보는 이미 직무·스킬을 통과한 사람이다. 같은 걸 또 봐도 아무도 안 걸린다.

⚠️ **명세 R02.3이 "가드 AI 로 마지막 검증 (직무, 스킬 검증)"이라고 적혀 있어 글자상 어긋난다.**
설명은 이렇게 한다: **"직무·스킬은 1차 필터에서 보장하고, 가드는 예산 조합과 LLM 응답 이상을
막는다."** 발표에서 질문이 나오면 이 문구를 쓴다.

⚠️ **G3에 미해결 구조 문제가 있다 — 아래 "미확정" 참고.**

### [6] 후보 노출 이후 — 변경 없음

노출 → 클라이언트 선택 → 요청 발송 → 수락/거절/만료 → 협상 → 계약. 기존 그대로.

### 미확정 (착수 전 정해야 함)

| # | 항목 | 내용 |
|---|---|---|
| ~~U1~~ | ~~G3 예산 조합 계산 범위~~ | **2026-08-11 확정: ㉮ 포지션별 `budgetCap` 이내인지만 확인.** 위 [5] 참고 |
| U2 | 프로젝트명(`title`) 임베딩 포함 여부 | 팀이 정한 목록에 없는데 현재 코드엔 들어가 있다. `"AI 추천 시스템 구축"` 자체가 맥락이라 넣는 쪽을 권장 |
| U3 | 유사도 정규화 상수 | 재색인 후 실측해서 결정 |
| U4 | 예산 경고를 화면에 띄울지 | G3가 기록만 남기는 구조라, 클라이언트에게 안내하려면 회차 플래그 + API 필드 + 프론트 문서 갱신이 필요하다. 필요해지면 정한다 |

### 향후 개선 (이번 범위 밖, 기록만)

| 항목 | 내용 |
|---|---|
| **상주근무 시 주소 비교** | 컬럼은 다 있다 — 근무지 `project.work_location`(주석: '근무 장소(상주 시)'), 프리랜서 `resume.zip_code`/`address`/`address_detail` 및 `freelancer_profile.address`. **프리랜서 쪽은 이력서 주소 권장**(우편번호가 있어 정확하고, 후보가 되려면 이력서가 반드시 있다). **비교는 시/군/구까지만, `address_detail`(상세주소)은 개인정보라 LLM에 보내지 않는다.** 거리 계산은 하지 않는다. 지금은 파이썬 `PositionRequirement`/`FreelancerProfile`에 주소 필드 자체가 없어서 SQL·dataclass·프롬프트를 다 손봐야 한다 |
| `position_skill`에 요구 숙련도 컬럼 | 있으면 숙련도를 보너스가 아니라 조건으로 쓸 수 있다 (3번 파트) |

### 3번과 협의할 것

| # | 내용 |
|---|---|
| 1 | `project_position.preferred_note`를 매칭까지 내려주기 — project 도메인 응답에 이미 있는지 확인 |
| 2 | 프로젝트 임베딩 시점을 결제 완료로 확정했음을 공유 (정책 P03 문구와 다름) |

**사전검수(P02) 기준은 건드리지 않기로 확정** — 협의 불필요.

## 2026-08-11 갱신 — 코드 리뷰 6건 검증 (내 파트만) + 유효 3건 수정

리뷰로 받은 지적을 명세(`docs/spec/requirements.md`)/정책(`policy.md`)/STATE.md/**프론트 전달
문서**와 대조해 판정했다. 최종 **4건 유효(전부 수정 완료), 3건 오탐.**

> **교훈 두 개. 둘 다 판정을 한 번씩 뒤집고 나서야 알았다.**
>
> 1. **STATE.md는 명세가 아니다.** ①을 "결정 1과 충돌하니 오탐"이라고 판정했다가 뒤집었다.
>    결정 1 자체가 명세 R01.2("프리랜서 **프로필**·이력서")를 우리가 임의로 좁힌 것이었고,
>    게다가 코드(포지션 쪽)는 이미 그 결정을 안 지키고 있었다. **우리 문서끼리만 대조하면
>    같이 틀린 걸 못 잡는다 — 명세 원문과 반대쪽 코드를 같이 볼 것.**
> 2. **레포 밖 프론트 문서도 볼 것.** ④는 코드·정책만 보고 "판단 대기"로 뒀는데,
>    `AI매칭_API_화면매핑_최신본.md`에 이미 확정된 배너 문구를 보고서야 오탐인 걸 알았다.
>    거기에만 있는 확정 사항이 있다.

### 유효 ② — 노출 안 된/거절된/가드 탈락 후보에게 매칭 요청이 나갔다 (가장 심각)

`MatchingRequestService.sendOneRequest()`가 `positionId` 일치·소유·모집중·중복만 보고
`exposed`/`rejected`/`guardPassed`를 전혀 안 봤다. 요청 API는 `candidateId`를 그대로 받으므로
화면을 안 거치면 **Stage F 가드(직무·스킬 재검증)에 떨어뜨린 후보에게도 요청이 나간다** —
가드가 통째로 무력화된다. 정책 P07("노출된 최종 후보 중 선택")과 정면으로 어긋남.

- `MatchingCandidate.isSelectable()` 신규(도메인 규칙): `exposed && !rejected && guardPassed`.
  `exposed`만 봐도 지금은 충분하지만 `guardPassed`를 같이 두는 건 의도적이다 — 노출 로직이
  바뀌어도 가드가 뚫리지 않게 한다.
- 새 에러코드 `MT_017 CANDIDATE_NOT_SELECTABLE`(400). **프론트 에러표 갱신 필요.**
- 테스트 3건(가드 탈락 / 대기 순번 / 이미 내린 후보).

### 유효 ⑥ — 자리가 다 찬 포지션에도 유료 재추천이 결제됐다

`MatchingRerecommendService.assertRecruiting()`이 CANCELED/CLOSED만 막아서, 인원이 다 찬
포지션도 재추천 회차가 열렸다. 추천은 나오지만 정작 요청을 보낼 때 `sendRequests`의 인원 초과
검증(MT_005)에 막혀 **돈만 나가고 아무것도 못 하는** 상태가 된다.

**리뷰어 제안(프로젝트 상태 IN_PROGRESS/COMPLETION_PENDING이면 차단)은 안 썼다.** 중도 종료로
자리가 다시 비면 진행중이어도 다시 뽑아야 하기 때문이다(명세의 "2/3명 진행 중 · 1명 계약 종료"
화면). 상태가 아니라 **남은 자리** 기준으로 막는 게 맞다.

- `countVacancy(positionId, headcount)` 신규. 자리 0이면 `MT_018 POSITION_ALREADY_FILLED`(400).
- 유료 재추천은 `quantity > 남은 자리`도 막는다(MT_005) — 초과분은 그대로 낭비되는 결제다.
- 인원 계산 기준을 `MatchingStatus.SLOT_RELEASED`(REJECTED/NEGOTIATION_FAILED/TERMINATED)로
  도메인에 올려 `MatchingRequestService`와 공유. 정상 완료(CLOSED)는 자리를 안 비운다.
- 테스트 3건(자리 참 / quantity 초과 / 중도 종료로 자리가 비면 진행중이어도 허용).

### 유효 ① — 임베딩 텍스트가 포지션 쪽과 짝이 안 맞았다 (판정 2회 뒤집힘, 결정 1 개정)

> **처음엔 "TODO만 지우면 된다"고 판정했다가 뒤집었다.** `.ai/STATE.md` 결정 1("자기소개+경력사항
> ↔ 프로젝트설명+담당업무+업무범위+우대사항")만 보고 오탐이라 했는데, **명세와 코드를 안 봤다.**
> 아래가 실제 근거다. 이 절이 최종 판정이다.

**명세 R01.2**: "프로젝트 요구조건 ↔ 프리랜서 **프로필·이력서** 벡터 비교" — 이력서만이 아니라
프로필(조건)까지다. 결정 1은 그걸 우리가 임의로 좁힌 것이었다.

**코드는 한쪽만 넣고 있었다.** `PositionEmbeddingTextBuilder`에는 이미 요구스킬·최소경력·
근무조건·기간이 들어가는데 `FreelancerEmbeddingTextBuilder`에는 대응 항목이 하나도 없었다:

| | 포지션 | 프리랜서(수정 전) |
|---|---|---|
| 스킬 | `requiredSkills` | 없음 |
| 연차 | `경력 N년 이상` | 없음 |
| 근무방식·형태 | `workLabel` | 없음 |
| 기간 | `periodLabel` | 없음 |

포지션 벡터엔 "Java, Spring Boot / 경력 3년 이상 / 상주 · 풀타임"이 들어가는데 프리랜서 벡터엔
그 말이 없어서, **프리랜서가 자기소개에 우연히 "자바"라고 써둔 경우에만** 걸렸다. 스킬·연차·
근무조건이 1차 추림에 사실상 반영되지 않던 상태다. (`FreelancerEmbeddingTextBuilder` 주석은
"PositionEmbeddingTextBuilder와 대칭이다"라고 적혀 있었는데 사실이 아니었다.)

**수정 내용**

- 프리랜서 텍스트에 **직무·스킬·경력연차·근무방식/형태·희망기간** 추가 → 포지션 쪽과 1:1 대응.
- 포지션 텍스트에 **`mainTask`(담당업무)** 추가. 원래 "노출 여부 팀 결정 대기(Task #4)"라 빼뒀는데
  그 결정은 2026-08-09에 이미 났다 — 철 지난 제외였다(결정 1에 원래 들어 있던 항목이다).
- **단가·시작가능일은 양쪽 다 안 넣는다.** 임베딩은 숫자를 비교하지 못해 "월 500만원"과
  "월 5000만원"이 벡터상 거의 같다 — 넣으면 정확도가 오히려 떨어진다. 포지션 쪽에도 예산·
  시작희망일이 없어서 넣으면 짝이 다시 어긋난다. 이 둘은 Stage E(LLM)가 원본 숫자를 보고 감점으로
  처리한다(위 "Stage B 조건필터 폐기"와 같은 이유).
- **조건 저장 → 임베딩 재생성 연결**(아래 별도 항목). 텍스트에 조건이 들어간 순간부터 필수가 됐다.
- 조립·전송을 `FreelancerEmbeddingRefresher`로 모았다 — 이력서 저장/조건 저장/관리자 재색인이
  각자 조립하면 **어디서 저장했느냐에 따라 같은 사람의 벡터가 달라진다**.

**결정 1 개정**: 대조 대상은 이제 "자기소개+경력사항+**조건(직무·스킬·연차·근무조건·기간)** ↔
프로젝트설명+담당업무+업무범위+우대사항+**요구스킬·최소경력·근무조건·기간**"이다. 단가·일정은
양쪽 다 제외하고 Stage E가 본다.

### 유효 ① 후속 — 조건 변경 시 임베딩이 재생성되지 않았다

원래 `FreelancerConditionService.upsert()`에 TODO만 있고 구현이 없었다. 위 수정으로 조건이
임베딩 텍스트에 들어가면서 **안 고치면 조건을 바꿔도 매칭이 옛날 조건으로 계속 돈다**.

- `ConditionUpdatedEvent`(freelancer) 신규 발행 → `ConditionUpdatedEventListener`(matching)가
  `@Async` + AFTER_COMMIT으로 받아 재생성. 이력서 쪽(`ResumeUpdatedEvent`)과 완전히 같은 구조다.
- 조건 미등록 프리랜서는 `findCondition`이 FREELANCER_NOT_FOUND를 던지므로 `null`로 흘려보내고
  이력서만으로 벡터를 만든다 — 여기서 예외를 올리면 **이력서만 쓴 사람의 임베딩이 통째로 안
  만들어져서 후보에 영영 안 잡힌다.**
- 테스트: `ConditionUpdatedEventListenerTest`(조건 저장이 실제로 재생성을 부르는지, 단가·시작일이
  텍스트에 안 들어가는지), `FreelancerEmbeddingTextBuilderTest` 6건.

### 오탐 ③ — 정렬 기준을 `fitScore`로 바꿔도 순서가 안 바뀐다

`ClientGradeResolver.resolveMatchingWeightPercent(projectId)`는 **프로젝트당 값 하나**라 그 회차
모든 후보에 똑같이 곱해진다. `baseScore` 정렬과 `fitScore` 정렬 결과가 항상 동일하다.
클라이언트 간 자원배분은 위 4-1에서 R02.6 충돌로 이미 폐기했다.

### 오탐 ⑤ — 계약관리/진행중 탭 분리

요구사항이 `내 프로젝트 → [요청받은 프로젝트, 진행중인 프로젝트]`와 별도 계약관리를 정의하고,
`/api/v1/contracts`는 역할 제한이 없어 프리랜서도 조회 가능하다. 리뷰어 전제("별도 진행중
화면이 없다면")가 성립하지 않는다. 2026-08-10에 이미 확인한 건이다.

### 오탐 ④ — `lowScoreWarned`는 지금이 맞다 (프론트 문구가 이미 확정돼 있음)

`persistCandidates()`가 **대기 순번(노출 인원 밖)** 후보만 보고 경고를 켜는 게 맞다. 처음엔
"노출 후보가 다 낮은데 경고가 안 뜬다"는 지적이 타당해 보여 넓히려 했으나, 프론트 전달 문서
(`AI매칭_API_화면매핑_최신본.md`)에 **띄울 문구까지 이미 확정돼 있었다**:

> 현재 후보를 모두 거절하면, **다음 순번 후보 중** 적합도가 낮은 후보가 포함될 수 있어요.

이 경고는 "지금 보이는 후보가 별로다"가 아니라 **"더 눌러봐야 소용없다"**를 알리는 값이다.
조건을 노출 후보까지 넓히면 저 배너 문구가 거짓말이 된다 — 배너는 다음 순번을 말하는데 실제
원인은 지금 후보이기 때문이다. 위 결정 2, 정책 P09("이후 재추천 결과 화면에서")와도 일치한다.

노출 후보가 전부 낮을 때도 알리고 싶다면 **별도 필드 + 별도 문구**가 필요한 신규 기능이지, 이
플래그를 넓힐 일이 아니다. (지금 필드는 boolean 하나라 "누가 낮은지" 구분도 못 한다.)

- **곁가지 수정**: `CandidateListResponse.lowScoreWarned`의 Swagger `@Schema` 설명이
  "재추천을 반복하면 true가 된다"로 **실제 로직과 달랐다**(프론트 문서 665행에 "Swagger만 보고
  구현하면 틀립니다"로 이미 적혀 있던 건). 실제 동작대로 고치고, 왜 넓히면 안 되는지 주석으로 남겼다.

## 아직 팀 확인 대기 중인 것

| 항목 | 상태 |
|---|---|
| ~~적합도 점수 스케일이 진짜 0~100인지~~ | **2026-08-10 확정: 0~100.** 회의를 기다릴 필요가 없었다 — 스키마 주석(`matching_candidate.base_score` "LLM 원점수(등급 가중치 적용 전, 0~100)")과 코드(`RankedFreelancer` 0~100, `MatchingRoundCreationService.LOW_SCORE_THRESHOLD = 50.0`)에 이미 0~100으로 박혀 있었다. **실제로는 Pairing-python 쪽이 이 계약을 안 지키고 있었다**: 프롬프트에 범위를 안 알려줘서 LLM이 10점 만점으로 답했고(`score=9.5`), 그대로 저장돼 모든 후보가 50점 미만 = "적합도 낮음"으로 찍히는 상태였다(에러가 안 나서 안 보이던 버그). 파이썬에 프롬프트 명시 + 응답 스키마 `minimum/maximum` + `ge/le` + 스케일 검증(최고점 10 이하면 거부)까지 넣어 방어 완료. |
| 골드 등급 수수료 할인 여부 | 다이아만 정책에 명시됨, 확인 필요 |
| 프로젝트 등록에 인원별 예산 배분 필드 존재 여부 | 없으면 지금처럼 순예산 전체 조합으로만 판단 |
| ~~`currentSituation`/`mainTask`를 프리랜서의 "받은 매칭 요청" 카드에 노출할지~~ | **2026-08-09 3번 답변 완료·구현 완료.** `mainTask`만 노출, 카드가 아니라 **요청 상세**(`GET /requests/{requestId}`)에서만. `currentSituation`은 노출 안 함(배경 설명이라 길어지기만 함). 아래 "2026-08-09 갱신 — 매칭 요청 상세 mainTask 노출" 참고 |
| ~~budgetCap 계산 시 기간이 WEEK 단위면 몇 주를 1개월로 칠지~~ | **2026-08-09 확정: 4주=1개월.** `BudgetCapCalculator` 코드 정리 완료 |

## 프론트 공유 문서 (레포 밖)

`C:\Users\user\Desktop\AI매칭_API_화면매핑_최신본.md` — 사용자가 관리하는 프론트 전달용 API-화면 매핑 문서(이 레포에는 없음). 2026-08-09에 실제 코드와 대조 검증 완료, 후속 작업 없음:

- **실제 오류 1건 발견·반영 완료**: 재추천 API 에러표의 `MT_009`(추천 후보 없음)는 코드에서 실제로 던지는 곳이 없었음(enum 정의만 있고 미사용, dead code) — 사용자가 에러표에서 제거하고 "후보 없으면 201+빈 배열" 문구를 추가함.
- **참고 안내 반영 완료**: `RerecommendRequest.quantity`는 PAID일 때 필수인데 코드에 `@NotNull` 검증이 없어서 빠뜨리면 500(NPE) 위험 — 사용자가 경고 문구와 "아직 확정/수정 필요" 표 행을 추가함.
- 나머지(공통 래퍼, PageResponse, 엔드포인트별 요청/응답 필드, enum 값, 에러코드 전체 목록, meta API 경로)는 전부 코드와 일치 확인함. 백엔드 쪽 `RerecommendRequest.quantity` `@NotNull` 검증 자체는 아직 코드로 안 고침(문서에만 위험 안내 — 실제 수정은 백로그, 아래 참고).
