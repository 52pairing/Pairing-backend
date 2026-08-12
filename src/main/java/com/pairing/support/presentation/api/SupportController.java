package com.pairing.support.presentation.api;

import com.pairing.account.domain.model.Role;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.support.application.usecase.ChatbotUseCase;
import com.pairing.support.application.usecase.InquiryUseCase;
import com.pairing.support.domain.model.InquiryStatus;
import com.pairing.support.exception.ChatbotErrorCode;
import com.pairing.support.exception.InquiryErrorCode;
import com.pairing.support.presentation.api.request.ChatbotAskRequest;
import com.pairing.support.presentation.api.request.InquiryCreateRequest;
import com.pairing.support.presentation.api.response.ChatbotAnswerResponse;
import com.pairing.support.presentation.api.response.ChatbotQuotaResponse;
import com.pairing.support.presentation.api.response.InquiryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FAQ 챗봇과 1:1 문의. (요구사항 R44, R45)
 *
 * <p>둘은 서로 독립된 창구다. 챗봇을 거쳐야 문의할 수 있는 구조가 아니라 사용자가 원하는 쪽을 고른다.
 * 챗봇은 하루 10회로 제한되고, LLM 호출은 AI 서버(Pairing-python)가 담당한다.
 *
 * <p>추천 질문 목록만 아직 고정 문구다(README 기준 초기엔 고정 문구로 두어도 된다).
 * <p>1:1 문의는 실제 로직으로 연결되어 있다. 챗봇(R44)은 AI 서버 연동 전까지 스켈레톤 고정 응답을 유지한다.
 */
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "18. Support", description = "챗봇/1:1 문의 API")
public class SupportController {

    private final ChatbotUseCase chatbotUseCase;
    private final InquiryUseCase inquiryUseCase;

    // ==========================================
    // 챗봇 (R44)
    // ==========================================

    @PostMapping("/chatbot/questions")
    @Operation(summary = "챗봇 질의",
            description = "FAQ·이용안내·정책 범위에서 답합니다. 하루 10회를 넘기면 429 로 막힙니다.")
    @ApiErrorCodeExample(domain = ChatbotErrorCode.class,
            value = {"SESSION_NOT_FOUND", "SESSION_FORBIDDEN", "QUOTA_EXCEEDED", "AI_SERVER_CALL_FAILED"})
    public ResponseEntity<ApiResponse<ChatbotAnswerResponse>> ask(
            @Valid @RequestBody ChatbotAskRequest request,
            @CurrentAccountId Long accountId
    ) {
        ChatbotAnswerResponse data = ChatbotAnswerResponse.from(
                chatbotUseCase.ask(request.toCommand(accountId)));
        return ResponseEntity.ok(ApiResponse.success("CHATBOT_ANSWERED", "응답했습니다.", data));
    }

    @GetMapping("/chatbot/suggested-questions")
    @Operation(summary = "챗봇 추천 질문",
            description = "입력창 위에 칩으로 노출되는 질문 목록입니다. 누르면 그대로 질의합니다.")
    public ResponseEntity<ApiResponse<List<String>>> findSuggestedQuestions() {
        // TODO: FAQ 조회수 상위 질문 또는 고정 목록 반환
        return ResponseEntity.ok(ApiResponse.success("SUGGESTED_QUESTIONS_FOUND", "조회에 성공했습니다.",
                List.of("프로젝트는 어떻게 등록하나요?",
                        "프리랜서 매칭은 어떻게 진행되나요?",
                        "무료 재추천은 언제 사용할 수 있나요?",
                        "착수금 수수료가 무엇인가요?",
                        "협상은 최대 몇 회까지 가능한가요?",
                        "계약서는 어떻게 작성되나요?")));
    }

    @GetMapping("/chatbot/quota")
    @Operation(summary = "챗봇 잔여 한도 조회", description = "자정에 초기화됩니다.")
    public ResponseEntity<ApiResponse<ChatbotQuotaResponse>> findQuota(@CurrentAccountId Long accountId) {
        ChatbotQuotaResponse data = ChatbotQuotaResponse.from(chatbotUseCase.getQuota(accountId));
        return ResponseEntity.ok(ApiResponse.success("CHATBOT_QUOTA_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping("/chatbot/messages")
    @Operation(summary = "챗봇 오늘 대화 이력",
            description = "오늘 주고받은 질문·답변을 시간순으로 반환합니다. 채팅 화면 진입 시 한 번 호출하면 됩니다. "
                    + "이어서 물을 때 쓸 sessionId 는 마지막 항목에서 꺼내 쓰세요.")
    public ResponseEntity<ApiResponse<List<ChatbotAnswerResponse>>> findTodayChatbotMessages(
            @CurrentAccountId Long accountId
    ) {
        List<ChatbotAnswerResponse> data = chatbotUseCase.findTodayMessages(accountId).stream()
                .map(ChatbotAnswerResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("CHATBOT_MESSAGES_FOUND", "조회에 성공했습니다.", data));
    }

    // ==========================================
    // 1:1 문의 (R45)
    // ==========================================

    @PostMapping("/inquiries")
    @Operation(summary = "1:1 문의 등록",
            description = "챗봇 이용 여부와 무관하게 언제든 접수할 수 있습니다. "
                    + "fileIds 는 POST /api/v1/files 로 먼저 업로드해 받은 값이어야 합니다.")
    @ApiErrorCodeExample(domain = InquiryErrorCode.class, value = {"INVALID_ATTACHMENT"})
    public ResponseEntity<ApiResponse<InquiryResponse>> createInquiry(
            @Valid @RequestBody InquiryCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        InquiryResponse response = InquiryResponse.from(inquiryUseCase.create(request.toCommand(accountId)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("INQUIRY_CREATED", "문의를 접수했습니다.", response));
    }

    @GetMapping("/inquiries/mine")
    @Operation(summary = "내 문의 목록")
    public ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> findMyInquiries(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<InquiryResponse> response = PageResponse.from(
                inquiryUseCase.findMine(accountId, status, pageable).map(InquiryResponse::from));
        return ResponseEntity.ok(ApiResponse.success("INQUIRIES_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/inquiries/{inquiryId}")
    @Operation(summary = "문의 상세")
    @ApiErrorCodeExample(domain = InquiryErrorCode.class, value = {"INQUIRY_NOT_FOUND", "INQUIRY_FORBIDDEN"})
    public ResponseEntity<ApiResponse<InquiryResponse>> findInquiry(
            @PathVariable Long inquiryId,
            @CurrentAccountId Long accountId
    ) {
        InquiryResponse response = InquiryResponse.from(inquiryUseCase.findOne(accountId, inquiryId));
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_FOUND", "조회에 성공했습니다.", response));
    }

    // 관리자용 문의 요약·목록·답변은 관리자 서버(pairing-admin)로 옮겼다.
    // 같은 inquiry 테이블을 쓰므로 그쪽에서 답변하면 사용자 조회에 그대로 반영된다.
}
