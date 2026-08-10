package com.pairing.contract.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.port.ContractPartyReaderPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.application.result.ContractDetail;
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
import com.pairing.meta.domain.model.PartyRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ObjectMapper objectMapper;

    @Override
    public Page<ContractSummary> findMine(Long accountId, ContractStatus status, Pageable pageable) {
        return contractRepository.findByParty(accountId, status, pageable)
                .map(contract -> toSummary(contract, accountId));
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
                        project.projectTitle(), project.jobRole(), draftText(contract),
                        freelancer.settlementAccount())));
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

    /** 목록 카드 한 장. 상대 이름은 보는 사람의 반대편을 채운다. */
    private ContractSummary toSummary(Contract contract, Long accountId) {
        ContractSignature mine = contract.findSignature(accountId);

        String counterpartName = mine.getPartyRole() == PartyRole.CLIENT
                ? partyReaderPort.findFreelancerName(contract.getFreelancerId())
                : partyReaderPort.findClientName(contract.getClientId());

        return new ContractSummary(
                contract,
                projectReaderPort.findByPositionId(contract.getPositionId()).projectTitle(),
                counterpartName,
                mine.getStatus() == SignatureStatus.PENDING);
    }
}
