package com.pairing.support.presentation.api.response;

import com.pairing.account.domain.model.Role;
import com.pairing.support.application.result.InquiryResult;
import com.pairing.support.domain.model.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 1:1 문의. */
@Schema(description = "1:1 문의 응답")
public record InquiryResponse(

        @Schema(description = "화면 표시용 문의번호", example = "QNA-20260805-0012")
        String inquiryNo,

        @Schema(description = "문의 ID", example = "1300") Long inquiryId,
        @Schema(description = "작성자 이름. 관리자 화면에서만 채워진다.", example = "홍길동") String writerName,
        @Schema(description = "작성자 회원유형. 관리자 화면에서만 채워진다.") Role writerRole,
        @Schema(description = "작성자 이메일. 관리자 화면에서만 채워진다.", example = "user@pairing.com") String writerEmail,
        @Schema(description = "제목", example = "정산 관련 문의드립니다") String title,
        @Schema(description = "내용") String content,
        @Schema(description = "상태") InquiryStatus status,
        @Schema(description = "답변 내용. 미답변이면 null") String answer,
        @Schema(description = "답변자 표기", example = "페어링 고객지원") String answererName,
        @Schema(description = "답변 시각") LocalDateTime answeredAt,
        @Schema(description = "첨부파일") List<InquiryFileResponse> files,
        @Schema(description = "등록 시각") LocalDateTime createdAt
) {

    public static InquiryResponse from(InquiryResult result) {
        List<InquiryFileResponse> files = result.files().stream()
                .map(f -> new InquiryFileResponse(f.fileId(), f.originalName(), f.objectKey()))
                .toList();
        return new InquiryResponse(result.inquiryNo(), result.inquiryId(), result.writerName(), result.writerRole(),
                result.writerEmail(), result.title(), result.content(), result.status(), result.answer(),
                result.answererName(), result.answeredAt(), files, result.createdAt());
    }
}
