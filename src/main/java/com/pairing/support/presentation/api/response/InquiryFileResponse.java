package com.pairing.support.presentation.api.response;

import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

/** 1:1 문의 첨부파일. */
@Schema(description = "문의 첨부파일")
public record InquiryFileResponse(

        @Schema(description = "파일 ID", example = "42") Long fileId,
        @Schema(description = "원본 파일명", example = "오류화면.png") String originalName,
        @Schema(description = "다운로드 URL. 응답 시점에 CDN 절대 URL 로 변환된다") String fileUrl
) implements CdnMappable {
}
