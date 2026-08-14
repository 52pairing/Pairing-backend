package com.pairing.settlement.presentation.api.response;

import com.pairing.settlement.application.result.MySettlementSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마이페이지 결제 내역 요약. 결제 완료된 수수료만 센다.
 *
 * <p>관리자 집계({@code SettlementSummaryResponse})와 이름이 비슷하지만 다른 값이다.
 * 그쪽은 플랫폼 전체 수익이고 이쪽은 <b>내가 낸 돈</b>이다.
 *
 * <p>클라이언트와 프리랜서가 같은 응답을 받아 화면에 필요한 칸만 고른다.
 * 탭을 바꿔도 값이 안 바뀌므로 화면 진입 시 한 번만 부르면 된다.
 */
@Schema(description = "내 결제 내역 요약")
public record MySettlementSummaryResponse(

        @Schema(description = "총 납부 수수료(원). 착수금 + 성공보수", example = "11100000")
        long totalAmount,

        @Schema(description = "착수금 수수료 합계(원)", example = "3750000")
        long depositAmount,

        @Schema(description = "성공보수 수수료 합계(원)", example = "7350000")
        long successFeeAmount,

        @Schema(description = "착수금을 낸 프로젝트 수", example = "3")
        long depositProjectCount,

        @Schema(description = "성공보수를 낸 프로젝트 수. 프리랜서 화면의 '완료 프로젝트 수'", example = "1")
        long successFeeProjectCount
) {

    public static MySettlementSummaryResponse from(MySettlementSummary summary) {
        return new MySettlementSummaryResponse(
                summary.totalAmount(),
                summary.depositAmount(),
                summary.successFeeAmount(),
                summary.depositProjectCount(),
                summary.successFeeProjectCount());
    }
}
