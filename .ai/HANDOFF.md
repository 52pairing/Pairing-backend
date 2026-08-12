# 인수인계 — AI매칭(4번 파트)

> ## 🔵 지금 상태 (2026-08-12) — 새 세션은 여기부터 읽는다
>
> **매칭 파이프라인 재설계를 코드로 옮기는 중이다.** 설계는 전부 확정됐고 남은 건 실행이다.
>
> **읽는 순서**
> 1. 이 박스
> 2. 아래 **"▶ 다음에 할 일"** 표 — 순서대로 하면 중간에 안 깨진다
> 3. `.ai/STATE.md`의 **"2026-08-11 갱신 — 매칭 파이프라인 재설계"**(설계) +
>    **"2026-08-12 확정 — 착수 전 결정 13건"**(왜 그렇게 정했나)
> 4. 이 문서 맨 아래 **"주의 사항"** — 반복해서 걸린 것들
>
> **작업 중인 브랜치 2개** (둘 다 push 완료, PR 아직 안 올림)
>
> | 레포 | 브랜치 | 담긴 것 | 남은 것 |
> |---|---|---|---|
> | backend | `feature/matching-embedding-text-redesign` | B1 임베딩 텍스트, B2 budgetCap, 수수료율 하드코딩 제거 | **B4 가드 교체** + 7번 자바 수신 |
> | python | `feature/matching-condition-score` | B2 budget_cap, B3 조건점수 25:75, 7번 similarity, 13번 CI pgvector | **없음 — PR 올리면 된다** |
>
> **핵심 숫자 (자주 헷갈린다)**
> - 최종 점수 = 유사도 **25** + 조건점수 **75** (조건 배점 합 100을 0~1로 정규화 후 ×75)
> - 조건 배점: 스킬 30 / 연차 20 / 단가 20 / 근무방식 10 / 근무형태 8 / 시작일 6 / 기간 6
> - 월단가 환산: **일급 ×20, 시급 ×160, 4주 = 1개월** (협상 도메인과 같은 값이어야 한다)
> - 후보 풀 = 모집 인원 × 3, 노출 = 모집 인원
>
> **DB 환경 — 로컬·배포 둘 다 준비 완료 (2026-08-12)**
>
> | | pgvector | 임베딩 테이블 | 데이터 |
> |---|---|---|---|
> | 로컬 (윈도우 네이티브 **PostgreSQL 18**) | **0.8.6** ✅ | ✅ | 0행 |
> | 배포 | ✅ | ✅ | freelancer 1 / position 7 (2026-08-11 생성) |
>
> - **로컬은 도커가 아니라 네이티브 PG18을 쓴다.** 도커 `pairing-postgres`(PG16)는 5432가
>   충돌하므로 **꺼둔다** — Redis만 띄운다: `docker compose up -d redis`
> - 스프링 스키마(51개 테이블)와 `freelancer_profile.matching_paused`도 이미 있다
> - `psql`은 PATH에 없다. 전체 경로로 부른다:
>   `"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U pairing -d pairing ...`
> - 배포 벡터는 **B1 이전(옛 규칙)** 이라 B5 재색인 대상이다
>
> **아직 한 번도 안 해본 것**
> - **C1 end-to-end 테스트.** 이력서 저장 → 임베딩 → 모집 시작 → 추천 → 요청 → 수락을 실제로
>   돌려본 적이 없다. **남은 것 중 가장 큰 리스크다.** 이제 DB가 준비됐으니 막을 것은 없다.
>
> **사람 대기 중**
> - **E2 회신만 남았다** — 3번이 정책 P03 제안 문구를 보내와 검토를 요청했고, 회신문은
>   아래 "E2 회신" 절에 써뒀다. **보내면 전달 항목은 전부 종료된다.**

이어서 작업할 때는 이 문서 + `.ai/STATE.md`를 먼저 읽는다. 아래는 이력이다.

**2026-08-10 배치.** `feature/freelancer-matching-settings`(freelancer `/me/matching-settings` 실구현 +
matchingPaused 하드필터 반영, 원래 2번 담당이지만 4번이 직접 진행), `feature/matching-request-auto-expire`
(응답기한 3일 자동 만료 스케줄러 + `rejectReason` 필드 노출), `feature/matching-embedding-reindex`
(임베딩 모델 교체 대응 관리자 일괄 재색인 API, Pairing-python 팀원 요청) 작업 완료, push 대기/완료 상태는
`.ai/WORKLOG.md` 최신 항목 참고. `MatchingRequestResponse.rejectReason`(DIRECT_REJECT/EXPIRED/
NEGOTIATION_FAILED) 신규 — 직접 거절과 자동 만료를 프론트가 구분할 수 있게 함.

