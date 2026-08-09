# 인수인계 — AI매칭(4번 파트) 3일 스프린트

최종 갱신: 2026-08-09. 이어서 작업할 때는 이 문서 + `.ai/STATE.md`를 먼저 읽는다.

## 진행 현황 요약

**1일차·2일차 항목(아래 1~8번) 전부 완료.** 매칭 도메인 9개 엔드포인트가 실제 로직으로 동작한다
(스켈레톤 고정 응답 없음). PR #34로 develop에 merge 완료.

**스텁 어댑터 3개 중 2개 완전 교체, 1개 부분 교체 — PR #51 develop에 merge 완료(2026-08-09).**
`ProjectDirectoryPort`/`NegotiationPort`는 실제 구현으로 완전히 바뀌었고, `FreelancerDirectoryPort`는
카드 요약만 실구현이고 `resolveFreelancerId`/`findCondition`은 account 도메인 쪽 메서드 대기 중이라
스텁으로 남아있다. 같은 브랜치에 budgetCap 버그 수정(2건)과 결제 완료 → 매칭 초기 추천 이벤트 리스너
신규 구현도 같이 들어가 merge됐다. 상세는 `.ai/STATE.md` 참고.
이 컴퓨터에 `gh` CLI가 없어서 이슈/PR은 AI가 텍스트만 만들고 사용자가 GitHub 웹에서 직접 생성한다.

**`fix/rerecommend-quantity-validation` 브랜치 신규 오픈 (develop 대상, PR 생성 대기).** 아래 20번
항목(재추천 quantity 누락 시 500 나던 것) 수정 — `./gradlew build` 통과 확인, push 완료. PR 텍스트는
이 세션에서 준비해 사용자에게 전달, 사용자가 GitHub 웹에서 생성 예정.

## 지금 당장 할 일 (순서대로)

### 1일차 오전 — 뼈대 [완료]

1. ~~`com.pairing.matching.domain.model`에 실제 도메인 모델 추가~~ — 완료.
2. ~~`application/port/out/MatchingPort` + `infrastructure/llm/PythonMatchingAdapter`~~ — 완료. Resilience4j 서킷브레이커, Bucket4j 레이트리밋, `X-Internal-Api-Key`/`X-Trace-Id` 헤더까지 포함.

### 1일차 오후 — 협상팀 연동 [완료 — 단, 협상 도메인 쪽은 아직 스텁]

3. ~~`POST /api/v1/matchings/requests/{requestId}/acceptance` 실제 구현~~ — 완료. 스냅샷 캡처, `budgetCap` 계산, negotiation 호출까지 다 붙였다.
   - ~~negotiation 쪽 실제 인바운드 UseCase 없음~~ — 2026-08-08 해소, `NegotiationAdapter`로 실제 연동 완료(아래 12번 참고).
   - **새로 남은 것**: 협상 타결/결렬 시 매칭 상태를 갱신해줄 통로가 없음. `MatchingNegotiationOutcomeUseCase.markNegotiationAgreed/markNegotiationFailed(requestId)` 시그니처를 5번에게 전달함 — 양쪽 다 구현 전.
4. ~~결제완료 이벤트 시점에 "1차 추천 라운드 생성"~~ — 2026-08-09 완료. 3번이 착수금 결제 커밋 후 `RecruitingStartedEvent(projectId)`를 발행하도록 만들었고, 매칭 쪽 `RecruitingStartedEventListener`/`RecruitingStartedPositionHandler`가 이를 받아 포지션별로 스냅샷 동결 → 임베딩 upsert(`MatchingPort.upsertPositionEmbedding` 신규) → `MatchingRoundCreationService.createRound(INITIAL, ...)` 순서로 처리한다. 멱등 가드·포지션 단위 예외 격리 적용, 테스트(`RecruitingStartedEventListenerTest`) 통과 확인.

### 2일차 — 후보 노출/선택/요청 [완료]

5. ~~`GET /positions/{positionId}/candidates`~~ — 완료(`MatchingCandidateService`/`CandidateResponseAssembler`).
6. ~~`POST /candidates/{candidateId}/rejection`~~ — 완료. 스키마에 `is_rejected` 컬럼을 추가해야 했음(원래 없었음).
7. ~~`POST /positions/{positionId}/rerecommendations`~~ — 완료(`MatchingRerecommendService`/`MatchingRoundCreationService`). 무료/유료 한도는 포지션이 아니라 **프로젝트 전체 기준**(P40/P41)이라는 점에 주의해서 구현함.
8. ~~`POST /requests`, `GET /requests`, `GET /requests/received`, `GET /requests/{requestId}`, `POST /requests/{requestId}/rejection`~~ — 완료(`MatchingRequestService`/`MatchingRequestResponseAssembler`).
   - `findSentRequests`에서 `projectId` 파라미터 없이 조회하면 `ProjectDirectoryPort.findProjectIdsOwnedByAccount`가 스텁이라 **빈 페이지**가 돌아온다. project 도메인이 실제로 붙기 전까지는 알려진 제약.

