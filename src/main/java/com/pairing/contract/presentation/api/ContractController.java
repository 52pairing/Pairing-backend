package com.pairing.contract.presentation.api;

import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.usecase.ContractCommandUseCase;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.contract.presentation.api.request.ContractRejectRequest;
import com.pairing.contract.presentation.api.request.ContractSignRequest;
import com.pairing.contract.presentation.api.request.ContractTerminateRequest;
import com.pairing.contract.presentation.api.response.ContractFileResponse;
import com.pairing.contract.presentation.api.response.ContractResponse;
import com.pairing.contract.presentation.api.response.ContractSummaryResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 표준계약서. (요구사항 R25, R43)
 *
 * <p>협상이 타결되면 계약서가 자동 생성된다. 이 API 는 조회·서명·완료·파기를 다룬다.
 * 계약서 생성 자체는 협상 타결 시 서버가 수행하므로 생성 엔드포인트를 두지 않는다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "14. Contract", description = "계약 API")
public class ContractController {

    private final ContractQueryUseCase contractQueryUseCase;
    private final ContractCommandUseCase contractCommandUseCase;

    @GetMapping
    @Operation(summary = "내 계약 목록", description = "헤더의 계약관리 화면에 사용합니다.")
    public ResponseEntity<ApiResponse<PageResponse<ContractSummaryResponse>>> findMine(
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        Page<ContractSummaryResponse> data = contractQueryUseCase
                .findMine(accountId, status, PageRequest.of(page, size))
                .map(ContractSummaryResponse::from);

        return ResponseEntity.ok(ApiResponse.success("CONTRACTS_FOUND", "조회에 성공했습니다.",
                PageResponse.from(data)));
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "계약 상세", description = "계약서 본문에 들어가는 조건과 양측 서명 현황을 반환합니다.")
    @ApiErrorCodeExample(domain = ContractErrorCode.class,
            value = {"CONTRACT_NOT_FOUND", "NOT_CONTRACT_PARTY"})
    public ResponseEntity<ApiResponse<ContractResponse>> findOne(
            @PathVariable Long contractId,
            @CurrentAccountId Long accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success("CONTRACT_FOUND", "조회에 성공했습니다.",
                ContractResponse.from(contractQueryUseCase.getDetail(contractId, accountId))));
    }

    @GetMapping(value = "/{contractId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "계약서 PDF 다운로드",
            description = "계약서를 PDF 로 내려받습니다. 서명 전에도 받을 수 있고, 서명이 끝나면 "
                    + "하단 서명란에 서명 이미지와 시각이 함께 찍힙니다. 당사자만 받을 수 있습니다.")
    @ApiErrorCodeExample(domain = ContractErrorCode.class,
            value = {"CONTRACT_NOT_FOUND", "NOT_CONTRACT_PARTY", "PDF_RENDER_FAILED"})
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long contractId,
            @CurrentAccountId Long accountId
    ) {
        byte[] pdf = contractQueryUseCase.renderPdf(contractId, accountId);
        String fileName = contractQueryUseCase.pdfFileName(contractId, accountId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // 파일명에 한글이 없어도 RFC 5987 형식으로 주면 브라우저가 일관되게 처리한다.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .body(pdf);
    }

    @PostMapping("/{contractId}/signature")
    @Operation(summary = "계약 서명",
            description = "양측이 모두 서명하면 체결되고 해당 포지션 인원이 확정됩니다. 서명 기한은 없습니다.")
    @ApiErrorCodeExample(domain = ContractErrorCode.class,
            value = {"CONTRACT_NOT_FOUND", "NOT_CONTRACT_PARTY", "INVALID_CONTRACT_STATUS", "ALREADY_SIGNED"})
    public ResponseEntity<ApiResponse<ContractResponse>> sign(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractSignRequest request,
            @CurrentAccountId Long accountId,
            HttpServletRequest httpRequest
    ) {
        // 서명 증거로 접속 정보를 남긴다. 프록시 뒤라 값이 없을 수 있어 도메인이 null 을 허용한다.
        contractCommandUseCase.sign(new SignContractCommand(contractId, accountId,
                request.signatureFileId(),
                httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT)));

        return ResponseEntity.ok(ApiResponse.success("CONTRACT_SIGNED", "서명했습니다.",
                ContractResponse.from(contractQueryUseCase.getDetail(contractId, accountId))));
    }

    @PostMapping("/{contractId}/rejection")
    @Operation(summary = "계약 서명 거부", description = "거부하면 상대에게 알림이 가고 해당 매칭은 종료됩니다.")
    @ApiErrorCodeExample(domain = ContractErrorCode.class,
            value = {"CONTRACT_NOT_FOUND", "NOT_CONTRACT_PARTY", "INVALID_CONTRACT_STATUS", "ALREADY_SIGNED"})
    public ResponseEntity<ApiResponse<ContractResponse>> reject(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractRejectRequest request,
            @CurrentAccountId Long accountId
    ) {
        contractCommandUseCase.reject(contractId, accountId, request.reason());

        return ResponseEntity.ok(ApiResponse.success("CONTRACT_REJECTED", "서명을 거부했습니다.",
                ContractResponse.from(contractQueryUseCase.getDetail(contractId, accountId))));
    }

    @PostMapping("/{contractId}/completion")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "프로젝트 완료 처리",
            description = "클라이언트가 완료를 누르면 상태가 완료 대기로 바뀌고 성공보수 수수료 결제가 열립니다.")
    public ResponseEntity<ApiResponse<ContractResponse>> complete(
            @PathVariable Long contractId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 매칭 상태 COMPLETION_PENDING, 성공보수 정산 생성
        return ResponseEntity.ok(ApiResponse.success("CONTRACT_COMPLETED", "완료 처리했습니다.", sampleDetail()));
    }

    @PostMapping("/{contractId}/termination")
    @Operation(summary = "계약 중도 파기",
            description = "수행분 정산과 별도로 파기 주체가 상대방 10% · 플랫폼 10%의 위약금을 부담합니다.")
    public ResponseEntity<ApiResponse<ContractResponse>> terminate(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractTerminateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 계약 TERMINATED, 위약금 생성, 상대 알림
        return ResponseEntity.ok(ApiResponse.success("CONTRACT_TERMINATED", "계약을 파기했습니다.", sampleDetail()));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private ContractResponse sampleDetail() {
        List<ContractResponse.Signature> signatures = List.of(
                new ContractResponse.Signature(PartyRole.CLIENT, "주식회사 페어링",
                        SignatureStatus.SIGNED, LocalDateTime.now(), "signatures/uuid.png", null),
                new ContractResponse.Signature(PartyRole.FREELANCER, "홍길동",
                        SignatureStatus.PENDING, null, null, null));

        return new ContractResponse(600L, "PR-2026-000123", 1L, "페어링 웹 리뉴얼", 300L,
                "주식회사 페어링", "홍길동",
                new ContractResponse.ClientParty("주식회사 페어링", "1208147521", "정신아",
                        "경기도 성남시 분당구 판교역로 235", "0212345678"),
                new ContractResponse.FreelancerParty("홍길동", "01098765432", JobRole.BACKEND,
                        "카카오뱅크 3333012345678 (예금주: 홍길동)"),
                JobRole.BACKEND, ContractStatus.SIGN_PENDING,
                22_000_000L, PayUnit.MONTHLY, 6_000_000L, 6_600_000L, 15_400_000L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 2, 28),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null,
                7, 7, 3, new BigDecimal("10.00"), "협상 로그 기반 특약사항",
                List.of(new ContractResponse.Clause(1, "용역의 내용",
                                "프리랜서는 클라이언트의 요청에 따라 백엔드 개발 업무를 수행한다."),
                        new ContractResponse.Clause(2, "용역 기간",
                                "용역 기간은 2026.09.01~2027.02.28 로 한다. 단, 양 당사자의 합의에 의해 연장할 수 있다."),
                        new ContractResponse.Clause(3, "용역비",
                                "클라이언트는 프리랜서에게 월 6,000,000원을 매월 말일에 지급한다. 플랫폼 수수료가 별도 적용된다."),
                        new ContractResponse.Clause(4, "비밀유지",
                                "양 당사자는 본 계약과 관련하여 취득한 상대방의 영업비밀 및 개인정보를 제3자에게 누설하지 않는다.")),
                9L, signatures, null, LocalDateTime.now());
    }
}
