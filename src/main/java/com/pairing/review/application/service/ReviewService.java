package com.pairing.review.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.review.application.command.CreateReviewCommand;
import com.pairing.review.application.result.PendingReviewResult;
import com.pairing.review.application.result.ReviewResult;
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.application.usecase.ReviewUseCase;
import com.pairing.review.domain.model.Review;
import com.pairing.review.domain.model.ReviewRating;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.repository.ReviewRepository;
import com.pairing.review.domain.repository.SiteReviewRepository;
import com.pairing.review.exception.ReviewErrorCode;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** 작성 대기는 화면이 목록으로 훑는 용도라 페이지를 나누지 않는다. 한 사람이 이만큼 밀릴 일은 없다. */
    private static final int PENDING_LIMIT = 100;

    private final ReviewRepository reviewRepository;
    private final SiteReviewRepository siteReviewRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final ContractQueryUseCase contractQueryUseCase;
    private final SettlementQueryUseCase settlementQueryUseCase;
    private final PlatformTransactionManager transactionManager;

    @Override
    public ReviewResult create(CreateReviewCommand command) {
        if (reviewRepository.existsByContractIdAndReviewerAccountId(command.contractId(),
                command.reviewerAccountId())) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        Account reviewer = accountQueryUseCase.getById(command.reviewerAccountId());

        // 계약에서 프로젝트와 상대방을 유도한다. 없는 계약이면 CT_001, 당사자가 아니면 CT_002 로 여기서 끊긴다.
        // 프론트가 보낸 값을 그대로 믿으면 남의 계약에 리뷰를 남기거나, 없는 참조로 저장하다 500 이 난다.
        ContractDetail contract = contractQueryUseCase.getDetail(command.contractId(),
                command.reviewerAccountId());

        // 대금 지급이 끝나야 리뷰가 열린다(P51). 작성 대기 목록에서만 거르면 API 를 직접 부르는 경로가 뚫린다.
        if (!isReviewable(contract.contract(), command.reviewerAccountId())) {
            throw new BusinessException(ReviewErrorCode.NOT_REVIEWABLE_YET);
        }

        boolean reviewerIsClient = Objects.equals(contract.client().accountId(), command.reviewerAccountId());
        PartyRole reviewerRole = reviewerIsClient ? PartyRole.CLIENT : PartyRole.FREELANCER;
        PartyRole revieweeRole = reviewerIsClient ? PartyRole.FREELANCER : PartyRole.CLIENT;
        Long revieweeAccountId = reviewerIsClient
                ? contract.freelancer().accountId()
                : contract.client().accountId();

        // 상대 프로필이 지워지면 계약에 이름만 남고 계정 참조가 끊긴다. 그 계약은 리뷰 대상이 없다.
        if (revieweeAccountId == null) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }

        Long projectId = contract.contract().getProjectId();

        Review review = Review.create(command.contractId(), projectId, command.reviewerAccountId(),
                reviewerRole, revieweeAccountId, revieweeRole, command.counterpartScore(),
                command.counterpartContent());
        SiteReview siteReview = SiteReview.create(command.contractId(), projectId,
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
    public Map<Long, ReviewRating> getRatings(Collection<Long> accountIds) {
        return reviewRepository.findRatingsByRevieweeAccountIds(accountIds).stream()
                .collect(Collectors.toMap(ReviewRating::accountId, Function.identity()));
    }

    @Override
    public List<PendingReviewResult> findPending(Long accountId) {
        // 기준은 "대금 지급 완료"다(R22). 성공보수 수수료까지 결제되면 프로젝트가 CLOSED 가 된다.
        // 계약의 COMPLETED 는 검수 완료 시점이라 성공보수 결제 전이어서 여기서는 쓰지 않는다.
        return contractQueryUseCase.findMine(accountId, null, null, PageRequest.of(0, PENDING_LIMIT))
                .getContent().stream()
                .filter(summary -> isReviewable(summary.contract(), accountId))
                .filter(summary -> !reviewRepository.existsByContractIdAndReviewerAccountId(
                        summary.contract().getId(), accountId))
                .map(summary -> new PendingReviewResult(
                        summary.contract().getId(),
                        summary.projectTitle(),
                        summary.counterpartName(),
                        summary.contract().getCompletedAt()))
                .toList();
    }

    /**
     * 이 계약에 리뷰를 쓸 수 있는지. 두 조건을 모두 만족해야 한다.
     *
     * <ol>
     *   <li>거래가 끝났다 — 프로젝트가 CLOSED</li>
     *   <li><b>내 몫의 성공보수를 냈다</b></li>
     * </ol>
     *
     * <p>프로젝트를 CLOSED 로 만드는 것은 클라이언트 성공보수 결제뿐이다(P30). 그래서 1번만 보면
     * 프리랜서는 자기 성공보수를 내지 않고도 리뷰를 쓸 수 있다. "대금 지급 완료 후 작성"(P51)과
     * 어긋나므로 납부자 본인 기준으로 한 번 더 확인한다.
     *
     * <p>클라이언트에게는 2번이 사실상 항상 참이다. CLOSED 라는 것 자체가 본인이 냈다는 뜻이라
     * 조건이 겹치지만, 판정 근거를 역할별로 나누면 P30 이 바뀔 때 또 어긋난다.
     */
    private boolean isReviewable(Contract contract, Long reviewerAccountId) {
        return isSettled(contract) && hasPaidOwnSuccessFee(reviewerAccountId, contract.getProjectId());
    }

    /** 이 프로젝트에서 본인이 납부자인 성공보수 정산이 결제 완료됐는지. */
    private boolean hasPaidOwnSuccessFee(Long accountId, Long projectId) {
        return !settlementQueryUseCase.findMine(accountId, projectId, SettlementPhase.SUCCESS_FEE,
                SettlementStatus.PAID, PageRequest.of(0, 1)).isEmpty();
    }

    /**
     * 거래가 끝났는지. 프로젝트가 CLOSED 면 클라이언트 성공보수까지 결제된 것이다.
     *
     * <p>파기·거부된 계약은 제외한다. 끝까지 가지 않은 거래는 평가 대상이 아니다.
     *
     * <p>계약마다 프로젝트를 한 번씩 읽는다. 한 사람의 미작성 리뷰가 많아질 일이 없어 지금은 이대로 둔다.
     */
    private boolean isSettled(Contract contract) {
        if (contract.getStatus() != ContractStatus.SIGNED && contract.getStatus() != ContractStatus.COMPLETED) {
            return false;
        }
        try {
            return projectQueryUseCase.getById(contract.getProjectId()).getStatus() == ProjectStatus.CLOSED;
        } catch (BusinessException e) {
            // 프로젝트가 지워졌으면 판단할 근거가 없다. 계약은 5년 보관이라 프로젝트보다 오래 남는다.
            return false;
        }
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
