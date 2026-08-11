package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.project.application.event.ProjectUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 모집 시작 후 프로젝트 정보가 수정됐다는 신호({@link ProjectUpdatedEvent}, project 도메인이
 * 수정 커밋 후 발행)를 받아 포지션별 임베딩을 다시 올린다.
 *
 * <p>매칭 요청 카드({@link MatchingRequestResponseAssembler})가 쓰는 {@code MatchingSnapshot}은
 * 여기서 건드리지 않는다 — 그건 최초 모집 시작 시점에 고정해서 계속 써야 하는 값이라(R32),
 * 프로젝트가 수정돼도 그대로 유지해야 한다. 반대로 AI 추천 검색용 임베딩(pgvector)은 텍스트가
 * 바뀌면 낡으므로 최신 상태로 갱신해야 정확도가 유지된다 — 둘은 서로 다른 목적이라 별도로 관리한다.
 *
 * <p>수정 트랜잭션이 이미 커밋된 뒤에 도착하므로 AFTER_COMMIT에서 받는다. 포지션 하나가
 * 실패해도(AI 서버 문제 등) 같은 프로젝트의 다른 포지션 처리는 계속돼야 해서 포지션 단위로
 * 예외를 잡는다(실패해도 프로젝트 수정 자체는 이미 커밋된 뒤라 영향 없음).
 *
 * <p><b>{@code @Async}인 이유.</b> AFTER_COMMIT 리스너는 커밋한 스레드에서 그대로 이어 실행되므로,
 * 이대로 두면 <b>클라이언트의 프로젝트 수정 API 응답이 포지션 수만큼의 임베딩 생성을 다 기다린다</b>.
 * 임베딩 갱신은 수정 결과와 무관하게 뒤에서 처리하면 되는 값이라 별도 스레드로 넘긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectUpdatedEventListener {

    private final ProjectDirectoryPort projectDirectoryPort;
    private final MatchingPort matchingPort;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectUpdated(ProjectUpdatedEvent event) {
        List<Long> positionIds = projectDirectoryPort.findPositionIds(event.projectId());
        for (Long positionId : positionIds) {
            try {
                refreshEmbedding(event.projectId(), positionId);
            } catch (Exception e) {
                log.error("[프로젝트 수정 → 임베딩 재생성 실패] projectId={}, positionId={}",
                        event.projectId(), positionId, e);
            }
        }
    }

    private void refreshEmbedding(Long projectId, Long positionId) {
        ProjectPositionSummary summary = projectDirectoryPort.findPositionSummary(projectId, positionId);
        matchingPort.upsertPositionEmbedding(positionId, PositionEmbeddingTextBuilder.buildText(summary));
    }
}
