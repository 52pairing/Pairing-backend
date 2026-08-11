package com.pairing.settlement.application.port;

/**
 * 프리랜서 착수금 결제가 모두 끝나면 프로젝트를 진행중으로 넘긴다. (정책 P27)
 *
 * <p>정산 도메인이 project 의 리포지토리를 직접 쓰지 않기 위한 아웃바운드 포트다.
 */
public interface ProjectProgressStarterPort {

    /**
     * @return 이번 호출로 실제 진행중이 됐으면 true. 인원이 덜 찼으면 false
     */
    boolean startProgress(Long projectId);
}
