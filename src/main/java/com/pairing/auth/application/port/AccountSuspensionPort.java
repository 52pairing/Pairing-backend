package com.pairing.auth.application.port;

/**
 * 회원 정지 여부. 정지 상태는 컬럼이 아니라 Redis에서 관리한다. (스키마 v12 결정)
 *
 * <p>정지 처리(등록/해제)는 관리자 도메인이 담당하고, 여기서는 로그인 차단을 위해 조회만 한다.
 */
public interface AccountSuspensionPort {

    boolean isSuspended(Long accountId);
}
