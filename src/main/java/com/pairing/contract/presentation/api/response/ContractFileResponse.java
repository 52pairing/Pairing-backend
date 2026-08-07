package com.pairing.contract.presentation.api.response;

import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

/** 계약서 PDF 다운로드 정보. */
@Schema(description = "계약서 파일 응답")
public record ContractFileResponse(

        @Schema(description = "파일 ID", example = "9") Long fileId,
        @Schema(description = "파일명", example = "PR-2026-000123.pdf") String originalName,
        @Schema(description = "다운로드 URL") String fileUrl
) implements CdnMappable {
}
