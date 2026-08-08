# 인수인계 — AI매칭(4번 파트) 3일 스프린트

최종 갱신: 2026-08-08. 이어서 작업할 때는 이 문서 + `.ai/STATE.md`를 먼저 읽는다.

## 진행 현황 요약

**1일차·2일차 항목(아래 1~8번) 전부 완료.** 매칭 도메인 9개 엔드포인트가 실제 로직으로 동작한다
(스켈레톤 고정 응답 없음). PR #34로 develop에 merge 완료.

**스텁 어댑터 3개 중 2개 완전 교체, 1개 부분 교체 완료** (`feature/matching-directory-adapters` 브랜치,
아직 이슈·PR 안 만듦 — 다음에 할 일). `ProjectDirectoryPort`/`NegotiationPort`는 실제 구현으로 완전히
바뀌었고, `FreelancerDirectoryPort`는 카드 요약만 실구현이고 `resolveFreelancerId`/`findCondition`은
account 도메인 쪽 메서드 대기 중이라 스텁으로 남아있다. 상세는 `.ai/STATE.md` 참고.

## 지금 당장 할 일 (순서대로)

### 1일차 오전 — 뼈대 [완료]

1. ~~`com.pairing.matching.domain.model`에 실제 도메인 모델 추가~~ — 완료.
2. ~~`application/port/out/MatchingPort` + `infrastructure/llm/PythonMatchingAdapter`~~ — 완료. Resilience4j 서킷브레이커, Bucket4j 레이트리밋, `X-Internal-Api-Key`/`X-Trace-Id` 헤더까지 포함.

### 1일차 오후 — 협상팀 연동 [완료 — 단, 협상 도메인 쪽은 아직 스텁]

3. ~~`POST /api/v1/matchings/requests/{requestId}/acceptance` 실제 구현~~ — 완료. 스냅샷 캡처, `budgetCap` 계산, negotiation 호출까지 다 붙였다.
   - ~~negotiation 쪽 실제 인바운드 UseCase 없음~~ — 2026-08-08 해소, `NegotiationAdapter`로 실제 연동 완료(아래 12번 참고).
   - **새로 남은 것**: 협상 타결/결렬 시 매칭 상태를 갱신해줄 통로가 없음. `MatchingNegotiationOutcomeUseCase.markNegotiationAgreed/markNegotiationFailed(requestId)` 시그니처를 5번에게 전달함 — 양쪽 다 구현 전.
4. 결제완료 이벤트 시점에 "1차 추천 라운드 생성" — **아직 안 함**. `MatchingRoundCreationService.createRound(...)`(2일차에 재추천용으로 만듦)를 그대로 재사용하면 되는데, 트리거를 걸 payment 도메인 자체가 아직 없다. payment 도메인이 생기면: `RecommendationType.INITIAL`, `recruitCount = position.headcount()`, `costAmount = 0`으로 `createRound` 호출하는 얇은 진입점만 추가하면 됨.

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
12. ~~freelancer/project/negotiation 도메인이 실제로 만들어지면 스텁 어댑터 3개 교체~~ — 2026-08-08, `feature/matching-directory-adapters` 브랜치에서 완료(Project/Negotiation 완전 교체, Freelancer는 카드 요약만). **이 브랜치 이슈·PR 아직 안 만듦 — 다음 세션 최우선.** PR 올리기 전에 `local` 프로파일로 서버 띄워 Swagger에서 실제 호출 확인하고 `Verification` 섹션에 기록할 것(지난 PR 때 빠뜨렸던 절차).
    - 프리랜서 등급 타이브레이커(base_score 동점 시 마스터>시니어>주니어)는 아직 랭킹 로직에 미반영.
    - `resolveFreelancerId`/`findCondition`(freelancerId 기준)은 account 도메인의 account_id↔freelancer_profile.id 조회 메서드가 나와야 완전 교체 가능(2번이 1번에게 승인 요청, 대기 중).
13. 통합 테스트, `.ai/API.md`/`docs/api-dto.csv` 최종 동기화, 에러코드(`AI_001~AI_030`) 매핑 점검.
14. `MatchingNegotiationOutcomeUseCase`(협상 결렬/타결 통보 인바운드 포트) 구현. 시그니처는 5번에게 전달 완료, `MatchingRequestService`에 구현 추가 필요.
15. `currentSituation`/`mainTask`(프로젝트 현재 상황/담당 업무) 노출 여부 팀 답변 오면 `MatchingRequestResponse`에 필드 2개 추가 여부 결정.

## 열려있는 결정/블로커 (건드리기 전에 확인)

- `resolveFreelancerId`/`findCondition`(freelancerId 기준) — account 도메인 메서드 대기 중이라 여전히 placeholder.
- 적합도 점수 스케일 0~100 여부, 골드 등급 수수료 할인, 프로젝트 인원별 예산배분 필드 존재 여부, `currentSituation`/`mainTask` 노출 여부 — `.ai/STATE.md` 하단 표 참고, 팀 확인 대기 중이라 확정 전까지는 가정값으로 진행.

## 참고할 실제 파일

- Pairing-python 계약 문서: `C:\52_Pairing\Pairing-python\README.md`
- Spring↔AI서버 연동 참고 구현: `C:\Algoga_V3_backend`의 `com.kidmily.algoga_server.chatbot` 도메인 (Port/Adapter/서킷브레이커/레이트리밋/내부API 전부 실제 코드로 있음)
- 매칭 DTO 계약: `com.pairing.matching.presentation.api.*` (이미 확정, 필드 변경 시 `.ai/API.md`와 `docs/api-dto.csv` 같이 갱신)
