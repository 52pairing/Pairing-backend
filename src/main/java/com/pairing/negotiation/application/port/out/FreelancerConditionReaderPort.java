package com.pairing.negotiation.application.port.out;

import java.util.Optional;

/**
 * 프리랜서 희망 조건 중 협상이 필요한 값만 읽는 포트(협상 소유, 읽기 전용).
 *
 * <p>요구사항 R09 예외조건이 <b>"프리 최저 수용가 위반 시 차단(가드)"</b> 이라, 마지노선을 받을 때
 * 등록해둔 최저 수용가를 하한으로 검증해야 한다. 협상은 그 값을 갖고 있지 않으므로 읽어 온다.
 *
 * <p><b>스냅샷이 아니라 현재 등록값을 읽는다.</b> 프리랜서가 마이페이지에서 희망 조건을 낮추면
 * 그 순간부터 더 낮은 마지노선을 낼 수 있어야 한다 — "지금 받아들일 수 있는 최저선"이 기준이기
 * 때문이다. 협상 시작 시점 값으로 고정하면 사용자가 조건을 바꿔도 협상에서만 옛 기준에 묶인다.
 */
public interface FreelancerConditionReaderPort {

    /**
     * 등록해둔 최저 수용 금액(원, 월 단가 기준). 등록하지 않았으면 empty.
     *
     * @param freelancerProfileId 협상이 아는 프리랜서 식별자(freelancer_profile.id)
     */
    Optional<Long> findMinAcceptAmount(Long freelancerProfileId);
}
