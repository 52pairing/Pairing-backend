package com.pairing.matching.presentation.api.response;

import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.RejectReason;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
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
 *
 * <p><b>프로젝트 본문 7개도 같은 규칙이다</b>({@code currentSituation}, {@code detailScope},
 * {@code extraNote}, {@code workLocation}, {@code startNegotiable}, {@code periodValue}/
 * {@code periodUnit}, {@code totalHeadcount} — 프론트 요청, 2026-08-15). 프리랜서가 수락 전에
 * 프로젝트를 다 보고 판단해야 해서 넣었는데, 목록에도 채우면 20건 페이지마다 스냅샷 없는 옛 요청이
 * 프로젝트를 한 번씩 더 읽는다. 그래서 상세에서만 채운다.
 *
 * <p><b>세 개는 비어 있을 수 있다</b> — {@code detailScope}/{@code extraNote}/{@code workLocation}은
 * 프로젝트 등록 시 선택 입력이다. 화면에서 빈 값 처리가 필요하다.
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

        @Schema(description = "현재 프로젝트 진행 상황. 상세 조회에서만 채워진다.",
                example = "기존 사내 시스템을 리뉴얼하는 단계입니다.")
        String currentSituation,

        @Schema(description = "세부 업무범위. 상세 조회에서만 채워진다. **등록 시 선택 입력이라 비어 있을 수 있다.**",
                example = "주문/결제 도메인 신규 기능 개발")
        String detailScope,

        @Schema(description = "기타 전달사항 또는 우대사항. 상세 조회에서만 채워진다. **선택 입력이라 비어 있을 수 있다.**",
                example = "MSA 경험자 우대")
        String extraNote,

        @Schema(description = "근무 장소. 상세 조회에서만 채워진다. **선택 입력이라 비어 있을 수 있다**(원격이면 보통 없다).",
                example = "서울 강남구")
        String workLocation,

        @Schema(description = "시작일 협의 가능 여부. 상세 조회에서만 채워진다.", example = "true")
        Boolean startNegotiable,

        @Schema(description = "예상 기간 값. 상세 조회에서만 채워진다. periodLabel과 같은 값을 숫자로 준다.",
                example = "6")
        Integer periodValue,

        @Schema(description = "예상 기간 단위. 상세 조회에서만 채워진다.", example = "MONTH")
        PeriodUnit periodUnit,

        @Schema(description = "프로젝트 전체 모집 인원(모든 포지션 합계). 상세 조회에서만 채워진다.",
                example = "3")
        Integer totalHeadcount,

        @Schema(description = "요청 시각")
        LocalDateTime requestedAt,

        @Schema(description = "응답 기한. 3일 후 자동 만료된다.")
        LocalDateTime expiresAt,

        @Schema(description = "응답 시각")
        LocalDateTime respondedAt,

        @Schema(description = "거절/만료/협상결렬 사유. status가 REJECTED 또는 NEGOTIATION_FAILED일 때만 값이 있다. "
                + "직접 거절(DIRECT_REJECT)과 응답 기한 만료(EXPIRED)는 둘 다 status=REJECTED라 이 필드 없이는 "
                + "구분이 안 되고, 협상 결렬(NEGOTIATION_FAILED)은 status 자체가 NEGOTIATION_FAILED다.")
        RejectReason rejectReason,

        // 협상 중 카드에 "라운드 4/15" 로 찍는다. 협상 시작 전에는 null.
        @Schema(description = "현재 협상 라운드", example = "4") Integer currentRound,
        @Schema(description = "최대 협상 라운드", example = "15") Integer maxRound,
        @Schema(description = "확인하지 않은 새 AI 제안 수", example = "1") Integer newProposalCount,

        @Schema(description = "협상 ID. 수락 후 생성된다.", example = "300")
        Long negotiationId
) {
}
