package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.freelancer.domain.model.CampusType;
import com.pairing.freelancer.domain.model.GraduationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** resume_education 테이블의 한 행. resume 에 종속된다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeEducationEmbeddable {

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "school_name", nullable = false, length = 100)
    private String schoolName;

    @Column(name = "major", length = 100)
    private String major;

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_status", nullable = false, length = 20)
    private GraduationStatus graduationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "campus_type", length = 10)
    private CampusType campusType;

    public ResumeEducationEmbeddable(LocalDate startDate, LocalDate endDate, String schoolName, String major,
                                     GraduationStatus graduationStatus, CampusType campusType) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.schoolName = schoolName;
        this.major = major;
        this.graduationStatus = graduationStatus;
        this.campusType = campusType;
    }
}
