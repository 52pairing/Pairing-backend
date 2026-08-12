package com.pairing.support.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.file.application.result.FileResult;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.global.exception.BusinessException;
import com.pairing.support.application.command.CreateInquiryCommand;
import com.pairing.support.application.result.InquiryFileResult;
import com.pairing.support.application.result.InquiryResult;
import com.pairing.support.application.usecase.InquiryUseCase;
import com.pairing.support.domain.model.Inquiry;
import com.pairing.support.domain.model.InquiryStatus;
import com.pairing.support.domain.repository.InquiryRepository;
import com.pairing.support.exception.InquiryErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryService implements InquiryUseCase {

    private static final String ANSWERER_NAME = "페어링 고객지원";

    private final InquiryRepository inquiryRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;

    @Override
    @Transactional
    public InquiryResult create(CreateInquiryCommand command) {
        Account writer = accountQueryUseCase.getById(command.writerAccountId());
        requireOwnedFiles(command.fileIds(), command.writerAccountId());
        Inquiry inquiry = Inquiry.create(command.writerAccountId(), writer.getName(), writer.getRole(),
                writer.getEmail(), command.title(), command.content(), command.fileIds());
        Inquiry saved = inquiryRepository.save(inquiry);
        return toResult(saved, false);
    }

    @Override
    public Page<InquiryResult> findMine(Long accountId, InquiryStatus status, Pageable pageable) {
        Page<Inquiry> page = status == null
                ? inquiryRepository.findByWriterAccountId(accountId, pageable)
                : inquiryRepository.findByWriterAccountIdAndStatus(accountId, status, pageable);
        return page.map(inquiry -> toResult(inquiry, false));
    }

    @Override
    public InquiryResult findOne(Long accountId, Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND));
        boolean isWriter = inquiry.isWrittenBy(accountId);
        boolean isAdmin = !isWriter && accountQueryUseCase.getById(accountId).getRole() == Role.ADMIN;
        if (!isWriter && !isAdmin) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_FORBIDDEN);
        }
        return toResult(inquiry, isAdmin);
    }

    // 관리자용 요약·목록·답변은 관리자 서버(pairing-admin)로 옮겼다. 답변 알림도 그쪽에서 만든다.
    // 답변된 문의를 사용자가 보는 경로(findMine/findOne)는 여기 그대로 남는다 — 같은 테이블이다.

    private InquiryResult toResult(Inquiry inquiry, boolean includeWriterInfo) {
        return new InquiryResult(
                inquiry.getId(),
                inquiry.getInquiryNo(),
                inquiry.getWriterAccountId(),
                includeWriterInfo ? inquiry.getWriterName() : null,
                includeWriterInfo ? inquiry.getWriterRole() : null,
                includeWriterInfo ? inquiry.getWriterEmail() : null,
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                inquiry.getAnswer(),
                inquiry.getStatus() == InquiryStatus.ANSWERED ? ANSWERER_NAME : null,
                inquiry.getAnsweredAt(),
                inquiry.getFileIds().stream().map(this::resolveFile).filter(Objects::nonNull).toList(),
                inquiry.getCreatedAt()
        );
    }

    /**
     * 첨부로 넘어온 fileId 가 본인이 올린 파일인지 확인한다.
     *
     * <p>확인하지 않고 저장하면 {@code inquiry_file} 의 FK 에서 걸려 500 이 난다. 잘못 보낸 요청이므로
     * 400 으로 끊는 게 맞고, 남의 파일을 자기 문의에 붙여 objectKey 를 들여다보는 것도 여기서 막힌다.
     *
     * <p>업로드는 됐는데 문의 등록 전에 파일을 지운 경우도 여기로 걸린다. 다시 올려야 한다.
     */
    private void requireOwnedFiles(List<Long> fileIds, Long writerAccountId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        boolean allOwned = fileIds.stream()
                .allMatch(fileId -> fileQueryUseCase.isOwnedBy(fileId, writerAccountId));
        if (!allOwned) {
            throw new BusinessException(InquiryErrorCode.INVALID_ATTACHMENT);
        }
    }

    private InquiryFileResult resolveFile(Long fileId) {
        try {
            FileResult file = fileQueryUseCase.getById(fileId);
            return new InquiryFileResult(file.fileId(), file.originalName(), file.objectKey());
        } catch (BusinessException e) {
            // 첨부파일이 나중에 삭제됐어도 문의 조회 자체는 막지 않는다.
            return null;
        }
    }
}
