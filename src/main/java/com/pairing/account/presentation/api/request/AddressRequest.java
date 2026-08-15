package com.pairing.account.presentation.api.request;

import com.pairing.account.domain.model.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 주소 입력. 가입·마이페이지 수정이 모두 이 모양을 쓴다.
 *
 * <p><b>주소검색 위젯(다음·카카오 우편번호)이 준 값을 그대로 보낸다.</b> 위젯 결과를 하나로 합쳐서
 * 보내면 안 된다 — 합치면 수정 화면에서 다시 나눌 수 없다. {@code addressDetail} 만 사용자가 직접 쓴다.
 *
 * <p>{@code roadAddress} 에는 위젯의 {@code roadAddress} 를 <b>자르지 말고 그대로</b> 넣는다.
 * 시·도·시·군·구가 포함된 전체 주소다. 앞부분을 잘라 보내려 하면 세종시처럼 시·군·구가 빈 경우에
 * 규칙이 지저분해지고, 위젯에는 잘라낸 값을 주는 필드도 없다.
 *
 * <p>서버는 형식만 본다. 시·군·구 코드표를 들고 있지 않아 "실존하는 지역인가"는 검증하지 않는다.
 */
@Schema(description = "주소")
public record AddressRequest(

        @Schema(description = "시·도. 주소 찾기 결과. 지역 구분용이며 화면 표시는 roadAddress 가 담당한다.",
                example = "서울")
        @NotBlank(message = "시·도는 필수입니다.")
        @Size(max = 20, message = "시·도는 20자 이하여야 합니다.")
        String sido,

        // 세종특별자치시는 시·군·구가 없어 위젯이 빈 값을 준다. 필수로 두면 세종시 사용자가 가입할 수 없다.
        @Schema(description = "시·군·구. 주소 찾기 결과. 세종시처럼 없는 지역은 비워 보낸다.", example = "강남구")
        @Size(max = 40, message = "시·군·구는 40자 이하여야 합니다.")
        String sigungu,

        @Schema(description = "전체 도로명 주소. 위젯의 roadAddress 를 자르지 말고 그대로 보낸다.",
                example = "서울 강남구 테헤란로 123")
        @NotBlank(message = "도로명 주소는 필수입니다.")
        @Size(max = 255, message = "도로명 주소는 255자 이하여야 합니다.")
        String roadAddress,

        @Schema(description = "상세 주소. 사용자가 직접 입력한다.", example = "10층 1002호")
        @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
        String addressDetail,

        @Schema(description = "우편번호. 주소 찾기 결과", example = "06234")
        @Pattern(regexp = "^$|^\\d{5,6}$", message = "우편번호는 숫자 5~6자리여야 합니다.")
        @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
        String zipCode
) {

    public Address toAddress() {
        return Address.of(sido, sigungu, roadAddress, addressDetail, zipCode);
    }
}
