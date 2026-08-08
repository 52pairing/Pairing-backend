package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.Getter;

/** 외부 링크 한 건(선택, 깃허브·포트폴리오·노션 등). 이력서 저장 시 전체 교체된다. */
@Getter
public class ResumeLink {

    private final String url;

    private ResumeLink(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
        this.url = url;
    }

    public static ResumeLink of(String url) {
        return new ResumeLink(url);
    }
}
