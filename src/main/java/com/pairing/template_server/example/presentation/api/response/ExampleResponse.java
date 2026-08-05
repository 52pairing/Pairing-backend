package com.pairing.template_server.example.presentation.api.response;

import com.pairing.template_server.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CdnMappable을 구현하면 이름이 {@code ~Url}로 끝나는 필드가 직렬화 시점에
 * object key → CDN 절대 URL로 자동 변환된다. (필드별 애노테이션 불필요)
 */
@Schema(description = "예시 데이터 응답")
public record ExampleResponse(
        @Schema(description = "예시의 고유 식별자(ID)", example = "1")
        Long exampleId,

        @Schema(description = "예시 이미지 URL (DB에는 object key로 저장되고 응답에서 절대 URL로 변환됨)",
                example = "https://my-bucket.s3.ap-northeast-2.amazonaws.com/examples/uuid.png")
        String imageUrl
) implements CdnMappable {

    public static ExampleResponse of(Long exampleId) {
        return new ExampleResponse(exampleId, null);
    }
}
