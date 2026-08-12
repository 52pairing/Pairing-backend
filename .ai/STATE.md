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

1. **파이프라인 (2026-08-11 팀 확정으로 전면 개정, 2026-08-12 비중 조정)**: 하드필터 → **임베딩 25 + DB 조건점수 75 합산** → 상위 (모집인원×3) → LLM 최종선정 → 가드. 상세는 아래 **"2026-08-11 갱신 — 매칭 파이프라인 재설계(팀 확정)"** 절이 최종본이다. 이 줄 아래의 옛 서술(Stage B 폐기, 임베딩에 구조화 필드 포함 등)은 **전부 그 절로 대체됐다.**
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

### [2][3] 임베딩 25 + 조건점수 75 = 합산

> **2026-08-12 개정.** 원안은 30:70 이었다. 아래 "왜 25:75 인가" 참고.

**합산이지 순차가 아니다.** 유사도로 먼저 3N을 뽑고 그 안에서 조건 정렬하면(순차), 누가 후보가
되는지를 유사도가 100% 정하게 되어 **75라는 비중이 무의미해진다.** 조건이 완벽한데 자기소개가
짧아 유사도가 낮은 사람이 아예 안 나오면 안 된다.

```
점수 = 정규화된_유사도 × 25  +  조건점수(0~1) × 75   →  이 값 순으로 상위 (모집인원 × 3)
```

**왜 25:75 인가 (2026-08-12 확정)**

이 비율이 실제로 정하는 건 하나다:

```
유사도가 뒤집을 수 있는 조건 격차 = S ÷ (100 − S)
```

| 비중 | 뒤집히는 조건 격차 | 판정 |
|---|---|---|
| 30:70 (원안) | 42.9% | **스킬 1/5 + 경력 미달인 후보가 자기소개만으로 올라온다.** 과함 |
| **25:75 (확정)** | **33.3%** | 그 케이스를 막는다 |
| 20:80 (후보) | 25% | 더 안전하지만 임베딩이 거의 안 움직인다 |

**임베딩 비중을 낮춰도 의미 적합도 판단은 죽지 않는다** — LLM(Stage E)이 자기소개·경력사항
원문과 담당업무·업무범위·우대사항 원문을 직접 읽고 다시 판단한다. 1차 추림에서 임베딩이 하는
일은 "적합도 채점"이 아니라 **"LLM 에게 보낼 3N 명을 싸게 고르는 것"** 이다.

⚠️ **25:75 에서도 남는 것**: 스킬 1/5(중급) + 경력 2/3 = 조건 31.3% 손실은 **아직 유사도가
이긴다**(여유 2%p). 착수 전에 알고 받아들인 것이다. 실제 추천에서 "스킬 거의 없는데 글만 잘
맞는 후보"가 눈에 띄면 20:80 으로 조인다 —
`Pairing-python/tests/test_matching_scoring.py::test_similarity_can_outweigh_a_moderate_condition_gap`
이 그 경계를 숫자로 고정해두었다.

**임베딩 대상 (25점)**

| 프로젝트 | 프리랜서 |
|---|---|
| 프로젝트명 `project.title` | 학력의 **학과 전부** `resume_education.major` |
| 진행 상황 `project.current_situation` | 경력 **세부 입력내용** `resume_career.job_description` |
| 주요 담당업무 `project.main_task` | 자기소개 `resume.self_introduction` |
| 세부 업무범위 `project.detail_scope` | |
| 우대사항 `project.extra_note` | |

- **프로젝트명 포함(U2 확정)**: `"AI 추천 시스템 구축"`, `"사내 ERP 고도화"` 같은 제목 자체가
  맥락이다. 프리랜서 자기소개의 "ERP 구축 경험"과 걸린다.
- **학과를 넣는 이유**: 클라이언트가 우대사항에 `"○○학과 우대"`라고 쓸 수 있어서다. 그래서
  **우대사항이 프로젝트 쪽에 반드시 같이 들어가야** 이 항목이 의미를 갖는다(둘은 세트다).
- **학력이 여러 개면 학과를 전부 넣는다(U5 확정).** 학과명은 몇 글자라 길이 부담이 없고,
  최종학력만 보면 학부 전공이 맞는 사람을 놓친다(석사 경영학 + 학부 컴공).
- 경력의 **회사명·부서/직급은 제외**(팀 확정). 업무 내용만 본다.
- ~~포지션별 우대사항 `project_position.preferred_note`~~ — **쓸 수 없다(2026-08-11 3번 확인).**
  우대사항은 **프로젝트 단위(`extra_note`)로 통일**되어 폐기됐다. JPA 매핑도 없고
  (`ProjectPositionJpaEntity` 주석에 명시), 등록·수정 DTO에도 없고, **공용 DB는 ddl-auto라
  컬럼 자체가 없다.** 되살리려면 엔티티+DTO+등록 위저드 입력란+프론트까지 열어야 해서 기획
  결정 사안이다. **`extra_note`만 쓴다.**

