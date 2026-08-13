package com.pairing.contract.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.port.ContractArchivePort;
import com.pairing.contract.application.port.ContractFileReaderPort;
import com.pairing.contract.application.port.ContractPartyReaderPort;
import com.pairing.contract.application.port.ContractPdfPort;
import com.pairing.account.domain.model.BusinessField;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.application.port.ContractSettlementReaderPort;
import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractTab;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.global.infrastructure.s3.S3Settings;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 목록 카드에 새로 실린 값들. 화면이 "김개발 · 프론트엔드", 양측 서명 체크, "결제 필요" 배지를
 * 그리려면 목록 응답만으로 판단할 수 있어야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractSummaryMappingTest {

    private static final Long CONTRACT_ID = 600L;
    private static final Long CLIENT_ACCOUNT_ID = 1000L;
    private static final Long FREELANCER_ACCOUNT_ID = 2000L;

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractProjectReaderPort projectReaderPort;
    @Mock
    private ContractPartyReaderPort partyReaderPort;
    @Mock
    private ContractFileReaderPort fileReaderPort;
    @Mock
    private ContractPdfPort contractPdfPort;
    @Mock
    private ContractArchivePort archivePort;
    @Mock
    private ContractSettlementReaderPort settlementReaderPort;
    @Mock
    private S3Settings s3Settings;

    private ContractQueryService service;
    private Contract contract;

    @BeforeEach
    void setUp() {
        service = new ContractQueryService(contractRepository, projectReaderPort, partyReaderPort,
                fileReaderPort, contractPdfPort, archivePort, settlementReaderPort,
                s3Settings, new ObjectMapper());

        contract = Contract.create(300L, 1L, 10L, 100L, 200L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 6_200_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);

        given(projectReaderPort.findByPositionId(any())).willReturn(
                new ContractProjectReaderPort.ProjectView(
                        "B2B 주문 관리 서비스 리뉴얼", JobRole.FRONTEND, List.of(SkillCode.REACT)));
        given(partyReaderPort.findFreelancerName(any())).willReturn("김개발");
        given(partyReaderPort.findClientSummary(any())).willReturn(
                new ContractPartyReaderPort.ClientSummary(
                        "주식회사 페어링", BusinessField.IT_CONTENTS_AI));

        // 저장 전이라 id 가 아직 없다. Set.of() 는 contains(null) 에서 터지므로 널을 견디는 집합을 쓴다.
        // 실제 목록은 리포지토리가 돌려준 계약이라 id 가 항상 있다.
        given(settlementReaderPort.findPaidDepositContractIds(any())).willReturn(new HashSet<>());
        given(settlementReaderPort.findPayableSettlementIds(any(), any())).willReturn(new HashMap<>());
    }

    private ContractSummary firstSummary() {
        return summaryFor(CLIENT_ACCOUNT_ID);
    }

    private ContractSummary summaryFor(Long viewerAccountId) {
        given(contractRepository.findByParty(any(), any(), any(), any(), any()))
                .willReturn(page(List.of(contract)));

        return service.findMine(viewerAccountId, null, null, null, PageRequest.of(0, 10))
                .getContent().get(0);
    }

    private Page<Contract> page(List<Contract> contracts) {
        return new PageImpl<>(contracts, PageRequest.of(0, 10), contracts.size());
    }

    @Test
    @DisplayName("직무가 함께 내려간다")
    void includesJobRole() {
        // 카드가 "김개발 · 프론트엔드" 로 찍는다. 상세를 열지 않고도 누가 어느 자리인지 알아야 한다.
        assertThat(firstSummary().jobRole()).isEqualTo(JobRole.FRONTEND);
    }

    @Test
    @DisplayName("양측 서명 여부가 각각 내려간다")
    void includesBothSignatureFlags() {
        contract.completeDraft("{}", null);
        contract.sign(FREELANCER_ACCOUNT_ID, "SESSION", null, null, null, null);

        ContractSummary summary = firstSummary();

        assertThat(summary.freelancerSigned()).isTrue();
        assertThat(summary.clientSigned()).isFalse();
        // 뷰어가 클라이언트라 아직 내 차례다.
        assertThat(summary.signatureRequired()).isTrue();
    }

    @Test
    @DisplayName("착수금을 낸 계약만 depositPaid 가 켜진다")
    void marksDepositPaid() {
        assertThat(firstSummary().depositPaid()).isFalse();

        // 정산이 "물어본 계약 전부 결제됐다" 고 답한 경우.
        given(settlementReaderPort.findPaidDepositContractIds(any()))
                .willAnswer(invocation -> new HashSet<Long>(invocation.getArgument(0)));

        assertThat(firstSummary().depositPaid()).isTrue();
    }

    @Test
    @DisplayName("업종은 프리랜서가 볼 때만 채운다 — 클라이언트 화면의 상대는 프리랜서다")
    void fillsBusinessFieldForFreelancerViewerOnly() {
        // 프리랜서 카드가 "주식회사 페어링 · IT/컨텐츠/AI" 로 찍는다.
        ContractSummary freelancerView = summaryFor(FREELANCER_ACCOUNT_ID);
        assertThat(freelancerView.counterpartName()).isEqualTo("주식회사 페어링");
        assertThat(freelancerView.clientBusinessField()).isEqualTo(BusinessField.IT_CONTENTS_AI);

        // 클라이언트가 보면 상대가 프리랜서라 업종을 채울 이유가 없다. 조회도 안 탄다.
        ContractSummary clientView = summaryFor(CLIENT_ACCOUNT_ID);
        assertThat(clientView.counterpartName()).isEqualTo("김개발");
        assertThat(clientView.clientBusinessField()).isNull();
    }

    @Test
    @DisplayName("결제할 정산 ID 가 계약별로 매칭된다")
    void mapsPayableSettlementId() {
        assertThat(firstSummary().payableSettlementId()).isNull();

        // 정산이 "이 계약은 700번을 내면 된다" 고 답한 경우.
        given(settlementReaderPort.findPayableSettlementIds(any(), any()))
                .willAnswer(invocation -> {
                    Map<Long, Long> result = new HashMap<>();
                    ((Collection<Long>) invocation.getArgument(1))
                            .forEach(id -> result.put(id, 700L));
                    return result;
                });

        assertThat(firstSummary().payableSettlementId()).isEqualTo(700L);
    }

    @Test
    @DisplayName("정산 조회는 계약 수와 무관하게 페이지당 한 번만 부른다")
    void asksSettlementOncePerPage() {
        // 계약마다 물으면 페이지 크기만큼 쿼리가 늘어난다. 배지 하나 때문에 그럴 이유가 없다.
        given(contractRepository.findByParty(any(), any(), any(), any(), any()))
                .willReturn(page(List.of(contract, contract, contract)));

        service.findMine(CLIENT_ACCOUNT_ID, null, null, null, PageRequest.of(0, 10));

        verify(settlementReaderPort, times(1)).findPaidDepositContractIds(any());
        verify(settlementReaderPort, times(1)).findPayableSettlementIds(any(), any());
    }

    @Test
    @DisplayName("projectId 와 tab 이 그대로 리포지토리로 넘어간다")
    void passesFiltersThrough() {
        given(contractRepository.findByParty(any(), any(), any(), any(), any()))
                .willReturn(page(List.of()));

        service.findMine(CLIENT_ACCOUNT_ID, 77L, null, ContractTab.AWAITING_ME,
                PageRequest.of(0, 10));

        verify(contractRepository).findByParty(CLIENT_ACCOUNT_ID, 77L, null,
                ContractTab.AWAITING_ME, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("빈 페이지에서도 착수금 조회가 터지지 않는다")
    void handlesEmptyPage() {
        given(contractRepository.findByParty(any(), any(), any(), any(), any()))
                .willReturn(page(List.of()));

        Page<ContractSummary> result =
                service.findMine(CLIENT_ACCOUNT_ID, null, null, null, PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("굳혀둔 계약서가 있으면 다시 그리지 않는다")
    void servesArchivedPdf() {
        // 다시 그리면 조항 문구를 고쳤을 때 이미 체결된 계약서까지 새 양식으로 바뀐다.
        byte[] archived = "%PDF-보관본".getBytes();
        contract.attachPdf(77L);
        givenDetailLoadable();
        given(archivePort.read(77L)).willReturn(Optional.of(archived));

        assertThat(service.renderPdf(CONTRACT_ID, CLIENT_ACCOUNT_ID)).isEqualTo(archived);
        verify(contractPdfPort, never()).render(any());
    }

    @Test
    @DisplayName("굳혀둔 파일을 못 읽으면 그 자리에서 그린다")
    void fallsBackToRendering() {
        // 스토리지가 잠깐 흔들린 것만으로 계약서를 아예 못 보게 되면 안 된다.
        // 체결 전 계약과 이 기능이 생기기 전 계약도 이 경로로 온다.
        byte[] rendered = "%PDF-즉석".getBytes();
        givenDetailLoadable();
        given(archivePort.read(any())).willReturn(Optional.empty());
        given(contractPdfPort.render(any())).willReturn(rendered);

        assertThat(service.renderPdf(CONTRACT_ID, CLIENT_ACCOUNT_ID)).isEqualTo(rendered);
    }

    /** 상세 조회가 통과하도록 최소한만 심는다. */
    private void givenDetailLoadable() {
        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(projectReaderPort.findByPositionId(any())).willReturn(
                new ContractProjectReaderPort.ProjectView("페어링 웹 리뉴얼", JobRole.BACKEND, List.of()));
        given(partyReaderPort.findClient(any())).willReturn(ContractPartyReaderPort.ClientParty.EMPTY);
        given(partyReaderPort.findFreelancer(any()))
                .willReturn(ContractPartyReaderPort.FreelancerParty.EMPTY);
    }
}
