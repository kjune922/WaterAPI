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

    /**
     * openai 프로필이 활성화됐을 때 이 프로필 사용
     * 스프링이 openAiRestClient 메서드를 호출하고 반환된 RestClient 객체를
     * openAiRestClient 라는 이름의 빈으로 등록한다.
     * apikey 에는 설정의 openai.api-key 값이 들어온다.
     * 환경변수 OPENAI_API_KEY 를 쓰고 없으면 예외를 잡는다
     *
     * <객체 구성 부분>
     * RestClient.builder() : 객체를 만들기 위한 빌더 생성
     * .baseUrl("https://api.openai.com/v1") : 기본 서버 주소
     * .defaultHeader(AUTHORIZATION, "Bearer " + apikey) : 요청에 기본으로 넣을 인증 헤더
     * .dfeaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE) : 요청 본문을 JSON 형식으로 보낸다는 헤더
     * .build() : 위 설정들을 적용한 RestClient 객체 생성
     *
     * 이제 restClient.post().uri("/responses") 를 실행하면
     * 기본 주소와 경로가 연결돼서 https://api.openai.com/v1/responses 로 요청하고, 설정한 기본 헤더 함께 사용함
     * 참고로 디폴트 값은 stub 이다
     */

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
