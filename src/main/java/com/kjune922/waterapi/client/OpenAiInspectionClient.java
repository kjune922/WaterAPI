package com.kjune922.waterapi.client;

import com.kjune922.waterapi.exception.OpenAiAnalysisException;
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

            /**
             * <외부 서버에 POST 요청을 보냄>
             * restClient.post() : POST 요청 준비
             * .uri("/responses") : 요청할 경로 저장
             * .body(requestBody) : 요청 본문에 앞에서 만든 데이터 넣기
             * .retrieve() : 응답을 받아 처리
             * .body(String.class) : 응답 본문을 문자열로 받기
             *
             * 원래는 컨트롤러에서 HTTP요청을 받은 쪽이었지만
             * 이제는 내 애플리케이션이 HTTP요청을 보내는 쪽임
             */
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null ||
                    responseBody.isBlank()) {
                throw new OpenAiAnalysisException(
                        "OpenAI 응답이 비어 있습니다."
                );
            }

            /**
             * 응답에서 분석 결과 부분을 꺼냄
             * responseBody는 서버가 반환한 전체 응답이고 그 안의 output과 content를 살펴보고
             * type이 output_text 인 항목의 text를 꺼낸다
             */
            String analysisJson =
                    extractOutputText(responseBody);

            /**
             * 분석 결과 JSON을 DTO로 변환
             * ObjectMapper가 JSON값을 읽어서 InsepctionAnalysisResponse DTO 필드로 담아줌
             */
            return objectMapper.readValue(
                    analysisJson,
                    InspectionAnalysisResponse.class
            );
        } catch (RestClientException |
                 JacksonException e) {
            throw new OpenAiAnalysisException(
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


        /**
         * 앞선 properties 는 각 항목의 타입을 정했다면, schema 는 응답 JSON 전체의 구조와 규칙을 정함
         * "type", "object" : 결과가 {...} 형태의 JSON 객체
         * "properties", properties : 객체 안의 항목은 앞에서 정의한 타입과 조건을 따름
         * "required" , List.of(...) : 나열한 4개의 항목이 모두 있어야 함
         * "additionalProperties", false : 정의한 항목 외엔 다른 항목 추가 X
         *
         * ex)
         * {
         *   "summary": "점검 내용 요약",
         *   "abnormalityType": "이상 유형",
         *   "riskLevel": "CAUTION",
         *   "recommendedAction": "권장 조치"
         * }
         */

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

        /**
         * schema를 어떤 응답 형식으로 사용할지 지정하는 format
         * "type", "json_schema" : JSON Schema 로 응답형식 지정
         * "name", "inspection_analysis" : 이 응답 형식에 붙인 이름
         * "strict", true : 지정한 스키마를 엄격하게 따르도록 요청
         * "schema", schema : 앞에서 만든 실제 구조와 규칙 전달
         *
         * format은 마지막 요청 Map 안에 들어가서
         * 가장 바깥 Map의 "text" 값으로 Map이 들어가고 그 안에 "format" 값으로 만든 Map이 들어감
         */

        Map<String, Object> format = Map.of(
                "type", "json_schema",
                "name", "inspection_analysis",
                "strict", true,
                "schema", schema
        );

        /**
         * instructions : 어떻게 분석할지 설명하는 지침
         * text.format : 분석 결과를 어떤 json 구조로 받을지 지정하는 정보
         */

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

    /**
     * 완성된 최종형태 예시
     * {
     *   "text": {
     *     "format": {
     *       "type": "json_schema",
     *       "name": "inspection_analysis",
     *       "strict": true,
     *       "schema": {
     *         "type": "object",
     *         "properties": "...앞에서 만든 항목별 규칙...",
     *         "required": [
     *           "summary",
     *           "abnormalityType",
     *           "riskLevel",
     *           "recommendedAction"
     *         ],
     *         "additionalProperties": false
     *       }
     *     }
     *   }
     * }
     */

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

        throw new OpenAiAnalysisException(
                "OpenAI 응답에서 분석 결과를 찾을 수 없습니다."
        );
    }
}