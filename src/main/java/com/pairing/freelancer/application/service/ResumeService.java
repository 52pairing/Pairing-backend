package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.event.ResumeUpdatedEvent;
import com.pairing.freelancer.application.result.ResumeDraftResult;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
public class ResumeService implements ResumeUseCase {

    private final ResumeRepository resumeRepository;
    private final ResumeDraftRepository resumeDraftRepository;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final FreelancerConditionUseCase freelancerConditionUseCase;

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumeResult> findMyResume(Long accountId) {
        return resumeRepository.findByAccountId(accountId).map(resume -> toResult(accountId, resume));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ResumeResult> findResumes(Collection<Long> accountIds) {
        List<Resume> resumes = resumeRepository.findByAccountIdIn(accountIds);
        if (resumes.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = resumes.stream().map(Resume::getAccountId).toList();
        Map<Long, Account> accounts = accountQueryUseCase.getByIds(ids);
        Map<Long, FreelancerProfile> profiles = accountQueryUseCase.findFreelancerProfilesByAccountIds(ids);

        List<Long> fileIds = resumes.stream()
                .flatMap(r -> Stream.of(r.getProfileFileId(), r.getPortfolioFileId()))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> objectKeys = fileQueryUseCase.findObjectKeys(fileIds);

        return resumes.stream().collect(Collectors.toMap(Resume::getAccountId, resume -> {
            Account account = accounts.get(resume.getAccountId());
            LocalDate birthDate = Optional.ofNullable(profiles.get(resume.getAccountId()))
                    .map(FreelancerProfile::getBirthDate)
                    .orElse(null);
            return toResult(resume, account, birthDate, objectKeys);
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findAllAccountIdsWithResume() {
        return resumeRepository.findAllAccountIds();
    }

    /**
     * 조건과 이력서를 한 번에 저장한다.
     *
     * <p>이 메서드가 {@code @Transactional} 이라 두 저장이 같은 트랜잭션에 들어간다.
     * 이력서에서 예외가 나면 <b>조건 저장도 함께 롤백</b>된다 — 화면에서는 한 번의 저장이므로
     * 절반만 반영된 상태를 남기지 않는다.
     *
     * <p>조건을 먼저 저장한다. 조건 쪽 검증(스킬 중복·급여 단위)이 이력서보다 단순해서,
     * 걸릴 문제라면 이력서를 건드리기 전에 걸리는 편이 낫다.
     */
    @Override
    public ResumeResult upsertWithCondition(UpsertConditionCommand conditionCommand,
                                            UpsertResumeCommand resumeCommand) {
        freelancerConditionUseCase.upsert(conditionCommand);
        return upsert(resumeCommand);
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
        String profileImageUrl = fileQueryUseCase.findObjectKey(resume.getProfileFileId()).orElse(null);
        String portfolioUrl = fileQueryUseCase.findObjectKey(resume.getPortfolioFileId()).orElse(null);
        return toResult(resume, account, birthDate, profileImageUrl, portfolioUrl);
    }

    /** {@link #findResumes} 가 미리 배치로 모은 계정·파일 정보를 받아 조립한다. */
    private ResumeResult toResult(Resume resume, Account account, LocalDate birthDate, Map<Long, String> objectKeys) {
        return toResult(resume, account, birthDate,
                objectKey(objectKeys, resume.getProfileFileId()),
                objectKey(objectKeys, resume.getPortfolioFileId()));
    }

    /**
     * fileId 가 null 이면 조회하지 않는다.
     *
     * <p>{@code Map.of()} 가 돌려주는 불변 맵은 {@code get(null)} 에 NPE 를 던진다. 파일이 없는
     * 이력서가 섞여 있으면 목록 전체가 500 으로 터지므로 여기서 먼저 끊는다.
     */
    private static String objectKey(Map<Long, String> objectKeys, Long fileId) {
        return fileId == null ? null : objectKeys.get(fileId);
    }

    private ResumeResult toResult(Resume resume, Account account, LocalDate birthDate,
                                  String profileImageUrl, String portfolioUrl) {
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
                profileImageUrl,
                resume.getSelfIntroduction(),
                portfolioUrl,
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
