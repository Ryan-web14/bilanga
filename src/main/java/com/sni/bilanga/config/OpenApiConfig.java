package com.sni.bilanga.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Description du contrat d'API.
 *
 * Le contrat n'était décrit nulle part : pour savoir ce que renvoie une route,
 * il fallait ouvrir le contrôleur. Deux points méritent d'être énoncés
 * explicitement, parce qu'ils surprennent et qu'aucune signature ne les révèle :
 * la sérialisation des entiers longs en chaînes, et l'exception que constitue
 * la route d'ingestion.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String DEVICE_KEY = "deviceKey";

    @Bean
    public OpenAPI bilangaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bilanga API")
                        .version("v1")
                        .description("""
                                Orchestrateur de la plateforme d'agriculture intelligente : \
                                ingestion des relevés, diagnostic (modèles + moteur agronomique), \
                                recommandations et alertes.

                                **Enveloppe** — toutes les réponses sont encadrées par `ApiResponse` :
                                `{ success, message, errorCode, timestamp, data }`. Les erreurs \
                                partagent les mêmes champs de tête et ajoutent `status`, `traceId` \
                                et, en cas de validation, `errors`. Le client n'a donc qu'à tester \
                                `success`.

                                **Identifiants** — tous les entiers longs, identifiants Snowflake \
                                compris, sont sérialisés **en chaînes**. Un identifiant à 19 \
                                chiffres dépasse l'entier sûr de JavaScript et serait arrondi \
                                silencieusement. Cela vaut aussi pour les compteurs, dont \
                                `pageInfo.totalElements`.

                                **Exception** — `POST /ingest/readings` renvoie son résultat \
                                sans enveloppe : le firmware du boîtier analyse ce corps tel \
                                quel, et un niveau d'imbrication de plus coûte cher sur un \
                                microcontrôleur. Ses **erreurs** suivent en revanche le format \
                                commun.

                                **Pagination** — `page`, `size`, `sort` en paramètres ; le \
                                résultat porte `data` et `pageInfo`.
                                """))
                .servers(List.of(new Server().url("/").description("Instance courante")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton d'accès obtenu via POST /auth/login."))
                        .addSecuritySchemes(DEVICE_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Device-Key")
                                .description("""
                                        Clé partagée du matériel de terrain, pour les routes \
                                        d'ingestion. Un microcontrôleur ne gère pas de cycle de \
                                        vie de jeton : l'authentification des objets est \
                                        distincte de celle des utilisateurs.""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
