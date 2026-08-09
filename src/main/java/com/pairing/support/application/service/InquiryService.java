package com.pairing.support.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.file.application.result.FileResult;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.global.exception.BusinessException;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.support.application.command.CreateInquiryCommand;
import com.pairing.support.application.result.InquiryFileResult;
import com.pairing.support.application.result.InquiryResult;
import com.pairing.support.application.result.InquirySummaryResult;
import com.pairing.support.application.usecase.InquiryAdminUseCase;
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
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryService implements InquiryUseCase, InquiryAdminUseCase {

    private static final String ANSWERER_NAME = "페어링 고객지원";

    private final InquiryRepository inquiryRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final NotificationCreateUseCase notificationCreateUseCase;

    @Override
    @Transactional
    public InquiryResult create(CreateInquiryCommand command) {
        Account writer = accountQueryUseCase.getById(command.writerAccountId());
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

    @Override
    public InquirySummaryResult getSummary() {
        long totalCount = inquiryRepository.count();
        long pendingCount = inquiryRepository.countByStatus(InquiryStatus.PENDING);
        long answeredCount = inquiryRepository.countByStatus(InquiryStatus.ANSWERED);
        long todayCount = inquiryRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay());
        return new InquirySummaryResult(totalCount, pendingCount, answeredCount, todayCount);
    }

    @Override
    public Page<InquiryResult> findAll(String keyword, Role writerRole, InquiryStatus status, Pageable pageable) {
        return inquiryRepository.search(keyword, writerRole, status, pageable)
                .map(inquiry -> toResult(inquiry, true));
    }

    @Override
    @Transactional
    public InquiryResult answer(Long inquiryId, String answer) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND));
        inquiry.answer(answer);
        Inquiry saved = inquiryRepository.save(inquiry);

        notificationCreateUseCase.create(new CreateNotificationCommand(saved.getWriterAccountId(),
                NotificationType.INQUIRY_ANSWERED, "문의하신 내용에 답변이 등록되었습니다.", saved.getTitle(),
                "/support/inquiries/" + saved.getId()));

        return toResult(saved, true);
    }

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
