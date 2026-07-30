package com.sni.bilanga.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Sérialise les entiers 64 bits sous forme de chaînes.
 *
 * Le générateur Snowflake produit des identifiants à dix-neuf chiffres. Or le
 * type numérique de JavaScript est un flottant double précision, dont le plus
 * grand entier exactement représentable s'arrête à seize chiffres. Au-delà,
 * l'analyse d'une réponse JSON arrondit sans lever d'erreur :
 *
 *   7277003285445382144  devient  7277003285445382000
 *
 * L'identifiant reçu par le client est alors faux, et toute requête ultérieure
 * sur cette ressource échoue en 404 sans cause apparente. Le défaut est
 * silencieux, différé, et coûteux à diagnostiquer.
 *
 * Transmettre ces valeurs en chaînes supprime la conversion numérique et donc
 * la perte de précision. En entrée, Jackson accepte indifféremment la forme
 * numérique et la forme textuelle : les clients existants restent compatibles.
 *
 * IMPORTANT — Jackson 3 : Spring Boot 4 sérialise le web avec Jackson 3
 * ({@code tools.jackson}), pas Jackson 2. Le module DOIT donc être un
 * {@link tools.jackson.databind.JacksonModule} ({@link SimpleModule} en est un) :
 * {@code JacksonAutoConfiguration} collecte ces beans et les applique au
 * {@code JsonMapper} du serveur. Un module Jackson 2 ({@code com.fasterxml})
 * serait ignoré — c'était le défaut corrigé ici.
 *
 * Portée : TOUT {@code Long}/{@code long} devient une chaîne, y compris des
 * compteurs comme {@code PageInfo.totalElements}. C'est l'intention d'origine
 * (sûreté de précision globale), mais cela impacte le contrat côté frontend.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule identifiersAsText() {
        SimpleModule module = new SimpleModule("bilanga-identifiers");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
