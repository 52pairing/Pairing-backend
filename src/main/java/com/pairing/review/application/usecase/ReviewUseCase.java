package com.pairing.review.application.usecase;

import com.pairing.review.application.command.CreateReviewCommand;
import com.pairing.review.application.result.PendingReviewResult;
import com.pairing.review.application.result.ReviewResult;
import com.pairing.review.application.result.ReviewSummaryResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewUseCase {

    /** 이미 이 계약을 리뷰했으면 {@code RV_001}. */
    ReviewResult create(CreateReviewCommand command);

    /** 내가 상대에게서 받은 평가. */
    Page<ReviewResult> findReceived(Long accountId, Pageable pageable);

    /** 내가 상대에게 남긴 평가. */
    Page<ReviewResult> findWritten(Long accountId, Pageable pageable);

    ReviewSummaryResult getSummary(Long accountId);

    /**
     * 대금 지급이 끝난 계약 중 내가 아직 리뷰를 쓰지 않은 것.
     *
     * <p>성공보수 수수료까지 결제되어 프로젝트가 종료된 건만 대상이다. 리뷰는 그 시점부터 열린다. (P51)
     */
    List<PendingReviewResult> findPending(Long accountId);
}
