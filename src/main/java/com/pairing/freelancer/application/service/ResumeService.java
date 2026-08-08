package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.Career;
import com.pairing.freelancer.domain.model.Certificate;
import com.pairing.freelancer.domain.model.Education;
import com.pairing.freelancer.domain.model.Resume;
import com.pairing.freelancer.domain.model.ResumeAgreements;
import com.pairing.freelancer.domain.model.ResumeLink;
import com.pairing.freelancer.domain.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ResumeService implements ResumeUseCase {

    private final ResumeRepository resumeRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumeResult> findMyResume(Long accountId) {
        return resumeRepository.findByAccountId(accountId).map(resume -> toResult(accountId, resume));
    }

    @Override
    public ResumeResult upsert(UpsertResumeCommand command) {
        List<Education> educations = command.educations().stream()
                .map(e -> Education.of(e.startDate(), e.endDate(), e.schoolName(), e.major(),
                        e.graduationStatus(), e.campusType()))
                .toList();
        List<Career> careers = command.careers().stream()
                .map(c -> Career.of(c.startDate(), c.endDate(), c.companyName(), c.departmentRank(),
                        c.jobDescription()))
                .toList();
        List<Certificate> certificates = command.certificates() == null
                ? List.of()
                : command.certificates().stream()
                        .map(c -> Certificate.of(c.acquiredDate(), c.name(), c.issuerScore(), c.note()))
                        .toList();
        List<ResumeLink> links = command.links() == null
                ? List.of()
                : command.links().stream().map(ResumeLink::of).toList();

        Resume resume = resumeRepository.findByAccountId(command.accountId())
                .map(existing -> {
                    existing.replaceWith(command.profileFileId(), command.contactPhone(), command.contactEmail(),
                            command.address(), command.selfIntroduction(), command.portfolioFileId(),
                            educations, careers, certificates, links);
                    return existing;
                })
                .orElseGet(() -> Resume.create(command.accountId(), command.profileFileId(), command.contactPhone(),
                        command.contactEmail(), command.address(), command.selfIntroduction(),
                        command.portfolioFileId(), educations, careers, certificates, links,
                        toAgreements(command.agreements())));

        // TODO: ai-server 연동 준비되면 저장 후 이력서 임베딩 재생성 요청
        Resume saved = resumeRepository.save(resume);
        return toResult(command.accountId(), saved);
    }

    private ResumeResult toResult(Long accountId, Resume resume) {
        Account account = accountQueryUseCase.getById(accountId);

        // 연락처를 비워두면 계정 값을 그대로 보여준다. 계정 값이 바뀌면 다음 조회부터 자동으로 반영된다.
        String contactPhone = isBlank(resume.getContactPhone()) ? account.getPhone() : resume.getContactPhone();
        String contactEmail = isBlank(resume.getContactEmail()) ? account.getEmail() : resume.getContactEmail();

        return new ResumeResult(
                resume.getId(),
                resume.getStatus(),
                account.getName(),
                // TODO: account 도메인에 FreelancerProfile 조회 포트 추가되면 생년월일 연결
                null,
                contactPhone,
                contactEmail,
                resume.getAddress(),
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
