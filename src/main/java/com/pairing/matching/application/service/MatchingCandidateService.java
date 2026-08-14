package com.pairing.matching.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.usecase.MatchingCandidateCommandUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateQueryUseCase;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pairing.matching.presentation.api.response.CandidateListResponse;
import com.pairing.matching.presentation.api.response.CandidateProfileSnapshotResponse;

import java.util.function.Supplier;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingCandidateService implements MatchingCandidateQueryUseCase, MatchingCandidateCommandUseCase {

    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final CandidateResponseAssembler candidateResponseAssembler;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public CandidateListResponse findCandidates(Long positionId, Long accountId) {
        // 라운드가 없는 건 에러가 아니라 **아직 만들어지는 중**이다. 최초 추천은 착수금 결제(모집 시작)
        // 이벤트를 받아 비동기로 돌고 LLM 호출까지 포함해 수 초~수십 초가 걸린다. 결제 직후 추천 후보
        // 탭을 열면 라운드가 없는 게 정상인데, 예전엔 여기서 MT_001 을 404로 던져 화면에 빨간 에러가
        // 뜨고 "다시 시도"를 눌러야 후보가 보였다.
        return matchingRoundRepository.findLatestByPositionId(positionId)
                .map(round -> candidateResponseAssembler.build(round, accountId))
                .orElseGet(() -> preparingResponse(positionId, accountId));
    }

    /**
     * 라운드가 아직 없을 때의 응답. 소유자 확인은 그대로 한다 — 평소엔 라운드에서 projectId 를 얻지만
     * 라운드가 없으므로 포지션에서 프로젝트를 거슬러 올라간다.
     */
    private CandidateListResponse preparingResponse(Long positionId, Long accountId) {
        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(positionId);
        if (!projectDirectoryPort.isOwnedByAccount(position.projectId(), accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        return candidateResponseAssembler.buildPreparing(positionId, position.projectId(), position.headcount());
    }

    @Override
    @Transactional
    public CandidateListResponse rejectCandidate(Long candidateId, Long accountId) {
        MatchingCandidate candidate = matchingCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.CANDIDATE_NOT_FOUND));

        // 요청을 보낸 후보는 거절할 수 없다. 후보 카드 상태는 **요청 보냄 / 거절함 / 아무것도 안 함**
        // 셋 중 하나여야 한다(2026-08-13 확정). 둘 다 걸리면 화면 표시와 서버 판정이 어긋나기 쉽고
        // (거절이 우선인지 요청이 우선인지), 이미 보낸 요청은 프리랜서 응답을 기다리는 중이라
        // 클라이언트가 후보를 내려도 그 요청이 없어지지 않는다.
        if (matchingRequestRepository.existsByCandidateId(candidateId)) {
            throw new BusinessException(MatchingErrorCode.CANDIDATE_ALREADY_REQUESTED);
        }

        candidate.reject();
        matchingCandidateRepository.save(candidate);

        // 거절한 후보가 속한 회차가 아니라 **포지션의 최신 회차**로 조립한다. 목록이 회차를 넘어
        // 누적되므로, 옛 회차 후보를 거절했다고 머리말(회차 번호·재추천 가능 여부)이 과거로
        // 되돌아가면 안 된다 - 화면이 "1회차"로 표시되면서 재추천 버튼 상태까지 어긋난다.
        MatchingRound round = matchingRoundRepository.findLatestByPositionId(candidate.getPositionId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ROUND_NOT_FOUND));
        return candidateResponseAssembler.build(round, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateProfileSnapshotResponse findCandidateProfile(Long candidateId, Long accountId) {
        MatchingCandidate candidate = matchingCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.CANDIDATE_NOT_FOUND));
        MatchingRound round = matchingRoundRepository.findById(candidate.getRoundId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ROUND_NOT_FOUND));
        if (!projectDirectoryPort.isOwnedByAccount(round.getProjectId(), accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }

        CandidateProfileSnapshotResponse.SnapshotPayload payload = matchingSnapshotRepository
                .findByFreelancerIdAndPositionIdAndSnapshotType(candidate.getFreelancerId(),
                        candidate.getPositionId(), SnapshotType.FREELANCER)
                .map(this::readSnapshot)
                .filter(this::hasProfilePayload)
                .orElseGet(() -> liveFallbackPayload(candidate.getFreelancerId()));
        boolean requested = matchingRequestRepository.existsByCandidateId(candidate.getId());

        return new CandidateProfileSnapshotResponse(
                candidate.getId(),
                round.getProjectId(),
                candidate.getPositionId(),
                candidate.getFreelancerId(),
                payload.card().name(),
                payload.card().profileImageUrl(),
                payload.card().grade().name(),
                payload.card().ratingAverage(),
                payload.card().reviewCount(),
                candidate.getFitScore(),
                CandidateResponseAssembler.splitFitReasons(candidate.getFitReason()),
                candidate.getRankNo(),
                requested,
                candidate.isRejected(),
                payload.capturedAt(),
                payload.condition(),
                payload.resume()
        );
    }

    private boolean hasProfilePayload(CandidateProfileSnapshotResponse.SnapshotPayload payload) {
        return payload.card() != null && payload.condition() != null && payload.resume() != null;
    }

    /**
     * 스냅샷이 없을 때 <b>현재</b> 프로필로 대체한다. 이 코드가 배포되기 전에 이미 노출됐던 후보는
     * 스냅샷이 없으므로, 없으면 화면이 아예 안 열린다.
     *
     * <p><b>조건·이력서는 없으면 null 로 둔다.</b> 스냅샷 캡처가 실패하는 이유가 대개 "이력서가
     * 없다"인데({@code findResume}가 MT_015를 던진다), 폴백도 같은 것을 읽으므로 그대로 두면
     * <b>같은 이유로 또 실패해 프로필이 영영 안 열린다</b>. 카드 정보(이름·등급·평점)만 있으면
     * 화면은 그릴 수 있고, 프론트 타입도 두 필드를 nullable 로 받는다.
     *
     * <p>{@code capturedAt}은 지금 시각이다 — 얼린 값이 아니라 현재 값이라는 표시다.
     */
    private CandidateProfileSnapshotResponse.SnapshotPayload liveFallbackPayload(Long freelancerId) {
        return new CandidateProfileSnapshotResponse.SnapshotPayload(
                freelancerDirectoryPort.findCardSummary(freelancerId),
                findOrNull(() -> freelancerDirectoryPort.findCondition(freelancerId), freelancerId, "condition"),
                findOrNull(() -> freelancerDirectoryPort.findResume(freelancerId), freelancerId, "resume"),
                LocalDateTime.now()
        );
    }

    private <T> T findOrNull(Supplier<T> lookup, Long freelancerId, String what) {
        try {
            return lookup.get();
        } catch (Exception e) {
            log.warn("MATCHING_DEBUG java.candidate.profile.live_fallback_partial freelancerId={} missing={} cause={}",
                    freelancerId, what, e.getMessage());
            return null;
        }
    }

    private CandidateProfileSnapshotResponse.SnapshotPayload readSnapshot(MatchingSnapshot snapshot) {
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(),
                    CandidateProfileSnapshotResponse.SnapshotPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
    }
}
