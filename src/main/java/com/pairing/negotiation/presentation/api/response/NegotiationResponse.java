package com.pairing.negotiation.presentation.api.response;

import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 협상 상세. 협상 화면 상단과 조건 카드에 필요한 값이다. */
@Schema(description = "협상 상세 응답")
public record NegotiationResponse(

        @Schema(description = "협상 ID", example = "300")
        Long negotiationId,

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼")
        String projectTitle,

        @Schema(description = "포지션 ID", example = "10")
        Long positionId,

        @Schema(description = "상대 이름", example = "홍길동")
        String counterpartName,

        @Schema(description = "상태")
        NegotiationStatus status,

        @Schema(description = "진행 라운드 수", example = "3")
        int totalRound,

        @Schema(description = "라운드 상한. 넘으면 최종 승인 단계로 간다.", example = "15")
        int maxRound,

        @Schema(description = "합의 금액(원). 타결 전에는 null", example = "22000000")
        Long agreedAmount,

        @Schema(description = "협상 채팅방 ID. AI Out 이후 사람 채팅으로 쓴다.", example = "500")
        Long chatRoomId,

        @Schema(description = "AI 협상 종료(AI Out) 시각")
        LocalDateTime aiOutAt,

        @Schema(description = "최종 승인 단계 여부. true 면 승인/거부만 가능하다.", example = "false")
        boolean finalApprovalRequired,

        @Schema(description = "협상 조건 목록")
        List<Condition> conditions
) {

    @Schema(description = "협상 조건")
    public record Condition(
            @Schema(description = "조건 ID", example = "401") Long conditionId,
            @Schema(description = "조건 종류") ConditionType type,
            @Schema(description = "클라이언트 값", example = "20000000") String clientValue,
            @Schema(description = "프리랜서 값", example = "25000000") String freelancerValue,
            @Schema(description = "AI 제안값", example = "22000000") String proposedValue,
            @Schema(description = "제안 근거", example = "프리랜서 경력이 요구 수준을 넘어 중간값을 제안합니다.") String reason,
            @Schema(description = "합의값. 합의 전에는 null") String agreedValue,
            @Schema(description = "조건 상태") ConditionStatus status,
            @Schema(description = "이 조건의 라운드 수", example = "2") int roundCount
    ) {
    }
}