> ⚠️ **`db/init/02-create-schema.sql`을 실제 스키마로 믿지 말 것.** 공용 DB는 `ddl-auto`로
> JPA 엔티티에서 생성되므로, 그 SQL 파일에만 남아 있고 실제로는 없는 컬럼이 있다
> (`position_skill.preferred_note`가 그 예). **필드 존재 여부는 JPA 엔티티나 응답 DTO로 확인할 것.**
> 확인 완료: `work_location`(향후 주소 비교용), `current_situation`, `main_task`, `detail_scope`,
> `extra_note`는 전부 실재한다.

**3번이 제안한 "포지션 `skills`를 임베딩에 넣기"는 받지 않았다.** 그게 정확히 이번 재설계의
발단이 된 버그다 — 포지션 벡터에만 `"Java, Spring Boot"`가 있고 프리랜서 벡터엔 대응하는 말이
없어서, 프리랜서가 자기소개에 우연히 "자바"라고 써둔 경우에만 걸렸다. 프리랜서 쪽에서도 스킬을
뺐으므로 포지션에만 넣으면 짝이 다시 어긋난다. 스킬은 코드값(`JAVA`)이지 문장도 아니다.
스킬은 [3] 조건점수 25점이 훨씬 정확하게 처리한다.

**유사도 정규화 — 순위 기반(U3 확정, 상수 없음)**

텍스트 코사인 유사도는 0.55~0.85 같은 좁은 구간에 몰린다. 그냥 배점을 곱하면 "고정 보너스 +
작은 변동"이 되어 **조건점수가 순위를 100% 결정**하고, 25:75라는 비중이 무의미해진다.

고정 상수(최저·최고)를 실측해 펴는 방법도 있으나, **순위 기반이면 상수 자체가 필요 없다**:

```
유사도 1등 → 30점,  중간 → 15점,  꼴찌 → 0점   (PERCENT_RANK)
```

분포가 어떻든 항상 0~30 전체를 쓴다. 재색인 후 실측 단계가 사라진다.

⚠️ **전제: 순위는 하드필터 통과자 _전원_ 을 대상으로 매긴다.** 유사도로 먼저 자르면 조건이
좋은데 유사도가 낮은 사람이 잘려나가 처음 문제로 돌아간다. 수백~수천 명 규모에서는 성능
문제가 없다. **수만 명 규모가 되면 다시 검토할 것.**

절대적 유사도 차이는 무시된다(1등과 2등이 거의 같아도 순위만큼 벌어진다). 그게 문제가 되면
고정 상수 방식으로 바꾸고 실측 단계를 넣는다.

**조건점수 (70점)**

채점식은 U6으로 확정했다.

> **2026-08-12 개정 — 배점 합계가 100 이다.** 원래 표(25/15/15/8/7/5/5)는 합이 **80인데 문서엔
> 70이라고 적혀 있었다**(앞 5개가 정확히 70이라, 시작일·기간이 나중에 붙으며 재검산이 빠진 것).
> 정규화하면 절대값은 의미가 없고 **항목 간 비율만** 남으므로, 100으로 맞춰 "스킬이 전체의 30%"가
> 바로 읽히게 했다. 항목 간 우선순위(스킬 > 연차=단가 > 근무방식 > 근무형태 > 시작일=기간)는 그대로다.

| 항목 | 배점 | 공식 |
|---|---|---|
| 스킬 | 30 | `30 × 일치율 × (0.8 + 0.2 × 평균숙련도)`<br>일치율 = 보유한 요구스킬 ÷ 요구스킬 개수<br>평균숙련도 = 보유한 요구스킬들의 값 평균 (초급 0 / 중급 0.5 / 고급 1)<br>**요구 스킬이 없는 포지션은 전원 만점**(우열이 없는데 0점을 주면 다른 항목 비중만 커진다) |
| 경력연차 | 20 | 충족 → 20 / 미달 → `20 × (보유 ÷ 요구)` |
| 단가 | 20 | `budgetCap` 이내 → 20 / 초과 → `20 × (1 − 초과율)`, 최소 0<br>초과율 = (희망 − cap) ÷ cap<br>**`budgetCap`이 없으면 만점**(기준이 없는데 0점을 주면 유사도 비중만 커진다) |
| 근무방식 | 10 | 일치 또는 어느 한쪽이 `ANY` → 10 / 불일치 → 0 |
| 근무형태 | 8 | 일치 또는 어느 한쪽이 `ANY` → 8 / 불일치 → 0 |
| 시작일 | 6 | **어느 한쪽이라도** 협의가능, 또는 시작희망일 전 → 6 / 늦음 → `6 × (1 − 지연일수/60)`, 최소 0 |
| 기간 | 6 | 값 없음(협의가능) 또는 희망 ≥ 요구 → 6 / 짧음 → `6 × (희망 ÷ 요구)` |
| **합계** | **100** | 이 합계를 100으로 나눠 0~1 로 만든 뒤 × 75 |

