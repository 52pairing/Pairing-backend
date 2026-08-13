package com.pairing.contract.presentation.api.response;

import com.pairing.contract.domain.model.ContractTab;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 탭 옆에 붙는 건수 배지.
 *
 * <p>{@code label} 을 함께 내려주는 이유는 탭 이름이 서버 이넘에 있기 때문이다. 화면이 따로
 * 들고 있으면 라벨을 바꿀 때 두 곳을 고쳐야 하고, 한쪽만 고치면 목록과 탭 이름이 어긋난다.
 */
@Schema(description = "계약 탭 건수")
public record ContractTabCountResponse(

        @Schema(description = "탭", example = "AWAITING_ME") ContractTab tab,
        @Schema(description = "탭 라벨", example = "서명 대기") String label,
        @Schema(description = "건수. 0인 탭도 내려간다", example = "3") long count
) {

    public static ContractTabCountResponse of(ContractTab tab, long count) {
        return new ContractTabCountResponse(tab, tab.getLabel(), count);
    }
}
