package com.pairing.matching.presentation.api.response.admin;

import com.pairing.global.common.api.response.PageResponse;
import com.pairing.matching.application.result.admin.EmbeddingMissingItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "임베딩 누락 대상 목록")
public record EmbeddingMissingResponse(
        Summary summary,
        PageResponse<Item> items
) {

    public static EmbeddingMissingResponse from(EmbeddingMissingResult result) {
        return new EmbeddingMissingResponse(
                new Summary(result.summary().freelancerMissingCount(), result.summary().positionMissingCount()),
                PageResponse.from(result.page().map(Item::from))
        );
    }

    public record Summary(long freelancerMissingCount, long positionMissingCount) {
    }

    public record Item(
            String targetType,
            Long targetId,
            String displayName,
            String status,
            String reason,
            String model,
            String lastLogStatus,
            LocalDateTime lastLogAt
    ) {
        static Item from(EmbeddingMissingItem item) {
            return new Item(item.targetType(), item.targetId(), item.displayName(), item.status(),
                    item.reason(), item.model(), item.lastLogStatus(), item.lastLogAt());
        }
    }
}
