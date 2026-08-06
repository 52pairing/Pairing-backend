package com.pairing.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 로그인한 계정 ID를 주입한다.
 *
 * <pre>{@code
 * @GetMapping("/api/v1/projects/mine")
 * public ResponseEntity<ApiResponse<List<ProjectResponse>>> myProjects(@CurrentAccountId Long accountId) { ... }
 * }</pre>
 *
 * <p>도메인마다 {@code Long.valueOf(authentication.getName())} 을 반복하면
 * 형식이 어긋났을 때 500이 나거나, 비로그인 처리를 빠뜨리게 된다.
 *
 * <p>비로그인 요청에는 401(GLOBAL_006)로 응답한다. 로그인 여부를 컨트롤러가 직접 판단하지 않아도 된다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentAccountId {
}
