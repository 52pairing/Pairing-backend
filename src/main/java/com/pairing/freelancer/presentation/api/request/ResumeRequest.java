package com.pairing.freelancer.presentation.api.request;

import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.domain.model.CampusType;
import com.pairing.freelancer.domain.model.GraduationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 이력서 등록/수정. (요구사항 R21 화면 2)
 *
 * <p>성명과 생년월일은 계정 정보에서 가져오며 수정할 수 없어 요청에 없다.
 * 사진과 포트폴리오는 먼저 업로드해 fileId 로 넣는다. (포트폴리오는 PDF only)
 */
@Schema(description = "이력서 등록/수정 요청")
public record ResumeRequest(

        @Schema(description = "프로필 사진 fileId", example = "3")
        @NotNull(message = "프로필 사진은 필수입니다.")
        Long profileFileId,

        @Schema(description = "연락처. 비우면 계정 전화번호를 쓴다.", example = "010-1234-5678")
        @Pattern(regexp = "^$|^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String contactPhone,

        @Schema(description = "연락 이메일. 비우면 계정 이메일을 쓴다.", example = "user@pairing.com")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String contactEmail,

        @Schema(description = "주소", example = "서울 강남구")
        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255)
        String address,

        @Schema(description = "간단 자기소개")
        @NotBlank(message = "자기소개는 필수입니다.")
        @Size(max = 2000, message = "자기소개는 2000자 이하여야 합니다.")
        String selfIntroduction,

        @Schema(description = "포트폴리오 fileId. PDF 만 허용", example = "4")
        @NotNull(message = "포트폴리오는 필수입니다.")
        Long portfolioFileId,

        @Schema(description = "학력사항. 1건 이상")
        @NotEmpty(message = "학력사항은 1건 이상입니다.")
        @Valid
        List<Education> educations,

        @Schema(description = "경력사항. 1건 이상")
        @NotEmpty(message = "경력사항은 1건 이상입니다.")
        @Valid
        List<Career> careers,

        @Schema(description = "자격증 및 어학(선택)")
        @Valid
        List<Certificate> certificates,

        @Schema(description = "링크(깃허브·포트폴리오·노션 등, 선택)")
        @Valid
        List<Link> links,

        @Schema(description = "이력서 등록 필수 동의 4종. 최초 등록 시에만 받고, 이후 수정에는 영향을 주지 않는다.")
        @NotNull(message = "약관 동의는 필수입니다.")
        @Valid
        Agreements agreements
) {

    public UpsertResumeCommand toCommand(Long accountId) {
        return new UpsertResumeCommand(
                accountId, profileFileId, contactPhone, contactEmail, address, selfIntroduction, portfolioFileId,
                educations.stream()
                        .map(e -> new UpsertResumeCommand.Education(e.startDate(), e.endDate(), e.schoolName(),
                                e.major(), e.graduationStatus(), e.campusType()))
                        .toList(),
                careers.stream()
                        .map(c -> new UpsertResumeCommand.Career(c.startDate(), c.endDate(), c.companyName(),
                                c.departmentRank(), c.jobDescription()))
                        .toList(),
                certificates == null
                        ? List.of()
                        : certificates.stream()
                                .map(c -> new UpsertResumeCommand.Certificate(c.acquiredDate(), c.name(),
                                        c.issuerScore(), c.note()))
                                .toList(),
                links == null ? List.of() : links.stream().map(Link::url).toList(),
                new UpsertResumeCommand.Agreements(agreements.profileCollectionAgreed(),
                        agreements.profileProvisionAgreed(), agreements.aiAnalysisAgreed(),
                        agreements.careerPortfolioUsageAgreed())
        );
    }

    @Schema(name = "ResumeEducationRequest", description = "학력")
    public record Education(
            @Schema(description = "입학일") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Schema(description = "졸업일. 재학중이면 비운다.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Schema(description = "학교명") @NotBlank @Size(max = 100) String schoolName,
            @Schema(description = "전공") @Size(max = 100) String major,
            @Schema(description = "졸업 구분") @NotNull GraduationStatus graduationStatus,
            @Schema(description = "본교/분교") CampusType campusType
    ) {
    }

    @Schema(name = "ResumeCareerRequest", description = "경력")
    public record Career(
            @Schema(description = "입사일") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Schema(description = "퇴사일. 재직중이면 비운다.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Schema(description = "회사/기관명") @NotBlank @Size(max = 100) String companyName,
            @Schema(description = "부서 및 직급") @Size(max = 100) String departmentRank,
            @Schema(description = "담당 업무") @Size(max = 2000) String jobDescription
    ) {
    }

    @Schema(name = "ResumeCertificateRequest", description = "자격증·어학")
    public record Certificate(
            @Schema(description = "취득일자") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquiredDate,
            @Schema(description = "자격/어학 시험명") @NotBlank @Size(max = 100) String name,
            @Schema(description = "발급기관/점수") @Size(max = 100) String issuerScore,
            @Schema(description = "비고") @Size(max = 255) String note
    ) {
    }

    @Schema(description = "링크")
    public record Link(
            @Schema(description = "URL", example = "https://github.com/pairing")
            @NotBlank @Size(max = 500) String url
    ) {
    }

    @Schema(name = "ResumeAgreementsRequest", description = "이력서 등록 필수 동의 4종. 넷 다 true 여야 한다.")
    public record Agreements(
            @Schema(description = "프로필 정보 수집 동의")
            @AssertTrue(message = "프로필 정보 수집에 동의해야 합니다.")
            boolean profileCollectionAgreed,

            @Schema(description = "클라이언트 제공 동의")
            @AssertTrue(message = "클라이언트 제공에 동의해야 합니다.")
            boolean profileProvisionAgreed,

            @Schema(description = "AI 매칭 분석 동의")
            @AssertTrue(message = "AI 매칭 분석에 동의해야 합니다.")
            boolean aiAnalysisAgreed,

            @Schema(description = "경력/포트폴리오 활용 동의")
            @AssertTrue(message = "경력/포트폴리오 활용에 동의해야 합니다.")
            boolean careerPortfolioUsageAgreed
    ) {
    }
}
