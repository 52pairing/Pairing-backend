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
import com.pairing.matching.application.result.ProjectContent;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link MatchingRequestResponse} 조립. 보는 쪽(클라이언트/프리랜서)에 따라 counterpartName이 달라진다.
 *
 * <p>제목·직무·스킬·경력·근무조건·기간·시작일은 project 도메인이 수정 가능한 항목이라(프로젝트 수정 API,
 * R32 "매칭 중에는 정보 수정 가능하지만 AI 매칭 시 활용하는 정보는 수정 전 정보") 라이브로 읽지 않고
 * 모집 시작 시점에 얼려둔 {@link MatchingSnapshot}(PROJECT/POSITION)에서 읽는다. companyProfile은
 * account 도메인 값이라(프로젝트 수정 범위 밖) 계속 라이브로 읽는다.
 */
@Slf4j
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
                                          LocalDate startDesiredDate, Long budgetAmount, String mainTask,
                                          String currentSituation, String detailScope, String extraNote,
                                          String workLocation, Boolean startNegotiable, Integer periodValue,
                                          PeriodUnit periodUnit) {

        /**
         * 2026-08-15 이전에 얼린 스냅샷인가. 그때는 아래 7개를 담지 않았다.
         *
         * <p>{@code periodValue}로 판별한다. 프로젝트 등록 시 필수라 DB가 NOT NULL이고, 새 스냅샷에는
         * 반드시 값이 있다 — 비어 있다는 건 이 필드를 담기 전에 얼렸다는 뜻이다. 선택 입력인
         * {@code detailScope}로 판별하면 "클라이언트가 안 적은 새 스냅샷"과 구분되지 않는다.
         */
        boolean predatesProjectContent() {
            return periodValue == null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PositionSnapshotPayload(JobRole jobRole, List<SkillCode> requiredSkills, Integer minCareerYears,
                                           Integer totalHeadcount) {
    }

    /** 목록/카드(발송·수락·거절 포함)에 쓴다. 상세 전용 필드는 전부 null이다. */
    MatchingRequestResponse build(MatchingRequest request, Long viewerAccountId) {
        return build(request, viewerAccountId, false);
    }

    /**
     * 페이지 조회 전용 일괄 조립. 행마다 PROJECT/POSITION 스냅샷을 따로 읽던 것을 페이지당
     * 타입별 1번으로 줄인다. 뷰어 계정도 페이지당 1번만 읽는다(행마다 같은 값을 다시 불렀었다).
     *
     * <p>회사 프로필은 projectId 단위로 캐시한다 — 프로젝트로 필터링해 보낸 요청 목록을 볼 때는
     * 페이지 전체가 같은 프로젝트라 사실상 1번만 부른다. 프리랜서 카드·협상 요약은 행마다 값이
     * 대개 다르므로(각 요청이 다른 후보·다른 협상) 아직 그대로 둔다 — 배치하려면 freelancer/
     * negotiation 도메인 쪽에 목록 조회 포트를 새로 추가해야 해서 이번 범위 밖이다.
     */
    List<MatchingRequestResponse> buildList(List<MatchingRequest> requests, Long viewerAccountId) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Account viewer = accountQueryUseCase.getById(viewerAccountId);
        List<Long> positionIds = requests.stream().map(MatchingRequest::getPositionId).distinct().toList();
        Map<Long, ProjectSnapshotPayload> projectSnapshots = readSnapshots(positionIds, SnapshotType.PROJECT,
                ProjectSnapshotPayload.class);
        Map<Long, PositionSnapshotPayload> positionSnapshots = readSnapshots(positionIds, SnapshotType.POSITION,
                PositionSnapshotPayload.class);
        Map<Long, String> companyProfileCache = new HashMap<>();

        return requests.stream()
                .map(request -> build(request, viewer, projectSnapshots, positionSnapshots, companyProfileCache))
                .toList();
    }

    /**
     * 상세 조회({@code GET /requests/{requestId}})에만 쓴다. 담당 업무와 프로젝트 본문 7개를 채운다.
     *
     * <p>프리랜서는 수락하면 곧바로 협상이 시작되므로, 그 전에 프로젝트를 다 보고 판단할 수 있어야
     * 한다(프론트 요청, 2026-08-15).
     */
    MatchingRequestResponse buildDetail(MatchingRequest request, Long viewerAccountId) {
        return build(request, viewerAccountId, true);
    }

    private MatchingRequestResponse build(MatchingRequest request, Long viewerAccountId, boolean detail) {
        Account viewer = accountQueryUseCase.getById(viewerAccountId);
        ProjectSnapshotPayload project = readSnapshot(request.getPositionId(), SnapshotType.PROJECT,
                ProjectSnapshotPayload.class);
        PositionSnapshotPayload position = readSnapshot(request.getPositionId(), SnapshotType.POSITION,
                PositionSnapshotPayload.class);
        String companyProfile = projectDirectoryPort.findCompanyProfile(request.getProjectId());
        return assemble(request, viewer, project, position, companyProfile, detail);
    }

    /** {@link #buildList}가 미리 읽어둔 스냅샷/캐시로 한 행을 조립한다. 목록 전용이라 detail은 항상 false다. */
    private MatchingRequestResponse build(MatchingRequest request, Account viewer,
                                          Map<Long, ProjectSnapshotPayload> projectSnapshots,
                                          Map<Long, PositionSnapshotPayload> positionSnapshots,
                                          Map<Long, String> companyProfileCache) {
        ProjectSnapshotPayload project = requireSnapshot(projectSnapshots, request.getPositionId());
        PositionSnapshotPayload position = requireSnapshot(positionSnapshots, request.getPositionId());
        String companyProfile = companyProfileCache.computeIfAbsent(request.getProjectId(),
                projectDirectoryPort::findCompanyProfile);
        return assemble(request, viewer, project, position, companyProfile, false);
    }

    private MatchingRequestResponse assemble(MatchingRequest request, Account viewer, ProjectSnapshotPayload project,
                                             PositionSnapshotPayload position, String companyProfile,
                                             boolean detail) {
        ProjectContentView content = resolveContent(request.getProjectId(), project, detail);
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
                detail ? project.mainTask() : null,
                content.currentSituation(),
                content.detailScope(),
                content.extraNote(),
                content.workLocation(),
                content.startNegotiable(),
                content.periodValue(),
                content.periodUnit(),
                detail ? position.totalHeadcount() : null,
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

    /** 응답에 실을 프로젝트 본문. 목록에서는 전부 null이라 원시타입을 쓰지 않는다. */
    private record ProjectContentView(String currentSituation, String detailScope, String extraNote,
                                      String workLocation, Boolean startNegotiable, Integer periodValue,
                                      PeriodUnit periodUnit) {

        private static final ProjectContentView EMPTY =
                new ProjectContentView(null, null, null, null, null, null, null);
    }

    /**
     * 얼려둔 값을 쓰되, 없으면 현재 값으로 채운다.
     *
     * <p><b>왜 폴백이 필요한가.</b> {@code saveSnapshotIfAbsent}는 이미 있는 스냅샷을 덮지 않는다.
     * 그래서 이 필드들을 담기 시작해도 <b>이미 모집 중인 프로젝트는 영원히 안 나온다.</b> 배포 시점에
     * 진행 중이던 요청이 전부 빈 화면을 받는 것보다, 현재 값이라도 보여주는 편이 낫다.
     *
     * <p>폴백으로 읽은 값은 얼린 값이 아니라 <b>지금</b> 값이라 R32의 취지에서 살짝 벗어난다. 그래도
     * 이 필드들은 협상 조건이 아니라 설명글이라 판단 근거가 뒤집히지는 않는다. 새로 모집을 시작하는
     * 프로젝트부터는 얼린 값이 나간다.
     *
     * <p>목록에서는 아예 부르지 않는다 — 옛 요청 20건이 페이지마다 프로젝트를 20번 더 읽게 된다.
     */
    private ProjectContentView resolveContent(Long projectId, ProjectSnapshotPayload project, boolean detail) {
        if (!detail) {
            return ProjectContentView.EMPTY;
        }
        if (!project.predatesProjectContent()) {
            return new ProjectContentView(project.currentSituation(), project.detailScope(), project.extraNote(),
                    project.workLocation(), project.startNegotiable(), project.periodValue(), project.periodUnit());
        }
        ProjectContent live = projectDirectoryPort.findProjectContent(projectId);
        log.info("MATCHING_DEBUG java.request.detail.project_content source=LIVE projectId={} reason=snapshot_predates",
                projectId);
        return new ProjectContentView(live.currentSituation(), live.detailScope(), live.extraNote(),
                live.workLocation(), live.startNegotiable(), live.periodValue(), live.periodUnit());
    }

    private <T> T readSnapshot(Long positionId, SnapshotType type, Class<T> payloadType) {
        MatchingSnapshot snapshot = matchingSnapshotRepository.findByPositionIdAndSnapshotType(positionId, type)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.SNAPSHOT_NOT_FOUND));
        return parseSnapshot(snapshot, payloadType);
    }

    /** 여러 포지션의 한 타입 스냅샷을 한 번에 읽어 positionId로 찾아볼 수 있게 맵으로 돌려준다. */
    private <T> Map<Long, T> readSnapshots(List<Long> positionIds, SnapshotType type, Class<T> payloadType) {
        return matchingSnapshotRepository.findAllByPositionIdInAndSnapshotType(positionIds, type).stream()
                .collect(Collectors.toMap(MatchingSnapshot::getPositionId,
                        snapshot -> parseSnapshot(snapshot, payloadType)));
    }

    private <T> T requireSnapshot(Map<Long, T> snapshots, Long positionId) {
        T snapshot = snapshots.get(positionId);
        if (snapshot == null) {
            throw new BusinessException(MatchingErrorCode.SNAPSHOT_NOT_FOUND);
        }
        return snapshot;
    }

    private <T> T parseSnapshot(MatchingSnapshot snapshot, Class<T> payloadType) {
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(), payloadType);
        } catch (JsonProcessingException e) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
    }
}