- **숙련도(초급/중급/고급)는 보너스이지 필수 조건이 아니다.** `position_skill`에 요구 숙련도
  컬럼이 없어서 프로젝트가 `"Java 고급 필요"`를 표현할 수 없기 때문이다.
- **연차는 초과 보유에 추가 가점을 주지 않는다.** 요구 3년에 10년차나 3년차나 20점이다.
  "3년이면 충분한데 10년차가 더 나은가"는 프로젝트 성격에 달린 판단이라 LLM(Stage E)에 맡긴다.
  가점하면 예산만 비싼 고연차가 항상 앞에 선다.
- 검산: `5/5 전부 초급 = 30×1.0×0.8 = 24점` **>** `3/5 전부 고급 = 30×0.6×1.0 = 18점`
  → **개수가 이긴다.** 연차 요구 3년·보유 1년 = 6.67점. 단가 cap 900만·희망 1,100만 = 15.56점.
- **시급/일급 → 월단가 환산은 일급 ×20 / 시급 ×160**(설계 결정 3 + 협상 도메인
  `FreelancerConditionSnapshot.monthlyPay()`와 동일). 어긋나면 매칭이 같은 사람을 협상보다
  비싸게 봐서 감점이 과해지는데 **양쪽 다 에러 없이 돌아가 발견이 어렵다.**
- `freelancer_condition.pay_amount`는 **원 단위 저장**이다(화면만 만원 단위로 받고 서버는 만원
  배수인지만 검증 — `FreelancerCondition.requireInTenThousandUnit` 주석). 만원으로 착각해 10000을
  곱하면 전원이 예산 초과로 0점이 된다.
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
| G3 | **예산 조합** — 노출 후보 전원의 월단가 **합계** ≤ `남은 1인 상한 × 노출 인원 × 1.2` | **탈락 아님. 경고만 기록** |
| G4 | **LLM 응답 이상** — 중복 ID / 요청 인원 초과 / `reason` 누락 | 중복·초과분은 버림, `reason` 없으면 그 후보 탈락 |

**G3 세부 (2026-08-12 확정)**

```
이 포지션 몫 월예산 = budgetCap × 모집 인원
이미 쓴 것          = Σ(자리를 차지 중인 사람의 월단가)      ← 아래 규칙
남은 1인 상한       = (몫 − 이미 쓴 것) ÷ 남은 자리
판정                = Σ(노출 후보 월단가) ≤ 남은 1인 상한 × 노출 인원 × 1.2
```

- **"자리를 차지 중"** = `matching_request.status NOT IN MatchingStatus.SLOT_RELEASED`.
  `countByPositionIdAndStatusNotIn`이 이미 같은 기준으로 세고 있다(목록 버전 메서드만 추가하면 된다).
- **그 사람의 월단가는 협상 타결가를 우선한다.**
  `NegotiationPort` → `NegotiationProgressUseCase.findProgressByRequestId(requestId)` 로
  negotiationId 를 얻고 → `NegotiationQueryUseCase.getAgreedForContract(id).agreedAmount()`.
  **둘 다 이미 공개된 인바운드 유스케이스라 5번에게 요청할 것이 없다**(계약 도메인이 같은 경로를 쓴다).
  `Negotiation.agreedAmount` 는 **합의된 월 단가(원)** 이라 `budgetCap`과 단위가 같다.
- `getAgreedForContract`는 **타결 전이면 `NOT_AGREED` 예외를 던진다.** 매칭 요청 상태가
  `CONTRACT_PENDING` 이상일 때만 부를 것.
- ⚠️ **`agreedAmount`에 개월 수를 곱하지 말 것 (5번 확인, 2026-08-12).** 이미 월 단가라
  `budgetCap`과 단위가 같다. 곱하면 상한이 개월 수배로 부풀어 **경고가 영영 안 뜬다.**
  (`AgreedNegotiationView` javadoc이 "총액"이라고 잘못 적어놨던 것 — 5번이 수정 중. 계약
  도메인은 `Contract.java`가 `salaryAmount * months`로 제대로 곱하고 있어 문제 없었다.)
- 타결가가 없으면 **희망 단가**를 쓴다. 그때는 근사가 아니라 정확한 값이다 — (a) 아직 협상 전이면
  타결가 자체가 존재하지 않고, (b) `NegotiationConditionCalculator`는 `monthlyPay > budgetCap`
  일 때만 AMOUNT 조건을 만들므로 조건이 없다는 건 깎을 게 없었다는 뜻이다.
- **최초 추천에서는 "이미 쓴 것"이 0이라 `남은 1인 상한 = budgetCap`** 이다. 이 계산이 의미를 갖는
  것은 재추천뿐이다. 안 하면 자리가 찰수록 "항상 여유 있음"으로 나와 경고가 무의미해진다.
