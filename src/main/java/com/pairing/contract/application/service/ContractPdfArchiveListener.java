package com.pairing.contract.application.service;

import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.application.port.ContractArchivePort;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.PartyRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 체결된 계약서를 PDF 로 굳혀 둔다.
 *
 * <p><b>왜 굳히나.</b> 계약서는 요청할 때마다 다시 그린다. 그러면 조항 문구나 표기 규칙을 고쳤을 때
 * 이미 체결된 계약서까지 새 양식으로 바뀐다. 계약은 5년 보관 대상이라 그때 그 문서가 그대로
 * 남아야 한다.
 *
 * <p><b>커밋 뒤로 미루는 이유.</b> PDF 렌더링과 외부 스토리지 업로드가 붙어 있어 체결 트랜잭션에
 * 두면 서명 응답이 그만큼 늦어진다. 실패했을 때 서명까지 되돌리는 것도 과하다 — 파일이 없으면
 * 예전처럼 그 자리에서 그리면 되기 때문이다.
 *
 * <p><b>{@code @Async} 는 붙이지 않았다.</b> 붙이면 서명 직후 곧바로 PDF 를 받는 요청과 겹쳐
 * 두 번 저장될 수 있다. 같은 스레드에 두면 응답 전에 끝나 그 창이 없다. 렌더링이 몇백 ms 라
 * 체감되는 지연도 아니다.
 *
 * <p>실패해도 삼킨다. {@code pdf_file_id} 가 비면 조회가 자동으로 렌더링으로 떨어져서
 * 사용자는 아무 차이를 느끼지 못한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContractPdfArchiveListener {

    private final ContractRepository contractRepository;
    private final ContractQueryUseCase contractQueryUseCase;
    private final ContractArchivePort archivePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ContractSignedEvent event) {
        try {
            Contract contract = contractRepository.findById(event.contractId()).orElse(null);
            if (contract == null || contract.getPdfFileId() != null) {
                return;   // 이미 굳혔다. 이벤트가 두 번 와도 두 번 저장하지 않는다
            }

            Long freelancerAccountId = contract.accountIdOf(PartyRole.FREELANCER);
            byte[] pdf = contractQueryUseCase.renderPdf(contract.getId(), freelancerAccountId);

            contract.attachPdf(archivePort.archive(pdf, contract.getContractNo(), freelancerAccountId));
            contractRepository.updateState(contract);

        } catch (Exception e) {
            log.error("계약서 PDF 보관에 실패했다. 계약 체결은 처리됐고 조회는 매번 렌더링으로 동작한다. "
                    + "contractId={}", event.contractId(), e);
        }
    }
}
