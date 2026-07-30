package com.sni.bilanga.config;


import com.sni.bilanga.config.properties.BilangaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class MlClientConfig {

    private final BilangaProperties.Ml ml;

    @Bean
    public RestClient mlRestClient() {
        return RestClient.builder()
                .baseUrl(ml.getBaseUrl())
                .build();
    }
}