- **개인별로 재지 않는다.** 개인이 `budgetCap` 이내인지는 [3] 조건점수 단가 20점이 이미 본다.
  또 보면 같은 판정을 두 번 하는 것이고, 시니어1+주니어2 같은 정상 구성을 오탐한다.
- `guardReason` 예시(화면 노출 없음, 로그·디버깅용이라 숫자를 다 넣는다):
  `예산 조합 초과: 합계 2,000만 > 상한 1,800만 (남은 1인 750만 × 노출 2명 × 1.2, 확정 1명 타결가 1,200만 반영)`

**G4 세부 (2026-08-12 확정)**

- **중복 ID** → 뒤엣것 버림
- **요청 인원 초과** → 상위 N만 취함(순위가 이미 매겨져 있다). 전량 실패로 처리하지 않는다
- **`reason` 누락** → 그 후보 탈락. 빈자리는 **다음 순위 후보가 채운다**(가드 탈락 처리와 같은 방식)

**G3는 "포지션 단위 조합" 검증이다 (2026-08-11 확정, 옛 U1).**

```
budgetCap  = 순예산 ÷ 프로젝트 전체 인원 ÷ 개월수   (월단가, BudgetCapCalculator 현행 공식)
판정       = Σ(노출 후보 희망 월단가)  ≤  budgetCap × 노출 인원 × 1.2
```

- 분모를 **모집 인원이 아니라 노출 인원**으로 잡는다. 후보가 부족해 2명만 노출됐으면 2명 몫으로
  판정해야 한다 — 3명 몫으로 재면 남은 자리 예산까지 끌어다 쓴 셈이 되어 헐거워진다.
- `× 1.2`는 메모 §3의 "**조합 총액** ≤ 순예산 × 1.2(20% 오차범위)" 규칙 그대로다. 이 배수는
  조합 총액에 정의된 것이고 **개인 상한에 적용할 근거는 없다.**

**개인별로 보지 않는 이유 (검토했다가 뒤집은 안, ㉮)**: 후보 개인이 `budgetCap` 이내인지는
**[3] 조건점수의 단가 15점이 이미 본다.** 가드에서 또 개인을 보면 완전히 같은 판정을 두 번 하는
것이라 새로 알아내는 게 없다. SQL은 후보를 한 명씩 채점해서 **합계를 알 수 없고**, 가드는 후보가
확정된 뒤라 알 수 있다 — 그 차이가 가드의 존재 이유다.

또 개인 기준은 **시니어 1명 + 주니어 2명 같은 정상적인 팀 구성을 오탐**한다. 순예산 2,700만원에
`1,100 + 800 + 800 = 2,700만원`이면 예산이 딱 맞는데, 개인 상한 900만원으로 재면 시니어가
경고에 걸린다. 메모 §3도 "**인원별 균등 분배 아님.** 프리랜서가 입력한 희망 단가에 따라 조합을
맞춘다"고 명시한다.

**프로젝트 전체 조합(메모 §4 원안, ㉯)은 지금 구조에서 불가능하다.** 추천이 포지션별로 따로
돌아서 포지션 A가 끝난 시점에 B는 아직 추천 전이다. 게다가 §4의 핵심인 "제일 비싼 포지션을
다음 순위로 교체" 알고리즘은 **후보를 실제로 바꾸는** 동작인데, 우리는 아래처럼 경고만 하기로
했으므로 실행할 대상이 없다. 후보 교체까지 하기로 결정하면 그때 ㉯를 다시 검토한다.

**G3는 후보를 탈락시키지 않는다.** 메모 §4 5단계가 "그래도 안 맞으면 예산 안내 경고 노출하고
진행(P09 저품질 경고와 같은 패턴)"이라 배제가 아니고, [3] 조건점수의 단가 15점도 초과율만큼
감점만 하기로 했다. 여기서 배제하면 **Stage B 폐기 사유(단가로 배제하면 사전검수 후보수와
어긋남)가 그대로 되살아난다.** `guardPassed=true`로 통과시키되 `guardReason`에 사유를 남긴다.

→ 결과적으로 **가드에서 실제로 후보를 거르는 건 G4뿐이고, G3는 기록·경고용**이다.

**U4 개정 (2026-08-12) — 예산 경고를 화면에 띄우기로 했다.**

원래는 "화면에 안 띄우고 `guardReason` 기록만"이었으나, 사용자가 프론트에 전달하기로 하면서
확정했다. `lowScoreWarned`와 **완전히 같은 패턴**이다 — 회차 단위 boolean 하나, 같은 자리.

| 항목 | 값 |
|---|---|
| API 필드 | `CandidateListResponse.budgetWarned` (boolean) |
| 켜는 조건 | 그 회차에서 G3가 사유를 냈을 때 |
| 화면 문구 | `추천된 후보들의 희망 단가 합계가 남은 예산을 넘습니다. 협상에서 조정이 필요할 수 있어요.` |

