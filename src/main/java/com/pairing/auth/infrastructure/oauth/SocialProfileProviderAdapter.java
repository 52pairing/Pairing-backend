package com.pairing.auth.infrastructure.oauth;

import com.pairing.account.domain.model.SocialProvider;
import com.pairing.auth.application.port.SocialProfile;
import com.pairing.auth.application.port.SocialProfileProviderPort;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SocialProfileProviderAdapter implements SocialProfileProviderPort {

    private final Map<SocialProvider, SocialOAuthClient> clients = new EnumMap<>(SocialProvider.class);

    public SocialProfileProviderAdapter(List<SocialOAuthClient> socialOAuthClients) {
        socialOAuthClients.forEach(client -> clients.put(client.provider(), client));
    }

    @Override
    public String buildAuthorizeUrl(SocialProvider provider, String state) {
        return resolve(provider).buildAuthorizeUrl(state);
    }

    @Override
    public SocialProfile fetchProfile(SocialProvider provider, String code) {
        return resolve(provider).fetchProfile(code);
    }

    private SocialOAuthClient resolve(SocialProvider provider) {
        SocialOAuthClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
        return client;
    }
}
