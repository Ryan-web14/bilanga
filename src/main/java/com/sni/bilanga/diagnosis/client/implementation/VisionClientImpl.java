package com.sni.bilanga.diagnosis.client.implementation;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.client.dto.response.VisionPrediction;
import com.sni.bilanga.diagnosis.client.interfaces.VisionClient;
import com.sni.bilanga.diagnosis.client.support.MlHttpExchange;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.utils.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Classification d'image. Le délai d'attente est nettement plus large que pour le
 * tabulaire : un réseau convolutif sur une photo pleine résolution demande
 * plusieurs secondes, et l'exploitant préfère attendre que perdre sa prise de vue.
 */
@Service
@RequiredArgsConstructor
public class VisionClientImpl implements VisionClient {

    private static final String LABEL = "vision";

    private final MlHttpExchange exchange;
    private final BilangaProperties.Ml ml;



    @Override
    public VisionPrediction predict(String crop, MultipartFile image) {
        Map<String, Object> payload = Map.of(
                "crop", crop,
                "imageBase64", encode(image)
        );

        return exchange.post(ml.getBaseUrl() + "/predict/vision-b64", payload, VisionPrediction.class,
                Duration.ofSeconds(ml.getVisionTimeoutSeconds()), LABEL);
    }

    /**
     * L'échec de lecture du fichier reçu vient de la requête, pas du modèle :
     * le signaler comme une indisponibilité du service induirait en erreur.
     */
    private String encode(MultipartFile image) {
        try {
            return Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            throw new BusinessRuleException(
                    "L'image transmise n'a pas pu être lue.", ErrorCode.INVALID_FILE_FORMAT, e);
        }
    }
}