**"예산 초과"라고 단정하지 않는다.** 협상에서 조정될 수 있고 이 경고로 후보가 빠지지도 않는다.
"이 사람들 뽑지 마세요"가 아니라 **"협상이 필요합니다"**로 읽혀야 한다.

`guardReason`(개발자용, 숫자 포함)은 그대로 두고 화면에는 안 내보낸다 — 상한 계산식이 그대로
노출되면 클라이언트가 역산해서 다른 후보의 희망 단가를 추정할 수 있다.

**아직 구현 안 됨.** 작업 목록은 `.ai/HANDOFF.md` F3 참고. 프론트 문서에는 "서버 준비 전"임을
명시해뒀다.

**직무·스킬 재검증은 뺀다(팀 확정).** 하드필터를 통과한 사람만 LLM에 가고 LLM은 그 풀 안에서만
고르므로, 가드에 도착한 후보는 이미 직무·스킬을 통과한 사람이다. 같은 걸 또 봐도 아무도 안 걸린다.

⚠️ **명세 R02.3이 "가드 AI 로 마지막 검증 (직무, 스킬 검증)"이라고 적혀 있어 글자상 어긋난다.**
설명은 이렇게 한다: **"직무·스킬은 1차 필터에서 보장하고, 가드는 예산 조합과 LLM 응답 이상을
막는다."** 발표에서 질문이 나오면 이 문구를 쓴다.

~~⚠️ G3에 미해결 구조 문제가 있다~~ — **2026-08-12 해소.** 문제는 "재추천 때 이미 계약된 인원의
단가를 세지 않아, 자리가 찰수록 경고가 무의미해진다"였고, 위 **G3 세부**의 "이미 쓴 것" 계산으로
해결했다. (원래 이 줄이 가리키던 "미확정" 절은 문서에 존재한 적이 없다 — 끊긴 참조였다.)

### [6] 후보 노출 이후 — 변경 없음

노출 → 클라이언트 선택 → 요청 발송 → 수락/거절/만료 → 협상 → 계약. 기존 그대로.

### 착수 전 결정 — 전부 확정됨 (2026-08-11)

| # | 항목 | 결정 |
|---|---|---|
| U1 | G3 예산 검증 범위 | **포지션 단위 조합 합계.** `Σ(노출 후보 월단가) ≤ budgetCap × 노출 인원 × 1.2`. 개인별 판정은 [3]과 중복이라 폐기 → [5] |
| U2 | 프로젝트명 임베딩 포함 | **포함** → [2] |
| U3 | 유사도 정규화 | **순위 기반(PERCENT_RANK), 상수 불필요.** 하드필터 통과자 전원 대상 → [2] |
| U4 | 예산 경고 화면 노출 | **안 띄운다.** 서버에 `guardReason`으로 기록만. 협상 단계가 이미 `budgetCap`으로 거르고, 적합도 저하 배너가 이미 있어 배너 두 개면 화면이 복잡하다. DB 마이그레이션+API+프론트 작업이 붙는 것도 이유. **기록은 남으므로 나중에 빈도를 보고 추가할 수 있다** |
| U5 | 학력 복수일 때 학과 | **전부 넣는다** → [2] |
| U6 | 조건점수 채점식 | **확정** → [3] 표 |
| U7 | 포지션별 우대사항 `preferred_note` | **쓸 수 없음(3번 확인).** 우대사항은 프로젝트 단위(`extra_note`)로 통일되어 폐기됨 — 매핑·DTO·컬럼 전부 없음. `extra_note`만 쓴다 → [2] |

### 향후 개선 (이번 범위 밖, 기록만)

| 항목 | 내용 |
|---|---|
| **상주근무 시 주소 비교** | 컬럼은 다 있다 — 근무지 `project.work_location`(주석: '근무 장소(상주 시)'), 프리랜서 `resume.zip_code`/`address`/`address_detail` 및 `freelancer_profile.address`. **프리랜서 쪽은 이력서 주소 권장**(우편번호가 있어 정확하고, 후보가 되려면 이력서가 반드시 있다). **비교는 시/군/구까지만, `address_detail`(상세주소)은 개인정보라 LLM에 보내지 않는다.** 거리 계산은 하지 않는다. 지금은 파이썬 `PositionRequirement`/`FreelancerProfile`에 주소 필드 자체가 없어서 SQL·dataclass·프롬프트를 다 손봐야 한다 |
| `position_skill`에 요구 숙련도 컬럼 | 있으면 숙련도를 보너스가 아니라 조건으로 쓸 수 있다 (3번 파트) |

### 3번과 협의 — 2026-08-11 완료

