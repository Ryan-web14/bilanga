package com.sni.bilanga.diagnosis.client.implementation;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.client.dto.response.SoilPrediction;
import com.sni.bilanga.diagnosis.client.interfaces.TabularClient;
import com.sni.bilanga.diagnosis.client.support.MlHttpExchange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Analyse tabulaire du sol. L'inférence est légère — un RandomForest sur huit
 * mesures — d'où un délai d'attente court : au-delà, c'est le service qui va mal,
 * pas le calcul qui traîne.
 */
@Service
@RequiredArgsConstructor
public class TabularClientImpl implements TabularClient {

    private static final String LABEL = "sol";

    private final MlHttpExchange exchange;
    private final BilangaProperties.Ml ml;



    @Override
    public SoilPrediction predict(Map<String, Object> features) {
        return exchange.post(ml.getBaseUrl() + "/predict/soil", features, SoilPrediction.class,
                Duration.ofSeconds(ml.getSoilTimeoutSeconds()), LABEL);
    }
}
