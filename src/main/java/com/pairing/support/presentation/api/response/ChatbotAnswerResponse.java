package com.pairing.support.presentation.api.response;

import com.pairing.support.application.result.ChatbotAnswerResult;
import com.pairing.support.domain.model.ChatbotIntent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 챗봇 응답. */
@Schema(description = "챗봇 응답")
public record ChatbotAnswerResponse(

        @Schema(description = "세션 ID. 다음 질문에 그대로 넣는다.", example = "1200") Long sessionId,
        @Schema(description = "질문", example = "착수금 수수료는 언제 결제하나요?") String question,
        @Schema(description = "답변") String answer,

        @Schema(description = "답변 아래에 띄울 이동 버튼. 없으면 빈 배열이다. "
                + "AI 가 고른 화면 코드를 서버가 경로로 바꿔 내려주므로, 답변 본문을 파싱할 필요가 없다. "
                + "지난 대화 조회에서는 항상 빈 배열이다.")
        List<Action> actions,

        @Schema(description = "오늘 남은 질의 횟수", example = "9") int remainingQuota,
        @Schema(description = "응답 시각") LocalDateTime createdAt
) {

    public static ChatbotAnswerResponse from(ChatbotAnswerResult result) {
        return new ChatbotAnswerResponse(result.sessionId(), result.question(), result.answer(),
                toActions(result.intent()), result.remainingQuota(), result.createdAt());
    }

    /** 버튼은 최대 1개다. 여러 개를 띄우면 무엇을 눌러야 할지 되레 헷갈린다. */
    private static List<Action> toActions(ChatbotIntent intent) {
        if (intent == null || !intent.hasAction()) {
            return List.of();
        }
        return List.of(new Action(intent.name(), intent.getLabel(), intent.getUrl()));
    }

    @Schema(name = "ChatbotActionResponse", description = "답변에 이어지는 이동 버튼")
    public record Action(

            @Schema(description = "화면 코드", example = "RESUME_EDIT") String code,
            @Schema(description = "버튼 문구", example = "이력서 작성하러 가기") String label,
            @Schema(description = "이동 경로(프론트 라우트)", example = "/mypage/resume") String url
    ) {
    }
}