**3일차 배치(2026-08-09).** `feature/matching-embedding-detail-fields`(22번)/`feature/matching-freelancer-grade-tiebreaker`(12번 하위)/`feature/matching-project-stage-sync`(25번)/`feature/matching-freelancer-directory-real-impl`(12번 하위) 전부 develop에 merge 완료. Pairing-python `feature/matching-prompt-real-implementation`(9번)은 push 완료, develop 대상 PR 오픈해서 리뷰/머지 대기 중. **11번(budgetCap Stage F)은 A안(현행 유지) 확정, 코드 변경 없음** — 아래 11번 항목 참고. **10번(Pairing-python 하드필터)도 2026-08-09 완료.** **10-2번(Stage E 감점)도 2026-08-10 완료** — 아래 10-2번 항목 참고.

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
10-2. ~~Stage E 감점~~ — **2026-08-10 완료.** Python `FreelancerProfile`(pay_unit/pay_amount/work_style/work_form/available_from/start_negotiable/period_value/period_unit)과 `PositionRequirement`(budget_amount/period_value/period_unit/start_desired_date/start_negotiable)에 조건 필드 추가, `DirectoryRepository` SQL도 함께 확장(프리랜서 쪽은 `string_agg` 때문에 GROUP BY에도 추가 필요). `_build_prompt`에 "어긋나도 제외 말고 감점+사유" 지시 + `start_negotiable` 항목은 감점 제외 + 총예산(프로젝트 전체)과 1인 월단가를 직접 비교하지 말라는 주의 추가. 값 없는 항목은 줄 자체를 빼서 `None`이 조건 값으로 오해되지 않게 함. `tests/test_matching_service.py`에 3건 추가(조건 값 프롬프트 반영, 협의가능 표시, 값 없을 때 줄 생략) — 전체 22건 통과, ruff 통과. `feature/matching-stage-e-condition-penalty` 브랜치. **API 응답 모양은 안 바뀜**(기존 `reason` 문자열에 감점 사유가 항목으로 추가될 뿐).
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
    - **협상 결렬/거절 시 프로젝트 단계 재계산**: `MatchingNegotiationOutcomeUseCase.markNegotiationFailed()`와 `MatchingRequestCommandUseCase.reject()`(직접 거절) 양쪽에서 `ProjectCommandUseCase.syncStage(projectId, hasContractPending, hasNegotiating)` 호출. 두 boolean은 그 프로젝트의 매칭 요청 전체를 새로 카운트해서 계산(`MatchingRequestRepository.existsByProjectIdAndStatusIn` 신규 추가) — **`existsActiveByProjectId()`는 안 씀**(P41 무료 재추천 판정용이라 REQUEST_PENDING도 "있음"으로 세서 기준이 다름, 3번이 명시적으로 경고). `markNegotiationAgreed()`(타결)는 3번이 계약 도메인에서 직접 `awaitContract()` 호출할 거라 안 건드림. ~~`expire()`(응답기한 만료)는 실제로 아무도 호출 안 하고 있어서(선행 기능 자체가 미구현) 지금은 훅 지점이 없음~~ — **2026-08-10 완료.** `MatchingRequestExpiryScheduler`(10분 주기)가 호출하고, `accept()`/`reject()`도 스케줄러 주기 사이 창구를 막기 위해 진입 시 `isExpired()`를 먼저 확인해서 지났으면 그 자리에서 만료 처리 후 `MT_016`으로 막는다. `MatchingRequestExpirer`(REQUIRES_NEW)로 커밋을 분리해서, 뒤이어 던지는 예외가 방금 커밋한 만료 처리까지 롤백시키는 문제(리뷰로 발견)도 같이 고쳤다.
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

**구현 완료.** 상세는 `.ai/WORKLOG.md` "freelancer `/me/matching-settings` 실구현 완료" 참고. 요약:
- `db/init/02-create-schema.sql`의 `matching_paused` 컬럼에 JPA/도메인 매핑 연결 완료.
- `FreelancerProfile`/`FreelancerProfileJpaEntity`/`FreelancerProfileMapper`에 `matchingPaused` 추가, `AccountCommandUseCase.updateFreelancerMatchingSettings` 신규.
- `matchable` = `aiMatchingAgreed && !matchingPaused && 이력서 존재`, 사유 우선순위는 동의 미비 > 일시중지 > 이력서 미완성으로 확정.
- `FreelancerController`의 두 엔드포인트(`GET`/`PUT /me/matching-settings`) 스텁 제거, 실구현으로 교체. `FreelancerMyPageIntegrationTest`에 H2 통합테스트 4개 추가.
- `feature/freelancer-matching-settings` 브랜치, 코드+테스트+문서 같은 커밋으로 push.

## ~~2026-08-11 — 매칭 파이프라인 재설계 (착수 전 계획)~~ ⚠️ **낡음, 따라가지 말 것**

> **이 절은 착수 전에 적어둔 계획이라 체크박스가 전부 비어 있고 숫자도 옛것(30:70, 조건점수 70)이다.**
> 실제 진행 상황과 확정 숫자는 **아래 "남은 작업 전체 목록 (2026-08-12 갱신)"** 이 최종본이다.
> 기록으로만 남긴다.

## 2026-08-11 신규 — 매칭 파이프라인 재설계 (팀 확정, 코드 미착수)

**설계 확정본은 `.ai/STATE.md` "2026-08-11 갱신 — 매칭 파이프라인 재설계(팀 확정)" 절이다.**
아래는 그걸 코드로 옮기는 작업 목록. 순서대로 하면 중간에 빌드가 깨지지 않는다.

### 0단계 — 되돌리기 (오늘 커밋 `0157334` 취소)

- [ ] `FreelancerEmbeddingTextBuilder`에서 조건 필드(직무·스킬·연차·근무조건·기간) 제거
- [ ] `ConditionUpdatedEvent` / `ConditionUpdatedEventListener` / `FreelancerConditionService`의
      발행 코드 삭제 — 조건이 임베딩에서 빠지므로 재생성이 불필요해짐
- [ ] `ConditionUpdatedEventListenerTest` 삭제, `FreelancerEmbeddingTextBuilderTest` 정리
- [ ] `FreelancerEmbeddingRefresher`는 **유지** — 이력서 저장/관리자 재색인이 같은 조립을 써야 한다
      (경로마다 다르게 조립하면 어디서 저장했느냐에 따라 같은 사람의 벡터가 달라진다)

