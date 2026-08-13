package com.pairing.project.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.file.exception.FileErrorCode;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionStatus;
import com.pairing.project.domain.model.ProjectPaymentStatus;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.project.application.usecase.ProjectPreReviewUseCase;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.application.result.ProjectAttachment;
import com.pairing.project.exception.ProjectErrorCode;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    private final ProjectCommandUseCase projectCommandUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final ProjectPreReviewUseCase projectPreReviewUseCase;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 등록",
            description = "등록 직후 상태는 REGISTERED 이며, 착수금 수수료를 결제해야 모집이 시작됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = FileErrorCode.class, value = {"FILE_NOT_FOUND"})
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody ProjectCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        Long projectId = projectCommandUseCase.create(request.toCommand(accountId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("PROJECT_CREATED", "프로젝트가 등록되었습니다.",
                        ProjectResponse.from(projectQueryUseCase.getDetail(projectId))));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 상세 조회",
            description = "본인이 등록한 프로젝트만 조회할 수 있습니다. "
                    + "참여 프리랜서 열람은 매칭 도메인 구현 후 엽니다.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class,
            value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER"})
    public ResponseEntity<ApiResponse<ProjectResponse>> findOne(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success("PROJECT_FOUND", "조회에 성공했습니다.",
                ProjectResponse.from(projectQueryUseCase.getDetailForOwner(projectId, accountId))));
    }

    @GetMapping("/{projectId}/files/{fileId}/download")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "첨부 자료 다운로드",
            description = "첨부 파일을 내려받습니다. 상세 조회와 같은 범위로, 본인이 등록한 프로젝트의 첨부만 받을 수 있습니다. "
                    + "상세 응답의 fileUrl 은 CDN 직링크라 브라우저가 이미지·PDF 를 그냥 열어버리므로, "
                    + "저장이 필요하면 이 API 를 쓰세요.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class, value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER"})
    @ApiErrorCodeExample(domain = FileErrorCode.class, value = {"FILE_NOT_FOUND"})
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long projectId,
            @PathVariable Long fileId,
            @CurrentAccountId Long accountId
    ) {
        ProjectAttachment attachment = projectQueryUseCase.downloadAttachment(projectId, fileId, accountId);

        return ResponseEntity.ok()
                // 첨부는 jpg·pdf·zip 아무거나 올 수 있다. 옥텟이면 브라우저가 타입을 따지지 않고 저장한다.
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                // 한글 파일명이 흔해서 RFC 5987 형식으로 준다. 계약서 PDF 와 같은 방식이다.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.originalName(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(attachment.content());
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
        projectCommandUseCase.update(request.toCommand(projectId, accountId));

        return ResponseEntity.ok(ApiResponse.success("PROJECT_UPDATED", "수정되었습니다.",
                ProjectResponse.from(projectQueryUseCase.getDetail(projectId))));
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
        Page<ProjectSummaryResponse> data = projectQueryUseCase
                .findMine(accountId, tab, PageRequest.of(page, size))
                .map(ProjectSummaryResponse::from);

        return ResponseEntity.ok(ApiResponse.success("PROJECTS_FOUND", "조회에 성공했습니다.",
                PageResponse.from(data)));
    }

    @GetMapping("/mine/tab-counts")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "내 프로젝트 탭별 건수", description = "탭 옆 배지 숫자입니다. 건수가 0인 탭도 내려갑니다.")
    public ResponseEntity<ApiResponse<List<ProjectTabCountResponse>>> findMyTabCounts(
            @CurrentAccountId Long accountId
    ) {
        Map<ProjectTab, Long> counts = projectQueryUseCase.countMyTabs(accountId);

        return ResponseEntity.ok(ApiResponse.success("TAB_COUNTS_FOUND", "조회에 성공했습니다.",
                Arrays.stream(ProjectTab.values())
                        .map(tab -> new ProjectTabCountResponse(
                                tab, null, tab.getLabel(), counts.getOrDefault(tab, 0L)))
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
        return ResponseEntity.ok(ApiResponse.success("PRE_REVIEW_DONE", "검수가 완료되었습니다.",
                ProjectPreReviewResponse.from(projectPreReviewUseCase.review(request.toCommand()))));
    }

    @GetMapping("/{projectId}/pre-review")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "등록 후 검수 결과 조회",
            description = "등록된 포지션 기준으로 다시 집계합니다. 착수금 결제 여부 판단에 사용합니다.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class,
            value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER"})
    public ResponseEntity<ApiResponse<ProjectPreReviewResponse>> findPreReview(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success("PRE_REVIEW_FOUND", "조회에 성공했습니다.",
                ProjectPreReviewResponse.from(
                        projectPreReviewUseCase.reviewRegistered(projectId, accountId))));
    }

    @PostMapping("/{projectId}/completion")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 완료 처리",
            description = "진행중인 프로젝트를 완료 대기로 넘깁니다. 성공보수 수수료 정산이 함께 만들어지고 "
                    + "결제 버튼이 활성화됩니다. 그 결제까지 끝나야 종료 상태가 되고 리뷰 작성이 열립니다.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class,
            value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER", "INVALID_STATUS"})
    public ResponseEntity<ApiResponse<ProjectResponse>> complete(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        projectCommandUseCase.complete(projectId, accountId);

        return ResponseEntity.ok(ApiResponse.success("PROJECT_COMPLETION_REQUESTED",
                "완료 처리했습니다. 성공보수 수수료를 결제하면 종료됩니다.",
                ProjectResponse.from(projectQueryUseCase.getDetail(projectId))));
    }

    @PostMapping("/{projectId}/registration-cancellation")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 등록 취소",
            description = "잘못 등록한 프로젝트를 내립니다. 착수금 결제 전(등록 완료)에만 호출할 수 있습니다. "
                    + "프로젝트는 취소됨 상태가 되고 결제 대기 중이던 착수금 정산도 함께 취소됩니다. "
                    + "결제 후에는 모집 종료를 사용하세요.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class,
            value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER", "REGISTRATION_CANCEL_NOT_ALLOWED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> cancelRegistration(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        projectCommandUseCase.cancelRegistration(projectId, accountId);

        return ResponseEntity.ok(ApiResponse.success("PROJECT_REGISTRATION_CANCELED",
                "프로젝트 등록을 취소했습니다.",
                ProjectResponse.from(projectQueryUseCase.getDetail(projectId))));
    }

    @PostMapping("/{projectId}/recruit-close")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "모집 종료",
            description = "남은 모집 기간과 무관하게 모집을 닫습니다. "
                    + "더 이상 모집·협상·계약을 진행하지 않는다는 뜻이므로 프로젝트는 취소됨 상태가 됩니다. "
                    + "채워지지 않은 모집 직군은 함께 마감됩니다. "
                    + "모집중일 때만 호출할 수 있습니다.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class,
            value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER", "RECRUIT_CLOSE_NOT_ALLOWED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> closeRecruit(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        projectCommandUseCase.closeRecruit(projectId, accountId);

        return ResponseEntity.ok(ApiResponse.success("RECRUIT_CLOSED", "모집을 종료했습니다.",
                ProjectResponse.from(projectQueryUseCase.getDetail(projectId))));
    }

    @PostMapping("/{projectId}/recruit-extensions")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "모집 기간 연장",
            description = "1주 단위로 최대 2회 연장합니다. 상한을 넘기면 클라이언트 파기로 간주되어 위약금이 발생합니다.")
    @ApiErrorCodeExample(domain = ProjectErrorCode.class,
            value = {"PROJECT_NOT_FOUND", "NOT_PROJECT_OWNER", "INVALID_STATUS", "EXTENSION_LIMIT_EXCEEDED"})
    public ResponseEntity<ApiResponse<ProjectResponse>> extendRecruit(
            @PathVariable Long projectId,
            @CurrentAccountId Long accountId
    ) {
        projectCommandUseCase.extendRecruit(projectId, accountId);

        return ResponseEntity.ok(ApiResponse.success("RECRUIT_EXTENDED", "모집 기간을 연장했습니다.",
                ProjectResponse.from(projectQueryUseCase.getDetail(projectId))));
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
                List.of(new ProjectResponse.AttachedFile(1L, "기획서.pdf", 29_491L, "files/project/uuid.pdf")),
                LocalDateTime.now(), 700L);
    }

    private PageResponse<ProjectSummaryResponse> samplePage(int page, int size) {
        ProjectSummaryResponse summary = new ProjectSummaryResponse(
                1L, "PRJ-001", "페어링 웹 리뉴얼", ProjectStatus.RECRUITING, "1 / 2명 확정 · 1명 모집중",
                ProjectPaymentStatus.DEPOSIT_PAID,
                List.of(JobRole.BACKEND.getLabel()), List.of("Java", "Spring Boot"),
                50_000_000L, "6개월", LocalDate.of(2026, 9, 1), 2, 1,
                LocalDateTime.now().plusWeeks(2), "삼성전자", "김프리", 700L, LocalDateTime.now());

        return new PageResponse<>(List.of(summary), page, size, 1, 1, true, true);
    }

}
