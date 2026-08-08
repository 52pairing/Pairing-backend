package com.pairing.review.application.usecase;

import com.pairing.review.application.command.CreateReviewCommand;
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
     * 대금 지급이 끝났는데 미작성인 계약 목록.
     *
     * <p>TODO: contract/settlement 도메인이 구현되면 완료+지급 완료 계약을 조회해 채운다.
     * 지금은 그 데이터 자체가 없어 항상 빈 리스트를 반환한다.
     */
    List<ReviewResult> findPending(Long accountId);
}
