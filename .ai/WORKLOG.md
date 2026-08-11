# 작업 기록 — AI매칭(4번 파트)

시간순 기록이라 중간 날짜의 "다음 할 일"/"미해결" 문장은 그 시점 기준이고 이후에 해결됐을 수 있다.
**현재 상태는 이 파일 맨 아래(최신 날짜) 항목과 `.ai/STATE.md`/`HANDOFF.md`를 기준으로 판단할 것.**

## 2026-08-07

- `Pairing-python` 레포 코드 리뷰 완료. 초기에 수업 자료 기준으로 세운 가정(로컬 임베딩, ChromaDB) 정정: 실제로는 Gemini `text-embedding-004` + pgvector.
- Pairing-backend `matching`/`meta` 도메인 스켈레톤 확인. API 계약(엔드포인트·DTO)은 확정 상태.
- `CandidateResponse`, `MatchingRequestResponse`에서 `fitScore` 필드 제거 (점수 비노출 결정 반영). `MatchingController` 샘플 데이터, `docs/api-dto.csv`, `.ai/API.md` 동기화. 컴파일 확인. **커밋/푸시 아직 안 함.**
- 협상(5번) 담당자와 매칭→협상 핸드오프 계약 확정: 스냅샷 필드(4개 diff 대상 + 2개 보조), PERIOD 조건부 제외, budgetCap 1단계 단순화, 동기 호출 방식.
- `api명세최신.xlsx` 검토: 매칭 도메인(51~59행) 엔드포인트 자체는 실제 컨트롤러와 일치. 58행(매칭 요청 수락) 선행 작업에 `12. Negotiation` 추가 필요 — 아직 엑셀 파일 자체는 수정 안 함(사용자가 직접 반영하기로 함).
- 3일 스프린트 일정 수립. 상세는 `.ai/HANDOFF.md`.
- `.ai/STATE.md`, `.ai/HANDOFF.md` 신규 작성.
- 등급 가중치(grade_weight) 확정: 클라이언트 등급 실버0%/골드1%/다이아2%. 프리랜서 등급 타이브레이커(동점일 때만)는 설계만 하고 구현은 3일차 랭킹 서비스로 미룸. 상세는 `.ai/STATE.md` "확정된 설계 결정 4-1".
- **1일차~2일차 코드 구현 (이번 세션에서 실제로 다 짬, 컴파일·테스트 83개 통과 확인)**
  - `matching` 도메인 뼈대: enum 4종, `MatchingErrorCode`, `MatchingRound`/`MatchingCandidate`/`MatchingRequest`/`MatchingSnapshot` 도메인모델 + JPA엔티티 + 리포지토리 전부.
  - `matching_candidate`에 `is_rejected` 컬럼 추가(스키마+도메인+JPA) — 클라이언트가 요청 발송 전에 후보를 거절(비활성 표시)하는 상태를 저장할 자리가 원래 스키마에 없었음.
  - **버그 수정**: `findFreelancerIdsByPositionId` → `findFreelancerIdsByProjectId`로 스코프 변경. R02 예외조건 5("동일한 프로젝트 안에서는 이미 추천된 프리랜서가 재추천 결과에 다시 노출되지 않는다")가 포지션이 아니라 프로젝트 단위 dedup이라 원래 설계가 좁게 잘못 잡혀 있었음.
  - `PythonMatchingAdapter`(Resilience4j 서킷브레이커 + Bucket4j 레이트리밋 + 헤더 전파) 완성, `application.yaml`에 `ai.pairing-python.*` 설정 추가.
  - **임시 스텁 포트 3개** — freelancer/project/negotiation 도메인이 전부 아직 실제 영속 계층이 없어서(enum만 존재), matching 자체 로직은 실제 DB로 완성하고 남의 도메인 데이터는 포트+스텁 어댑터로 자리만 만들어 진행:
    - `FreelancerDirectoryPort` (`infrastructure.directory.StubFreelancerDirectoryAdapter`) — accountId→freelancerId 변환, 카드 요약, `FreelancerConditionResponse` 조회.
    - `ProjectDirectoryPort` (`infrastructure.directory.StubProjectDirectoryAdapter`) — 소유권 검증, 헤드카운트, 프로젝트/포지션 요약, 클라이언트 accountId 조회.
    - `NegotiationPort` (`infrastructure.negotiation.StubNegotiationAdapter`) — 협상 생성(실패 안 시키고 음수 placeholder ID 반환, 로그 경고), 협상 요약 조회(항상 empty).
    - 세 도메인이 실제로 구현되면 어댑터 클래스 하나씩만 교체하면 됨. `application/port/out/*Port.java` Javadoc에 교체 지점 명시함.
  - **실제 구현(진짜 DB, 스텁 아님)**: `GET .../candidates`, `POST .../candidates/{id}/rejection`, `POST .../requests`(발송), `GET .../requests`(보낸 목록), `GET .../requests/received`, `GET .../requests/{id}`(상세), `POST .../requests/{id}/acceptance`(수락+스냅샷+협상연동), `POST .../requests/{id}/rejection`, `POST .../positions/{id}/rerecommendations`(무료/유료 분기+Stage C~E 호출+가드+저장) — **매칭 도메인 9개 엔드포인트 전부 스켈레톤 탈출.**
  - `budgetCap` 계산 실구현(수수료율 구간×클라이언트등급, `ClientProfileRepository`는 실제 리포지토리라 여기는 스텁 아님). `grade_weight`도 같은 `ClientGradeResolver`로 실제 적용.
  - Stage F 가드는 항상 통과 처리(placeholder) — 규칙 기반 실제 재검증 로직은 3일차.
  - fitReason은 DB에 `"|"`로 이어붙인 문자열로 저장, API 응답에서 다시 나눠 태그 리스트로 변환(`CandidateResponseAssembler`). Pairing-python의 LLM 응답이 이 구분자로 합쳐 내려주도록 3일차에 맞춰야 함.
  - **버그 수정**: 신규 `@Value("${ai.pairing-python.*}")` 프로퍼티를 `src/test/resources/application.yaml`에 안 넣어서 `PairingApplicationTests` 컨텍스트 로딩이 깨졌던 것 발견·수정.
  - 협상(5번)이 보낸 `FreelancerConditionSnapshot` 초안 검토 → 확정 계약(6개 필드) 중 2개(`minAcceptAmount`, PERIOD) 빠진 것 확인, 사용자에게 전달할 답장 문구 작성(전송은 사용자가 직접).

## 2026-08-08

- **PR #34 오픈** (`feature/4-matching` → `develop`, 1~2일차 매칭 도메인 구현 전체).
- **CI 실패 원인 분석·수정**: `global/ratelimit/RedisRateLimitConfig.lettuceProxyManager()`가 빈 생성 시점에 `redisClient.connect(...)`로 즉시 Redis에 연결하고 있었음. `global` 패키지라 모든 Spring 컨텍스트 로딩에 걸려서, Redis 컨테이너가 없는 CI에서는 애플리케이션 자체가 기동 실패(테스트 70개 연쇄 실패). `@Bean`에 `@Lazy` + 유일한 소비자인 `RateLimitProvider`의 생성자 주입 지점에도 `@Lazy` 명시(빈 쪽 `@Lazy`만으로는 다른 즉시 생성 싱글톤의 생성자 주입까지 안 미뤄짐). 로컬에서 Redis 컨테이너를 일부러 끄고 재현·검증함. CI 통과 확인 후 push, 팀원이 develop에 merge.
- **버그 수정(로컬에서 발견)**: `matching_candidate`(similarity/base_score/grade_weight/fit_score)와 `matching_round`(cost_amount) 컬럼이 DB엔 `NUMERIC`인데 JPA 엔티티는 `Double`/`long`만 쓰고 있어서, `ddl-auto: validate`/`update` 모두에서 스키마 불일치로 걸림. 엔티티에 `columnDefinition` 명시해서 해결.
- **develop을 두 차례 재동기화**: 팀원들 도메인이 대거 실구현으로 전환됨을 확인.
  - 2번(프리랜서): 조건/이력서 실구현, 후보 카드용 `FreelancerCandidateSummaryUseCase` 신규 노출, `FreelancerConditionResponse.periodValue` `Integer`(nullable)로 수정 완료.
  - 3번(프로젝트): 등록/상세조회 실구현, 매칭 연동 전용 `ProjectQueryUseCase` 신규 추가(요청한 5개 메서드 전부 제공).
  - 1번(계정): `AccountQueryUseCase.findClientProfileById`/`findFreelancerProfileById` 신규 노출(프로필 id로 역조회).
  - 5번(협상): `NegotiationCommandUseCase`(생성), `NegotiationProgressUseCase`(진행조회) 신규 노출. 관리자 조회, 채팅 연동, 로그 무결성 등도 실구현.
  - project 도메인 연동으로 chat/file 도메인도 실구현 전환됨.
