package com.pairing.negotiation.application.usecase;

/**
 * 프로젝트 도메인 이벤트가 협상에 미치는 결과를 반영하는 인바운드 포트(방향: project → negotiation).
 *
 * <p>사람 액션({@link NegotiationLoopUseCase})과 분리한다. 이건 시스템(스케줄러·모집 종료)이 부르는
 * 경로라 당사자 검증(accountId)이 없고, 대상도 단건이 아니라 그 프로젝트의 협상 전체다.
 */
public interface NegotiationProjectOutcomeUseCase {

    /**
     * 취소된 프로젝트의 <b>진행 중(IN_PROGRESS)</b> 협상을 전부 결렬 처리한다.
     *
     * <p>이미 타결(AGREED)된 협상은 대상이 아니다 — 그건 결렬이 아니라 타결된 사실이고, 계약 도메인이
     * {@code Contract.terminate} 로 따로 정리한다. 각 협상은 사람 포기({@code giveUp})와 <b>같은 종결
     * 절차</b>(상태 결렬 + 대리인 예약 정리 + 매칭 종결 + 화면 갱신 + 알림)를 밟는다.
     */
    void failInProgressForCanceledProject(Long projectId);
}
