package com.pairing.matching.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.application.result.NegotiationSummary;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link MatchingRequestResponse} 조립. 보는 쪽(클라이언트/프리랜서)에 따라 counterpartName이 달라진다.
 *
 * <p>제목·직무·스킬·경력·근무조건·기간·시작일은 project 도메인이 수정 가능한 항목이라(프로젝트 수정 API,
 * R32 "매칭 중에는 정보 수정 가능하지만 AI 매칭 시 활용하는 정보는 수정 전 정보") 라이브로 읽지 않고
 * 모집 시작 시점에 얼려둔 {@link MatchingSnapshot}(PROJECT/POSITION)에서 읽는다. companyProfile은
 * account 도메인 값이라(프로젝트 수정 범위 밖) 계속 라이브로 읽는다.
 */
@Component
@RequiredArgsConstructor
class MatchingRequestResponseAssembler {

    private final AccountQueryUseCase accountQueryUseCase;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final NegotiationPort negotiationPort;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final ObjectMapper objectMapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProjectSnapshotPayload(String title, String companyName, String workLabel, String periodLabel,
                                          LocalDate startDesiredDate, Long budgetAmount, String mainTask) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PositionSnapshotPayload(JobRole jobRole, List<SkillCode> requiredSkills, Integer minCareerYears) {
    }

    /** 목록/카드(발송·수락·거절 포함)에 쓴다. {@code mainTask}는 상세 전용이라 항상 null이다. */
    MatchingRequestResponse build(MatchingRequest request, Long viewerAccountId) {
        return build(request, viewerAccountId, false);
    }

    /** 상세 조회({@code GET /requests/{requestId}})에만 쓴다. {@code mainTask}를 채워서 돌려준다. */
    MatchingRequestResponse buildDetail(MatchingRequest request, Long viewerAccountId) {
        return build(request, viewerAccountId, true);
    }

    private MatchingRequestResponse build(MatchingRequest request, Long viewerAccountId, boolean includeMainTask) {
        Account viewer = accountQueryUseCase.getById(viewerAccountId);
        ProjectSnapshotPayload project = readSnapshot(request.getPositionId(), SnapshotType.PROJECT,
                ProjectSnapshotPayload.class);
        PositionSnapshotPayload position = readSnapshot(request.getPositionId(), SnapshotType.POSITION,
                PositionSnapshotPayload.class);
        String companyProfile = projectDirectoryPort.findCompanyProfile(request.getProjectId());
        FreelancerCardSummary freelancer = freelancerDirectoryPort.findCardSummary(request.getFreelancerId());
        String counterpartName = viewer.getRole() == Role.CLIENT ? freelancer.name() : project.companyName();

        NegotiationSummary negotiation = negotiationPort.findSummaryByRequestId(request.getId()).orElse(null);

        return new MatchingRequestResponse(
                request.getId(),
                request.getProjectId(),
                project.title(),
                request.getPositionId(),
                position.jobRole(),
                counterpartName,
                project.companyName(),
                companyProfile,
                position.requiredSkills(),
                position.minCareerYears(),
                project.workLabel(),
                project.periodLabel(),
                project.startDesiredDate(),
                request.getStatus(),
                project.budgetAmount(),
                includeMainTask ? project.mainTask() : null,
                request.getRequestedAt(),
                request.getExpiresAt(),
                request.getRespondedAt(),
                request.getRejectReason(),
                negotiation != null ? negotiation.currentRound() : null,
                negotiation != null ? negotiation.maxRound() : null,
                negotiation != null ? negotiation.newProposalCount() : null,
                negotiation != null ? negotiation.negotiationId() : null
        );
    }

    private <T> T readSnapshot(Long positionId, SnapshotType type, Class<T> payloadType) {
        MatchingSnapshot snapshot = matchingSnapshotRepository.findByPositionIdAndSnapshotType(positionId, type)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.SNAPSHOT_NOT_FOUND));
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(), payloadType);
        } catch (JsonProcessingException e) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
    }
}
