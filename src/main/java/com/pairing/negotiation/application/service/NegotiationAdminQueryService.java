package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.NegotiationAdminReaderPort;
import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort.ProjectView;
import com.pairing.negotiation.application.result.admin.AdminDetail;
import com.pairing.negotiation.application.result.admin.AdminListItem;
import com.pairing.negotiation.application.result.admin.AdminRawLog;
import com.pairing.negotiation.application.result.admin.AdminSummary;
import com.pairing.negotiation.application.usecase.NegotiationAdminQueryUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NegotiationAdminQueryService implements NegotiationAdminQueryUseCase {

    private final NegotiationRepository negotiationRepository;
    private final NegotiationMessageRepository messageRepository;
    private final ProjectReaderPort projectReaderPort;
    private final PartyNameReaderPort partyNameReaderPort;
    private final NegotiationAdminReaderPort adminReaderPort;

    @Override
    public AdminSummary getSummary() {
        return adminReaderPort.loadSummary();
    }

    @Override
    public Page<AdminListItem> search(String keyword, NegotiationStatus status, Pageable pageable) {
        return adminReaderPort.search(keyword, status, pageable);
    }

    @Override
    public AdminDetail getDetail(Long negotiationId) {
        Negotiation negotiation = load(negotiationId);

        ProjectView project = projectReaderPort.findById(negotiation.getProjectId()).orElse(null);
        String title = project != null ? project.title() : null;
        Long clientProfileId = project != null ? project.clientProfileId() : null;
        String clientName = partyNameReaderPort.findClientCompanyName(clientProfileId).orElse(null);
        String freelancerName = partyNameReaderPort.findFreelancerName(negotiation.getFreelancerId()).orElse(null);

        Map<Long, ConditionType> typeById = negotiation.getConditions().stream()
                .collect(Collectors.toMap(NegotiationCondition::getId, NegotiationCondition::getConditionType));

        List<AdminDetail.RoundLog> roundLogs = messageRepository.findByNegotiationId(negotiationId).stream()
                .filter(m -> m.getMessageType() != NegotiationMessageType.SYSTEM)
                .map(m -> toRoundLog(m, typeById.get(m.getConditionId())))
                .toList();

        AdminDetail.FinalResult finalResult = negotiation.getStatus() == NegotiationStatus.AGREED
                ? toFinalResult(negotiation.getConditions())
                : null;

        return new AdminDetail(negotiation.getId(), negotiation.getProjectId(), title, clientName, freelancerName,
                negotiation.getStatus(), negotiation.getStartedAt(), negotiation.getEndedAt(),
                negotiation.getTotalRound(), finalResult, roundLogs);
    }

    @Override
    public List<AdminRawLog> getRawLogs(Long negotiationId) {
        load(negotiationId);   // 존재 검증(NG_001)
        return adminReaderPort.loadRawLogs(negotiationId);
    }

    @Override
    public AdminMessages getMessages(Long negotiationId) {
        Negotiation negotiation = load(negotiationId);   // 존재 검증(NG_001). 관리자는 당사자 검증 없이 전체를 본다.
        Map<Long, ConditionType> typeById = negotiation.getConditions().stream()
                .collect(Collectors.toMap(NegotiationCondition::getId, NegotiationCondition::getConditionType));
        return new AdminMessages(messageRepository.findByNegotiationId(negotiationId), typeById);
    }

    // ----- helpers -----

    private Negotiation load(Long negotiationId) {
        return negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));
    }

    /** 제안/응답 메시지 한 건 → 라운드 로그. 값은 조건 타입에 맞는 필드로 라우팅한다. */
    private AdminDetail.RoundLog toRoundLog(NegotiationMessage m, ConditionType type) {
        boolean proposal = m.getMessageType() == NegotiationMessageType.PROPOSAL;
        String value = m.getProposedValue();

        String amount = type == ConditionType.AMOUNT ? formatAmount(value) : null;
        String period = type == ConditionType.PERIOD ? value : null;
        String workCondition = (type == ConditionType.WORK_STYLE || type == ConditionType.WORK_FORM) ? value : null;
        String workScope = (type == ConditionType.SCOPE || type == ConditionType.OTHER
                || type == ConditionType.START_DATE) ? value : null;

        return new AdminDetail.RoundLog(
                m.getRoundNo(),
                m.getSenderType(),
                m.getSenderType() != null ? m.getSenderType().getLabel() : null,
                proposal ? "제안" : "응답",
                m.getCreatedAt(),
                amount, period, workScope, workCondition,
                proposal ? m.getReason() : null,
                proposal ? null : m.getResponse(),
                null);
    }

    /** 타결 시 합의값에서 최종 결과 라벨을 조립한다. */
    private AdminDetail.FinalResult toFinalResult(List<NegotiationCondition> conditions) {
        Map<ConditionType, String> agreed = conditions.stream()
                .filter(c -> c.getAgreedValue() != null)
                .collect(Collectors.toMap(NegotiationCondition::getConditionType,
                        NegotiationCondition::getAgreedValue, (a, b) -> a));
        return new AdminDetail.FinalResult(
                agreed.containsKey(ConditionType.AMOUNT) ? formatAmount(agreed.get(ConditionType.AMOUNT)) : null,
                agreed.get(ConditionType.PERIOD),
                agreed.get(ConditionType.WORK_STYLE),
                agreed.get(ConditionType.SCOPE));
    }

    /** 금액 문자열 → "월 5,000,000원" 표기. 숫자가 아니면 원문 그대로. */
    private String formatAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long amount = Long.parseLong(raw.trim());
            return "월 " + String.format("%,d", amount) + "원";
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