- **팀 커뮤니케이션 결과 (모두 반영 완료)**:
  - `minAcceptAmount`는 payUnit 환산 없는 **절대 월단가**로 최종 확정(freelancer/negotiation 양쪽 코드에 동일하게 명시돼있음을 재확인).
  - 협상 진행조회 계약 확정: `NegotiationProgressUseCase.findProgressByRequestId(requestId)` → `{negotiationId, currentRound, maxRound, newProposalCount}`.
  - `newProposalCount` 정의를 "현재 라운드 제안 수"에서 **"클라가 마지막으로 읽은 시점 이후 온 새 제안 수"**로 정정 요청 → 5번이 반영 완료(읽음 트리거는 채팅과 동일한 명시적 엔드포인트 `POST /negotiations/{id}/read` 패턴). 해당 커밋은 `feature/negotiation-unread-proposals` 브랜치에 있고 아직 develop에 merge 전 — merge되면 우리 쪽 코드 변경 없이 자동 반영됨.
  - 협상 결렬/타결을 매칭에 통보하는 신규 인바운드 포트 시그니처를 5번에게 전달: `MatchingNegotiationOutcomeUseCase.markNegotiationAgreed(requestId)` / `.markNegotiationFailed(requestId)`. **양쪽 다 아직 구현 전** — 다음에 할 일.
- **스텁 어댑터 3개 교체** (`feature/matching-directory-adapters` 브랜치, `develop`에서 새로 분기):
  - `ProjectDirectoryPort` — 완전 교체(`ProjectDirectoryAdapter`). `findPositionSummary`가 positionId만으로 projectId를 역조회할 방법이 project 도메인에 없어서, **포트 시그니처를 `findPositionSummary(projectId, positionId)`로 변경**하고 매칭이 자신의 `MatchingRound`(project_id 보유)에서 projectId를 얻어 넘기도록 호출부 3곳(`MatchingRequestResponseAssembler`, `MatchingRequestService.sendOneRequest`/`accept`, `MatchingRerecommendService.rerecommend`) 수정. 회사명/업종/직원수는 `AccountQueryUseCase.findClientProfileById`로 별도 조회.
  - `NegotiationPort` — 완전 교체(`NegotiationAdapter`). `NegotiationCommandUseCase`/`NegotiationProgressUseCase` 위임 호출.
  - `FreelancerDirectoryPort` — 부분 교체(`FreelancerDirectoryAdapter`). `findCardSummary`만 실구현, `resolveFreelancerId`/`findCondition`은 account_id↔freelancer_profile.id 양방향 조회가 아직 없어(2번이 1번에게 승인 요청, 대기 중) 스텁 유지(로그로 명시).
  - `./gradlew test` 전체 통과 확인 후 커밋·push. **이슈/PR 아직 안 만듦.**
- **팀에 결정 요청 (미확정)**: 3번이 덤으로 준 `currentSituation`(프로젝트 현재 상황)/`mainTask`(주요 담당 업무)를 프리랜서의 "받은 매칭 요청" 카드에 노출할지 — 프리랜서는 수락 전에는 프로젝트 상세를 볼 권한이 없어서 지금은 이 정보를 어디서도 못 본다. 요구사항 문서(프로젝트 등록 화면, R30 부근)엔 실제 입력 항목으로 존재함. 개발량은 적음(필드 2개 추가). 화면 기획 확인 필요해서 팀에 질문 전달함, 답 대기 중.

## 2026-08-09

- **develop 재동기화**: 3번의 매칭 연동 포트 확장(`findPositionSummaries`/`findProjectPositionSummary(positionId)`/`findStatus`, `ProjectPositionSummary.totalHeadcount`, `RecruitingStartedEvent` 발행), 정산(착수금 결제) 도메인, 리뷰 도메인, 등급(Grade) 도메인, 알림(Notification) 도메인이 전부 develop에 merge됨.
- **H2 통합테스트 신규 작성**: `MatchingIntegrationTest`(9개 — 후보조회/거절/요청발송/보낸목록/받은목록/수락/거절/재추천)와 `RecruitingStartedEventListenerTest`(2개 — 이벤트 처리·멱등성). Swagger 수동 클릭 대신 이걸로 검증하기로 함(H2 = 실 DB 대신 쓰는 인메모리 DB, 서버 안 띄우고 코드로 API 호출·응답 검증).
- **버그 발견·수정 1**: 테스트 작성 중 `MatchingRoundCreationService.persistCandidates`가 `applyGuard()`를 `applyGradeWeight()`보다 먼저 호출하고 있어서, `MatchingCandidate`의 상태머신(EMBEDDING→LLM_FINAL→GUARD) 규칙상 실제 추천 라운드 생성 때마다 무조건 예외가 나는 상태였음(테스트가 아니었으면 배포 후 처음 라운드 생성 시점에야 발견됐을 버그). 호출 순서를 `applyLlmResult→applyGradeWeight→applyGuard`로 정정.
- **3번의 버그 리포트 확인·수정 (B①②는 실제 버그, B③은 우리 쪽엔 문제 없음)**:
  - budgetCap이 포지션 인원(`position.headcount()`)으로 나뉘던 것 → 프로젝트 전체 인원(`totalHeadcount`)으로 나누게 수정.
  - budgetAmount(계약기간 전체 총액)를 개월 수로 안 나누고 월급과 비교하던 단위 불일치 → `periodValue`/`periodUnit`을 매칭 쪽 `ProjectPositionSummary`에 raw 필드로 추가하고 개월 수로 나누게 수정. WEEK 단위 주→개월 환산 규칙은 아직 미정이라 4주=1개월 임시값 사용, 3번에게 질문 전달.
  - `findClientAccountId`가 client_profile.id를 그대로 쓰는 것 아니냐는 리포트는 확인 결과 우리 어댑터가 이미 account 도메인 조회로 진짜 accountId로 변환하고 있어서 문제 없음 — 3번에게 회신.
- **결제→매칭 이벤트 리스너 신규 구현** (HANDOFF 3일차 항목 4, 7 완료): `RecruitingStartedEvent(projectId)`를 받아 포지션별로 스냅샷 동결(`MatchingSnapshot` PROJECT/POSITION 타입 최초 사용) → 임베딩 upsert(`MatchingPort.upsertPositionEmbedding` 신규, Pairing-python `PUT /embeddings/positions` 연동) → `MatchingRoundCreationService.createRound(INITIAL, ...)` 순서로 처리. 멱등 가드, 포지션 단위 예외 격리 적용.
  - **버그 발견·수정 2 (재발 방지 필요)**: 처음엔 리스너 한 클래스 안에서 `@TransactionalEventListener`가 `this.method()`로 `@Transactional(REQUIRES_NEW)` 메서드를 자체 호출(self-invocation)하도록 짰는데, 스프링 프록시를 안 거쳐서 트랜잭션이 조용히 무시되고 라운드 저장이 안 되는 걸 테스트로 재현·발견함. `RecruitingStartedEventListener`(이벤트 수신+포지션 목록 순회)와 `RecruitingStartedPositionHandler`(포지션 1건 처리, 실제 REQUIRES_NEW 적용)로 빈을 분리해서 해결.
- `ProjectDirectoryPort.findPositionIds(projectId)` 신규 추가 — project 도메인의 `findPositionSummaries`가 positionId를 안 내려줘서(포지션 여러 개일 때 어떤 요약이 어떤 포지션 건지 구분 불가), 대신 `ProjectQueryUseCase.getById(projectId).getPositions()`로 직접 포지션 ID 목록을 얻어오게 함. project 쪽에 `positionId` 필드 추가를 요청하면 나중에 더 가벼운 방식으로 교체 가능.
- 3번에게 회신 정리해서 전달함(B①②③ 확인 결과 + C 구현 완료 알림 + WEEK 환산 규칙 질문).
- **이슈·PR 생성**: 이 컴퓨터에 GitHub CLI(`gh`)가 안 깔려 있고 git 로그인 정보로는 API 쓰기 권한이 없어서(읽기 GET은 됨, 이슈/PR 생성 같은 POST는 401) AI가 직접 만들지 못함. 이슈·PR 제목/본문을 `docs/ai/git-issue-pr-guide.md` 템플릿대로 작성해서 사용자에게 전달했고, 사용자가 직접 GitHub에서 생성함 → **PR #51** (`feature/matching-directory-adapters` → `develop`), 제목 "[Feat] 매칭 스텁 어댑터 교체 + budgetCap 버그 수정 + 결제 완료 이벤트 리스너".
- **develop 재동기화(2차)**: 리뷰 도메인 완성 후속 연결 4건 merge(`ClientQueryService`, `FreelancerCandidateSummaryResult`/`Service`, `ResumeService`, `SiteReviewAdminService`) — 매칭이 쓰는 `FreelancerCandidateSummaryResult`는 필드 모양 그대로라 우리 쪽 코드 변경 불필요, 테스트 재확인만 함.
- **PR #51 CI 실패 진단·수정**: push 직후 CI(`ubuntu-latest`, Redis 없음)에서 `MatchingIntegrationTest`의 재추천 테스트만 500으로 실패. 로컬(Redis 켜져 있음)에서는 항상 통과해서 재현이 안 됨 → `gh` CLI 없이 **GitHub Actions REST API를 git 자격증명(자격증명 관리자에 이미 저장된 OAuth 토큰, `git credential fill`로 재사용)으로 직접 호출**해서 실패한 job의 로그와 `test-report` 아티팩트(HTML)를 내려받아 원인 특정.
  - **원인**: `/api/v1/matchings/positions/{id}/rerecommendations`에만 걸려있는 `MatchingRateLimitInterceptor`가 요청마다 `RateLimitProvider`(Redis 기반 `lettuceProxyManager`, `@Lazy`라 첫 실제 사용 시점에 연결)를 타는데, Redis가 없는 CI에서는 그 시점에 연결 실패로 500이 남. 로컬은 Redis 컨테이너가 떠 있어서 우연히 통과했었고, 재추천 엔드포인트를 실제로 호출하는 테스트가 이번에 처음 생기면서(`MatchingIntegrationTest`) 처음 드러남 — 8/8~8/9 세션 초반에 "고쳤다"고 기록한 Redis 이슈와는 결이 다름(그건 컨텍스트 기동 자체가 깨지던 것, 이번 건 기동은 되고 실제 호출 시점에만 터지는 것).
  - **수정**: `MatchingIntegrationTest`에 `RateLimitProvider`를 `@MockitoBean`으로 교체하고 항상 허용하는 로컬 Bucket4j 버킷을 반환하도록 스텁. 로컬 `./gradlew clean build` 전체 통과 확인 후 커밋·push.
