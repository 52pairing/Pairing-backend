package com.pairing.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pairing.account.domain.model.SocialProvider;
import com.pairing.auth.application.port.SocialProfile;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.OAuthSettings;
import com.pairing.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 OAuth 2.0 연동.
 *
 * <p>Spring OAuth2 Client 대신 직접 호출한다. 이 서버는 세션 없이 자체 JWT 쿠키를 쓰고
 * 콜백도 프론트가 받아서 넘겨주는 구조라, 자동설정의 리다이렉트 흐름을 되돌리는 코드가 더 많아진다.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements SocialOAuthClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final OAuthSettings oAuthSettings;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthSettings oAuthSettings, RestClient.Builder restClientBuilder) {
        this.oAuthSettings = oAuthSettings;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthSettings.Registration registration = registration();

        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", registration.getClientId())
                .queryParam("redirect_uri", registration.getRedirectUri())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public SocialProfile fetchProfile(String code) {
        String accessToken = exchangeToken(code);
        JsonNode user = requestUserInfo(accessToken);
        if (user == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }

        JsonNode kakaoAccount = user.path("kakao_account");
        String email = kakaoAccount.path("email").asText(null);
        boolean emailVerified = kakaoAccount.path("is_email_verified").asBoolean(false);
        String nickname = kakaoAccount.path("profile").path("nickname").asText(null);

        if (email == null || email.isBlank()) {
            // 이메일 제공 동의를 받지 못하면 계정 식별과 중복 판정을 할 수 없다.
            log.warn("카카오 계정에서 이메일을 받지 못했다.");
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }

        return new SocialProfile(SocialProvider.KAKAO, user.path("id").asText(), email, emailVerified, nickname);
    }

    private String exchangeToken(String code) {
        OAuthSettings.Registration registration = registration();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", registration.getClientId());
        form.add("redirect_uri", registration.getRedirectUri());
        form.add("code", code);
        if (!registration.getClientSecret().isBlank()) {
            form.add("client_secret", registration.getClientSecret());
        }

        JsonNode response = post(TOKEN_URL, form);
        String accessToken = response == null ? null : response.path("access_token").asText(null);

        if (accessToken == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
        return accessToken;
    }

    private JsonNode post(String url, MultiValueMap<String, String> form) {
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("카카오 토큰 교환 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    private JsonNode requestUserInfo(String accessToken) {
        try {
            return restClient.get()
                    .uri(USER_INFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("카카오 사용자 조회 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    private OAuthSettings.Registration registration() {
        OAuthSettings.Registration registration = oAuthSettings.getKakao();
        if (!registration.isConfigured()) {
            log.warn("카카오 OAuth 설정이 비어 있다. KAKAO_CLIENT_ID / KAKAO_REDIRECT_URI 를 확인하라.");
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
        return registration;
    }
}
