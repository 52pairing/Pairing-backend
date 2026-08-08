# 인수인계 — AI매칭(4번 파트) 3일 스프린트

최종 갱신: 2026-08-07 저녁. 이어서 작업할 때는 이 문서 + `.ai/STATE.md`를 먼저 읽는다.

## 진행 현황 요약

**1일차·2일차 항목(아래 1~8번) 전부 완료.** 매칭 도메인 9개 엔드포인트가 실제 로직으로 동작한다
(스켈레톤 고정 응답 없음). `./gradlew test` 83개 통과 확인.

다만 freelancer/project/negotiation 3개 도메인이 아직 실제 구현이 없어서, 그 데이터가 필요한 자리는
`FreelancerDirectoryPort`/`ProjectDirectoryPort`/`NegotiationPort` 스텁 어댑터로 채워놨다
(`.ai/STATE.md` 참고). **이 3개 도메인이 실제로 만들어지면 어댑터 클래스만 교체하면 되고, matching
쪽 코드는 안 건드려도 된다.** 지금부터는 3일차(아래) 위주로 진행한다.

## 지금 당장 할 일 (순서대로)

### 1일차 오전 — 뼈대 [완료]

1. ~~`com.pairing.matching.domain.model`에 실제 도메인 모델 추가~~ — 완료.
2. ~~`application/port/out/MatchingPort` + `infrastructure/llm/PythonMatchingAdapter`~~ — 완료. Resilience4j 서킷브레이커, Bucket4j 레이트리밋, `X-Internal-Api-Key`/`X-Trace-Id` 헤더까지 포함.

### 1일차 오후 — 협상팀 연동 [완료 — 단, 협상 도메인 쪽은 아직 스텁]

3. ~~`POST /api/v1/matchings/requests/{requestId}/acceptance` 실제 구현~~ — 완료. 스냅샷 캡처, `budgetCap` 계산, negotiation 호출까지 다 붙였다.
   - **주의**: negotiation 쪽 실제 인바운드 UseCase가 아직 없어서 `NegotiationPort`를 `StubNegotiationAdapter`로 임시 구현해뒀다(협상 생성은 음수 placeholder ID를 돌려주고 로그로 경고만 남김, 실패로 처리하지 않음 — 수락 자체는 유효한 사용자 행동이라 롤백시키면 안 되기 때문). **negotiation팀이 실제 `NegotiationCommandUseCase`를 만들면 `StubNegotiationAdapter` 하나만 그 UseCase 위임 호출로 교체.** 5번(협상)이 보낸 `FreelancerConditionSnapshot` 초안에 필드 2개(`minAcceptAmount`, PERIOD)가 빠져있어 확인 요청 보냄(채팅 로그 참고) — 답 오면 필드 매핑 다시 확인.
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
12. freelancer/project/negotiation 도메인이 실제로 만들어지면 `StubFreelancerDirectoryAdapter`/`StubProjectDirectoryAdapter`/`StubNegotiationAdapter` 3개 교체. 프리랜서 등급 타이브레이커(base_score 동점 시 마스터>시니어>주니어)도 이때 랭킹 로직에 추가.
13. 통합 테스트, `.ai/API.md`/`docs/api-dto.csv` 최종 동기화, 에러코드(`AI_001~AI_030`) 매핑 점검.

## 열려있는 결정/블로커 (건드리기 전에 확인)

- freelancer/project/negotiation 도메인 실구현 부재 — 스텁 어댑터 3개로 우회했지만, 그 도메인들이 실제로 나오기 전까지는 이름/사진/등급/평점, 프로젝트 상세, 협상 진행 정보가 전부 placeholder다.
- freelancer `FreelancerConditionResponse.periodValue`가 지금 primitive `int`라 "선택 입력이라 null 가능"이라는 전제가 깨져 있음 — 2번(프리랜서) 담당자에게 nullable(`Integer`)로 바꿀 수 있는지 확인 필요.
- 적합도 점수 스케일 0~100 여부, 골드 등급 수수료 할인, 프로젝트 인원별 예산배분 필드 존재 여부 — `.ai/STATE.md` 하단 표 참고, 팀 확인 대기 중이라 확정 전까지는 가정값으로 진행.

## 참고할 실제 파일

- Pairing-python 계약 문서: `C:\52_Pairing\Pairing-python\README.md`
- Spring↔AI서버 연동 참고 구현: `C:\Algoga_V3_backend`의 `com.kidmily.algoga_server.chatbot` 도메인 (Port/Adapter/서킷브레이커/레이트리밋/내부API 전부 실제 코드로 있음)
- 매칭 DTO 계약: `com.pairing.matching.presentation.api.*` (이미 확정, 필드 변경 시 `.ai/API.md`와 `docs/api-dto.csv` 같이 갱신)
