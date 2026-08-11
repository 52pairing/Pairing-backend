package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 자격증·어학 한 건(선택). 이력서 저장 시 전체 교체된다.
 *
 * <p>발급기관과 점수를 나눠 둔다. 화면 입력칸이 따로라, 합쳐 저장하면 수정 화면에서 다시
 * 두 칸으로 되돌릴 수 없다.
 */
@Getter
public class Certificate {

    private final LocalDate acquiredDate;
    private final String name;
    private final String issuer;
    private final String score;
    private final String note;

    private Certificate(LocalDate acquiredDate, String name, String issuer, String score, String note) {
        if (acquiredDate == null || name == null || name.isBlank()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
        this.acquiredDate = acquiredDate;
        this.name = name;
        this.issuer = issuer;
        this.score = score;
        this.note = note;
    }

    public static Certificate of(LocalDate acquiredDate, String name, String issuer, String score, String note) {
        return new Certificate(acquiredDate, name, issuer, score, note);
    }
}
