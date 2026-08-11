package com.pairing.settlement.application.command;

import com.pairing.freelancer.domain.model.FreelancerGrade;

/**
 * 프리랜서 성공보수 정산 생성 입력. 프로젝트가 완료 대기로 넘어간 시점에 발생한다. (P30)
 *
 * <p>기준 금액은 <b>그 계약의 총액</b>이다. 클라이언트 성공보수가 프로젝트 예산을 쓰는 것과 다르다.
 * 프로젝트에 여러 명을 뽑으면 프리랜서마다 계약 금액이 달라 예산으로는 나눌 수 없다.
 * 프리랜서 착수금과 같은 기준이다.
 *
 * <p>등급을 호출부가 넘긴다. 정산 도메인이 freelancer_profile 을 직접 읽지 않기 위해서다.
 */
public record CreateFreelancerSuccessFeeCommand(
        Long projectId,
        Long contractId,
        Long payerAccountId,
        long contractAmount,
        FreelancerGrade freelancerGrade
) {
}
