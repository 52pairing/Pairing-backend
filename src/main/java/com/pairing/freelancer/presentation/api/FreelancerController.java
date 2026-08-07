package com.pairing.freelancer.presentation.api;

import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.domain.model.CampusType;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.freelancer.domain.model.GraduationStatus;
import com.pairing.freelancer.domain.model.ResumeStatus;
import com.pairing.freelancer.presentation.api.request.FreelancerConditionRequest;
import com.pairing.freelancer.presentation.api.request.FreelancerProfileUpdateRequest;
import com.pairing.freelancer.presentation.api.request.MatchingSettingsRequest;
import com.pairing.freelancer.presentation.api.request.ResumeRequest;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.freelancer.presentation.api.response.FreelancerMyPageResponse;
import com.pairing.freelancer.presentation.api.response.FreelancerResumePageResponse;
import com.pairing.freelancer.presentation.api.response.MatchingSettingsResponse;
import com.pairing.freelancer.presentation.api.response.ResumeResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 프리랜서 마이페이지 · 조건 · 이력서. (요구사항 R17, R21)
 *
 * <p>조건(화면 1)과 이력서(화면 2)를 나눠서 저장한다. 화면을 오갈 때 입력값이 남아야 해서
 * 각각 따로 저장할 수 있어야 하기 때문이다.
 *
 * <p>매칭 중에도 수정할 수 있지만, 진행 중인 매칭에는 매칭 시작 시점의 정보가 적용된다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/freelancers")
@RequiredArgsConstructor
@Tag(name = "20. Freelancer", description = "프리랜서 마이페이지/이력서 API")
public class FreelancerController {

