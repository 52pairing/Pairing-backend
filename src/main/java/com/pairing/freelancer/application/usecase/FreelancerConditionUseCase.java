package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.domain.model.FreelancerCondition;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface FreelancerConditionUseCase {

    /** 등록하지 않았으면 empty. */
    Optional<FreelancerCondition> findMyCondition(Long accountId);

    /**
     * 여러 계정의 조건을 한 번에. 매칭 도메인이 노출 확정 시점에 프리랜서 조건을
     * 스냅샷으로 굳힐 때, 후보마다 {@link #findMyCondition} 을 부르면 N+1 이라 만들었다.
     *
     * <p>조건을 등록하지 않은 계정은 결과에 없다.
     */
    Map<Long, FreelancerCondition> findConditions(Collection<Long> accountIds);

    /** 없으면 생성하고 있으면 전체 교체한다. */
    FreelancerCondition upsert(UpsertConditionCommand command);
}
