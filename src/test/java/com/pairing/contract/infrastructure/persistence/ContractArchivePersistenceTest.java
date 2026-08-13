package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 체결 시점에 굳힌 값들이 DB 까지 내려가는지 본다.
 *
 * <p>목으로만 검증하면 이 종류의 버그를 못 잡는다. 도메인은 값을 잘 들고 있는데 저장 경로가
 * 그 필드를 안 옮기면 <b>메모리에서만 굳고 DB 에는 안 남는다.</b> 실제로 프로젝트 쪽에서
 * {@code confirmedCount} 가 정확히 그렇게 빠져 있었고, DB 왕복 테스트로만 잡혔다.
 *
 * <p>그래서 저장하고 영속성 컨텍스트를 비운 뒤 <b>다시 읽어서</b> 확인한다. 그냥 보면 메모리
 * 인스턴스를 보게 돼 매퍼가 필드를 빠뜨려도 통과해 버린다.
 */
@SpringBootTest
@Transactional
class ContractArchivePersistenceTest {

    private static final Long CLIENT_ACCOUNT_ID = 960_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 960_002L;
    private static final byte[] ACCOUNT_SNAPSHOT =
            "카카오뱅크 3333012345678 (예금주: 김민준)".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("굳힌 정산 계좌가 DB 에 저장된다")
    void persistsSettlementAccountSnapshot() {
        Contract saved = contractRepository.save(draft(9_601L));

        Contract loaded = reload(saved.getId());
        loaded.freezeSettlementAccount(ACCOUNT_SNAPSHOT);
        contractRepository.updateState(loaded);

        assertThat(reload(saved.getId()).getSettlementAccountEnc()).isEqualTo(ACCOUNT_SNAPSHOT);
    }

    @Test
    @DisplayName("보관한 계약서 fileId 가 DB 에 저장된다")
    void persistsArchivedPdfFileId() {
        Contract saved = contractRepository.save(draft(9_602L));

        Contract loaded = reload(saved.getId());
        loaded.attachPdf(777L);
        contractRepository.updateState(loaded);

        assertThat(reload(saved.getId()).getPdfFileId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("한 번 굳힌 계좌는 다시 저장해도 덮이지 않는다")
    void keepsFirstSnapshot() {
        // 체결 이후에 바뀐 계좌가 계약서에 들어가면 동결하는 의미가 없다.
        Contract saved = contractRepository.save(draft(9_603L));

        Contract first = reload(saved.getId());
        first.freezeSettlementAccount(ACCOUNT_SNAPSHOT);
        contractRepository.updateState(first);

        Contract second = reload(saved.getId());
        second.freezeSettlementAccount("신한은행 110999999999".getBytes(StandardCharsets.UTF_8));
        contractRepository.updateState(second);

        assertThat(reload(saved.getId()).getSettlementAccountEnc()).isEqualTo(ACCOUNT_SNAPSHOT);
    }

    @Test
    @DisplayName("굳히지 않은 계약은 두 칸이 비어 있다 — 조회가 현재 계좌·렌더링으로 떨어진다")
    void leavesBothEmptyWhenNotFrozen() {
        Contract saved = contractRepository.save(draft(9_604L));

        Contract loaded = reload(saved.getId());
        assertThat(loaded.getSettlementAccountEnc()).isNull();
        assertThat(loaded.getPdfFileId()).isNull();
    }

    /** 영속성 컨텍스트를 비우고 DB 에서 새로 읽는다. */
    private Contract reload(Long contractId) {
        entityManager.flush();
        entityManager.clear();
        return contractRepository.findById(contractId).orElseThrow();
    }

    /** negotiationId 는 계약마다 유일해야 해서 테스트끼리 겹치지 않게 받는다. */
    private Contract draft(Long negotiationId) {
        Contract contract = Contract.create(negotiationId, 1L, 10L, 100L, 200L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contract.completeDraft("{}", null);
        return contract;
    }
}
