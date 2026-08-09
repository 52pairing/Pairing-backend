package com.pairing.project.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모집 인원 1건. 프로젝트 애그리거트 안에서만 만들어진다.
 *
 * <p>인원수는 착수금 결제 후 바꿀 수 없다. 매칭 요청·계약이 포지션 단위로 묶이기 때문이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position {

    private static final int MIN_CAREER = 1;
    private static final int MAX_CAREER = 50;
    private static final int MIN_HEADCOUNT = 1;
    private static final int MAX_HEADCOUNT = 50;

    private Long id;
    private int positionNo;
    private JobCategory jobCategory;
    private JobRole jobRole;
    private int minCareerYears;
    private int headcount;
    private int confirmedCount;
    private PositionStatus status;
    private LocalDateTime closedAt;
    private List<SkillCode> skills;

    private Position(Long id, int positionNo, JobCategory jobCategory, JobRole jobRole,
                     int minCareerYears, int headcount, int confirmedCount,
                     PositionStatus status, LocalDateTime closedAt, List<SkillCode> skills) {
        this.id = id;
        this.positionNo = positionNo;
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.minCareerYears = minCareerYears;
        this.headcount = headcount;
        this.confirmedCount = confirmedCount;
        this.status = status;
        this.closedAt = closedAt;
        this.skills = skills;
    }

    static Position create(int positionNo, JobCategory jobCategory, JobRole jobRole,
                           int minCareerYears, int headcount, List<SkillCode> skills) {
        validate(jobCategory, jobRole, minCareerYears, headcount, skills);
        return new Position(null, positionNo, jobCategory, jobRole, minCareerYears,
                headcount, 0, PositionStatus.RECRUITING, null, List.copyOf(skills));
    }

    public static Position reconstitute(Long id, int positionNo, JobCategory jobCategory, JobRole jobRole,
                                        int minCareerYears, int headcount, int confirmedCount,
                                        PositionStatus status, LocalDateTime closedAt,
                                        List<SkillCode> skills) {
        return new Position(id, positionNo, jobCategory, jobRole, minCareerYears,
                headcount, confirmedCount, status, closedAt, List.copyOf(skills));
    }

    /** 모집 중에만 조건을 바꿀 수 있다. 인원 변경 가능 여부는 프로젝트가 판단한다. */
    void changeCondition(JobCategory jobCategory, JobRole jobRole, int minCareerYears,
                         int headcount, List<SkillCode> skills) {
        validate(jobCategory, jobRole, minCareerYears, headcount, skills);
        if (headcount < this.confirmedCount) {
            throw new BusinessException(ProjectErrorCode.HEADCOUNT_BELOW_CONFIRMED);
        }
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.minCareerYears = minCareerYears;
        this.headcount = headcount;
        this.skills = List.copyOf(skills);
    }

    /**
     * 화면 표시 순서를 다시 매긴다.
     *
     * <p>{@code uk_project_position (project_id, position_no)} 때문에 프로젝트 안에서 번호가 겹치면
     * 안 된다. 수정으로 순서가 바뀌면 전체를 다시 매겨야 충돌하지 않는다.
     */
    void renumber(int positionNo) {
        this.positionNo = positionNo;
    }

    /**
     * 계약 체결 1건. 양측 서명이 끝나면 계약 도메인이 호출한다.
     *
     * <p>모집 인원을 다 채우면 더 뽑을 이유가 없으므로 여기서 바로 닫는다.
     */
    void confirm() {
        if (isFilled()) {
            throw new BusinessException(ProjectErrorCode.POSITION_ALREADY_FILLED);
        }
        this.confirmedCount++;
        if (isFilled()) {
            close();
        }
    }

    /** 모집 종료. 닫힌 시각을 함께 남긴다. 이미 닫혀 있으면 시각을 덮어쓰지 않는다. */
    void close() {
        if (this.status == PositionStatus.CLOSED) {
            return;
        }
        this.status = PositionStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    boolean isFilled() {
        return confirmedCount >= headcount;
    }

    private static void validate(JobCategory jobCategory, JobRole jobRole, int minCareerYears,
                                 int headcount, List<SkillCode> skills) {
        if (jobCategory == null || jobRole == null || skills == null || skills.isEmpty()) {
            throw new BusinessException(ProjectErrorCode.INVALID_POSITION);
        }
        if (minCareerYears < MIN_CAREER || minCareerYears > MAX_CAREER) {
            throw new BusinessException(ProjectErrorCode.INVALID_POSITION);
        }
        if (headcount < MIN_HEADCOUNT || headcount > MAX_HEADCOUNT) {
            throw new BusinessException(ProjectErrorCode.INVALID_POSITION);
        }
    }
}