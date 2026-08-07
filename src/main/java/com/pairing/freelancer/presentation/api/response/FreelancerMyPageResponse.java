package com.pairing.freelancer.presentation.api.response;

import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 프리랜서 마이페이지. (요구사항 R17)
 *
 * <p>결제수단은 여러 건이라 여기에 담지 않는다. 마이페이지 결제수단 탭이
 * {@code GET /api/v1/accounts/me/payment-methods} 를 따로 호출한다.
 */
@Schema(description = "프리랜서 마이페이지 응답")
public record FreelancerMyPageResponse(

        @Schema(description = "계정 ID", example = "7") Long accountId,
        @Schema(description = "이름(수정 불가)", example = "홍길동") String name,
        @Schema(description = "이메일(수정 불가)", example = "user@pairing.com") String email,
        @Schema(description = "전화번호", example = "01012345678") String phone,
        @Schema(description = "생년월일(수정 불가)") LocalDate birthDate,
        @Schema(description = "주소", example = "서울 강남구") String address,
        @Schema(description = "프로필 사진 URL") String profileImageUrl,
        @Schema(description = "AI 매칭 사용 여부", example = "true") boolean aiMatchingAgreed,
        @Schema(description = "등급") FreelancerGrade grade,
        @Schema(description = "평균 별점", example = "4.5") Double ratingAverage,
        @Schema(description = "리뷰 건수", example = "12") int reviewCount,
        @Schema(description = "이력서 작성 완료 여부", example = "true") boolean resumeCompleted,
        @Schema(description = "탈퇴 가능 여부. 진행 중 프로젝트나 미납금이 있으면 false", example = "true") boolean withdrawable
) implements CdnMappable {
}
