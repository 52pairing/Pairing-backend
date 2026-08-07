package com.pairing.support.presentation.api.request;

import com.pairing.support.domain.model.InquiryCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 1:1 문의 등록. (요구사항 R45)
 *
 * <p>챗봇 이용 여부와 무관하게 언제든 접수할 수 있다.
 */
@Schema(description = "1:1 문의 등록 요청")
public record InquiryCreateRequest(

        // 작성 화면에는 선택 UI 가 없다. 비워 보내면 서버가 내용으로 분류한다.
        @Schema(description = "문의 유형. 생략 가능", example = "PAYMENT")
        InquiryCategory category,

        @Schema(description = "제목", example = "정산 관련 문의드립니다")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "내용", example = "성공보수 수수료 결제일이 언제인지 확인 부탁드립니다.")
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 2000, message = "내용은 2000자 이하여야 합니다.")
        String content,

        // 파일 API 로 먼저 업로드하고 fileId 만 보낸다.
        @Schema(description = "첨부파일 ID 목록. 선택", example = "[42]")
        List<Long> fileIds
) {
}
