package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.EmbeddingReindexResult;
import com.pairing.matching.application.usecase.EmbeddingReindexUseCase;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
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
    private final MatchingRoundRepository matchingRoundRepository;
    private final FreelancerEmbeddingRefresher freelancerEmbeddingRefresher;

    /** 진행률 로그 주기(건). 1600건 규모에서 16줄이면 위치 파악에 충분하고 로그를 덮지 않는다. */
    private static final int PROGRESS_LOG_INTERVAL = 100;

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
        // 대상 수를 **시작할 때** 남긴다. 끝에만 찍으면 도중에 죽었을 때(재배포로 컨테이너가 교체되면
        // 이 비동기 루프는 아무 흔적 없이 사라진다) 몇 명을 처리하려던 것인지조차 알 수 없다.
        log.info("MATCHING_DEBUG java.reindex.start target=freelancer targets={}", freelancerIds.size());

        int processed = 0;
        int success = 0;
        int fail = 0;
        int skippedBlank = 0;
        for (Long freelancerId : freelancerIds) {
            try {
                // 이력서·조건 저장 경로와 같은 조립을 써야 한다. 여기서만 따로 만들면 재색인 전후로
                // 같은 사람의 벡터가 달라진다.
                if (freelancerEmbeddingRefresher.refreshByFreelancerId(freelancerId)) {
                    success++;
                } else {
                    skippedBlank++;
                }
            } catch (Exception e) {
                fail++;
                log.warn("[임베딩 재색인 실패 - 프리랜서] freelancerId={}", freelancerId, e);
            }
            processed++;
            // 진행률을 주기적으로 남긴다. 루프가 중간에 죽으면 요약 로그가 아예 안 찍히므로,
            // "어디까지 갔나"는 이 줄로만 알 수 있다.
            if (processed % PROGRESS_LOG_INTERVAL == 0) {
                log.info("MATCHING_DEBUG java.reindex.progress target=freelancer processed={}/{} "
                                + "succeeded={} failed={} skipped_blank={} last_freelancer_id={}",
                        processed, freelancerIds.size(), success, fail, skippedBlank, freelancerId);
            }
        }

        log.info("MATCHING_DEBUG java.reindex.summary target=freelancer targets={} processed={} "
                        + "succeeded={} failed={} skipped_blank={}",
                freelancerIds.size(), processed, success, fail, skippedBlank);
        return new int[] {success, fail};
    }

    private int[] reindexPositions() {
        List<MatchingRound> rounds = matchingRoundRepository.findLatestRoundsByDistinctPosition();
        log.info("MATCHING_DEBUG java.reindex.start target=position targets={}", rounds.size());

        int processed = 0;
        int success = 0;
        int fail = 0;
        for (MatchingRound round : rounds) {
            Long positionId = round.getPositionId();
            try {
                var summary = projectDirectoryPort.findPositionSummary(round.getProjectId(), positionId);
                matchingPort.upsertPositionEmbedding(positionId, PositionEmbeddingTextBuilder.buildText(summary));
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[임베딩 재색인 실패 - 포지션] positionId={}", positionId, e);
            }
            processed++;
            if (processed % PROGRESS_LOG_INTERVAL == 0) {
                log.info("MATCHING_DEBUG java.reindex.progress target=position processed={}/{} "
                                + "succeeded={} failed={} last_position_id={}",
                        processed, rounds.size(), success, fail, positionId);
            }
        }

        log.info("MATCHING_DEBUG java.reindex.summary target=position targets={} processed={} "
                        + "succeeded={} failed={}",
                rounds.size(), processed, success, fail);
        return new int[] {success, fail};
    }
}
