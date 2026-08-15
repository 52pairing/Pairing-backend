package com.pairing.matching.application.result;

import com.pairing.meta.domain.model.PeriodUnit;

/**
 * 프리랜서가 매칭 요청을 수락하기 전에 보는 프로젝트 본문. project 도메인 소유 데이터.
 *
 * <p><b>{@link ProjectPositionSummary}와 따로 두는 이유.</b> 그쪽은 요약본이라 근무 장소·시작일 협의
 * 여부가 없어서 이 두 개를 채우려면 프로젝트 엔티티를 한 번 더 읽어야 한다. 요약본은 재색인 루프
 * ({@code EmbeddingReindexService})와 수락·재추천·회차 생성이 모두 쓰는 경로라, 화면 표시용 필드
 * 때문에 그쪽 전부에 조회를 하나씩 더 붙이게 된다. 이 record 는 모집 시작(스냅샷 얼리기)과 요청 상세
 * 조회에서만 쓰므로 그 부담이 없다.
 *
 * <p><b>클라이언트 내부 정보는 담지 않는다.</b> 결제·정산 상태, 재추천 사용 횟수, 확정 인원, 모집
 * 연장 횟수는 프로젝트 상세 조회에는 있지만 여기로 넘기지 않는다 — 프리랜서에게 "당신은 3번째
 * 재추천으로 뽑혔다", "이미 2자리가 찼다"가 드러난다. 첨부 자료도 뺐다(다운로드 권한이 클라이언트
 * 전용이라 project 도메인 결정이 필요하다).
 *
 * <p>{@code detailScope}/{@code extraNote}/{@code workLocation}은 <b>등록 시 선택 입력</b>이라 비어
 * 있을 수 있다. 화면에서 빈 값 처리가 필요하다.
 */
public record ProjectContent(
        String currentSituation,
        boolean startNegotiable,
        int periodValue,
        PeriodUnit periodUnit,
        String detailScope,
        String extraNote,
        String workLocation
) {
}