### 1단계 — 임베딩 텍스트 확정 (Java)

- [ ] 프리랜서: 자기소개 + **학과 전부(`resume_education.major`, 학력 여러 개면 다)** +
      경력 **`job_description`만**(회사명·부서/직급 제거).
      `FreelancerResumeSummary`에 학과 목록 추가 + `FreelancerDirectoryAdapter` 매핑
- [ ] 포지션: **프로젝트명** + 진행상황(`currentSituation`) + 담당업무 + 업무범위 +
      우대사항(`extraNote`). 최소경력·근무조건·기간·요구스킬 **제거**
- [ ] `ProjectPositionSummary`에 `currentSituation` 추가 + 어댑터 매핑
- [x] ~~`preferred_note`~~ — **쓸 수 없음 확정(3번).** 우대사항이 프로젝트 단위로 통일되며
      폐기됐다(매핑·DTO·컬럼 전부 없음). `extra_note`만 쓴다

### 2단계 — budgetCap 전달 (Java → Python)

- [ ] Java: `BudgetCapCalculator`로 계산한 월단가를 `/recommendations` 요청에 추가
- [ ] Python: 요청 스키마에 `budget_cap` 추가

### 3단계 — 조건 점수 SQL (Python, 본 작업)

- [ ] `search_similar_freelancers` 확장: 하드필터에 **요구 스킬 1개 이상** 추가
- [ ] 조건점수 70 계산 — 채점식은 `.ai/STATE.md` [3] 표 그대로
- [ ] **유사도 컷을 없앤다.** 하드필터 통과자 전원에 대해 유사도를 계산하고
      `PERCENT_RANK`로 0~30 정규화 → `+ 조건점수×70` → **그 합계로** 상위 (인원×3)
      - 유사도로 먼저 자르면 조건 좋고 유사도 낮은 사람이 잘려 처음 문제로 돌아간다
      - 수만 명 규모가 되면 성능 재검토 필요(지금은 문제없음)

### 4단계 — 가드 교체 (Java)

- [ ] `evaluateGuard`에서 직무·스킬 재검증 제거
- [ ] G3 추가 — **포지션 단위 조합 합계**로 판정:
      `Σ(노출 후보 희망 월단가) ≤ budgetCap × 노출 인원 × 1.2`
      - 분모는 모집 인원이 아니라 **노출 인원**(후보 부족 시 2명이면 2명 몫으로 재야 한다)
      - **개인별로 재지 말 것** — [3] 조건점수 단가 15점이 이미 개인을 본다. 중복이고,
        시니어1+주니어2 같은 정상 구성을 오탐한다
      - **탈락시키지 말 것** — `guardPassed=true` 유지하고 `guardReason`에 사유만 남긴다.
        배제하면 Stage B 폐기 사유(단가로 거르면 사전검수 후보수와 어긋남)가 되살아난다
- [ ] G4 LLM 응답 이상 검증 추가(중복 ID / 인원 초과 / `reason` 누락) — **여기만 실제로 거른다**

### 5단계 — 배포 후

- [ ] **`POST /admin/embeddings/reindex` 1회 실행 필수.** 임베딩 텍스트 규칙이 바뀌므로 기존
      벡터가 전부 낡는다. 옛 규칙 벡터와 새 규칙 벡터가 섞이면 비교 자체가 무의미해진다
- [ ] 유사도 분포 측정 → 정규화 상수 확정 → 3단계에 반영

### 착수 전 결정 필요

**2026-08-11 전부 확정됨.** 근거는 `.ai/STATE.md` "착수 전 결정" 표 참고.

| # | 결정 |
|---|---|
| U1 | 포지션 단위 조합 합계, 탈락 없이 경고만 |
| U2 | 프로젝트명 임베딩에 **포함** |
| U3 | **순위 기반 정규화**(PERCENT_RANK), 하드필터 통과자 전원 대상. 실측 단계 불필요 |
| U4 | 예산 경고 화면에 **안 띄움**. `guardReason` 기록만 |
| U5 | 학력 여러 개면 학과 **전부** |
| U6 | 조건점수 채점식 확정 (STATE.md [3] 표) |
| U7 | `preferred_note`는 **폐기된 필드라 쓸 수 없음**(3번 확인). `extra_note`만 쓴다 |

### 3번과 협의 — 2026-08-11 완료, 남은 액션 없음

- [x] ~~`preferred_note`를 매칭까지 내려주기~~ — **불가.** 우대사항이 프로젝트 단위로 통일되며
      폐기됨(엔티티 매핑·DTO·공용 DB 컬럼 전부 없음). 되살리려면 등록 위저드+프론트까지
      열어야 해서 기획 결정 사안. **`extra_note`만 쓴다**
- [x] 프로젝트 임베딩 시점 = 결제 완료 — 동의. **3번이 정책 P03 문구를 코드에 맞춰 고쳐주기로 함**
- [x] `current_situation` 임베딩 사용 — 동의, project 도메인 정책 변경 없음
- [x] 사전검수(P02) 기준 유지 — 동의

> ⚠️ **`db/init/02-create-schema.sql`을 실제 스키마로 믿지 말 것.** 공용 DB는 `ddl-auto`라
> JPA 엔티티에서 생성된다. 그 SQL 파일에만 남고 실제로는 없는 컬럼이 있다
> (`position_skill.preferred_note`). **필드 존재는 JPA 엔티티/응답 DTO로 확인할 것.**

