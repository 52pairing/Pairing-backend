package com.pairing.support.domain.model;

import com.pairing.account.domain.model.Role;
import com.pairing.global.exception.BusinessException;
import com.pairing.support.exception.InquiryErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 1:1 문의 1건. (요구사항 R45)
 *
 * <p>{@code writerName}/{@code writerRole}/{@code writerEmail} 은 작성 시점 계정 정보를 그대로
 * 스냅샷해서 저장한다. 계정의 이름·역할·이메일은 가입 후 바꿀 수 있는 경로가 없어서 스냅샷과
 * 실제 값이 어긋날 일이 없고, 관리자 검색·필터를 계정 테이블 조인 없이 문의 테이블만으로 처리할 수 있다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    private static final DateTimeFormatter INQUIRY_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Long id;
    private Long writerAccountId;
    private String writerName;
    private Role writerRole;
    private String writerEmail;
    private String title;
    private String content;
    private List<Long> fileIds;
    private InquiryStatus status;
    private String answer;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;

    private Inquiry(Long id, Long writerAccountId, String writerName, Role writerRole, String writerEmail,
                    String title, String content, List<Long> fileIds, InquiryStatus status, String answer,
                    LocalDateTime answeredAt, LocalDateTime createdAt) {
        if (writerAccountId == null || writerName == null || writerRole == null || title == null || title.isBlank()
                || content == null || content.isBlank()) {
            throw new BusinessException(InquiryErrorCode.INVALID_INQUIRY_FIELD);
        }
        this.id = id;
        this.writerAccountId = writerAccountId;
        this.writerName = writerName;
        this.writerRole = writerRole;
        this.writerEmail = writerEmail;
        this.title = title;
        this.content = content;
        this.fileIds = fileIds == null ? List.of() : fileIds;
        this.status = status;
        this.answer = answer;
        this.answeredAt = answeredAt;
        this.createdAt = createdAt;
    }

    public static Inquiry create(Long writerAccountId, String writerName, Role writerRole, String writerEmail,
                                 String title, String content, List<Long> fileIds) {
        return new Inquiry(null, writerAccountId, writerName, writerRole, writerEmail, title, content, fileIds,
                InquiryStatus.PENDING, null, null, LocalDateTime.now());
    }

    public static Inquiry reconstitute(Long id, Long writerAccountId, String writerName, Role writerRole,
                                       String writerEmail, String title, String content, List<Long> fileIds,
                                       InquiryStatus status, String answer, LocalDateTime answeredAt,
                                       LocalDateTime createdAt) {
        return new Inquiry(id, writerAccountId, writerName, writerRole, writerEmail, title, content, fileIds,
                status, answer, answeredAt, createdAt);
    }

    public boolean isWrittenBy(Long accountId) {
        return this.writerAccountId.equals(accountId);
    }

    /** 답변하면 상태가 {@link InquiryStatus#ANSWERED} 로 바뀐다. 이미 답변된 문의도 다시 답변(수정)할 수 있다. */
    public void answer(String answer) {
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(InquiryErrorCode.INVALID_INQUIRY_FIELD);
        }
        this.answer = answer;
        this.status = InquiryStatus.ANSWERED;
        this.answeredAt = LocalDateTime.now();
    }

    /** 화면 표시용 문의번호. 등록일 + id 로 만든다(예: "QNA-20260805-0012"). */
    public String getInquiryNo() {
        return "QNA-" + createdAt.toLocalDate().format(INQUIRY_NO_DATE_FORMAT) + "-" + String.format("%04d", id);
    }
}
