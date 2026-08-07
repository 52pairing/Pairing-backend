package com.pairing.matching.application.port.out;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.matching.application.result.FreelancerCardSummary;

/**
 * freelancer 도메인 조회 포트. 매칭은 이 인터페이스로만 프리랜서 정보를 읽는다.
 *
 * <p><b>임시 스텁 상태(2026-08-07)</b>: freelancer 도메인에 아직 실제 영속 계층이 없어
 * {@code infrastructure.directory.StubFreelancerDirectoryAdapter}가 고정값을 돌려준다.
 * freelancer 도메인이 실제 구현되면 이 어댑터 하나만 실제 조회 코드로 교체하면 된다.
 */
public interface FreelancerDirectoryPort {

    /** 로그인 계정(accountId) -&gt; freelancer_profile.id. 매칭 요청·스냅샷은 전부 이 값을 쓴다(account.id 아님). */
    Long resolveFreelancerId(Long accountId);

    /** 후보 카드 노출용 요약(이름/사진/등급/평점). */
    FreelancerCardSummary findCardSummary(Long freelancerId);

    /**
     * 협상 스냅샷 캡처·가드 재검증에 쓰는 현재 조건. 새 스키마를 만들지 않고
     * freelancer 도메인이 이미 정의한 {@code FreelancerConditionResponse}를 그대로 재사용한다.
     */
    FreelancerConditionResponse findCondition(Long freelancerId);
}