---

# 남은 작업 전체 목록 (2026-08-12 갱신)

설계 확정본은 `.ai/STATE.md`의 **"2026-08-11 갱신 — 매칭 파이프라인 재설계"** + **"2026-08-12
확정 — 착수 전 결정 13건"** 두 절이다. 결정 근거를 찾을 땐 후자를 먼저 볼 것.

---

## ▶ 다음에 할 일 (순서대로. 이 순서를 지키면 중간에 안 깨진다)

| 순서 | 할 일 | 어디 | 예상 |
|---|---|---|---|
| **1** | **E2 회신 보내기** (아래 "E2 회신" 절 그대로) — 나머지 4건은 종료됨 | 슬랙/이슈 | 2분 |
| ~~2~~ | ~~7번 `similarity` 응답 필드~~ | ✅ 완료 (2026-08-12) | |
| ~~3~~ | ~~13번 CI에 pgvector~~ | ✅ 완료 (2026-08-12) | |
| **4** | **python PR 올리고 머지** (10번: python 먼저) ← **여기** | GitHub 웹 | — |
| **5** | **B4 가드 교체** (G3+G4) + 7번 자바 수신 | backend `feature/matching-embedding-text-redesign` | 4시간 |
| **6** | **backend PR 올리고 머지** | GitHub 웹 | — |
| **7** | 11번 재색인 API 1회 + `REINDEX` | 배포 후 | 10분 |
| **8** | **C1 통합 테스트** — 남은 것 중 가장 큰 리스크 | 로컬 (DB 준비됨) | 0.5~2일 |
| **9** | C2 실제 Gemini 호출 품질 확인 | | 1~3시간 |
| **10** | D1 그라파나 / D2 트래픽 테스트 | | 5~7시간 |

> ~~12번 DB 환경~~ — **2026-08-12 완료.** 로컬(네이티브 PG18 + pgvector 0.8.6)·배포 둘 다 준비됐다.
> 위 "지금 상태" 박스 참고.
>
> **13번(CI pgvector)은 아직 안 했다** — 순서 3번.
>
> **프론트 렌더링은 4번 파트가 아니다(2026-08-12 확정).** 매칭 API는 응답 모양이 안 바뀌고
> 프론트 전달 문서도 나가 있으므로, 여기서 할 일은 없다. 화면 구현·렌더링은 프론트 담당.

---

## A. PR — **전부 머지 완료 (2026-08-12)**

