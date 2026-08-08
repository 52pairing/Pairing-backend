# 현재 상태 — AI매칭(4번 파트)

최종 갱신: 2026-08-08

## 담당 범위

AI매칭 전체 파이프라인 (요구사항 R01~R05). 관련 레포 2개:

- `Pairing-backend` (Java/Spring, 이 레포) — `com.pairing.matching` 도메인
- `Pairing-python` (FastAPI, 별도 레포, 로컬 경로 `C:\52_Pairing\Pairing-python`) — 임베딩·LLM 계산 서버

임베딩 생성·저장까지 이 파트가 직접 구현한다. 프로필/프로젝트 도메인 자체(실데이터)는 2번/3번 소유, 이 파트는 그 텍스트를 벡터화·비교하는 부분만 담당.

## 기술 스택 (확정)

- **임베딩**: Gemini `text-embedding-004` (768차원). 로컬 모델 아님, Spring 백엔드와 같은 Gemini API 키 공유(용도별로 모델명만 다르게 설정: `GeminiTask.EMBEDDING/MATCHING/REVIEW`)
- **벡터 저장소**: pgvector — Spring이 쓰는 **같은 PostgreSQL**에 `freelancer_embedding`, `position_embedding` 테이블 (AI 서버 소유, 이미 SQL 스키마 존재: `Pairing-python/db/init/10-create-ai-schema.sql`)
- **LLM**: Gemini `gemini-2.0-flash` (`GeminiTask.MATCHING`), 구조화 출력은 `google-genai` SDK의 `response_schema`로 강제
- **Spring↔Pairing-python 계약**: `Pairing-python/README.md` "3. 스프링 ↔ AI 서버 통신 규약" 절이 계약서. `X-Internal-Api-Key` 헤더 인증, `X-Trace-Id` 전파, 에러코드 `AI_001~AI_030`

## Pairing-python 현황 (이미 구현됨)

- `PUT /api/v1/embeddings/freelancers`, `PUT /api/v1/embeddings/positions` — 임베딩 upsert (해시 비교로 재계산 스킵, 프리랜서 쪽만 구현됨)
- `GET /api/v1/embeddings/positions/{id}/candidates?limit=` — pgvector 코사인 검색
- `POST /api/v1/matchings/recommendations` — 후보풀 조회 + Gemini 재랭킹(`{freelancer_id, score, reason}` 구조화 출력)
- **미구현(TODO)**: `MatchingService._build_prompt`가 스텁 — 지금은 freelancer_id+유사도만 나열, 실제 이력서/포지션 요구조건 텍스트 없음. 하드필터(직군/직무/일정/단가)도 검색에 전혀 없음.

## Pairing-backend `matching` 도메인 현황 (2026-08-08 갱신)

- `MatchingController`의 9개 엔드포인트 **전부 실제 구현**(고정 샘플 응답 없음). PR #34로 develop에 merge 완료.
- `meta` 도메인이 공통 마스터 데이터(직군/직무/스킬/근무조건) 제공 — `/api/v1/meta/*`, 비로그인도 호출 가능.
- Spring → Pairing-python 호출 코드(`MatchingPort`+`PythonMatchingAdapter`, Resilience4j 서킷브레이커, Bucket4j 레이트리밋) 완성.
- **다른 도메인 연동 포트 3개 — 대부분 실제 구현으로 교체 완료** (`feature/matching-directory-adapters` 브랜치, 아직 이슈·PR 안 만듦):
  - `ProjectDirectoryPort` ← `infrastructure/directory/ProjectDirectoryAdapter` — **완전 교체**. `ProjectQueryUseCase`(project 도메인) + `AccountQueryUseCase.findClientProfileById`(회사명/업종/직원수)로 실제 조회. `findPositionSummary`는 project 도메인이 positionId 단독으로 projectId를 역조회할 방법이 없어서 **포트 시그니처를 `findPositionSummary(projectId, positionId)`로 변경**했다 — 매칭이 자신의 `MatchingRound`(project_id 보유)에서 얻어 넘긴다.
  - `NegotiationPort` ← `infrastructure/negotiation/NegotiationAdapter` — **완전 교체**. negotiation의 `NegotiationCommandUseCase`(생성)/`NegotiationProgressUseCase`(진행조회) 위임 호출.
  - `FreelancerDirectoryPort` ← `infrastructure/directory/FreelancerDirectoryAdapter` — **부분 교체**. `findCardSummary`만 실구현(`FreelancerCandidateSummaryUseCase`). `resolveFreelancerId`/`findCondition`은 account_id↔freelancer_profile.id 양방향 조회가 account 도메인에 아직 없어(2번이 1번에게 승인 요청, 대기 중) 스텁 유지 — 로그 경고로 표시해둠.
