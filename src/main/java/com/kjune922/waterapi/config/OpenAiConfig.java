package com.kjune922.waterapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("openai")
public class OpenAiConfig {

    @Bean
    public RestClient openAiRestClient(@Value("${openai.api-key}") String apikey) {
        if(apikey == null || apikey.isBlank()){
            throw new IllegalArgumentException("OPENAI_API_KEY 환경변수가 필요합니다.");
        }

        return RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer " + apikey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
    }
}
