# 인수인계 — AI매칭(4번 파트) 3일 스프린트

최종 갱신: 2026-08-09. 이어서 작업할 때는 이 문서 + `.ai/STATE.md`를 먼저 읽는다.

**3일차 배치(2026-08-09).** `feature/matching-embedding-detail-fields`(22번)/`feature/matching-freelancer-grade-tiebreaker`(12번 하위)/`feature/matching-project-stage-sync`(25번)/`feature/matching-freelancer-directory-real-impl`(12번 하위) 전부 develop에 merge 완료. Pairing-python `feature/matching-prompt-real-implementation`(9번)은 push 완료, develop 대상 PR 오픈해서 리뷰/머지 대기 중. **11번(budgetCap Stage F)은 A안(현행 유지) 확정, 코드 변경 없음** — 아래 11번 항목 참고. **10번(Pairing-python 하드필터)도 2026-08-09 완료** — 남은 건 10-2번(Stage E 감점 반영, 미착수)뿐.

## 진행 현황 요약

**1일차·2일차 항목(아래 1~8번) 전부 완료.** 매칭 도메인 9개 엔드포인트가 실제 로직으로 동작한다
(스켈레톤 고정 응답 없음). PR #34로 develop에 merge 완료.

**스텁 어댑터 3개 전부 완전 교체.** `ProjectDirectoryPort`/`NegotiationPort`는 PR #51(2026-08-09)로,
`FreelancerDirectoryPort`는 처음엔 `findCardSummary`만 실구현이었다가 2026-08-09(3일차 배치)에
`resolveFreelancerId`/`findCondition`까지 완전 교체됐다(아래 11번/12번 참고). 같은 브랜치(PR #51)에
budgetCap 버그 수정(2건)과 결제 완료 → 매칭 초기 추천 이벤트 리스너 신규 구현도 같이 들어가 merge됐다.
상세는 `.ai/STATE.md` 참고.
이 컴퓨터에 `gh` CLI가 없어서 이슈/PR은 AI가 텍스트만 만들고 사용자가 GitHub 웹에서 직접 생성한다.

**추가로 develop에 merge된 것들(2026-08-09, PR #51 이후)**:
- **PR #53**(`fix/rerecommend-quantity-validation`) — 재추천 `quantity` 누락 NPE(MT_012) + `type=INITIAL` 우회(MT_013) 수정 2건. merge 완료.
- (`feature/matching-negotiation-outcome`) — `MatchingNegotiationOutcomeUseCase` 구현 + budgetCap WEEK→개월 확정. merge 완료.
- **5번(협상)이 반대쪽도 완료**: `NegotiationLoopService`가 타결/결렬 시 `MatchingNegotiationOutcomeUseCase.markNegotiationAgreed/markNegotiationFailed`를 호출하도록 배선 완료(commit "협상 타결/결렬 시 매칭 요청 상태 갱신 배선"). 14번 항목 양쪽 다 완료.

**`fix/matching-request-card-snapshot-read` 브랜치 신규 오픈 (develop 대상, PR 생성 대기).** 아래 23번
항목(매칭 요청 카드가 프로젝트 정보를 라이브로 읽던 R32 위반 버그) 수정 — `./gradlew clean build` 통과
확인, push 완료.

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

9. ~~`Pairing-python/app/domains/matching/service.py`의 `_build_prompt` 실제 구현~~ — 2026-08-09 완료. 스프링 소유 테이블(project/project_position/position_skill/freelancer_condition/resume/resume_career)을 읽기 전용으로 직접 조회하는 `DirectoryRepository`(`domains/matching/repository.py`) 신규 추가, 포지션 요구조건 전문과 프리랜서 이력(자기소개/경력사항/스킬)을 프롬프트에 채움. 벡터 유사도는 후보 풀 좁히는 용도로만 쓰고 판단 근거에서 제외. `reason`을 `"|"`로 이어붙인 근거 목록으로 받도록 프롬프트/스키마 설명 변경(스프링 `CandidateResponseAssembler.splitFitReasons`가 이 구분자로 다시 나눔). 단위 테스트(`tests/test_matching_service.py`, mock 기반) + `pytest`/`ruff` 통과 확인. `feature/matching-prompt-real-implementation` 브랜치, `develop` 대상 PR 오픈 완료.
10. ~~`Pairing-python`의 `search_similar_freelancers`에 하드필터 추가~~ — **2026-08-09 완료.**
    - **AI매칭 동의 + 직군/직무**: `EmbeddingRepository.search_similar_freelancers`가 벡터 검색 자체에서 `freelancer_profile`(ai_matching_agreed) + `account`(status=ACTIVE) + `freelancer_condition`(job_category/job_role)을 조인해서 미리 걸러낸다(사전검수 P02와 동일 기준이라 후보수 불일치 위험 없음). `MatchingService.recommend()`가 포지션 조회를 벡터 검색보다 먼저 하도록 순서를 바꿔서 job_category/job_role을 넘긴다.
    - ~~일정/근무조건/단가(느슨하게)~~ **폐기 확정.** 필터링하지 않고 Stage E(LLM 최종선정) 프롬프트가 감점+추천사유로만 반영하도록 남겨둠(다음 항목). 상세 근거는 `.ai/STATE.md` "2026-08-09 갱신 — Stage B 조건필터 폐기" 참고.
    - **이전에 노출된 프리랜서 제외도 같이 이동 완료.** Java `MatchingRoundCreationService.excludePreviouslySurfaced`(결과 받은 뒤 후처리)를 삭제하고, `matchingCandidateRepository.findFreelancerIdsByProjectId(projectId)`를 미리 조회해 `MatchingPort.recommend(..., excludedFreelancerIds)`로 넘긴다. Python `MatchingRequest.excluded_freelancer_ids` 신규 필드로 받아서 `EmbeddingRepository`가 벡터 검색 SQL에 `NOT IN`으로 반영 — 풀 크기가 줄어드는 문제 해결.
    - 착수 전 실제 검증 중 Pairing-python `_build_prompt`(9번)의 컬럼명 버그 2건 발견·수정 — `.ai/STATE.md` 참고, `fix/directory-repository-column-names` 브랜치 merge 완료.
10-2. **(신규, 다음 작업)** Stage E 감점: 일정/근무조건/단가 불일치를 LLM이 판단하게 하려면 Python `FreelancerProfile`(pay_unit/pay_amount/work_style/work_form/available_from)과 `PositionRequirement`(budgetAmount/periodValue/periodUnit/startDesiredDate)에 조건 필드를 추가하고 `_build_prompt`에 "불일치해도 제외 말고 감점+사유" 지시를 넣어야 한다. 아직 미착수.
10-1. ~~(신규 발견, 2026-08-09) 프리랜서 임베딩이 실환경에서 한 번도 생성되지 않고 있었음~~ — **완료.** 전체 파이프라인(Stage A~F) 재검증 중 발견: `MatchingPort`에 `upsertFreelancerEmbedding`이 아예 없었고, freelancer 도메인 어디에도 매칭을 호출하는 코드가 0건이었다(프로젝트 쪽 `upsertPositionEmbedding`은 정상 연결돼 있었음). `.ai/STATE.md` "확정된 설계 결정 4"에 이미 "프리랜서는 조건 저장 시마다 PUT 호출"이라고 적혀 있었는데 실제로는 구현이 안 된 상태였음. `freelancer.application.event.ResumeUpdatedEvent`(이력서 저장 후 발행) 신규 + 매칭의 `ResumeUpdatedEventListener`가 받아서 `FreelancerDirectoryPort.findResumeSummary`(자기소개+경력사항)로 텍스트 조립 후 `MatchingPort.upsertFreelancerEmbedding` 호출. 트리거는 이력서(Resume) 저장 시점 — 임베딩 텍스트가 자기소개/경력사항(Resume 필드)이라 조건(Condition) 저장 시점이 아니라 이력서 저장 시점에 걸었다. `ResumeUpdatedEventListenerTest` 추가, `./gradlew clean build` 통과. `feature/matching-freelancer-embedding-trigger` 브랜치.
11. **(2026-08-09 착수 보류 결정, 사용자 확인)** `budgetCap`을 1단계 단순 공식(구현 완료, `BudgetCapCalculator`)에서 Stage F 정확한 배분 알고리즘(포지션별 1순위 조합 → 초과시 비싼 포지션을 다음 순위로 교체)으로 교체하고, Stage F 가드(`MatchingRoundCreationService`의 `applyGuard(true, null)` placeholder)도 실제 검증으로 교체하는 항목. 착수 전 확인해보니 두 가지가 막혀 있었음:
    - ~~가드가 검증할 프리랜서 실제 스킬/단가 데이터는 `FreelancerDirectoryPort.findCondition()`으로 가져오는데, 이게 아직 고정값을 돌려주는 스텁이었다~~ — 2026-08-09 해소. 2번이 "account 도메인 승인은 이미 끝났고 필요한 포트(`AccountQueryUseCase.findFreelancerProfileById/ByAccountId`, `FreelancerConditionUseCase.findMyCondition`)도 이미 다 있는데 매칭이 어댑터만 안 바꿔놨다"고 확인해줌 — `FreelancerDirectoryAdapter`(12번 참고) 실구현으로 교체 완료.
    - ~~"포지션별 1순위 조합 → 교체" 배분 규칙의 정확한 알고리즘(교체 순서, 종료 조건 등)이 이 리포 안에 문서로 없고, PM 상태표에도 "2번(조건)/3번(예산·정산 정책) 확정 필요"로 남아있어 임의로 정하면 다시 뜯어고칠 위험이 큼~~ — 2026-08-09 **결론**: 3번이 정책·요구사항 전수 확인한 결과 그런 확정 규칙은 원래 존재한 적이 없었음(이 항목의 전제 자체가 잘못된 것으로 확인됨). 포지션별 예산/기간 입력란도 없어(클라이언트는 총액만 입력) 배분 알고리즘을 만들 재료 자체가 없음. **현재 공식(순예산÷인원÷개월수, A안) 그대로 유지하기로 확정 — 코드 변경 없음.** 경력별 차등(B안)·LLM 조합 배분(C안)은 "AI가 예산까지 배분한다"가 제품 포인트로 필요할 때 팀 회의에서 다시 논의.
    - ~~Stage F 가드(`MatchingRoundCreationService`의 `applyGuard(true, null)` placeholder) 실제 구현~~ — **2026-08-09 완료.** R02.3 요구사항 원문("가드 AI로 마지막 검증 (직무, 스킬 검증)")을 그대로 따라 **직무+스킬만** 재검증한다 — 예산 조합 재검증은 이번에도 요구사항에 근거가 없어서(Stage B 조건필터 폐기와 같은 사유) 넣지 않았다. `findCondition(freelancerId)`로 실제 조건을 가져와 포지션의 jobRole/requiredSkills(`ProjectDirectoryPort.findPositionSummary`, 실시간 조회)와 비교한다. 가드에 떨어진 후보는 레코드는 남기되(`guardPassed=false`) 노출(`expose`)하지 않고 다음 순위 후보가 그 노출 자리를 채운다. `RecruitingStartedEventListenerTest`가 그동안 `freelancer_condition` 없이 통과하던 것도 이번에 드러나서(가드가 findCondition을 실제로 부르니 없으면 예외) 실제 조건을 심도록 수정. `MatchingIntegrationTest`에 가드 탈락 시나리오(점수는 더 높지만 요구 스킬이 없는 후보가 스킵되고 다음 후보가 노출되는지) 신규 검증 추가. `feature/matching-stage-f-guard` 브랜치.
12. ~~freelancer/project/negotiation 도메인이 실제로 만들어지면 스텁 어댑터 3개 교체~~ — 2026-08-08, `feature/matching-directory-adapters` 브랜치에서 완료(Project/Negotiation 완전 교체, Freelancer는 카드 요약만). ~~이슈·PR 생성~~ — 2026-08-09 **PR #51** 오픈 완료(develop 대상). 첫 push 때 CI가 재추천 테스트에서만 500 실패 → `/rerecommendations`의 레이트리밋이 Redis 없는 CI에서 실제 연결을 시도해서였음, 테스트에서 `RateLimitProvider` 목 처리로 해결·재push 완료. **다음 세션 최우선: CI 그린 확인하고 팀원 리뷰/머지 대기.**
    - ~~프리랜서 등급 타이브레이커(base_score 동점 시 마스터>시니어>주니어)는 아직 랭킹 로직에 미반영~~ — 2026-08-09 완료. `MatchingRoundCreationService.breakScoreTiesByGrade`가 점수 내림차순+동점 시 등급 내림차순 안정 정렬을 수행(LLM이 준 순서는 점수·등급이 모두 같을 때만 유지됨). `feature/matching-freelancer-grade-tiebreaker` 브랜치.
    - ~~`resolveFreelancerId`/`findCondition`(freelancerId 기준)은 account 도메인의 account_id↔freelancer_profile.id 조회 메서드가 나와야 완전 교체 가능~~ — 2026-08-09 완료. `AccountQueryUseCase.findFreelancerProfileByAccountId/ById` + `FreelancerConditionUseCase.findMyCondition`으로 실구현 교체. 둘 다 못 찾으면 `MatchingErrorCode.FREELANCER_NOT_FOUND`(MT_015, 신규). `MatchingIntegrationTest`에 실제 `freelancer_condition` 시드 추가(기존엔 findCondition 스텁이라 필요 없었음). `feature/matching-freelancer-directory-real-impl` 브랜치.
    - `src/test/java/com/pairing/matching/presentation/api/MatchingIntegrationTest.java`(H2 통합테스트, 후보조회/거절/요청발송/조회/수락/거절/재추천 9개 케이스)로 검증 완료 — Swagger 수동 클릭 대신 이 테스트를 돌려서 확인하면 됨. PR Verification 섹션에 이 테스트 통과를 근거로 적을 것.
13. ~~통합 테스트, `.ai/API.md`/`docs/api-dto.csv` 최종 동기화, 에러코드 매핑 점검~~ — 2026-08-09 완료. `.ai/API.md` 에러 코드 표에 MT_001~MT_015 전부 추가(기존엔 매칭 에러코드가 하나도 없었음). `docs/api-dto.csv`의 `RerecommendRequest.type`/`quantity` 설명에 실제 검증 규칙(MT_012/MT_013) 명시. 겸사겸사 `docs/spec/requirements.md` 클라이언트 회원가입 명세에 회사주소 필드도 반영(`feature/client-address-plus`로 이미 구현·merge된 게 명세엔 빠져 있었음).
14. ~~`MatchingNegotiationOutcomeUseCase`(협상 결렬/타결 통보 인바운드 포트) 구현~~ — 2026-08-09 매칭 쪽 구현 완료(`feature/matching-negotiation-outcome` 브랜치, develop 대상 PR 생성 대기).
    - `markNegotiationAgreed(requestId)` → `MatchingRequest.agreeNegotiation()`(신규 도메인 메서드, NEGOTIATING일 때만 허용) → `CONTRACT_PENDING`.
    - `markNegotiationFailed(requestId)` → 기존 `MatchingRequest.failNegotiation()` 그대로 사용 → `NEGOTIATION_FAILED`.
    - 존재하지 않는 requestId는 `MatchingErrorCode.REQUEST_NOT_FOUND`, NEGOTIATING이 아닌 상태에서 호출하면 `INVALID_MATCHING_STATE`.
    - 단위 테스트 `MatchingNegotiationOutcomeServiceTest`(4개: 타결/결렬/요청없음/상태불일치) 작성, `./gradlew build` 통과 확인.
    - ~~아직 남은 것(negotiation/5번 쪽 작업)~~ — 2026-08-09 5번이 `NegotiationLoopService`에 배선 완료, develop에 merge됨. 양쪽 다 완료.
15. ~~`currentSituation`/`mainTask`(프로젝트 현재 상황/담당 업무) 노출 여부 팀 답변 오면 `MatchingRequestResponse`에 필드 추가 여부 결정~~ — **2026-08-09 완료.** 3번 답변: `mainTask`만, 카드 아니라 요청 상세(`GET /requests/{requestId}`)에서만 노출. `currentSituation`은 노출 안 함. 구현하며 `findRequest()`의 클라이언트 접근 시 항상 404 나던 버그도 같이 발견·수정. `.ai/STATE.md` "매칭 요청 상세에 mainTask 노출" 참고. `feature/matching-request-detail-main-task` 브랜치.
16. ~~budgetCap이 포지션 인원/총액 단위로 잘못 계산되던 버그 2건(3번 리포트)~~ — 2026-08-09 수정 완료. `position.headcount()`→`totalHeadcount()`, budgetAmount를 개월 수로도 나누도록 수정. ~~WEEK 기간 주→개월 환산 규칙~~ — 2026-08-09 **4주=1개월로 확정**(사용자 확인). `negotiation` 도메인의 `NegotiationConditionCalculator`도 이미 같은 값(4주=1개월)을 쓰고 있어서 두 도메인 계산 기준이 일치함을 재확인. `BudgetCapCalculator`의 `TEMP_WEEKS_PER_MONTH`(임시값 표기)를 `WEEKS_PER_MONTH`로 정리.
17. **(참고, 재발 방지)** `@TransactionalEventListener` 안에서 `@Transactional(REQUIRES_NEW)` 메서드를 "같은 빈 안에서 `this.method()`로" 부르면 스프링 프록시를 안 거쳐 트랜잭션이 조용히 무시된다(자체 호출 self-invocation 문제). 실제로 이 버그로 라운드 저장이 안 되는 걸 테스트로 재현해서 발견 — 새 트랜잭션이 꼭 필요한 메서드는 반드시 별도 빈으로 분리해서 호출할 것(`RecruitingStartedEventListener`/`RecruitingStartedPositionHandler` 참고).
18. **(참고, 재발 방지)** 재추천 엔드포인트의 레이트리밋(`RateLimitProvider`)은 Redis가 실제로 있어야 동작한다. 이 엔드포인트를 호출하는 테스트를 새로 짤 땐 `RateLimitProvider`를 `@MockitoBean`으로 목 처리해야 Redis 없는 CI에서도 통과한다(`MatchingIntegrationTest` 참고). 로컬은 Redis 컨테이너가 떠 있어서 이 문제가 안 드러나니 착각하지 말 것.
19. ~~`AI매칭_API_화면매핑_최신본.md`(레포 밖, 프론트 공유용 문서) 검토~~ — 2026-08-09 완료. MT_009(dead code, 실제론 201+빈 배열) 제거, `RerecommendRequest.quantity` PAID 필수·검증 없음 경고를 사용자가 문서에 반영, 재검토까지 끝남.
20. ~~`RerecommendRequest.quantity`에 `type=PAID`일 때만 필수가 되는 조건부 검증이 없다~~ — 2026-08-09 수정 완료. `MatchingErrorCode.QUANTITY_REQUIRED`(MT_012) 추가, `MatchingRerecommendService.rerecommend()` 초입에 `type == PAID && quantity == null`이면 던지도록 처리. 회귀 테스트(`MatchingIntegrationTest.rerecommendPaidWithoutQuantityReturnsBadRequest`) 추가. `fix/rerecommend-quantity-validation` 브랜치, PR 생성 대기.
21. ~~`RerecommendRequest.type`이 `INITIAL`도 그대로 받아버리는 문제~~ — 2026-08-09 수정 완료. 코드 리뷰로 발견: `type == FREE`가 아니면 전부 유료 취급하는 구조라 `type=INITIAL`을 보내면 (a) `quantity`가 없으면 20번 수정으로도 못 막던 NPE, (b) `quantity`가 있으면 재추천 API로 `INITIAL` 태그 라운드가 만들어지는 오동작이 가능했음(20번 수정만으론 해결 안 됨). `MatchingErrorCode.INVALID_RERECOMMEND_TYPE`(MT_013) 추가, `rerecommend()` 최초 진입부에 `type`이 FREE/PAID가 아니면 던지도록 처리. 회귀 테스트(`MatchingIntegrationTest.rerecommendWithInitialTypeReturnsBadRequest`) 추가. 20번과 같은 브랜치(`fix/rerecommend-quantity-validation`)에 포함.
22. ~~임베딩 텍스트에 `detailScope`(업무범위)/`extraNote`(우대사항)가 빠져있다~~ — 2026-08-09 완료. `currentSituation`/`mainTask`는 Task #4(팀 답변 대기) 그대로 보류하고, `detailScope`/`extraNote`만 project의 `ProjectPositionSummary`(project.application.result)와 매칭의 로컬 `ProjectPositionSummary`, `ProjectDirectoryAdapter.findPositionSummary`, `PositionEmbeddingTextBuilder.buildText`까지 전파했다. `feature/matching-embedding-detail-fields` 브랜치.
23. ~~매칭 요청 카드가 프로젝트 정보를 라이브로 읽던 문제(R32)~~ — 2026-08-09 수정 완료, **PR #59로 develop에 merge됨**. 3번이 프로젝트 수정 API 구현 중 발견: `MatchingRequestResponseAssembler`가 `ProjectDirectoryPort.findPositionSummary`로 매번 라이브 조회를 해서, 결제 후 클라이언트가 프로젝트를 수정하면 이미 수락된 요청 카드 내용(제목/직무/스킬/경력/근무조건/기간/시작일)이 바뀌는 문제였다. 모집 시작 시점에 얼려두는 `MatchingSnapshot`(PROJECT/POSITION)을 읽도록 교체. `companyProfile`(account 도메인 값)만 `ProjectDirectoryPort.findCompanyProfile` 신규 추가해 계속 라이브로 읽는다. 3번 확인: 최초 모집 시작 시점 고정이 맞고(재추천마다 다시 얼리면 "수정 전" 기준이 계속 밀림), 결제 후 인원/포지션/예산은 이미 잠겨서 재추천 시 재계산해도 값이 안 바뀜.
24. ~~모집 시작 후 프로젝트 수정 시 포지션 임베딩 재생성~~ — 2026-08-09 완료, **PR #61로 develop에 merge됨**. 3번이 같은 PR(#57, 프로젝트 수정 API)에서 `ProjectUpdatedEvent(projectId)`(수정 커밋 후 발행, headcount 잠긴 이후=결제 완료 후에만)를 새로 만들어놨는데 매칭 쪽에 받는 곳이 없던 걸 develop 재동기화 중 발견. `ProjectUpdatedEventListener` 신규 추가 — 프로젝트의 포지션마다 최신 정보로 임베딩 재생성(`MatchingPort.upsertPositionEmbedding`). **23번(요청 카드 스냅샷)과는 반대 방향이라 안 헷갈릴 것**: 요청 카드용 `MatchingSnapshot`은 절대 안 건드림(최초 모집 시작 시점 고정 유지, R32), AI 검색용 임베딩만 최신화(검색 정확도 문제라 최신이 맞음). 임베딩 텍스트 조립 로직은 `RecruitingStartedPositionHandler`와 공유하도록 `PositionEmbeddingTextBuilder`로 추출. 테스트 3개(`ProjectUpdatedEventListenerTest`: 포지션별 재생성/포지션 단위 예외 격리/스냅샷 불변) 작성, `./gradlew clean build` 통과.
25. ~~매칭↔프로젝트 상태 연동 3건 (3번 코드 리뷰로 발견)~~ — 2026-08-09 수정 완료.
    - **프로젝트 상태 체크**: 매칭이 `ProjectStatus`를 전혀 참조 안 해서 모집 종료·취소된 프로젝트에도 매칭 요청 발송/재추천이 됐다(모집 종료로 강제 마감된 포지션은 인원이 안 찼는데도 CLOSED라 인원 초과 검증만으론 못 막음). `ProjectDirectoryPort.findStatus(projectId)` 신규 추가(project의 기존 `ProjectQueryUseCase.findStatus` 위임). `MatchingRequestService.sendOneRequest()`/`MatchingRerecommendService.rerecommend()` 양쪽에 `assertRecruiting()` 추가 — **CANCELED/CLOSED만 막음**(RECRUITING 엄격 강제 아님). 같은 프로젝트의 다른 포지션이 협상중/계약대기로 앞서가도 이 포지션은 여전히 열려있을 수 있어서 판단해서 이렇게 함(3번이 판단을 맡김).
    - **수락 시 프로젝트를 협상중으로**: `MatchingRequestService.accept()` 마지막에 `ProjectCommandUseCase.startNegotiating(projectId)` 추가. 같은 트랜잭션이라 프로젝트가 이미 취소·종료면 PJ_012가 그대로 올라가 수락도 롤백됨(매칭에서 따로 안 잡음, 3번이 이미 그렇게 설계).
    - **협상 결렬/거절 시 프로젝트 단계 재계산**: `MatchingNegotiationOutcomeUseCase.markNegotiationFailed()`와 `MatchingRequestCommandUseCase.reject()`(직접 거절) 양쪽에서 `ProjectCommandUseCase.syncStage(projectId, hasContractPending, hasNegotiating)` 호출. 두 boolean은 그 프로젝트의 매칭 요청 전체를 새로 카운트해서 계산(`MatchingRequestRepository.existsByProjectIdAndStatusIn` 신규 추가) — **`existsActiveByProjectId()`는 안 씀**(P41 무료 재추천 판정용이라 REQUEST_PENDING도 "있음"으로 세서 기준이 다름, 3번이 명시적으로 경고). `markNegotiationAgreed()`(타결)는 3번이 계약 도메인에서 직접 `awaitContract()` 호출할 거라 안 건드림. `expire()`(응답기한 만료)는 실제로 아무도 호출 안 하고 있어서(선행 기능 자체가 미구현) 지금은 훅 지점이 없음 — 나중에 만들 때 같이 챙길 것.
    - 회귀 테스트 추가: `MatchingIntegrationTest`(CLOSED/CANCELED 프로젝트 차단 2건, accept 시 프로젝트 상태 전이 검증), `MatchingNegotiationOutcomeServiceTest`(결렬 시 프로젝트 RECRUITING 복귀 검증). `feature/matching-project-stage-sync` 브랜치, PR 생성 대기.

## 열려있는 결정/블로커 (건드리기 전에 확인)

- 적합도 점수 스케일 0~100 여부, 골드 등급 수수료 할인, 프로젝트 인원별 예산배분 필드 존재 여부 — `.ai/STATE.md` 하단 표 참고, 팀 확인 대기 중이라 확정 전까지는 가정값으로 진행. (budgetCap의 WEEK→개월 환산 규칙은 2026-08-09 4주=1개월로 확정됨, `currentSituation`/`mainTask` 노출 여부도 2026-08-09 확정·구현 완료, 더 이상 대기 항목 아님)
- **(신규, 낮은 우선순위)** 프론트 공유 문서(`docs/personal/AI매칭_API_화면매핑_최신본.md`)가 뒤처짐 — MT_012/MT_013 검증이 이미 코드에 있는데 문서엔 아직 "검증 없음, 500 위험"이라고 남아있음(3번 코드 리뷰로 발견). MT_013/MT_014도 에러 코드 표에 없음. 사용자가 관리하는 개인 문서라 다음에 사용자가 갱신할 항목으로 안내.

## 참고할 실제 파일

- Pairing-python 계약 문서: `C:\52_Pairing\Pairing-python\README.md`
- Spring↔AI서버 연동 참고 구현: `C:\Algoga_V3_backend`의 `com.kidmily.algoga_server.chatbot` 도메인 (Port/Adapter/서킷브레이커/레이트리밋/내부API 전부 실제 코드로 있음)
- 매칭 DTO 계약: `com.pairing.matching.presentation.api.*` (이미 확정, 필드 변경 시 `.ai/API.md`와 `docs/api-dto.csv` 같이 갱신)
- 프론트 전달용 API-화면 매핑 문서(레포 밖, 사용자가 직접 관리): `C:\Users\user\Desktop\AI매칭_API_화면매핑_최신본.md`

## 2026-08-10 신규 — freelancer `/me/matching-settings` 스텁 교체 (원래 2번 담당, 사용자가 직접 진행하기로 함)

마이페이지 AI매칭 토글이 `FreelancerController`에서 저장 없이 에코만 하는 스텁이었음을 발견. 원래 freelancer 도메인(2번) 파일이라 4번이 건드릴 일이 아니었는데, 사용자가 직접 만들기로 결정 — **freelancer 폴더를 건드리는 걸 알고 진행하는 것**(도메인 경계 예외).

착수 전 조사 완료, 상세는 `.ai/WORKLOG.md` "freelancer `/me/matching-settings` 스텁 교체 착수" 참고. 요약:
- `db/init/02-create-schema.sql`에 `matching_paused` 컬럼 이미 있음 — 스키마 변경 불필요.
- `FreelancerProfile`/`FreelancerProfileJpaEntity`에 `matchingPaused` 필드 매핑만 추가하면 됨.
- `matchable` = `aiMatchingAgreed && !matchingPaused && ResumeStatus.COMPLETED`(추정, 구현하며 확정).
- `FreelancerController`의 두 엔드포인트(`GET`/`PUT /me/matching-settings`) 실구현으로 교체.