    private final FreelancerConditionUseCase freelancerConditionUseCase;

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "마이페이지 조회", description = "계정 정보와 등급·평점 요약을 함께 반환합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<FreelancerMyPageResponse>> findMe(@CurrentAccountId Long accountId) {
        // TODO: 계정 + 프로필 + 결제수단 마스킹 + 리뷰 집계 조회
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_FOUND", "조회에 성공했습니다.", sampleMyPage()));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "마이페이지 수정",
            description = "이름·생년월일·이메일은 수정할 수 없습니다. 비밀번호 변경은 PATCH /api/v1/auth/password 를 사용합니다.")
    public ResponseEntity<ApiResponse<FreelancerMyPageResponse>> updateMe(
            @Valid @RequestBody FreelancerProfileUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 현재 비밀번호 확인 후 수정
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_UPDATED", "수정되었습니다.", sampleMyPage()));
    }

    @GetMapping("/me/condition")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "내 조건 조회", description = "등록하지 않았으면 data 가 null 입니다.")
    public ResponseEntity<ApiResponse<FreelancerConditionResponse>> findMyCondition(
            @CurrentAccountId Long accountId
    ) {
        FreelancerConditionResponse response = freelancerConditionUseCase.findMyCondition(accountId)
                .map(FreelancerConditionResponse::from)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success("CONDITION_FOUND", "조회에 성공했습니다.", response));
    }

    @PutMapping("/me/condition")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "내 조건 등록/수정",
            description = "없으면 생성하고 있으면 덮어씁니다. 저장 후 임베딩이 갱신됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    public ResponseEntity<ApiResponse<FreelancerConditionResponse>> upsertMyCondition(
            @Valid @RequestBody FreelancerConditionRequest request,
            @CurrentAccountId Long accountId
    ) {
        FreelancerConditionResponse response =
                FreelancerConditionResponse.from(freelancerConditionUseCase.upsert(request.toCommand(accountId)));
        return ResponseEntity.ok(ApiResponse.success("CONDITION_SAVED", "저장되었습니다.", response));
    }

    @GetMapping("/me/resume")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "내 이력서 조회 (조건 포함)",
            description = "마이페이지 '내 이력서' 화면이 한 번에 그릴 수 있도록 희망 조건과 이력서를 함께 반환합니다. "
                    + "저장은 조건과 이력서를 따로 호출합니다.")
    public ResponseEntity<ApiResponse<FreelancerResumePageResponse>> findMyResume(
            @CurrentAccountId Long accountId
    ) {
        // TODO: freelancer_condition + resume + 하위 목록을 한 번에 조회
        return ResponseEntity.ok(ApiResponse.success("RESUME_FOUND", "조회에 성공했습니다.",
                new FreelancerResumePageResponse(ResumeStatus.COMPLETED, LocalDateTime.now(),
                        sampleCondition(), sampleResume(),
                        "수정한 이력서는 새로운 추천부터 반영됩니다. "
                                + "이미 진행 중인 매칭과 협상에는 매칭 시작 당시의 정보가 기준으로 적용됩니다.")));
    }

    @PutMapping("/me/resume")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "내 이력서 등록/수정",
            description = "포트폴리오 등록/삭제도 여기서 같이 처리합니다. "
                    + "필수 항목을 모두 채우면 상태가 COMPLETED 가 되고 매칭 대상에 포함됩니다.")
    public ResponseEntity<ApiResponse<ResumeResponse>> upsertMyResume(
            @Valid @RequestBody ResumeRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 하위 목록(학력/경력/자격증/링크/포트폴리오) 전체 교체 방식으로 저장 후 임베딩 재생성 요청
        return ResponseEntity.ok(ApiResponse.success("RESUME_SAVED", "저장되었습니다.", sampleResume()));
    }

    // ==========================================
    // 매칭 설정 (마이페이지 > 매칭 설정)
    // ==========================================

    @GetMapping("/me/matching-settings")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 설정 조회")
    public ResponseEntity<ApiResponse<MatchingSettingsResponse>> findMyMatchingSettings(
            @CurrentAccountId Long accountId
    ) {
        // TODO: 프로필 설정값 + 이력서 완성 여부로 matchable 판정
        return ResponseEntity.ok(ApiResponse.success("MATCHING_SETTINGS_FOUND", "조회에 성공했습니다.",
                new MatchingSettingsResponse(true, false, true, null)));
    }

    @PutMapping("/me/matching-settings")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 설정 변경",
            description = "매칭을 중지해도 이미 진행 중인 매칭과 협상은 그대로 이어집니다.")
    public ResponseEntity<ApiResponse<MatchingSettingsResponse>> updateMyMatchingSettings(
            @Valid @RequestBody MatchingSettingsRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 설정 저장. 중지로 바뀌면 추천 대상에서 제외
        return ResponseEntity.ok(ApiResponse.success("MATCHING_SETTINGS_UPDATED", "변경되었습니다.",
                new MatchingSettingsResponse(request.aiMatchingAgreed(), request.matchingPaused(),
                        !request.matchingPaused(), null)));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private FreelancerMyPageResponse sampleMyPage() {
        return new FreelancerMyPageResponse(7L, "홍길동", "user@pairing.com", "01012345678",
                LocalDate.of(1995, 3, 1), "서울 강남구", "profiles/uuid.png", true,
                FreelancerGrade.SENIOR, 4.5, 12, true, true);
    }

    private FreelancerConditionResponse sampleCondition() {
        return new FreelancerConditionResponse(50L, JobCategory.DEVELOPMENT, JobRole.BACKEND, "프리랜서",
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 5_000_000L, 4_000_000L,
                LocalDate.now().plusWeeks(2), true, 6, PeriodUnit.MONTH, true, 5,
                List.of(new FreelancerConditionResponse.Skill(SkillCode.JAVA, SkillLevel.ADVANCED)));
    }

    private ResumeResponse sampleResume() {
        return new ResumeResponse(60L, ResumeStatus.COMPLETED, "홍길동", LocalDate.of(1995, 3, 1),
                "01012345678", "user@pairing.com", "서울 강남구", "profiles/uuid.png",
                "백엔드 5년차입니다.", "portfolios/uuid.pdf",
                List.of(new ResumeResponse.Education(LocalDate.of(2014, 3, 1), LocalDate.of(2018, 2, 28),
                        "페어링대학교", "컴퓨터공학", GraduationStatus.GRADUATED, CampusType.MAIN)),
                List.of(new ResumeResponse.Career(LocalDate.of(2018, 3, 1), LocalDate.of(2023, 2, 28),
                        "주식회사 예시", "서버개발팀 대리", "결제 시스템 개발")),
                List.of(new ResumeResponse.Certificate(LocalDate.of(2020, 5, 1), "정보처리기사",
                        "한국산업인력공단", null)),
                List.of("https://github.com/pairing"));
    }
}
