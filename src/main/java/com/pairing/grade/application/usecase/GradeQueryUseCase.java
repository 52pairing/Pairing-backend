package com.pairing.grade.application.usecase;

import com.pairing.account.domain.model.Role;
import com.pairing.grade.application.result.MyGradeResult;
import com.pairing.grade.domain.model.GradeTier;

import java.util.List;

public interface GradeQueryUseCase {

    /** 역할별 등급 기준표. CLIENT/FREELANCER 외 역할이면 {@code GR_001}. */
    List<GradeTier> findByRole(Role role);

    /** 로그인 계정의 현재 등급과 다음 등급까지 남은 조건. */
    MyGradeResult getMyGrade(Long accountId);
}
