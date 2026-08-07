package com.pairing.freelancer.presentation.api.response;

import com.pairing.freelancer.domain.model.CampusType;
import com.pairing.freelancer.domain.model.GraduationStatus;
import com.pairing.freelancer.domain.model.ResumeStatus;
import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 이력서 상세. 성명·생년월일은 계정 정보에서 가져온 값이라 수정할 수 없다. */
@Schema(description = "이력서 응답")
public record ResumeResponse(

        @Schema(description = "이력서 ID", example = "60") Long resumeId,
        @Schema(description = "상태") ResumeStatus status,
        @Schema(description = "성명(수정 불가)", example = "홍길동") String name,
        @Schema(description = "생년월일(수정 불가)") LocalDate birthDate,
        @Schema(description = "연락처", example = "01012345678") String contactPhone,
        @Schema(description = "연락 이메일", example = "user@pairing.com") String contactEmail,
        @Schema(description = "주소", example = "서울 강남구") String address,
        @Schema(description = "프로필 사진 URL") String profileImageUrl,
        @Schema(description = "간단 자기소개") String selfIntroduction,
        @Schema(description = "포트폴리오 URL") String portfolioUrl,
        @Schema(name = "ResumeEducationResponse", description = "학력") List<Education> educations,
        @Schema(name = "ResumeCareerResponse", description = "경력") List<Career> careers,
        @Schema(name = "ResumeCertificateResponse", description = "자격증·어학") List<Certificate> certificates,
        @Schema(description = "링크") List<String> links
) implements CdnMappable {

    @Schema(description = "학력")
    public record Education(
            @Schema(description = "입학일") LocalDate startDate,
            @Schema(description = "졸업일") LocalDate endDate,
            @Schema(description = "학교명") String schoolName,
            @Schema(description = "전공") String major,
            @Schema(description = "졸업 구분") GraduationStatus graduationStatus,
            @Schema(description = "본교/분교") CampusType campusType
    ) {
    }

    @Schema(description = "경력")
    public record Career(
            @Schema(description = "입사일") LocalDate startDate,
            @Schema(description = "퇴사일") LocalDate endDate,
            @Schema(description = "회사/기관명") String companyName,
            @Schema(description = "부서 및 직급") String departmentRank,
            @Schema(description = "담당 업무") String jobDescription
    ) {
    }

    @Schema(description = "자격증·어학")
    public record Certificate(
            @Schema(description = "취득일자") LocalDate acquiredDate,
            @Schema(description = "시험명") String name,
            @Schema(description = "발급기관/점수") String issuerScore,
            @Schema(description = "비고") String note
    ) {
    }
}
