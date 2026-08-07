package com.pairing.account.presentation.api.response;

import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [관리자] 회원 상세. (요구사항 R37)
 *
 * <p>목록보다 훨씬 두껍다. 기본 정보 + 활동 현황 6지표 + 프로젝트 이력을 한 화면에 보여준다.
 * 클라이언트만 채워지는 항목(기업명·사업자번호·사업분야·직원수)은 프리랜서면 null 이다.
 */
@Schema(description = "관리자 회원 상세 응답")
public record AdminAccountDetailResponse(

        @Schema(description = "계정 ID", example = "7") Long accountId,
        @Schema(description = "화면 표시용 회원번호", example = "MEM-001") String memberNo,
        @Schema(description = "유형") Role role,
        @Schema(description = "이름 또는 담당자명", example = "김담당") String name,
        @Schema(description = "이메일", example = "hr@samsung.com") String email,
        @Schema(description = "전화번호", example = "02-1234-5678") String phone,
        @Schema(description = "가입 방식") SignupType signupType,
        @Schema(description = "가입일") LocalDate joinedAt,
        @Schema(description = "최근 로그인") LocalDateTime lastLoginAt,
        @Schema(description = "상태") AccountStatus status,
        @Schema(description = "정지 여부", example = "false") boolean suspended,
        @Schema(description = "정지 사유") String suspendReason,

        @Schema(description = "기업명. 클라이언트만", example = "삼성전자") String companyName,
        @Schema(description = "사업자번호. 클라이언트만", example = "123-45-67890") String businessNo,
        @Schema(description = "사업 분야. 클라이언트만", example = "IT·콘텐츠·AI") String businessField,
        @Schema(description = "직원수. 클라이언트만", example = "500명 이상") String employeeCount,

        @Schema(description = "활동 현황") Activity activity,
        @Schema(description = "프로젝트 이력") List<ProjectHistory> projectHistories
) {

    /** 화면 상단 지표 카드 6칸. */
    @Schema(description = "활동 현황")
    public record Activity(
            @Schema(description = "진행중 프로젝트", example = "2") long inProgressCount,
            @Schema(description = "완료 프로젝트", example = "2") long completedCount,
            @Schema(description = "취소", example = "0") long canceledCount,
            @Schema(description = "누적 거래금액(원)", example = "10000000") long totalTransactionAmount,
            @Schema(description = "리뷰 수", example = "3") long reviewCount,
            @Schema(description = "평균 별점", example = "4.8") Double ratingAverage
    ) {
    }

    @Schema(description = "프로젝트 이력")
    public record ProjectHistory(
            @Schema(description = "프로젝트 ID", example = "1") Long projectId,
            @Schema(description = "프로젝트 번호", example = "PRJ-001") String projectNo,
            @Schema(description = "프로젝트명", example = "쇼핑몰 관리자 페이지 리뉴얼") String title,
            @Schema(description = "상태 라벨", example = "진행중") String statusLabel,
            @Schema(description = "예산 표기", example = "월 5,000,000원") String budgetLabel,
            @Schema(description = "등록일") LocalDate createdAt
    ) {
    }
}
