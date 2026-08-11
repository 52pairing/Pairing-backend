package com.pairing.freelancer.presentation.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.freelancer.application.usecase.FreelancerCommandUseCase;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.application.usecase.FreelancerQueryUseCase;
import com.pairing.freelancer.application.result.ResumeDraftResult;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.ResumeStatus;
import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.freelancer.presentation.api.request.FreelancerConditionRequest;
import com.pairing.freelancer.presentation.api.request.FreelancerProfileUpdateRequest;
import com.pairing.freelancer.presentation.api.request.MatchingSettingsRequest;
import com.pairing.freelancer.presentation.api.request.ResumeDraftRequest;
import com.pairing.freelancer.presentation.api.request.ResumeRequest;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.freelancer.presentation.api.response.FreelancerMyPageResponse;
import com.pairing.freelancer.presentation.api.response.FreelancerResumePageResponse;
import com.pairing.freelancer.presentation.api.response.MatchingSettingsResponse;
import com.pairing.freelancer.presentation.api.response.ResumeDraftResponse;
import com.pairing.freelancer.presentation.api.response.ResumeResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
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

/**
 * 프리랜서 마이페이지 · 조건 · 이력서. (요구사항 R17, R21)
 *
 * <p>조건(화면 1)과 이력서(화면 2)를 나눠서 저장한다. 화면을 오갈 때 입력값이 남아야 해서
 * 각각 따로 저장할 수 있어야 하기 때문이다.
 *
 * <p>매칭 중에도 수정할 수 있지만, 진행 중인 매칭에는 매칭 시작 시점의 정보가 적용된다.
 */
@RestController
@RequestMapping("/api/v1/freelancers")
@RequiredArgsConstructor
@Tag(name = "20. Freelancer", description = "프리랜서 마이페이지/이력서 API")
public class FreelancerController {

    private static final String REASON_AI_MATCHING_NOT_AGREED = "AI 매칭에 동의해야 추천 대상에 포함됩니다.";
    private static final String REASON_MATCHING_PAUSED = "매칭을 재개해야 추천 대상에 포함됩니다.";
    private static final String REASON_RESUME_INCOMPLETE = "이력서를 완성해야 추천 대상에 포함됩니다.";

    private final FreelancerConditionUseCase freelancerConditionUseCase;
    private final ResumeUseCase resumeUseCase;
    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final FreelancerQueryUseCase freelancerQueryUseCase;
    private final FreelancerCommandUseCase freelancerCommandUseCase;
    private final ObjectMapper objectMapper;

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "마이페이지 조회", description = "계정 정보와 등급·평점 요약을 함께 반환합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    @ApiErrorCodeExample(domain = AccountErrorCode.class, value = {"PROFILE_NOT_FOUND"})
    public ResponseEntity<ApiResponse<FreelancerMyPageResponse>> findMe(@CurrentAccountId Long accountId) {
        FreelancerMyPageResponse response = FreelancerMyPageResponse.from(freelancerQueryUseCase.findMyPage(accountId));
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_FOUND", "조회에 성공했습니다.", response));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "마이페이지 수정",
            description = "이름·생년월일·이메일은 수정할 수 없습니다. 비밀번호 변경은 PATCH /api/v1/auth/password 를 사용합니다. "
                    + "수정 전에 POST /api/v1/auth/email-verifications(purpose=PROFILE_UPDATE)로 이메일 인증을 먼저 마쳐야 합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED", "INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AccountErrorCode.class, value = {"PROFILE_NOT_FOUND"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {"EMAIL_NOT_VERIFIED"})
    public ResponseEntity<ApiResponse<FreelancerMyPageResponse>> updateMe(
            @Valid @RequestBody FreelancerProfileUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        FreelancerMyPageResponse response =
                FreelancerMyPageResponse.from(freelancerCommandUseCase.updateMyPage(request.toCommand(accountId)));
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_UPDATED", "수정되었습니다.", response));
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
        FreelancerConditionResponse conditionResponse = freelancerConditionUseCase.findMyCondition(accountId)
                .map(FreelancerConditionResponse::from)
                .orElse(null);
        var resumeResult = resumeUseCase.findMyResume(accountId);
        ResumeResponse resumeResponse = resumeResult.map(ResumeResponse::from).orElse(null);

        FreelancerResumePageResponse response = new FreelancerResumePageResponse(
                resumeResult.map(r -> r.status()).orElse(ResumeStatus.DRAFT),
                resumeResult.map(r -> r.updatedAt()).orElse(null),
                conditionResponse,
                resumeResponse,
                "수정한 이력서는 새로운 추천부터 반영됩니다. "
                        + "이미 진행 중인 매칭과 협상에는 매칭 시작 당시의 정보가 기준으로 적용됩니다.");
        return ResponseEntity.ok(ApiResponse.success("RESUME_FOUND", "조회에 성공했습니다.", response));
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
        ResumeResponse response = ResumeResponse.from(resumeUseCase.upsert(request.toCommand(accountId)));
        return ResponseEntity.ok(ApiResponse.success("RESUME_SAVED", "저장되었습니다.", response));
    }

    @PutMapping("/me/resume/draft")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "이력서 임시 저장",
            description = "작성 중인 내용을 검증 없이 통째로 보관합니다. 필수 항목을 안 채워도 저장됩니다. "
                    + "계정당 1건이라 저장할 때마다 덮어쓰고, 정식 등록(PUT /me/resume)에 성공하면 지워집니다. "
                    + "여기에 저장한 내용은 매칭에 쓰이지 않습니다.")
    @ApiErrorCodeExample(domain = FreelancerErrorCode.class, value = {"DRAFT_TOO_LARGE"})
    public ResponseEntity<ApiResponse<ResumeDraftResponse>> saveMyResumeDraft(
            @Valid @RequestBody ResumeDraftRequest request,
            @CurrentAccountId Long accountId
    ) {
        ResumeDraftResult result = resumeUseCase.saveDraft(accountId, request.payload().toString());
        ResumeDraftResponse response = new ResumeDraftResponse(request.payload(), result.savedAt());
        return ResponseEntity.ok(ApiResponse.success("RESUME_DRAFT_SAVED", "임시 저장되었습니다.", response));
    }

