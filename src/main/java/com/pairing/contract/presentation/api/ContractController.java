package com.pairing.contract.presentation.api;

import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.usecase.ContractCommandUseCase;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.contract.presentation.api.request.ContractRejectRequest;
import com.pairing.contract.presentation.api.request.ContractSignRequest;
import com.pairing.contract.presentation.api.request.ContractTerminateRequest;
import com.pairing.contract.presentation.api.response.ContractResponse;
import com.pairing.contract.presentation.api.response.ContractSummaryResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.security.CurrentAccountId;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 표준계약서. (요구사항 R25, R43)
 *
 * <p>협상이 타결되면 계약서가 자동 생성된다. 생성 엔드포인트를 두지 않는 이유가 그것이다.
 * 이 API 는 조회·PDF·서명·거부를 다룬다.
 *
 * <p>프로젝트 완료 처리는 여기 없다. 완료는 프로젝트 단위라 여러 명을 뽑은 프로젝트에서
 * 계약 1건만 완료한다는 것이 성립하지 않는다. {@code POST /api/v1/projects/{id}/completion} 을 쓴다.
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "14. Contract", description = "계약 API")
public class ContractController {

    private final ContractQueryUseCase contractQueryUseCase;
    private final ContractCommandUseCase contractCommandUseCase;

    @GetMapping
    @Operation(summary = "내 계약 목록",
            description = "헤더의 계약관리 화면에 사용합니다. projectId 를 주면 그 프로젝트의 계약만 "
                    + "나오므로 프로젝트 상세의 계약 탭에도 같은 API 를 씁니다. 최신순입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ContractSummaryResponse>>> findMine(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        Page<ContractSummaryResponse> data = contractQueryUseCase
                .findMine(accountId, projectId, status, PageRequest.of(page, size))
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

    /**
     * 중도 파기. 아직 구현하지 않았다.
     *
     * <p>정책 P32 가 "수행분 정산 후 위약금" 인데, 며칠 일했는지·산출물이 얼마나 나왔는지를 남기는
     * 곳이 없어 수행분을 계산할 근거가 없다. 그 기준이 정해져야 만들 수 있다.
     *
     * <p>고정 응답을 돌려주면 화면이 성공으로 알고 넘어간다. 파기는 되돌릴 수 없는 처리라
     * 명시적으로 막는다.
     */
    @PostMapping("/{contractId}/termination")
    @Operation(summary = "계약 중도 파기 (미구현)",
            description = "수행분 산정 기준이 정해지지 않아 아직 동작하지 않습니다. 501 을 반환합니다. "
                    + "위약금은 파기 주체가 상대방 10% · 플랫폼 10% 를 부담합니다(P32).")
    public ResponseEntity<ApiResponse<Void>> terminate(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractTerminateRequest request,
            @CurrentAccountId Long accountId
    ) {
        throw new BusinessException(ContractErrorCode.TERMINATION_NOT_SUPPORTED);
    }
}
