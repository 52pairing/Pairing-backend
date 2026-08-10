package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.command.FreelancerProfileUpdateCommand;
import com.pairing.freelancer.application.result.FreelancerMyPageResult;

public interface FreelancerCommandUseCase {

    /** 마이페이지 수정. 본인 확인을 위해 현재 비밀번호를 함께 받는다(소셜 계정은 비밀번호가 없어 확인을 건너뛴다). */
    FreelancerMyPageResult updateMyPage(FreelancerProfileUpdateCommand command);
}