- `budgetCap`(수수료율 구간×클라이언트등급)과 `grade_weight`(0/1/2%) 계산은 `ClientGradeResolver`/`BudgetCapCalculator`가 실제 리포지토리를 조회해서 처리한다.
- Stage F 가드(직무/스킬·예산 조합 재검증)는 지금 항상 통과 처리(placeholder). 규칙 기반 실제 검증은 아직 남음.
- fitReason은 DB에 `"|"`로 이어붙인 문자열로 저장하고 API 응답에서 다시 나눠 태그 리스트로 돌려준다(`CandidateResponseAssembler`). Pairing-python이 이 구분자로 합친 문자열을 내려주도록 `_build_prompt`/응답 스키마를 맞춰야 한다(아직 안 함).
- **로컬 개발 환경에서 발견·수정한 버그 2건** (코드 정상, 인프라/설정 문제였음):
  - `global/ratelimit/RedisRateLimitConfig`가 빈 생성 시 즉시 Redis에 연결해서 Redis 없는 환경(CI)에서 전체 컨텍스트 로딩이 실패 → `@Lazy`(빈 + 생성자 주입 지점 둘 다)로 지연 연결하도록 수정.
  - `matching_candidate`/`matching_round`의 NUMERIC 컬럼(similarity/base_score/grade_weight/fit_score/cost_amount)에 JPA 엔티티가 `columnDefinition`을 안 줘서 스키마 검증 실패 → 명시해서 해결.
- **협상(5번) 연동 — 아직 남은 것**: 협상이 타결(AGREED)/결렬(FAILED)되는 시점에 매칭 쪽 상태(`MatchingRequest.advanceStatus`/`failNegotiation`)를 갱신해줄 통로가 없다. `MatchingNegotiationOutcomeUseCase.markNegotiationAgreed(requestId)`/`.markNegotiationFailed(requestId)` 시그니처를 5번에게 전달했고, **양쪽 다 아직 구현 전**.
- `newProposalCount`(협상 진행조회 응답)는 "클라가 마지막으로 읽은 시점 이후 온 새 제안 수"로 정의 확정. 실제 반영은 5번의 `feature/negotiation-unread-proposals` 브랜치가 develop에 merge된 뒤 자동 적용됨(우리 코드 수정 불필요).

## 확정된 설계 결정 (요약, 상세 근거는 각 요구사항 R01~R05/정책 P02~P09 참고)

