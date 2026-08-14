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

    /**
     * {@code defaultCandidate = false}: <b>타입만 보고 주입되지 않게 막는다.</b>
     *
     * <p>이 레포의 다른 어댑터들(OAuth·계약·협상·챗봇)은 각자 {@code RestClient}를 내부에서 만든다.
     * 그래서 이게 컨텍스트의 유일한 {@code RestClient} 빈인데, 누가 나중에 생성자에
     * {@code RestClient}만 적으면 <b>AI 서버를 가리키는 이 클라이언트가 딸려간다</b>
     * (baseUrl이 AI 서버, 읽기 타임아웃 60초). 이름을 명시한 곳에만 주입되게 한다.
     */
    @Bean(defaultCandidate = false)
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
