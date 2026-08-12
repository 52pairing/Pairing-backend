package com.pairing.freelancer.infrastructure.mapper;

import com.pairing.freelancer.domain.model.Career;
import com.pairing.freelancer.domain.model.Certificate;
import com.pairing.freelancer.domain.model.Education;
import com.pairing.freelancer.domain.model.Resume;
import com.pairing.freelancer.domain.model.ResumeAgreements;
import com.pairing.freelancer.domain.model.ResumeLink;
import com.pairing.freelancer.infrastructure.persistence.ResumeCareerEmbeddable;
import com.pairing.freelancer.infrastructure.persistence.ResumeCertificateEmbeddable;
import com.pairing.freelancer.infrastructure.persistence.ResumeEducationEmbeddable;
import com.pairing.freelancer.infrastructure.persistence.ResumeJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ResumeMapper {

    default ResumeJpaEntity toJpaEntity(Resume resume) {
        if (resume == null) {
            return null;
        }
        return new ResumeJpaEntity(
                resume.getId(),
                resume.getAccountId(),
                resume.getStatus(),
                resume.getProfileFileId(),
                resume.getContactPhone(),
                resume.getContactEmail(),
                resume.getZipCode(),
                resume.getAddress(),
                resume.getAddressDetail(),
                resume.getSelfIntroduction(),
                resume.getPortfolioFileId(),
                resume.getAgreements().isProfileCollectionAgreed(),
                resume.getAgreements().isProfileProvisionAgreed(),
                resume.getAgreements().isAiAnalysisAgreed(),
                resume.getAgreements().isCareerPortfolioUsageAgreed(),
                resume.getUpdatedAt(),
                resume.getEducations().stream().map(this::toEmbeddable).toList(),
                resume.getCareers().stream().map(this::toEmbeddable).toList(),
                resume.getCertificates().stream().map(this::toEmbeddable).toList(),
                resume.getLinks().stream().map(this::toUrl).toList()
        );
    }

    default ResumeEducationEmbeddable toEmbeddable(Education education) {
        if (education == null) {
            return null;
        }
        return new ResumeEducationEmbeddable(education.getStartDate(), education.getEndDate(),
                education.getSchoolName(), education.getMajor(), education.getGraduationStatus(),
                education.getCampusType());
    }

    default Education toDomainEducation(ResumeEducationEmbeddable embeddable) {
        if (embeddable == null) {
            return null;
        }
        return Education.of(embeddable.getStartDate(), embeddable.getEndDate(), embeddable.getSchoolName(),
                embeddable.getMajor(), embeddable.getGraduationStatus(), embeddable.getCampusType());
    }

    default ResumeCareerEmbeddable toEmbeddable(Career career) {
        if (career == null) {
            return null;
        }
        return new ResumeCareerEmbeddable(career.getStartDate(), career.getEndDate(), career.getCompanyName(),
                career.getDepartment(), career.getPosition(), career.getJobDescription());
    }

    default Career toDomainCareer(ResumeCareerEmbeddable embeddable) {
        if (embeddable == null) {
            return null;
        }
        return Career.of(embeddable.getStartDate(), embeddable.getEndDate(), embeddable.getCompanyName(),
                embeddable.getDepartment(), embeddable.getPosition(), embeddable.getJobDescription());
    }

    default ResumeCertificateEmbeddable toEmbeddable(Certificate certificate) {
        if (certificate == null) {
            return null;
        }
        return new ResumeCertificateEmbeddable(certificate.getAcquiredDate(), certificate.getName(),
                certificate.getIssuer(), certificate.getScore(), certificate.getNote());
    }

    default Certificate toDomainCertificate(ResumeCertificateEmbeddable embeddable) {
        if (embeddable == null) {
            return null;
        }
        return Certificate.of(embeddable.getAcquiredDate(), embeddable.getName(), embeddable.getIssuer(),
                embeddable.getScore(), embeddable.getNote());
    }

    default String toUrl(ResumeLink link) {
        return link == null ? null : link.getUrl();
    }

    default ResumeLink toDomainLink(String url) {
        return url == null ? null : ResumeLink.of(url);
    }

    default Resume toDomain(ResumeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<Education> educations = entity.getEducations().stream().map(this::toDomainEducation).toList();
        List<Career> careers = entity.getCareers().stream().map(this::toDomainCareer).toList();
        List<Certificate> certificates = entity.getCertificates().stream().map(this::toDomainCertificate).toList();
        List<ResumeLink> links = entity.getLinks().stream().map(this::toDomainLink).toList();

        return Resume.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getStatus(),
                entity.getProfileFileId(),
                entity.getContactPhone(),
                entity.getContactEmail(),
                entity.getZipCode(),
                entity.getAddress(),
                entity.getAddressDetail(),
                entity.getSelfIntroduction(),
                entity.getPortfolioFileId(),
                educations,
                careers,
                certificates,
                links,
                ResumeAgreements.of(entity.isProfileCollectionAgreed(), entity.isProfileProvisionAgreed(),
                        entity.isAiAnalysisAgreed(), entity.isCareerPortfolioUsageAgreed()),
                entity.getUpdatedAt()
        );
    }
}
