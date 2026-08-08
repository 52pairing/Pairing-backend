package com.pairing.review.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.review.application.command.CreateReviewCommand;
import com.pairing.review.application.result.ReviewResult;
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.application.usecase.ReviewUseCase;
import com.pairing.review.domain.model.Review;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.repository.ReviewRepository;
import com.pairing.review.domain.repository.SiteReviewRepository;
import com.pairing.review.exception.ReviewErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 클래스 전체를 {@code @Transactional} 로 묶지 않는다. {@code toResult()} 가 조회하는
 * {@code ProjectQueryUseCase.getById()} 는 대상이 없으면 예외를 던지는 계약이라(project 도메인 소유,
 * null 대신 예외를 던지도록 설계됨), 같은 트랜잭션 안에서 호출하면 그 예외를 여기서 잡아도
 * 트랜잭션은 이미 rollback-only로 표시되어 커밋 시점에 {@code UnexpectedRollbackException}이 난다.
 * 리뷰/사이트리뷰 저장은 {@link TransactionTemplate} 으로 필요한 구간만 짧게 묶는다.
 */
@Service
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final SiteReviewRepository siteReviewRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final PlatformTransactionManager transactionManager;

    @Override
    public ReviewResult create(CreateReviewCommand command) {
        if (reviewRepository.existsByContractIdAndReviewerAccountId(command.contractId(),
                command.reviewerAccountId())) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        Account reviewer = accountQueryUseCase.getById(command.reviewerAccountId());
        PartyRole reviewerRole = toPartyRole(reviewer.getRole());
        PartyRole revieweeRole = reviewerRole == PartyRole.CLIENT ? PartyRole.FREELANCER : PartyRole.CLIENT;

        Review review = Review.create(command.contractId(), command.projectId(), command.reviewerAccountId(),
                reviewerRole, command.revieweeAccountId(), revieweeRole, command.counterpartScore(),
                command.counterpartContent());
        SiteReview siteReview = SiteReview.create(command.contractId(), command.projectId(),
                command.reviewerAccountId(), reviewerRole, command.siteScore(), command.siteContent());

        Review saved = new TransactionTemplate(transactionManager).execute(status -> {
            Review savedReview = reviewRepository.save(review);
            siteReviewRepository.save(siteReview);
            return savedReview;
        });

        return toResult(saved, reviewer.getName());
    }

    @Override
    public Page<ReviewResult> findReceived(Long accountId, Pageable pageable) {
        return reviewRepository.findByRevieweeAccountId(accountId, pageable).map(this::toResult);
    }

    @Override
    public Page<ReviewResult> findWritten(Long accountId, Pageable pageable) {
        return reviewRepository.findByReviewerAccountId(accountId, pageable).map(this::toResult);
    }

    @Override
    public ReviewSummaryResult getSummary(Long accountId) {
        Double averageScore = reviewRepository.findAverageScoreByRevieweeAccountId(accountId);
        long reviewCount = reviewRepository.countByRevieweeAccountId(accountId);
        String grade = resolveCurrentGrade(accountId);
        return new ReviewSummaryResult(averageScore, (int) reviewCount, grade);
    }

    @Override
    public List<ReviewResult> findPending(Long accountId) {
        // TODO: contract/settlement 도메인 구현되면 완료+지급완료 계약 중 미작성 건 조회
        return List.of();
    }

    private String resolveCurrentGrade(Long accountId) {
        Account account = accountQueryUseCase.getById(accountId);
        if (account.getRole() == Role.FREELANCER) {
            return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                    .map(profile -> profile.getGrade())
                    .orElse(null);
        }
        return accountQueryUseCase.getClientProfile(accountId).getGrade();
    }

    private ReviewResult toResult(Review review) {
        String reviewerName = resolveDisplayName(review.getReviewerAccountId(), review.getReviewerRole());
        return toResult(review, reviewerName);
    }

    private ReviewResult toResult(Review review, String reviewerName) {
        return new ReviewResult(
                review.getId(),
                review.getContractId(),
                resolveProjectTitle(review.getProjectId()),
                reviewerName,
                review.getReviewerRole(),
                review.getScore(),
                review.getContent(),
                review.getCreatedAt()
        );
    }

    private String resolveProjectTitle(Long projectId) {
        try {
            return projectQueryUseCase.getById(projectId).getTitle();
        } catch (BusinessException e) {
            // 프로젝트 참조가 어긋나도(예: 삭제됨) 리뷰 목록 조회 자체는 막지 않는다.
            return null;
        }
    }

    private String resolveDisplayName(Long accountId, PartyRole role) {
        try {
            return role == PartyRole.CLIENT
                    ? accountQueryUseCase.getClientProfile(accountId).getCompanyName()
                    : accountQueryUseCase.getById(accountId).getName();
        } catch (BusinessException e) {
            return null;
        }
    }

    private PartyRole toPartyRole(Role role) {
        return role == Role.CLIENT ? PartyRole.CLIENT : PartyRole.FREELANCER;
    }
}
