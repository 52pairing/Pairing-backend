package com.pairing.contract.application.port;

import com.pairing.contract.application.port.ContractPartyReaderPort.FreelancerParty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약서 제5조에 찍히는 정산 계좌 한 줄.
 *
 * <p>체결 시점에 굳혀둔 값이 있으면 그것을 쓴다. 없으면 현재 계좌를 조합한다.
 * 이 규칙이 깨지면 프리랜서가 계좌를 바꿨을 때 <b>이미 체결된 계약서의 표시까지 따라 바뀐다.</b>
 * 계약은 5년 보관 대상이라 그때 그 문서가 그대로 남아야 한다.
 */
class FreelancerPartyTest {

    private static final String FROZEN = "카카오뱅크 3333012345678 (예금주: 김민준)";

    @Test
    @DisplayName("굳혀둔 계좌가 있으면 현재 계좌가 바뀌어도 그대로다")
    void prefersFrozenAccount() {
        FreelancerParty changed = new FreelancerParty(1L, "김민준", "01012345678",
                "신한은행", "110123456789", "김민준")
                .withFrozenAccount(FROZEN);

        assertThat(changed.settlementAccount()).isEqualTo(FROZEN);
    }

    @Test
    @DisplayName("굳혀둔 계좌가 없으면 현재 계좌를 조합한다")
    void composesFromCurrentAccount() {
        FreelancerParty party = new FreelancerParty(1L, "김민준", "01012345678",
                "신한은행", "110123456789", "김민준");

        assertThat(party.settlementAccount()).isEqualTo("신한은행 110123456789 (예금주: 김민준)");
    }

    @Test
    @DisplayName("예금주가 없으면 은행과 번호만 적는다")
    void omitsHolderWhenMissing() {
        FreelancerParty party = new FreelancerParty(1L, "김민준", "01012345678",
                "신한은행", "110123456789", null);

        assertThat(party.settlementAccount()).isEqualTo("신한은행 110123456789");
    }

    @Test
    @DisplayName("계좌가 아예 없으면 null 이다 — 계약서에는 '-' 로 나간다")
    void nullWhenNoAccount() {
        assertThat(FreelancerParty.EMPTY.settlementAccount()).isNull();
    }

    @Test
    @DisplayName("굳힐 값이 null 이면 원본을 그대로 둔다")
    void keepsOriginalWhenNothingToFreeze() {
        FreelancerParty party = new FreelancerParty(1L, "김민준", "01012345678",
                "신한은행", "110123456789", "김민준");

        assertThat(party.withFrozenAccount(null)).isSameAs(party);
    }
}
