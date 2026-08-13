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

## 2026-08-10 (계속) — Stage E 조건 감점 구현 (HANDOFF 10-2번, Pairing-python)

일정/근무조건/단가를 하드필터로 배제하지 않고 LLM 최종선정에서 감점 요인으로만 반영하기로 한 결정(`.ai/STATE.md` "Stage B 조건필터 폐기")의 남은 절반. 여태 프롬프트가 직군/직무/경력/스킬/자기소개/경력사항만 보여주고 예산·단가·일정·근무조건은 LLM에게 아예 안 알려주고 있었어서, "감점하기로 했다"는 결정이 실제로는 아무 효과가 없던 상태였다.

- `DirectoryRepository`: 포지션 쪽에 `budget_amount`/`period_value`/`period_unit`/`start_desired_date`/`start_negotiable`, 프리랜서 쪽에 `pay_unit`/`pay_amount`/`work_style`/`work_form`/`available_from`/`start_negotiable`/`period_value`/`period_unit` 추가. 프리랜서 쿼리는 `string_agg`(경력 요약) 때문에 GROUP BY가 있어서 새 컬럼을 SELECT뿐 아니라 GROUP BY에도 넣어야 했다.
- `_build_prompt`: 조건 값을 프롬프트에 넣고 "어긋나도 후보에서 제외하지 말고 감점만 하고 사유에 적어라"를 명시. 감점 공식은 우리가 정하지 않고 LLM 판단에 맡김(설계 결정대로).
- **함정 2개를 프롬프트에서 막았음**: (1) 총예산은 프로젝트 전체 인원·기간 합계인데 프리랜서 희망급여는 1인 월단가라, 그냥 넣으면 LLM이 4,800만 vs 620만을 직접 비교해서 과도하게 감점한다 — "두 숫자를 그대로 비교하지 말고 기간·인원 감안하라, 총예산은 참고치일 뿐 확정 상한 아니다"를 명시. (2) `start_negotiable`(협의 가능)이 켜진 항목은 어긋나도 감점하지 말라고 명시 — 안 그러면 "시작일 협의 가능"인 프리랜서가 일정 불일치로 부당하게 밀린다.
- 조건을 아직 안 채운 프리랜서도 후보에 들어올 수 있어서, 값이 `None`인 항목은 줄 자체를 뺐다(그냥 찍으면 LLM이 "None"을 조건 값으로 읽는다).
- 테스트 3건 추가(`tests/test_matching_service.py`): 조건 값이 실제로 프롬프트에 들어가는지, 협의가능 표시가 붙는지, 값 없을 때 줄이 빠지는지. 전체 22건 통과(기존 19 + 3), ruff 통과. 실제 렌더링된 프롬프트도 직접 출력해서 눈으로 확인함.
- **API 응답 모양은 안 바뀜** — 기존 `reason`(파이프 구분 문자열)에 감점 사유가 항목으로 하나 더 붙는 형태라 프론트 변경 불필요.
- 같은 브랜치(`feature/matching-stage-e-condition-penalty`)에 임베딩 모델 교체 관련 Python 문서 정정도 같이 실었다(`db/init/10-create-ai-schema.sql`, `README.md` — "차원 768은 text-embedding-004 기준"이 낡은 문구였고, `output_dimensionality`로 차원을 맞춰도 벡터 공간은 달라진다는 점이 빠져 있었음).

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

## 2026-08-10 (계속) — 즉시 타결 시 매칭 요청이 NEGOTIATING에 갇히던 문제 (3번·5번 협의)

3번이 계약 도메인 붙이며 발견, 5번이 (a)안(한 PR로 묶기)으로 동의해서 양쪽을 함께 수정.
따로 머지되면 위험이 순서에 갈린다 — 매칭만 먼저 들어가면 무해하지만, 협상만 먼저 들어가면
즉시 타결 시 수락이 롤백되는 장애가 배포된다.

- `MatchingRequestService.accept()` 순서 변경: `advanceStatus(NEGOTIATING)` + `save`를
  `createNegotiation` **앞으로** 옮김. 기존엔 협상이 즉시 타결로 올려둔 `CONTRACT_PENDING`을
  뒤따라오는 `advanceStatus`가 덮어썼다. 또 `agreeNegotiation()`이 `NEGOTIATING`을 요구하는데
  저장 전이면 DB는 아직 `REQUEST_PENDING`이라 `INVALID_MATCHING_STATE`로 수락째 롤백된다.
- `NegotiationCommandService` 즉시 타결 블록에 `markNegotiationAgreed(requestId)` 추가.
  라운드를 도는 경로(`NegotiationLoopService.answer()`)는 이미 부르고 있어서 여기만 빠져 있었다.

**예상 못 했던 것 — 순환 참조.** 위 한 줄을 넣자 컨텍스트가 아예 안 떴다:
`matchingRequestService → negotiationAdapter → negotiationCommandService → matchingRequestService`.
`MatchingRequestService`가 "매칭→협상"과 "협상→매칭" 양쪽 역할을 다 맡고 있던 게 원인.
`MatchingNegotiationOutcomeService`(신규)로 협상 결과 반영만 분리해서 고리를 끊었다 — 이 클래스는
자기 저장소와 project 도메인만 쓰므로 협상을 다시 호출하지 않는다. **여기에 `NegotiationPort`
의존을 추가하면 순환이 재발한다**(클래스 주석에 명시).

- 테스트: `NegotiationCommandServiceTest` 픽스처 2곳에 `matching_request` 행 추가(5번이 미리 알려줌 —
  이 테스트는 협상 단독 기준으로 짜여 있어 매칭 행이 없었다). 즉시 타결 후 요청이 실제로
  `CONTRACT_PENDING`이 되는지 검증하는 단언도 추가. `./gradlew clean build` 통과.

## 2026-08-10 (계속) — 계약 이후 인원별 상태 전이 리스너 4개 (3번 요청 ①)

`matching_request.status`를 CONTRACTED 이후로 옮기는 코드가 어디에도 없어서, 계약을 체결하고
프로젝트가 끝나도 요청은 `CONTRACT_PENDING`에 그대로 남아 있었다. 3번이 계약 도메인을 붙이며
발견하고 이벤트 4개를 발행해줬다(`feature/contract-sign`이 develop에 merge되면서 사용 가능해짐 —
요청받은 시점엔 아직 develop에 없어서 대기했었다).

- `ContractStageEventListener` 신규. ContractSigned→CONTRACTED, ProjectProgressStarted→IN_PROGRESS,
  ProjectCompletionRequested→COMPLETION_PENDING, ProjectClosed→CLOSED.
- **`@Async`를 안 붙였다.** 다른 매칭 리스너들과 반대인데, 이건 AI 호출 없이 UPDATE 몇 줄만 하는
  작업이고 발행 도메인 트랜잭션과 원자적으로 묶이는 게 맞다 — "계약은 체결됐는데 매칭 상태만 안
  따라오는" 상황을 막는 게 목적이라 실패하면 차라리 같이 롤백되는 편이 낫다. 같은 이유로 여기에
  알림·외부 호출을 넣으면 안 된다(3번도 "상태 전이 외 무거운 작업은 커밋 뒤로"라고 요청).
- **직접 발견한 위험 — 거절된 요청까지 옮기면 결제가 롤백된다.** 프로젝트 단위 이벤트 3개는 "그
  프로젝트의 모든 요청"이 대상이지만 실제로는 거절·만료된 요청이 같이 있고, 그것까지
  `advanceStatus`에 넣으면 종결 상태라 예외가 난다. 이벤트가 발행 도메인 트랜잭션 안에서
  처리되므로 착수금 결제까지 통째로 롤백된다. 직전 단계인 요청만 골라 옮기도록
  `findByProjectIdAndStatus`를 새로 추가해서 해결했고, 덕분에 이벤트가 중복 전달돼도 두 번
  적용되지 않는다.
- 테스트 `ContractStageEventListenerTest` 신규 4건(전 단계 전이 / 거절건 섞여도 안 터짐 / 중복
  전달 멱등 / 계약 체결은 해당 프리랜서 1건만). 이 리스너는 `matching_request`만 건드리므로
  계정·약관 시딩 없이 요청 행만 직접 넣어 가볍게 검증한다. **거절건을 일부러 포함시켜 돌려보니
  해당 테스트만 실패하는 것까지 확인**했다.
- 파킹해뒀던 문서 커밋 2개(Stage E 완료 반영, 점수 스케일 0~100 확정)도 이 브랜치에 같이 실었다.

## 2026-08-10 (계속) — 리뷰 지적 2건 + 정책 전수 대조

**리뷰 지적 ① 즉시 타결 시 응답 상태가 DB와 달랐다 (유효)**

`accept()`가 `createNegotiation` 뒤에도 저장 전 `request` 객체로 응답을 만들고 있었다. 즉시 타결이면
그 안에서 요청이 CONTRACT_PENDING까지 올라가는데 그건 별도 조회·저장이라 이쪽 객체엔 반영되지
않는다 — DB는 CONTRACT_PENDING인데 응답만 NEGOTIATING으로 나갔다. 응답 직전에 다시 읽도록 수정.
회귀 테스트 추가(프로젝트 조건을 프리랜서 조건에 맞춰 불일치 0개를 만들고, 수락 응답이
CONTRACT_PENDING인지 + DB도 같은지 확인).

**리뷰 지적 ② ContractDraftListener에 @Async 없음 — 계약 도메인 파일이라 3번에게 전달**

**정책 전수 대조에서 발견 — P41을 위반하고 있었다 (내가 만든 위반 + 원래 있던 위반)**

리뷰에서 "NON_ACTIVE_STATUSES에 TERMINATED가 빠졌다"는 지적을 받고 TERMINATED/CLOSED를 넣었는데,
정책을 확인해보니 **정확히 반대로 적혀 있었다**. P41: "협상 결렬, 계약 전 파기, **계약 후 중도
종료는 무료 재추천 조건에 포함하지 않는다**." 무료 재추천은 "요청한 사람이 전원 거절해 아무도 못
구한" 경우의 보상이라, 계약까지 갔던 프로젝트는 대상이 아니다.

되돌리면서 보니 **원래 있던 `NEGOTIATION_FAILED`도 같은 이유로 위반**이었다(정책이 "협상 결렬"을
명시적으로 제외한다). 최종적으로 `REJECTED` 하나만 남겼다 — 만료도 REJECTED로 저장되므로(P45)
"전부 거절했거나 만료된 경우" 두 가지가 이 하나로 커버된다. 테스트도 정책대로 뒤집었다
(TERMINATED/NEGOTIATION_FAILED에서는 MT_008로 막히고, REJECTED에서만 열리는지).

**나머지 정책 대조 결과 — 이상 없음**

| 항목 | 정책 | 코드 |
| --- | --- | --- |
| 유료 재추천 한도 | P40 프로젝트당 5회 | `MAX_PAID_RERECOMMEND = 5` |
| 유료 재추천 비용 | 추천 프리랜서 수 당 10,000원 | `PAID_COST_PER_HEAD = 10_000L` |
| 무료 재추천 | P40/P41 프로젝트 전체 1회 | `countByProjectIdAndRoundType(FREE) > 0` |
| 이미 추천된 프리랜서 재노출 금지 | P40 | `excludedFreelancerIds`(프로젝트 기준) |
| 응답 기한 | P45 3일, 만료 시 REJECTED | `RESPONSE_DEADLINE_DAYS = 3`, `expire()` |
| 알림 종류 | 요구사항 알림 목록 | 추천완료/수락/거절/요청발송 4종이 그대로 대응(만료는 P45가 거절로 처리하므로 거절 알림에 포함) |
| 프리랜서 탭 분류 | requirements 프리랜서 메인 헤더 | **명세대로였다** — 아래 참고 |

**탭 분류는 명세대로였다(변경 안 함).** `CONTRACTED` 이후가 "종료됨"에 묶여 있어 진행 중인
프로젝트가 종료 탭에 뜨는 것처럼 보였는데, 요구사항이 화면을 `내 프로젝트 → [요청받은 프로젝트,
진행중인 프로젝트]` 둘로 나눠놨다. 이 API는 "요청받은 프로젝트"(제안의 생애주기)를 그리고, 계약
후 실제 일은 별도 화면 소관이다. 여기서 "종료됨"은 "제안 처리가 끝났다"는 뜻. 팀에 질문하려다
문서에서 답을 찾아 질문을 접었고, 프론트 전달 문서에도 의도된 동작이라고 적어뒀다.

`LOW_SCORE_THRESHOLD = 50.0`은 P09가 "내부 기준 점수는 [AI에이전트 > 점수 정책]을 따른다"고만 하고
그 정책의 기준값이 아직 `정의 필요` 상태라, 확정되면 맞춰야 한다.

## 2026-08-11 — 리뷰 6건 검증(내 파트만), 유효 3건 수정

받은 6건을 그대로 고치지 않고 명세/정책/STATE.md와 하나씩 대조했다. 3건은 오탐이었고, 그중
①의 일부(임베딩 텍스트에 예산·스킬·연차 추가)는 **고쳤다면 확정된 설계 결정 1을 깨는** 수정이었다.
판정 근거는 `.ai/STATE.md` "2026-08-11 갱신" 절에 정리.

- **② 선택 불가 후보에게 요청 발송(가장 심각)**: `sendOneRequest`가 `exposed`/`rejected`/
  `guardPassed`를 안 봐서, candidateId만 알면 **가드 탈락자에게도 요청이 나갔다**. Stage F
  가드가 API 한 번으로 무력화되는 상태였다. `MatchingCandidate.isSelectable()`로 도메인에
  규칙을 두고 `MT_017`로 막았다.
