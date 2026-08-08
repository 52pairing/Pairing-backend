package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDate;

/** 학력 한 건. 이력서 저장 시 전체 교체된다. */
@Getter
public class Education {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String schoolName;
    private final String major;
    private final GraduationStatus graduationStatus;
    private final CampusType campusType;

    private Education(LocalDate startDate, LocalDate endDate, String schoolName, String major,
                      GraduationStatus graduationStatus, CampusType campusType) {
        if (startDate == null || schoolName == null || schoolName.isBlank() || graduationStatus == null) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.schoolName = schoolName;
        this.major = major;
        this.graduationStatus = graduationStatus;
        this.campusType = campusType;
    }

    public static Education of(LocalDate startDate, LocalDate endDate, String schoolName, String major,
                               GraduationStatus graduationStatus, CampusType campusType) {
        return new Education(startDate, endDate, schoolName, major, graduationStatus, campusType);
    }
}
