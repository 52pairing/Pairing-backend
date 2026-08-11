package com.pairing.matching.application.port.out;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.application.result.FreelancerResumeSummary;

import java.util.List;

/**
 * freelancer 도메인 조회 포트. 매칭은 이 인터페이스로만 프리랜서 정보를 읽는다.
 *
 * <p>{@code infrastructure.directory.FreelancerDirectoryAdapter}가 실제 구현체다.
 */
public interface FreelancerDirectoryPort {

    /** 로그인 계정(accountId) -&gt; freelancer_profile.id. 매칭 요청·스냅샷은 전부 이 값을 쓴다(account.id 아님). */
    Long resolveFreelancerId(Long accountId);

    /**
     * freelancer_profile.id -&gt; 로그인 계정(accountId). {@link #resolveFreelancerId}의 반대 방향이다.
     * 알림은 계정 단위로 보내는데 매칭이 들고 있는 건 freelancerId뿐이라 변환이 필요하다.
     */
    Long resolveAccountId(Long freelancerId);

    /** 후보 카드 노출용 요약(이름/사진/등급/평점). */
    FreelancerCardSummary findCardSummary(Long freelancerId);

    /**
     * 협상 스냅샷 캡처·가드 재검증에 쓰는 현재 조건. 새 스키마를 만들지 않고
     * freelancer 도메인이 이미 정의한 {@code FreelancerConditionResponse}를 그대로 재사용한다.
     */
    FreelancerConditionResponse findCondition(Long freelancerId);

    /**
     * 프리랜서 임베딩 텍스트(자기소개+경력사항) 조립에 쓰는 이력서 요약.
     * {@code ResumeUpdatedEventListener}가 이력서 저장 이벤트를 받을 때마다 호출한다.
     */
    FreelancerResumeSummary findResumeSummary(Long freelancerId);

    /** 이력서를 등록한 모든 프리랜서 ID. 임베딩 일괄 재색인 대상을 고르는 데 쓴다. */
    List<Long> findAllFreelancerIdsWithResume();
}
