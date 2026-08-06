package com.pairing.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
@Aspect
@Component
public class ApiLoggingAop {

    /**
     * 로그에 남으면 안 되는 값들.
     *
     * <p>Command/Request는 record라 toString()에 모든 필드가 그대로 찍힌다. 그대로 두면
     * 비밀번호 평문, 카드번호, 계좌번호, 인증코드, 토큰이 로그 파일에 쌓인다.
     * 필드 이름 기준으로 값을 지운 뒤 출력한다.
     *
     * <p>새 도메인에서 민감한 필드명을 추가하면 이 목록에도 넣는다.
     */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(password|passwordConfirm|passwordHash|newPassword|currentPassword"
                    + "|cardNumber|accountNo|code|token|ticket|secret|refreshToken|accessToken)"
                    + "=([^,\\]]*)");

    private static final String MASK = "***";

    // ==========================================
    // 1. Pointcut 정의
    // ==========================================

    // Presentation 계층 (Controller)
    @Pointcut("execution(* com.pairing.*..presentation..*Controller.*(..))")
    private void controllerPointcut() {}

    // Application 계층 (Service, UseCase)
    @Pointcut("execution(* com.pairing.*..application..*Service.*(..)) || " +
            "execution(* com.pairing.*..application..*UseCase.*(..))")
    private void applicationPointcut() {}


    // ==========================================
    // 2. Controller 로깅 (HTTP 요청/응답 전문)
    // ==========================================
    @Around("controllerPointcut()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        String userId = "Anonymous";
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            userId = auth.getName();
        }

        String method = request.getMethod();
        String requestUri = request.getRequestURI();
        String controllerName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.info("[API Request] User: {} | {} {} | Controller: {}.{}()",
                userId, method, requestUri, controllerName, methodName);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[API Response] {} {} | Time: {}ms", method, requestUri, executionTime);
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[API Error] {} {} | Time: {}ms | Exception: {}", method, requestUri, executionTime, e.getClass().getSimpleName());
            throw e;
        }
    }


    // ==========================================
    // 3. Service 로깅 (비즈니스 로직 중심, HTTP 몰라도 됨)
    // ==========================================
    @Around("applicationPointcut()")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // 서비스는 HTTP URI가 없으므로 클래스.메서드명과 파라미터만 찍는다.
        // 비밀번호·카드번호 같은 값은 마스킹한 뒤 남긴다.
        log.info("[Service Start] {}.{}() | Args: {}", serviceName, methodName, maskSensitive(args));

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            // 성능 경고 (비즈니스 로직이 1초 이상 걸리면 경고)
            if (executionTime > 1000) {
                log.warn("[Performance Warning] {}.{}() 실행 시간이 {}ms로 느립니다!", serviceName, methodName, executionTime);
            } else {
                log.info("[Service End] {}.{}() | Time: {}ms", serviceName, methodName, executionTime);
            }
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[Service Error] {}.{}() | Time: {}ms | Exception: {} | Msg: {}",
                    serviceName, methodName, executionTime, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }


    // ==========================================
    // 4. 민감 정보 마스킹
    // ==========================================
    // 테스트에서 직접 검증할 수 있도록 package-private 으로 둔다.
    static String maskSensitive(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        String rendered;
        try {
            rendered = Arrays.deepToString(args);
        } catch (RuntimeException e) {
            // toString()에서 터진 것 때문에 요청까지 실패시키지 않는다.
            return "[unprintable]";
        }

        return SENSITIVE_FIELD_PATTERN.matcher(rendered).replaceAll("$1=" + MASK);
    }
}