- **프론트 전달용 문서 검토 (완료)**: 사용자가 데스크탑에 만든 `AI매칭_API_화면매핑_최신본.md`(레포 밖 파일, 프론트 공유용)를 실제 코드와 대조 검증. 실제 오류 1건 발견: 재추천 API 에러표에 `MT_009`(추천 후보 없음)를 넣었는데, 코드상 `CANDIDATE_POOL_EMPTY`는 enum에 정의만 있고 실제로 던지는 곳이 없음 — 후보가 없으면 에러가 아니라 `candidates: []`로 201 성공 응답이 내려감(라운드는 EXHAUSTED). 추가로 `RerecommendRequest.quantity`가 PAID일 때 `@NotNull` 검증이 없어서 누락 시 500(NPE) 위험이 있다는 점도 안내함. **사용자가 문서에 바로 반영함**(재추천 에러표에서 MT_009 제거, "후보 없으면 201+빈 배열" 문구 추가, quantity PAID 필수·검증 없음 경고 추가, "아직 확정/수정 필요" 표에 행 추가) → 재검토해서 전부 정확하게 반영된 것 확인 완료. 이 문서 관련 후속 작업 없음.
- **PR #51 merge 완료** — 사용자가 CI 그린 확인 후 develop에 merge.
- **`RerecommendRequest.quantity` NPE 수정** (HANDOFF 20번, `fix/rerecommend-quantity-validation` 브랜치, PR #53 오픈) — `MatchingErrorCode.QUANTITY_REQUIRED`(MT_012) 추가, PAID+quantity null이면 400으로 응답하도록 수정. 이슈 #52도 같이 생성.
- **budgetCap WEEK→개월 환산 4주=1개월 확정** (`docs/budgetcap-week-conversion-confirmed` 브랜치) — 사용자 확인. `negotiation` 도메인의 `NegotiationConditionCalculator`도 이미 같은 값을 쓰고 있어서 두 도메인 계산 기준이 원래부터 일치했음을 확인. `BudgetCapCalculator`의 임시값 표기 제거.
- **`MatchingNegotiationOutcomeUseCase` 구현** (HANDOFF 14번, `feature/matching-negotiation-outcome` 브랜치) — 매칭 쪽 인바운드 포트 신규 작성, `MatchingRequestService`가 구현체. `markNegotiationAgreed`는 신규 도메인 메서드 `MatchingRequest.agreeNegotiation()`(기존 `failNegotiation()`과 대칭, NEGOTIATING일 때만 허용)을 거쳐 `CONTRACT_PENDING`으로, `markNegotiationFailed`는 기존 `failNegotiation()`으로 `NEGOTIATION_FAILED`로 전환. 단위 테스트 4개(`MatchingNegotiationOutcomeServiceTest`) 작성, `./gradlew build` 통과 확인. **negotiation(5번) 쪽에 남은 일**: `NegotiationLoopService.agree()`/`.fail()`이 이 포트를 호출하도록 이어붙이는 작업 — negotiation 도메인 코드라 매칭이 대신 하지 않음, 5번에게 전달 필요.
- **참고**: 위 3개 브랜치(`fix/rerecommend-quantity-validation`, `docs/budgetcap-week-conversion-confirmed`, `feature/matching-negotiation-outcome`)가 전부 develop에서 독립적으로 분기돼 아직 서로 merge 안 된 상태라, `.ai/*.md` 문서 쪽에서 merge 시 충돌이 날 수 있음(코드 충돌은 아님, 서로 다른 파일/메서드 건드림). 문서 충돌은 내용 합치면 되는 수준.
- **위 3개 브랜치(PR #53 포함) 전부 develop에 merge 완료.** 5번도 같은 날 `NegotiationLoopService`에 배선을 마쳐 협상↔매칭 양방향 연동이 다 이어짐 — HANDOFF 14번 완전히 종료.
- **코드 리뷰로 발견한 추가 버그 수정** (재추천 `type`이 `INITIAL`도 그대로 받던 문제, HANDOFF 21번) — 위 PR #53과 같은 브랜치에 포함, `MatchingErrorCode.INVALID_RERECOMMEND_TYPE`(MT_013) 추가.
- **매칭 요청 카드 라이브 조회 버그(R32) 수정** (HANDOFF 23번, `fix/matching-request-card-snapshot-read` 브랜치) — 3번이 프로젝트 수정 API 구현 중 발견해 질문, 코드 확인 후 답변, 3번 확인받아 구현까지 진행. `MatchingRequestResponseAssembler`가 `MatchingSnapshot`(PROJECT/POSITION)을 읽도록 교체, `companyProfile`만 신규 포트(`findCompanyProfile`)로 계속 라이브 조회. `MatchingIntegrationTest`에 스냅샷 시딩 추가(`seedMatchingSnapshots`). `./gradlew clean build` 통과, push 완료 — **PR #59로 develop에 merge됨**.
- **모집 시작 후 프로젝트 수정 → 포지션 임베딩 재생성** (HANDOFF 24번, `feature/project-updated-embedding-refresh` 브랜치) — 3번의 `ProjectUpdatedEvent`(PR #57에서 신규 발행, 매칭이 안 받고 있던 걸 develop 재동기화 중 발견) 처리하는 `ProjectUpdatedEventListener` 신규. 위 R32 스냅샷 수정과는 반대 방향(요청 카드는 고정, 임베딩은 최신화)이라 안 겹침. `PositionEmbeddingTextBuilder` 공유 클래스로 추출. 테스트 3개, `./gradlew clean build` 통과, push 완료 — **PR #61로 develop에 merge됨**.
- **매칭↔프로젝트 상태 연동 3건** (HANDOFF 25번, `feature/matching-project-stage-sync` 브랜치) — 3번이 PR #59 코드 리뷰 중 발견: 매칭이 `ProjectStatus`를 전혀 참조 안 해서 모집 종료·취소된 프로젝트에도 재추천/요청 발송이 되고 있었음. 3번이 이미 project 쪽에 만들어둔 `findStatus`/`startNegotiating`/`syncStage`를 매칭이 그냥 안 부르고 있었던 것(호출 0건 확인). 3가지 다 연결: ①요청 발송·재추천 전 `assertRecruiting()`(CANCELED/CLOSED만 차단) ②`accept()` 끝에 `startNegotiating` ③`markNegotiationFailed`/`reject`에 `syncStage`(카운트 기준 새 리포지토리 메서드 `existsByProjectIdAndStatusIn` 추가, 기존 `existsActiveByProjectId`는 기준이 달라 재사용 안 함). 회귀 테스트 추가하면서 `MatchingNegotiationOutcomeServiceTest`가 실존하지 않는 더미 projectId(1L)를 쓰고 있던 것도 발견해 실제 프로젝트 row 시딩으로 수정. `./gradlew clean build` 통과, push 완료. 상세는 `.ai/STATE.md` 2026-08-09 갱신 섹션.
- **3일차 배치 — HANDOFF 9/12/22번 완료, 11번 보류 결정**:
  - Pairing-python `_build_prompt` 실구현(HANDOFF 9번) — `feature/matching-prompt-real-implementation` 브랜치, develop 대상 PR 오픈.
  - 임베딩 텍스트 `detailScope`/`extraNote` 추가(HANDOFF 22번) — `feature/matching-embedding-detail-fields` 브랜치, merge 완료.
  - 프리랜서 등급 타이브레이커(HANDOFF 12번 하위) — `feature/matching-freelancer-grade-tiebreaker` 브랜치, merge 완료. `RecruitingStartedEventListenerTest`가 가짜 freelancerId(999_001L)를 쓰던 것도 발견해 실제 시딩으로 수정.
  - budgetCap Stage F(HANDOFF 11번) 착수 보류 — findCondition 스텁 + 배분 알고리즘 규칙 미확정, 2번/3번에게 확인 요청.
- **`FreelancerDirectoryAdapter` 완전 교체** — 2번이 "account 도메인 승인은 이미 끝났고 필요한 포트(`AccountQueryUseCase.findFreelancerProfileById/ByAccountId`, `FreelancerConditionUseCase.findMyCondition`)도 이미 있는데 매칭 어댑터만 안 바꿔놨다"고 확인해줘서 `resolveFreelancerId`/`findCondition` 실구현으로 교체. `MatchingErrorCode.FREELANCER_NOT_FOUND`(MT_015) 신규. `feature/matching-freelancer-directory-real-impl` 브랜치, push 완료. 이걸로 budgetCap Stage F 블로커 중 findCondition 쪽은 해소됨(배분 규칙은 여전히 미확정).
- **budgetCap 배분 정책 결론** — 3번이 정책·요구사항 전수 확인한 결과 "포지션별 1순위 조합" 같은 확정 규칙은 원래 존재한 적이 없었음(HANDOFF 11번이 애초에 잘못된 전제였던 것 확인). 포지션별 예산/기간 입력란 자체가 없어(클라이언트는 총액만 입력) 재료도 없음. 현재 공식(순예산÷인원÷개월수, 경력 무관 동일 상한)을 그대로 유지하는 A안으로 확정 — **코드 변경 없음**. 경력별 차등(B안)이나 LLM 조합 배분(C안)은 제품 결정이 필요해 팀 회의 안건으로 남김.
- **문서 동기화(HANDOFF 13번)**: `.ai/API.md` 에러 코드 표에 MT_001~MT_015 전부 추가(기존엔 매칭 에러코드가 하나도 없었음). `docs/api-dto.csv`의 `RerecommendRequest.type`/`quantity` 설명에 실제 검증 규칙(MT_012/MT_013) 명시. `docs/spec/requirements.md` 클라이언트 회원가입 명세에 회사주소 필드 반영(`feature/client-address-plus`로 이미 구현·merge된 게 명세엔 누락돼 있었음).
- **전체 파이프라인(Stage A~F) 재검증** — 사용자가 준 단계별 스펙과 코드를 하나씩 대조:
  - Stage C(임베딩 랭킹)/D(추림 컷)는 정상. Stage A/B(하드필터·조건필터)는 여전히 미구현(HANDOFF 10번). Stage F(가드)도 여전히 placeholder.
  - **Stage E(LLM 최종선정) 불일치 발견**: 확정 설계는 "프로젝트당 1회 호출, 전체 포지션 한번에"인데 실제 코드는 포지션마다 따로 `matchingPort.recommend()`를 호출함(`RecruitingStartedEventListener`가 포지션 순회). Python API(`MatchingRequest`)도 `position_id` 하나만 받는 구조라 여러 포지션을 한 번에 처리할 수 없음. 재설계 필요 — 아직 손 안 댐, 범위가 커서 별도 논의 필요할 수 있음.
  - **치명적 결함 발견·해결**: 프리랜서 임베딩(자기소개+경력사항)이 실환경에서 한 번도 생성된 적이 없었음(`MatchingPort`에 메서드 자체가 없었고 freelancer 도메인 어디서도 호출 안 함) — `freelancer_embedding` 테이블이 영원히 비어 있어 Stage C가 검색할 대상이 없는 상태였다. 상세는 아래 신규 배치 참고.
- **프리랜서 임베딩 트리거 신규 연결** (`feature/matching-freelancer-embedding-trigger` 브랜치):
  - `MatchingPort.upsertFreelancerEmbedding(freelancerId, text)` 신규 + `PythonMatchingAdapter`에 구현(`PUT /embeddings/freelancers`, 서킷브레이커 포함, `upsertPositionEmbedding`과 대칭).
  - `matching.application.result.FreelancerResumeSummary`(selfIntroduction + 경력 목록) 신규, `FreelancerDirectoryPort.findResumeSummary(freelancerId)` 신규 — `FreelancerDirectoryAdapter`가 freelancerId→accountId 역조회 후 `ResumeUseCase.findMyResume`으로 실제 조회.
  - `matching.application.service.FreelancerEmbeddingTextBuilder` 신규 — `PositionEmbeddingTextBuilder`와 대칭, 자기소개+경력사항(회사명/직급/담당업무) 조립.
  - `freelancer.application.event.ResumeUpdatedEvent(accountId)` 신규 — `ResumeService.upsert()`가 저장 후 발행(기존 TODO 주석 자리를 실제 발행 코드로 교체).
  - `matching.application.service.ResumeUpdatedEventListener` 신규 — `@TransactionalEventListener(AFTER_COMMIT)`로 받아서 freelancerId 조회 → 이력서 요약 조회 → 텍스트 조립 → 임베딩 upsert. 매칭 자신의 DB에 쓰는 게 없어 `ProjectUpdatedEventListener`와 같은 이유로 별도 REQUIRES_NEW 빈 분리는 필요 없음.
  - 트리거 시점은 **이력서(Resume) 저장**으로 잡음 — 임베딩 텍스트가 자기소개/경력사항(Resume 도메인 필드)이라, `.ai/STATE.md`에 적혀 있던 "조건(Condition) 저장 시마다"는 실제 텍스트 구성과 안 맞아서 이력서 저장 시점으로 정정.
  - `ResumeUpdatedEventListenerTest` 신규(이력서 저장 → 임베딩 upsert 호출 검증), `./gradlew clean build` 통과.
  - 이전에 held-back 상태였던 문서 동기화 커밋(HANDOFF 13번, 회사주소 명세 포함)도 이 브랜치에 같이 실어서 push — "문서만 고친 건 단독 브랜치로 안 올리고 기능 브랜치에 묻어간다"는 사용자 피드백 반영.
- **Pairing-python `_build_prompt`(HANDOFF 9번)에서 발견한 실제 컬럼명 버그 2건 수정** — 하드필터(HANDOFF 10번) 착수 전에 실제 DB로 검증해보려다 발견:
  - `freelancer_condition`/`resume`은 `freelancer_profile.id`가 아니라 `account_id`로 연결됨(당시 `db/init/*.sql` 문서 기준으로 짜서 `freelancer_id`라고 잘못 알고 있었음). `freelancer_embedding`이 쓰는 id는 `freelancer_profile.id`라 반드시 `freelancer_profile`을 거쳐 `account_id`로 다리를 놓아야 함.
  - `project_position.preferred_note`는 존재하지 않는 컬럼(`ProjectPositionJpaEntity` 주석: "우대사항은 프로젝트 단위(`extra_note`)로 통일했다") — 중복 필드라 제거.
  - **검증 과정에서 혼란이 있었음**: 로컬 도커 Postgres 컨테이너(`pairing-postgres`)에 직접 붙어 확인했더니 `freelancer_id` 컬럼이 그대로 있어서(FK도 걸려있어서) 처음 판단이 틀렸다고 오해했었음 — 이 컨테이너는 row가 0개인 빈 컨테이너로, 실제 Spring 앱이 `ddl-auto`로 한 번도 안 건드린 raw SQL 초기화 상태였던 것으로 보임(로컬 개발이 H2를 쓰기 때문에 이 도커 Postgres 자체를 Spring이 아예 안 쓰고 있음). **2번이 실제 컬럼명을 최종 확인**: `account_id`가 맞고 `freelancer_id`는 애초에 없는 컬럼. `fix/directory-repository-column-names` 브랜치(Pairing-python), PR 오픈.
  - **교훈**: 이 레포의 `db/init/*.sql`은 `application.yaml` 주석상 "스키마 단일 소스"라고 되어 있지만 실제로 로컬에 떠 있는 도커 컨테이너와도 100% 일치하지 않을 수 있다 — Python처럼 raw SQL로 스프링 테이블을 직접 읽는 코드는 **Java JPA 엔티티(`@Column`)를 최종 근거로 삼고, 애매하면 실제 담당자에게 확인**하는 게 안전하다(이번에도 그렇게 해서 정답 확인함).
- **Stage B "느슨한 조건 필터" 확인 요청 관련 — 3번 답변이 다른 걸 가리켰음**: 3번이 "사전 검수"(정책 P02, `ProjectPreReviewService`)를 언급했는데, 이건 **프로젝트 등록 시점**에 직무+요구스킬(AND)만으로 후보 수를 세는 별개 기능이라 확인 요청한 것(AI 추천 Stage B, 일정/근무조건/단가 느슨한 필터)과 무관함. 재질문 필요 — 아직 답변 대기.

## 다음 세션에서 할 일

1. ~~PR #51/#53/`feature/matching-negotiation-outcome` merge, 협상 인바운드 배선~~ — 전부 완료.
1-1. ~~`fix/matching-request-card-snapshot-read` PR 생성~~ — PR #59 merge 완료.
1-2. ~~`feature/project-updated-embedding-refresh` PR 머지~~ — PR #61 merge 완료.
1-3. ~~`feature/matching-project-stage-sync` PR 리뷰/머지~~ — 매칭↔프로젝트 상태 연동 3건, merge 완료.
2. ~~`MatchingNegotiationOutcomeUseCase`(협상 결렬/타결 통보) 구현~~ — 매칭+negotiation 양쪽 다 완료.
3. ~~`currentSituation`/`mainTask` 노출 여부 팀 답변 오면 반영~~ — **2026-08-09 결정·구현 완료.** `mainTask`는 상세 조회에서만 노출, `currentSituation`은 노출 안 함으로 확정. `.ai/HANDOFF.md`/프론트 전달 문서 참고.
4. ~~budgetCap의 WEEK→개월 환산 규칙~~ — 4주=1개월로 확정, 반영 완료.
5. ~~임베딩 텍스트에 `detailScope`/`extraNote`도 빠져있음(HANDOFF 22번)~~ — 완료.
6. ~~`resolveFreelancerId`/`findCondition`(freelancerId 기준)~~ — 완료. 계정 승인은 이미 끝나 있었고 매칭 어댑터만 안 바꿔놓은 상태였음.
7. ~~`expire()`(응답기한 만료) 자동 처리 자체가 아직 미구현~~ — **2026-08-10 완료.** `MatchingRequestExpiryScheduler` 신규(10분 주기) + `accept()`/`reject()`에 `isExpired()` 선체크 추가(스케줄러 주기 사이 창구 방지, MT_016). 아래 "매칭 요청 응답기한 자동 만료" 참고.
8. ~~`AI매칭_API_화면매핑_최신본.md`(MT_009/quantity 경고) 반영 확인~~ — 완료. 그 이후 코드가 또 바뀌어서(MT_012 실제 검증 추가, MT_013/MT_014/MT_015 신규) 문서가 다시 뒤처짐 — 사용자가 직접 갱신할 항목.
9. ~~Pairing-python `search_similar_freelancers` 하드필터 추가(HANDOFF 10번)~~ — **2026-08-09 완료.**
    - **AI매칭 동의 + 직군/직무 일치**: `freelancer_embedding`→`freelancer_profile`→`freelancer_condition`/`account` 조인으로 벡터 검색 자체에서 필터링(`EmbeddingRepository.search_similar_freelancers`). `MatchingService.recommend()`가 포지션 조회를 먼저 하도록 순서 변경(job_category/job_role을 얻으려고). `GET /embeddings/positions/{id}/candidates`(Java에서 실제로 부르는 곳이 없는 죽은 엔드포인트)는 job_category/job_role이 없으면 필터 없이 그대로 동작하도록 옵셔널 처리해서 안 건드림.
    - **이전에 노출된 프리랜서 제외도 같이 이동**: Java `MatchingRoundCreationService.excludePreviouslySurfaced`(사후 필터) 삭제, `findFreelancerIdsByProjectId` 결과를 `MatchingPort.recommend(..., excludedFreelancerIds)`로 미리 넘기도록 변경. Python `MatchingRequest.excluded_freelancer_ids` 신규 필드로 받아 SQL `NOT IN`으로 반영.
    - Java 3곳 수정: `MatchingPort`/`PythonMatchingAdapter`(요청 바디에 `excluded_freelancer_ids` 추가, fallback 시그니처도 맞춤)/`MatchingRoundCreationService`. 기존 목 스텁 3건(`MatchingIntegrationTest`, `RecruitingStartedEventListenerTest` 2건)이 3-arg `recommend()`를 stub하고 있어서 4-arg(`eq(List.of())`)로 갱신. `./gradlew build` 통과.
    - Python 4개 파일 수정: `embedding/repository.py`(조인 추가), `embedding/service.py`/`matching/service.py`(파라미터 전달)/`matching/schemas.py`+`router.py`(요청 필드 추가). `pytest`(10개)/`ruff` 통과.
    - ~~일정/근무조건/단가(느슨하게)~~ 폐기(위 참고). Stage E 감점 반영은 아직 미착수(HANDOFF 10-2번) — Python `FreelancerProfile`/`PositionRequirement`에 조건 필드 추가 + 프롬프트 지시 추가가 남음.
    - ~~일정/근무조건/단가(느슨하게)~~ **(2026-08-09 방향 확정) 폐기.** 3번 재확인 답변: 사전검수(P02)는 직무+요구스킬+계정ACTIVE+AI매칭동의만 보고 후보 수를 안내하는데, 매칭이 여기에 일정/단가 필터를 더 얹으면 사전검수가 안내한 후보 수보다 실제 추천이 줄 수 있고 착수금은 환불이 없어 클레임 구조가 된다는 논거. `policy.md` P03/P04(파이프라인은 "임베딩→LLM"이 전부, 조건필터 단계 자체가 정책에 없었음), `requirements.md` R02.3(가드도 직무·스킬만 검증), `ProjectPreReviewService`/`FreelancerCandidateCountService` 코드로 직접 재확인해 3번 말이 맞음을 확인. 사용자가 기억하던 "단가 20% 오차"는 이거와는 다른, 이미 구현된 budgetCap 1.2배 규칙이었고 Stage B용으로 따로 정해진 수치는 없었음을 재확인.
      **결론: Stage B는 AI매칭 동의+직군/직무만 하드필터. 일정/근무조건/단가 불일치는 Stage E(LLM 최종선정)에서 감점+추천사유로만 반영, 후보 배제 안 함(P09 "적합도 낮은 후보도 노출될 수 있다"와 동일 패턴). 계산식은 안 만들고 LLM이 원본 데이터 보고 판단.** 상세는 `.ai/STATE.md` "2026-08-09 갱신 — Stage B 조건필터 폐기" 참고.
    - 이전에 노출된 프리랜서 제외(`excludePreviouslySurfaced`)도 Java가 결과 받은 뒤 후처리하는 대신 Python이 검색 전에 미리 빼도록 옮기는 것도 이 작업 범위(풀 크기 줄어드는 문제 해결). Python `/recommendations` 요청 스키마에 제외 id 목록 추가 필요.
10. budgetCap Stage F(HANDOFF 11번) — 배분 알고리즘 규칙 자체가 확정된 적 없음이 3번 확인으로 밝혀짐. 현재 공식(A안) 유지로 결정, 팀 회의에서 경력 차등(B안)/LLM 조합(C안) 여부만 다시 논의될 수 있음.
11. (선택) 이 컴퓨터에 `gh` CLI 설치하면 다음부터 이슈/PR을 AI가 직접 생성할 수 있음 — 지금은 매번 텍스트만 만들어주고 사용자가 직접 생성 중.
12. ~~프리랜서 임베딩이 실환경에서 한 번도 생성되지 않던 결함~~ — 완료(위 참고). 이걸로 Stage C가 이제야 실제로 검색할 대상이 생김.
13. **Stage E(LLM 최종선정) 재설계** — 확정 설계는 "프로젝트당 1회 호출, 전체 포지션 한번에"인데 지금은 포지션마다 따로 호출함. Python `MatchingRequest`가 `position_id` 단일값만 받는 구조라 API 계약 자체를 바꿔야 함(여러 포지션 id + 포지션별 후보 풀을 한 번에 받아서 LLM 프롬프트도 여러 포지션을 한꺼번에 판단하게). 범위가 커서 착수 전 사용자와 우선순위 논의 필요.
14. Pairing-python `fix/directory-repository-column-names` PR — 리뷰/머지 대기(2번이 컬럼명 confirm 완료, 코드는 맞게 고쳐져 있음).

## 2026-08-09 (계속) — Stage F 가드 실제 구현

- `MatchingRoundCreationService.applyGuard(true, null)` placeholder를 실제 검증으로 교체(HANDOFF 11번 하위 항목). R02.3 요구사항 원문("가드 AI로 마지막 검증 (직무, 스킬 검증)")만 그대로 구현 — **예산 조합 재검증은 넣지 않음**. 이전에 STATE.md에 "예산 조합 재검증"이 가드 범위로 적혀 있었는데, 이번에 다시 요구사항 원문을 확인해보니 근거가 없었음(Stage B 조건필터가 근거 없이 끼워넣은 가정이었던 것과 같은 패턴). budgetCap은 협상 단계(`NegotiationConditionCalculator`)에서 이미 별도로 재검증되고 있어서 가드에 중복으로 넣을 이유도 없음.
- `findCondition(freelancerId)`로 프리랜서 실제 조건을 가져와 `ProjectDirectoryPort.findPositionSummary`(실시간 조회, 스냅샷 아님)의 jobRole/requiredSkills와 비교. 가드에 떨어진 후보는 `guardPassed=false`로 기록은 남기되 노출(`expose`)하지 않고, 노출 인원(exposeCount) 자리는 다음 순위의 가드 통과 후보가 채우도록 `persistCandidates`의 노출 카운팅 로직을 별도 카운터(`exposedCount`)로 분리.
- **테스트 빈틈 발견·보완**: `RecruitingStartedEventListenerTest`가 그동안 freelancerId(999_001L)에 `freelancer_condition`을 전혀 안 심고도 통과하고 있었음 — 가드가 placeholder라 `findCondition`을 실제로 안 불렀기 때문. 가드가 실제로 호출하게 되면서 예외가 나서 발견, 포지션 요구조건(BACKEND/SPRING_BOOT)과 일치하는 조건을 `freelancerConditionUseCase.upsert()`로 심도록 수정.
- `MatchingIntegrationTest`에 가드 탈락 시나리오 신규 테스트 추가: 점수가 더 높지만(95점) 요구 스킬(SPRING_BOOT)이 없는 후보와 점수가 낮지만(80점) 스킬이 일치하는 후보를 같이 LLM 응답으로 주고, 가드 통과한 후자만 노출되는지 확인(가드가 없었다면 95점 후보가 노출됐을 것이므로 실질적인 검증이 됨).
- `./gradlew build` 전체 통과. `feature/matching-stage-f-guard` 브랜치.

## 2026-08-10 — 매칭 요청 상세에 `mainTask` 노출 (task #4 해소)

3번에게 물었던 `currentSituation`/`mainTask` 노출 여부 답변 옴: **`mainTask`만, 카드가 아니라 요청 상세(`GET /requests/{requestId}`)에서만** 노출하기로 결정. 프리랜서가 수락 여부 판단할 때 mainTask가 제일 직접적인 정보고, currentSituation은 배경 설명이라 상세에서도 길어지기만 한다는 이유. 3번 쪽 작업 없음(`ProjectPositionSummary.mainTask`는 이미 `findPositionSummary` 응답에 있었음, 매칭 쪽만 안 옮기고 있었음).

- `ProjectPositionSummary`(매칭 로컬)에 `mainTask` 추가, `ProjectDirectoryAdapter`가 매핑.
- `RecruitingStartedPositionHandler.freezeSnapshot`이 PROJECT 스냅샷에 `mainTask`도 얼림(R32 대상, 프로젝트 수정 가능 필드라 라이브 아니라 스냅샷). 이미 모집 시작한 프로젝트는 스냅샷에 이 필드가 없어 null로 나옴 — 3번이 미리 경고해준 부분, 실제로 로컬 테스트 데이터(snapshot payload)에 필드 추가해서 확인함.
- `MatchingRequestResponseAssembler`에 `buildDetail()` 신규(기존 `build()`는 그대로 두고 mainTask는 항상 null) — `findRequest()`만 `buildDetail()` 사용.
- **진짜 버그 발견·수정**: `GET /requests/{requestId}`에 처음으로 실제 테스트(클라이언트 시점)를 붙이자마자 404가 남. 원인은 `findRequest()`가 클라이언트 소유 여부 확인보다 `resolveFreelancerId(accountId)`를 먼저 무조건 호출하고 있었던 것 — 클라이언트 accountId는 freelancer_profile이 없으니 매번 `FREELANCER_NOT_FOUND`(404)가 났다. **이 엔드포인트를 실제로 호출하는 테스트가 지금까지 하나도 없어서 안 드러난 버그.** 클라이언트 소유 확인을 먼저 하고, 아닐 때만 `resolveFreelancerId`를 부르도록 순서를 바꿔서 해결.
- `docs/api-dto.csv`/`.ai/API.md` 동기화. `MatchingIntegrationTest`에 "상세에서만 보이고 목록/받은요청엔 안 보인다" 테스트 추가. `./gradlew build` 전체 통과.
- `feature/matching-request-detail-main-task` 브랜치.

## 2026-08-10 (계속) — freelancer `/me/matching-settings` 스텁 교체 착수

사용자가 마이페이지 화면 시안(AI 매칭 온/오프 토글)을 보고 실제로 저장되는지 확인 요청 → 확인해보니 `FreelancerController.findMyMatchingSettings`/`updateMyMatchingSettings`(`/api/v1/freelancers/me/matching-settings`)가 TODO 주석 달린 고정 응답이었음(저장 안 하고 요청값 그대로 에코). 원래 freelancer 도메인(2번) 담당이라 2번에게 알릴 메시지까지 작성했는데, **사용자가 직접 만들기로 결정** — freelancer 폴더를 건드려야 한다는 것도 확인하고 진행하기로 함.

**조사 완료, 구현 시작 전 상태:**
- `MatchingSettingsRequest(Boolean aiMatchingAgreed, Boolean matchingPaused)` / `MatchingSettingsResponse(boolean aiMatchingAgreed, boolean matchingPaused, boolean matchable, String unmatchableReason)` — 이미 정의돼 있음(DTO 자체는 스텁 아님, 컨트롤러 로직만 스텁).
- `db/init/02-create-schema.sql`의 `freelancer_profile` 테이블에 `matching_paused BOOLEAN DEFAULT FALSE NOT NULL` **컬럼이 이미 존재함** — 스키마 마이그레이션 불필요, JPA 엔티티/도메인 모델에 매핑만 안 해놨던 상태.
- `FreelancerProfile`(도메인)/`FreelancerProfileJpaEntity`에 `matchingPaused` 필드가 없음 — 추가 필요.
- `ai_matching_agreed`는 이미 필드로 있지만 컨트롤러가 저장 안 하고 있었음 — 저장 로직 연결 필요.
- `matchable` 계산 기준(추정, 확정 아님): `aiMatchingAgreed && !matchingPaused && 이력서 ResumeStatus.COMPLETED`. `ResumeStatus`는 `DRAFT`/`COMPLETED` 2종, "완료된 이력서만 매칭에 쓰인다"는 주석이 이미 있음.
- 다음 단계: `FreelancerProfile`/`FreelancerProfileJpaEntity`/매퍼에 `matchingPaused` 추가 → 저장용 유스케이스(예: `FreelancerConditionUseCase` 유사 패턴 또는 새 메서드) → `FreelancerController` 두 엔드포인트 실구현 교체 → `matchable`/`unmatchableReason` 계산 로직(이력서 상태 조회 필요) → 테스트.

## 2026-08-10 (계속) — freelancer `/me/matching-settings` 실구현 완료

- `FreelancerProfile`(account 도메인 모델)/`FreelancerProfileJpaEntity`/`FreelancerProfileMapper`에 `matchingPaused` 필드 추가(컬럼은 이미 있어서 매핑만). `updateMatchingSettings(aiMatchingAgreed, matchingPaused)` 도메인 메서드 추가.
- `AccountCommandUseCase`에 `updateFreelancerMatchingSettings(accountId, aiMatchingAgreed, matchingPaused)` 신규 — `updateClientProfile`과 같은 패턴(계정 도메인이 자기 애그리거트 저장을 책임짐). freelancer 도메인은 이미 `AccountQueryUseCase`를 직접 의존하는 기존 패턴(`ResumeService` 등)이 있어서 `FreelancerController`가 `AccountQueryUseCase`/`AccountCommandUseCase`를 직접 주입받는 것도 같은 결.
- `matchable` 판정 확정: `aiMatchingAgreed && !matchingPaused && (이력서 존재 = COMPLETED)`. 이력서 존재 여부는 `resumeUseCase.findMyResume(accountId).isPresent()`로 확인(이 도메인은 이력서가 있으면 항상 COMPLETED — `Resume.create`가 검증을 통과해야만 생성되기 때문에 DRAFT 상태로 저장되는 경로가 없음. `FreelancerResumePageResponse`도 "없으면 DRAFT" 로 다루는 걸 확인하고 같은 전제를 씀).
- `unmatchableReason` 우선순위: AI매칭 미동의 > 매칭 일시중지 > 이력서 미완성. 이력서 미완성 문구는 기존 `MatchingSettingsResponse` 스키마 예시 문구("이력서를 완성해야...")를 그대로 씀 — 나머지 두 사유는 이번에 새로 정한 문구.
- `FreelancerController` 두 엔드포인트 실구현으로 교체. TODO 주석 제거.
- 테스트: `FreelancerMyPageIntegrationTest`에 4개 추가(H2, 실제 HTTP 요청) — 이력서 없을 때 미완성 사유, 이력서 완료 후 matchable, PUT 저장이 실제로 DB에 반영되는지(리포지토리 직접 조회로 재확인), AI매칭 동의 해제가 이력서 완료보다 우선하는지. `./gradlew build` 전체 통과.
- `feature/freelancer-matching-settings` 브랜치. 문서만 올리지 않도록 이번엔 코드와 같은 커밋/브랜치로 묶어서 push.

## 2026-08-10 (계속) — 매칭 요청 응답기한(3일) 자동 만료 + rejectReason 노출

프론트 화면 시안(프리랜서 "프로젝트 제안" 목록) 리뷰 중 발견: `expiresAt`/`expire()`/`isExpired()`는 이미 있었는데 실제로 호출하는 곳이 없어서, 프리랜서가 요청을 방치하면 `REQUEST_PENDING`으로 영원히 남는 상태였음. 이게 단순 표시 문제가 아니라 **무료 재추천 조건(P41)을 실제로 막는 살아있는 버그**였음을 코드 추적으로 확인 — `MatchingRerecommendService.assertFreeAvailable()`가 `existsActiveByProjectId()`를 보는데, 이건 `REJECTED`/`NEGOTIATION_FAILED`가 아닌 상태가 하나라도 있으면 true라서, 응답 안 한 프리랜서 한 명 때문에 클라이언트가 무료 재추천을 영원히 못 쓸 수 있었음.

- `MatchingRequestExpiryScheduler`(신규, `application.scheduler`) — 10분마다 `expireOverdueRequests()` 호출. `ProjectRecruitExpiryScheduler`와 같은 패턴(`@EnableScheduling`은 `ProjectSchedulingConfig`가 전역으로 이미 켜둠).
- `accept()`/`reject()`는 스케줄러 주기(10분) 사이의 창구를 막기 위해 진입 시 `isExpired()`를 먼저 확인 — 지났으면 그 자리에서 만료 처리 후 `MT_016`으로 막음.
- **버그 발견·수정**: 처음엔 만료 처리(`request.expire()` + `save()`)를 accept()/reject() 자신의 트랜잭션 안에서 그냥 하고 바로 예외를 던졌는데, 그 예외가 트랜잭션을 롤백시켜서 방금 저장한 만료 처리까지 같이 사라지는 걸 테스트가 잡아냄(코드 리뷰로 트랜잭션 위험을 미리 지적받고, 실제로 통합테스트 돌려보니 그대로 재현됨). `MatchingRequestExpirer`(신규, REQUIRES_NEW)로 분리해서 해결 — `RecruitingStartedPositionHandler`와 같은 이유(자기 자신 호출로는 REQUIRES_NEW가 실제로 적용 안 됨)로 별도 빈으로 뺐음. 스케줄러의 배치 처리(`expireOverdueRequests()`)도 같은 이유로 건별 독립 커밋되도록 이 빈을 거치게 바꿔서, "한 건 실패해도 나머지는 처리한다"는 말이 실제로 성립하게 함.
- **`rejectReason` 필드 추가**: 화면 시안에 "응답 기한 만료"와 "거절함" 배너가 서로 다른 문구로 있는데, 지금 API는 둘 다 `status=REJECTED`로만 내려가서 프론트가 구분을 못 하는 걸 발견. `MatchingRequestResponse`에 `rejectReason`(`RejectReason` enum, `DIRECT_REJECT`/`EXPIRED`/`NEGOTIATION_FAILED`) 추가 — `status`처럼 원본 enum 그대로 내려주고 라벨 매핑은 프론트가 함. mainTask와 달리 상세 전용이 아니라 목록/카드에도 항상 채워짐(배너가 목록 카드에도 나오는 시안이라서).
- 화면 시안에 있던 "AI 94%" 점수 배지는 **일부러 안 만듦** — `fitScore`는 클라이언트 등급 가중치가 반영된 값이라 프리랜서에게 그대로 보여주면 "왜 나는 같은 실력인데 점수가 다르지"로 오해살 수 있고, 원래 정책도 "적합도 점수 숫자는 노출 안 함"(클라이언트 후보 카드 기준)이라 프리랜서 쪽도 그대로 따르기로 사용자가 결정.
- 테스트: `MatchingIntegrationTest`에 4건 추가 — 만료 전 무료 재추천 막힘 → 만료 처리 → `REJECTED`+`rejectReason=EXPIRED` → 무료 재추천 풀림, 스케줄러 주기 사이에 수락 시도하면 `MT_016`으로 막히는 케이스, 직접 거절 시 `rejectReason=DIRECT_REJECT`.
- 문서 동기화: `.ai/STATE.md`/`HANDOFF.md`/`API.md`(MT_016), `docs/api-spec.csv`(매칭 요청 발송 API 스켈레톤→구현완료), `docs/api-dto.csv`(rejectReason), 프론트 전달 문서(`AI매칭_API_화면매핑_최신본.md`, Desktop) — mainTask/currentSituation 결정 반영, Pairing-python 하드필터/Stage F 가드 해결 반영, rejectReason 필드+enum 테이블 추가.
- `feature/matching-request-auto-expire` 브랜치. `./gradlew clean build` 전체 통과 확인.

## 2026-08-10 (계속) — 임베딩 일괄 재색인 관리자 API 추가

Pairing-python 담당 팀원이 임베딩 모델을 `text-embedding-004` → `gemini-embedding-001`로 교체(신규 API 키에서 기존 모델이 404, PR #19). 차원(768)은 그대로라 DB 스키마는 안 바뀌지만, **모델이 바뀌면 같은 텍스트도 완전히 다른 벡터가 나와서** 옛 모델로 만든 벡터와 새 모델로 만든 벡터가 섞이면 에러 없이 조용히 추천 결과가 틀어진다. 현재는 이력서 저장/모집 시작 시점에만 임베딩이 생성돼서 기존 벡터를 한 번에 다시 만들 방법이 없다는 요청을 받음(임베딩 텍스트 조립 로직이 전부 Java 쪽에 있어 매칭 도메인 담당).

- `POST /api/v1/matchings/admin/embeddings/reindex`(신규, ADMIN 전용, 다른 도메인 admin처럼 컨트롤러 자체 경로+전역 시큐리티 규칙 `/api/v1/*/admin/**`로만 막음) — 이력서 있는 프리랜서 전체 + 모집 시작한 포지션 전체(`MatchingSnapshotRepository`에 POSITION 타입 스냅샷이 있는 것들)를 순회하며 기존 텍스트 조립 로직(`FreelancerEmbeddingTextBuilder`/`PositionEmbeddingTextBuilder`)을 그대로 재사용해 `MatchingPort.upsertFreelancerEmbedding`/`upsertPositionEmbedding`을 다시 호출. 새 로직 중복 구현 없이 기존 호출을 반복하는 얇은 오케스트레이션(`EmbeddingReindexService`)이라 실패한 항목만 건너뛰고 나머지는 계속 진행, 성공/실패 건수를 응답으로 돌려줌.
- 대상 목록을 고르려고 두 군데 추가: `ResumeRepository.findAllAccountIds()`(freelancer 도메인, 이력서 있는 계정 전체) → `FreelancerDirectoryPort.findAllFreelancerIdsWithResume()`, `MatchingSnapshotRepository.findAllBySnapshotType(POSITION)`(매칭 자신의 도메인이라 새 포트 불필요).
- 테스트는 `@SpringBootTest` 대신 순수 Mockito 단위테스트(`EmbeddingReindexServiceTest`)로 작성 — 이 서비스는 포트 호출만 반복하는 얇은 오케스트레이션이라 실제 DB/Gemini 없이도 충분히 검증되고, 안 그래도 알려진 풀스위트 전용 flaky 이슈(`.ai/HANDOFF.md` 참고)에 컨텍스트를 더 안 보태려는 목적도 있음.
- `feature/matching-embedding-reindex` 브랜치. `.ai/API.md`(11. Matching 표), `.ai/STATE.md`(임베딩 모델명 갱신 + 재색인 API 언급) 동기화.

## 2026-08-10 (계속) — LLM 호출 비동기 처리 (강사 요구사항)

강사 요구사항: "AI쪽 LLM 돌릴 때 프론트 화면에서 기다리게 하지 말고 비동기로 처리". 확인해보니
`AsyncConfig`(`@EnableAsync` + 스레드풀 core 5/max 10/queue 500)는 이미 있는데 `@Async`를 쓰는 곳이
코드 전체에 한 곳도 없었다. 그래서 LLM 호출이 전부 요청 스레드를 붙잡고 있었다.

**문제가 컸던 이유**: `@TransactionalEventListener(AFTER_COMMIT)`는 커밋한 스레드에서 그대로 이어
실행된다. 즉 매칭이 남의 도메인 API 응답을 붙잡고 있었다 — 결제(정산 도메인), 이력서 저장(freelancer),
프로젝트 수정(project). LLM 읽기 타임아웃이 60초라 포지션이 여러 개면 결제 화면이 1분 넘게 멈춘다.

- `RecruitingStartedEventListener`(결제 완료 → 최초 추천), `ResumeUpdatedEventListener`(이력서 저장 →
  프리랜서 임베딩), `ProjectUpdatedEventListener`(프로젝트 수정 → 포지션 임베딩) 3곳에 `@Async` 추가.
  전부 결과를 응답에 안 싣는 후처리라 스레드만 분리하면 되고, 각 리스너가 이미 예외를 잡아 로그로
  남기고 있어서 비동기로 바뀌어도 실패가 묻히지 않는다.
- 관리자 재색인 API(`POST /admin/embeddings/reindex`)를 **202 즉시 응답 + 백그라운드 처리**로 변경.
  대상 1건마다 외부 AI 호출이라 전체가 몇 분씩 걸리는데, 동기로 두면 관리자 화면이 멈추고 그 전에
  프록시 타임아웃에 먼저 끊긴다. `EmbeddingReindexUseCase.startReindexAll()`(`@Async`) 신규,
  기존 `reindexAll()`은 결과를 돌려주는 동기 버전으로 남겨 테스트/배치용으로 유지.
  결과 건수는 응답 대신 완료 로그로 남긴다 — 응답 DTO(`EmbeddingReindexResponse`)는 쓰는 곳이
  없어져서 삭제하고 `docs/api-dto.csv` 행도 제거.
- `ApiResponse.accepted(code, message)` 추가(202). 기존 `success`(200)/`created`(201)와 같은 패턴.
- **테스트 5개가 깨졌다** — 비동기로 바뀌니 검증이 백그라운드 작업보다 먼저 실행됨. sleep이나
  Mockito `timeout()`으로 때우면 느리고 CI에서 간헐적으로 깨지므로, `SyncTaskExecutorTestConfig`
  (테스트 전용 `@Primary` `SyncTaskExecutor`)를 만들어 해당 3개 테스트 클래스에서 `@Import`.
  검증 대상은 리스너의 처리 내용이지 스프링의 비동기 동작 자체가 아니라, 실행 스레드만 동기로
  바꿔 결정적으로 만들었다.
- `feature/matching-async-llm-processing` 브랜치. `./gradlew clean build` 전체 통과.

**남은 것(2번, 팀 협의 중)**: 재추천 API(`POST /rerecommendations`)는 사용자가 결과를 기다리는
화면이라 그냥 비동기로 던지면 보여줄 게 없다. (a) 현행 유지 + 프론트 로딩 UI, (b) 202 응답 후
WebSocket 알림(이 프로젝트에 STOMP·알림 도메인이 이미 있음) 중 선택 필요 — 프론트 작업이 같이
필요해서 사용자가 팀과 협의하기로 함.

## 2026-08-10 (계속) — 재추천 비동기 전환 + 매칭 알림 5종

강사 요구사항 2번(재추천도 비동기)과 알림 담당자의 `NotificationCreateUseCase` 연동 요청을 함께 처리.
재추천 방식은 사용자가 팀과 협의해 **(b) 비동기 + 알림 푸시**로 결정.

**재추천 비동기 전환**

- `POST /positions/{id}/rerecommendations`가 `202` + 본문 없음으로 바뀐다(기존 `201` + 후보 목록).
  후보는 AI 호출이 끝난 뒤 비동기로 채워지고, 완료되면 `MATCHING_RECOMMENDED` 알림이 간다.
- **검증은 동기로 남겼다.** 한도 초과(MT_008)·모집 종료(MT_014)·quantity 누락(MT_012)은 버튼을 누른
  즉시 알려줘야지 알림으로 실패를 통보하면 쓰기 나쁘다.
- **회차 레코드 생성까지도 동기다.** `MatchingRoundCreationService.createRound`를
  `openRound`(회차만)와 `fillCandidates`(AI 호출 + 후보 저장)로 쪼갰다. 회차가 저장돼야
  `assertFreeAvailable`이 다음 요청을 막는데, LLM까지 기다렸다 저장하면 그 사이 같은 버튼을 두 번
  누르면 회차가 두 개 생긴다(무료 1회 정책 구멍). `createRound`는 둘을 합친 형태로 남겨서 최초
  추천(모집 시작, 이미 비동기 문맥)이 계속 쓴다.
- `RerecommendRequestedEvent` + `RerecommendRequestedEventListener`(`@Async` + AFTER_COMMIT +
  REQUIRES_NEW) 신규. AFTER_COMMIT이어야 비동기 스레드가 방금 만든 회차를 조회할 수 있다.
- 후보가 0명이어도 알림을 보낸다 — 기다리는 쪽에서는 "아직 처리 중"과 구분이 안 되기 때문.

**알림 5종**(`MatchingNotifier`로 모음 — 문구·링크가 흩어지면 같은 상황에 다른 말이 나간다)

| 시점 | 받는 사람 | 타입 |
| --- | --- | --- |
| 매칭 요청 발송 | 프리랜서 | `MATCHING_REQUESTED` |
| 요청 수락 | 클라이언트 | `MATCHING_ACCEPTED` |
| 요청 거절 | 클라이언트 | `MATCHING_REJECTED` |
| 응답기한 만료(자동) | 클라이언트 | `MATCHING_REJECTED` (문구로 구분) |
| 재추천 완료 | 클라이언트 | `MATCHING_RECOMMENDED` |

- 만료 알림은 `MatchingRequestExpirer.expireNow()`에 넣었다 — 스케줄러 경로와 수락/거절 중 발견되는
  경로가 모두 여기를 지나므로 한 번만 보내진다.
- 알림용으로 `FreelancerDirectoryPort.resolveAccountId(freelancerId)` 신규(기존 `resolveFreelancerId`의
  반대 방향). 알림은 계정 단위인데 매칭이 들고 있는 건 freelancerId뿐이라 변환이 필요했다.
- **알림 실패가 본 기능을 막지 않는다.** 매칭 요청은 정상 처리됐는데 알림 한 건 때문에 500이 나가면
  안 되므로 `MatchingNotifier`에서 예외를 삼키고 로그만 남긴다.

**테스트**: 재추천 통합테스트 4건을 새 흐름(202 → 후보 조회 API 재호출)으로 수정하고
`MatchingIntegrationTest`에도 `SyncTaskExecutorTestConfig`를 `@Import`. `./gradlew clean build` 통과.
(전체 빌드 1회차에서 `ChatServiceTest`가 실패했는데 단독 실행은 통과하고 2회차 전체 빌드도 통과 —
이 레포에 이미 알려진 풀스위트 전용 flaky 이슈로 판단, 매칭 변경과 무관.)

## 2026-08-10 (계속) — 재추천 비동기 리뷰 지적 3건 처리

**① 실패한 회차가 RUNNING으로 방치됨 (유효, 지적보다 결과가 더 나빴음)**

`fillCandidates()` 실패 시 로그만 찍고 끝나서 회차가 `RUNNING`으로 영원히 남았다. 추적해보니
그것보다 심각한 게 있었는데, `assertFreeAvailable`이 `countByProjectIdAndRoundType(FREE) > 0`으로만
보기 때문에 **AI 서버가 잠깐 죽으면 무료 재추천 1회가 영영 사라진다**(후보는 한 명도 못 받았는데).

- 실패 시 `round.fail()` + 실패 알림 발송 추가. 성공하든 실패하든 알림이 가야 클라이언트가
  "추천 생성 중" 화면에서 안 오는 알림을 기다리지 않는다.
- `countByProjectIdAndRoundType`이 `FAILED` 회차를 세지 않도록 변경(`...AndStatusNot`). 무료/유료
  한도 계산과 화면의 `paidRerecommendRemaining` 양쪽에 같이 적용된다.
- **트랜잭션 문제를 하나 더 찾았다**: 실패 처리를 리스너의 같은 트랜잭션에서 하면, 이미
  rollback-only로 오염된 트랜잭션이라 FAILED 저장이 같이 롤백된다. `RerecommendRoundFiller`(별도 빈,
  성공/실패 각각 REQUIRES_NEW)로 분리해서 해결 — `MatchingRequestExpirer`와 같은 패턴.
- 회귀 테스트 추가: AI 호출이 터지면 회차가 FAILED로 닫히고, 이어서 재추천을 다시 걸면 성공하며
  `paidRerecommendRemaining`이 4(5회 중 성공한 1회만 차감)로 나오는지 검증.

**② 알림/Swagger 한글 깨짐 — 오탐**

소스 파일은 UTF-8이고(`file` 확인), `build.gradle`에 `options.encoding = 'UTF-8'`이 이미 있으며,
컴파일된 `.class`에서 한글 문자열 9개를 추출해 전부 정상인 것까지 확인했다. 리뷰 도구가 cp949로
읽어서 깨져 보인 것으로 보인다(이 환경의 콘솔 출력도 같은 이유로 깨진다). 코드 변경 없음.

**③ 무료 재추천 판정에서 종결 상태가 active로 잡힘 (유효, 지적보다 1개 더 있었음)**

`NON_ACTIVE_STATUSES`가 `REJECTED`/`NEGOTIATION_FAILED` 2개뿐인데 도메인의
`MatchingRequest.isTerminal()`은 4개(`TERMINATED`/`CLOSED` 포함)를 종결로 본다. 지적된 `TERMINATED`
외에 `CLOSED`도 빠져 있어서 둘 다 추가하고, 두 목록이 어긋나면 안 된다는 주석을 달았다.
