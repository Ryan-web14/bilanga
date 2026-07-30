package com.sni.bilanga.weather.client.interfaces;

import com.sni.bilanga.weather.client.dto.response.HourlyForecast;

import java.util.List;

/**
 * Accès aux prévisions d'un fournisseur externe.
 *
 * <p>Interface, comme pour le microservice d'inférence : le moteur météo dépend
 * de ce contrat et jamais du transport. Changer de fournisseur — ou en substituer
 * un jeu de données fixe pour une démonstration — ne doit toucher qu'une
 * implémentation.
 */
public interface WeatherClient {

    /** Nom du fournisseur, consigné avec chaque prévision mise en cache. */
    String provider();

    /**
     * Prévisions horaires pour un point, sur l'horizon configuré.
     *
     * @throws com.sni.bilanga.exception.customs.ServiceUnavailableException
     *         dès que le résultat n'est pas exploitable. L'appelant décide seul
     *         s'il peut se passer de la météo — et il le peut : le système doit
     *         rester utilisable sans elle.
     */
    List<HourlyForecast> forecast(double latitude, double longitude);
}
