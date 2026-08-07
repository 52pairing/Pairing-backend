package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDate;

/** 자격증·어학 한 건(선택). 이력서 저장 시 전체 교체된다. */
@Getter
public class Certificate {

    private final LocalDate acquiredDate;
    private final String name;
    private final String issuerScore;
    private final String note;

    private Certificate(LocalDate acquiredDate, String name, String issuerScore, String note) {
        if (acquiredDate == null || name == null || name.isBlank()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
        this.acquiredDate = acquiredDate;
        this.name = name;
        this.issuerScore = issuerScore;
        this.note = note;
    }

    public static Certificate of(LocalDate acquiredDate, String name, String issuerScore, String note) {
        return new Certificate(acquiredDate, name, issuerScore, note);
    }
}
