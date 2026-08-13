package com.pairing.review.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.review.application.result.SiteReviewResult;
import com.pairing.global.exception.BusinessException;
import com.pairing.review.application.usecase.SiteReviewPublicUseCase;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.repository.SiteReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private final SiteReviewRepository siteReviewRepository;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final AccountQueryUseCase accountQueryUseCase;

    @Override
    @Transactional(readOnly = true)
    public List<SiteReviewResult> findPromoted(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return siteReviewRepository.findPromoted(HOME_MIN_SCORE, pageable).stream()
                .map(this::toResult)
                .toList();
    }

    private SiteReviewResult toResult(SiteReview siteReview) {
        String projectTitle;
        try {
            projectTitle = projectQueryUseCase.getById(siteReview.getProjectId()).getTitle();
        } catch (BusinessException e) {
            // 프로젝트가 지워졌어도 후기 자체는 보여준다. 프로젝트명만 비운다.
            projectTitle = null;
        }

        return new SiteReviewResult(
                siteReview.getId(),
                siteReview.getWriterRole(),
                maskedWriterName(siteReview),
                siteReview.getScore(),
                siteReview.getContent(),
                projectTitle,
                siteReview.isPromoted(),
                siteReview.getCreatedAt());
    }

    /** 비로그인 화면이라 실명을 그대로 내보내지 않는다. 이름을 못 찾으면 역할 이름으로 대체한다. */
    private String maskedWriterName(SiteReview siteReview) {
        String name = resolveWriterName(siteReview);
        if (name == null || name.isBlank()) {
            return siteReview.getWriterRole() == PartyRole.CLIENT ? "클라이언트" : "프리랜서";
        }
        return mask(name);
    }

    private String resolveWriterName(SiteReview siteReview) {
        try {
            return siteReview.getWriterRole() == PartyRole.CLIENT
                    ? accountQueryUseCase.getClientProfile(siteReview.getWriterAccountId()).getCompanyName()
                    : accountQueryUseCase.getById(siteReview.getWriterAccountId()).getName();
        } catch (BusinessException e) {
            return null;
        }
    }

    /** 첫 글자만 남기고 가린다. (예: 이프리 -&gt; 이**) */
    private String mask(String name) {
        if (name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