    @GetMapping("/me/resume/draft")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "이력서 임시 저장 불러오기",
            description = "임시 저장한 내용이 없으면 data 가 null 입니다. 저장할 때 보낸 JSON 을 그대로 돌려줍니다.")
    public ResponseEntity<ApiResponse<ResumeDraftResponse>> findMyResumeDraft(@CurrentAccountId Long accountId) {
        ResumeDraftResponse response = resumeUseCase.findMyDraft(accountId)
                .map(draft -> new ResumeDraftResponse(readPayload(draft.payload()), draft.savedAt()))
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success("RESUME_DRAFT_FOUND", "조회에 성공했습니다.", response));
    }

    /** 저장할 때 JsonNode 를 직렬화한 값이라 되읽기에 실패할 일은 없다. 그래도 500으로 새지 않게 막는다. */
    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
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
        FreelancerProfile profile = accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
        MatchingSettingsResponse response =
                toMatchingSettingsResponse(accountId, profile.isAiMatchingAgreed(), profile.isMatchingPaused());
        return ResponseEntity.ok(ApiResponse.success("MATCHING_SETTINGS_FOUND", "조회에 성공했습니다.", response));
    }

    @PutMapping("/me/matching-settings")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 설정 변경",
            description = "매칭을 중지해도 이미 진행 중인 매칭과 협상은 그대로 이어집니다.")
    public ResponseEntity<ApiResponse<MatchingSettingsResponse>> updateMyMatchingSettings(
            @Valid @RequestBody MatchingSettingsRequest request,
            @CurrentAccountId Long accountId
    ) {
        accountCommandUseCase.updateFreelancerMatchingSettings(accountId, request.aiMatchingAgreed(),
                request.matchingPaused());
        MatchingSettingsResponse response =
                toMatchingSettingsResponse(accountId, request.aiMatchingAgreed(), request.matchingPaused());
        return ResponseEntity.ok(ApiResponse.success("MATCHING_SETTINGS_UPDATED", "변경되었습니다.", response));
    }

    /**
     * 추천 대상 포함 여부(matchable) 판정. AI 매칭 동의 -&gt; 매칭 일시중지 -&gt; 이력서 완성 순으로
     * 확인해서 가장 먼저 걸리는 사유 하나만 돌려준다.
     */
    private MatchingSettingsResponse toMatchingSettingsResponse(Long accountId, boolean aiMatchingAgreed,
                                                                 boolean matchingPaused) {
        String unmatchableReason;
        if (!aiMatchingAgreed) {
            unmatchableReason = REASON_AI_MATCHING_NOT_AGREED;
        } else if (matchingPaused) {
            unmatchableReason = REASON_MATCHING_PAUSED;
        } else if (resumeUseCase.findMyResume(accountId).isEmpty()) {
            unmatchableReason = REASON_RESUME_INCOMPLETE;
        } else {
            unmatchableReason = null;
        }
        return new MatchingSettingsResponse(aiMatchingAgreed, matchingPaused, unmatchableReason == null,
                unmatchableReason);
    }

}