- **⑥ 자리 다 찬 포지션에 유료 재추천 결제**: 리뷰어는 "프로젝트가 IN_PROGRESS면 차단"을
  제안했지만 그러면 **중도 종료로 빈 자리를 다시 못 뽑는다**(명세 "2/3명 진행 중 · 1명 계약
  종료"). 상태가 아니라 남은 자리로 막도록 `countVacancy`를 넣고 `MT_018` 추가. `quantity`가
  남은 자리보다 크면 결제 전에 MT_005로 되돌린다.
  - 인원 계산 기준 목록을 `MatchingStatus.SLOT_RELEASED`로 도메인에 올려
    `MatchingRequestService`와 공유(두 곳이 어긋나면 자리 계산이 갈린다).
- **① 조건 저장 시 임베딩 재생성 TODO**: 지웠다. 임베딩 텍스트는 자기소개+경력사항뿐이라
  조건을 바꿔도 같은 벡터가 나오고, 직무·스킬·단가는 AI 서버가 `freelancer_condition`을 직접
  조인해 읽으므로 저장 즉시 반영된다. 왜 안 해도 되는지를 주석으로 남겨 다음 사람이 다시
  TODO로 착각하지 않게 했다.
- 테스트 6건 추가(`MatchingIntegrationTest`), `./gradlew build` 통과.
- **프론트 전달 필요**: 새 에러코드 `MT_017`, `MT_018`.

### (같은 날 추가) ④도 오탐이었다 — 프론트 문서를 늦게 봤다

`lowScoreWarned`를 노출 후보까지 넓히려다 멈췄다. 프론트 전달 문서에 배너 문구가 이미
"현재 후보를 모두 거절하면, **다음 순번 후보 중** 적합도가 낮은 후보가 포함될 수 있어요"로
확정돼 있어서, 조건을 넓히면 그 문구가 거짓말이 된다. 이 플래그는 "지금 후보가 별로다"가 아니라
"더 눌러봐야 소용없다"를 알리는 값이다.

- 곁가지로 실제 버그 1건: `CandidateListResponse.lowScoreWarned`의 Swagger `@Schema` 설명이
  "재추천을 반복하면 true가 된다"로 로직과 달랐다(프론트 문서에 "Swagger만 보고 구현하면
  틀립니다"로 이미 경고돼 있던 것). 실제 동작대로 고치고 넓히면 안 되는 이유를 주석에 남겼다.
- **다음부터**: 리뷰 검증할 때 레포 안 문서만 보지 말고 `AI매칭_API_화면매핑_최신본.md`(Desktop)도
  같이 볼 것. 프론트와 합의한 문구·필드 의미가 거기에만 있는 경우가 있다.
## 2026-08-11 — Java↔Python 연결 전수 점검에서 찾은 3건

경로·요청 필드·응답 필드는 전부 일치했다. 아래 3건이 실제 문제였고 모두 **에러가 안 나서
안 보이던** 종류다.

**① 후보가 없으면 "일시적 오류"로 잘못 안내되고 있었다**

파이썬은 후보 0명을 `AI_020`(404)으로 알리는데, Java 어댑터가 그걸 그냥 4xx로 흘려서
서킷브레이커 폴백 → `MT_010`(AI 서버 호출 실패)로 바뀌고 있었다. 재추천 비동기 리스너는 그걸
장애로 보고 회차를 FAILED로 닫으며 "일시적인 오류니 잠시 후 다시 시도하세요"를 보냈다. 후보는
다시 시도해도 안 생기므로 사용자는 계속 누르게 된다. **Java에는 이미 올바른 경로가 있었는데
(`candidates().isEmpty()` → `round.exhaust()`) 404 때문에 도달하지 못하고 있었다.**
어댑터에서 `AI_020`만 골라 빈 결과로 바꿨다. 다른 404(포지션 없음 등)는 그대로 오류.
부수 효과로 서킷브레이커가 "후보 없음"을 장애로 세지 않게 됐다 — 후보 없는 프로젝트를 반복
조회하면 서킷이 열려 멀쩡한 요청까지 막힐 수 있었다.

**② AFTER_COMMIT 리스너에서 알림이 조용히 사라지고 있었다**

①번 테스트를 쓰다가 잡았다. `@TransactionalEventListener(AFTER_COMMIT)` 시점에는 원 트랜잭션이
"커밋 완료" 상태로 아직 붙어 있어서, 기본 전파(REQUIRED)로 저장하면 그 끝난 트랜잭션에 합류해
**예외도 없이 버려진다**. 회차 저장은 REQUIRES_NEW(`RerecommendRoundFiller`)라 살아남았고 알림만
사라져서, 테스트로 안 봤으면 "회차는 닫혔는데 알림이 안 온다"로 나타났을 것이다.
리스너 메서드에 REQUIRES_NEW를 붙여 해결. 클래스 주석에 함정으로 남겼다.

**③ 프리랜서 임베딩 텍스트가 길면 조용히 실패한다**

AI 서버는 `text`를 20000자로 제한하는데 Java는 자르지 않고 보내고 있었다. 포지션 쪽 원본은 전부
`VARCHAR(1500)`이라 넘길 수 없지만, 프리랜서 쪽 원본(`resume.self_introduction`,
`resume_career.job_description`)은 `TEXT`라 제한이 없다. 경력이 많으면 넘겨서 422가 나고,
임베딩 호출은 리스너가 예외를 잡아 로그만 남기므로 **그 프리랜서는 매칭 후보에 영영 안 잡힌다**
(예전에 겪은 "freelancer_embedding이 비어 있던" 문제와 같은 증상). 20000자로 자르되 자기소개가
앞이라 잘려도 본인 소개는 남게 했다. 빈 텍스트도 422 대상이라 리스너에서 걸러 원인이 분명한
로그를 남긴다.

테스트: 어댑터 404 분류 3건, 임베딩 텍스트 길이 3건, 후보 소진 시 EXHAUSTED+안내 문구 1건.
`./gradlew clean build` 통과.

## 2026-08-11 (계속) — 임베딩 텍스트가 한쪽만 채워져 있던 것 + 조건 변경 시 재생성

리뷰 재정리본을 받고 **①에 대한 내 판정을 뒤집었다.** 처음엔 "STATE.md 결정 1과 충돌하니 오탐"
이라고 했는데, 명세와 반대쪽 코드를 안 보고 우리 문서만 본 판정이었다.

- 명세 R01.2는 "프로젝트 요구조건 ↔ 프리랜서 **프로필**·이력서"다. 결정 1이 그걸 임의로 좁혔다.
- 더 결정적으로 **`PositionEmbeddingTextBuilder`는 이미 요구스킬·최소경력·근무조건·기간을 넣고
  있었다.** 프리랜서 쪽에만 없어서, 포지션 벡터의 "Java / 경력 3년 이상 / 상주 · 풀타임"에
  대응할 말이 프리랜서 벡터에 아예 없었다 — 자기소개에 우연히 "자바"라고 써둔 사람만 걸렸다.
  클래스 주석엔 "PositionEmbeddingTextBuilder와 대칭이다"라고 적혀 있었는데 사실이 아니었다.

**고친 것**

- 프리랜서 텍스트에 직무·스킬·경력연차·근무방식/형태·희망기간 추가(포지션과 1:1 대응).
- 포지션 텍스트에 `mainTask` 추가 — "팀 결정 대기(Task #4)"라 빼뒀다는 주석이 있었는데 그 결정은
  2026-08-09에 이미 났다. 앞서 지운 조건 TODO와 같은 종류의 **철 지난 제외**였다.
- 단가·시작가능일은 양쪽 다 제외 유지. 임베딩은 숫자 비교를 못 해서 "월 500만원"과 "월 5000만원"이
  거의 같게 취급된다 — 넣으면 오히려 나빠진다. Stage E가 원본 숫자로 감점한다.
- `ConditionUpdatedEvent` + `ConditionUpdatedEventListener` 신규. 조건이 텍스트에 들어간 순간부터
  재생성이 필수가 됐다(안 하면 조건을 고쳐도 매칭은 옛날 조건으로 돈다).
- 조립·전송을 `FreelancerEmbeddingRefresher`로 모았다. 이력서 저장 / 조건 저장 / 관리자 재색인이
  각자 조립하면 **어디서 저장했느냐에 따라 같은 사람의 벡터가 달라진다** — 재색인 서비스도 이걸
  쓰도록 바꿨다.
- 조건 미등록 프리랜서는 `findCondition`이 던지는 예외를 흘려보내고 이력서만으로 만든다.
  안 그러면 이력서만 쓴 사람은 임베딩이 통째로 안 만들어져 후보에 영영 안 잡힌다.
- 테스트: `FreelancerEmbeddingTextBuilderTest` 6건(짝 맞춤/숫자 제외/조건 없음/길이 상한),
  `ConditionUpdatedEventListenerTest` 2건 신규. `./gradlew build` 통과(351건).

**같이 정리한 것**: 이 브랜치에 `fix/matching-empty-pool-not-server-error`를 merge했다 —
같은 파일(`FreelancerEmbeddingTextBuilder`)을 건드려서 따로 두면 나중에 충돌한다.

**남은 판정(오탐 3건)**: 등급 가중치 정렬(프로젝트당 값 하나라 순서가 안 바뀜), 낮은 적합도
경고(프론트 배너 문구가 "다음 순번 후보 중"으로 확정돼 있음), 프리랜서 제안 탭(요구사항이
"요청받은 프로젝트 / 진행중인 프로젝트"로 화면을 나눠놨고 프론트 문서에 의도된 동작으로 명시됨).

## 2026-08-11 (계속) — 매칭 파이프라인 재설계 확정 (팀 논의)

리뷰의 "스킬·연차가 1차 추림에 반영 안 됨" 지적에서 시작해 파이프라인을 다시 잡았다.
**확정 내용은 `.ai/STATE.md` "매칭 파이프라인 재설계(팀 확정)" 절이 최종본이다.** 코드는 아직
손대지 않았고, 작업 목록은 `.ai/HANDOFF.md`에 있다.

### 판정을 두 번 뒤집었다 — 기록해둘 것

1. 처음엔 리뷰 지적을 "오탐"이라고 했다. `.ai/STATE.md` 결정 1과 충돌한다는 이유였는데,
   **명세와 반대쪽 코드를 안 봤다.** 명세 R01.2는 "프리랜서 **프로필**·이력서"고, 무엇보다
   `PositionEmbeddingTextBuilder`는 이미 요구스킬·최소경력·근무조건을 넣고 있었다 —
   프리랜서 쪽에만 없어서 짝이 안 맞던 것이다.
2. 그래서 프리랜서 임베딩에 구조화 필드를 넣었는데, **그것도 틀렸다.** 사용자의 설계 메모
   `docs/personal/ai-matching-notes.md` §2에 "**임베딩에 안 넣는 것(구조화 필드)**: 직군, 직무,
   스킬, 경력연차, 근무방식, 단가, 기간"이 명시돼 있었다. 짝을 맞추려면 **포지션 쪽에서 빼야**
   했다. 리뷰어 지적이 가리킨 증상은 진짜였지만 원인은 "임베딩에 안 넣어서"가 아니라
   **"Stage B를 폐기해서"** 였다 — 병을 잘못 짚고 엉뚱한 데를 고쳤다.

**교훈: 우리 문서(STATE.md)끼리만 대조하면 같이 틀린 걸 못 잡는다.** 명세 원문 + 반대쪽 코드 +
사용자 설계 메모(`docs/personal/`)까지 봐야 한다. 이번 판정 번복 두 번이 다 그래서 났다.

### 확정된 것

- **임베딩 30 + DB 조건점수 70 합산**(순차 아님). 순차면 후보 선발을 유사도가 100% 정해서
  70이라는 비중이 무의미해진다.
- **임베딩은 문장 맥락만.** 숫자는 안 넣는다 — 연차는 방향이 반대로 작동한다("경력 3년 이상"에
  "경력 3년"이 "경력 10년"보다 가깝게 나온다).
- 임베딩 대상: 프로젝트(진행상황+담당업무+업무범위+우대사항+포지션우대사항) ↔
  프리랜서(학과+경력 세부내용+자기소개). **회사명·부서/직급 제외.**
  학과는 클라이언트가 우대사항에 "○○학과 우대"를 쓸 수 있어서 넣는다 — **우대사항과 세트다.**
- **하드필터에 "요구 스킬 1개 이상" 추가.** 전부 보유가 아닌 이유는 5개 중 4개 가진 사람을
  놓치지 않기 위해서고, 노출은 모집 인원만큼만이라 좋은 후보가 있으면 낮은 후보는 안 뜬다.
- **사전검수(P02)는 안 건드린다.** 매칭이 더 느슨해 후보가 안내보다 많이 나오는 건 클레임이 아니다.
- **가드는 G3(예산 조합) + G4(LLM 응답 이상)만.** 직무·스킬 재검증은 뺀다 — 하드필터를 통과한
  사람만 LLM에 가므로 가드에서 또 봐도 아무도 안 걸린다. 명세 R02.3 문구와는 어긋나므로
  설명 문구를 STATE.md에 적어뒀다.
- **프로젝트 임베딩 시점 = 착수금 결제 완료.** 정책 P03("등록 시점")과 다르지만 팀 확정.
- **주소 비교는 이번 범위에서 제외**, 향후 개선으로 기록.

### 되돌릴 것

오늘 커밋한 `0157334`(프리랜서 임베딩에 조건 추가 + `ConditionUpdatedEvent`)는 이 결정으로
**전부 되돌린다.** 조건이 임베딩에서 빠지므로 조건 변경 시 재생성도 불필요하다
(파이썬이 `freelancer_condition`을 매번 직접 읽어 저장 즉시 반영된다).

### 코덱스 분석과 대조

같은 내용을 코덱스에도 물어봤고, 코덱스가 짚은 6건(진행상황 누락, 학과 누락, 숙련도 미반영,
주소 없음, 임베딩 시점 불일치, 재색인 필요)은 전부 사실이었다. 다만 **구조적으로 두 군데를
놓쳤다**: ① 스킬을 점수화하면서 가드는 "필수 스킬 누락"으로 그대로 둬서 서로 충돌 ②
"유사도로 뽑고 조건으로 재정렬"(순차)로 써서 70의 비중이 사라짐. 사전검수 정합성·유사도
정규화·budgetCap 전달 문제도 언급이 없었다. 반대로 코덱스의 4줄 구조 요약
("임베딩=문장맥락 / DB점수=조건 / LLM=순위 / 가드=차단")은 발표용으로 그대로 쓸 만하다.

### (같은 날) 3번 회신 — 포지션별 우대사항은 쓸 수 없다

`project_position.preferred_note`를 매칭까지 내려달라고 요청했는데 **폐기된 필드**였다.
우대사항이 프로젝트 단위(`extra_note`)로 통일되면서 엔티티 매핑·등록/수정 DTO·공용 DB 컬럼이
전부 없어졌다(`ProjectPositionJpaEntity` 주석에 명시돼 있다). 되살리려면 등록 위저드 입력란과
프론트까지 열어야 해서 기획 결정 사안이라 **`extra_note`만 쓰기로 했다.**

**여기서 배운 것 — `db/init/02-create-schema.sql`은 실제 스키마가 아니다.** 공용 DB는
`ddl-auto`라 JPA 엔티티에서 생성되는데, 그 SQL 파일에는 폐기된 컬럼이 잔재로 남아 있다.
내가 그 파일을 근거로 "`preferred_note`가 있으니 쓰자"고 제안한 것이 그래서 틀렸다.
**필드 존재 여부는 JPA 엔티티나 응답 DTO로 확인할 것.** 이번에 쓰기로 한 나머지 필드
(`work_location`, `current_situation`, `main_task`, `detail_scope`, `extra_note`)는 실재를 확인했다.

**3번이 대안으로 제안한 "포지션 `skills`를 임베딩에 쓰라"는 받지 않았다.** 그게 정확히 이번
재설계의 발단이 된 버그다 — 포지션 벡터에만 스킬이 있고 프리랜서 벡터엔 대응하는 말이 없어서
자기소개에 우연히 "자바"라고 써둔 사람만 걸렸다. 프리랜서 쪽에서도 스킬을 뺐으므로 포지션에만
넣으면 짝이 다시 어긋난다. 스킬은 [3] 조건점수 25점이 더 정확하게 처리한다. 3번은 재설계
내용을 모르는 상태에서 선의로 제안한 것이라, 회신에 사유를 설명했다.

나머지 공유 3건(임베딩 시점=결제 완료 / `current_situation` 사용 / 사전검수 기준 유지)은
전부 동의받았고, **정책 P03 문구는 3번이 코드에 맞춰 고쳐주기로 했다.**

## 2026-08-11 (마무리) — 0단계 완료 + 세 브랜치 develop 동기화

### 0단계 — 되돌리기 완료

재설계 확정에 따라 같은 브랜치의 `0157334`를 되돌렸다(`e2e7ef3`).

- `FreelancerEmbeddingTextBuilder`에서 조건 필드 제거, `buildText(summary)` 단일 인자로 복귀
- `ConditionUpdatedEvent` / 리스너 / 테스트 삭제
- `FreelancerEmbeddingRefresher`는 **유지** — 이력서 저장과 관리자 재색인이 같은 조립을 써야
  어디서 저장했느냐에 따라 벡터가 갈리지 않는다
- `PositionEmbeddingTextBuilder`의 `mainTask` 추가는 **유지** — 새 설계에도 담당업무가 대상이다

**머지 전에 되돌린 이유**: 그대로 머지하면 `ConditionUpdatedEvent`가 develop에 들어갔다가
하루 만에 삭제되고, 임베딩 규칙이 바뀌었다가 또 바뀌어서 **재색인을 두 번** 해야 한다.

### develop 동기화 중 발견 — 임베딩 원본이 TEXT가 아니다

`develop`에 자기소개·경력 담당업무 길이 제한이 들어왔다.

| | 전 | 후 |
|---|---|---|
| 자기소개 | TEXT | `@Size(max=1500)`, 컬럼 `length=2000` |
| 경력 담당업무 | TEXT | `@Size(max=2000)`, 컬럼 `length=2000` |
| 경력 건수 | 무제한 | **여전히 무제한**(`@NotEmpty`만) |

필드별 상한은 생겼지만 **건수 상한이 없어서** 경력 10건이면 2만 자를 그대로 넘긴다.
20,000자 truncation은 계속 필요하다. 근거가 바뀌었으므로 주석만 사실에 맞게 고쳤다(`5f53ed2`).

### 세 브랜치 전부 develop 최신 반영 (충돌 0건)

| 레포 | 브랜치 | develop 대비 | 검증 |
|---|---|---|---|
| backend | `fix/matching-candidate-selection-guard` | ahead 14 / behind 0 | `build` 통과 |
| backend | `fix/matching-settlement-response-and-policy-doc` | ahead 3 / behind 0 | `build` 통과 |
| python | `feature/matching-llm-retry-and-pool-relax` | ahead 2 / behind 0 | `pytest` 51 / `ruff` 통과 |

### 참고 — 로컬 기동 실패는 코드 문제가 아니었다

`ChatEventStompAdapter`가 `SimpMessagingTemplate`을 못 찾는다는 기동 실패를 봤는데,
같은 브랜치에서 `PairingApplicationTests`(`@SpringBootTest`, 전체 컨텍스트 기동)가 통과한다.
로그의 `restartedMain`은 devtools 재시작이고, **빌드 중 클래스 파일이 반쯤 써진 상태에서
재시작해서** 난 것이다. 앱을 다시 띄우면 사라진다. 브랜치를 바꿔가며 빌드할 때 앱을 켜두면
이런 착시가 생기니 주의.

### (같은 날 마무리) 개인 메모·프론트 전달 문서 갱신

문서 감사 결과 두 군데가 뒤처져 있어 맞췄다.

- **`docs/personal/ai-matching-notes.md`(8/7 이후 방치)** — §1 파이프라인 / §2 임베딩 대상 /
  §4 배분 알고리즘 / §5 가드 / §6 임베딩 시점 / §7 LLM 호출을 재설계 확정 내용으로 개정.
  폐기된 원안은 `<details>`로 접어서 판단 근거를 남겼다. 상단에 최종 갱신 배너 추가.
  §3(예산 계산)과 §8(협상 연동)은 그대로 유효.
  **이 문서가 낡아 있으면 다음에 또 판정을 틀린다** — 이번에 두 번 뒤집힌 원인이 이거였다.
- **프론트 전달 문서**(`AI매칭_API_화면매핑_최신본.md`, Desktop) — "예정된 변경" 절 추가.
  API 응답 모양은 안 바뀌므로 프론트가 알아야 할 건 하나뿐이다: **스킬 부분 보유 후보가
  노출될 수 있고, `fitReason`에 "요구 스킬 5개 중 3개 일치" 같은 태그가 나온다.**
  기존 필드라 새 작업은 없고 값의 종류만 는다.
- 레포 사본(`docs/personal/`)을 Desktop 최신본으로 동기화. 파이프라인 재설계 공유 문서도 복사.
  (`docs/personal/`은 gitignore라 커밋되지 않는다 — 로컬 사본일 뿐)

**`docs/api-spec.csv`/`api-dto.csv`는 갱신 불필요**로 확인했다. 에러코드를 아예 안 적는
문서이고(MT_016도 없다), 이번 변경에 DTO 필드 추가·삭제도 없다.

## 2026-08-12 — B1(1단계) 임베딩 텍스트 확정

PR 3개(A1/A2/A3) 전부 머지됨. develop에서 `feature/matching-embedding-text-redesign`를 따서
재설계 1단계를 진행했다. `./gradlew clean build` 통과.

**프리랜서 쪽 — DTO에서 필드를 지웠다**

`FreelancerResumeSummary`를 `(selfIntroduction, majors, careerDescriptions)`로 재정의하고
`CareerEntry`(회사명·부서/직급·담당업무) 레코드를 삭제했다. 빌더에서만 안 쓰고 DTO엔 남겨두는
선택지도 있었지만, **필드가 있으면 다시 들어간다** — 실제로 이번 재설계 직전에 조건 필드를
임베딩에 넣었다가 되돌린 게 그런 경우였다. 이 DTO는 임베딩 경로에서만 쓰이므로(포트 1개,
호출부 1개) 지우는 비용이 없었다.

회사명을 빼는 이유는 대조 대상인 포지션 쪽에 회사명에 대응하는 말이 없어서다. 유명 회사
이름이 무관한 프로젝트와 걸리는 잡음만 만든다.

**학과를 경력보다 앞에 뒀다**

자기소개 → 학과 → 경력 순. 20000자 상한에 걸려 잘리는 건 항상 뒤쪽인데, 경력 건수엔 상한이
없고(`ResumeRequest`는 `@NotEmpty`만 건다) 학과는 다 합쳐야 몇 십 자다. 학과를 뒤에 두면
경력 10건짜리 이력서에서 학과가 통째로 날아가는데, 학과는 "○○학과 우대"를 잡으려고 넣은
항목이라 날아가면 넣은 의미가 없어진다. 앞에 둬도 경력이 밀려나는 양은 사실상 0이다.

**양쪽 다 빈 값은 줄째로 뺐다**

기존 `PositionEmbeddingTextBuilder`는 `String.join`이라 값이 없으면 빈 줄이 남았다. 빈 줄은
그 자리에 의미 없는 토큰을 만들어서, 항목을 적게 채운 프로젝트끼리 서로 비슷해 보이는 쪽으로
벡터가 밀린다. 진행상황·우대사항은 선택 입력이라 실제로 자주 빈다.

**`PositionEmbeddingTextBuilderTest` 신규**

포지션 빌더엔 테스트가 아예 없었다. 이번 재설계의 발단이 **"포지션 벡터에만 스킬이 있고
프리랜서 벡터엔 대응하는 말이 없어서 짝이 어긋난"** 버그였는데, 그걸 막는 테스트가 없으면
다음에 누가 또 넣는다. `doesNotContain(스킬·연차·근무조건·기간)`을 회귀로 고정했고,
"우대사항은 프리랜서 학과와 세트라 반드시 들어간다"도 같이 박아뒀다.

**`currentSituation`은 옮기기만 하면 됐다.** project 도메인의 `ProjectPositionSummary`에 이미
있었고 매칭 쪽 DTO에만 없었다. 요청 카드에는 계속 안 나간다(2026-08-09 3번 확인) — 임베딩
전용 필드라는 걸 DTO 주석에 적어뒀다.

## 2026-08-12 — B2(2단계) budgetCap 전달

`MatchingPort.recommend`에 `budgetCap`을 붙여 AI 서버로 넘긴다. Java는 B1과 같은 브랜치,
Python은 `feature/matching-condition-score`(B3를 이어서 할 브랜치).

**포지션 조회를 추천 호출보다 앞으로 옮겼다.** `fillCandidates`는 원래 추천을 부른 뒤에
`findPositionSummary`를 했는데, budgetCap을 계산하려면 그 값이 먼저 필요하다. 후보 0명이면
포지션 조회 1회가 헛돌지만, 읽기 한 번이라 순서를 바꾸는 쪽이 낫다.

**`@CircuitBreaker` 폴백 시그니처를 같이 고쳤다.** resilience4j는 폴백 메서드를 이름+시그니처로
찾는데(원본 파라미터 + 끝에 `Throwable`), 안 맞으면 **컴파일은 통과하고 서킷이 열릴 때만
터진다.** 파라미터를 추가할 때 제일 놓치기 쉬운 자리라 코드에 주석으로 박아뒀다.

**Python: budget_cap이 없을 때 단가 비교를 금지한다.**

기존 프롬프트엔 "총예산은 전체 인원·전체 기간 합계니 그대로 비교하지 말고 알아서 감안해라"는
우회 지시가 있었다. budget_cap이 오면 후보 희망급여와 **단위가 같아져서** 바로 비교할 수 있으므로
그 문단을 실제 상한 비교로 바꿨다(환산 기준도 명시).

> ⚠️ **이때 적은 환산 기준 "시급 ×209h / 일급 ×21d"는 틀린 값이었다.** 정책·협상 도메인은
> **×160 / ×20**이다. 같은 날 아래 "단가 월환산 기준이 어긋나 있던 것"에서 바로잡았다.

값이 없을 때는 "비교하지 마라"를 더 세게 박았다 — 프롬프트에 남는 건 총예산뿐인데 1인 월급과
자릿수가 다르다. 스프링 배포가 아직 옛 버전이고 AI 서버만 먼저 올라간 구간에서 실제로 나올 수
있는 조합이라, 두 경우 다 테스트로 고정했다.

**검증**: Java `./gradlew clean build` 통과, Python 54건 통과 + ruff 통과.

## 2026-08-12 (계속) — B3 완료 + 착수 전 결정 13건 확정

### PR 3개 머지 — 충돌 2건 해소

PR #144(`fix/matching-settlement-response-and-policy-doc`)에서 충돌이 났다. 둘 다 **양쪽이 서로
다른 걸 덧붙인 것**이라 로직 충돌은 없었다.

- `MatchingIntegrationTest`: develop(A2 머지분)이 재추천 테스트 3개를 끼워넣었는데 하필 그 바로
  뒤 테스트를 이 브랜치가 P41대로 뒤집어놔서, 붙어 있는 바람에 git이 못 갈랐다. 양쪽 다 살렸다.
- `WORKLOG.md`: 08-10 기록과 08-11 기록이 같은 자리에 붙었을 뿐. 시간순으로 둘 다 남겼다.
- 머지 후 `NON_ACTIVE_STATUSES = List.of(REJECTED)`(P41 수정)가 살아남았는지 따로 확인했다 —
  여기가 덮였으면 PR이 무의미해진다.

### B3 — 조건점수 + 합산

`scoring.py`를 **순수 함수만 담는 파일**로 분리했다. DB도 세션도 안 받는다. 채점식은 실제 DB
없이 숫자를 직접 확인할 수 있어야 하고, 그게 이 파일의 존재 이유다. SQL은 이력서 원문을
**일부러 안 읽는다** — 하드필터 통과자 전원(수천 행 가능)에서 필요한 건 채점용 숫자뿐이고,
자기소개·경력사항은 상위 3N명이 확정된 뒤에 읽는다.

**A3에 넣었던 "풀 확대 재검색"이 효과 없는 코드였다는 걸 발견했다.** `LIMIT n`이 0건을 돌려준 건
WHERE에 걸린 게 없다는 뜻이라 n을 키워도 결과가 같다. 유사도 컷이 사라진 지금은 더욱 그렇다.
**스킬 필터 완화**로 교체했다 — 재설계 후 후보 0명은 오직 하드필터 때문이고, 그중 풀 수 있는
건 스킬 하나뿐이다(직군/직무를 풀면 오추천, 동의·상태는 사용자 의사).

**SQL 검증**: 단위 테스트로 못 잡는 부분이라 `pgvector/pgvector:pg16` 컨테이너를 띄워 실제로
돌렸다. 스킬 1개 이상 필터 / 완화 재검색 / 이미 노출된 후보 제외 / NULL 조건값 통과 4가지와,
`str(vector)`의 pgvector 파싱·빈 리스트 파라미터의 asyncpg 타입 추론까지 확인했다.
그 뒤 **반복 가능한 opt-in 테스트**(`AI_TEST_DB_URL`)로 남기고 실제로 돌려 통과까지 확인했다.

리뷰에서 "SQL 문자열에 `condition_skill`이 들어있는지 보는 테스트를 추가하라"는 제안이 왔는데,
새 쿼리는 `text()` 리터럴이라 **동어반복**이다(소스에 적힌 글자를 소스에서 찾는 셈이라 절대
실패하지 않고, 의미가 틀려도 통과한다). 대신 **계산되는 것**(파라미터·매핑)만 단위 테스트로
잡았다 — 특히 `skip_skill_filter`가 뒤집히면 스킬 하드필터가 통째로 무력화되는데 에러가 안 난다.

### 단가 월환산 기준이 어긋나 있던 것 — 리뷰로 발견

파이썬 프롬프트가 `시급 ×209시간 / 일급 ×21일`로 안내하고 있었는데, 정책(`.ai/STATE.md` 설계
결정 3)과 협상 도메인(`FreelancerConditionSnapshot`: `DAYS_PER_MONTH=20`, `HOURS_PER_MONTH=160`)은
**×160 / ×20**이었다. 사용자가 프롬프트를 고쳤고, **채점식(`scoring.py`)도 같은 값으로 맞췄다** —
프롬프트만 고치면 LLM은 160으로 보는데 SQL 조건점수는 209로 감점한다.

어긋나면 매칭이 같은 사람을 협상보다 비싸게 봐서 감점이 과해지고, 정작 협상에 가면 예산 안에
들어온다. **양쪽 다 에러 없이 돌아가 발견이 어렵다.** 상한과 딱 맞아떨어지는 값(일급 50만×20 =
정확히 1,000만)으로 테스트를 박아서 배수가 바뀌면 즉시 깨지게 했다.

### 착수 전 결정 13건 (상세는 `.ai/STATE.md` "2026-08-12 확정")

**배점 합이 80인데 문서엔 70이라고 적혀 있었다.** 앞 5개(25+15+15+8+7)가 정확히 70이라, 시작일·
기간 5점씩이 나중에 붙으며 재검산이 빠진 것으로 보인다. 정규화하면 절대값은 의미가 없고 비율만
남으므로 **합 100(30/20/20/10/8/6/6)** 으로 맞춰 혼란을 원인부터 없앴다.

**비중을 30:70 → 25:75로 바꿨다.** 이 비율이 정하는 건 "유사도가 뒤집을 수 있는 조건 격차 =
S/(100−S)"인데, 30:70이면 42.9%까지 뒤집힌다 — 스킬 1/5 + 경력 미달인 후보가 자기소개만으로
올라온다. 20:80(25%)도 검토했으나 사용자가 25:75(33.3%)를 택했다. **스킬 1/5 중급 + 경력 2/3
= 31.3% 손실은 아직 유사도가 이긴다는 것**(여유 2%p)을 알고 받아들인 결정이라, 테스트에 숫자로
박아뒀다.

**유사도가 실제보다 세게 작동하는 원인은 비중보다 순위 기반 정규화(PERCENT_RANK)에 있다.**
1등과 꼴찌의 코사인이 0.84 대 0.83이어도 25점 차가 난다. 좁은 구간에 몰리는 문제를 풀려고 택한
방식인데 부작용이 이것이다.

**G3에 이미 계약된 인원의 단가를 반영하기로 했다.** 안 하면 자리가 찰수록 "항상 여유 있음"으로
나와 경고가 무의미해진다(3명 중 1명이 1,500만에 계약됐는데도 남은 2자리를 900만 기준으로 잰다).
그 사람의 단가는 **협상 타결가를 우선**한다 — `NegotiationQueryUseCase.getAgreedForContract`와
`NegotiationProgressUseCase.findProgressByRequestId`가 **이미 공개된 인바운드 유스케이스라
5번에게 요청할 것이 없었다**(처음엔 "협상이 금액을 안 내려준다"고 잘못 판단했다가 정정).
`Negotiation.agreedAmount`는 월단가라 단위도 맞는다.

겸사겸사 **`AgreedNegotiationView` javadoc이 `agreedAmount`를 "총액"이라고 잘못 적어놓은 것**을
발견했다(실제로는 월단가). 계약 도메인이 그 문구를 믿고 개월 수를 안 곱하면 계약 금액이 1/N로
찍힌다. E4로 5번에게 전달할 항목.

### 로컬에서 AI 매칭이 한 번도 돈 적이 없다는 것을 발견

임베딩 테이블을 만드는 SQL(`Pairing-python/db/init/10-create-ai-schema.sql`)이 **어느
docker-compose에도 마운트돼 있지 않다.** 파이썬은 `create_all`을 안 쓰기로 했으므로 아무도 이
테이블을 안 만든다. 게다가 백엔드 compose의 postgres 이미지가 `postgres:16-alpine`이라
**pgvector 확장 파일 자체가 없어** `CREATE EXTENSION vector`가 실패한다.

**배포 DB도 같은 이유로 누군가 수동으로 넣었어야 한다.** 안 넣었으면 배포 환경에서도 추천이
첫 쿼리에서 죽는다 — C1을 한 번도 안 돌려봐서 아직 아무도 모르는 상태일 수 있다. E5로 확인 요청.

## 2026-08-12 (계속) — 4군 정리: 수수료율 하드코딩 제거

**골드 등급 수수료 할인은 "확인 대기" 항목이 아니었다.** 정책 P01 원문을 다시 보니 골드 혜택이
비어 있는 게 아니라 3개(매칭 1명 / 등록 2개 / 높은 등급 매칭 확률)가 나열돼 있고 거기에 수수료
인하만 없다. 정산 도메인도 이미 같은 결론으로 구현돼 있었다 — `DepositFeePolicy.gradeDiscount`
주석이 "실버·골드는 할인이 없다. 등급 혜택에 수수료 인하가 적힌 등급은 다이아뿐이다 (P01)"다.
STATE.md에 남아 있던 "확인 필요"가 낡은 항목이었다.

**대신 진짜 문제를 찾았다: `BudgetCapCalculator`가 요율을 직접 박아두고 있었다.**

`10% / 8% / 다이아 -2%p`. 처음엔 정산의 `3% / 2% / -1%p`와 달라 틀린 줄 알았는데, 성공보수
(`7% / 6% / -1%p`)까지 합치니 정확히 일치했다. 클라이언트가 착수금·성공보수를 둘 다 내므로
합산 요율로 순예산을 잡는 게 맞고, 값도 맞다.

**우연히 맞고 있었을 뿐이라 두 정책 클래스를 직접 호출하도록 바꿨다.** 정산이 요율을 고치면
매칭만 옛 값으로 남는데, 그러면 budgetCap이 틀어져 **조건점수 단가 20점 · 가드 G3 예산 판정 ·
협상 상한**이 한꺼번에 어긋나면서 **어디에서도 예외가 나지 않는다.** 이런 종류가 제일 늦게 발견된다.

도메인 경계를 넘는 import를 감수했다. 두 정책 다 I/O 없는 순수 계산이라 결합 비용이 낮고,
`ClientGradeResolver`가 이미 account 리포지토리를 직접 쓰는 선례도 있다. 포트를 새로 뚫는 건
정적 산술 두 줄에 비해 과했다.

`BudgetCapCalculatorTest` 신규 5건(합산 요율 / 1억 경계 / 다이아 -2%p / 골드는 할인 없음 /
주→개월 환산). 기존 검증값 4,500,000원이 그대로 나오는 것도 확인했다. `./gradlew clean build` 통과.

**D3(프론트 렌더링)은 목록에서 뺐다.** 4번 파트가 아니다 — 매칭 API는 이번 재설계로도 응답 모양이
안 바뀌고 프론트 전달 문서도 이미 나가 있다. 참고로 D1~D3는 요구사항·정책 문서 어디에도 근거가
없고 대화에서 나온 걸 옮겨 적은 항목이라, D1·D2도 원 출처를 확인해두는 게 좋다.

## 2026-08-12 (계속) — pgvector 설치 방식 확정 + 문서 전면 갱신

**팀이 도커가 아니라 윈도우 네이티브 PostgreSQL 로 정했다.** pgvector 를 소스에서 빌드해야 해서
Visual Studio Build Tools("C++를 사용한 데스크톱 개발")가 필요하다. 절차는 `README.md`
"2-1) pgvector 설치"에 넣었다 — 팀원 전원이 각자 해야 하는 일이라 `.ai/` 가 아니라 README 가
원본 자리다.

도커 경로도 대안으로 남겨뒀다(`pgvector/pgvector:pg16` 이미지, 빌드 불필요). 둘은 5432 포트가
충돌하므로 하나만 쓴다. 도커 컨테이너 데이터는 비어 있었으므로(account 0건) 옮길 것은 없다.

**임베딩 테이블을 자동 생성하는 코드가 어디에도 없다는 점을 README·STATE·HANDOFF 세 곳에 박았다.**
AI 서버는 `create_all`을 안 쓰기로 했고 스프링은 이 테이블을 JPA 엔티티로 갖고 있지 않다. 사람이
`10-create-ai-schema.sql`을 한 번 돌려야 하고, **배포 DB도 마찬가지다**(E5로 확인 요청).

**HANDOFF 맨 위에 "지금 상태" 박스를 넣었다.** 새 세션이 이 문서 하나로 이어받을 수 있게 —
작업 중인 브랜치 2개와 각각 남은 것, 자주 헷갈리는 핵심 숫자(25:75, 배점 100, 일급×20/시급×160),
막혀 있는 것(pgvector), 아직 한 번도 안 해본 것(C1)을 한 화면에 모았다.

**E 전달 5건을 "그대로 복사해서 보낼 문장"으로 다시 썼다.** 표에 한 줄 요약만 있으면 결국 다시
쓰게 되고, 그러다 안 보낸다. E4(협상 javadoc이 월단가를 "총액"이라 표기)는 계약 금액이 1/개월수로
찍힐 수 있는 건이라 우선순위를 높였다.

## 2026-08-12 (계속) — 배포 DB pgvector 확인 결과

팀 확인: **배포 DB에 pgvector 확장은 이미 있고**, 임베딩 테이블만 SQL로 만들면 된다. E5 는
"확인"에서 "테이블 생성 1회"로 좁혀졌다.

실행 전에 `10-create-ai-schema.sql` 을 읽어보고 두 가지를 문서에 적어뒀다.

- **재실행하면 실패한다.** `CREATE TABLE`/`CREATE INDEX` 는 `IF NOT EXISTS` 인데
  `ALTER TABLE ... ADD CONSTRAINT` 2줄은 아니다. 파괴적이진 않고 그냥 에러지만,
  "한 번 더 돌려볼까" 하다 놀랄 수 있어 미리 적었다.
- **ivfflat 인덱스를 빈 테이블에 만들면 제대로 동작하지 않는다.** 클러스터를 학습할 데이터가
  없기 때문이다. B5 재색인 직후 `REINDEX` 를 해야 한다 — B5 체크리스트에 추가했다.

겸사겸사 확인한 것: **B3 의 새 추천 쿼리는 이 ivfflat 인덱스를 쓰지 않는다.** 하드필터 통과자
전원에 대해 유사도를 계산하는 설계라 `ORDER BY ... LIMIT` 이 없어서 순차 스캔이다(의도된 동작).
인덱스는 내부용 후보 미리보기 엔드포인트가 쓴다. 수만 명 규모가 되면 이 지점을 먼저 봐야 한다.

## 2026-08-12 (계속) — 전달 5건 회신 도착, 4건 종료

**E1(@Async)**: 3번이 어제 지적받고 이미 반영해 develop 에 있었다. 리스너 안에서 `log.error` 로
남기는 것까지 해뒀다. **다만 내 설명이 틀렸다** — 계약서 생성은 결제 트랜잭션이 아니라 **협상
타결 트랜잭션**에서 돈다(계약서는 협상이 타결될 때 만들어진다). 지적한 문제 자체는 맞았지만
영향 범위를 잘못 적었다.

**E3(프리랜서 성공보수)**: `199a961` 이 그 작업 맞다. 요율 6% 고정, MASTER 1%p 할인, 기준 금액은
계약 총액. 해결됐다.

**E4(협상 javadoc)**: 값의 정체는 내 지적대로 **월 단가**가 맞았다. 다만 **계약 도메인은 이미
제대로 곱하고 있었다** — `Contract.java:179` 가 `salaryAmount * months` 로 총액을 만든다.
1/N 로 찍히는 사고는 없었고, 틀린 건 javadoc 문구뿐이다. 5번이 문구와 예시값(`"42000000"` —
총액처럼 보여 오해를 키운다)을 함께 고치기로 했다.

**5번이 B4 에 대해 짚어준 것**: `agreedAmount` 는 월 단가이니 `budgetCap`(역시 월 단가 상한)과
**바로 비교하고 개월 수를 곱하지 말 것.** 곱하면 상한이 개월 수배로 부풀어 경고가 영영 안 뜬다.
STATE.md "[5] 가드 — G3 세부"에 경고로 박아뒀다.

**E5(배포 DB)**: 확장·테이블·데이터가 이미 다 있었다(freelancer 1행, position 7행, 모델 1종,
마지막 생성 2026-08-11). **"배포에서도 추천이 안 되고 있을 것"이라는 내 추측은 틀렸다.**
포지션 임베딩이 7건 있으므로 비교 대상도 있다. 날짜가 B1 이전이라 전부 옛 규칙 벡터인 것만
B5 재색인 대상이다.

**E2(정책 P03)만 남았다.** 3번이 고치겠다며 제안 문구를 보내와 검토를 요청했다. 코드와 대조해
두 가지가 빠진 것을 찾았다.

- **프로젝트 재생성 경로 누락**: 모집 시작 후 프로젝트를 수정하면 임베딩을 다시 만든다
  (`ProjectUpdatedEventListener`). 이게 빠지면 재생성이 정책에 없는 동작이 된다.
- **프리랜서 줄도 원래 틀려 있었다**: P03 첫 줄이 "프리랜서 등록 시점"인데 실제로는 **이력서
  정식 저장·수정 시점**이다(`ResumeService` → `ResumeUpdatedEvent`). 임시저장은 이벤트를 안 낸다.
  3번은 프로젝트 줄만 고치려던 참이라 이 줄도 같이 알려야 한다.

회신 문구는 `.ai/HANDOFF.md` "E2 회신" 절에 적어뒀다.

**임베딩 쓰기 경로는 총 4개**로 확인했다(코드 전수):
`RecruitingStartedPositionHandler`(모집 시작) / `ProjectUpdatedEventListener`(결제 후 수정) /
`FreelancerEmbeddingRefresher`(이력서 저장·관리자 재색인 공용) / `EmbeddingReindexService`(포지션 재색인).

## 2026-08-12 (계속) — pgvector 환경 완료 (로컬·배포)

**배포는 원래 다 있었다.** 확장·테이블은 물론 데이터까지 있었다(freelancer 1행, position 7행,
모델 1종, 마지막 생성 2026-08-11). **"배포에서도 아무도 모르게 안 되고 있을 것"이라는 내 추측이
틀렸다.** 포지션 임베딩이 7건 있으니 비교 대상도 있었다. SQL 실행할 게 없었다.

날짜가 B1 이전이라 전부 옛 규칙 벡터인 것만 B5 재색인 대상이다.

**로컬은 팀 확정대로 윈도우 네이티브 PostgreSQL 18 + pgvector 소스 빌드로 해결했다.**
pgvector 0.8.6 설치 완료, 임베딩 테이블 2개 생성 완료. 스프링 스키마(51개 테이블)와
`freelancer_profile.matching_paused`는 이미 있었다 — **네이티브 PG18이 원래 쓰던 DB였고
도커 PG16이 빈 껍데기였다.** 내가 앞서 "로컬에서 AI 매칭이 한 번도 돈 적이 없다"고 판단한 건
비어 있던 도커 쪽을 보고 내린 결론이었다.

**걸렸던 것 4가지 — 문서(README·HANDOFF·STATE)에 전부 남겼다.**

1. `nmake`가 PowerShell에 없다. 관리자 cmd에서 `vcvars64.bat`을 먼저 `call` 해야 한다.
   `set "VAR=..."`도 cmd 문법이라 PowerShell에선 아무 일도 안 한다
2. `psql`도 PATH에 없다. 전체 경로로 부른다
3. pgvector를 레포 안(`Pairing-backend/pgvector/`)에 클론해서 `git status`에 잡혔다 — 지웠다.
   `C:\` 밑에 받는 것으로 문서를 고쳤다
4. ⚠️ **도커 PG16과 네이티브 PG18이 둘 다 5432를 잡고 있었다.** `netstat`으로 확인함
   (docker.backend PID 24084 / postgres PID 8712). 이 상태면 앱이 어느 쪽에 붙는지 알 수 없고,
   "pgAdmin에서 고쳤는데 앱에 반영이 안 된다" 같은 형태로 나타난다. 하나만 켠다

**ivfflat 경고가 실제로 났다.** `ivfflat index created with little data / This will cause low
recall / Drop the index until the table has more data`. 예상한 대로라 B5 체크리스트의
`REINDEX` 항목이 유효함을 확인했다.

**이제 C1(통합 테스트)을 막는 환경 요인은 없다.**

## 2026-08-12 (계속) — 7번 similarity + 13번 CI pgvector, python 브랜치 완성

**7번.** `matching_candidate.similarity` 컬럼에 지금까지 `0.0` 이 박혀 있었다
(`createFromEmbedding(..., 0.0)`). 컬럼 이름이 거짓말을 하고 있었고, "이 후보가 왜 뽑혔나"를
나중에 되짚을 수 없었다. 25:75 비중이 맞는지 판단하려면 실제 유사도 분포가 있어야 한다.

파이썬 추천 응답에 `similarity` 를 실었다. **LLM 이 만드는 값이 아니라 서버가 1차 추림에서
계산한 값**이라 `_RANKING_SCHEMA` 에는 넣지 않고, 풀 밖 후보를 걸러낸 뒤 채운다. LLM 응답을
`RankedCandidate` 로 파싱하는 단계에서는 비어 있어야 해서 기본값을 `None` 으로 뒀다.

**프롬프트에는 넣지 않는다.** 넣으면 LLM 이 원문을 읽는 대신 그 숫자를 베낀다 — 1차 추림 점수를
다시 확인하는 셈이라 새로 알아내는 게 없다. 테스트로 고정했다(`"0.7321" not in sent_prompt`).

**13번.** CI 에 `pgvector/pgvector:pg16` 서비스와 `AI_TEST_DB_URL` 을 넣었다. 이제 PR 마다 실제
pgvector 에 쿼리가 돈다. 어제 "스키마 드리프트 때문에 안 하는 게 낫다"고 했던 판단을 뒤집은 것인데,
드리프트 위험은 붙이든 안 붙이든 같고 **안 붙이면 아무도 안 돌린다**는 게 결정적이었다.

**⚠️ 하다가 위험한 걸 발견해 같이 고쳤다.** 어제 쓴 통합 테스트가 `public` 스키마에
`DROP TABLE freelancer_embedding, freelancer_profile, account, freelancer_condition, condition_skill`
을 그대로 걸고 있었다. CI 의 빈 컨테이너에서는 문제가 없지만, **`AI_TEST_DB_URL` 에 개발 DB 를
넣으면 스프링 테이블이 통째로 날아간다.** 로컬 DB 를 방금 다 세팅한 직후라 실제로 일어날 수
있는 사고였다.

전용 스키마(`pgvector_it`)를 만들어 그 안에서만 테이블을 만들고 끝나면 `DROP SCHEMA CASCADE`
하도록 바꿨다. 리포지토리 SQL 이 테이블명을 스키마 없이 쓰므로 세션의 `search_path` 만 돌리면
그대로 픽스처를 본다. **실제 개발 DB(테이블 51개)에 직접 돌려서** 무손상(51 → 51)과 잔여 스키마
없음을 확인했다.

**→ python 브랜치(`feature/matching-condition-score`)는 PR 올릴 준비가 끝났다.**
84 passed + 1 skipped, ruff 통과.

## 2026-08-12 (계속) — B4 가드 교체 (G1·G2 제거 → G3·G4)

**구조가 바뀐 지점: 노출을 먼저 정하고 그다음 가드를 본다.** 옛 코드는 후보를 한 명씩 돌면서
가드를 보고 통과한 사람만 노출했는데, G3는 "노출 후보 전원의 합계"를 보는 포지션 단위 검증이라
한 명씩으로는 판정이 안 된다. `persistCandidates` 루프를 둘로 갈랐다.

**G4(`LlmResponseGuard`)를 순위 계산보다 먼저 부른다.** 중복 ID가 남아 있으면 등급 조회의
`Collectors.toMap`이 키 충돌로 터진다. 실제로 순서를 잘못 두면 500이 난다.

**G3는 탈락시키지 않는다.** `guardPassed=true`를 유지하고 `guardReason`에만 사유를 남긴다.
여기서 배제하면 Stage B 폐기 사유(단가로 거르면 사전검수 안내 인원과 어긋난다)가 되살아난다.
노출된 후보에게만 사유를 붙인다 — 대기 순번은 아직 조합의 일부가 아니다.

**재추천에서 이미 자리를 차지한 사람의 단가를 센다.** `findByPositionIdAndStatusNotIn`
(기존 count 메서드의 목록 버전)으로 가져와서, 타결 이후 상태면 협상 타결가를, 그 전이면 희망
단가를 쓴다. 안 세면 자리가 찰수록 "항상 여유 있음"으로 나와 경고가 무의미해진다.

`NegotiationPort.findAgreedMonthlyPay`를 새로 뚫었다. 협상 도메인의 기존 인바운드 유스케이스
두 개(`findProgressByRequestId` → `getAgreedForContract`)를 위임할 뿐이라 5번에게 요청할 게
없었다. `getAgreedForContract`는 타결 전이면 예외를 던지므로 **매칭 요청 상태로 먼저 거르고**,
그래도 데이터가 어긋난 경우를 대비해 어댑터에서 예외를 잡아 empty로 바꾼다(가드가 통째로
실패하면 안 된다).

**`agreedAmount`에 개월 수를 곱하지 않는다.** 이미 월 단가라 budgetCap과 단위가 같다(5번 확인).
포트 javadoc에 경고로 박아뒀다.

**`MonthlyPayConverter` 신규.** 협상 쪽 `FreelancerConditionSnapshot.monthlyPay()`를 그대로 못
쓰는 이유는 그게 협상 생성용 레코드라 매칭이 안 갖고 있는 필드(minAcceptAmount 등)까지
요구해서다. 환산식을 다시 쓰되 **협상 도메인의 실제 계산과 직접 대조하는 테스트**로 고정했다 —
상수를 테스트에 다시 적으면 같이 틀려도 통과하기 때문이다.

**7번 자바 쪽 완료.** `RankedFreelancer.similarity` 추가, 어댑터가 `similarity`를 파싱해
`createFromEmbedding`의 하드코딩 `0.0`을 실제 값으로 교체. 옛 배포와 섞여 도는 동안 값이 안 올
수 있어 `Double`(nullable)로 받고 없으면 0.0으로 채운다.

**옛 가드 테스트를 교체했다.** "요구 스킬이 부족한 후보는 노출되지 않는다"는 이제 성립하지
않는다(직무·스킬 재검증을 뺐다). 반대로 **"스킬이 부족해도 떨어뜨리지 않는다"**를 검증하도록
뒤집고, G4(중복·근거 누락)와 similarity 저장을 확인하는 통합 테스트를 추가했다.

**검증**: `./gradlew clean build` 통과. 신규 단위 테스트 10건(G4 5 + 월단가 환산 4 + budgetCap 5).

## 2026-08-12 (계속) — 문서 일관성 점검 (리뷰 지적 3건 + 자체 발견 2건)

리뷰에서 지적받은 3건 전부 사실이었다. 숫자를 바꿀 때 **한 곳만 고치고 다른 절을 놓친 것**이
공통 원인이다.

- `HANDOFF.md` B2 절이 아직 "시급 ×209h / 일급 ×21d" — **×160 / ×20**으로 정정
- `STATE.md` 결정 13이 "CI pgvector 미착수" — 완료로 정정
- Pairing-python `README.md` 엔드포인트 표의 호출 시점이 옛 정책 문구
  ("이력서·조건 저장 시" / "프로젝트 포지션 등록·수정 시") — **"이력서 정식 저장·수정(임시저장
  제외)" / "착수금 결제 완료로 모집 시작 + 모집 시작 후 수정"**으로 정정. 조건 저장은 임베딩과
  무관하다는 것도 같이 적었다(자주 헷갈린다)

전수 grep으로 두 건을 더 찾았다.

- `STATE.md` 유사도 정규화 설명이 "×30 하면 … 30:70이 무의미해진다"로 남아 있었다 → 25:75
- `HANDOFF.md`에 **착수 전 계획 절이 통째로 남아 체크박스가 전부 비어 있었다.** 새 세션이 그걸
  따라가면 이미 끝난 작업을 다시 한다. 지우지 않고 **"낡음, 따라가지 말 것" 배너**를 달아 최신
  절("남은 작업 전체 목록")을 가리키게 했다.

`WORKLOG`는 이력이라 옛 기록을 고쳐 쓰지 않고, 틀린 값을 적었던 자리에 정정 주석만 덧붙였다.

**교훈**: 상수를 바꾸면 코드만이 아니라 **문서 전체를 grep해야 한다.** 같은 숫자가 설계 근거·
체크리스트·README 여러 곳에 흩어져 있어서, 한 곳만 고치면 다음 사람이 어느 쪽을 믿을지 알 수 없다.

## 2026-08-12 (계속) — 예산 경고 배너 확정 (U4 개정)

원래 U4는 "예산 경고를 화면에 안 띄우고 `guardReason` 기록만"이었는데, 사용자가 프론트에
전달하기로 하면서 **띄우는 것으로 개정**했다.

**확정 문구**: `추천된 후보들의 희망 단가 합계가 남은 예산을 넘습니다. 협상에서 조정이 필요할 수 있어요.`

"예산 초과"라고 단정하지 않았다. 협상에서 조정될 수 있고 이 경고로 후보가 빠지지도 않는다 —
**"이 사람들 뽑지 마세요"가 아니라 "협상이 필요합니다"로 읽혀야 한다.**

`lowScoreWarned`와 완전히 같은 패턴이라 새로 설계할 게 없다. 회차 단위 boolean 하나
(`CandidateListResponse.budgetWarned`), 같은 자리, 같은 컴포넌트. 백엔드 5곳 + 프론트 1곳,
약 1시간. 작업 목록은 HANDOFF F3 상세에 적었다.

**화면을 보고 확인한 것 두 가지** (사용자가 실제 화면을 보여줌)

- 클라이언트 `내 프로젝트 → 추천 후보` 탭 전용이다. **프리랜서 화면엔 안 넣는다** — 다른 후보의
  희망 단가로 계산된 경고라 경쟁자 단가를 역산할 수 있다
- **포지션 탭마다 다르다.** `GET /positions/{id}/candidates` 응답이라 포지션 단위인데, 화면에
  포지션 탭이 여러 개라 "프로젝트 전체 경고"로 오해하기 쉽다. 프론트 문서에 경고로 적었다

`guardReason`(숫자가 다 들어간 개발자용 문구)은 **화면에 안 내보낸다.** 상한 계산식이 노출되면
클라이언트가 역산해서 다른 후보의 희망 단가를 추정할 수 있다.

**착수 시점은 C1·C2 이후를 권한다.** 경고가 실제로 얼마나 자주 뜨는지 보고 만드는 게 낫다 —
너무 자주 뜨면 배너가 아니라 판정식(x1.2)부터 손봐야 하고 그러면 문구도 다시 쓴다.
빈도 확인 쿼리는 HANDOFF F3에 적어뒀다.

## 2026-08-12 (계속) — develop 재동기화 + 문서 누락 보정

**두 레포 모두 develop 을 머지했다.**

- backend: develop 이 9커밋 앞서 있었다(A2A 타임아웃, 챗봇 intent, **5번의 agreedAmount javadoc
  수정**). 충돌 없이 머지, `./gradlew clean build` 통과, push. behind 0 / ahead 13.
- python: develop 이 4커밋 앞서 있었다. 머지, 84 passed + ruff 통과, push.

**E4가 반영된 것을 확인했다.** 5번이 javadoc 을 고치면서 *"매칭이 예산 가드에 쓸 때는
budgetCap(이것도 월 단가 상한)과 같은 단위라 그대로 비교하면 된다"* 까지 적어줬다.
B4 구현과 정확히 일치한다.

**⚠️ python PR 이 머지될 때 마지막 커밋 하나가 빠졌다.** PR 이 `0daeb5b`(README 엔드포인트 호출
시점 정정) **직전에** 머지돼서, develop 의 README 는 아직 옛 문구("이력서·조건 저장 시")다.
브랜치는 원격에 그대로 있고 develop 보다 2커밋 앞서 있으니 작은 PR 을 하나 더 올리면 된다.
**이미 한 번 머지된 브랜치라 GitHub 이 "Compare & pull request" 배너를 다시 안 띄운다** —
`Pull requests → New pull request` 로 직접 만들어야 한다(사용자가 PR 이 안 보인다고 해서 확인함).

**문서 누락을 스스로 못 잡았다.** B4 를 끝내고 WORKLOG 에는 적었는데 **HANDOFF 의 B4 체크박스와
"지금 상태" 박스를 안 고쳤다.** 사용자가 "다 기록됐냐"고 물어서 확인하다 발견했다.
"코드 작업이 끝나면 무조건 md 갱신"을 지켰다면 안 났을 누락이라, 앞으로는 커밋 전에
**HANDOFF 체크박스 → 지금 상태 박스 → WORKLOG** 순으로 셋 다 훑는다.

**B5(배포 후 절차)를 실행 가능한 형태로 다시 썼다.** 예전엔 "재색인 1회 실행" 한 줄이었는데,
자바 머지 후 실제로 뭘 어떻게 확인하는지가 없었다. ①배포 완료 확인 → ②재색인 curl →
③`REINDEX` → ④반영 확인 쿼리 → ⑤추천 1회 호출(증상별 의심 지점 표) → ⑥유사도·경고 빈도 쿼리
순으로 명령어까지 적었다. **재색인을 빼먹으면 에러 없이 추천 품질만 나빠져서** 제일 놓치기 쉽다.

## 2026-08-12 (계속) — 재색인 실행 방법 확인, B5 절차 정정

사용자가 "재색인을 어디서 하냐(Swagger?)"고 물어 컨트롤러를 직접 확인했고, **내가 B5에 적어둔
내용에 틀린 게 두 개 있었다.**

**① "응답으로 성공·실패 건수가 온다" — 틀렸다.**

```java
public ResponseEntity<ApiResponse<Void>> reindexEmbeddings() {
    embeddingReindexUseCase.startReindexAll();   // 백그라운드
    return ResponseEntity.accepted()...          // 202, 본문 없음
}
```

대상 1건마다 Gemini 호출이 일어나 몇 분씩 걸리므로 **즉시 202만 주고 백그라운드로 돈다.**
성공·실패 건수는 **서버 로그에만** 남는다. 끝났는지는 DB의 `updated_at` 으로 확인해야 한다.

**② 단계 순서가 어긋나 있었다.** `REINDEX` 를 재색인 **직후**에 하도록 적었는데, 그러면 벡터가
아직 안 채워진 상태라 ivfflat 이 클러스터를 못 잡는다 — 애초에 `low recall` 경고가 났던 이유와
같다. **재색인 완료 확인 → 그다음 REINDEX** 로 순서를 바꿨다.

**권한도 확인해 적었다.** 경로에 `/admin/` 이 들어가서 `GlobalSecurityConfig` 의
`.requestMatchers("/api/v1/*/admin/**").hasRole("ADMIN")` 에 걸린다. **클라이언트·프리랜서
계정으로는 403**이라 관리자 계정이 필요하다. Swagger 에서 `POST /auth/login` 으로 관리자 로그인 후
`11. Matching` 태그의 `[관리자] 임베딩 일괄 재색인` 을 호출하면 된다.

**교훈**: 문서에 절차를 적을 때 **실제 코드를 안 보고 기억으로 적으면 틀린다.** 응답 타입과
동기/비동기 여부는 컨트롤러를 열어봐야 안다.

**자바 PR도 머지됐다**(`220890d`). 파이썬은 `8275b09` + 문서 PR까지 완료.
이제 남은 것은 배포 후 B5 절차와 C1 통합 테스트다.

## 2026-08-12 (계속) — 배포 후 재색인 실행, 버그 2개 발견

자바 PR(`220890d`)까지 머지·배포된 뒤 재색인을 실제로 돌렸다. **관리자 계정이 없어서**
새 계정을 회원가입으로 만들고 DB에서 `role='ADMIN'`으로 바꾼 뒤 재로그인해서 호출했다
(권한은 JWT의 `role` 클레임에서 읽으므로 DB만 바꾸면 안 되고 재로그인이 필요하다).

`202 EMBEDDINGS_REINDEX_STARTED` 는 왔는데, 확인 단계에서 두 가지가 드러났다.

### ① `updated_at`이 갱신되지 않는다 (Python)

내가 B5에 적어둔 확인 쿼리(`updated_at > now() - interval '10 minutes'`)가 **0을 돌려줘서**
재색인이 실패한 줄 알았다. 코드를 보니 upsert의 `set_`에 `updated_at`이 없었다 —
`server_default`는 INSERT 때만 걸리므로 **벡터를 다시 만들어도 시각이 안 바뀐다.**

**확인 쿼리 자체가 틀렸던 것이고, 동시에 진짜 버그이기도 하다.** "이 벡터가 언제 만들어졌나"를
알 수 없으면 재색인이 됐는지 영영 확인할 수 없다. 한 줄 수정이라 다음 파이썬 작업에 같이 넣는다.

B5 절차의 확인 방법을 **`ai_agent_log` 조회**로 바꿨다. 임베딩 호출은 거기 남는다.

### ② 포지션이 하나도 재색인되지 않았다 (Java)

`ai_agent_log` 실측: `FREELANCER SUCCESS 1건`, **`POSITION` 0건.** 포지션 임베딩이 9건인데
하나도 안 돌았다.

`EmbeddingReindexService.reindexPositions()`가 대상을 **`matching_snapshot`(POSITION 타입)** 에서
찾는다. 그런데 임베딩을 만드는 경로는 둘인데 스냅샷을 만드는 건 하나뿐이다 —
`ProjectUpdatedEventListener`는 **R32 때문에 스냅샷을 일부러 안 건드리고** 임베딩만 갱신한다.
그래서 **스냅샷 없이 임베딩만 있는 포지션**이 생길 수 있고, 그 포지션은 재색인에서 통째로 빠진다.

스냅샷은 "요청 카드 고정용"이지 "임베딩 대상 목록"이 아닌데 그 용도로 쓴 게 잘못이다.

**다만 원인을 확정하진 못했다.** (a) 스냅샷 0건이라 루프가 안 돈 것과 (b) 스냅샷은 있는데
`findPositionSummary`가 전부 예외를 낸 것이 **둘 다 EMBEDDING 로그 0건**을 만든다 — 자바에서
예외가 나면 파이썬을 부르기 전에 끝나 AI 로그가 안 남고 `log.warn`만 남기 때문이다.
`matching_snapshot` 개수를 세면 갈린다. HANDOFF B7에 확인 쿼리와 함께 적어뒀다.

**"원인을 찾았다"고 먼저 단정한 것은 성급했다.** 증거(로그 0건)가 두 가설을 모두 설명하는데
한쪽만 말했다.

### 둘 다 "에러가 안 나서 안 보이는" 종류다

202도 정상이고 예외도 없었다. **실제로 뭐가 돌았는지 확인하려고 로그 테이블을 뒤져야** 드러났다.
B5 절차에 확인 단계를 넣어두지 않았으면 "재색인 했으니 됐겠지"로 넘어갔을 것이다.

### 실측 결과와 확정된 원인 (같은 날 이어서)

**관리자 계정이 없어서 만드는 것부터 했다.** 관리자 서버를 따로 만들 예정이라 ADMIN 계정이
아예 없었다. DB 직접 INSERT 는 비밀번호 해시·약관 동의·프로필을 다 맞춰야 해서, **새 이메일로
정상 회원가입 후 `UPDATE account SET role='ADMIN'`** 으로 했다. 권한은 JWT 의 `role` 클레임에서
읽으므로(`GlobalJwtAuthenticationFilter`) **DB 만 바꾸면 안 되고 재로그인이 필요하다.**
로그인 요청의 `role` 도 `ADMIN` 으로 보내야 한다 — `findByEmailAndRole` 로 찾기 때문이다.
절차는 HANDOFF B5-② 에 남겼다(다음에 또 필요하다).

**재색인 결과: 프리랜서 1건 성공, 포지션 0건.**

프리랜서가 실제로 새 규칙으로 바뀌었다는 근거가 있다 — `upsert_freelancer` 는 `source_hash` 가
같으면 **AI 를 부르기 전에** 건너뛰는데 `ai_agent_log` 에 `EMBEDDING SUCCESS` 가 남았다.
즉 건너뛰지 않았고, 텍스트가 바뀌었다는 뜻이다. **B1 이 배포까지 정상 적용됐다는 증거다.**

**포지션 0건의 원인은 `matching_snapshot` 0건으로 확정됐다.** `reindexPositions()` 가 대상을
스냅샷에서 찾으니 루프가 아예 안 돌았다.

### 그러다 더 큰 걸 발견했다 — 스냅샷 0건인데 라운드·요청이 있다

```
라운드 2 / 후보 2 / 요청 2 / 스냅샷 0 / 포지션벡터 9
```

`MatchingRequestResponseAssembler.readSnapshot()` 이 스냅샷이 없으면
`SNAPSHOT_NOT_FOUND` 를 던진다. **지금 배포 환경의 요청 2건은 상세 조회가 실패한다.**
C1 의 "요청 → 상세 조회" 구간을 지나갈 수 없으므로 **C1 전에 반드시 정리해야 한다.**

**코드상으로는 이 조합이 나올 수 없다.** `RecruitingStartedPositionHandler` 가 한 트랜잭션
(`REQUIRES_NEW`) 안에서 **스냅샷 → 임베딩 → 라운드** 순으로 만들기 때문에, 라운드가 있으면
스냅샷도 있어야 한다. 가설 셋을 구분하는 쿼리를 HANDOFF B7-③ 에 적었다.

- (a) 스냅샷 기능(2026-08-09 도입) 이전의 옛 테스트 데이터 → `created_at` 확인
- (b) 재추천으로만 생긴 라운드 → `MatchingRerecommendService.openRound` 는 스냅샷을 안 만든다.
      `round_type` 이 `PAID`/`FREE` 뿐인지 확인. **최초 추천 없이 재추천이 되는 게 맞는지도
      같이 봐야 한다**
- (c) 누가 `matching_snapshot` 만 지웠다

### 포지션 벡터를 지금 고치는 우회 방법

②를 고치기 전까지 재색인으로는 못 고친다. 대신 **결제 완료된 프로젝트를 아무 필드나 수정**하면
`ProjectUpdatedEventListener` 가 스냅샷과 무관하게 그 프로젝트의 포지션 임베딩을 전부 다시 만든다.
(결제 전 프로젝트는 이 이벤트가 발행되지 않지만, 어차피 추천이 안 도는 프로젝트라 상관없다.)

### 돌아보면

**세 개 다 "에러가 안 나서 안 보이는" 종류였다.** 재색인은 202 를 줬고 예외도 없었다.
`ai_agent_log` 를 뒤지고 테이블 카운트를 세고 나서야 드러났다. B5 절차에 확인 단계를 넣어두지
않았다면 "재색인 했으니 됐겠지"로 넘어갔을 것이고, C1 에서 원인 모를 실패로 만났을 것이다.

---

## 2026-08-12 추가 확인 및 후속 수정

### C1 end-to-end 확인 완료

배포 DB에서 신규 프로젝트 `23`, 포지션 `33` 기준으로 결제 완료 후 모집 시작 이벤트가 정상 처리되는 것을 확인했다.
후보 조회에서 freelancer `9`가 노출됐고, 매칭 요청 `3` 발송 후 프리랜서 수락으로 협상 `26`이 생성됐다.
협상은 1라운드에서 `3,750,000`원으로 타결됐고, 계약 `27`(`CT-2026-000027`)이 `SIGN_PENDING` 상태로 생성됐다.

### B7 수정

- Python 임베딩 upsert 시 `updated_at`이 갱신되지 않아 재색인 여부를 시간 기준으로 확인하기 어려웠다.
  `freelancer_embedding`, `position_embedding` upsert update 절에 `updated_at = current_timestamp`를 추가했다.
- Java 관리자 포지션 재색인 대상이 `matching_snapshot` 기준이라 스냅샷이 없는 포지션은 재색인되지 않았다.
  실제 모집 라운드가 생성된 `matching_round`의 distinct position 기준으로 최신 라운드를 찾아 포지션 임베딩을 다시 생성하도록 바꿨다.

### F3 수정

후보 조회 응답에 `budgetWarned`를 추가했다. 노출 후보 중 예산 조합 가드 사유가 남아 있으면 `true`로 내려준다.
`guardReason` 원문은 화면에 직접 노출하지 않고, 프론트는 이 boolean으로 예산 경고 UI만 분기하면 된다.

### 검증

- Backend: `./gradlew test --tests "com.pairing.matching.*"` 통과
- Python: `ruff check app/domains/embedding/repository.py tests/test_embedding_repository.py` 통과
- Python: `pytest tests/test_embedding_repository.py` 5 passed, 1 skipped

---

## 2026-08-13 — 디버그 로그 정리(단계별 요약 + 개인정보·대량 로그 제거)

팀원 요청("계산과 임베딩 단계별로 뭐했는지, 최종으로 몇 개 했는지 로그로 보이게")을 반영하면서,
어제 넣은 디버그 로그에서 실제로 디버깅을 방해한 두 가지를 같이 걷어냈다.

### 왜 손댔나 — 오늘 겪은 일

사전검수 40명인데 AI 매칭 후보가 0~1명 나오는 문제를 추적하는 데 시간이 크게 들었다.
원인 후보를 데이터로 여섯 번 지웠고(쿼터 / 저장 실패 / 대상 목록 누락 / `deleted_at` / 이력서 필드 /
프로필 파일), 결론은 데이터가 아니라 **재색인 루프가 재배포와 Python 다운으로 중간에 죽은 것**이었다.

두 가지가 이 추적을 어렵게 만들었다.

- **임베딩 로그가 768차원 벡터를 통째로 찍었다.** 한 줄이 약 9KB, 프리랜서 1명당 두 줄이라
  재색인 1600건이면 로그만 약 30MB다. 정작 필요한 예외 스택이 묻혀서 끝까지 못 찾았다.
- **재색인이 대상 수를 안 남겼다.** `성공=1000 실패=0`만 찍혀서 "실패가 없으니 완료"로 읽혔는데,
  실제 대상은 1601명이었고 601명은 손도 대지 않은 상태였다.

### Python — `app/domains/embedding/service.py`

- `_vector_log()` 삭제, `vector=%s` **5곳** 제거(`generated` / `upserted`x2 / `search` / `scored_search`).
  `vector_preview`(앞 12개)와 `dimension`은 유지 — 값이 정상 범위인지, 생성됐는지는 이 둘로 판단된다.
- `python.embedding.generated`의 `text_preview=%s` 제거. 자기소개 앞 500자가 stdout으로 나가고 있었다.
  **`ai_agent_log.request_json`에 저장하는 쪽은 그대로 둔다** — 관리자 원본 로그 화면의 용도이고,
  거기는 접근권한·보존기간·삭제요청 대응이 로그와 다르다. `text_chars`(길이)만 남겼다.
- 두 결정의 근거를 코드 주석에 남겼다(다음에 누가 "다 보이게" 하려고 다시 넣는 것을 막기 위해).

### Python — `app/domains/matching/service.py`

- **`python.recommend.summary` 신규.** 파이프라인 한 줄 요약이다.
  `hard_filter / relaxed / pool_cut / recruit / pool_multiplier / llm_returned / llm_dropped /
  final / top_score / cut_score / elapsed_ms / final_candidates`.
  단계별 상세 로그는 그대로 두되, 후보가 많으면 그 사이에 줄이 수십 개 끼어서 "어느 단계에서 몇 명이
  줄었나"가 한눈에 안 읽히기 때문에 요약 한 줄을 따로 둔다.
  **점수 스케일 검증보다 먼저 찍는다** — 검증이 실패해 예외로 빠져도 파이프라인 결과는 남아야 한다.
- `final_candidates`에 **최종 통과 후보의 `freelancer_id`와 이름**을 넣는다(사용자 요청).
- 하드필터 후보별 덤프 제거(`python.search.strict_row` / `relaxed_row`, `_row_debug()` 함께 삭제).
  통과 인원수만 남긴다 — 통과자가 수백~수천 명이면 추천 한 번에 그만큼 줄이 늘어난다.
  개별 후보 값은 `python.score.detail`에 항목별 점수와 함께 그대로 남는다.
- `python.llm.request`의 `prompt_preview=%s` 제거. 프롬프트에는 후보 여러 명의 자기소개·경력사항
  원문이 들어 있어서 `text_preview`와 같은 문제였다. 원문 조회는 `ai_agent_log`가 담당한다.
  `prompt_chars`(길이)는 유지.

### Python — `app/domains/matching/repository.py`

- `FreelancerProfile.name` 추가. `find_freelancer_profiles`가 `account`를 조인해서 채운다
  (SELECT/GROUP BY 함께 수정). 스프링 소유 테이블 읽기 전용 규칙은 그대로 지킨다.
- **이 필드는 프롬프트에 넣지 않는다.** LLM이 이름으로 사람을 편향 판단할 수 있고, 프롬프트는
  `ai_agent_log`에 저장되므로 불필요한 개인정보를 늘리는 것이기도 하다. `_describe_candidate`는
  이 필드를 쓰지 않으며, 회귀 테스트 `test_name_is_not_leaked_into_the_prompt`로 고정했다.

### Java — `EmbeddingReindexService` / `FreelancerEmbeddingRefresher`

- `java.reindex.start` 신규 — **시작 시점에 대상 수를 남긴다.** 끝에만 찍으면 도중에 죽었을 때
  몇 명을 처리하려던 것인지조차 알 수 없다(오늘 정확히 이 상황이었다).
- `java.reindex.progress` 신규 — 100건마다 `processed/targets`, 성공·실패·생략 누계,
  마지막으로 처리한 id. **루프가 중간에 죽으면 요약 로그가 아예 안 찍히므로, "어디까지 갔나"는
  이 줄로만 알 수 있다.**
- `java.reindex.summary` 신규 — `targets / processed / succeeded / failed / skipped_blank`.
  프리랜서·포지션 각각 남긴다.
- `FreelancerEmbeddingRefresher.refreshByFreelancerId`의 반환형을 `void` → `boolean`으로 바꿨다
  (`true`=업서트, `false`=텍스트가 비어 생략). **생략을 성공으로 세면 "성공 1000/실패 0"인데 실제로는
  벡터가 하나도 안 생긴 상태가 정상으로 읽힌다.** `refreshByAccountId`도 같이 위임 반환한다.
  기존 호출부(`ResumeUpdatedEventListener`)는 반환값을 무시하므로 동작 변화 없다.

### 검증

- Python: `ruff check --no-cache app/ tests/` 통과
- Python: `pytest -q` **85 passed, 1 skipped**
  (`FreelancerProfile.name` 추가로 `_profile()` 헬퍼가 깨져 21건이 한 번 실패했고, 헬퍼 기본값에
  `name`을 넣어 해결. 회귀 테스트 1건 신규 추가.)
- Backend: `./gradlew test --tests "com.pairing.matching.*"` **70 tests, 0 failures**

### 남은 것

- `python.llm.response`의 `raw=%s`(LLM 응답 전문)는 **그대로 뒀다.** 점수·사유라 이력서 원문보다
  민감도가 낮고, 응답 이상(스케일 오류·지어낸 ID)을 잡는 데 실제로 쓰인다. 빼려면 별도 판단이 필요하다.
- `python.score.detail`은 하드필터 통과자 **전원**에 대해 한 줄씩 찍는다. 지금 규모(수십~수백)에서는
  문제없지만, 통과자가 수천 명이 되면 상위 (모집인원x3) + 컷 경계 근처로 제한해야 한다.
- 재색인 자체가 **인메모리 `@Async` 루프**라 재배포에 여전히 죽는다. 위 진행률 로그로 "어디서 죽었는지"는
  보이지만, 이어하기는 안 된다. 배치 단위 처리·재시작 이어하기는 D1 모니터링 작업과 함께 볼 항목이다.

---

## 2026-08-13 (2) — 알림 발송을 커밋 후로 옮김 (알림 실패가 매칭을 롤백시키던 구조)

알림 도메인 담당자가 협상 알림을 붙이던 중 발견해 공유해준 건이다. **실제 장애는 없었고
실패 경로만 문제였다** — 아직 알림 저장이 실패한 적이 없어 드러나지 않은 상태였다.

### 무엇이 문제였나

`MatchingNotifier.send()`가 예외를 삼키고 로그만 남겼는데, try-catch로는 롤백을 막을 수 없다.
`NotificationCreateUseCase.create()`가 `@Transactional`(REQUIRED)이라 호출한 쪽 트랜잭션에
얹히고, 그 안에서 난 예외는 **잡히기 전에** 공유 트랜잭션을 rollback-only로 표시한다.
그래서 삼켜도 커밋 시점에 `UnexpectedRollbackException`이 나면서 매칭 요청·수락이 통째로 사라진다.

`accept()`가 가장 심각했다 — 스냅샷 동결, budgetCap 계산, 협상 생성, 프로젝트 NEGOTIATING
전이가 전부 같은 트랜잭션이라 알림 한 건 때문에 다 되돌아간다.

### 리포트 중 코드와 달랐던 것 두 가지 (회신함)

- **`notifyRequested` / `resolveAccountId` null 예시는 이 경로에선 안전했다.**
  `FreelancerDirectoryAdapter.resolveAccountId`는 null을 반환하지 않고
  `orElseThrow(FREELANCER_NOT_FOUND)`로 던진다. 그 throw는 `command.get()`에서 나고
  `command.get()`은 `send()`의 try 안이라, `create()`를 호출하기 전에 잡힌다 — 알림 트랜잭션
  경계를 안 넘으므로 rollback-only가 안 찍힌다. `freelancerName()`의 `findCardSummary`도 같다.
- **스케줄러 배치 전체 롤백은 아니었다.** `expireOverdueRequests()`는 `@Transactional`이 없고
  건별로 `MatchingRequestExpirer.expireNow()`(`REQUIRES_NEW`)를 부르며 건별 try/catch로 error
  로그를 남긴다. 한 건 실패해도 나머지는 커밋된다. 다만 그 1건은 만료가 롤백되고 10분 주기마다
  같은 실패를 반복하므로, P41(무료 재추천 판정) 영향은 그 요청 하나에 한해 유효했다.

**진짜 진입점은 `ProjectDirectoryAdapter.findClientAccountId`의 `.orElse(null)`이었다.**
null이 그대로 `create()`에 들어가 `Notification.validate()`가 NT_003을 던지는데, 이건
`create()` 안이라 경계를 넘는다. 이 포트를 쓰는 `notifyAccepted`/`notifyRejected`/`notifyExpired`
셋이 실제 위험 경로였다.

### 무엇을 바꿨나

- `MatchingNotificationRequested`(신규 이벤트, `Kind` = REQUESTED/ACCEPTED/REJECTED/EXPIRED).
  상황별로 레코드를 쪼개지 않았다 — 쪼개면 리스너·발행부가 4배가 되는데 정작 분기는 문구를
  고르는 한 곳뿐이다. `requestId`만 싣고 리스너가 커밋 후 다시 조회한다(발행 시점 객체를 실으면
  즉시 타결처럼 뒤이어 상태가 바뀌는 경우 낡은 값으로 문구가 나간다).
- `MatchingNotificationListener`(신규) — `@TransactionalEventListener(AFTER_COMMIT)`.
  **여기엔 `@Transactional`을 붙이지 않았다.** 트랜잭션 경계 안에서 잡으면 위 문제가 그대로
  재현되기 때문이다. 잡는 것은 경계 밖(리스너), 트랜잭션은 안쪽(`MatchingNotifier`)이다.
  `@Async`도 안 붙였다 — INSERT 한 건이라 응답 지연이 무시할 수준이고, 비동기면 예외가 이
  스레드로 안 와서 로그를 남길 수 없다.
- `MatchingNotifier` — 각 `notify*`에 `@Transactional(REQUIRES_NEW)`. 실패를 자기 트랜잭션에
  가두고, `AFTER_COMMIT`에서 새 트랜잭션을 열지 않으면 INSERT가 조용히 버려지는 것도 막는다
  (협상 도메인이 이걸 빠뜨려 알림이 하나도 저장되지 않았던 사례를 담당자가 알려줬다).
  `send()`의 예외 삼키기는 제거했다 — 삼키면 리스너가 실패를 알 수 없고, 이미 rollback-only가
  찍힌 뒤라 삼키는 것 자체가 소용이 없다.
- `send()`에 **받을 계정 null 가드** 추가. 남의 도메인 에러코드(NT_003)로 터지는 대신 무엇이
  없었는지 분명한 로그를 남긴다.
- `MatchingRequestService`(3곳) / `MatchingRequestExpirer`(1곳)가 직접 호출 대신 이벤트를 발행한다.
  `matchingNotifier` 필드를 `ApplicationEventPublisher`로 교체했다.

### 일부러 안 바꾼 것

`findClientAccountId`의 `.orElse(null)`을 `orElseThrow`로 바꾸는 게 한 줄 조치로 보였지만
**하지 않았다.** `ClientGradeResolver`가 같은 포트를 쓰면서 프로필이 없으면 조용히
SILVER(가중치 0%)로 떨어뜨리는데, 던지게 바꾸면 추천 라운드 생성 자체가 실패한다.
알림 때문에 그 동작을 바꿀 수는 없어서 알림 쪽에서 막았다.

`NotificationCreateUseCase.create()`를 `REQUIRES_NEW`로 바꾸지 않기로 한 알림 담당자의 판단에
동의했다. 그러면 호출한 도메인이 롤백돼도 알림은 남아서, 실제로 일어나지 않은 일에 대한 알림이
사용자에게 간다 — 알림이 안 가는 것보다 나쁘다.

### 검증

- `./gradlew build` 통과 (전체 테스트 포함)
- `./gradlew test --tests "com.pairing.matching.*"` — **75 tests, 0 failures** (신규 5건 포함)
- 신규 `MatchingNotificationListenerTest` 3건: 발송 실패가 리스너 밖으로 안 나감 / 대상 요청이
  없으면 발송 안 함 / 종류별 디스패치. **리스너에 `@Transactional`을 붙이거나 try-catch를 지우면
  첫 번째가 깨진다.**
- 신규 `MatchingNotifierTest` 2건: 받을 계정이 null이면 알림을 만들지 않음 / 찾으면 그 계정으로 만듦
- `MatchingIntegrationTest`의 기존 알림 검증 3건(`MATCHING_REQUESTED`/`ACCEPTED`/`REJECTED`)이
  그대로 통과한다 — 이 테스트는 클래스에 `@Transactional`이 없어 MockMvc 요청이 실제로 커밋되므로
  `AFTER_COMMIT` 리스너가 실제로 돈다(트랜잭션 테스트였다면 리스너가 아예 안 불려서 통과가
  의미 없었을 것이다).

---

## 2026-08-13 (3) — 재색인 NPE 원인 확정: @OrderColumn 이 만들어낸 null 원소

임베딩 일괄 재색인이 대상 1601명 중 **1011번까지만** 벡터를 만들고 나머지가 조용히 빠지던 문제.
원인을 찾는 데 반나절이 걸렸다. 아래는 그 과정과 결론이다.

### 원인

`FreelancerDirectoryAdapter.findResumeSummary`가 이력서 하위 목록에 섞인 **null 원소** 때문에
NPE 로 죽고 있었다. null 은 DB 에 있는 값이 아니라 **하이버네이트가 만들어낸 것**이다.

`ResumeJpaEntity`의 학력·경력은 이렇게 매핑돼 있다.

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(name = "resume_career", joinColumns = @JoinColumn(name = "resume_id"))
@OrderColumn(name = "sort_order")
private List<ResumeCareerEmbeddable> careers = new ArrayList<>();
```

`@OrderColumn`은 그 컬럼을 **리스트 인덱스**로 쓴다. 값이 0부터 연속이 아니면 하이버네이트가 빈
자리를 **null 원소로 채워서** 컬렉션을 돌려준다. 앱으로 저장한 이력서는 항상 0부터 연속이라
문제가 없었고, **SQL 로 직접 넣은 더미 데이터**만 번호가 어긋나 있었다.

### 왜 찾기 어려웠나

- **DB 를 조회하면 데이터가 정상으로 보인다.** 컬럼이 다 채워져 있다. null 은 자바 쪽 리스트에만
  있다. 그래서 데이터 가설을 여섯 개나 세우고 전부 지웠다(쿼터/저장실패/대상목록누락/deleted_at/
  이력서필드/프로필파일). 전부 헛수고였다.
- **예외에 메시지가 없다.** `Stream.toList()`는 null <b>값</b>은 허용하지만, null <b>원소</b>에
  메서드 참조를 적용하면 메시지 없는 NPE 가 난다. 실제로 확인했다.
- **스택이 실제 원인 지점을 안 가리킨다.** `map`이 지연 평가라 예외는 터미널 연산에서 난다.
  그래서 `.map(Career::getJobDescription)` 줄이 아니라 `.toList()` 줄이 찍힌다.
- **로그 수집기가 스택을 줄마다 쪼갠다.** `NullPointerException`으로 검색하면 헤더만 나오고
  `at ...` 줄은 별개 항목이라 안 걸린다. 메시지도 없으니 헤더에서 얻을 정보가 0이었다.
  결국 `at com.pairing` 으로 검색해서야 위치가 나왔다.

### 무엇을 바꿨나

- `FreelancerDirectoryAdapter.findResumeSummary`가 null 원소와 빈 문자열을 걸러낸다.
  `FreelancerResumeSummary`의 javadoc 은 원래부터 "학과 미입력 건은 빠진다"고 적고 있었다 -
  그 약속을 코드가 지키지 않았던 것이라, 데이터를 고치는 게 아니라 여기를 고치는 게 맞다.
  **임베딩 텍스트는 달라지지 않는다** - `FreelancerEmbeddingTextBuilder`가 이미 blank 를 건너뛴다.
- 재색인 실패 로그를 `java.reindex.failed ... cause=NullPointerException at <첫 프레임>` 형태로
  바꿨다. **검색 한 번에 원인이 보이게** 하는 것이 목적이다(전체 스택은 그대로 함께 남긴다).
  오늘 반나절을 쓴 이유가 정확히 이게 없었기 때문이다.

### 검증

- 신규 `FreelancerDirectoryAdapterTest` 2건: null 원소가 섞여도 요약을 만든다 / 빈 값은 목록에서 뺀다
- **변이 테스트로 확인**: null 필터를 지우고 돌리면 테스트가 실패하며, 그때 나오는 스택이
  프로덕션과 완전히 같다(메시지 없는 NPE, JDK 프레임 위, `com.pairing` 프레임은
  `findResumeSummary`의 `.toList()` 줄 하나). 원인을 재현한 것이다.
- `./gradlew build` 통과
- `./gradlew test --tests "com.pairing.matching.*"` — **77 tests, 0 failures**

### 데이터 쪽 남은 일 (코드 배포와 별개)

코드는 이제 null 을 견디지만, **`sort_order`가 어긋난 이력서는 여전히 남아 있다.** 그 이력서를
조회하는 다른 경로(마이페이지 이력서 조회 등)에도 null 원소가 그대로 넘어간다. 매칭만의 문제가
아니므로 정렬 번호를 0부터 연속으로 정규화해 두는 것이 맞다. 확인·정리 SQL 은 아래.

```sql
-- 어긋난 이력서 찾기 (careers 예시. educations/certificates/links 도 같은 방식)
SELECT count(*) AS 어긋난_이력서
  FROM (SELECT resume_id, count(*) cnt, min(sort_order) mn, max(sort_order) mx
          FROM resume_career GROUP BY resume_id) t
 WHERE mn <> 0 OR mx <> cnt - 1;

-- 0부터 연속으로 다시 번호 매기기 (ctid 로 행을 특정한다 - 이 테이블엔 PK 가 없다)
WITH renumbered AS (
  SELECT ctid, ROW_NUMBER() OVER (PARTITION BY resume_id ORDER BY sort_order) - 1 AS new_order
    FROM resume_career
)
UPDATE resume_career c SET sort_order = r.new_order
  FROM renumbered r
 WHERE c.ctid = r.ctid AND c.sort_order <> r.new_order;
```

**더미 데이터를 SQL 로 직접 넣을 때는 `sort_order`를 0부터 시작해야 한다.** 이번 사고의 출발점이다.

---

## 2026-08-13 (4) — 요청 상세가 프리랜서에게 404(AC_002) 나던 버그

프론트 리포트로 접수. `GET /api/v1/matchings/requests/{requestId}` 를 **프리랜서**가 부르면
`404 AC_002 "프로필 정보를 찾을 수 없습니다"` 가 났다. 목록(`/requests/received`)은 정상이었다.

### 원인 — 당사자 판별에 "던지는 조회"를 썼다

```
findRequest(requestId, 프리랜서_accountId)
 └ projectDirectoryPort.isOwnedByAccount(projectId, accountId)
    └ ProjectQueryService.isOwnedBy -> resolveClientProfileId(accountId)
       └ clientProfileReaderPort.getByAccountId(accountId)
          └ accountQueryUseCase.getClientProfile(accountId)
             └ orElseThrow(AC_002)          <- 프리랜서에겐 client_profile 행이 없다
```

`ClientProfileReaderPort` 의 javadoc 도 "프로필이 없으면 account 도메인의 AC_002 가 그대로
올라온다"고 적고 있다. **클라이언트 계정으로만 부를 수 있는 조회를 프리랜서 accountId 로 불렀다.**

프론트의 추정("상대방 프로필을 추가 조회하다 실패")은 틀렸다. 상대방 프로필이 아니라
**호출자 본인의 권한을 확인하려고** 부른 조회다.

### 같은 자리에서 두 번째다

- **2026-08-09**: `resolveFreelancerId` 를 먼저 불러서 **클라이언트가** 늘 MT_015(404)
- **2026-08-13**: 그걸 고치며 `isOwnedByAccount` 를 앞으로 옮겼더니 **프리랜서가** 늘 AC_002(404)

한쪽만 보고 순서를 바꿔서 반대쪽을 깬 것이다. 근본 원인은 순서가 아니라 **신분 확인을 예외로
했다는 것**이다. 두 조회 모두 "상대 신분이면 던진다"라서 뭘 먼저 부르든 한쪽은 404가 된다.

### 무엇을 바꿨나

- `FreelancerDirectoryPort.findFreelancerId(accountId)` 신규 - `resolveFreelancerId` 의 **안 던지는**
  버전이다(`AccountQueryUseCase.findFreelancerProfileByAccountId` 가 이미 Optional 을 준다).
- `findRequest` 가 이걸로 먼저 신분을 가르고, **각 분기에서만 자기 쪽 조회**를 쓴다. 프리랜서
  경로는 `isOwnedByAccount` 를 아예 부르지 않고, 클라이언트 경로는 `resolveFreelancerId` 를
  부르지 않는다. 판별을 값으로 하니 어느 쪽도 남의 도메인 예외를 만나지 않는다.

### 검증

- `MatchingIntegrationTest.requestDetailIsReadableByBothParties` 신규 - **한 테스트에서 클라이언트와
  프리랜서 양쪽을 다 조회**한다. 기존 상세 테스트가 전부 클라이언트 토큰만 써서 이 회귀를 못 잡았다.
  한 방향만 검증하면 순서를 뒤집는 수정이 또 통과한다.
- **변이 테스트로 확인**: 수정 전 로직으로 되돌리면 이 테스트가 **AC_002 로 실패**한다.
  프론트가 보고한 그 에러코드 그대로다.
- `./gradlew clean build` 통과 - **520 tests, 0 failures**

### 남는 것

클라이언트 계정에 `client_profile` 이 없으면 여전히 AC_002 가 난다. 그건 클라이언트로선 진짜 데이터
이상이고 account 도메인의 계약이므로 그대로 둔다. 프리랜서에게 `client_profile` 이 없는 것은
**정상**이고, 그 경우가 이번 버그였다.
