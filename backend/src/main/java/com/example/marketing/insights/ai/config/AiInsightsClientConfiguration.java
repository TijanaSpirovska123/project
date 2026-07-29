package com.example.marketing.insights.ai.config;

import com.example.marketing.infrastructure.logging.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiInsightsProperties.class)
public class AiInsightsClientConfiguration {

    @Bean
    @Qualifier("aiInsightsRestClient")
    public RestClient aiInsightsRestClient(RestClient.Builder builder, AiInsightsProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.connectionTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((httpRequest, body, execution) -> {
                    String requestId = MDC.get(RequestIdFilter.MDC_KEY);

                    if (requestId != null) {
                        httpRequest.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
                    }

                    return execution.execute(httpRequest, body);
                })
                .build();
    }


}
