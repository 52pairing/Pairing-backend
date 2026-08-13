package com.pairing.settlement.presentation.api;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.project.domain.model.Project;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.application.usecase.SettlementPaymentUseCase;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import com.pairing.settlement.domain.model.PenaltyStatus;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.settlement.exception.SettlementErrorCode;
import com.pairing.settlement.presentation.api.request.SettlementPayRequest;
import com.pairing.settlement.presentation.api.response.MySettlementSummaryResponse;
import com.pairing.settlement.presentation.api.response.PenaltyResponse;
import com.pairing.settlement.presentation.api.response.SettlementResponse;
import com.pairing.settlement.presentation.api.response.SettlementSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 수수료 정산과 위약금. (요구사항 R20, R34, R39)
 *
 * <p>실제 용역비는 플랫폼을 거치지 않는다. 여기서 다루는 돈은 플랫폼 수수료와 위약금뿐이다.
 * 결제는 PG 연동 없이 가상계좌 잔액을 증감하는 수동 처리다.
 *
 * <p><b>위약금 엔드포인트만</b> 아직 고정 응답이다. Penalty 도메인이 없고, 중도 파기 자체가
 * 수행분 산정 기준(P28 제12조) 미정으로 막혀 있어 발생할 일이 없다. 정산은 실제로 동작한다.
 */
@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "15. Settlement", description = "정산/결제 API")
public class SettlementController {

    private final SettlementQueryUseCase settlementQueryUseCase;
    private final SettlementPaymentUseCase settlementPaymentUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final AccountQueryUseCase accountQueryUseCase;

    @GetMapping("/mine")
    @Operation(summary = "내 정산 목록",
            description = "착수금·성공보수 수수료 내역입니다. 클라이언트와 프리랜서 모두 조회합니다. "
                    + "projectId 를 주면 그 프로젝트에서 낸 수수료만 나옵니다. 최신순입니다.")
    public ResponseEntity<ApiResponse<PageResponse<SettlementResponse>>> findMine(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) SettlementPhase phase,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        // 여러 정산이 같은 프로젝트를 가리키는 경우가 많아 요청 단위로 이름 조회를 모은다.
        Map<Long, ProjectInfo> titleCache = new HashMap<>();
        Map<Long, String> methodLabels = paymentMethodLabels(accountId);

        Page<SettlementResponse> data = settlementQueryUseCase
                .findMine(accountId, projectId, phase, status, PageRequest.of(page, size))
                .map(result -> toResponse(result, titleCache, methodLabels));

