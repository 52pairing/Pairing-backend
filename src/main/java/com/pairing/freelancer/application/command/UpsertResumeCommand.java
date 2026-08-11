package com.pairing.freelancer.application.command;

import com.pairing.freelancer.domain.model.CampusType;
import com.pairing.freelancer.domain.model.GraduationStatus;

import java.time.LocalDate;
import java.util.List;

public record UpsertResumeCommand(
        Long accountId,
        Long profileFileId,
        String contactPhone,
        String contactEmail,
        String zipCode,
        String address,
        String addressDetail,
        String selfIntroduction,
        Long portfolioFileId,
        List<Education> educations,
        List<Career> careers,
        List<Certificate> certificates,
        List<String> links,
        Agreements agreements
) {

    public record Education(LocalDate startDate, LocalDate endDate, String schoolName, String major,
                            GraduationStatus graduationStatus, CampusType campusType) {
    }

    public record Career(LocalDate startDate, LocalDate endDate, String companyName, String departmentRank,
                         String jobDescription) {
    }

    public record Certificate(LocalDate acquiredDate, String name, String issuer, String score, String note) {
    }

    public record Agreements(boolean profileCollectionAgreed, boolean profileProvisionAgreed,
                             boolean aiAnalysisAgreed, boolean careerPortfolioUsageAgreed) {
    }
}
