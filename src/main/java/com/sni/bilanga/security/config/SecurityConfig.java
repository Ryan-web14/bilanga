package com.sni.bilanga.security.config;


import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.security.authorization.AdminApiAuthorizationManager;
import com.sni.bilanga.security.filter.JWTFilter;
import com.sni.bilanga.security.ratelimit.RateLimitingFilter;
import com.sni.bilanga.security.service.user.CustomUserDetailService;
import com.sni.bilanga.utils.path.ApiPath;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.util.List;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private final CustomUserDetailService userDetailService;
    private final PasswordEncoder passwordEncoder;
    private final JWTFilter jwtFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final AdminApiAuthorizationManager adminApiAuthorizationManager;

    /**
     * Les mêmes réglages que ceux lus par le reste de l'application. Le motif
     * CORS et l'ouverture des routes métier étaient codés en dur : les modifier
     * demandait de recompiler, et rien ne les rendait visibles au démarrage —
     * {@code ConfigurationGuard} peut désormais les énoncer.
     */
    private final AppProperties.Security security;


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                // Safe, app-wide security headers. A strict Content-Security-Policy is applied
                // per-page on the server-rendered password-reset pages (see PasswordResetFormController)
                // rather than globally, so it does not break API/Swagger responses.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> {
                        auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                        // Routes réellement publiques : celles par lesquelles on
                        // entre, et qu'on ne peut donc pas exiger authentifiées.
                        auth.requestMatchers(
                                ApiPath.V1 + "/auth/login",
                                ApiPath.V1 + "/auth/refresh",
                                ApiPath.V1 + "/auth/register",
                                ApiPath.V1 + "/auth/ott/**",
                                ApiPath.V1 + "/auth/password-reset/**",
                                ApiPath.V1 + "/auth/unlock-account",
                                ApiPath.V1 + "/auth/unlock-account/confirm",
                                ApiPath.V1 + "/auth/email/verify/resend"
                        ).permitAll();

                        // Amorçage du tout premier compte.
                        //
                        // POURQUOI CETTE ROUTE EST OUVERTE. Sur une base neuve, aucun
                        // utilisateur n'existe. Les trois portes se fermaient jusqu'ici
                        // en même temps : DefaultAdminSeeder porte @Profile("dev"),
                        // bootstrap-admin.enabled est false et non surchargeable en
                        // prod, et cette route tombait sur AdminApiAuthorizationManager
                        // qui exige SYSTEM:USERS — donc 403 pour un appelant anonyme.
                        //
                        // Chaque décision était juste isolément ; leur conjonction
                        // donnait un déploiement où PERSONNE ne peut jamais entrer, avec
                        // un symptôme trompeur : l'application démarre, /actuator/health
                        // répond, et tout le reste renvoie 403.
                        //
                        // Exiger une permission pour créer le compte qui délivre les
                        // permissions est un cercle sans issue. C'est le motif standard
                        // d'une route d'amorçage.
                        //
                        // ⚠️ CE QUI REND L'OUVERTURE ACCEPTABLE : la route REFUSE de
                        // s'exécuter une seconde fois. UserProvisioningController répond
                        // 409 dès qu'un compte ADMIN existe. La fenêtre n'est donc
                        // ouverte qu'entre le premier démarrage et le premier appel.
                        //
                        // ⚠️ CE QUI RESTE À VOTRE CHARGE : appeler cette route
                        // IMMÉDIATEMENT après le premier déploiement. Tant qu'aucun
                        // administrateur n'existe, n'importe qui peut se créer le compte
                        // qui détient tous les droits. Seul /bootstrap-admin est ouvert ;
                        // /staff reste gardé par SYSTEM:USERS.
                        auth.requestMatchers(HttpMethod.POST,
                                ApiPath.V1 + "/admin/provisioning/bootstrap-admin").permitAll();

                        // Ingestion : authentifiée par clé partagée (X-Device-Key),
                        // vérifiée dans le contrôleur. Un microcontrôleur ne gère
                        // pas de cycle de vie de jeton — c'est une authentification
                        // distincte, pas une absence d'authentification.
                        auth.requestMatchers(ApiPath.V1 + "/ingest/**").permitAll();

                        // La règle /ws/** a été retirée au lot 5 : aucun point
                        // d'entrée WebSocket n'existe, et la dépendance a été
                        // supprimée du pom.xml.
                        // Les SOUS-CHEMINS de santé sont ouverts aussi : /health seul
                        // ne couvrait ni /health/liveness ni /health/readiness, qui
                        // répondaient donc 401 — précisément quand on cherche à savoir
                        // pourquoi le service est déclaré DOWN.
                        auth.requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll();

                        // Documentation d'API. Ces chemins sont HORS du préfixe
                        // /sni/api/v1, donc non couverts par les règles ci-dessus.
                        // Les ressources statiques de l'interface Swagger sont
                        // servies depuis /webjars : sans elles, la page se charge
                        // et reste vide, ce qui se diagnostique mal.
                        auth.requestMatchers(
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/webjars/**", "/swagger-resources/**"
                        ).permitAll();

                        // ⚠️ Le fourre-tout historique. Il figurait AVANT la règle
                        // ci-dessous, et Spring Security retenant la première
                        // correspondance, le gestionnaire d'autorisation n'était
                        // jamais consulté : la matrice de permissions de la V24
                        // était écrite et inerte.
                        //
                        // Il est désormais piloté par configuration
                        // (app.security.open-business-routes.enabled) plutôt que
                        // codé en dur. Motif : le fermer n'est tenable qu'une fois
                        // un compte administrateur amorcé et le jeton émis par le
                        // frontend — sinon plus personne n'entre, y compris pour
                        // amorcer ce compte. Voir AppProperties.Security.OpenBusinessRoutes.
                        if (security.getOpenBusinessRoutes().isEnabled()) {
                                auth.requestMatchers(ApiPath.V1 + "/**").permitAll();
                        }

                        auth.requestMatchers(ApiPath.V1 + "/**").access(adminApiAuthorizationManager);
                        auth.anyRequest().authenticated();
                })
                // Les deux gestionnaires construisaient bien un corps de réponse,
                // mais ne l'écrivaient jamais : le client recevait un 401 ou un
                // 403 au corps vide, là où toutes les autres erreurs de l'API
                // portent errorCode, message et traceId. Un refus muet se
                // diagnostique mal — rien ne distingue « jeton absent » de
                // « permission manquante ».
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "Authentification requise pour accéder à cette ressource.",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN",
                                        "Vous n'avez pas les droits requis pour cette ressource.",
                                        request.getRequestURI())))
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Écrit un refus au format des autres erreurs de l'API.
     *
     * <p>Rédigé à la main, sans {@code ObjectMapper} : ces deux gestionnaires
     * s'exécutent en amont de la couche MVC, donc hors de portée de
     * {@code GlobalExceptionHandler}. Le corps est fixe et ne contient que
     * l'URI demandée — il n'y a donc rien à sérialiser dynamiquement, et faire
     * dépendre un chemin d'erreur du sérialiseur ajouterait une façon d'échouer
     * là où l'on est déjà en train de signaler un échec.
     */
    private static void writeError(HttpServletResponse response, int status,
                                   String errorCode, String message, String path)
            throws java.io.IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format(
                "{\"success\":false,\"errorCode\":\"%s\",\"status\":%d,"
                        + "\"message\":\"%s\",\"path\":\"%s\"}",
                errorCode, status, message, path == null ? "" : path));
    }

    /**
     * Origines autorisées, lues depuis la configuration plutôt que codées en dur.
     *
     * <p>Le motif {@code "*"} reste le défaut du profil {@code dev} — le frontend
     * y tourne sur un port arbitraire, et l'énumérer n'apporterait rien. En
     * {@code prod}, la liste est explicite.
     *
     * <p>{@code allowCredentials} demeure {@code false}, et c'est ce qui rend
     * {@code "*"} tolérable en développement : l'authentification passe par un
     * en-tête {@code Authorization}, jamais par un cookie. Le navigateur n'a donc
     * rien d'implicite à joindre, et une page tierce ne peut pas rejouer la
     * session de l'utilisateur — ce qui serait exactement le risque si des
     * cookies portaient l'identité.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(security.getCors().getAllowedOriginPatterns());
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManger() throws AuthenticationException {
        return new ProviderManager(daoAuthenticationProvider());
    }

    private DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