| # | 내용 | 결과 |
|---|---|---|
| 1 | `preferred_note`를 매칭까지 내려주기 | ❌ **불가.** 우대사항이 프로젝트 단위(`extra_note`)로 통일되며 폐기됨 — 엔티티 매핑·등록/수정 DTO·공용 DB 컬럼 전부 없음. 되살리려면 등록 위저드 입력란과 프론트까지 열어야 해서 **기획 결정 사안**. `extra_note`만 쓰기로 |
| 2 | 프로젝트 임베딩 시점 = 착수금 결제 완료 | ✅ 동의. *"결제 전에는 후보 추천 자체가 시작되지 않으니(P27) 등록 시점에 임베딩을 만들 이유가 없다"* — **3번이 `docs/spec/policy.md` P03 문구를 코드에 맞춰 고쳐주기로 함** |
| 3 | `current_situation` 임베딩 사용 | ✅ 동의. 화면 노출과 무관, project 도메인 정책 변경 없음 |
| 4 | 사전검수(P02) 기준 유지 | ✅ 동의. *"안내보다 후보가 많이 나오는 건 클레임이 안 되고, 반대는 된다"* |

**3번이 제안한 "포지션 `skills`를 임베딩에 쓰라"는 받지 않았다** — 사유는 위 [2] 절 참고.
포지션별 우대사항이 매칭에 꼭 필요해지면 기획에 올려 되살릴지 정하기로 했다(현재는 불필요).

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

## 2026-08-12 확정 — 착수 전 결정 13건 (1~3군)

재설계를 코드로 옮기기 전에 "나중에 다시 고치지 않도록" 한 번에 정한 것들. **1~3군은 전부 확정,
4군(팀 판단 3건)만 남았다.**

### 1군 — 점수 체계

| # | 결정 | 근거 |
|---|---|---|
| 1 | **조건점수 배점 합 = 100** (30/20/20/10/8/6/6) | 원래 표는 합이 80인데 문서엔 70이라 적혀 있었다. 정규화하면 비율만 남으므로 100으로 맞춰 혼란을 원인부터 없앴다 |
| 2 | 후보 0명 시 **스킬 필터만 풀어 1회 재검색** | 명세 "조건 완화 후 재검색". 직군/직무는 못 푼다(오추천), 동의·상태는 사용자 의사다. 스킬 0개면 조건점수 30점이 0이라 어차피 순위 맨 뒤 |
| 3 | 최종 비중 **25:75** (원안 30:70) | 30:70 은 조건 42.9% 격차까지 유사도가 뒤집는다 — 스킬 1/5 + 경력 미달이 자기소개만으로 올라왔다 |

### 2군 — 가드(B4) 구조

| # | 결정 |
|---|---|
| 4 | G3 합계 대상 = **노출된 후보만**(대기 순번 제외) |
| 5 | G4 `reason` 누락 탈락 시 **다음 순위가 빈자리를 채운다** |
| 6 | G4 인원 초과 시 **상위 N만** 취한다(전량 실패 아님) |
| 7 | `matching_candidate.similarity`에 **실제 코사인 유사도를 저장**한다. 지금은 `0.0`이 박혀 있어 컬럼이 거짓말을 한다 — 파이썬 추천 응답에 필드 추가 → 자바가 저장 |
| 8 | `guardReason`은 숫자를 다 넣는다(화면 노출 없음, 로그·디버깅용) |
| 9 | G3에서 **이미 자리를 차지한 인원의 단가를 반영**한다. 협상 타결가 우선, 없으면 희망 단가 |

> **7번이 이번 재설계에서 유일하게 API 모양이 바뀌는 항목이다.** 파이썬이 먼저 나가야 한다
> (자바는 모르는 필드를 무시하므로 안전).

### 3군 — 배포·환경

| # | 결정 | 상태 |
|---|---|---|
| 10 | **Python PR 먼저 머지 → 확인 → Java.** `develop` 머지 = 즉시 배포라 머지 순서가 배포 순서다. 반대로 하면 스프링이 `budget_cap`을 보내는데 AI 서버가 모르는 구간이 생긴다 | 확정 |
| 11 | 스프링 배포 직후 **재색인 API 1회**(`POST /admin/embeddings/reindex`). 임베딩 텍스트 규칙이 바뀌어 옛 벡터가 전부 낡았다 | 확정 |
| 12 | pgvector 환경 준비 | **✅ 완료 (2026-08-12).** 로컬 = 윈도우 네이티브 PG18 + pgvector 0.8.6, 배포 = 이미 다 있었음(freelancer 1 / position 7행) |
| 13 | **CI에 pgvector 서비스 추가** | **✅ 완료 (2026-08-12).** `.github/workflows/ci.yml`에 `pgvector/pgvector:pg16` 서비스 + `AI_TEST_DB_URL` 주입. PR마다 실제 pgvector로 SQL이 돈다 |

### 4군 — 2026-08-12 정리

| # | 항목 | 결론 |
|---|---|---|
| 14 | 골드 등급 수수료 할인 | **없음(현행 유지).** 정책 P01 + 정산 `DepositFeePolicy`가 이미 같은 결론. 확인 대기 항목이 아니었다 |
| 15 | D3 프론트 렌더링 | **4번 파트 아님 → 목록에서 제외.** 매칭 API는 응답 모양이 안 바뀌고 프론트 전달 문서도 나가 있다 |
| 16 | E 전달 4건 | 아래 HANDOFF E 표. **지금 보낼 것** |

