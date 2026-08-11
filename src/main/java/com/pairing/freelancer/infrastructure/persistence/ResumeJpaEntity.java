package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.freelancer.domain.model.ResumeStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** resume 테이블 매핑. account 와 1:1이다. */
@Entity
@Table(name = "resume")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ResumeStatus status;

    @Column(name = "profile_file_id", nullable = false)
    private Long profileFileId;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    // 주소 찾기로 채워지는 값이라 기존 이력서에는 없다. 그래서 nullable 이다.
    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(name = "self_introduction", nullable = false, length = 2000)
    private String selfIntroduction;

    @Column(name = "portfolio_file_id", nullable = false)
    private Long portfolioFileId;

    @Column(name = "profile_collection_agreed", nullable = false)
    private boolean profileCollectionAgreed;

    @Column(name = "profile_provision_agreed", nullable = false)
    private boolean profileProvisionAgreed;

    @Column(name = "ai_analysis_agreed", nullable = false)
    private boolean aiAnalysisAgreed;

    @Column(name = "career_portfolio_usage_agreed", nullable = false)
    private boolean careerPortfolioUsageAgreed;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 이력서 저장 시 하위 목록은 전체 교체된다. 개별 행 단위 CRUD 가 없어 컬렉션 매핑으로 충분하다.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "resume_education", joinColumns = @JoinColumn(name = "resume_id"))
    @OrderColumn(name = "sort_order")
    private List<ResumeEducationEmbeddable> educations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "resume_career", joinColumns = @JoinColumn(name = "resume_id"))
    @OrderColumn(name = "sort_order")
    private List<ResumeCareerEmbeddable> careers = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "resume_certificate", joinColumns = @JoinColumn(name = "resume_id"))
    @OrderColumn(name = "sort_order")
    private List<ResumeCertificateEmbeddable> certificates = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "resume_link", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "url", length = 500)
    @OrderColumn(name = "sort_order")
    private List<String> links = new ArrayList<>();

    public ResumeJpaEntity(Long id, Long accountId, ResumeStatus status, Long profileFileId, String contactPhone,
                           String contactEmail, String zipCode, String address, String addressDetail,
                           String selfIntroduction, Long portfolioFileId,
                           boolean profileCollectionAgreed, boolean profileProvisionAgreed,
                           boolean aiAnalysisAgreed, boolean careerPortfolioUsageAgreed, LocalDateTime updatedAt,
                           List<ResumeEducationEmbeddable> educations, List<ResumeCareerEmbeddable> careers,
                           List<ResumeCertificateEmbeddable> certificates, List<String> links) {
        this.id = id;
        this.accountId = accountId;
        this.status = status;
        this.profileFileId = profileFileId;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.selfIntroduction = selfIntroduction;
        this.portfolioFileId = portfolioFileId;
        this.profileCollectionAgreed = profileCollectionAgreed;
        this.profileProvisionAgreed = profileProvisionAgreed;
        this.aiAnalysisAgreed = aiAnalysisAgreed;
        this.careerPortfolioUsageAgreed = careerPortfolioUsageAgreed;
        this.updatedAt = updatedAt;
        this.educations = educations == null ? new ArrayList<>() : educations;
        this.careers = careers == null ? new ArrayList<>() : careers;
        this.certificates = certificates == null ? new ArrayList<>() : certificates;
        this.links = links == null ? new ArrayList<>() : links;
    }
}