1. **파이프라인**: 하드필터(AI매칭 동의, 직군/직무) → 조건필터(일정/근무조건/단가, 느슨하게) → 임베딩 유사도(자기소개+경력사항 ↔ 프로젝트설명+담당업무+업무범위+우대사항) → 상위 (모집인원×3) 추림 → LLM 1회 호출(포지션당, 전체 포지션 후보 한번에, 프로젝트당 1회 원칙) → 규칙기반 가드(직무/스킬 재검증 + 예산 조합 재검증)
2. **점수**: 사람이 가중치 배점 안 함. LLM이 최종 점수·근거 산출. 0~100 스케일 가정(팀 정책 회의 "월요일 확정" 대기 중). 50점 미만이면 경고 — **최초 추천화면에서 미리 보여줌**(대기 순번 N+1~3N도 이미 한 번의 LLM 호출로 채점되어 있어서 가능). 점수 숫자 자체는 화면·API 응답에 노출 안 함.
3. **예산**: 순예산 = 입력예산×(1-수수료율), 수수료율 = 금액구간(1억)×클라등급(다이아 -2%). 프리랜서 단가는 월단가로 통일(일급×20일, 시급×160시간). 조합 총액 ≤ 순예산×1.2 허용.
4. **임베딩 쓰기**: 프리랜서는 조건 저장 시마다 PUT 호출(해시로 중복 스킵). 프로젝트(포지션)는 **검수 통과 시점에 딱 1번**(프로젝트는 등록 후 수정 불가능이라 재계산 불필요). 매칭 실행(추천)은 착수금 결제 완료 시점에 트리거.
4-1. **등급 가중치(grade_weight, 2026-08-07 확정)**: 명세/정책/원본 엑셀 다 확인해도 숫자 없음(정성적 "매칭 확률 증가"만 있음, 수수료 할인 1%/2%는 별개) — 그 패턴을 그대로 재사용해 **클라이언트 등급: 실버 0% / 골드 +1% / 다이아 +2%**로 확정. `MatchingCandidate.applyGradeWeight(double)`에 이미 구현됨(퍼센트 값은 서비스 계층에서 등급 조회해 전달). **클라이언트 간 자원배분(등급 높은 클라한테 좋은 프리 우선배정)은 하지 않음** — R02.6("같은 프리랜서가 여러 클라이언트에게 동시 추천 가능")과 충돌해서 폐기. 대신 **프리랜서 등급 타이브레이커**를 추가: base_score가 완전 동점일 때만 프리랜서 등급(마스터>시니어>주니어)으로 2차 정렬. 이건 아직 구현 전 — 노출 순위(rank_no) 계산하는 서비스 로직 만들 때 반영해야 함(엔티티 레벨 변경 불필요, 정렬 비교자만 추가하면 됨).
5. **협상(5번) 연동 계약**: `POST /requests/{requestId}/acceptance` 처리 시 아래를 동기 호출로 넘김(같은 트랜잭션, 실패 시 롤백)
   - **스냅샷 diff 대상 4개**: `payUnit`+`payAmount`, `workStyle`, `workForm`, `availableFrom` (전부 `FreelancerConditionResponse` 필드 재사용)
   - **보조 2개**: `minAcceptAmount`(금액 가드 하한), `startNegotiable`
   - **PERIOD**: `periodValue`가 null이면 그 협상에서 제외, 값 있으면 포함(블랑켓 제외 아님)
   - 경력연차·스킬은 매칭 필터로만 쓰고 diff 대상 아님
   - `budgetCap`: 3일 마감 때문에 1단계는 "순예산÷확정인원"으로 단순화, 나중에 Stage F 정확한 배분값으로 교체(필드명 동일 유지)
   - ~~블로커: negotiation 도메인에 application 계층이 아직 없음~~ — 2026-08-08 해소. `NegotiationCommandUseCase`/`NegotiationProgressUseCase` 실구현 완료, `NegotiationAdapter`로 연동함.

## 아직 팀 확인 대기 중인 것

| 항목 | 상태 |
|---|---|
| 적합도 점수 스케일이 진짜 0~100인지 | policy.md "정의 필요, 월요일 확정" 회의 결과 대기 |
| 골드 등급 수수료 할인 여부 | 다이아만 정책에 명시됨, 확인 필요 |
| 프로젝트 등록에 인원별 예산 배분 필드 존재 여부 | 없으면 지금처럼 순예산 전체 조합으로만 판단 |
| `currentSituation`(프로젝트 현재 상황)/`mainTask`(주요 담당 업무)를 프리랜서의 "받은 매칭 요청" 카드에 노출할지 | 2026-08-08 팀에 질문 전달, 답 대기 중. 데이터는 이미 `ProjectQueryUseCase`에서 옴, 노출하려면 `MatchingRequestResponse`에 필드 2개만 추가하면 됨 |
