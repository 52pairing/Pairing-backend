package com.pairing.global.security;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentAccountId} 파라미터를 계정 ID로 바꿔준다.
 *
 * <p>JWT 필터가 토큰 subject(계정 ID)를 principal 문자열로 넣어두므로 그것을 꺼내 Long으로 변환한다.
 * WebConfig 에서 등록한다.
 */
@Component
public class CurrentAccountIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAccountId.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            // 우리가 발급한 토큰이면 subject 는 항상 계정 ID다. 아니면 인증으로 보지 않는다.
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
