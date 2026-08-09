package com.pairing.settlement.application.port;

/**
 * 성공보수 결제가 끝나면 프로젝트를 종료로 넘긴다. (정책 P30)
 *
 * <p>{@link ProjectRecruitStarterPort} 와 같은 이유로 아웃바운드 포트로 둔다.
 * 정산 도메인은 project 의 리포지토리를 직접 쓰지 않는다.
 */
public interface ProjectCloserPort {

    void close(Long projectId);
}