### 3일차 — Pairing-python 완성 + 정확도 개선 + 스텁 교체 [남음]

9. `Pairing-python/app/domains/matching/service.py`의 `_build_prompt` 실제 구현: 포지션 요구조건(직군/직무/스킬/경력/예산 등) + 프리랜서 이력서 원문(자기소개, 경력사항)을 프롬프트에 채움. **지금 스텁은 임베딩 유사도를 그대로 프롬프트에 넣고 있는데, 이건 빼야 함** (우리 결정: LLM은 원문만 보고 판단, 유사도는 추리는 용도로만 씀). **추가**: fit_reason을 `"|"`로 이어붙인 문자열로 내려주도록 응답 스키마/프롬프트 맞추기(Spring `CandidateResponseAssembler`가 이 구분자로 다시 나눔).
10. `Pairing-python`의 `search_similar_freelancers`에 하드필터 추가(직군/직무/AI매칭 동의/일정/단가) — 지금은 조건 없이 순수 벡터 검색만 함. 이전에 노출된 프리랜서 제외도 지금은 Spring 쪽에서 결과 받은 뒤 후처리로 거르고 있음(`MatchingRoundCreationService.excludePreviouslySurfaced`) — Pairing-python이 검색 전에 미리 제외하도록 옮기면 풀 크기가 줄어드는 문제가 해결됨.
11. `budgetCap`을 1단계 단순 공식(구현 완료, `BudgetCapCalculator`)에서 Stage F 정확한 배분 알고리즘(포지션별 1순위 조합 → 초과시 비싼 포지션을 다음 순위로 교체)으로 교체. 지금 Stage F 가드(`MatchingRoundCreationService`의 `applyGuard(true, null)`)도 항상 통과 처리인 placeholder라 같이 실제 검증으로 교체.
12. ~~freelancer/project/negotiation 도메인이 실제로 만들어지면 스텁 어댑터 3개 교체~~ — 2026-08-08, `feature/matching-directory-adapters` 브랜치에서 완료(Project/Negotiation 완전 교체, Freelancer는 카드 요약만). ~~이슈·PR 생성~~ — 2026-08-09 **PR #51** 오픈 완료(develop 대상). 첫 push 때 CI가 재추천 테스트에서만 500 실패 → `/rerecommendations`의 레이트리밋이 Redis 없는 CI에서 실제 연결을 시도해서였음, 테스트에서 `RateLimitProvider` 목 처리로 해결·재push 완료. **다음 세션 최우선: CI 그린 확인하고 팀원 리뷰/머지 대기.**
    - 프리랜서 등급 타이브레이커(base_score 동점 시 마스터>시니어>주니어)는 아직 랭킹 로직에 미반영.
    - `resolveFreelancerId`/`findCondition`(freelancerId 기준)은 account 도메인의 account_id↔freelancer_profile.id 조회 메서드가 나와야 완전 교체 가능(2번이 1번에게 승인 요청, 대기 중).
    - `src/test/java/com/pairing/matching/presentation/api/MatchingIntegrationTest.java`(H2 통합테스트, 후보조회/거절/요청발송/조회/수락/거절/재추천 9개 케이스)로 검증 완료 — Swagger 수동 클릭 대신 이 테스트를 돌려서 확인하면 됨. PR Verification 섹션에 이 테스트 통과를 근거로 적을 것.
