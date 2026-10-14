package com.paisavasool.app.integration.optimizer;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OptimizerProperties.class)
public class OptimizerConfiguration {

    @Bean
    RestClient optimizerRestClient(RestClient.Builder builder, OptimizerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
            .baseUrl(properties.baseUrl().toString())
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    OptimizerClient optimizerClient(RestClient optimizerRestClient) {
        return new RestClientOptimizerClient(optimizerRestClient);
    }
}
