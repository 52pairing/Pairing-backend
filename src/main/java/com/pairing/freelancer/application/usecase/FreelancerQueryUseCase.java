package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.result.FreelancerMyPageResult;

public interface FreelancerQueryUseCase {

    /** 마이페이지 조회. 계정 정보 + 등급·평점 요약 + 이력서 완성 여부를 함께 반환한다. */
    FreelancerMyPageResult findMyPage(Long accountId);
}
