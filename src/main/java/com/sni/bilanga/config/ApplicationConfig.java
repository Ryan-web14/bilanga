package com.sni.bilanga.config;


import com.sni.bilanga.config.properties.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ApplicationConfig {

    private final Environment environment;

    private final AppProperties appProperties;

    public ApplicationConfig(Environment environment, AppProperties appProperties) {
        this.environment = environment;
        this.appProperties = appProperties;
    }

    @Bean
    public boolean isDevEnvironment() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equals("dev") || profile.equals("test")) {
                return true;
            }
        }
        return appProperties.getError().isVerbose();
    }

}
