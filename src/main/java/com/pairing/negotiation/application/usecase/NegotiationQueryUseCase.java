package com.pairing.negotiation.application.usecase;

import com.pairing.negotiation.application.result.AgreedNegotiationView;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** 협상 조회 인바운드 포트. 뷰어 관점(role)이 결정된 결과를 돌려준다. */
public interface NegotiationQueryUseCase {

    /** 협상 상세. 당사자만 열람 가능(아니면 NG_002), 없으면 NG_001. */
    NegotiationView getDetail(Long negotiationId, Long accountId);

    /**
     * 내 협상 목록.
     * <ul>
     *   <li>projectId 있음 → 클라이언트 협상 탭. 그 프로젝트 소유자만 조회 가능(아니면 NG_002).</li>
     *   <li>projectId 없음 → 내가 프리랜서인 협상 목록.</li>
     * </ul>
     * status 가 있으면 해당 상태로 필터한다. 목록은 DB 페이징이며 요약(조건 미포함)이다.
     */
    Page<NegotiationView> findMine(Long accountId, Long projectId, NegotiationStatus status, Pageable pageable);

    /** 협상 로그(라운드순). 당사자만 조회 가능(아니면 NG_002), 없으면 NG_001. */
    List<NegotiationMessage> findMessages(Long negotiationId, Long accountId);

    /** 협상 로그 해시 체인 무결성 검증(위변조 탐지). 당사자만(아니면 NG_002), 없으면 NG_001. */
    NegotiationLogVerifier.Result verifyLog(Long negotiationId, Long accountId);

    /**
     * 헤더 배지용 '내 응답 대기' 협상 건수. 목록의 {@code waitingForMe} 와 같은 기준으로 센다.
     *
     * <p>목록({@link #findMine})은 페이징이라 프론트가 1페이지만 받으면 숫자가 실제보다 작아진다.
     * 헤더는 화면 어디서나 정확한 수가 필요하므로 DB 에서 전량을 센다.
     *
     * <p>한 계정이 클라·프리 양쪽일 수 있어 두 역할을 합산한다. 어느 쪽도 아니면 0.
     */
    long countWaitingForMe(Long accountId);

    /**
     * 타결 협상의 계약 생성용 스냅샷(서버간 호출). AGREED 조건만 담고 마지노선은 담지 않는다.
     *
     * <p>{@link #getDetail}은 뷰어 계정이 필요하고 floor 를 뷰어 기준으로 걸러 내므로 계약 생성에는 맞지 않아 따로 둔다.
     * 계약 도메인이 협상 리포지토리를 직접 읽지 않게 하는 것이 목적이다.
     *
     * @throws com.pairing.global.exception.BusinessException 없으면 NG_001, 타결 상태가 아니면 NG_009
     */
    AgreedNegotiationView getAgreedForContract(Long negotiationId);
}
