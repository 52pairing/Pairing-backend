package com.pairing.review.application.usecase;

import com.pairing.review.application.command.CreateReviewCommand;
import com.pairing.review.application.result.PendingReviewResult;
import com.pairing.review.application.result.ReviewResult;
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.domain.model.ReviewRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ReviewUseCase {

    /** 이미 이 계약을 리뷰했으면 {@code RV_001}. */
    ReviewResult create(CreateReviewCommand command);

    /** 내가 상대에게서 받은 평가. */
    Page<ReviewResult> findReceived(Long accountId, Pageable pageable);

    /** 내가 상대에게 남긴 평가. */
    Page<ReviewResult> findWritten(Long accountId, Pageable pageable);

    ReviewSummaryResult getSummary(Long accountId);

    /**
     * 여러 계정의 평균 별점·건수를 한 번에. 목록 화면이 사람마다 되묻지 않게 하려는 것이다.
     *
     * <p>{@link #getSummary} 와 달리 <b>등급을 계산하지 않는다.</b> 등급은 계정과 프로필을 더 읽어야
     * 나오는데, 목록을 그리는 쪽은 대개 프로필을 이미 들고 있어 그 조회가 통째로 낭비다.
     *
     * <p>받은 리뷰가 없는 계정은 <b>결과에 없다.</b> 0건과 조회 실패를 구분해야 하는 쪽은
     * {@code ReviewRating.empty} 로 채워 쓴다.
     */
    Map<Long, ReviewRating> getRatings(Collection<Long> accountIds);

    /**
     * 대금 지급이 끝난 계약 중 내가 아직 리뷰를 쓰지 않은 것.
     *
     * <p>성공보수 수수료까지 결제되어 프로젝트가 종료된 건만 대상이다. 리뷰는 그 시점부터 열린다. (P51)
     */
    List<PendingReviewResult> findPending(Long accountId);
}
