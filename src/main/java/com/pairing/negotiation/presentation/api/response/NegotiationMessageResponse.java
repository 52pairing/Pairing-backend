package com.pairing.negotiation.presentation.api.response;

import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.SenderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 협상 로그 1건. (요구사항 R12)
 *
 * <p>제안·이유·응답이 그대로 남는다. 투명성 확보가 목적이라 삭제하지 않는다.
 */
@Schema(description = "협상 로그 항목")
public record NegotiationMessageResponse(

        @Schema(description = "메시지 ID", example = "900") Long messageId,
        @Schema(description = "라운드 번호", example = "3") int roundNo,
        @Schema(description = "작성 주체") SenderType senderType,
        @Schema(description = "메시지 종류") NegotiationMessageType messageType,
        @Schema(description = "대상 조건. 조건과 무관한 안내는 null") ConditionType conditionType,
        @Schema(description = "본문", example = "월 220만원을 제안합니다.") String content,
        @Schema(description = "근거. 모든 AI 제안에는 근거가 붙는다.",
                example = "클라이언트 예산 상한과 프리랜서 최저 수용가의 중간값입니다.") String reason,
        @Schema(description = "제안값", example = "2200000") String proposedValue,
        @Schema(description = "응답 결과. YES/NO 또는 직접 입력값") String response,
        @Schema(description = "작성 시각") LocalDateTime createdAt
) {
}
