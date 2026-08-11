package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.EmbeddingReindexResult;
import com.pairing.matching.application.usecase.EmbeddingReindexUseCase;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 임베딩 일괄 재색인(관리자 전용). 이력서 텍스트/포지션 요구조건 자체는 안 바뀌었으니, 이미 다른
 * 곳에서 쓰는 조립 로직({@link FreelancerEmbeddingTextBuilder}, {@link PositionEmbeddingTextBuilder})을
 * 그대로 재사용해 호출만 다시 한다 — 임베딩 모델을 바꿨을 때 벡터 공간이 달라지는 문제 대응용이다.
 *
 * <p>한 건 실패해도 나머지는 계속 진행한다. 매칭 자신의 DB에 쓰는 게 없어 트랜잭션을 걸 필요가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingReindexService implements EmbeddingReindexUseCase {

    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final MatchingPort matchingPort;
    private final MatchingSnapshotRepository matchingSnapshotRepository;

    /**
     * 컨트롤러가 부르는 진입점. 프록시를 거쳐야 {@code @Async}가 실제로 적용되므로 자기 자신을
     * 호출하지 않고 컨트롤러 → 이 메서드 순서로만 들어온다.
     */
    @Override
    @Async
    public void startReindexAll() {
        log.info("[임베딩 재색인 시작]");
        EmbeddingReindexResult result = reindexAll();
        log.info("[임베딩 재색인 완료] 프리랜서 성공={} 실패={}, 포지션 성공={} 실패={}",
                result.freelancerSuccessCount(), result.freelancerFailCount(),
                result.positionSuccessCount(), result.positionFailCount());
    }

    @Override
    public EmbeddingReindexResult reindexAll() {
        int[] freelancerCounts = reindexFreelancers();
        int[] positionCounts = reindexPositions();
        return new EmbeddingReindexResult(freelancerCounts[0], freelancerCounts[1],
                positionCounts[0], positionCounts[1]);
    }

    private int[] reindexFreelancers() {
        List<Long> freelancerIds = freelancerDirectoryPort.findAllFreelancerIdsWithResume();
        int success = 0;
        int fail = 0;
        for (Long freelancerId : freelancerIds) {
            try {
                var summary = freelancerDirectoryPort.findResumeSummary(freelancerId);
                matchingPort.upsertFreelancerEmbedding(freelancerId, FreelancerEmbeddingTextBuilder.buildText(summary));
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[임베딩 재색인 실패 - 프리랜서] freelancerId={}", freelancerId, e);
            }
        }
        return new int[] {success, fail};
    }

    private int[] reindexPositions() {
        List<MatchingSnapshot> positionSnapshots =
                matchingSnapshotRepository.findAllBySnapshotType(SnapshotType.POSITION);
        int success = 0;
        int fail = 0;
        for (MatchingSnapshot snapshot : positionSnapshots) {
            Long positionId = snapshot.getPositionId();
            try {
                var summary = projectDirectoryPort.findPositionSummary(snapshot.getProjectId(), positionId);
                matchingPort.upsertPositionEmbedding(positionId, PositionEmbeddingTextBuilder.buildText(summary));
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[임베딩 재색인 실패 - 포지션] positionId={}", positionId, e);
            }
        }
        return new int[] {success, fail};
    }
}
