package com.pairing.template_server.global.security;

import com.pairing.template_server.global.exception.RequiredPropertyMissingException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급 / 검증 / 클레임 추출을 담당한다.
 * 토큰의 subject에는 사용자 식별자(예: 이메일, 로그인 ID)를, role 클레임에는 권한을 담는다.
 */
@Component
public class GlobalJwtProvider {

    public static final String ACCESS_TOKEN_COOKIE = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    @Value("${jwt.secret-key:}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // 배포 도메인은 환경변수(COOKIE_DOMAIN)로 주입한다.
    // 값을 비워두면 Domain 속성 자체를 생략(host-only 쿠키)해서 localhost에서도 정상 동작한다.
    @Value("${jwt.cookie-domain}")
    private String cookieDomain;

    // 로컬(http)에서는 false여야 쿠키가 저장된다. 배포(https)에서는 true.
    @Value("${jwt.cookie-secure}")
    private boolean cookieSecure;

    // HS256의 최소 키 길이. 이보다 짧으면 jjwt가 기동 시점에 예외를 던진다.
    private static final int MIN_SECRET_BYTES = 32;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        // 서명 키가 없거나 짧으면 즉시 기동을 실패시킨다.
        // 기본값을 두면 환경변수 주입을 빠뜨린 채 배포됐을 때 공개된 키로 토큰을 서명하게 되고,
        // 그 사실을 아무도 모르는 상태로 서비스가 돌아간다. 조용히 취약해지는 것보다 못 뜨는 게 낫다.
        if (secretKey == null || secretKey.isBlank()) {
            throw new RequiredPropertyMissingException(
                    "jwt.secret-key",
                    "JWT_SECRET_KEY",
                    "JWT 서명 키가 비어 있습니다.",
                    """
                    아래 중 한 가지 방법으로 서명 키를 주입한 뒤 다시 시작하세요.

                      1) 터미널에서 실행하는 경우 (Git Bash / macOS / Linux)
                         export JWT_SECRET_KEY=$(openssl rand -base64 48)

                      2) 터미널에서 실행하는 경우 (PowerShell)
                         $env:JWT_SECRET_KEY = [Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))

                      3) IntelliJ에서 실행하는 경우
                         Run/Debug Configurations > Environment variables 에 JWT_SECRET_KEY 추가

                    HS256을 사용하므로 32바이트 이상이어야 합니다.
                    운영 환경에서는 이 값을 소스나 문서에 남기지 말고 시크릿 저장소에서 주입하세요.""");
        }

        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new RequiredPropertyMissingException(
                    "jwt.secret-key",
                    "JWT_SECRET_KEY",
                    "JWT 서명 키가 너무 짧습니다. (현재 %d바이트, 최소 %d바이트)"
                            .formatted(keyBytes.length, MIN_SECRET_BYTES),
                    """
                    HS256은 최소 32바이트 키가 필요합니다. 더 긴 키로 교체하세요.
                      openssl rand -base64 48""");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // ==========================================
    // 1. 토큰 발급
    // ==========================================
    public String createAccessToken(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    // ==========================================
    // 2. 쿠키 생성 / 삭제
    // ==========================================
    public ResponseCookie createCookie(String name, String token) {
        // 쿠키 만료 시간을 실제 토큰 종류의 만료 시간과 맞춘다.
        long maxAgeSeconds = REFRESH_TOKEN_COOKIE.equals(name)
                ? refreshTokenExpiration / 1000
                : accessTokenExpiration / 1000;

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSecure ? "None" : "Lax") // 크로스 도메인 전송은 Secure + None 조합에서만 허용된다.
                .maxAge(maxAgeSeconds);

        if (!cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        return builder.build();
    }

    public ResponseCookie deleteCookie(String name) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSecure ? "None" : "Lax")
                .maxAge(0); // 수명을 0으로 주어 즉시 삭제 유도

        // 생성 시 사용한 domain 설정과 일치해야 삭제된다.
        if (!cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        return builder.build();
    }

    // ==========================================
    // 3. 검증 및 클레임 추출
    // ==========================================
    /**
     * 토큰을 검증하고 클레임을 반환한다. 실패 사유를 예외로 그대로 전파한다.
     *
     * <p>인증 필터는 "만료(→ 재발급 유도)"와 "위조(→ 재로그인)"를 구분해서 응답해야 하므로
     * boolean이 아니라 이 메서드를 사용한다.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException 만료된 토큰
     * @throws JwtException 서명 불일치 등 유효하지 않은 토큰
     */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 유효성만 boolean으로 확인한다.
     *
     * <p>주의: 실패 사유를 삼킨다. 클라이언트에게 만료/위조를 구분해 알려야 하는 곳에서는
     * {@link #parseClaims(String)}를 쓴다.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
    }
}