**수수료율 하드코딩 제거 (14번에서 파생, 2026-08-12).**

`BudgetCapCalculator`에 `10% / 8% / 다이아 -2%p`가 직접 박혀 있었다. 대조해보니 정산 도메인 값의
합과 정확히 같았다:

| | 착수금(`DepositFeePolicy`) | 성공보수(`SuccessFeePolicy`) | 합 = 매칭이 쓰던 값 |
|---|---|---|---|
| 1억 미만 | 3% | 7% | **10%** |
| 1억 이상 | 2% | 6% | **8%** |
| 다이아 | −1%p | −1%p | **−2%p** |
| 골드·실버 | 0 | 0 | **0** |

**우연히 맞고 있었을 뿐이라 두 정책 클래스를 직접 호출하도록 바꿨다.** 정산이 요율을 고치면
매칭만 옛 값으로 남는데, 그러면 budgetCap이 틀어져 **조건점수 단가 20점 · 가드 G3 · 협상 상한**이
한꺼번에 어긋나면서 **어디에서도 예외가 나지 않는다.** 도메인 경계를 넘는 import보다 단일 출처가
더 중요하다고 판단했다(두 정책 다 I/O 없는 순수 계산이라 결합 비용이 낮고, `ClientGradeResolver`가
이미 account 리포지토리를 직접 쓰는 선례도 있다).

> 성공보수의 기준 금액은 원래 **계약 총액**이지만 추천 시점엔 계약이 없어 프로젝트 예산으로 구간을
> 판정한다. 상한 계산용 추정값이라 이 근사는 허용된다(코드 주석에도 명시).

### 12번 상세 — pgvector 환경 (**완료**)

**결과 (2026-08-12)**

| | pgvector | 임베딩 테이블 | 데이터 |
|---|---|---|---|
| 로컬 (윈도우 네이티브 PostgreSQL 18) | **0.8.6** | ✅ | 0행 |
| 배포 | ✅ | ✅ | freelancer 1 / position 7 (2026-08-11 = **B1 이전, 재색인 대상**) |

**배포는 원래 다 있었다.** "배포에서도 추천이 안 되고 있을 것"이라던 추측은 틀렸다 —
포지션 임베딩이 7건 있어 비교 대상도 있었다. 로컬만 비어 있던 것이다.

**로컬에서 걸렸던 것 (같은 걸 다시 겪지 않게)**

- `postgres:16-alpine`에도 윈도우 설치본에도 pgvector가 **없다.** 소스 빌드가 필요하다
- `nmake`는 PowerShell에 없다. 관리자 cmd에서 `vcvars64.bat`을 먼저 `call` 해야 한다.
  `set "VAR=..."`도 cmd 문법이라 PowerShell에선 안 먹는다
- ⚠️ **도커 PG16과 네이티브 PG18이 둘 다 5432를 잡고 있었다.** 이러면 앱이 어디에 붙는지
  알 수 없다. 네이티브를 쓰면 도커 postgres는 끄고 Redis만 띄운다
- ivfflat 인덱스를 빈 테이블에 만들면 pgvector가 `low recall` 경고를 낸다 → B5 재색인 직후
  `REINDEX` 필요

### (아래는 발견 당시 기록)

**팀 확정(2026-08-12): 윈도우에 PostgreSQL을 직접 설치하고 pgvector를 소스에서 빌드한다.**
절차 원본은 `README.md` "2-1) pgvector 설치"에 있다(팀원이 볼 자리라 거기에 뒀다). 요약:

```bat
:: Build Tools에서 "C++를 사용한 데스크톱 개발" 설치 후,
:: 관리자 권한 x64 Native Tools Command Prompt 에서
set "PGROOT=C:\Program Files\PostgreSQL\18"
git clone https://github.com/pgvector/pgvector.git
cd pgvector
nmake /F Makefile.win
nmake /F Makefile.win install
```

그다음 `pairing` DB에서 `CREATE EXTENSION vector;` → `Pairing-python/db/init/10-create-ai-schema.sql` 실행.

**왜 이게 필요했나 (2026-08-12 발견)**

임베딩 테이블을 만드는 SQL은 `Pairing-python/db/init/10-create-ai-schema.sql` 하나뿐인데,
**어느 `docker-compose.yml`에도 마운트돼 있지 않다.** 파이썬은 `create_all`을 안 쓰기로 했으므로
(README 명시) **아무도 이 테이블을 만들지 않는다.** 게다가 백엔드 compose 의 postgres 이미지가
`postgres:16-alpine`이라 **pgvector 확장 파일 자체가 없어** `CREATE EXTENSION vector`가 실패한다.

- 로컬 확인 결과(2026-08-12): `vector` 확장 없음, `freelancer_embedding`/`position_embedding` 없음,
  `freelancer_profile.matching_paused` 없음(스프링이 그 머지 이후 안 뜬 상태 — `ddl-auto: update`라 뜨면 생긴다)
