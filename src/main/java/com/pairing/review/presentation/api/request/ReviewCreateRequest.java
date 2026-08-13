package com.pairing.review.presentation.api.request;

import com.pairing.review.application.command.CreateReviewCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상호 평가 + 사이트 후기. (요구사항 R22)
 *
 * <p>화면 하나에서 같이 작성하므로 요청도 하나로 받는다.
 *
 * <p><b>별점은 둘 다 필수, 텍스트 리뷰는 둘 다 선택이다.</b> 상대 평가든 사이트 후기든 마찬가지다.
 * 별점을 선택으로 두면 평균 평점과 등급 산정에서 표본이 들쭉날쭉해지고, 글쓰기를 강제하면
 * 리뷰 작성률 자체가 떨어진다.
 *
 * <p>작성 후 수정·삭제할 수 없다. 프론트에서 확인 문구를 먼저 보여줘야 한다.
 *
 * <p>프로젝트와 상대방은 {@code contractId} 로 서버가 계약에서 유도한다. 요청에서 받지 않는다 —
 * 프론트가 보낸 값을 믿으면 남의 계약에 리뷰를 남기거나 엉뚱한 상대에게 평점이 쌓인다.
 */
@Schema(description = "리뷰 작성 요청")
public record ReviewCreateRequest(

        @Schema(description = "계약 ID. 어느 거래에 대한 평가인지", example = "600")
        @NotNull(message = "계약 ID는 필수입니다.")
        Long contractId,

        @Schema(description = "상대에 대한 평가. 별점은 필수, 내용은 선택")
        @NotNull(message = "상대 평가 별점은 필수입니다.")
        @Valid
        Rating counterpart,

        @Schema(description = "사이트 이용 후기. 별점은 필수, 내용은 선택")
        @NotNull(message = "사이트 후기 별점은 필수입니다.")
        @Valid
        Rating site
) {

    public CreateReviewCommand toCommand(Long reviewerAccountId) {
        return new CreateReviewCommand(
                contractId, reviewerAccountId,
                counterpart.score(), counterpart.content(),
                site.score(), site.content()
        );
    }

    @Schema(description = "별점과 리뷰")
    public record Rating(

            @Schema(description = "별점. 1~5, 1점 단위", example = "5")
            @NotNull(message = "별점은 필수입니다.")
            @Min(value = 1, message = "별점은 1점 이상입니다.")
            @Max(value = 5, message = "별점은 5점 이하입니다.")
            Integer score,

            @Schema(description = "리뷰 내용(선택). 500자 이하", example = "일정 준수가 좋았습니다.")
            @Size(max = 500, message = "리뷰는 500자 이하여야 합니다.")
            String content
    ) {
    }
}
