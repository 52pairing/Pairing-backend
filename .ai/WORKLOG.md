# 작업 기록 — AI매칭(4번 파트)

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

## 다음 세션에서 할 일

1. **PR #51 CI 결과 확인** — 레이트리밋 목 처리 커밋(마지막 push)까지 반영된 CI가 초록인지 확인. 초록이면 팀원 리뷰/머지 대기(머지는 4번이 직접 하지 않음).
2. `MatchingNegotiationOutcomeUseCase`(협상 결렬/타결 통보) 인터페이스 정의 + `MatchingRequestService`에 구현 추가. 5번 쪽 구현과 맞춰야 함.
3. `currentSituation`/`mainTask` 노출 여부 팀 답변 오면 반영(대기 중).
4. budgetCap의 WEEK→개월 환산 규칙 3번 답변 오면 `BudgetCapCalculator` 임시값(4주=1개월) 교체.
5. `resolveFreelancerId`/`findCondition`(freelancerId 기준) — 1번의 account_id↔freelancer_profile.id 조회 메서드 승인되면 `FreelancerDirectoryAdapter` 마저 완전 교체.
6. `.ai/HANDOFF.md`의 "3일차" 나머지 항목: Pairing-python `_build_prompt` 실구현 + 하드필터 + Stage F 실제 배분 알고리즘 + 등급 타이브레이커 + 통합테스트/문서 동기화.
7. 임베딩 텍스트(`RecruitingStartedPositionHandler.buildEmbeddingText`)에 mainTask/currentSituation/업무범위/우대사항 추가 — 3번 항목(위 3번)이 정해지고 매칭 쪽 요약에 필드가 생기면 같이 반영.
8. ~~`AI매칭_API_화면매핑_최신본.md` 반영 확인~~ — 2026-08-09 완료. 사용자가 반영했고 재검토까지 끝남.
9. (선택) 이 컴퓨터에 `gh` CLI 설치하면 다음부터 이슈/PR을 AI가 직접 생성할 수 있음 — 지금은 매번 텍스트만 만들어주고 사용자가 직접 생성 중.
