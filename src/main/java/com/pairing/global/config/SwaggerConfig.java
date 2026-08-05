package com.pairing.global.config;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ErrorResponse;
import com.pairing.global.exception.BaseErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.time.Instant;

@Configuration
public class SwaggerConfig {

    /**
     * @ApiErrorCodeExample 이 붙은 컨트롤러 메서드를 스캔해
     * 해당 에러코드의 응답 예시를 Swagger 문서에 자동으로 추가한다.
     */
    @Bean
    public OperationCustomizer customErrorCodeCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            ApiErrorCodeExample[] annotations = handlerMethod.getMethod().getAnnotationsByType(ApiErrorCodeExample.class);
            if (annotations.length == 0) {
                return operation;
            }

            ApiResponses responses = operation.getResponses();

            for (ApiErrorCodeExample apiExample : annotations) {
                Class<? extends BaseErrorCode> domainClass = apiExample.domain();

                for (String codeName : apiExample.value()) {
                    BaseErrorCode errorCode = getErrorCodeInstance(domainClass, codeName);

                    if (errorCode == null) continue;

                    String statusCode = String.valueOf(errorCode.getStatus().value());

                    // 문서용 예시이므로 traceId는 고정값을 넣는다.
                    ErrorResponse errorResponseExample = new ErrorResponse(
                            Instant.parse("2026-05-21T07:09:00Z"),
                            errorCode.getStatus().value(),
                            errorCode.getCode(),
                            errorCode.getMessage(),
                            "example-trace-id-1234"
                    );

                    Example example = new Example();
                    example.value(errorResponseExample);
                    example.description(errorCode.getMessage());

                    ApiResponse apiResponse = responses.containsKey(statusCode)
                            ? responses.get(statusCode)
                            : new ApiResponse().description(errorCode.getStatus().getReasonPhrase());

                    Content content = apiResponse.getContent();
                    if (content == null) content = new Content();

                    MediaType mediaType = content.get("application/json");
                    if (mediaType == null) mediaType = new MediaType();

                    String exampleKey = domainClass.getSimpleName().toUpperCase() + "_" + codeName;
                    mediaType.addExamples(exampleKey, example);

                    content.addMediaType("application/json", mediaType);
                    apiResponse.setContent(content);

                    responses.addApiResponse(statusCode, apiResponse);
                }
            }
            return operation;
        };
    }

    private BaseErrorCode getErrorCodeInstance(Class<? extends BaseErrorCode> domainClass, String codeName) {
        if (domainClass.isEnum()) {
            for (BaseErrorCode enumConstant : domainClass.getEnumConstants()) {
                if (((Enum<?>) enumConstant).name().equals(codeName)) {
                    return enumConstant;
                }
            }
        }
        return null;
    }

    /** Swagger UI에서 JWT 토큰을 넣을 수 있도록 bearerAuth 스킴을 등록한다. */
    @Bean
    public OpenAPI openAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("Template Server API")
                        .description("프로젝트 기본 템플릿 API 문서")
                        .version("v1"))
                .addSecurityItem(securityRequirement)
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme));
    }
}
