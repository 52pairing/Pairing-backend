package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 협상 애그리거트 매핑 왕복 확인. 스켈레톤을 실서비스로 바꾸기 전에 JPA 매핑(@OneToMany, floor 컬럼,
 * enum, EntityGraph)이 런타임에 저장/복원되는지 먼저 잡는다.
 */
@SpringBootTest
@Transactional
class NegotiationPersistenceTest {

    @Autowired
    private NegotiationRepository negotiationRepository;

    private Negotiation newNegotiation() {
        return Negotiation.create(100L, 1L, 10L, 51L, 50_000_000L, List.of(
                NegotiationCondition.create(ConditionType.AMOUNT, "3200000", "4000000", 0),
                NegotiationCondition.create(ConditionType.PERIOD, "6", "4", 1)));
    }

    @Test
    @DisplayName("협상 + 조건이 함께 저장되고, 조회 시 조건까지 복원된다")
    void saveAndReloadAggregate() {
        Long id = negotiationRepository.save(newNegotiation()).getId();

        Negotiation reloaded = negotiationRepository.findById(id).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(reloaded.getBudgetCap()).isEqualTo(50_000_000L);
        assertThat(reloaded.getTotalRound()).isZero();
        assertThat(reloaded.getConditions()).hasSize(2);
        assertThat(reloaded.getConditions())
                .extracting(NegotiationCondition::getConditionType)
                .containsExactlyInAnyOrder(ConditionType.AMOUNT, ConditionType.PERIOD);
        // 자식의 negotiationId(부모 id)가 복원되는지
        assertThat(reloaded.getConditions()).allSatisfy(c ->
                assertThat(c.getNegotiationId()).isEqualTo(id));
    }

    @Test
    @DisplayName("마지노선(floor)과 조건 락이 저장·복원된다 (floor 컬럼 매핑 확인)")
    void savesFloorAndLock() {
        Negotiation n = newNegotiation();
        NegotiationCondition amount = n.getConditions().get(0);
        amount.submitFloor(PartyRole.CLIENT, "3500000");
        amount.submitFloor(PartyRole.FREELANCER, "3800000");
        amount.lock("3600000");

        Long id = negotiationRepository.save(n).getId();
        Negotiation reloaded = negotiationRepository.findById(id).orElseThrow();

        NegotiationCondition reloadedAmount = reloaded.getConditions().stream()
                .filter(c -> c.getConditionType() == ConditionType.AMOUNT)
                .findFirst().orElseThrow();

        assertThat(reloadedAmount.getStatus()).isEqualTo(ConditionStatus.AGREED);
        assertThat(reloadedAmount.getAgreedValue()).isEqualTo("3600000");
        assertThat(reloadedAmount.getAgreedAt()).isNotNull();
        assertThat(reloadedAmount.floorForViewer(PartyRole.CLIENT)).isEqualTo("3500000");
        assertThat(reloadedAmount.floorForViewer(PartyRole.FREELANCER)).isEqualTo("3800000");
    }

    @Test
    @DisplayName("findByFreelancerId/findByProjectId: 명함 ID 기준으로 페이징 조회한다")
    void findByFreelancerIdAndProjectId() {
        Long id = negotiationRepository.save(newNegotiation()).getId();  // freelancer_profile.id=51, project.id=1
        var pageable = PageRequest.of(0, 10);

        assertThat(negotiationRepository.findByFreelancerId(51L, null, pageable))
                .extracting(Negotiation::getId).contains(id);
        assertThat(negotiationRepository.findByProjectId(1L, null, pageable))
                .extracting(Negotiation::getId).contains(id);
        assertThat(negotiationRepository.findByFreelancerId(999L, null, pageable))
                .extracting(Negotiation::getId).doesNotContain(id);
    }
}
