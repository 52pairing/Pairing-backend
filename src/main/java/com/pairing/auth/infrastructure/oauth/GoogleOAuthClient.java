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

/** 구글 OAuth 2.0 연동. 사용자 식별자는 sub 값이다. */
@Slf4j
@Component
public class GoogleOAuthClient implements SocialOAuthClient {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String SCOPE = "openid email profile";

    private final OAuthSettings oAuthSettings;
    private final RestClient restClient;

    public GoogleOAuthClient(OAuthSettings oAuthSettings, RestClient.Builder restClientBuilder) {
        this.oAuthSettings = oAuthSettings;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        OAuthSettings.Registration registration = registration();

        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", registration.getClientId())
                .queryParam("redirect_uri", registration.getRedirectUri())
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public SocialProfile fetchProfile(String code) {
        String accessToken = exchangeToken(code);
        JsonNode user = requestUserInfo(accessToken);
        if (user == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }

        String email = user.path("email").asText(null);
        if (email == null || email.isBlank()) {
            log.warn("구글 계정에서 이메일을 받지 못했다.");
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }

        return new SocialProfile(
                SocialProvider.GOOGLE,
                user.path("sub").asText(),
                email,
                user.path("email_verified").asBoolean(false),
                user.path("name").asText(null)
        );
    }

    private String exchangeToken(String code) {
        OAuthSettings.Registration registration = registration();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        form.add("redirect_uri", registration.getRedirectUri());
        form.add("code", code);

        try {
            JsonNode response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            String accessToken = response == null ? null : response.path("access_token").asText(null);
            if (accessToken == null) {
                throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
            }
            return accessToken;
        } catch (RestClientException e) {
            log.warn("구글 토큰 교환 실패: {}", e.getMessage());
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
            log.warn("구글 사용자 조회 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    private OAuthSettings.Registration registration() {
        OAuthSettings.Registration registration = oAuthSettings.getGoogle();
        if (!registration.isConfigured()) {
            log.warn("구글 OAuth 설정이 비어 있다. GOOGLE_CLIENT_ID / GOOGLE_REDIRECT_URI 를 확인하라.");
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
        return registration;
    }
}
