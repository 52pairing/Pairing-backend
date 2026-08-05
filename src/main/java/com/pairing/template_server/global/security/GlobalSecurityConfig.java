package com.pairing.template_server.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 컨트롤러/서비스의 @PreAuthorize 활성화
@RequiredArgsConstructor
public class GlobalSecurityConfig {

    private final GlobalJwtAuthenticationFilter globalJwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS는 시큐리티 체인에서 전담한다. (WebConfig에서 중복 설정하지 않음)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. 누구나 접근 가능 (인증, 마스터 데이터, 문서, 헬스체크)
                        //    새 공개 API가 생기면 반드시 이 목록에 명시적으로 추가해야 한다.
                        .requestMatchers(
                                "/api/v1/auth/**",
                                // 직군·직무·스킬 등 선택 목록과 등급·수수료 안내, 비로그인 메인 페이지 정보
                                "/api/v1/meta/**",
                                // 소셜 로그인 리다이렉트 경로
                                "/oauth2/**",
                                "/login/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info",
                                // STOMP 핸드셰이크. WebSocket 프레임에는 시큐리티 필터가 걸리지 않으므로
                                // 인증은 JwtHandshakeInterceptor가 핸드셰이크 시점에 한 번 수행한다.
                                // 경로는 app.websocket.endpoint 와 맞춰야 한다.
                                "/ws/**",
                                // 시큐리티가 ERROR 디스패치까지 인가 검사를 하므로(6.x 기본값),
                                // 열어두지 않으면 실제 예외가 401로 덮여 원인 파악이 어려워진다.
                                "/error"
                        ).permitAll()

                        // 2. 데모 도메인. 토큰 없이 바로 호출해볼 수 있게 열어두었다.
                        //    실제 프로젝트를 시작할 때 example 도메인과 함께 이 줄을 삭제한다.
                        .requestMatchers("/api/v1/examples/**").permitAll()

                        // 3. 관리자 전용
                        .requestMatchers("/api/v1/admin/**", "/api/v1/*/admin/**").hasRole("ADMIN")

                        // 4. 나머지는 인증 필수(fail-closed).
                        //    permitAll()을 기본값으로 두면 @PreAuthorize를 빠뜨린 새 API가 전체 공개되므로,
                        //    "깜빡하면 닫히는" 쪽이 안전하다. 세부 권한은 각 컨트롤러의 @PreAuthorize에서 처리한다.
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint) // 401 (인증 안 됨)
                        .accessDeniedHandler(accessDeniedHandler)           // 403 (권한 없음)
                )
                .addFilterBefore(globalJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 프론트엔드 도메인을 정확히 명시해야 브라우저가 허용한다. (credentials 사용 시 와일드카드 * 금지)
        configuration.setAllowedOriginPatterns(
                Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList()
        );

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        // 쿠키(인증 정보) 통신을 위해 true
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "X-Trace-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
