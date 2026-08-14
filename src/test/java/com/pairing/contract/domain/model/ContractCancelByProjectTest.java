package com.pairing.contract.domain.model;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 취소로 계약이 끝나는 경로. (정책 P46)
 *
 * <p>당사자가 누른 중도 파기({@code terminate})와 다르다. 그쪽은 체결된 계약만 대상이고 아니면
 * 예외를 던지는데, 프로젝트가 통째로 사라지는 경우엔 <b>서명 전 계약도 갈 곳이 없다.</b>
 * 남겨두면 프리랜서가 서명을 눌렀을 때 프로젝트 쪽에서 {@code PJ_012} 가 올라와
 * 계약 화면에 남의 도메인 에러가 뜬다.
 */
class ContractCancelByProjectTest {

    private static final LocalDate RETENTION = LocalDate.now().plusYears(5);

    @Test
    @DisplayName("체결된 계약이 중도 파기로 간다")
    void terminatesConcludedContract() {
        Contract contract = signPending();
        contract.sign(2000L, "SESSION", null, null, null, null);
        contract.sign(1000L, "SESSION", null, null, null, null);

        assertThat(contract.cancelByProject(RETENTION)).isTrue();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(contract.getTerminatedAt()).isNotNull();
        assertThat(contract.getRetentionUntil()).isEqualTo(RETENTION);
    }

    @Test
    @DisplayName("서명 전 계약도 대상이다")
    void terminatesUnsignedContract() {
        // terminate() 는 여기서 예외를 던진다. 그대로 두면 서명 대기로 남아 프리랜서가 누른다.
        Contract contract = signPending();

        assertThat(contract.cancelByProject(RETENTION)).isTrue();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
    }

    @Test
    @DisplayName("파기 주체는 클라이언트다")
    void recordsClientAsTerminator() {
        // 모집 기간을 넘겼든 직접 닫았든 프로젝트를 접은 쪽이다.
        Contract contract = signPending();

        contract.cancelByProject(RETENTION);

        assertThat(contract.getTerminatedBy()).isEqualTo(PartyRole.CLIENT);
    }

    @Test
    @DisplayName("이미 끝난 계약은 건드리지 않는다")
    void skipsAlreadyFinished() {
        // 이벤트가 두 번 와도 파기 시각이 덮어써지면 안 된다.
        Contract contract = signPending();
        contract.cancelByProject(RETENTION);
        var firstTerminatedAt = contract.getTerminatedAt();

        assertThat(contract.cancelByProject(RETENTION)).isFalse();
        assertThat(contract.getTerminatedAt()).isEqualTo(firstTerminatedAt);
    }

    private Contract signPending() {
        Contract contract = Contract.create(500L, 1L, 10L, 100L, 200L,
                1000L, 2000L, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contract.completeDraft("{}", null);
        return contract;
    }
}
