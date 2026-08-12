package com.pairing.freelancer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** resume_career 테이블의 한 행. resume 에 종속된다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeCareerEmbeddable {

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "job_description", length = 2000)
    private String jobDescription;

    public ResumeCareerEmbeddable(LocalDate startDate, LocalDate endDate, String companyName,
                                  String department, String position, String jobDescription) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.companyName = companyName;
        this.department = department;
        this.position = position;
        this.jobDescription = jobDescription;
    }
}
