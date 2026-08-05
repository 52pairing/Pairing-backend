package com.pairing.template_server.global.common.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "페이지 응답")
public record PageResponse<T>(

        @Schema(description = "현재 페이지 데이터")
        List<T> content,

        @Schema(description = "현재 페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "10")
        int size,

        @Schema(description = "전체 데이터 개수", example = "35")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "4")
        int totalPages,

        @Schema(description = "첫 페이지 여부", example = "true")
        boolean first,

        @Schema(description = "마지막 페이지 여부", example = "false")
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
