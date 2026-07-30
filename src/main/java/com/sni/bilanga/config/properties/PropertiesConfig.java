package com.sni.bilanga.config.properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expose chaque groupe de réglages comme un bean à part entière.
 *
 * <p>Sans cela, chaque classe devrait recevoir l'objet racine et écrire
 * {@code properties.getTrend().getWindowHours()} à chaque usage. En injectant
 * directement le groupe qui la concerne, une classe déclare exactement ce dont
 * elle dépend — et le compilateur refuse qu'elle lise un réglage d'un autre
 * domaine, ce que la lecture par {@code @Value} autorisait sans broncher.
 */
@Configuration
@EnableConfigurationProperties({BilangaProperties.class, AppProperties.class})
public class PropertiesConfig {

    @Bean
    public AppProperties.Security securityProperties(AppProperties properties) {
        return properties.getSecurity();
    }

    @Bean
    public BilangaProperties.Ml mlProperties(BilangaProperties properties) {
        return properties.getMl();
    }

    @Bean
    public BilangaProperties.Ingest ingestProperties(BilangaProperties properties) {
        return properties.getIngest();
    }

    @Bean
    public BilangaProperties.Diagnosis diagnosisProperties(BilangaProperties properties) {
        return properties.getDiagnosis();
    }

    @Bean
    public BilangaProperties.Confidence confidenceProperties(BilangaProperties properties) {
        return properties.getConfidence();
    }

    @Bean
    public BilangaProperties.Risk riskProperties(BilangaProperties properties) {
        return properties.getRisk();
    }

    @Bean
    public BilangaProperties.Agronomic agronomicProperties(BilangaProperties properties) {
        return properties.getAgronomic();
    }

    @Bean
    public BilangaProperties.Arbitration arbitrationProperties(BilangaProperties properties) {
        return properties.getArbitration();
    }

    @Bean
    public BilangaProperties.Trend trendProperties(BilangaProperties properties) {
        return properties.getTrend();
    }

    @Bean
    public BilangaProperties.SensorHealth sensorHealthProperties(BilangaProperties properties) {
        return properties.getSensorHealth();
    }

    @Bean
    public BilangaProperties.Weather weatherProperties(BilangaProperties properties) {
        return properties.getWeather();
    }

    @Bean
    public BilangaProperties.Neighbourhood neighbourhoodProperties(BilangaProperties properties) {
        return properties.getNeighbourhood();
    }

    @Bean
    public BilangaProperties.Overview overviewProperties(BilangaProperties properties) {
        return properties.getOverview();
    }

    @Bean
    public BilangaProperties.Alert alertProperties(BilangaProperties properties) {
        return properties.getAlert();
    }

    @Bean
    public BilangaProperties.Notification notificationProperties(BilangaProperties properties) {
        return properties.getNotification();
    }

    @Bean
    public BilangaProperties.Sms smsProperties(BilangaProperties properties) {
        return properties.getNotification().getSms();
    }

    @Bean
    public BilangaProperties.Email emailProperties(BilangaProperties properties) {
        return properties.getNotification().getEmail();
    }
}