- 도커를 계속 쓰는 사람은 이미지를 `pgvector/pgvector:pg16`으로 바꾸면 빌드 없이 된다
  (같은 PG16이라 기존 볼륨 그대로 붙는다). **네이티브 PostgreSQL과는 5432 포트가 충돌하므로
  둘 중 하나만 쓴다** — 네이티브로 가면 도커는 `docker compose up -d redis`로 Redis만 띄운다
- ⚠️ **배포 DB도 같은 이유로 누군가 수동으로 넣었어야 한다.** 안 넣었으면 배포 환경에서도 추천이
  첫 쿼리에서 죽는다. C1을 한 번도 안 돌려봐서 **아직 아무도 모르는 상태일 수 있다.**
  확인: `SELECT extname FROM pg_extension WHERE extname='vector';`

## 아직 팀 확인 대기 중인 것

| 항목 | 상태 |
|---|---|
| ~~적합도 점수 스케일이 진짜 0~100인지~~ | **2026-08-10 확정: 0~100.** 회의를 기다릴 필요가 없었다 — 스키마 주석(`matching_candidate.base_score` "LLM 원점수(등급 가중치 적용 전, 0~100)")과 코드(`RankedFreelancer` 0~100, `MatchingRoundCreationService.LOW_SCORE_THRESHOLD = 50.0`)에 이미 0~100으로 박혀 있었다. **실제로는 Pairing-python 쪽이 이 계약을 안 지키고 있었다**: 프롬프트에 범위를 안 알려줘서 LLM이 10점 만점으로 답했고(`score=9.5`), 그대로 저장돼 모든 후보가 50점 미만 = "적합도 낮음"으로 찍히는 상태였다(에러가 안 나서 안 보이던 버그). 파이썬에 프롬프트 명시 + 응답 스키마 `minimum/maximum` + `ge/le` + 스케일 검증(최고점 10 이하면 거부)까지 넣어 방어 완료. |
| ~~골드 등급 수수료 할인 여부~~ | **2026-08-12 해소: 골드는 할인 없음.** 확인이 필요한 항목이 아니었다 — 정책 P01의 골드 혜택은 비어 있는 게 아니라 3개(등록 2개/높은 등급 매칭 확률/매칭 1명)가 나열돼 있고 거기에 수수료 인하만 없다. 정산 도메인도 같은 결론으로 이미 구현돼 있다(`DepositFeePolicy.gradeDiscount`: "실버·골드는 할인이 없다"). **`BudgetCapCalculator`가 요율을 직접 박아두던 것도 이때 제거하고 정산 정책을 참조하도록 바꿨다** — 아래 표 참고 |
| 프로젝트 등록에 인원별 예산 배분 필드 존재 여부 | 없으면 지금처럼 순예산 전체 조합으로만 판단 |
| ~~`currentSituation`/`mainTask`를 프리랜서의 "받은 매칭 요청" 카드에 노출할지~~ | **2026-08-09 3번 답변 완료·구현 완료.** `mainTask`만 노출, 카드가 아니라 **요청 상세**(`GET /requests/{requestId}`)에서만. `currentSituation`은 노출 안 함(배경 설명이라 길어지기만 함). 아래 "2026-08-09 갱신 — 매칭 요청 상세 mainTask 노출" 참고 |
| ~~budgetCap 계산 시 기간이 WEEK 단위면 몇 주를 1개월로 칠지~~ | **2026-08-09 확정: 4주=1개월.** `BudgetCapCalculator` 코드 정리 완료 |

## 프론트 공유 문서 (레포 밖)

`C:\Users\user\Desktop\AI매칭_API_화면매핑_최신본.md` — 사용자가 관리하는 프론트 전달용 API-화면 매핑 문서(이 레포에는 없음). 2026-08-09에 실제 코드와 대조 검증 완료, 후속 작업 없음:

- **실제 오류 1건 발견·반영 완료**: 재추천 API 에러표의 `MT_009`(추천 후보 없음)는 코드에서 실제로 던지는 곳이 없었음(enum 정의만 있고 미사용, dead code) — 사용자가 에러표에서 제거하고 "후보 없으면 201+빈 배열" 문구를 추가함.
- **참고 안내 반영 완료**: `RerecommendRequest.quantity`는 PAID일 때 필수인데 코드에 `@NotNull` 검증이 없어서 빠뜨리면 500(NPE) 위험 — 사용자가 경고 문구와 "아직 확정/수정 필요" 표 행을 추가함.
- 나머지(공통 래퍼, PageResponse, 엔드포인트별 요청/응답 필드, enum 값, 에러코드 전체 목록, meta API 경로)는 전부 코드와 일치 확인함. 백엔드 쪽 `RerecommendRequest.quantity` `@NotNull` 검증 자체는 아직 코드로 안 고침(문서에만 위험 안내 — 실제 수정은 백로그, 아래 참고).
