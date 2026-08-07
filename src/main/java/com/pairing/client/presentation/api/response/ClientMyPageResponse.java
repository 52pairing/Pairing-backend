package com.pairing.client.presentation.api.response;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 클라이언트 마이페이지. (요구사항 R31)
 *
 * <p>결제수단은 여러 건이라 여기에 담지 않는다. 마이페이지 결제수단 탭이
 * {@code GET /api/v1/accounts/me/payment-methods} 를 따로 호출한다.
 */
@Schema(description = "클라이언트 마이페이지 응답")
public record ClientMyPageResponse(

        @Schema(description = "계정 ID", example = "3") Long accountId,
        @Schema(description = "기업명", example = "주식회사 페어링") String companyName,
        @Schema(description = "사업자등록번호(수정 불가)", example = "1234567890") String businessNo,
        @Schema(description = "사업 분야(수정 불가)") BusinessField businessField,
        @Schema(description = "직원수 구간") EmployeeCount employeeCount,
        @Schema(description = "업무 이메일(수정 불가)", example = "owner@pairing.com") String email,
        @Schema(description = "대표자명(수정 불가)", example = "홍길동") String name,
        @Schema(description = "휴대폰번호", example = "01012345678") String phone,
        @Schema(description = "주소") String address,
        @Schema(description = "기업 로고 URL") String logoImageUrl,
        @Schema(description = "등급") ClientGrade grade,
        @Schema(description = "평균 별점", example = "4.2") Double ratingAverage,
        @Schema(description = "리뷰 건수", example = "8") int reviewCount,
        @Schema(description = "탈퇴 가능 여부", example = "true") boolean withdrawable
) implements CdnMappable {
}
