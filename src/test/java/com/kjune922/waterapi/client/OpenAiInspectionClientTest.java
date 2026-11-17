package com.kjune922.waterapi.client;

import com.kjune922.waterapi.exception.OpenAiAnalysisException;
import com.kjune922.waterapi.domain.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiInspectionClientTest {
    private MockRestServiceServer server;
    private OpenAiInspectionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiInspectionClient(builder.build(), new ObjectMapper(), "test-model");
    }

    @Test
    void 구조화된_결과를_읽는다() {
        String response = responseWithText("""
                {"analyzable":true,"analysis":{"summary":"진동 발생","abnormalityType":"VIBRATION",
                "riskLevel":"CAUTION","recommendedAction":"담당자 확인"}}
                """);
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(client.analyze("진동 발생").getRiskLevel()).isEqualTo(RiskLevel.CAUTION);
        server.verify();
    }

    @Test
    void 사용량_제한은_분석실패로_전달한다() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.analyze("진동 발생"))
                .isInstanceOf(OpenAiAnalysisException.class);
        server.verify();
    }

    @Test
    void 거절_응답은_분석결과로_취급하지_않는다() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("""
                        {"output":[{"content":[{"type":"refusal","refusal":"cannot analyze"}]}]}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.analyze("입력"))
                .isInstanceOf(OpenAiAnalysisException.class);
        server.verify();
    }

    @Test
    void 잘못된_JSON은_분석실패로_전달한다() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.analyze("입력"))
                .isInstanceOf(OpenAiAnalysisException.class);
        server.verify();
    }

    @Test
    void 정보가_부족하면_분석결과로_반환하지_않는다() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(responseWithText("{\"analyzable\":false,\"analysis\":null}"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.analyze("점검했습니다"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관찰 정보가 부족");
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{}", "{\"analyzable\":\"true\",\"analysis\":{}}", "{\"analyzable\":true,\"analysis\":null}"
    })
    void 불완전한_분석가능_응답을_거부한다(String json) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(responseWithText(json), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.analyze("진동 발생"))
                .isInstanceOf(OpenAiAnalysisException.class);
        server.verify();
    }

    private String responseWithText(String json) {
        return new ObjectMapper().writeValueAsString(java.util.Map.of(
                "output", java.util.List.of(java.util.Map.of("content", java.util.List.of(
                        java.util.Map.of("type", "output_text", "text", json))))));
    }
}