| # | 레포 | 브랜치 | 상태 |
|---|---|---|---|
| A1 | backend | `fix/matching-settlement-response-and-policy-doc` | ✅ 머지 (PR #144, 충돌 2건 해소 후) |
| A2 | backend | `fix/matching-candidate-selection-guard` | ✅ 머지 |
| A3 | python | `feature/matching-llm-retry-and-pool-relax` | ✅ 머지 |

> A3에 넣었던 "후보 0명 시 풀 확대 재검색"은 **효과가 없는 코드였다.** `LIMIT n`이 0건을
> 돌려준 건 WHERE에 걸린 게 없다는 뜻이라 n을 키워도 결과가 같다. B3에서 **스킬 필터 완화**로
> 교체했다(`_RELAXED_POOL_MULTIPLIER` 삭제).

## B. 매칭 파이프라인 재설계 — 0~3단계 완료, 4~5단계 남음

0단계(되돌리기)는 `e2e7ef3`으로 완료.

**진행 중인 브랜치 2개:**

| 레포 | 브랜치 | 담긴 것 |
|---|---|---|
| backend | `feature/matching-embedding-text-redesign` | B1 + B2(Java). **B4도 여기에 얹는다** |
| python | `feature/matching-condition-score` | B2(Python) + B3. **7번·13번도 여기에 얹는다** |

### B1. 1단계 — 임베딩 텍스트 확정 (Java) — **완료 (2026-08-12)**

브랜치 `feature/matching-embedding-text-redesign`. `./gradlew clean build` 통과.

- [x] `FreelancerResumeSummary`를 **임베딩에 실제로 들어가는 것만** 담도록 재정의:
      `(selfIntroduction, majors, careerDescriptions)`. 기존 `CareerEntry`(회사명·부서/직급·
      담당업무) 레코드는 **삭제**했다 — 필드로 남겨두면 다시 넣기 쉬워서다
- [x] `FreelancerDirectoryAdapter`: `resume.educations()` → `major`, `resume.careers()` →
      `jobDescription`만 뽑아 매핑
- [x] `FreelancerEmbeddingTextBuilder`: 자기소개 + 학과 전부 + 경력 담당업무.
      **학과를 경력보다 앞에** 둔다(경력 건수엔 상한이 없어 뒤에 두면 잘려 나간다)
- [x] `ProjectPositionSummary`(매칭)에 `currentSituation` 추가 + `ProjectDirectoryAdapter` 매핑.
      project 쪽 DTO엔 이미 있어서 옮기기만 하면 됐다
- [x] `PositionEmbeddingTextBuilder`: 프로젝트명 + 진행상황 + 담당업무 + 업무범위 + 우대사항.
      직무·요구스킬·최소경력·근무조건·기간 **제거**
- [x] 양쪽 다 **값 없는 항목은 줄째로 뺀다** — 빈 줄이 남으면 그 자리에 의미 없는 토큰이 들어가,
      항목 수가 적은 프로젝트끼리 서로 비슷해 보이는 쪽으로 벡터가 밀린다
- [x] `FreelancerEmbeddingTextBuilderTest` 5건으로 확장, `PositionEmbeddingTextBuilderTest`
      **신규 4건**(원래 포지션 쪽엔 테스트가 없었다 — 이번 재설계의 발단이 된 "포지션에만 스킬이
      있어서 짝이 어긋난" 버그를 회귀로 고정)

> `preferred_note`(포지션별 우대사항)는 **쓸 수 없다** — 우대사항이 프로젝트 단위로 통일되며
> 폐기됐다(3번 확인). `extra_note`만 쓴다.

### B2. 2단계 — budgetCap 전달 (Java → Python) — **완료 (2026-08-12)**

Java는 B1과 같은 브랜치, Python은 `feature/matching-condition-score`(B3도 여기서 이어간다).

- [x] `MatchingPort.recommend(..., long budgetCap)` 파라미터 추가.
      `PythonMatchingAdapter`가 `budget_cap`으로 실어 보낸다
- [x] `MatchingRoundCreationService.fillCandidates`에서 **포지션 조회를 추천 호출보다 앞으로**
      옮기고 `BudgetCapCalculator`로 계산해 넘긴다
- [x] `@CircuitBreaker` 폴백(`recommendFallback`) 시그니처도 같이 맞춤 — **어긋나면 컴파일은
      통과하고 서킷이 열릴 때만 터진다**
- [x] Python: `MatchingRequest.budget_cap`(선택) → `service.recommend` → `_build_prompt`.
      값이 있으면 후보 희망급여를 월단가로 환산(**시급 ×160h / 일급 ×20d**)해 상한과 직접 비교시키고,
      **없으면 단가 비교를 금지**한다(총예산은 전체 인원×전체 기간이라 1인 월급과 자릿수가 달라,
      비교시키면 멀쩡한 후보가 전부 예산 초과로 감점된다 — 옛 배포와 섞여 도는 동안 실제로 난다)
- [x] 테스트: Java 2건(값 관통 + 통합테스트 verify), Python 3건

> 단가는 SQL 혼자 계산 못 한다. 프리랜서는 시급/일급→월단가 환산이, 포지션은 순예산
> (수수료율에 **클라이언트 등급** 필요 = account 도메인) ÷ 인원 ÷ 개월이 필요하다.

### B3. 3단계 — 조건점수 + 25:75 합산 (Python) — **완료 (2026-08-12)**

브랜치 `feature/matching-condition-score`. 81 passed + 1 skipped, ruff 통과.

- [x] `search_scored_candidates` 신규 — 하드필터에 **요구 스킬 1개 이상** 추가, **LIMIT 없음**
      (유사도로 자르지 않는다). 이력서 원문은 안 읽고 채점용 값만 가져온다
- [x] `matching/scoring.py` 신규 — **순수 함수만** 둔다(DB·세션 안 받음). 채점식은 DB 없이
      검증할 수 있어야 하고 이 파일이 그 경계다
- [x] 배점 합 100 → `× 75`, 유사도는 `PERCENT_RANK` → `× 25`. **합산한 뒤** 상위 (인원×3)
- [x] 후보 0명 시 **스킬 필터 완화 재검색**(옛 "풀 확대"는 무의미해서 교체)
- [x] 테스트 24건(채점 전 항목 + 25:75 경계) + 리포지토리 4건(파라미터·매핑·실DB)

**7번·13번도 같은 브랜치에 얹어 완료 (2026-08-12). 84 passed + 1 skipped, ruff 통과.**

- [x] **7번**: `RankedCandidate.similarity`(기본 `None`) 추가. **LLM 이 만드는 값이 아니라 서버가
      채우는 값**이라 `_RANKING_SCHEMA` 에 넣지 않고, 풀 밖 후보를 걸러낸 뒤 1차 추림에서 계산한
      코사인 유사도로 덮어쓴다. **프롬프트에는 안 넣는다** — 넣으면 LLM 이 원문을 읽는 대신 그
      숫자를 베낀다. 테스트 3건(값 전달 / 프롬프트 미노출 / 지어낸 ID 는 유사도 조회 전에 제거)
- [x] **13번**: CI 에 `pgvector/pgvector:pg16` 서비스 + `AI_TEST_DB_URL` 주입.
      **PR 마다 실제 pgvector 로 SQL 이 돈다.** 서비스가 없으면 그 테스트만 skip 된다
- [x] ⚠️ **통합 테스트가 `public` 스키마에 `DROP TABLE` 하던 것을 전용 스키마로 격리했다.**
      `AI_TEST_DB_URL` 에 개발 DB 를 넣으면 `account`·`freelancer_profile` 등 스프링 테이블이
      통째로 날아가는 구조였다. 실제 개발 DB(테이블 51개)로 돌려 무손상·잔여물 없음을 확인했다

> **→ python 브랜치는 PR 올릴 준비 완료.** 담긴 것: B2(budget_cap) + B3(조건점수 25:75) +
> 7번(similarity) + 13번(CI) + 단가 환산 정책 정렬(×160/×20) + README 갱신.

### B4. 4단계 — 가드 교체 (Java) ← **다음 작업**

브랜치는 `feature/matching-embedding-text-redesign`에 **이어서 얹는다**(B1+B2와 한 PR).
상세 규칙은 `.ai/STATE.md` "[5] 가드"의 **G3 세부 / G4 세부** 절이 최종본이다.

- [ ] `evaluateGuard`에서 **직무·스킬 재검증 제거**
- [ ] **판정 시점을 옮긴다.** 지금은 후보를 한 명씩 보고 **노출 전에** 판정하는데, G3는
      "노출 후보 전원의 합계"라 **노출이 확정된 뒤**에 판정해야 한다. `persistCandidates` 루프 구조가 바뀐다
- [ ] G3 예산 조합 — `Σ(노출 후보 월단가) ≤ 남은 1인 상한 × 노출 인원 × 1.2`
      - **남은 1인 상한** = (`budgetCap` × 모집 인원 − 이미 자리를 차지한 사람들의 월단가 합) ÷ 남은 자리
      - 그 사람들의 월단가는 **협상 타결가 우선**(`NegotiationPort` 신규 메서드), 없으면 희망 단가
      - `getAgreedForContract`는 **타결 전이면 예외를 던진다** — 상태가 `CONTRACT_PENDING` 이상일 때만 호출
      - **탈락시키지 말 것.** `guardPassed=true` 유지, `guardReason`에 기록만
- [ ] G4 LLM 응답 이상 — 중복 ID(뒤엣것 버림) / 인원 초과(상위 N만) / `reason` 누락(탈락,
      빈자리는 다음 순위가 채움). **여기만 실제로 거른다**
- [ ] **7번 자바 쪽**: `RankedFreelancer`에 `similarity` 추가 → `PythonMatchingAdapter` 파싱 →
      `createFromEmbedding(..., 0.0)`의 하드코딩 `0.0`을 실제 값으로 교체
- [ ] 신규 리포지토리 메서드: 자리를 차지 중인 프리랜서 ID 목록
      (`countByPositionIdAndStatusNotIn`의 목록 버전)
- [ ] 테스트 — 기존 가드 테스트(`MatchingIntegrationTest`의 스킬 미달 시나리오 등)가 깨지므로 같이 고친다

### B5. 5단계 — 배포 후

- [ ] **`POST /api/v1/matchings/admin/embeddings/reindex` 1회 실행 (필수)**
      임베딩 텍스트 규칙이 바뀌므로 기존 벡터가 전부 낡는다. 옛 규칙 벡터와 새 규칙 벡터가
      섞이면 비교 자체가 무의미해진다
- [ ] 재색인 **직후** `REINDEX INDEX idx_freelancer_embedding_cosine;`
      ivfflat 은 빈 테이블에 만들면 클러스터를 못 잡아서, 데이터가 들어온 뒤 다시 만들어야
      제대로 동작한다. (추천 본 경로는 이 인덱스를 안 쓰지만 후보 미리보기 엔드포인트가 쓴다)
- [ ] 프론트 전달 문서(`AI매칭_API_화면매핑_최신본.md`) 갱신 — API 응답 모양은 안 바뀌지만
      후보 순서 산출 방식이 달라진 것을 공유

### B6. 환경 — 12번 pgvector — **완료 (2026-08-12)**

**팀 확정: 도커가 아니라 윈도우에 PostgreSQL을 직접 설치한다.** 절차 원본은
`README.md` "2-1) pgvector 설치"에 있다(팀원이 볼 자리라 거기에 뒀다).

| | pgvector | 임베딩 테이블 | 데이터 | 비고 |
|---|---|---|---|---|
| 로컬 (네이티브 **PG18**) | **0.8.6** | ✅ | 0행 | 스프링 테이블 51개, `matching_paused` 있음 |
| 배포 | ✅ | ✅ | freelancer 1 / position 7 | 2026-08-11 생성 = **B1 이전 = 재색인 대상** |

**막혔던 지점과 해결 (같은 걸 다시 겪지 않게)**

- `postgres:16-alpine` 이미지엔 pgvector가 **아예 없다.** 네이티브 설치도 마찬가지라
  **소스에서 빌드해야 한다**(`nmake /F Makefile.win`)
- `nmake`는 **PowerShell에 없다.** 관리자 권한 cmd에서 `vcvars64.bat`을 먼저 `call` 해야
  잡힌다. `set "VAR=..."`도 cmd 문법이라 PowerShell에선 안 먹는다
- `psql`도 PATH에 없다 —
  `"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U pairing -d pairing -f ...`
- pgvector를 **레포 안에 클론하지 말 것**(`git status`에 잡힌다). `C:\` 밑에 받는다
- ⚠️ **도커 PG16과 네이티브 PG18이 둘 다 5432를 잡으면 앱이 어디에 붙는지 알 수 없다.**
  실제로 그 상태였다. 네이티브를 쓰면 `docker compose stop postgres`, Redis만 띄운다

**남은 것 하나**

- [ ] 재색인(B5) **직후** `REINDEX INDEX idx_freelancer_embedding_cosine;`
      — 빈 테이블에 만들어서 pgvector가 `ivfflat index created with little data / This will
      cause low recall` 경고를 실제로 냈다. 데이터가 들어온 뒤 다시 만들어야 한다

> ⚠️ **임베딩 테이블을 자동 생성하는 코드는 어디에도 없다.** AI 서버는 `create_all`을 안 쓰기로
> 했고 스프링은 이 테이블을 JPA 엔티티로 갖고 있지 않다. **환경이 새로 생길 때마다 사람이
> `Pairing-python/db/init/10-create-ai-schema.sql`을 한 번 돌려야 한다.**
> 그 SQL은 `ALTER TABLE ... ADD CONSTRAINT` 2줄 때문에 **재실행하면 실패한다**(파괴적이진 않다).

## C. 검증 — 아직 한 번도 안 한 것

| # | 항목 | 비고 |
|---|---|---|
| C1 | **통합 테스트(end-to-end)** | 이력서 저장 → 임베딩 → 모집 시작 → 추천 → 요청 → 수락까지 실제로 한 번도 안 돌려봤다. **남은 것 중 가장 큰 리스크** |
| C2 | 실제 Gemini 호출로 추천 품질 확인 | 점수 스케일 버그(0~10으로 답하던 것)를 실호출로만 잡았던 전례가 있다 |
| C3 | 유사도 분포 측정 | 순위 기반 정규화로 바꿔서 상수는 불필요해졌지만, 분포가 극단적이면 재검토 |

## D.+ 요구사항 (미착수)

| # | 항목 | 상태 |
|---|---|---|
| D1 | 그라파나 모니터링 지표 | 미착수 |
| D2 | 트래픽 테스트 | 미착수 |
| ~~D3~~ | ~~프론트 렌더링~~ | **제외 (2026-08-12).** 4번 파트가 아니다 — 매칭 API는 이번 재설계로도 응답 모양이 안 바뀌고 프론트 전달 문서도 나가 있다. 화면 구현·렌더링은 프론트 담당 |
| D4 | ~~LLM 비동기 처리~~ | **완료** (리스너 4개 + API 2개 202 응답 + 알림) |

## E. 다른 사람에게 전달만 하면 되는 것

**2026-08-12 — 5건 전부 회신 받음. 남은 건 E2 회신 하나뿐이다.**

| # | 대상 | 한 줄 | 결과 |
|---|---|---|---|
| E1 | 3번 | `ContractDraftListener` `@Async` 누락 | ✅ **해결.** 이미 붙여서 develop 반영, 리스너 안에서 `log.error`도 추가. 단 **결제 트랜잭션이 아니라 협상 타결 트랜잭션**이었다(계약서는 협상 타결 시 생성) — 내 설명이 틀렸고 지적 자체는 맞았다 |
| E2 | 3번 | 정책 P03 문구 수정 | 🔴 **3번이 수정 중. 제안 문구 검토를 요청받았다 → 아래 회신 필요** |
| E3 | 3번 | 프리랜서 성공보수 정산 | ✅ **해결.** `199a961`이 그 작업 맞음. 요율 6% 고정, MASTER 1%p 할인, 기준 = 계약 총액 |
| E4 | 5번 | `AgreedNegotiationView` javadoc이 월단가를 "총액"이라 표기 | ✅ **문구만 틀렸고 계약 도메인은 정상.** `Contract.java:179`가 `salaryAmount * months`로 제대로 곱한다 — 1/N 사고는 없었다. 5번이 javadoc + 예시값(`"42000000"`)을 고치기로 함 |
| E5 | 팀 | 배포 DB pgvector | ✅ **이미 다 있음.** 확장·테이블·데이터(freelancer 1행, position 7행, 모델 1종) 확인. **할 일 없음** |

> **⚠️ B4에서 지킬 것 (5번 확인, 2026-08-12)**
> `agreedAmount`는 **월 단가**이므로 `budgetCap`(역시 월 단가 상한)과 **바로 비교한다.
> 개월 수를 곱하지 않는다.** 곱하면 상한이 개월 수배로 부풀어 경고가 영영 안 뜬다.

**보낼 문장 (그대로 복사해서 쓰면 된다)**

> **E2 회신 → 3번 (아직 안 보냄)**
>
> 제안하신 문구 방향은 맞습니다. **두 가지만 추가**하면 코드와 정확히 맞습니다.
>
> **(1) 프로젝트 — 재생성 경로가 빠졌습니다**
> 모집 시작 후 클라이언트가 프로젝트를 수정하면 임베딩을 **다시 만듭니다**
> (`ProjectUpdatedEvent` → `ProjectUpdatedEventListener`, 결제 후에만 발행).
> 이게 빠지면 "결제 완료 시점에 저장한다"만 남아서, 수정 후 재생성이 정책에 없는 동작이 됩니다.
> 참고로 **요청 카드용 스냅샷은 반대로 절대 안 건드립니다**(R32, 최초 모집 시작 시점 고정).
> 임베딩은 검색 정확도라 최신이 맞고, 카드는 계약 근거라 고정이 맞습니다.
>
> **(2) 프리랜서 줄도 지금 틀려 있습니다**
> P03 첫 줄이 *"프리랜서 등록 시점에 프리랜서 정보 임베딩 저장"* 인데, 실제로는
> **이력서를 정식 저장·수정할 때**입니다(`ResumeService` → `ResumeUpdatedEvent`).
> 회원 등록 시점이 아니고, **임시저장(`saveDraft`)은 이벤트를 안 냅니다** — 미완성 이력서로
> 벡터가 생기지 않게 일부러 그렇게 했습니다.
>
> **제안 문구**
> > 프리랜서 정보는 **이력서를 정식 저장·수정하는 시점**에 임베딩 저장한다. 임시저장은 대상이 아니다.
> > 프로젝트 정보는 **착수금 수수료 결제가 완료되어 모집이 시작되는 시점**에 임베딩 저장하고,
> > 모집 시작 후 프로젝트가 수정되면 다시 만든다.
> > 등록만 하고 결제하지 않은 프로젝트는 추천이 시작되지 않아 임베딩 비용을 쓰지 않는다.
>
> (관리자 일괄 재색인 API도 임베딩을 만드는 경로지만, 운영 도구라 정책 문구에는 안 넣어도 된다고 봅니다.)

> **E5 → 팀 (인프라 담당) — 2026-08-12 확인 완료**
> 배포 DB에 **pgvector 확장은 있고**, 임베딩 테이블만 SQL로 만들면 된다는 답을 받았다.
> 남은 액션은 아래 "배포 DB 테이블 생성" 하나뿐이다.

**환경 준비는 로컬·배포 둘 다 끝났다(2026-08-12).** 새 환경이 생기면 위 B6 절차를 따른다.

- 참고: **B3의 새 추천 쿼리는 ivfflat 인덱스를 쓰지 않는다.** 하드필터 통과자 전원에 대해
  유사도를 계산하는 설계라 `ORDER BY ... LIMIT`이 없어서 순차 스캔이다(의도된 동작). 인덱스는
  내부용 후보 미리보기 엔드포인트(`GET /embeddings/positions/{id}/candidates`)가 쓴다.
  **수만 명 규모가 되면 이 지점을 먼저 본다.**

## F. 향후 개선 (범위 밖, 기록만)

| # | 항목 | 내용 |
|---|---|---|
| F1 | 상주근무 시 주소 비교 | 컬럼은 다 있다(`project.work_location`, `resume.zip_code/address`). 시/군/구까지만, **상세주소는 개인정보라 LLM에 금지**, 거리 계산 안 함. Python `PositionRequirement`/`FreelancerProfile`에 필드 추가부터 필요 |
| F2 | `position_skill`에 요구 숙련도 컬럼 | 있으면 숙련도를 보너스가 아니라 조건으로 쓸 수 있다 (3번 파트) |
| F3 | 예산 경고 화면 노출 (U4) | **2026-08-12 하기로 확정.** 문구까지 정했고 프론트에도 전달됨. 아래 상세 참고 |
| F4 | LLM 호출 비용 | 포지션당 1회라 포지션 3개면 3회. 원안("프로젝트당 1회")과 다르다 |
| F5 | 포지션별 우대사항 되살리기 | 기획 결정 사안. 지금은 불필요 |

### F3 상세 — 예산 경고 배너 (2026-08-12 확정, 미구현)

**`lowScoreWarned`와 완전히 같은 패턴이다.** 새로 설계할 게 없고 이미 뚫린 길을 한 번 더 쓴다.

| # | 어디 | 무엇 |
|---|---|---|
| 1 | `MatchingRoundJpaEntity` | `budget_warned` 컬럼 (`ddl-auto`가 생성) |
| 2 | `MatchingRound` | `warnBudget()` — `warnLowScore()` 바로 옆에 같은 모양으로 |
| 3 | `MatchingRoundCreationService` | `evaluateBudgetCombination`이 사유를 냈으면 `round.warnBudget()` |
| 4 | `CandidateListResponse` | `budgetWarned` 필드 |
| 5 | `CandidateResponseAssembler` | 매핑 |
| 6 | 프론트 | `budgetWarned=true`면 상단 배너 (기존 컴포넌트 재사용) |

백엔드 약 1시간. **확정 문구:**

```text
추천된 후보들의 희망 단가 합계가 남은 예산을 넘습니다. 협상에서 조정이 필요할 수 있어요.
```

- **`guardReason`은 화면에 내보내지 않는다.** 상한 계산식이 그대로 노출되면 클라이언트가
  역산해서 다른 후보의 희망 단가를 추정할 수 있다. 개발자용 로그로만 둔다
- `lowScoreWarned`와 **동시에 true일 수 있다.** 배너 2개를 어떻게 보여줄지는 프론트가 정한다
  (프론트 전달 문서에 질문으로 남겼다)
- ⏳ **착수 시점**: C1·C2를 먼저 돌려 **경고가 실제로 얼마나 자주 뜨는지** 보고 만드는 게 낫다.
  너무 자주 뜨면 배너가 아니라 **판정식(×1.2)부터** 손봐야 하고, 그러면 문구도 다시 쓴다.
  아래 쿼리로 빈도를 먼저 본다:

```sql
SELECT count(*) FILTER (WHERE guard_reason IS NOT NULL) AS 경고, count(*) AS 전체
FROM matching_candidate WHERE is_exposed = true;
```

## 주의 사항 (반복해서 걸린 것)

1. **`db/init/02-create-schema.sql`을 실제 스키마로 믿지 말 것.** 공용 DB는 `ddl-auto`라 JPA
   엔티티에서 생성된다. 그 SQL에만 남고 실제로는 없는 컬럼이 있다(`preferred_note`).
   **필드 존재는 JPA 엔티티나 응답 DTO로 확인할 것.**
2. **우리 문서(STATE.md)끼리만 대조하면 같이 틀린 걸 못 잡는다.** 명세 원문
   (`docs/spec/requirements.md`), 사용자 설계 메모(`docs/personal/ai-matching-notes.md`),
   프론트 전달 문서(Desktop)까지 봐야 한다. 이번에 판정을 두 번 뒤집었다.
3. **앱을 켜둔 채로 브랜치 전환·빌드를 하지 말 것.** devtools가 반쯤 써진 클래스로 재시작해서
   없는 빈을 못 찾는다는 기동 실패가 난다(코드 문제 아님).
4. **Gradle 출력을 파이프로 넘기지 말 것.** 종료 코드가 사라진다. `> file 2>&1` 후 `$?` 확인.
