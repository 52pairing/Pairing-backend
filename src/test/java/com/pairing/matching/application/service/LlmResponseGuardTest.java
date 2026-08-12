package com.pairing.matching.application.service;

import com.pairing.matching.application.result.RankedFreelancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가드 G4 — LLM 응답 이상 차단. <b>가드에서 실제로 후보를 거르는 곳은 여기뿐이다.</b>
 *
 * <p>AI 서버도 "풀 밖 ID는 버린다"는 방어를 갖고 있지만 그건 지어낸 ID만 본다. 중복·초과·근거
 * 누락은 풀 안의 정상 ID로도 일어난다.
 */
class LlmResponseGuardTest {

    @Test
    @DisplayName("같은 사람을 두 번 주면 앞선 순위만 남긴다")
    void dropsDuplicateKeepingHigherRank() {
        List<RankedFreelancer> result = LlmResponseGuard.sanitize(List.of(
                new RankedFreelancer(1L, 95.0, "1순위", 0.9),
                new RankedFreelancer(2L, 90.0, "2순위", 0.8),
                new RankedFreelancer(1L, 70.0, "중복", 0.9)), 3);

        assertThat(result).extracting(RankedFreelancer::freelancerId).containsExactly(1L, 2L);
        assertThat(result.get(0).score()).isEqualTo(95.0);
    }

    @Test
    @DisplayName("추천 근거가 비었으면 버린다 — 화면에 근거 없는 후보가 뜨면 안 된다")
    void dropsCandidateWithoutReason() {
        List<RankedFreelancer> result = LlmResponseGuard.sanitize(List.of(
                new RankedFreelancer(1L, 95.0, null, 0.9),
                new RankedFreelancer(2L, 90.0, "   ", 0.8),
                new RankedFreelancer(3L, 85.0, "요구 스킬 일치", 0.7)), 3);

        assertThat(result).extracting(RankedFreelancer::freelancerId).containsExactly(3L);
    }

    @Test
    @DisplayName("요청보다 많이 주면 상위 N만 남긴다 — 전량 실패로 처리하지 않는다")
    void truncatesToRequestedPoolSize() {
        List<RankedFreelancer> overflow = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new RankedFreelancer((long) i, 100.0 - i, "근거" + i, 0.9))
                .toList();

        // 모집 2명이면 풀은 2 x 3 = 6명까지다.
        List<RankedFreelancer> result = LlmResponseGuard.sanitize(overflow, 2);

        assertThat(result).hasSize(6);
        assertThat(result.get(0).freelancerId()).isEqualTo(1L);
        assertThat(result.get(5).freelancerId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("freelancerId가 없는 후보도 버린다")
    void dropsCandidateWithoutId() {
        List<RankedFreelancer> result = LlmResponseGuard.sanitize(List.of(
                new RankedFreelancer(null, 95.0, "근거", 0.9),
                new RankedFreelancer(1L, 90.0, "근거", 0.8)), 3);

        assertThat(result).extracting(RankedFreelancer::freelancerId).containsExactly(1L);
    }

    @Test
    @DisplayName("정상 응답은 순서 그대로 통과시킨다")
    void keepsHealthyResponseUntouched() {
        List<RankedFreelancer> healthy = List.of(
                new RankedFreelancer(1L, 95.0, "근거1", 0.9),
                new RankedFreelancer(2L, 90.0, "근거2", 0.8));

        assertThat(LlmResponseGuard.sanitize(healthy, 2)).isEqualTo(healthy);
    }
}
