package com.pairing.account.presentation.api.response;

import com.pairing.account.domain.model.Address;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주소 5칸. 수정 화면이 각 칸을 다시 채울 때 쓴다.
 *
 * <p>한 줄로 합친 주소는 응답의 {@code address} 필드로 따로 나간다. <b>화면에 한 줄만 찍는 곳은
 * 그걸 그대로 쓰면 되고</b>, 수정 폼만 이 객체를 쓴다.
 *
 * <p>이 기능 이전에 가입한 계정은 나눠 담긴 값이 없어 {@code null} 이 나간다. 그때도
 * {@code address}(한 줄)는 옛 값 그대로 나가므로 조회 화면은 정상이다. 수정 폼은 주소 찾기를
 * 다시 시키면 된다.
 */
@Schema(description = "주소 상세. 나눠 담긴 값이 없는 옛 계정은 null")
public record AddressResponse(

        @Schema(description = "시·도. 지역 구분용", example = "서울") String sido,
        @Schema(description = "시·군·구. 세종시처럼 없는 지역은 null", example = "강남구") String sigungu,
        @Schema(description = "전체 도로명 주소(시·도·시·군·구 포함)", example = "서울 강남구 테헤란로 123")
        String roadAddress,
        @Schema(description = "상세 주소", example = "10층 1002호") String addressDetail,
        @Schema(description = "우편번호", example = "06234") String zipCode
) {

    public static AddressResponse from(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressResponse(address.sido(), address.sigungu(), address.roadAddress(),
                address.addressDetail(), address.zipCode());
    }
}
