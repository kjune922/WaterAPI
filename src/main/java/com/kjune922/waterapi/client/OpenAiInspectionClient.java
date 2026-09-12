package com.kjune922.waterapi.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
@Profile("openai")
public class OpenAiInspectionClient
        implements InspectionAiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiInspectionClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${openai.model}") String model
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public InspectionAnalysisResponse analyze(
            String inspectionContent
    ) {
        try {
            Map<String, Object> requestBody =
                    createRequestBody(inspectionContent);

            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null ||
                    responseBody.isBlank()) {
                throw new IllegalStateException(
                        "OpenAI 응답이 비어 있습니다."
                );
            }

            String analysisJson =
                    extractOutputText(responseBody);

            return objectMapper.readValue(
                    analysisJson,
                    InspectionAnalysisResponse.class
            );
        } catch (RestClientException |
                 JacksonException e) {
            throw new IllegalStateException(
                    "OpenAI 분석 요청에 실패했습니다.",
                    e
            );
        }
    }

    private Map<String, Object> createRequestBody(
            String inspectionContent
    ) {
        Map<String, Object> properties = Map.of(
                "summary",
                Map.of("type", "string"),

                "abnormalityType",
                Map.of("type", "string"),

                "riskLevel",
                Map.of(
                        "type", "string",
                        "enum", List.of(
                                "NORMAL",
                                "CAUTION",
                                "WARNING"
                        )
                ),

                "recommendedAction",
                Map.of("type", "string")
        );

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of(
                        "summary",
                        "abnormalityType",
                        "riskLevel",
                        "recommendedAction"
                ),
                "additionalProperties", false
        );

        Map<String, Object> format = Map.of(
                "type", "json_schema",
                "name", "inspection_analysis",
                "strict", true,
                "schema", schema
        );

        return Map.of(
                "model", model,
                "instructions", """
                        당신은 상하수도 시설 점검 담당자를
                        보조하는 시스템입니다.

                        입력된 점검 기록을 분석해 핵심 내용,
                        이상 유형, 위험도와 권장 조치를
                        한국어로 작성하세요.

                        위험도는 NORMAL, CAUTION, WARNING 중
                        하나만 선택하세요.

                        AI 결과는 참고용이며 설비를 직접
                        제어하는 지시를 작성하지 마세요.
                        """,
                "input", inspectionContent,
                "text", Map.of("format", format)
        );
    }

    private String extractOutputText(String responseBody) {
        JsonNode root =
                objectMapper.readTree(responseBody);

        for (JsonNode output : root.path("output")) {
            for (JsonNode content :
                    output.path("content")) {

                if ("output_text".equals(
                        content.path("type").asText()
                )) {
                    String text =
                            content.path("text").asText();

                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        throw new IllegalStateException(
                "OpenAI 응답에서 분석 결과를 찾을 수 없습니다."
        );
    }
}