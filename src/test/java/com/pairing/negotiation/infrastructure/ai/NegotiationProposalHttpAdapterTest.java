package com.pairing.negotiation.infrastructure.ai;

import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort.ConditionInput;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort.ProposalContext;
import com.pairing.negotiation.domain.model.ConditionType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 파이썬 서버가 없을 때 stub 으로 폴백해 모든 조건에 제안이 채워지는지(루프가 안 멈추는지) 검증. */
class NegotiationProposalHttpAdapterTest {

    @Test
    @DisplayName("파이썬 호출 실패 시 stub 폴백 — 모든 조건에 제안이 채워진다")
    void fallsBackToStubWhenPythonUnreachable() {
        // 아무도 안 듣는 주소 → 연결 거부 → 폴백. 타임아웃 짧게.
        NegotiationProposalHttpAdapter adapter =
                new NegotiationProposalHttpAdapter("http://localhost:1", "test-key", 500);

        ProposalContext context = new ProposalContext(1L, 1, 5_000_000L, List.of(
                new ConditionInput(401L, ConditionType.AMOUNT, "4000000", "6000000", null, null),
                new ConditionInput(402L, ConditionType.WORK_STYLE, "ONSITE", "REMOTE", null, null)));

        NegotiationProposalPort.A2AResult result = adapter.propose(context);

        // 모든 조건에 결과(outcome)가 채워진다 → 루프가 멈추지 않는다.
        assertThat(result.outcomes()).hasSize(2);
        assertThat(result.outcomes()).extracting(NegotiationProposalPort.ConditionOutcome::conditionId)
                .containsExactlyInAnyOrder(401L, 402L);
        // AMOUNT 는 숫자라 중간값(stub)
        assertThat(result.outcomes().stream()
                .filter(o -> o.conditionId().equals(401L)).findFirst().orElseThrow().proposedValue())
                .isEqualTo("5000000");
        // stub 폴백도 대리인 대화 로그(메시지)를 남긴다.
        assertThat(result.messages()).isNotEmpty();
        assertThat(result.messages())
                .allMatch(m -> m.conditionId().equals(401L) || m.conditionId().equals(402L));
    }

    @Test
    @DisplayName("파이썬이 application/octet-stream 으로 내려줘도 JSON 으로 파싱한다 — stub 폴백 안 함")
    void parsesOctetStreamResponseAsJson() throws Exception {
        // 파이썬 대화값(5500000)을 담은 정상 JSON 본문을, content-type 만 octet-stream 으로 내려준다.
        String body = "{\"code\":\"NEGOTIATION_PROPOSED\",\"message\":\"ok\",\"data\":{"
                + "\"negotiation_id\":1,\"model\":\"m\","
                + "\"messages\":[{\"sender\":\"CLIENT_AGENT\",\"condition_id\":401,\"kind\":\"PROPOSAL\","
                + "\"proposed_value\":\"5000000\",\"content\":\"c\",\"reason\":\"r\"},"
                + "{\"sender\":\"FREELANCER_AGENT\",\"condition_id\":401,\"kind\":\"ACCEPT\","
                + "\"proposed_value\":\"5500000\",\"content\":\"c\",\"reason\":\"r\"}],"
                + "\"outcomes\":[{\"condition_id\":401,\"proposed_value\":\"5500000\",\"agreed\":true}]}}";

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/negotiations/propose", exchange -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            NegotiationProposalHttpAdapter adapter =
                    new NegotiationProposalHttpAdapter("http://127.0.0.1:" + port, "test-key", 2000);
            ProposalContext context = new ProposalContext(1L, 1, 5_000_000L, List.of(
                    new ConditionInput(401L, ConditionType.AMOUNT, "4000000", "6000000", null, null)));

            NegotiationProposalPort.A2AResult result = adapter.propose(context);

            // 파이썬 값(5500000)이 쓰였으면 octet-stream 을 JSON 으로 파싱한 것.
            // stub 폴백이었다면 중간값(5000000)이 나온다.
            assertThat(result.outcomes()).hasSize(1);
            assertThat(result.outcomes().get(0).proposedValue()).isEqualTo("5500000");
            assertThat(result.outcomes().get(0).agreed()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
