package com.pairing.account.presentation.api.response;

import com.pairing.account.application.result.WithdrawalEligibilityResult;
import com.pairing.account.domain.model.WithdrawalBlocker;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 탈퇴 가능 여부. 회원 탈퇴 화면 진입 시 호출한다.
 *
 * <p>{@code withdrawable} 이 false 면 탈퇴 버튼을 열지 않고 {@code blockers} 를 안내로 그린다.
 */
@Schema(description = "탈퇴 가능 여부")
public record WithdrawalEligibilityResponse(

        @Schema(description = "탈퇴 가능 여부. false 면 blockers 를 안내로 보여준다.", example = "false")
        boolean withdrawable,

        @Schema(description = "탈퇴를 막는 사유. 가능하면 빈 배열")
        List<Blocker> blockers
) {

    @Schema(description = "탈퇴를 막는 사유 한 건")
    public record Blocker(

            @Schema(description = "사유 코드", example = "NEGOTIATION")
            WithdrawalBlocker type,

            @Schema(description = "화면에 그대로 쓰는 문구. 프론트에서 조립하지 않는다.",
                    example = "진행 중인 협상")
            String label,

            @Schema(description = "건수", example = "1")
            long count,

            @Schema(description = "정리하러 갈 화면 경로", example = "/client/projects")
            String linkUrl
    ) {
    }

    public static WithdrawalEligibilityResponse from(WithdrawalEligibilityResult result) {
        return new WithdrawalEligibilityResponse(
                result.withdrawable(),
                result.blockers().stream()
                        .map(blocked -> new Blocker(
                                blocked.blocker(),
                                blocked.blocker().getLabel(),
                                blocked.count(),
                                blocked.linkUrl()))
                        .toList());
    }
}
