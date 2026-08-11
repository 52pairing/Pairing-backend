package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이력서 본문. 계정과 1:1이다. (요구사항 R21 화면 2)
 *
 * <p>성명·생년월일은 계정/프로필 정보에서 가져오는 값이라 여기 없다. 연락처(전화번호·이메일)는
 * 비워두면 계정 값을 쓰는 재정의(override)라 조회 시점에 계정 값과 함께 조합해야 한다.
 *
 * <p>{@code PUT /me/resume} 은 없으면 생성하고 있으면 이 모델 전체를 덮어쓴다. 필수 항목은
 * 요청 DTO 검증에서 이미 걸러지므로, 이 모델에 도달하면 항상 작성 완료 상태다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume {

    private Long id;
    private Long accountId;
    private ResumeStatus status;
    private Long profileFileId;
    private String contactPhone;
    private String contactEmail;
    private String zipCode;
    private String address;
    private String addressDetail;
    private String selfIntroduction;
    private Long portfolioFileId;
    private List<Education> educations;
    private List<Career> careers;
    private List<Certificate> certificates;
    private List<ResumeLink> links;
    private ResumeAgreements agreements;
    private LocalDateTime updatedAt;

    private Resume(Long id, Long accountId, ResumeStatus status, Long profileFileId, String contactPhone,
                   String contactEmail, String zipCode, String address, String addressDetail,
                   String selfIntroduction, Long portfolioFileId,
                   List<Education> educations, List<Career> careers, List<Certificate> certificates,
                   List<ResumeLink> links, ResumeAgreements agreements, LocalDateTime updatedAt) {
        validate(accountId, profileFileId, address, selfIntroduction, portfolioFileId, educations, careers);
        if (agreements == null) {
            throw new BusinessException(FreelancerErrorCode.AGREEMENTS_REQUIRED);
        }
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
        this.educations = educations;
        this.careers = careers;
        this.certificates = certificates;
        this.links = links;
        this.agreements = agreements;
        this.updatedAt = updatedAt;
    }

    public static Resume create(Long accountId, Long profileFileId, String contactPhone, String contactEmail,
                                String zipCode, String address, String addressDetail, String selfIntroduction,
                                Long portfolioFileId,
                                List<Education> educations, List<Career> careers, List<Certificate> certificates,
                                List<ResumeLink> links, ResumeAgreements agreements) {
        return new Resume(null, accountId, ResumeStatus.COMPLETED, profileFileId, contactPhone, contactEmail,
                zipCode, address, addressDetail, selfIntroduction, portfolioFileId, educations, careers,
                certificates, links, agreements, LocalDateTime.now());
    }

    public static Resume reconstitute(Long id, Long accountId, ResumeStatus status, Long profileFileId,
                                      String contactPhone, String contactEmail, String zipCode, String address,
                                      String addressDetail, String selfIntroduction, Long portfolioFileId,
                                      List<Education> educations, List<Career> careers,
                                      List<Certificate> certificates, List<ResumeLink> links,
                                      ResumeAgreements agreements, LocalDateTime updatedAt) {
        return new Resume(id, accountId, status, profileFileId, contactPhone, contactEmail, zipCode, address,
                addressDetail, selfIntroduction, portfolioFileId, educations, careers, certificates, links,
                agreements, updatedAt);
    }

    /** {@code PUT /me/resume} 재호출. 기존 값을 전부 새 값으로 교체한다. */
    public void replaceWith(Long profileFileId, String contactPhone, String contactEmail, String zipCode,
                            String address, String addressDetail, String selfIntroduction, Long portfolioFileId,
                            List<Education> educations, List<Career> careers, List<Certificate> certificates,
                            List<ResumeLink> links) {
        validate(this.accountId, profileFileId, address, selfIntroduction, portfolioFileId, educations, careers);
        this.status = ResumeStatus.COMPLETED;
        this.profileFileId = profileFileId;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.selfIntroduction = selfIntroduction;
        this.portfolioFileId = portfolioFileId;
        this.educations = educations;
        this.careers = careers;
        this.certificates = certificates;
        this.links = links;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validate(Long accountId, Long profileFileId, String address, String selfIntroduction,
                                 Long portfolioFileId, List<Education> educations, List<Career> careers) {
        if (accountId == null || profileFileId == null || address == null || address.isBlank()
                || selfIntroduction == null || selfIntroduction.isBlank() || portfolioFileId == null
                || educations == null || educations.isEmpty() || careers == null || careers.isEmpty()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
    }
}
