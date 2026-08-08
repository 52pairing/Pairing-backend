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

## 다음 세션에서 할 일

`.ai/HANDOFF.md`의 "3일차" 섹션부터 시작 (2일차까지 완료). 우선순위:
1. 5번(협상)·2번(프리랜서)·3번(프로젝트) 실제 코드 나오면 스텁 어댑터 3개 교체.
2. Pairing-python `_build_prompt` 실구현 + 하드필터 + Stage F 실제 배분 알고리즘.
3. 결제 완료 트리거로 "최초 추천 라운드 생성" 호출하는 지점(payment 도메인도 아직 없음 — 인바운드 UseCase만 노출해두고 대기하는 게 나을지 확인 필요).
