package com.pairing.negotiation.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 협상 심판 로직: 라운드 상한(15), 조건 락, 타결/결렬 전이. */
class NegotiationTest {

    private Negotiation withOneCondition() {
        return Negotiation.create(1L, 1L, 10L, 51L, 50_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "20000000", "25000000", 0)));
    }

    @Test
    @DisplayName("생성 시 IN_PROGRESS·라운드 0 으로 시작한다")
    void create() {
        Negotiation n = withOneCondition();

        assertThat(n.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(n.getTotalRound()).isZero();
        assertThat(n.getConditions()).hasSize(1);
    }

    @Test
    @DisplayName("라운드는 15까지만 올라가고, 소진 후 더 올리면 ROUND_LIMIT_REACHED")
    void roundCap() {
        Negotiation n = withOneCondition();

        for (int i = 0; i < Negotiation.MAX_ROUND; i++) {
            n.incrementRound();
        }
        assertThat(n.getTotalRound()).isEqualTo(15);
        assertThat(n.isMaxRoundReached()).isTrue();

        assertThatThrownBy(n::incrementRound)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.ROUND_LIMIT_REACHED);
    }

    @Test
    @DisplayName("전 조건 합의 시에만 타결(AGREED)된다")
    void agreeOnlyWhenAllConditionsAgreed() {
        Negotiation n = withOneCondition();

        assertThatThrownBy(() -> n.agree(23_000_000L))
                .isInstanceOf(BusinessException.class);

        n.getConditions().get(0).lock("23000000");
        n.agree(23_000_000L);

        assertThat(n.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(n.getAgreedAmount()).isEqualTo(23_000_000L);
        assertThat(n.getAiOutAt()).isNotNull();
    }

    @Test
    @DisplayName("포기/소진 시 결렬(FAILED)되고, 종료된 협상은 더 이상 라운드를 못 돈다")
    void fail() {
        Negotiation n = withOneCondition();
        n.fail("협상 포기");

        assertThat(n.getStatus()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(n.getEndReason()).isEqualTo("협상 포기");
        assertThatThrownBy(n::incrementRound)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NOT_IN_PROGRESS);
    }

    @Test
    @DisplayName("조건: 거절→REJECTED, 재지시→PENDING+라운드증가, 락→AGREED")
    void conditionTransitions() {
        NegotiationCondition c = NegotiationCondition.create(ConditionType.PERIOD, "6", "4", 0);

        c.reject();
        assertThat(c.getStatus()).isEqualTo(ConditionStatus.REJECTED);

        c.redirect(PartyRole.FREELANCER, "5");
        assertThat(c.getStatus()).isEqualTo(ConditionStatus.PENDING);
        assertThat(c.getRoundCount()).isEqualTo(1);
        assertThat(c.getFreelancerFloor()).isEqualTo("5");

        c.lock("5");
        assertThat(c.isAgreed()).isTrue();
        assertThatThrownBy(() -> c.lock("6"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.CONDITION_ALREADY_LOCKED);
    }

    @Test
    @DisplayName("마지노선은 뷰어 본인 것만 보인다(상대 floor 비공개)")
    void floorPrivacy() {
        NegotiationCondition c = NegotiationCondition.create(ConditionType.AMOUNT, "3200000", "4000000", 0);
        c.submitFloor(PartyRole.CLIENT, "3500000");
        c.submitFloor(PartyRole.FREELANCER, "3800000");

        assertThat(c.floorForViewer(PartyRole.CLIENT)).isEqualTo("3500000");
        assertThat(c.floorForViewer(PartyRole.FREELANCER)).isEqualTo("3800000");
    }
}
