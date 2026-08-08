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

## 다음 세션에서 할 일

1. `feature/matching-directory-adapters`에 이슈·PR 생성 (`docs/ai/git-issue-pr-guide.md` 규칙: 브랜치명 `feature/short-task-name` 컨벤션 확인함). **PR 올리기 전에 `local` 프로파일로 서버 띄워 Swagger UI에서 바뀐 동작(후보 카드/요청 상세/수락→협상생성)을 실제로 호출해 확인하고 PR `Verification` 섹션에 기록할 것 — 지난 PR 때 빠뜨렸던 절차라 이번엔 잊지 말 것.**
2. `MatchingNegotiationOutcomeUseCase`(협상 결렬/타결 통보) 인터페이스 정의 + `MatchingRequestService`에 구현 추가. 5번 쪽 구현과 맞춰야 함.
3. `currentSituation`/`mainTask` 노출 여부 팀 답변 오면 반영(대기 중).
4. `feature/negotiation-unread-proposals`가 develop에 merge되면 `newProposalCount` 동작이 자동으로 좋아지는지 확인만.
5. `resolveFreelancerId`/`findCondition`(freelancerId 기준) — 1번의 account_id↔freelancer_profile.id 조회 메서드 승인되면 `FreelancerDirectoryAdapter` 마저 완전 교체.
6. `.ai/HANDOFF.md`의 "3일차" 나머지 항목: Pairing-python `_build_prompt` 실구현 + 하드필터 + Stage F 실제 배분 알고리즘 + 등급 타이브레이커 + 통합테스트/문서 동기화.
7. 결제 완료 트리거로 "최초 추천 라운드 생성" 호출하는 지점(payment 도메인도 아직 없음 — 인바운드 UseCase만 노출해두고 대기하는 게 나을지 확인 필요).
