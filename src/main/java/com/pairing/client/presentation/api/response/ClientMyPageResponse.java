package com.pairing.client.presentation.api.response;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.client.application.result.ClientMyPageResult;
import com.pairing.account.presentation.api.response.AddressResponse;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 클라이언트 마이페이지(기업정보). (요구사항 R31)
 *
 * <p>결제수단은 마이페이지 결제수단 탭이 {@code GET /api/v1/accounts/me/payment-methods} 를 따로 호출한다.
 */
@Schema(description = "클라이언트 마이페이지 응답")
public record ClientMyPageResponse(

        @Schema(description = "계정 ID", example = "3") Long accountId,
        @Schema(description = "기업 로고 URL. 없으면 null(화면은 이니셜 원으로 대체)") String logoUrl,
        @Schema(description = "기업명", example = "주식회사 페어링") String companyName,
        @Schema(description = "사업자등록번호(수정 불가)", example = "1234567890") String businessNo,
        @Schema(description = "사업 분야(수정 불가)") BusinessField businessField,
        @Schema(description = "직원수 구간") EmployeeCount employeeCount,
        @Schema(description = "업무 이메일(수정 불가)", example = "owner@pairing.com") String email,
        @Schema(description = "담당자명(대표자명, 수정 불가)", example = "홍길동") String name,
        @Schema(description = "전화번호", example = "01012345678") String phone,
        @Schema(description = "주소(한 줄). 화면에 그대로 찍는다.", example = "서울특별시 강남구 테헤란로 123 10층")
        String address,
        @Schema(description = "주소 상세. 수정 폼이 쓴다. 나눠 담기 전 가입한 계정은 null")
        AddressResponse addressParts,
        @Schema(description = "등급") ClientGrade grade,
        @Schema(description = "평균 별점", example = "4.2") Double ratingAverage,
        @Schema(description = "리뷰 건수", example = "8") int reviewCount,
        @Schema(description = "탈퇴 가능 여부", example = "true") boolean withdrawable
) implements CdnMappable {

    public static ClientMyPageResponse from(ClientMyPageResult result) {
        return new ClientMyPageResponse(
                result.accountId(),
                result.logoUrl(),
                result.companyName(),
                result.businessNo(),
                result.businessField(),
                result.employeeCount(),
                result.email(),
                result.name(),
                result.phone(),
                result.address(),
                AddressResponse.from(result.addressParts()),
                result.grade(),
                result.ratingAverage(),
                result.reviewCount(),
                result.withdrawable()
        );
    }
}
