package com.pairing.support.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.support.domain.model.InquiryCategory;
import com.pairing.support.domain.model.InquiryStatus;
import com.pairing.support.presentation.api.request.ChatbotAskRequest;
import com.pairing.support.presentation.api.request.InquiryAnswerRequest;
import com.pairing.support.presentation.api.request.InquiryCreateRequest;
import com.pairing.support.presentation.api.response.ChatbotAnswerResponse;
import com.pairing.support.presentation.api.response.ChatbotQuotaResponse;
import com.pairing.support.presentation.api.response.InquiryFileResponse;
import com.pairing.support.presentation.api.response.InquiryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FAQ 챗봇과 1:1 문의. (요구사항 R44, R45)
 *
 * <p>둘은 서로 독립된 창구다. 챗봇을 거쳐야 문의할 수 있는 구조가 아니라 사용자가 원하는 쪽을 고른다.
 * 챗봇은 하루 10회로 제한되고, LLM 호출은 AI 서버가 담당한다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "18. Support", description = "챗봇/1:1 문의 API")
public class SupportController {

    // ==========================================
    // 챗봇 (R44)
    // ==========================================

    @PostMapping("/chatbot/questions")
    @Operation(summary = "챗봇 질의",
            description = "FAQ·이용안내·정책 범위에서 답합니다. 하루 10회를 넘기면 429 로 막힙니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    public ResponseEntity<ApiResponse<ChatbotAnswerResponse>> ask(
            @Valid @RequestBody ChatbotAskRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 일일 한도 확인 -> AI 서버 호출 -> 세션/메시지 저장 -> 사용량 증가
        ChatbotAnswerResponse data = new ChatbotAnswerResponse(1200L, request.question(),
                "착수금 수수료는 계약 체결 시점에 발생합니다.", 9, LocalDateTime.now());

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
        // TODO: chatbot_quota 조회
        return ResponseEntity.ok(ApiResponse.success("CHATBOT_QUOTA_FOUND", "조회에 성공했습니다.",
                new ChatbotQuotaResponse(LocalDate.now(), 10, 1, 9)));
    }

    @GetMapping("/chatbot/sessions/{sessionId}/messages")
    @Operation(summary = "챗봇 대화 이력", description = "한 세션의 질문·답변을 시간순으로 반환합니다.")
    public ResponseEntity<ApiResponse<List<ChatbotAnswerResponse>>> findChatbotMessages(
            @PathVariable Long sessionId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 본인 세션인지 확인 후 메시지 조회
        return ResponseEntity.ok(ApiResponse.success("CHATBOT_MESSAGES_FOUND", "조회에 성공했습니다.",
                List.of(new ChatbotAnswerResponse(sessionId, "착수금 수수료는 언제 결제하나요?",
                        "계약 체결 시점에 발생합니다.", 9, LocalDateTime.now()))));
    }

    // ==========================================
    // 1:1 문의 (R45)
    // ==========================================

    @PostMapping("/inquiries")
    @Operation(summary = "1:1 문의 등록", description = "챗봇 이용 여부와 무관하게 언제든 접수할 수 있습니다.")
    public ResponseEntity<ApiResponse<InquiryResponse>> createInquiry(
            @Valid @RequestBody InquiryCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 문의 저장 (상태 PENDING)
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("INQUIRY_CREATED", "문의를 접수했습니다.", sampleInquiry(null)));
    }

    @GetMapping("/inquiries/mine")
    @Operation(summary = "내 문의 목록")
    public ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> findMyInquiries(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 내 문의 조회
        return ResponseEntity.ok(ApiResponse.success("INQUIRIES_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleInquiry(null)), page, size, 1, 1, true, true)));
    }

    @GetMapping("/inquiries/{inquiryId}")
    @Operation(summary = "문의 상세")
    public ResponseEntity<ApiResponse<InquiryResponse>> findInquiry(
            @PathVariable Long inquiryId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 작성자 본인 또는 관리자만 열람
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_FOUND", "조회에 성공했습니다.", sampleInquiry(null)));
    }

    // ==========================================
    // 관리자 (R45)
    // ==========================================

    @GetMapping("/admin/inquiries")
    @Operation(summary = "[관리자] 문의 목록", description = "상태로 필터링합니다.")
    public ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> findInquiriesForAdmin(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // TODO: 전체 문의 조회
        return ResponseEntity.ok(ApiResponse.success("INQUIRIES_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleInquiry("홍길동")), page, size, 1, 1, true, true)));
    }

    @PostMapping("/admin/inquiries/{inquiryId}/answer")
    @Operation(summary = "[관리자] 문의 답변", description = "답변하면 상태가 ANSWERED 로 바뀌고 사용자에게 알림이 발송됩니다.")
    public ResponseEntity<ApiResponse<InquiryResponse>> answerInquiry(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request
    ) {
        // TODO: 답변 저장 -> 상태 ANSWERED -> 알림 발송
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_ANSWERED", "답변을 등록했습니다.",
                sampleInquiry("홍길동")));
    }

    private InquiryResponse sampleInquiry(String writerName) {
        return new InquiryResponse("QNA-20260805-0012", InquiryCategory.PAYMENT,
                1300L, writerName, "착수금 수수료 결제 문의",
                "착수금 수수료 결제 버튼이 활성화되지 않습니다. 프로젝트를 등록하고 AI 검수를 완료했는데도 결제가 진행되지 않아 문의드립니다.",
                InquiryStatus.PENDING, null, null, null,
                List.of(new InquiryFileResponse(42L, "오류화면.png", "inquiries/uuid.png")),
                LocalDateTime.now());
    }
}
