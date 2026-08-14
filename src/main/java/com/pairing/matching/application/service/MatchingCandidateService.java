package com.pairing.matching.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.usecase.MatchingCandidateCommandUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateQueryUseCase;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pairing.matching.presentation.api.response.CandidateListResponse;

@Service
@RequiredArgsConstructor
public class MatchingCandidateService implements MatchingCandidateQueryUseCase, MatchingCandidateCommandUseCase {

    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final CandidateResponseAssembler candidateResponseAssembler;

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
}
