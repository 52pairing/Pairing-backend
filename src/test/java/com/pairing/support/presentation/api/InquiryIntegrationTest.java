package com.pairing.support.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import com.pairing.account.infrastructure.persistence.AccountJpaEntity;
import com.pairing.account.infrastructure.persistence.SpringDataAccountRepository;
import com.pairing.account.infrastructure.persistence.SpringDataClientProfileRepository;
import com.pairing.account.infrastructure.persistence.SpringDataFreelancerProfileRepository;
import com.pairing.account.infrastructure.persistence.SpringDataPaymentMethodRepository;
import com.pairing.account.infrastructure.persistence.SpringDataSocialAccountRepository;
import com.pairing.auth.application.port.AccountSuspensionPort;
import com.pairing.auth.application.port.EmailSendLimitPort;
import com.pairing.auth.application.port.LoginAttemptPort;
import com.pairing.auth.application.port.MailSenderPort;
import com.pairing.auth.application.port.OAuthStatePort;
import com.pairing.auth.application.port.PasswordResetTokenPort;
import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.auth.application.port.SignUpTicketPort;
import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.global.port.out.FileStoragePort;
import com.pairing.support.infrastructure.persistence.SpringDataInquiryRepository;
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1:1 문의 등록 -> 내 목록/상세, 관리자 목록/답변, 첨부파일 반영을 실제 요청으로 확인한다.
 *
 * <p>챗봇(R44)은 이번 범위가 아니라 검증하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InquiryIntegrationTest {

    private static final String WRITER_EMAIL = "inquiry-writer@pairing.com";
    private static final String OTHER_EMAIL = "inquiry-other@pairing.com";
    private static final String ADMIN_EMAIL = "inquiry-admin@pairing.com";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpringDataTermsRepository termsRepository;
    @Autowired
    private SpringDataTermsAgreementRepository termsAgreementRepository;
    @Autowired
    private SpringDataAccountRepository accountRepository;
    @Autowired
    private SpringDataClientProfileRepository clientProfileRepository;
    @Autowired
    private SpringDataFreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private SpringDataPaymentMethodRepository paymentMethodRepository;
    @Autowired
    private SpringDataSocialAccountRepository socialAccountRepository;
    @Autowired
    private SpringDataInquiryRepository inquiryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private VerifiedMarkerPort verifiedMarkerPort;
    @MockitoBean
    private TokenStorePort tokenStorePort;
    @MockitoBean
    private SessionRegistryPort sessionRegistryPort;
    @MockitoBean
    private LoginAttemptPort loginAttemptPort;
    @MockitoBean
    private AccountSuspensionPort accountSuspensionPort;
    @MockitoBean
    private EmailSendLimitPort emailSendLimitPort;
    @MockitoBean
    private MailSenderPort mailSenderPort;
    @MockitoBean
    private SignUpTicketPort signUpTicketPort;
    @MockitoBean
    private OAuthStatePort oAuthStatePort;
    @MockitoBean
    private PasswordResetTokenPort passwordResetTokenPort;
    @MockitoBean
    private FileStoragePort fileStoragePort;

    private Long clientTermsId;
    private Long freelancerTermsId;
    private Long privacyTermsId;
    private Long marketingTermsId;
    private Cookie writerAccessToken;
    private Cookie otherAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        inquiryRepository.deleteAll();
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        clientProfileRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();

        clientTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "CLIENT");
        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);
        given(fileStoragePort.uploadFile(any(org.springframework.web.multipart.MultipartFile.class), anyString()))
                .willReturn("inquiry_attachment/test-object-key.png");

        writerAccessToken = signUpAndLoginFreelancer(WRITER_EMAIL, "이프리", "010-3333-4444", "110-123-456789");
        otherAccessToken = signUpAndLoginFreelancer(OTHER_EMAIL, "김다른", "010-5555-6666", "110-987-654321");
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private Cookie signUpAndLoginFreelancer(String email, String name, String phone, String bankAccountNo)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("phone", phone);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", bankAccountNo, "accountHolder", name));
        body.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"FREELANCER"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return loginResult.getResponse().getCookie("accessToken");
    }

    private Cookie signUpAndLoginClient(String email, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", "서울 강남구 테헤란로 1");
        body.put("email", email);
        body.put("name", name);
        body.put("phone", "010-7777-8888");
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-111-222333", "accountHolder", name));
        body.put("agreements", List.of(
                Map.of("termsId", clientTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", false)));

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"CLIENT"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return loginResult.getResponse().getCookie("accessToken");
    }

    /** 관리자는 공개 가입이 없어 계정을 직접 심고 일반 로그인으로 토큰을 받는다. */
    private Cookie loginAsAdmin() throws Exception {
        accountRepository.save(new AccountJpaEntity(null, ADMIN_EMAIL, passwordEncoder.encode(PASSWORD),
                Role.ADMIN, "관리자", "010-9999-0000", SignupType.EMAIL, AccountStatus.ACTIVE, true, 0,
                null, false, null, null, null, null, null, null, null, null, null, null));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"ADMIN"}"""
                                .formatted(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return loginResult.getResponse().getCookie("accessToken");
    }

    private Map<String, Object> inquiryCreateBody(List<Long> fileIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "착수금 수수료 결제 문의");
        body.put("content", "착수금 수수료 결제 버튼이 활성화되지 않습니다.");
        if (fileIds != null) {
            body.put("fileIds", fileIds);
        }
        return body;
    }

    @Test
    @DisplayName("문의를 등록하면 대기중 상태로 내 목록에 반영된다")
    void createInquiryReflectsInMineList() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.writerName").doesNotExist())
                .andExpect(jsonPath("$.data.inquiryNo", org.hamcrest.Matchers.matchesPattern("QNA-\\d{8}-\\d{4}")))
                .andReturn();

        String inquiryNo = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("inquiryNo").asText();

        mockMvc.perform(get("/api/v1/support/inquiries/mine").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].inquiryNo").value(inquiryNo));
    }

    @Test
    @DisplayName("상태로 필터링하면 대기중/답변완료가 구분되어 조회된다")
    void findMyInquiriesFiltersByStatus() throws Exception {
        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/support/inquiries/mine").param("status", "PENDING").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/support/inquiries/mine").param("status", "ANSWERED").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("본인이 아니면 문의 상세를 볼 수 없지만 관리자는 볼 수 있다")
    void findOneRestrictsToWriterOrAdmin() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long inquiryId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("inquiryId").asLong();

        mockMvc.perform(get("/api/v1/support/inquiries/" + inquiryId).cookie(writerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/support/inquiries/" + inquiryId).cookie(otherAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("IQ_002"));

        Cookie adminAccessToken = loginAsAdmin();
        mockMvc.perform(get("/api/v1/support/inquiries/" + inquiryId).cookie(adminAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("존재하지 않는 문의를 조회하면 404다")
    void unknownInquiryReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/support/inquiries/999999").cookie(writerAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("IQ_001"));
    }

    @Test
    @DisplayName("관리자 목록·상세에는 작성자 정보가 채워지고, 재답변도 반영된다")
    void adminSeesWriterInfoAndCanReAnswer() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long inquiryId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("inquiryId").asLong();

        Cookie adminAccessToken = loginAsAdmin();

        mockMvc.perform(get("/api/v1/support/admin/inquiries").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].writerName").value("이프리"))
                .andExpect(jsonPath("$.data.content[0].writerRole").value("FREELANCER"))
                .andExpect(jsonPath("$.data.content[0].writerEmail").value(WRITER_EMAIL));

        mockMvc.perform(get("/api/v1/support/inquiries/" + inquiryId).cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.writerName").value("이프리"))
                .andExpect(jsonPath("$.data.writerRole").value("FREELANCER"));

        mockMvc.perform(post("/api/v1/support/admin/inquiries/" + inquiryId + "/answer")
                        .cookie(adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer":"결제 버튼은 검수 완료 후 활성화됩니다."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer").value("결제 버튼은 검수 완료 후 활성화됩니다."))
                .andExpect(jsonPath("$.data.answererName").value("페어링 고객지원"));

        mockMvc.perform(get("/api/v1/support/inquiries/" + inquiryId).cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer").value("결제 버튼은 검수 완료 후 활성화됩니다."))
                .andExpect(jsonPath("$.data.writerName").doesNotExist());

        // 답변은 정책상 불변이 아니라 다시 등록(수정)할 수 있다.
        mockMvc.perform(post("/api/v1/support/admin/inquiries/" + inquiryId + "/answer")
                        .cookie(adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer":"결제수단을 다시 확인해주세요. 카드 유효기간이 만료됐습니다."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("결제수단을 다시 확인해주세요. 카드 유효기간이 만료됐습니다."));
    }

    @Test
    @DisplayName("관리자 요약 카드는 전체/대기/완료/오늘 접수 건수를 반환한다")
    void adminSummaryReflectsCounts() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long inquiryId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("inquiryId").asLong();

        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated());

        Cookie adminAccessToken = loginAsAdmin();

        mockMvc.perform(get("/api/v1/support/admin/inquiries/summary").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.todayCount").value(2));

        mockMvc.perform(post("/api/v1/support/admin/inquiries/" + inquiryId + "/answer")
                        .cookie(adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer":"확인했습니다."}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/support/admin/inquiries/summary").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.answeredCount").value(1));
    }

    @Test
    @DisplayName("관리자 목록은 회원명·제목·문의번호 키워드로 검색된다")
    void adminListSearchesByKeyword() throws Exception {
        MvcResult writerInquiry = mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated())
                .andReturn();
        var writerInquiryData = objectMapper.readTree(writerInquiry.getResponse().getContentAsString()).path("data");
        Long writerInquiryId = writerInquiryData.path("inquiryId").asLong();
        String writerInquiryNo = writerInquiryData.path("inquiryNo").asText();

        Map<String, Object> otherBody = new LinkedHashMap<>();
        otherBody.put("title", "프리랜서 프로필 수정 내용이 반영되지 않아요");
        otherBody.put("content", "포트폴리오 항목을 수정했는데 반영이 안 됩니다.");
        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherBody)))
                .andExpect(status().isCreated());

        Cookie adminAccessToken = loginAsAdmin();

        // 작성자 이름으로 검색
        mockMvc.perform(get("/api/v1/support/admin/inquiries").param("keyword", "김다른").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].writerName").value("김다른"));

        // 제목으로 검색
        mockMvc.perform(get("/api/v1/support/admin/inquiries").param("keyword", "결제").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(writerInquiryId));

        // 문의번호로 검색 (화면에 표시된 형식 그대로 붙여넣는 경우)
        mockMvc.perform(get("/api/v1/support/admin/inquiries").param("keyword", writerInquiryNo)
                        .cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(writerInquiryId));

        // 일치하는 게 없으면 빈 목록
        mockMvc.perform(get("/api/v1/support/admin/inquiries").param("keyword", "존재하지않는검색어")
                        .cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("관리자 목록은 회원유형으로도 필터링된다")
    void adminListFiltersByWriterRole() throws Exception {
        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(null))))
                .andExpect(status().isCreated());

        Cookie clientAccessToken = signUpAndLoginClient("inquiry-client@pairing.com", "김클라");
        Map<String, Object> clientBody = new LinkedHashMap<>();
        clientBody.put("title", "기업정보 수정 문의");
        clientBody.put("content", "사업자등록번호가 잘못 등록됐습니다.");
        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientBody)))
                .andExpect(status().isCreated());

        Cookie adminAccessToken = loginAsAdmin();

        mockMvc.perform(get("/api/v1/support/admin/inquiries").param("writerRole", "CLIENT")
                        .cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].writerRole").value("CLIENT"));

        mockMvc.perform(get("/api/v1/support/admin/inquiries").param("writerRole", "FREELANCER")
                        .cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].writerRole").value("FREELANCER"));

        mockMvc.perform(get("/api/v1/support/admin/inquiries").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("첨부파일을 함께 등록하면 응답에 원본 파일명과 다운로드 경로가 반영된다")
    void createInquiryWithAttachmentResolvesFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "오류화면.png", "image/png",
                "fake-image-bytes".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .param("purpose", "INQUIRY_ATTACHMENT")
                        .cookie(writerAccessToken))
                .andExpect(status().isCreated())
                .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data").path("fileId").asLong();

        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(List.of(fileId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.files.length()").value(1))
                .andExpect(jsonPath("$.data.files[0].fileId").value(fileId))
                .andExpect(jsonPath("$.data.files[0].originalName").value("오류화면.png"));
    }

    @Test
    @DisplayName("없는 fileId 를 첨부하면 400 으로 끊긴다")
    void createInquiryWithUnknownFileIsRejected() throws Exception {
        // 검증하지 않으면 inquiry_file 의 FK 에서 걸려 500 이 난다. 잘못 보낸 요청이므로 400 이어야 한다.
        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(List.of(999_999L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IQ_004"));
    }

    @Test
    @DisplayName("남이 올린 파일은 내 문의에 첨부할 수 없다")
    void createInquiryWithOthersFileIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "남의파일.png", "image/png",
                "fake-image-bytes".getBytes());

        // 관리자가 올린 파일 id 를 문의 작성자가 자기 문의에 붙이려 한다.
        Cookie adminAccessToken = loginAsAdmin();
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .param("purpose", "INQUIRY_ATTACHMENT")
                        .cookie(adminAccessToken))
                .andExpect(status().isCreated())
                .andReturn();
        Long othersFileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data").path("fileId").asLong();

        mockMvc.perform(post("/api/v1/support/inquiries")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inquiryCreateBody(List.of(othersFileId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IQ_004"));
    }
}
