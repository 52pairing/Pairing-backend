package com.pairing.contract.infrastructure;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.contract.application.port.FreelancerGradeReaderPort;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FreelancerGradeReaderPort 구현.
 *
 * <p>{@code freelancer_profile.grade} 는 문자열이라 enum 으로 바꾼다. 값이 깨져 있으면 수수료를
 * 더 받는 쪽(할인 없음)으로 떨어뜨린다. 반대로 하면 못 받은 수수료를 나중에 청구해야 한다.
 * 프로젝트 도메인의 {@code ClientProfileReaderAdapter} 와 같은 방식이다.
 */
@Component
@RequiredArgsConstructor
public class FreelancerGradeReaderAdapter implements FreelancerGradeReaderPort {

    private final AccountQueryUseCase accountQueryUseCase;

    @Override
    public FreelancerGrade findGrade(Long freelancerProfileId) {
        return accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .map(FreelancerProfile::getGrade)
                .map(FreelancerGrade::of)
                .orElse(FreelancerGrade.JUNIOR);
    }
}