13. 통합 테스트, `.ai/API.md`/`docs/api-dto.csv` 최종 동기화, 에러코드(`AI_001~AI_030`) 매핑 점검.
14. ~~`MatchingNegotiationOutcomeUseCase`(협상 결렬/타결 통보 인바운드 포트) 구현~~ — 2026-08-09 매칭 쪽 구현 완료(`feature/matching-negotiation-outcome` 브랜치, develop 대상 PR 생성 대기).
    - `markNegotiationAgreed(requestId)` → `MatchingRequest.agreeNegotiation()`(신규 도메인 메서드, NEGOTIATING일 때만 허용) → `CONTRACT_PENDING`.
    - `markNegotiationFailed(requestId)` → 기존 `MatchingRequest.failNegotiation()` 그대로 사용 → `NEGOTIATION_FAILED`.
    - 존재하지 않는 requestId는 `MatchingErrorCode.REQUEST_NOT_FOUND`, NEGOTIATING이 아닌 상태에서 호출하면 `INVALID_MATCHING_STATE`.
    - 단위 테스트 `MatchingNegotiationOutcomeServiceTest`(4개: 타결/결렬/요청없음/상태불일치) 작성, `./gradlew build` 통과 확인.
    - **아직 남은 것(negotiation/5번 쪽 작업)**: `NegotiationLoopService.agree()`(94번 줄)/`.fail()`(122, 149번 줄)이 각각 `Negotiation.agree()`/`.fail()` 호출 직후 `MatchingNegotiationOutcomeUseCase.markNegotiationAgreed(negotiation.getRequestId())`/`.markNegotiationFailed(...)`를 호출하도록 5번이 이어서 구현해야 한다 — 이건 negotiation 도메인 코드라 매칭(4번)이 대신 건드리지 않았다. 같은 트랜잭션 안에서 부르는 걸 전제로 설계함(매칭이 `accept()`에서 negotiation을 동기 호출하는 것과 대칭).
