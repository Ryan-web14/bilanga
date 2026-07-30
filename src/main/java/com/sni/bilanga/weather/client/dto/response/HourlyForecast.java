package com.sni.bilanga.weather.client.dto.response;

import java.time.Instant;

/**
 * Une échéance horaire, telle que le fournisseur la rend.
 *
 * <p>Volontairement neutre : le nom des champs d'Open-Meteo
 * ({@code temperature_2m}, {@code relative_humidity_2m}) ne remonte pas
 * au-delà du client. Changer de fournisseur ne doit toucher que
 * l'implémentation, pas le moteur qui consomme ces valeurs.
 */
public record HourlyForecast(Instant at,
                             Double temperature,
                             Double humidite,
                             Double precipitationMm,
                             Double windSpeed,
                             Double cloudCover) {
}
