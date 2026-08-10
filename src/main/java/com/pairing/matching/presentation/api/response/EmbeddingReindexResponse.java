package com.pairing.matching.presentation.api.response;

import com.pairing.matching.application.result.EmbeddingReindexResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "임베딩 일괄 재색인 결과")
public record EmbeddingReindexResponse(

        @Schema(description = "재색인 성공한 프리랜서 수", example = "48")
        int freelancerSuccessCount,

        @Schema(description = "재색인 실패한 프리랜서 수", example = "0")
        int freelancerFailCount,

        @Schema(description = "재색인 성공한 포지션 수", example = "12")
        int positionSuccessCount,

        @Schema(description = "재색인 실패한 포지션 수", example = "0")
        int positionFailCount
) {

    public static EmbeddingReindexResponse from(EmbeddingReindexResult result) {
        return new EmbeddingReindexResponse(result.freelancerSuccessCount(), result.freelancerFailCount(),
                result.positionSuccessCount(), result.positionFailCount());
    }
}