15. `currentSituation`/`mainTask`(프로젝트 현재 상황/담당 업무) 노출 여부 팀 답변 오면 `MatchingRequestResponse`에 필드 2개 추가 여부 결정.
16. ~~budgetCap이 포지션 인원/총액 단위로 잘못 계산되던 버그 2건(3번 리포트)~~ — 2026-08-09 수정 완료. `position.headcount()`→`totalHeadcount()`, budgetAmount를 개월 수로도 나누도록 수정. ~~WEEK 기간 주→개월 환산 규칙~~ — 2026-08-09 **4주=1개월로 확정**(사용자 확인). `negotiation` 도메인의 `NegotiationConditionCalculator`도 이미 같은 값(4주=1개월)을 쓰고 있어서 두 도메인 계산 기준이 일치함을 재확인. `BudgetCapCalculator`의 `TEMP_WEEKS_PER_MONTH`(임시값 표기)를 `WEEKS_PER_MONTH`로 정리.
17. **(참고, 재발 방지)** `@TransactionalEventListener` 안에서 `@Transactional(REQUIRES_NEW)` 메서드를 "같은 빈 안에서 `this.method()`로" 부르면 스프링 프록시를 안 거쳐 트랜잭션이 조용히 무시된다(자체 호출 self-invocation 문제). 실제로 이 버그로 라운드 저장이 안 되는 걸 테스트로 재현해서 발견 — 새 트랜잭션이 꼭 필요한 메서드는 반드시 별도 빈으로 분리해서 호출할 것(`RecruitingStartedEventListener`/`RecruitingStartedPositionHandler` 참고).
18. **(참고, 재발 방지)** 재추천 엔드포인트의 레이트리밋(`RateLimitProvider`)은 Redis가 실제로 있어야 동작한다. 이 엔드포인트를 호출하는 테스트를 새로 짤 땐 `RateLimitProvider`를 `@MockitoBean`으로 목 처리해야 Redis 없는 CI에서도 통과한다(`MatchingIntegrationTest` 참고). 로컬은 Redis 컨테이너가 떠 있어서 이 문제가 안 드러나니 착각하지 말 것.
19. ~~`AI매칭_API_화면매핑_최신본.md`(레포 밖, 프론트 공유용 문서) 검토~~ — 2026-08-09 완료. MT_009(dead code, 실제론 201+빈 배열) 제거, `RerecommendRequest.quantity` PAID 필수·검증 없음 경고를 사용자가 문서에 반영, 재검토까지 끝남.
20. ~~`RerecommendRequest.quantity`에 `type=PAID`일 때만 필수가 되는 조건부 검증이 없다~~ — 2026-08-09 수정 완료. `MatchingErrorCode.QUANTITY_REQUIRED`(MT_012) 추가, `MatchingRerecommendService.rerecommend()` 초입에 `type == PAID && quantity == null`이면 던지도록 처리. 회귀 테스트(`MatchingIntegrationTest.rerecommendPaidWithoutQuantityReturnsBadRequest`) 추가. `fix/rerecommend-quantity-validation` 브랜치, PR 생성 대기.
21. ~~`RerecommendRequest.type`이 `INITIAL`도 그대로 받아버리는 문제~~ — 2026-08-09 수정 완료. 코드 리뷰로 발견: `type == FREE`가 아니면 전부 유료 취급하는 구조라 `type=INITIAL`을 보내면 (a) `quantity`가 없으면 20번 수정으로도 못 막던 NPE, (b) `quantity`가 있으면 재추천 API로 `INITIAL` 태그 라운드가 만들어지는 오동작이 가능했음(20번 수정만으론 해결 안 됨). `MatchingErrorCode.INVALID_RERECOMMEND_TYPE`(MT_013) 추가, `rerecommend()` 최초 진입부에 `type`이 FREE/PAID가 아니면 던지도록 처리. 회귀 테스트(`MatchingIntegrationTest.rerecommendWithInitialTypeReturnsBadRequest`) 추가. 20번과 같은 브랜치(`fix/rerecommend-quantity-validation`)에 포함.
22. **(신규 발견, 우선순위 중)** 임베딩 텍스트(`PositionEmbeddingTextBuilder.buildText`, 2026-08-09에 `RecruitingStartedPositionHandler`에서 공유 클래스로 추출됨)에 `currentSituation`/`mainTask`(Task #4, 팀 답변 대기) 말고도 `detailScope`(업무범위)/`extraNote`(우대사항)도 빠져있다. 이 둘은 팀 결정 대기 항목이 아니라 애초에 매칭 쪽으로 안 이어져 있던 것 — project 도메인의 `Project`/`ProjectResponse`엔 이미 존재하는데, project가 매칭에 넘기는 `ProjectPositionSummary`(project.application.result)에도, 매칭 자신의 로컬 `ProjectPositionSummary`에도 없다. 원래 설계 결정(`.ai/STATE.md` "확정된 설계 결정 1": 임베딩 유사도 = 프로젝트설명+담당업무+업무범위+우대사항)엔 포함돼야 하는 필드라 Task #4와 별개로 백로그에 추가 필요.
23. ~~매칭 요청 카드가 프로젝트 정보를 라이브로 읽던 문제(R32)~~ — 2026-08-09 3번이 프로젝트 수정 API 구현 중 발견해 확인 요청, 수정 완료. `fix/matching-request-card-snapshot-read` 브랜치, PR 생성 대기. 상세는 `.ai/STATE.md` 참고.
24. ~~모집 시작 후 프로젝트 수정 시 포지션 임베딩 재생성~~ — 2026-08-09 완료. 3번이 같은 PR(#57, 프로젝트 수정 API)에서 `ProjectUpdatedEvent(projectId)`(수정 커밋 후 발행, headcount 잠긴 이후=결제 완료 후에만)를 새로 만들어놨는데 매칭 쪽에 받는 곳이 없던 걸 develop 재동기화 중 발견. `ProjectUpdatedEventListener` 신규 추가 — 프로젝트의 포지션마다 최신 정보로 임베딩 재생성(`MatchingPort.upsertPositionEmbedding`). **23번(요청 카드 스냅샷)과는 반대 방향이라 안 헷갈릴 것**: 요청 카드용 `MatchingSnapshot`은 절대 안 건드림(최초 모집 시작 시점 고정 유지, R32), AI 검색용 임베딩만 최신화(검색 정확도 문제라 최신이 맞음). 임베딩 텍스트 조립 로직은 `RecruitingStartedPositionHandler`와 공유하도록 `PositionEmbeddingTextBuilder`로 추출. 테스트 3개(`ProjectUpdatedEventListenerTest`: 포지션별 재생성/포지션 단위 예외 격리/스냅샷 불변) 작성, `./gradlew clean build` 통과. `feature/project-updated-embedding-refresh` 브랜치, PR 생성 대기.

## 열려있는 결정/블로커 (건드리기 전에 확인)

- `resolveFreelancerId`/`findCondition`(freelancerId 기준) — account 도메인 메서드 대기 중이라 여전히 placeholder.
- 적합도 점수 스케일 0~100 여부, 골드 등급 수수료 할인, 프로젝트 인원별 예산배분 필드 존재 여부, `currentSituation`/`mainTask` 노출 여부 — `.ai/STATE.md` 하단 표 참고, 팀 확인 대기 중이라 확정 전까지는 가정값으로 진행. (budgetCap의 WEEK→개월 환산 규칙은 2026-08-09 4주=1개월로 확정됨, 더 이상 대기 항목 아님)

## 참고할 실제 파일

- Pairing-python 계약 문서: `C:\52_Pairing\Pairing-python\README.md`
- Spring↔AI서버 연동 참고 구현: `C:\Algoga_V3_backend`의 `com.kidmily.algoga_server.chatbot` 도메인 (Port/Adapter/서킷브레이커/레이트리밋/내부API 전부 실제 코드로 있음)
- 매칭 DTO 계약: `com.pairing.matching.presentation.api.*` (이미 확정, 필드 변경 시 `.ai/API.md`와 `docs/api-dto.csv` 같이 갱신)
- 프론트 전달용 API-화면 매핑 문서(레포 밖, 사용자가 직접 관리): `C:\Users\user\Desktop\AI매칭_API_화면매핑_최신본.md`
