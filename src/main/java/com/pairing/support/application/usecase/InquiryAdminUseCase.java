package com.pairing.support.application.usecase;

import com.pairing.account.domain.model.Role;
import com.pairing.support.application.result.InquiryResult;
import com.pairing.support.application.result.InquirySummaryResult;
import com.pairing.support.domain.model.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryAdminUseCase {

    InquirySummaryResult getSummary();

    /** {@code keyword} 는 회원명·제목·문의번호를 한 번에 검색한다. 각 조건은 null 이면 걸지 않는다. */
    Page<InquiryResult> findAll(String keyword, Role writerRole, InquiryStatus status, Pageable pageable);

    /**
     * 답변하면 상태가 ANSWERED 로 바뀌고 작성자에게 알림이 발송된다. 이미 답변된 문의도 다시
     * 답변(수정)할 수 있고, 그때도 알림은 다시 발송된다. 없으면 {@code IQ_001}.
     */
    InquiryResult answer(Long inquiryId, String answer);
}
