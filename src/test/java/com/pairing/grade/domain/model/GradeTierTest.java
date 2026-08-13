package com.pairing.grade.domain.model;

import com.pairing.account.domain.model.Role;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 등급 기준표: 승급 판정 경계와 최고 등급 처리. */
class GradeTierTest {

    @Test
    @DisplayName("최고 등급 칸의 승급 기준값은 비어 있다 - 다음 등급이 없다는 표시다")
    void topTierHasNoPromotionThresholds() {
        GradeTier topClient = GradeTier.ofCode(Role.CLIENT, "DIAMOND");
        GradeTier topFreelancer = GradeTier.ofCode(Role.FREELANCER, "MASTER");

        assertThat(topClient.minRatingForNext()).isNull();
        assertThat(topClient.minCompletedForNext()).isNull();
        assertThat(topFreelancer.minRatingForNext()).isNull();
        assertThat(topFreelancer.minCompletedForNext()).isNull();
    }

    @Test
    @DisplayName("경계값을 정확히 채우면 승급한다")
    void promotesExactlyAtThreshold() {
        assertThat(GradeTier.resolve(Role.FREELANCER, 3.0, 5).code()).isEqualTo("SENIOR");
        assertThat(GradeTier.resolve(Role.FREELANCER, 4.0, 10).code()).isEqualTo("MASTER");
        assertThat(GradeTier.resolve(Role.CLIENT, 3.0, 10).code()).isEqualTo("GOLD");
        assertThat(GradeTier.resolve(Role.CLIENT, 4.0, 20).code()).isEqualTo("DIAMOND");
    }

    @Test
    @DisplayName("한 조건만 모자라도 승급하지 않는다")
    void doesNotPromoteWhenEitherConditionFails() {
        assertThat(GradeTier.resolve(Role.FREELANCER, 2.9, 100).code()).isEqualTo("JUNIOR");
        assertThat(GradeTier.resolve(Role.FREELANCER, 5.0, 4).code()).isEqualTo("JUNIOR");
    }

    @Test
    @DisplayName("리뷰가 없으면(평점 null) 기본 등급이고, 실적이 아무리 많아도 터지지 않는다")
    void nullRatingStaysAtBaseTier() {
        assertThat(GradeTier.resolve(Role.CLIENT, null, 999).code()).isEqualTo("SILVER");
    }

    @Test
    @DisplayName("최고 등급 조건을 넘겨도 판정이 터지지 않는다 - 기준값이 없는 칸에서 멈춘다")
    void resolveNeverUnboxesTopTierNulls() {
        assertThatCode(() -> GradeTier.resolve(Role.CLIENT, 5.0, 999)).doesNotThrowAnyException();
        assertThat(GradeTier.resolve(Role.CLIENT, 5.0, 999).code()).isEqualTo("DIAMOND");
    }

    @Test
    @DisplayName("등급표에 없는 코드가 저장돼 있으면 기본 등급으로 본다")
    void unknownCodeFallsBackToBase() {
        assertThat(GradeTier.ofCode(Role.CLIENT, "BRONZE").code()).isEqualTo("SILVER");
        assertThat(GradeTier.ofCode(Role.FREELANCER, null).code()).isEqualTo("JUNIOR");
    }

    @Test
    @DisplayName("클라이언트/프리랜서가 아닌 역할은 기준표가 없다")
    void rejectsOtherRoles() {
        assertThatThrownBy(() -> GradeTier.forRole(Role.ADMIN)).isInstanceOf(BusinessException.class);
    }
}