        return ResponseEntity.ok(ApiResponse.success("SETTLEMENTS_FOUND", "조회에 성공했습니다.",
                PageResponse.from(data)));
    }

    @GetMapping("/mine/summary")
    @Operation(summary = "내 결제 내역 요약",
            description = "마이페이지 결제 내역 상단 카드와 요약 줄에 사용합니다. "
                    + "**결제 완료된 수수료만** 셉니다 — 문구가 '총 납부 수수료'라 실제로 낸 것만 세야 합니다.\n\n"
                    + "목록 API 로는 만들 수 없습니다. 페이징이라 한 페이지 몫만 더하게 되어 2페이지부터 틀립니다.\n\n"
                    + "클라이언트와 프리랜서가 같은 응답을 받아 필요한 칸만 고릅니다.\n"
                    + "- 클라이언트: totalAmount · depositAmount · successFeeAmount\n"
                    + "- 프리랜서: successFeeAmount · successFeeProjectCount(완료 프로젝트 수)\n\n"
                    + "탭을 바꿔도 값이 안 바뀌므로 화면 진입 시 한 번만 부르면 됩니다. "
                    + "낸 게 없으면 전부 0 입니다.")
    public ResponseEntity<ApiResponse<MySettlementSummaryResponse>> findMySummary(
            @CurrentAccountId Long accountId
    ) {
        MySettlementSummaryResponse data =
                MySettlementSummaryResponse.from(settlementQueryUseCase.getMySummary(accountId));

        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_SUMMARY_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "정산 상세", description = "납부자 본인만 열람할 수 있습니다.")
    @ApiErrorCodeExample(domain = SettlementErrorCode.class, value = {"SETTLEMENT_NOT_FOUND", "NOT_PAYER"})
    public ResponseEntity<ApiResponse<SettlementResponse>> findOne(
            @PathVariable Long settlementId,
            @CurrentAccountId Long accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_FOUND", "조회에 성공했습니다.",
                toResponse(settlementQueryUseCase.getByIdForPayer(settlementId, accountId), accountId)));
    }

    @PostMapping("/{settlementId}/payment")
    @Operation(summary = "수수료 결제",
            description = "결제 가능한 상태에서만 호출합니다. "
                    + "클라이언트 착수금이면 결제와 동시에 프로젝트가 모집중으로 전환됩니다. "
                    + "클라이언트 성공보수면 프로젝트가 종료로 전환됩니다. "
                    + "PG 연동 전이라 승인 절차 없이 즉시 완료 처리됩니다.")
    @ApiErrorCodeExample(domain = SettlementErrorCode.class,
            value = {"SETTLEMENT_NOT_FOUND", "NOT_PAYER", "NOT_PAYABLE"})
    public ResponseEntity<ApiResponse<SettlementResponse>> pay(
            @PathVariable Long settlementId,
            @Valid @RequestBody SettlementPayRequest request,
            @CurrentAccountId Long accountId
    ) {
        SettlementResult result =
                settlementPaymentUseCase.pay(settlementId, accountId, request.paymentMethodId());

        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_PAID", "결제가 완료되었습니다.",
                toResponse(result, accountId)));
    }

    private SettlementResponse toResponse(SettlementResult result, Long accountId) {
        return toResponse(result, new HashMap<>(), paymentMethodLabels(accountId));
    }

    /**
     * 프로젝트명·발주 기업명·결제수단 표기를 붙여 응답을 조립한다.
     *
     * <p>정산 서비스가 project 를 직접 읽으면 project -> settlement 방향과 맞물려 순환이 되므로
     * 프레젠테이션에서 인바운드 포트를 조합한다. payerName 은 아직 채우지 않는다.
     */
    private SettlementResponse toResponse(SettlementResult result, Map<Long, ProjectInfo> projectCache,
                                          Map<Long, String> methodLabels) {
        ProjectInfo project = resolveProject(result.projectId(), projectCache);
        return SettlementResponse.from(result,
                project.title(),
                null,
                project.clientName(),
                methodLabels.get(result.paymentMethodId()));
    }

    /** 프로젝트 1건에서 뽑아 쓰는 값. 제목과 기업명을 따로 조회하지 않으려고 묶어 둔다. */
    private record ProjectInfo(String title, String clientName) {

        private static final ProjectInfo EMPTY = new ProjectInfo(null, null);
    }

    /**
     * 이 사람의 결제수단 id -&gt; 표기("신한카드 **** 1234").
     *
     * <p>정산마다 되물으면 목록 크기만큼 조회가 늘어난다. 한 사람이 가진 수단은 많아야 몇 개라
     * 요청 시작에 통째로 읽어 둔다. 조회 대상이 <b>본인 것뿐</b>이라 남의 카드가 섞이지 않는다.
     *
     * <p>삭제된 수단은 목록에서 빠져 표기가 null 이 된다. 결제 이력 자체는 승인번호로 남으므로
     * 화면이 그 칸만 감추면 된다.
     */
    private Map<Long, String> paymentMethodLabels(Long accountId) {
        return accountQueryUseCase.findMyPaymentMethods(accountId).stream()
                .collect(Collectors.toMap(PaymentMethod::getId, PaymentMethod::getDisplayName));
    }

    /**
     * 프로젝트명과 발주 기업명. 못 찾으면 두 칸 다 null 로 흘린다.
     *
     * <p>프로젝트가 삭제돼도 정산 이력은 남아야 한다. 여기서 예외를 그대로 올리면
     * 그 한 건 때문에 목록 전체가 실패한다. 실패도 캐시해 같은 프로젝트를 되묻지 않는다.
     *
     * <p>제목과 기업명을 한 번에 뽑는다. 따로 조회하면 목록 한 페이지에 프로젝트 조회가 두 배로
     * 나가는데, 기업명은 이미 읽어 온 {@code project.getClientId()} 로 한 단계만 더 가면 된다.
     */
    private ProjectInfo resolveProject(Long projectId, Map<Long, ProjectInfo> projectCache) {
        if (projectCache.containsKey(projectId)) {
            return projectCache.get(projectId);
        }

        ProjectInfo info = ProjectInfo.EMPTY;
        try {
            Project project = projectQueryUseCase.getById(projectId);
            // clientId 는 client_profile.id 다. account.id 가 아니라 프로필로 바로 찾는다.
            String clientName = accountQueryUseCase.findClientProfileById(project.getClientId())
                    .map(ClientProfile::getCompanyName)
                    .orElse(null);
            info = new ProjectInfo(project.getTitle(), clientName);
        } catch (BusinessException e) {
            // getById 는 대상이 없을 때만 던진다. 두 칸을 비우고 넘어간다.
        }

        projectCache.put(projectId, info);
        return info;
    }

    @GetMapping("/penalties/mine")
    @Operation(summary = "내 위약금 목록", description = "계약 파기로 발생한 위약금 내역입니다.")
    public ResponseEntity<ApiResponse<List<PenaltyResponse>>> findMyPenalties(
            @CurrentAccountId Long accountId
    ) {
        // TODO: 내가 납부자인 위약금 조회
        return ResponseEntity.ok(ApiResponse.success("PENALTIES_FOUND", "조회에 성공했습니다.",
                List.of(samplePenalty())));
    }

    @PostMapping("/penalties/{penaltyId}/payment")
    @Operation(summary = "위약금 납부")
    public ResponseEntity<ApiResponse<PenaltyResponse>> payPenalty(
            @PathVariable Long penaltyId,
            @Valid @RequestBody SettlementPayRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 결제수단 소유 확인 -> 원장 기록 -> 상태 PAID
        return ResponseEntity.ok(ApiResponse.success("PENALTY_PAID", "납부가 완료되었습니다.", samplePenalty()));
    }

    // ==========================================
    // 관리자 (R39)
    // ==========================================

    @GetMapping("/admin/summary")
    @Operation(summary = "[관리자] 정산 집계", description = "총 수익, 이번 달 수익, 결제 예정·미납·실패, 위약금 수수료입니다.")
    public ResponseEntity<ApiResponse<SettlementSummaryResponse>> findSummaryForAdmin() {
        // TODO: 집계 쿼리
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_SUMMARY_FOUND", "조회에 성공했습니다.",
                new SettlementSummaryResponse(1_105_000L, 400_000L, 225_000L, 150_000L, 0L, 0L)));
    }

    @GetMapping("/admin")
    @Operation(summary = "[관리자] 정산 목록",
            description = "정산번호 검색, 납부자 구분·단계·상태 필터를 지원합니다.")
    public ResponseEntity<ApiResponse<PageResponse<SettlementResponse>>> findAllForAdmin(
            @RequestParam(required = false) String settlementNo,
            @RequestParam(required = false) PartyRole payerRole,
            @RequestParam(required = false) SettlementPhase phase,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // TODO: 전체 정산 검색
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENTS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleSettlement()), page, size, 1, 1, true, true)));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private SettlementResponse sampleSettlement() {
        return new SettlementResponse(700L, "ST-2026-000045", 1L, "페어링 웹 리뉴얼", 600L,
                PartyRole.CLIENT, "주식회사 페어링", "주식회사 오이랩", SettlementPhase.DEPOSIT, 22_000_000L,
                new BigDecimal("3.00"), new BigDecimal("0.00"), 660_000L,
                SettlementStatus.PENDING,
                "신한카드 **** 1234", "AP-20260804-3821", null, null,
                LocalDate.now().plusDays(7), true, null);
    }

    private PenaltyResponse samplePenalty() {
        return new PenaltyResponse(800L, "페어링 웹 리뉴얼", 600L, PartyRole.CLIENT, "PLATFORM",
                10_000_000L, new BigDecimal("10.00"), 1_000_000L, PenaltyStatus.PENDING, null);
    }
}
