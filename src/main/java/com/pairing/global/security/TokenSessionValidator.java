package com.pairing.global.security;

/**
 * 액세스 토큰에 담긴 세션 ID가 아직 살아 있는지 확인한다.
 *
 * <p>중복 로그인을 막으려면 "가장 최근 로그인으로 발급된 토큰인지"를 매 요청에서 판단해야 하는데,
 * 무상태 JWT만으로는 알 수 없다. 판정에 필요한 저장소(Redis)는 auth 도메인이 소유하므로
 * global에는 인터페이스만 두고 구현은 도메인이 제공한다. (global -> 도메인 의존이 생기지 않는다)
 *
 * <p>구현 빈이 없으면 필터는 이 검사를 건너뛴다.
 */
public interface TokenSessionValidator {

    /**
     * @param subject   토큰 subject (계정 식별자)
     * @param sessionId 토큰의 sid 클레임
     * @return 유효한 세션이면 true. false면 필터가 401 GLOBAL_011로 응답한다.
     */
    boolean isAlive(String subject, String sessionId);
}
