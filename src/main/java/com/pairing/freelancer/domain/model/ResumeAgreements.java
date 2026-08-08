package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.Getter;

/**
 * 이력서(프로필) 등록 시 받는 필수 동의 4종.
 *
 * <p>회원가입 약관과는 별개다. 등록 시 한 번만 받고, 이후 이력서를 수정해도 다시 묻지 않는다
 * ({@link Resume#replaceWith} 는 이 값을 건드리지 않는다). 넷 다 필수라 저장된 값은 항상 모두 true다.
 */
@Getter
public class ResumeAgreements {

    private final boolean profileCollectionAgreed;
    private final boolean profileProvisionAgreed;
    private final boolean aiAnalysisAgreed;
    private final boolean careerPortfolioUsageAgreed;

    private ResumeAgreements(boolean profileCollectionAgreed, boolean profileProvisionAgreed,
                             boolean aiAnalysisAgreed, boolean careerPortfolioUsageAgreed) {
        if (!profileCollectionAgreed || !profileProvisionAgreed || !aiAnalysisAgreed
                || !careerPortfolioUsageAgreed) {
            throw new BusinessException(FreelancerErrorCode.AGREEMENTS_REQUIRED);
        }
        this.profileCollectionAgreed = profileCollectionAgreed;
        this.profileProvisionAgreed = profileProvisionAgreed;
        this.aiAnalysisAgreed = aiAnalysisAgreed;
        this.careerPortfolioUsageAgreed = careerPortfolioUsageAgreed;
    }

    /**
     * 최초 이력서 등록 시, 그리고 DB에서 복원할 때 공통으로 쓴다. 넷 중 하나라도 false 면 {@code FR_004}.
     * 저장된 값은 등록 시 이미 검증을 통과한 것이라 복원 시에도 항상 true 로 들어온다.
     */
    public static ResumeAgreements of(boolean profileCollectionAgreed, boolean profileProvisionAgreed,
                                      boolean aiAnalysisAgreed, boolean careerPortfolioUsageAgreed) {
        return new ResumeAgreements(profileCollectionAgreed, profileProvisionAgreed, aiAnalysisAgreed,
                careerPortfolioUsageAgreed);
    }
}
