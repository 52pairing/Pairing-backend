package com.pairing.file.presentation.api.response;

import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 업로드 결과.
 *
 * <p>fileUrl 은 응답 시점에 CDN 절대 URL 로 변환된다. DB 에는 object key 만 저장한다.
 */
@Schema(description = "파일 응답")
public record FileResponse(

        @Schema(description = "파일 ID. 다른 API 요청 본문에는 이 값만 넣는다.", example = "1")
        Long fileId,

        @Schema(description = "원본 파일명", example = "portfolio.pdf")
        String originalName,

        @Schema(description = "다운로드 URL")
        String fileUrl,

        @Schema(description = "MIME 타입", example = "application/pdf")
        String mimeType,

        @Schema(description = "크기(byte)", example = "1048576")
        Long sizeBytes
) implements CdnMappable {
}
