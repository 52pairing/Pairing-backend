package com.pairing.matching.infrastructure.directory;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.FreelancerCandidateSummaryUseCase;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.Career;
import com.pairing.freelancer.domain.model.Education;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import com.pairing.matching.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@link FreelancerDirectoryPort}의 실제 구현. account/freelancer 도메인의 인바운드 포트를 조합한다. */
@Component
@RequiredArgsConstructor
public class FreelancerDirectoryAdapter implements FreelancerDirectoryPort {

    private final FreelancerCandidateSummaryUseCase freelancerCandidateSummaryUseCase;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FreelancerConditionUseCase freelancerConditionUseCase;
    private final ResumeUseCase resumeUseCase;

    @Override
    public Long resolveFreelancerId(Long accountId) {
        return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                .map(FreelancerProfile::getId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
    }

    @Override
    public Long resolveAccountId(Long freelancerId) {
        return accountQueryUseCase.findFreelancerProfileById(freelancerId)
                .map(FreelancerProfile::getAccountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
    }

    @Override
    public FreelancerCardSummary findCardSummary(Long freelancerId) {
        FreelancerCandidateSummaryResult summary = freelancerCandidateSummaryUseCase.getSummary(freelancerId);
        return new FreelancerCardSummary(summary.name(), summary.profileImageUrl(), summary.grade(),
                summary.ratingAverage(), summary.reviewCount());
    }

    @Override
    public FreelancerConditionResponse findCondition(Long freelancerId) {
        Long accountId = accountQueryUseCase.findFreelancerProfileById(freelancerId)
                .map(FreelancerProfile::getAccountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
        return freelancerConditionUseCase.findMyCondition(accountId)
                .map(FreelancerConditionResponse::from)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
    }

    @Override
    public Optional<Long> findFreelancerId(Long accountId) {
        return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                .map(FreelancerProfile::getId);
    }

    @Override
    public FreelancerResumeSummary findResumeSummary(Long freelancerId) {
        Long accountId = accountQueryUseCase.findFreelancerProfileById(freelancerId)
                .map(FreelancerProfile::getAccountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
        ResumeResult resume = resumeUseCase.findMyResume(accountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));

        // 학과·담당업무만 뽑는다. 학교명·회사명·부서/직급은 임베딩 대상이 아니다
        // (`.ai/STATE.md` "[2][3] 임베딩 25 + 조건점수 75 / 텍스트 임베딩 재설계" 참고).
        //
        // **비어 있는 값은 여기서 걸러낸다.** `resume_education.major`와
        // `resume_career.job_description`은 둘 다 nullable 이고(선택 입력), 실제로 NULL 인 행이 있다.
        // 2026-08-13 임베딩 일괄 재색인이 이 지점에서 NPE 로 무더기 실패했다 - 대상 1601명 중
        // 1011번까지만 벡터가 생기고 나머지가 조용히 빠졌는데, 원인을 찾는 데 반나절이 걸렸다.
        //
        // FreelancerResumeSummary 의 javadoc 은 원래부터 "학과 미입력 건은 빠진다"고 적고 있었다.
        // 그 약속을 코드가 지키지 않았던 것이라, 데이터를 고치는 게 아니라 여기를 고치는 게 맞다.
        // 임베딩 텍스트는 달라지지 않는다 - FreelancerEmbeddingTextBuilder 가 이미 blank 를 건너뛴다.
        List<String> majors = resume.educations().stream()
                .filter(Objects::nonNull)
                .map(Education::getMajor)
                .filter(FreelancerDirectoryAdapter::hasText)
                .toList();
        List<String> careerDescriptions = resume.careers().stream()
                .filter(Objects::nonNull)
                .map(Career::getJobDescription)
                .filter(FreelancerDirectoryAdapter::hasText)
                .toList();
        return new FreelancerResumeSummary(resume.selfIntroduction(), majors, careerDescriptions);
    }

    @Override
    public List<Long> findAllFreelancerIdsWithResume() {
        return resumeUseCase.findAllAccountIdsWithResume().stream()
                .flatMap(accountId -> accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                        .map(FreelancerProfile::getId)
                        .stream())
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
