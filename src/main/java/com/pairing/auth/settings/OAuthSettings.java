package com.pairing.auth.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 소셜 공급자 등록 정보. 값은 환경변수로만 주입한다.
 *
 * <p>기동 시점에 검증하지 않는 이유: 소셜 로그인을 쓰지 않는 로컬 개발에서도 서버는 떠야 한다.
 * 값이 비어 있으면 실제 호출 시점에 AU_018로 실패한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthSettings {

    private Registration kakao = new Registration();
    private Registration google = new Registration();

    @Getter
    @Setter
    public static class Registration {

        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";

        public boolean isConfigured() {
            return !clientId.isBlank() && !redirectUri.isBlank();
        }
    }
}
