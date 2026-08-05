package com.pairing.template_server.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 traceId를 발급해 MDC와 응답 헤더(X-Trace-Id)에 심는다.
 *
 * <p>순서가 중요하다. 스프링 시큐리티 필터체인은 order = -100(SecurityProperties.DEFAULT_FILTER_ORDER)으로
 * 등록되므로, 순서를 지정하지 않으면 이 필터가 시큐리티 체인보다 <b>뒤</b>에 실행된다.
 * 그러면 JWT 필터나 401/403 핸들러가 응답을 만드는 시점에는 MDC가 비어 있어 traceId가 없는 에러 응답이 나간다.
 * 가장 앞으로 당겨 인증 실패 응답에도 로그와 대조 가능한 traceId가 실리게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    // 예외 응답(ErrorResponse)에서도 공유할 수 있도록 public
    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Trace ID 생성 (UUID 앞 8자리)
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);

        // 비동기 스레드나 인터셉터 등에서 꺼내 쓸 수 있도록 request 컨텍스트에도 저장
        request.setAttribute(TRACE_ID_KEY, traceId);

        // 2. 프론트엔드 응답 헤더에 추가
        response.setHeader("X-Trace-Id", traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 3. 스레드 풀 누수 방지를 위한 자원 해제
            MDC.clear();
        }
    }

    /**
     * 현재 요청의 traceId를 반환한다. MDC → request attribute 순으로 찾고,
     * 둘 다 없으면(비동기 스레드 등) 새로 만들어 반환한다.
     */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null) {
            return traceId;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String cachedTraceId = (String) attributes.getRequest().getAttribute(TRACE_ID_KEY);
            if (cachedTraceId != null) {
                return cachedTraceId;
            }
        }

        return UUID.randomUUID().toString().substring(0, 8);
    }
}
