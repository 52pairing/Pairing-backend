package com.pairing.contract.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.port.ContractFileReaderPort;
import com.pairing.contract.application.port.ContractPartyReaderPort;
import com.pairing.contract.application.port.ContractPdfPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.application.port.ContractSettlementReaderPort;
import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.result.ContractPdfView;
import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.domain.model.ContractSignature;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.domain.service.ContractClauseRenderer;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.infrastructure.s3.S3Settings;
import com.pairing.meta.domain.model.PartyRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 계약 조회.
 *
 * <p>프로젝트명·당사자 이름은 계약이 들고 있지 않아 조회 시점에 포트로 붙인다. 목록은 건수만큼
 * 포트를 타므로 페이지 크기를 크게 잡으면 호출이 늘어난다. 계약관리 화면이 한 페이지 10건이라
 * 지금은 문제되지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ContractQueryService implements ContractQueryUseCase {

    private final ContractRepository contractRepository;
    private final ContractProjectReaderPort projectReaderPort;
    private final ContractPartyReaderPort partyReaderPort;
    private final ContractFileReaderPort fileReaderPort;
    private final ContractPdfPort contractPdfPort;
    private final ContractSettlementReaderPort settlementReaderPort;
    private final S3Settings s3Settings;
    private final ObjectMapper objectMapper;

    /**
     * 목록. 착수금 결제 여부만 페이지 단위로 한 번에 받는다.
     *
     * <p>프로젝트명·상대 이름은 계약마다 포트를 타지만, 착수금은 계약 수만큼 정산을 물으면 쿼리가
     * 그만큼 늘어난다. 배지 하나 때문에 그럴 필요가 없어 페이지의 계약 ID 를 모아 한 번에 묻는다.
     */
    @Override
    public Page<ContractSummary> findMine(Long accountId, Long projectId, ContractStatus status,
                                          Pageable pageable) {
        Page<Contract> page = contractRepository.findByParty(accountId, projectId, status, pageable);

        Set<Long> paidContractIds = settlementReaderPort.findPaidDepositContractIds(
                page.getContent().stream().map(Contract::getId).toList());

        return page.map(contract -> toSummary(contract, accountId, paidContractIds));
    }

    @Override
    public ContractDetail getDetail(Long contractId, Long accountId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        if (!contract.isPartyOf(accountId)) {
            throw new BusinessException(ContractErrorCode.NOT_CONTRACT_PARTY);
        }

        ContractProjectReaderPort.ProjectView project =
                projectReaderPort.findByPositionId(contract.getPositionId());
        ContractPartyReaderPort.ClientParty client = partyReaderPort.findClient(contract.getClientId());
        ContractPartyReaderPort.FreelancerParty freelancer =
                partyReaderPort.findFreelancer(contract.getFreelancerId());

        return new ContractDetail(
                contract,
                project.projectTitle(),
                project.jobRole(),
                client,
                freelancer,
                ContractClauseRenderer.render(contract, new ContractClauseRenderer.ClauseContext(
                        project.projectTitle(), project.jobRole(), project.skills(),
                        draftText(contract), freelancer.settlementAccount())),
                signatureImageUrls(contract));
    }

    @Override
    public byte[] renderPdf(Long contractId, Long accountId) {
        return contractPdfPort.render(toPdfView(getDetail(contractId, accountId)));
    }

    @Override
    public String pdfFileName(Long contractId, Long accountId) {
        return getDetail(contractId, accountId).contract().getContractNo() + ".pdf";
    }

    /**
     * PDF 템플릿에 넣을 값으로 편다.
     *
     * <p>서명 그림은 <b>절대 주소</b>여야 한다. 응답 DTO 는 object key 를 담고 직렬화 시점에
     * CDN 루트가 붙지만, PDF 는 렌더러가 직접 받아 와야 해서 여기서 미리 붙인다.
     */
    private ContractPdfView toPdfView(ContractDetail detail) {
        Contract contract = detail.contract();

        List<ContractPdfView.Signature> signatures = contract.getSignatures().stream()
                .map(signature -> new ContractPdfView.Signature(
                        signature.getPartyRole() == PartyRole.CLIENT ? "갑 (클라이언트)" : "을 (프리랜서)",
                        signature.getPartyRole() == PartyRole.CLIENT
                                ? detail.clientName() : detail.freelancerName(),
                        absoluteUrl(detail.signatureImageUrls().get(signature.getAccountId())),
                        ContractPdfView.format(signature.getSignedAt())))
                .toList();

        return new ContractPdfView(
                contract.getContractNo(),
                ContractPdfView.format(contract.getCreatedAt()),
                new ContractPdfView.Party(detail.client().companyName(), detail.client().businessNo(),
                        detail.client().representative(), detail.client().address(),
                        detail.client().phone(), detail.clientName()),
                new ContractPdfView.Party(null, null, null, null,
                        detail.freelancer().phone(), detail.freelancerName()),
                detail.jobRole() == null ? "-" : detail.jobRole().getLabel(),
                detail.freelancer().settlementAccount(),
                detail.clauses(),
                signatures);
    }

    private String absoluteUrl(String objectKey) {
        return objectKey == null ? null : s3Settings.getCdnBase() + "/" + objectKey;
    }

    /** 서명 그림 주소. 안 그린 서명은 담지 않는다. 파일이 지워졌으면 키가 있어도 값이 없다. */
    private Map<Long, String> signatureImageUrls(Contract contract) {
        Map<Long, String> urls = new HashMap<>();

        contract.getSignatures().stream()
                .filter(signature -> signature.getSignatureFileId() != null)
                .forEach(signature -> {
                    String url = fileReaderPort.findUrl(signature.getSignatureFileId());
                    if (url != null) {
                        urls.put(signature.getAccountId(), url);
                    }
                });
        return urls;
    }

    /**
     * 저장해 둔 조항 스냅샷. DRAFT 라 아직 없거나 형식이 깨졌으면 null 을 돌려준다.
     *
     * <p>렌더러가 null 을 받으면 해당 칸을 일반 문구로 채운다. 계약서 조회가 막히는 것보다 낫다.
     */
    private ContractDraftText draftText(Contract contract) {
        String json = contract.getContentJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ContractDraftText.class);
        } catch (JsonProcessingException e) {
            log.warn("계약서 조항 스냅샷을 읽지 못했다. 기본 문구로 그린다. contractId={}", contract.getId(), e);
            return null;
        }
    }

    /**
     * 목록 카드 한 장. 상대 이름은 보는 사람의 반대편을 채운다.
     *
     * <p>양측 서명 여부는 뷰어와 무관하게 같은 값이다. 카드가 "클라이언트 서명 ○ / 프리랜서 서명 ✓"
     * 를 함께 보여줘서, 내 차례가 아닐 때 누구를 기다리는지 알 수 있어야 한다.
     */
    private ContractSummary toSummary(Contract contract, Long accountId, Set<Long> paidContractIds) {
        ContractSignature mine = contract.findSignature(accountId);

        String counterpartName = mine.getPartyRole() == PartyRole.CLIENT
                ? partyReaderPort.findFreelancerName(contract.getFreelancerId())
                : partyReaderPort.findClientName(contract.getClientId());

        ContractProjectReaderPort.ProjectView project =
                projectReaderPort.findByPositionId(contract.getPositionId());

        return new ContractSummary(
                contract,
                project.projectTitle(),
                project.jobRole(),
                counterpartName,
                mine.getStatus() == SignatureStatus.PENDING,
                contract.isSignedBy(PartyRole.CLIENT),
                contract.isSignedBy(PartyRole.FREELANCER),
                paidContractIds.contains(contract.getId()));
    }
}
