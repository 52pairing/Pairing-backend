package com.pairing.matching.infrastructure.directory;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.FreelancerCandidateSummaryUseCase;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.Career;
import com.pairing.freelancer.domain.model.CampusType;
import com.pairing.freelancer.domain.model.Education;
import com.pairing.freelancer.domain.model.GraduationStatus;
import com.pairing.freelancer.domain.model.ResumeStatus;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * 이력서 하위 목록에 <b>null 원소가 섞여 있어도</b> 임베딩 요약을 만들어내는지 확인한다.
 *
 * <p>{@code ResumeJpaEntity}의 학력·경력은 {@code @OrderColumn(name = "sort_order")} 매핑이다.
 * 하이버네이트는 그 컬럼을 <b>리스트 인덱스</b>로 쓰기 때문에, 값이 0부터 연속이 아니면 빈 자리를
 * <b>null 원소로 채워서</b> 컬렉션을 돌려준다. DB 를 조회해 보면 컬럼이 다 채워져 있어서 데이터가
 * 정상으로 보이는데, 자바 쪽 리스트에는 없던 null 이 들어 있다.
 *
 * <p>2026-08-13 임베딩 일괄 재색인이 이것 때문에 무더기로 실패했다. {@code Stream.toList()}는 null
 * <i>값</i>은 허용하지만 null <i>원소</i>에 메서드 참조를 적용하면 <b>메시지 없는 NPE</b>가 나고,
 * map 이 지연 평가라 스택에는 {@code .toList()} 줄만 찍힌다. 그래서 원인을 찾기가 어려웠다.
 *
 * <p>정렬 번호를 정상화하는 것과 별개로, 여기서 null 을 견디지 못하면 같은 사고가 다시 난다.
 */
@DisplayName("프리랜서 이력서 요약 조회")
class FreelancerDirectoryAdapterTest {

    private static final Long FREELANCER_ID = 7L;
    private static final Long ACCOUNT_ID = 70L;

    private ResumeUseCase resumeUseCase;
    private AccountQueryUseCase accountQueryUseCase;
    private FreelancerDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        resumeUseCase = Mockito.mock(ResumeUseCase.class);
        accountQueryUseCase = Mockito.mock(AccountQueryUseCase.class);
        adapter = new FreelancerDirectoryAdapter(
                Mockito.mock(FreelancerCandidateSummaryUseCase.class),
                accountQueryUseCase,
                Mockito.mock(FreelancerConditionUseCase.class),
                resumeUseCase
        );

        FreelancerProfile profile = Mockito.mock(FreelancerProfile.class);
        when(profile.getAccountId()).thenReturn(ACCOUNT_ID);
        when(accountQueryUseCase.findFreelancerProfileById(FREELANCER_ID)).thenReturn(Optional.of(profile));
    }

    @Test
    @DisplayName("정렬 번호 구멍 때문에 null 원소가 섞여도 요약을 만든다")
    void toleratesNullElementsFromOrderColumnGaps() {
        when(resumeUseCase.findMyResume(ACCOUNT_ID))
                .thenReturn(Optional.of(resume(listWithLeadingNull(career("주문 시스템 개발")),
                        listWithLeadingNull(education("컴퓨터공학과")))));

        assertThatCode(() -> adapter.findResumeSummary(FREELANCER_ID)).doesNotThrowAnyException();

        FreelancerResumeSummary summary = adapter.findResumeSummary(FREELANCER_ID);
        assertThat(summary.careerDescriptions()).containsExactly("주문 시스템 개발");
        assertThat(summary.majors()).containsExactly("컴퓨터공학과");
    }

    @Test
    @DisplayName("학과와 담당업무가 비어 있으면 목록에서 빼고 담는다")
    void dropsBlankValues() {
        when(resumeUseCase.findMyResume(ACCOUNT_ID))
                .thenReturn(Optional.of(resume(
                        List.of(career(null), career("  "), career("결제 API 구축")),
                        List.of(education(null), education("경영학과")))));

        FreelancerResumeSummary summary = adapter.findResumeSummary(FREELANCER_ID);

        assertThat(summary.careerDescriptions())
                .as("FreelancerResumeSummary javadoc 이 '미입력 건은 빠진다'고 약속하고 있다")
                .containsExactly("결제 API 구축");
        assertThat(summary.majors()).containsExactly("경영학과");
    }

    /** 하이버네이트가 sort_order 0 자리를 비워 둔 컬렉션을 재현한다. {@code List.of}는 null 을 못 담는다. */
    private static <T> List<T> listWithLeadingNull(T value) {
        List<T> list = new ArrayList<>();
        list.add(null);
        list.add(value);
        return list;
    }

    private static Career career(String jobDescription) {
        return Career.of(LocalDate.of(2019, 1, 1), null, "A사", "백엔드팀", "대리", jobDescription);
    }

    private static Education education(String major) {
        return Education.of(LocalDate.of(2014, 3, 1), LocalDate.of(2018, 2, 1), "페어링대학교", major,
                GraduationStatus.GRADUATED, CampusType.MAIN);
    }

    private static ResumeResult resume(List<Career> careers, List<Education> educations) {
        return new ResumeResult(1L, ResumeStatus.COMPLETED, "이프리", LocalDate.of(1995, 3, 1),
                "010-0000-0000", "free@pairing.com", "06234", "서울 강남구", null, null,
                "백엔드 6년차입니다.", null, educations, careers, List.of(), List.of(), null,
                LocalDateTime.now());
    }
}
