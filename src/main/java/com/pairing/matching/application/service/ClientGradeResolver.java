package com.pairing.matching.application.service;

import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 프로젝트를 등록한 클라이언트의 등급을 조회한다. 예산 계산(수수료 할인)과 매칭 점수 가중치가 같이 쓴다.
 *
 * <p>등급별 매칭 가중치(%)는 명세/정책에 숫자가 없어 수수료 할인 패턴(1%/2%)을 그대로 재사용해
 * 실버 0% / 골드 1% / 다이아 2%로 확정했다(.ai/STATE.md "확정된 설계 결정 4-1").
 */
@Component
@RequiredArgsConstructor
class ClientGradeResolver {

    private static final Map<ClientGrade, Double> MATCHING_WEIGHT_PERCENT = Map.of(
            ClientGrade.SILVER, 0.0,
            ClientGrade.GOLD, 1.0,
            ClientGrade.DIAMOND, 2.0
    );

    private final ProjectDirectoryPort projectDirectoryPort;
    private final ClientProfileRepository clientProfileRepository;

    ClientGrade resolve(Long projectId) {
        Long clientAccountId = projectDirectoryPort.findClientAccountId(projectId);
        return clientProfileRepository.findByAccountId(clientAccountId)
                .map(ClientProfile::getGrade)
                .map(ClientGrade::valueOf)
                .orElse(ClientGrade.SILVER);
    }

    double resolveMatchingWeightPercent(Long projectId) {
        return MATCHING_WEIGHT_PERCENT.getOrDefault(resolve(projectId), 0.0);
    }
}
