package com.pairing.matching.presentation.api.response;

import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.RejectReason;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭 요청 1건.
 *
 * <p>클라이언트가 보낸 목록과 프리랜서가 받은 목록이 같은 형태를 쓴다.
 * 보는 쪽에 따라 상대 정보(counterpartName)가 달라진다.
 *
 * <p>{@code mainTask}는 목록/카드 응답(발송/수락/거절 포함)에서는 항상 null이고, 상세 조회
 * ({@code GET /requests/{requestId}})에서만 값이 채워진다(3번 요청, 2026-08-09) — 줄바꿈이 있는
 * 긴 텍스트라 카드에는 안 맞고 상세에서만 보여주기로 함.
 */
@Schema(description = "매칭 요청 응답")
public record MatchingRequestResponse(

        @Schema(description = "매칭 요청 ID", example = "200")
        Long requestId,

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼")
        String projectTitle,

        @Schema(description = "포지션 ID", example = "10")
        Long positionId,

        @Schema(description = "포지션 직무")
        JobRole jobRole,

        @Schema(description = "상대 이름. 클라이언트가 보면 프리랜서명, 프리랜서가 보면 기업명", example = "홍길동")
        String counterpartName,

        @Schema(description = "발주 기업명", example = "주식회사 오이랩") String companyName,
        @Schema(description = "업종·직원수 표기", example = "IT/소프트웨어 · 50-100명") String companyProfile,

        @Schema(description = "요구 스킬") List<SkillCode> skills,
        @Schema(description = "최소 경력(년)", example = "3") Integer minCareerYears,
        @Schema(description = "근무 조건 표기", example = "재택 · 풀타임") String workLabel,
        @Schema(description = "예상 기간 표기", example = "4개월") String periodLabel,
        @Schema(description = "시작 희망일") LocalDate startDesiredDate,

        @Schema(description = "상태")
        MatchingStatus status,

        @Schema(description = "예산(원)", example = "50000000")
        Long budgetAmount,

        @Schema(description = "프로젝트 주요 담당 업무. 목록/카드 응답에서는 null — 상세 조회(GET /requests/{requestId})에서만 채워진다.",
                example = "주문 시스템 API 개발")
        String mainTask,

        @Schema(description = "요청 시각")
        LocalDateTime requestedAt,

        @Schema(description = "응답 기한. 3일 후 자동 만료된다.")
        LocalDateTime expiresAt,

        @Schema(description = "응답 시각")
        LocalDateTime respondedAt,

        @Schema(description = "거절/만료 사유. status가 REJECTED일 때만 값이 있다. "
                + "직접 거절과 응답 기한 만료를 구분하는 용도 — 둘 다 REJECTED라 status만으로는 구분이 안 된다.")
        RejectReason rejectReason,

        // 협상 중 카드에 "라운드 4/15" 로 찍는다. 협상 시작 전에는 null.
        @Schema(description = "현재 협상 라운드", example = "4") Integer currentRound,
        @Schema(description = "최대 협상 라운드", example = "15") Integer maxRound,
        @Schema(description = "확인하지 않은 새 AI 제안 수", example = "1") Integer newProposalCount,

        @Schema(description = "협상 ID. 수락 후 생성된다.", example = "300")
        Long negotiationId
) {
}
