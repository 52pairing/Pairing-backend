package com.pairing.matching.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.usecase.MatchingCandidateCommandUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateQueryUseCase;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
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
    private final CandidateResponseAssembler candidateResponseAssembler;

    @Override
    @Transactional(readOnly = true)
    public CandidateListResponse findCandidates(Long positionId, Long accountId) {
        MatchingRound round = matchingRoundRepository.findLatestByPositionId(positionId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ROUND_NOT_FOUND));
        return candidateResponseAssembler.build(round, accountId);
    }

    @Override
    @Transactional
    public CandidateListResponse rejectCandidate(Long candidateId, Long accountId) {
        MatchingCandidate candidate = matchingCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.CANDIDATE_NOT_FOUND));

        candidate.reject();
        matchingCandidateRepository.save(candidate);

        MatchingRound round = matchingRoundRepository.findById(candidate.getRoundId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ROUND_NOT_FOUND));
        return candidateResponseAssembler.build(round, accountId);
    }
}
