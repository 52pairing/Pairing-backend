package com.pairing.review.application.service;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.application.result.SiteReviewResult;
import com.pairing.review.application.usecase.SiteReviewPublicUseCase;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.repository.SiteReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 비로그인 메인에 노출할 사이트 리뷰 조회.
 *
 * <p>홍보 설정은 <b>관리자 서버(pairing-admin)</b>가 한다. 이 서비스는 그 결과를 읽어
 * 메인에 내려주기만 한다. 관리자가 아무것도 켜지 않으면 빈 목록이다.
 */
@Service
@RequiredArgsConstructor
public class SiteReviewPublicService implements SiteReviewPublicUseCase {

    /** 비로그인 메인에는 이 점수 이상인 홍보 리뷰만 내려간다. */
    private static final int HOME_MIN_SCORE = 4;

    // 프로젝트·계정 조회 포트를 더는 들지 않는다. 이름은 후기에 복사돼 있다.
    private final SiteReviewRepository siteReviewRepository;

    /**
     * 정렬은 {@code Pageable} 로 넘기지 않는다. 쿼리가 {@code createdAt DESC, id DESC} 를
     * 직접 들고 있어서, 여기서 또 지정하면 두 곳이 어긋날 때 어느 쪽이 이기는지가 불분명해진다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SiteReviewResult> findPromoted(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return siteReviewRepository.findPromoted(HOME_MIN_SCORE, pageable).stream()
                .map(this::toResult)
                .toList();
    }

    /**
     * 후기에 복사해 둔 값만 읽는다. <b>여기서 다른 도메인을 조회하지 않는다.</b>
     *
     * <p>예전에는 후기마다 프로젝트와 계정을 되찾았다. 6건이면 목록 1회 + 프로젝트 6회 + 작성자 6회였고,
     * 프로젝트 조회는 제목 한 칸을 얻으려고 포지션·스킬·첨부까지 딸려 오는 애그리거트 조회라
     * 실제로는 31번이 나갔다. 지금은 목록 1회로 끝난다.
     */
    private SiteReviewResult toResult(SiteReview siteReview) {
        return new SiteReviewResult(
                siteReview.getId(),
                siteReview.getWriterRole(),
                maskedWriterName(siteReview),
                siteReview.getScore(),
                siteReview.getContent(),
                siteReview.getProjectTitle(),
                siteReview.isPromoted(),
                siteReview.getCreatedAt());
    }

    /** 비로그인 화면이라 실명을 그대로 내보내지 않는다. 이름이 없으면 역할 이름으로 대체한다. */
    private String maskedWriterName(SiteReview siteReview) {
        String name = siteReview.getWriterName();
        if (name == null || name.isBlank()) {
            return siteReview.getWriterRole() == PartyRole.CLIENT ? "클라이언트" : "프리랜서";
        }
        return mask(name);
    }

    /** 첫 글자만 남기고 가린다. (예: 이프리 -&gt; 이**) */
    private String mask(String name) {
        if (name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
