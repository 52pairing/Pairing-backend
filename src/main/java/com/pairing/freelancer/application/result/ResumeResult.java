package com.pairing.freelancer.application.result;

import com.pairing.freelancer.domain.model.Career;
import com.pairing.freelancer.domain.model.Certificate;
import com.pairing.freelancer.domain.model.Education;
import com.pairing.freelancer.domain.model.ResumeAgreements;
import com.pairing.freelancer.domain.model.ResumeLink;
import com.pairing.freelancer.domain.model.ResumeStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 이력서 조회 결과. 성명·생년월일은 계정/프로필에서 가져오고, 연락처는 이력서에 값이 없으면
 * 계정 값으로 이미 채워서(fallback 적용 완료) 내려준다.
 */
public record ResumeResult(
        Long resumeId,
        ResumeStatus status,
        String name,
        LocalDate birthDate,
        String contactPhone,
        String contactEmail,
        String zipCode,
        String address,
        String addressDetail,
        String profileImageUrl,
        String selfIntroduction,
        String portfolioUrl,
        List<Education> educations,
        List<Career> careers,
        List<Certificate> certificates,
        List<ResumeLink> links,
        ResumeAgreements agreements,
        LocalDateTime updatedAt
) {
}
