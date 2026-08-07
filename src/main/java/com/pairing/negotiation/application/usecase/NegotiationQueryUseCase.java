package com.pairing.negotiation.application.usecase;

import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
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
}
