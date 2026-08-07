package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDate;

/** 경력 한 건. 이력서 저장 시 전체 교체된다. */
@Getter
public class Career {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String companyName;
    private final String departmentRank;
    private final String jobDescription;

    private Career(LocalDate startDate, LocalDate endDate, String companyName, String departmentRank,
                   String jobDescription) {
        if (startDate == null || companyName == null || companyName.isBlank()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.companyName = companyName;
        this.departmentRank = departmentRank;
        this.jobDescription = jobDescription;
    }

    public static Career of(LocalDate startDate, LocalDate endDate, String companyName, String departmentRank,
                            String jobDescription) {
        return new Career(startDate, endDate, companyName, departmentRank, jobDescription);
    }
}
