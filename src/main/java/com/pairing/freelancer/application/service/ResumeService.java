package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.event.ResumeUpdatedEvent;
import com.pairing.freelancer.application.result.ResumeDraftResult;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.Career;
import com.pairing.freelancer.domain.model.Certificate;
import com.pairing.freelancer.domain.model.Education;
import com.pairing.freelancer.domain.model.Resume;
import com.pairing.freelancer.domain.model.ResumeAgreements;
import com.pairing.freelancer.domain.model.ResumeDraft;
import com.pairing.freelancer.domain.model.ResumeLink;
import com.pairing.freelancer.domain.repository.ResumeDraftRepository;
import com.pairing.freelancer.domain.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ResumeService implements ResumeUseCase {

    private final ResumeRepository resumeRepository;
    private final ResumeDraftRepository resumeDraftRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumeResult> findMyResume(Long accountId) {
        return resumeRepository.findByAccountId(accountId).map(resume -> toResult(accountId, resume));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findAllAccountIdsWithResume() {
        return resumeRepository.findAllAccountIds();
    }

    @Override
    public ResumeResult upsert(UpsertResumeCommand command) {
        List<Education> educations = command.educations().stream()
                .map(e -> Education.of(e.startDate(), e.endDate(), e.schoolName(), e.major(),
                        e.graduationStatus(), e.campusType()))
                .toList();
        List<Career> careers = command.careers().stream()
                .map(c -> Career.of(c.startDate(), c.endDate(), c.companyName(), c.department(),
                        c.position(), c.jobDescription()))
                .toList();
        List<Certificate> certificates = command.certificates() == null
                ? List.of()
                : command.certificates().stream()
                        .map(c -> Certificate.of(c.acquiredDate(), c.name(), c.issuer(), c.score(), c.note()))
                        .toList();
        List<ResumeLink> links = command.links() == null
                ? List.of()
                : command.links().stream().map(ResumeLink::of).toList();

        Resume resume = resumeRepository.findByAccountId(command.accountId())
                .map(existing -> {
                    existing.replaceWith(command.profileFileId(), command.contactPhone(), command.contactEmail(),
                            command.zipCode(), command.address(), command.addressDetail(),
                            command.selfIntroduction(), command.portfolioFileId(),
                            educations, careers, certificates, links);
                    return existing;
                })
                .orElseGet(() -> Resume.create(command.accountId(), command.profileFileId(), command.contactPhone(),
                        command.contactEmail(), command.zipCode(), command.address(), command.addressDetail(),
                        command.selfIntroduction(), command.portfolioFileId(), educations, careers, certificates,
                        links, toAgreements(command.agreements())));

        Resume saved = resumeRepository.save(resume);

        // 정식 등록했으면 초안은 쓸모가 없다. 남겨 두면 다음 진입 때 옛 입력값이 되살아난다.
        resumeDraftRepository.deleteByAccountId(command.accountId());

        eventPublisher.publishEvent(new ResumeUpdatedEvent(command.accountId()));
        return toResult(command.accountId(), saved);
    }

    @Override
    public ResumeDraftResult saveDraft(Long accountId, String payload) {
        ResumeDraft draft = resumeDraftRepository.findByAccountId(accountId)
                .map(existing -> {
                    existing.replaceWith(payload);
                    return existing;
                })
                .orElseGet(() -> ResumeDraft.create(accountId, payload));

        ResumeDraft saved = resumeDraftRepository.save(draft);
        return new ResumeDraftResult(saved.getPayload(), saved.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumeDraftResult> findMyDraft(Long accountId) {
        return resumeDraftRepository.findByAccountId(accountId)
                .map(draft -> new ResumeDraftResult(draft.getPayload(), draft.getUpdatedAt()));
    }

    private ResumeResult toResult(Long accountId, Resume resume) {
        Account account = accountQueryUseCase.getById(accountId);
        LocalDate birthDate = accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                .map(FreelancerProfile::getBirthDate)
                .orElse(null);

        // 연락처를 비워두면 계정 값을 그대로 보여준다. 계정 값이 바뀌면 다음 조회부터 자동으로 반영된다.
        String contactPhone = isBlank(resume.getContactPhone()) ? account.getPhone() : resume.getContactPhone();
        String contactEmail = isBlank(resume.getContactEmail()) ? account.getEmail() : resume.getContactEmail();

        return new ResumeResult(
                resume.getId(),
                resume.getStatus(),
                account.getName(),
                birthDate,
                contactPhone,
                contactEmail,
                resume.getZipCode(),
                resume.getAddress(),
                resume.getAddressDetail(),
                fileQueryUseCase.findObjectKey(resume.getProfileFileId()).orElse(null),
                resume.getSelfIntroduction(),
                fileQueryUseCase.findObjectKey(resume.getPortfolioFileId()).orElse(null),
                resume.getEducations(),
                resume.getCareers(),
                resume.getCertificates(),
                resume.getLinks(),
                resume.getAgreements(),
                resume.getUpdatedAt()
        );
    }

    private static ResumeAgreements toAgreements(UpsertResumeCommand.Agreements agreements) {
        return ResumeAgreements.of(agreements.profileCollectionAgreed(), agreements.profileProvisionAgreed(),
                agreements.aiAnalysisAgreed(), agreements.careerPortfolioUsageAgreed());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
