package com.pairing.matching.infrastructure.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 서버(Pairing-python) 호출용 {@link RestClient}.
 *
 * <p>어댑터 안에서 만들지 않고 빈으로 뺀 이유는 <b>테스트에서 갈아끼우기 위해서다</b>.
 * 어댑터가 직접 {@code requestFactory}를 지정하면 {@code MockRestServiceServer}가 심어둔 팩토리를
 * 덮어써서 HTTP를 가로챌 수 없고, 그러면 <b>재시도가 실제로 도는지 확인할 방법이 없다</b> —
 * 애노테이션과 설정의 조합은 조용히 안 먹는 일이 흔하다(실제로 차단기가 그 상태로 붙어만 있었다).
 */
@Configuration
public class PythonMatchingClientConfig {

    @Bean
    public RestClient pythonMatchingRestClient(RestClient.Builder builder,
                                               @Value("${ai.pairing-python.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        // 벡터 검색 + LLM 생성까지 걸리므로 읽기 타임아웃을 넉넉히 둔다(계약서 기준 60s).
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return builder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }
}
