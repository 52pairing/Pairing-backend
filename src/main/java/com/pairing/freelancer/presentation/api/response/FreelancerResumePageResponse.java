package com.pairing.freelancer.presentation.api.response;

import com.pairing.freelancer.domain.model.ResumeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 마이페이지 "내 이력서" 한 화면. (요구사항 R21)
 *
 * <p>화면은 희망 조건과 이력서를 한 페이지에 붙여 보여준다. 그래서 조회는 이 응답 하나로 끝낸다.
 *
 * <p>저장도 한 번에 할 수 있다 — {@code PUT /me/resume} 요청에 {@code condition} 을 같이 실으면
 * 둘이 한 트랜잭션으로 저장된다. 조건만 고치는 화면은 {@code PUT /me/condition} 을 쓴다.
 *
 * <p>아직 등록하지 않았으면 해당 항목이 null 이다.
 */
@Schema(description = "내 이력서 화면 응답")
public record FreelancerResumePageResponse(

        @Schema(description = "이력서 상태. 필수 항목을 모두 채우면 COMPLETED")
        ResumeStatus status,

        @Schema(description = "마지막 수정 시각")
        LocalDateTime lastModifiedAt,

        @Schema(description = "기본 희망 조건. 미등록이면 null")
        FreelancerConditionResponse condition,

        @Schema(description = "이력서 본문. 미등록이면 null")
        ResumeResponse resume,

        @Schema(description = "안내 문구",
                example = "수정한 이력서는 새로운 추천부터 반영됩니다. 이미 진행 중인 매칭과 협상에는 매칭 시작 당시의 정보가 기준으로 적용됩니다.")
        String notice
) {
}
