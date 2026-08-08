package com.pairing.settlement.application.port;

/**
 * 착수금 결제가 끝나면 프로젝트를 모집중으로 넘긴다. (정책 P27)
 *
 * <p>정산 도메인이 project 의 리포지토리를 직접 쓰지 않기 위한 아웃바운드 포트다.
 */
public interface ProjectRecruitStarterPort {

    void startRecruiting(Long projectId);
}
