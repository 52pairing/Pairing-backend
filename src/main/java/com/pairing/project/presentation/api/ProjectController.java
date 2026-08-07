package com.pairing.project.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionStatus;
import com.pairing.project.domain.model.ProjectPaymentStatus;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.presentation.api.request.ProjectCreateRequest;
import com.pairing.project.domain.model.ProjectTab;
import com.pairing.project.presentation.api.request.ProjectPreReviewRequest;
import com.pairing.project.presentation.api.request.ProjectUpdateRequest;
import com.pairing.project.presentation.api.response.ProjectPreReviewResponse;
import com.pairing.project.presentation.api.response.ProjectResponse;
import com.pairing.project.presentation.api.response.ProjectSummaryResponse;
import com.pairing.project.presentation.api.response.ProjectTabCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 프로젝트 등록·조회·수정·취소. (요구사항 R28, R29, R30, R32)
 *
 * <p>현재는 API 계약을 고정하기 위한 스켈레톤이라 고정 응답을 돌려준다.
 * 담당 개발자는 UseCase 를 주입해 TODO 부분을 채운다.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "10. Project", description = "프로젝트 등록/조회/수정 API")
public class ProjectController {

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 등록",
            description = "등록 직후 상태는 REGISTERED 이며, 착수금 수수료를 결제해야 모집이 시작됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody ProjectCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 프로젝트 + 포지션 + 스킬 + 첨부 저장, 착수금 결제 대기 상태로 생성
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("PROJECT_CREATED", "프로젝트가 등록되었습니다.", sampleDetail()));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "프로젝트 상세 조회")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> findOne(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 조회 + 열람 권한 확인(클라이언트 본인 또는 참여 프리랜서)
        return ResponseEntity.ok(ApiResponse.success("PROJECT_FOUND", "조회에 성공했습니다.", sampleDetail()));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 수정",
            description = "등록 시 입력값을 모두 수정할 수 있습니다. "
                    + "모집 인원과 포지션 추가·삭제는 착수금 결제 전(등록 완료)까지만 가능합니다. "
                    + "진행 중인 매칭에는 매칭 시작 시점 정보가 적용됩니다.")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 소유자 확인 후 수정. REGISTERED 상태면 검수 로직 재실행(정책 P02).
        //       변경점이 없으면 프론트가 모달로 안내한다.
        return ResponseEntity.ok(ApiResponse.success("PROJECT_UPDATED", "수정되었습니다.", sampleDetail()));
    }

    @PostMapping("/{projectId}/cancellation")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 취소",
            description = "모집·협상·계약을 더 이상 진행하지 않습니다. 계약 체결 이후에는 위약금이 발생할 수 있습니다.")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 상태 CANCELED 전환, 진행 중 매칭/협상 정리
        return ResponseEntity.ok(ApiResponse.success("PROJECT_CANCELED", "프로젝트를 취소했습니다."));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "내 프로젝트 목록",
            description = "화면의 탭 하나가 여러 상태를 묶습니다. 예: MATCHING = 모집 중 + 협상 중 + 계약 대기. "
                    + "tab 을 비우면 전체입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ProjectSummaryResponse>>> findMine(
            @RequestParam(required = false) ProjectTab tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: tab.getStatuses() IN 조건으로 조회
        return ResponseEntity.ok(ApiResponse.success("PROJECTS_FOUND", "조회에 성공했습니다.", samplePage(page, size)));
    }

    @GetMapping("/mine/tab-counts")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "내 프로젝트 탭별 건수", description = "탭 옆 배지 숫자입니다. 건수가 0인 탭도 내려갑니다.")
    public ResponseEntity<ApiResponse<List<ProjectTabCountResponse>>> findMyTabCounts(
            @CurrentAccountId Long accountId
    ) {
        // TODO: 상태별 count 후 탭 단위로 합산
        return ResponseEntity.ok(ApiResponse.success("TAB_COUNTS_FOUND", "조회에 성공했습니다.",
                Arrays.stream(ProjectTab.values())
                        .map(tab -> new ProjectTabCountResponse(tab, null, tab.getLabel(), 1))
                        .toList()));
    }

    @PostMapping("/pre-review")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "사전 검수 (등록 5단계)",
            description = "입력한 포지션 조건으로 포지션별 예상 후보 수와 매칭 가능 여부를 안내합니다. "
                    + "요청한 positions 와 같은 순서·같은 개수로 내려갑니다. "
                    + "직무와 요구 스킬로만 집계하며 AI를 사용하지 않습니다. "
                    + "후보가 부족해도 그대로 등록할 수 있습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    public ResponseEntity<ApiResponse<ProjectPreReviewResponse>> preReview(
            @Valid @RequestBody ProjectPreReviewRequest request
    ) {
        // TODO: 요청 positions 를 순서대로 순회하며 1건씩 집계(직무 기준 병합 금지) -> 부족하면 조건 조정 제안 생성
        return ResponseEntity.ok(ApiResponse.success("PRE_REVIEW_DONE", "검수가 완료되었습니다.", samplePreReview()));
    }

    @GetMapping("/{projectId}/pre-review")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "등록 후 검수 결과 조회",
            description = "등록된 포지션 기준으로 다시 집계합니다. 착수금 결제 여부 판단에 사용합니다.")
    public ResponseEntity<ApiResponse<ProjectPreReviewResponse>> findPreReview(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 등록된 포지션 기준 집계
        return ResponseEntity.ok(ApiResponse.success("PRE_REVIEW_FOUND", "조회에 성공했습니다.", samplePreReview()));
    }

    @PostMapping("/{projectId}/completion")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 완료 처리",
            description = "완료 버튼을 눌러 프로젝트를 완료처리 합니다. 성공보수 수수료 정산이 생성되고 결제하면 리뷰 작성이 열립니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> complete(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 계약 완료 여부 확인 -> 상태 진행중 -> 완료버튼 누름 -> 완료 대기, 성공보수 정산 생성 후 결제 -> 프로젝트 완료, 리뷰 대상 등록
        return ResponseEntity.ok(ApiResponse.success("PROJECT_COMPLETED", "프로젝트를 완료 처리했습니다.",
                sampleDetail()));
    }

    @PostMapping("/{projectId}/termination")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 중도 종료",
            description = "진행 중인 계약이 남아 있는 상태에서 닫습니다. 계약별로 위약금이 발생할 수 있습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> terminate(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 진행 중 계약 TERMINATED 처리 -> 위약금 산정 -> 상태 CLOSED
        return ResponseEntity.ok(ApiResponse.success("PROJECT_TERMINATED", "프로젝트를 중도 종료했습니다.",
                sampleDetail()));
    }

    @PostMapping("/{projectId}/recruit-close")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "모집 종료",
            description = "남은 모집 기간과 무관하게 모집을 닫습니다. 이미 진행 중인 협상과 계약은 그대로 이어집니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> closeRecruit(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 모집 중 포지션 CLOSED 처리, 미응답 매칭 요청 만료
        return ResponseEntity.ok(ApiResponse.success("RECRUIT_CLOSED", "모집을 종료했습니다.", sampleDetail()));
    }

    @PostMapping("/{projectId}/recruit-extensions")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "모집 기간 연장",
            description = "1주 단위로 최대 2회 연장합니다. 상한을 넘기면 클라이언트 파기로 간주되어 위약금이 발생합니다.")
    public ResponseEntity<ApiResponse<ProjectResponse>> extendRecruit(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: extension_count 증가, recruit_deadline 1주 연장
        return ResponseEntity.ok(ApiResponse.success("RECRUIT_EXTENDED", "모집 기간을 연장했습니다.", sampleDetail()));
    }

    // ==========================================
    // 관리자 (R38)
    // ==========================================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/status-counts")
    @Operation(summary = "[관리자] 상태별 건수",
            description = "관리자 목록 탭은 상태 하나에 대응합니다. 건수가 0인 상태도 내려갑니다.")
    public ResponseEntity<ApiResponse<List<ProjectTabCountResponse>>> findStatusCountsForAdmin() {
        // TODO: 상태별 count
        return ResponseEntity.ok(ApiResponse.success("STATUS_COUNTS_FOUND", "조회에 성공했습니다.",
                Arrays.stream(ProjectStatus.values())
                        .map(status -> new ProjectTabCountResponse(null, status, status.getLabel(), 1))
                        .toList()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    @Operation(summary = "[관리자] 프로젝트 목록",
            description = "상태 필터와 키워드 검색을 지원합니다. ROLE_ADMIN 전용입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ProjectSummaryResponse>>> findAllForAdmin(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // TODO: 전체 프로젝트 검색
        return ResponseEntity.ok(ApiResponse.success("PROJECTS_FOUND", "조회에 성공했습니다.", samplePage(page, size)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{projectId}")
    @Operation(summary = "[관리자] 프로젝트 상세")
    public ResponseEntity<ApiResponse<ProjectResponse>> findOneForAdmin(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success("PROJECT_FOUND", "조회에 성공했습니다.", sampleDetail()));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private ProjectResponse sampleDetail() {
        ProjectResponse.Position position = new ProjectResponse.Position(
                10L, 1, JobCategory.DEVELOPMENT, JobRole.BACKEND, 3, 2, 1,
                PositionStatus.RECRUITING, List.of(SkillCode.JAVA, SkillCode.SPRING_BOOT));

        return new ProjectResponse(
                1L, "페어링 웹 리뉴얼", ProjectStatus.RECRUITING, "1 / 2명 확정 · 1명 모집중",
                ProjectPaymentStatus.DEPOSIT_PAID, LocalDate.of(2026, 9, 1), true,
                6, PeriodUnit.MONTH, 50_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME, null,
                "리뉴얼 목적과 배경", "웹 프론트·백엔드 개발", "페이지 20개", "장기 협업 우대",
                2, 1, LocalDateTime.now().plusWeeks(2), 0, 0, 0,
                List.of(position),
                List.of(new ProjectResponse.ParticipantFreelancer(7L, "김개발", JobRole.FRONTEND,
                        MatchingStatus.NEGOTIATING, PayUnit.MONTHLY, 6_200_000L, "협상중", 500L, null)),
                List.of(new ProjectResponse.AttachedFile(1L, "기획서.pdf", 29_491L, "files/project/uuid.pdf")),
                LocalDateTime.now(), 700L);
    }

    private PageResponse<ProjectSummaryResponse> samplePage(int page, int size) {
        ProjectSummaryResponse summary = new ProjectSummaryResponse(
                1L, "PRJ-001", "페어링 웹 리뉴얼", ProjectStatus.RECRUITING, "1 / 2명 확정 · 1명 모집중",
                ProjectPaymentStatus.DEPOSIT_PAID,
                List.of(JobRole.BACKEND.getLabel()), List.of("Java", "Spring Boot"),
                50_000_000L, "6개월", LocalDate.of(2026, 9, 1), 2, 1,
                LocalDateTime.now().plusWeeks(2), "삼성전자", "김프리", LocalDateTime.now());

        return new PageResponse<>(List.of(summary), page, size, 1, 1, true, true);
    }

    private ProjectPreReviewResponse samplePreReview() {
        return new ProjectPreReviewResponse(false,
                List.of(new ProjectPreReviewResponse.Item(0, JobRole.FRONTEND, 2, 5, true, null, List.of()),
                        new ProjectPreReviewResponse.Item(1, JobRole.BACKEND, 1, 0, false,
                                "현재 조건에 맞는 백엔드 개발자 후보가 모집 인원보다 부족합니다.",
                                List.of("요구 스킬을 줄이면 더 많은 후보를 확인할 수 있습니다.",
                                        "직무를 추가하거나 다른 직무로 변경해보세요."))),
                "현재 프리랜서 풀 기준 예상 결과입니다. 실제 후보 수 및 매칭 성사 여부는 달라질 수 있습니다.");
    }
}
