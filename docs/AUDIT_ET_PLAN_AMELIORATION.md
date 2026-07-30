# AUDIT GÉNÉRAL & PLAN D'AMÉLIORATION — Backend Bilanga

> **Date de l'audit** : 2026-07-29
> **Périmètre** : `src/main/java` (16 274 lignes, 313 fichiers suivis), `src/main/resources`,
> `pom.xml`, `compose.yaml`, `.env`, migrations Flyway V1→V10.
> **Méthode** : lecture du code source uniquement. Chaque constat est référencé
> `fichier:ligne`. Les documents narratifs (`PROJECT_CONTEXT.md`) n'ont pas servi de source.
> **Objectif du plan** : rendre l'existant robuste, cohérent, riche fonctionnellement,
> capable de traiter davantage d'éventualités et de restituer davantage de détail —
> **avant** d'ajouter de nouveaux domaines métier.

---

## 0. Résumé exécutif

Le cœur métier — la chaîne `capteur → IA → moteurs agronomiques → recommandations → alerte` —
est **la partie la plus soignée du dépôt** : `DiagnosisServiceImpl`, `RiskEngine`,
`AgronomicEngine`, `TrendAnalyzer`, `ConflictArbitrator` forment un moteur de décision
explicable, commenté, avec une vraie intention agronomique. C'est l'actif à protéger.

Autour de ce cœur, trois problèmes structurels dominent :

1. **La configuration ne correspond pas au code.** Sur 42 clés `@Value` lues par le code,
   la quasi-totalité est absente d'`application.yaml` ou écrite sous un chemin différent.
   Conséquence directe et vérifiable : **le point d'entrée d'ingestion IoT répond 503 en
   configuration par défaut** — le pipeline principal du projet est injoignable.
2. **Les conventions annoncées ne sont pas appliquées.** `ApiResponse<T>` est décrit comme
   l'enveloppe obligatoire de toutes les réponses : **aucun des 23 contrôleurs ne l'utilise**.
   Le contrat d'API est donc hétérogène (DTO nu, entité nue, `PaginatedResponse`, `String`).
3. **Le socle de sécurité est ouvert par construction**, et un scaffolding fintech « bokati »
   (dépendances, routes, `.env`) traverse encore tout le projet.

S'y ajoutent deux régressions franches à corriger immédiatement : **le flux de
réinitialisation de mot de passe est cassé** (aucun template Thymeleaf n'existe) et **le
fichier `.env` contenant les secrets est suivi par git**.

### Notation par axe

| Axe | Note | Commentaire |
|---|:---:|---|
| Moteur de diagnostic & connaissance | **A−** | Conception solide, explicable, bien commentée. Manque : cache, qualité de régression, seuils sourcés. |
| Modèle de données & migrations | **B+** | Flyway propre, indexés, seedés. Manque : contraintes `CHECK`, verrouillage optimiste, colonnes d'audit homogènes. |
| Contrat d'API | **C−** | Enveloppe non appliquée, pagination absente côté métier, entités nues exposées, pas d'OpenAPI. |
| Gestion des erreurs | **B−** | Handler global bien conçu, mais trous nets (403 → 500, IA indisponible → 500). |
| Sécurité | **D** | `permitAll("/**")`, auto-admin sans jeton, secrets versionnés, CORS `*`. |
| Configuration & environnements | **D** | Dérive massive code ↔ yaml, `.env` inerte, `init.sql` manquant, pas de profils. |
| Observabilité | **D** | Aucune métrique, aucun `traceId` propagé, logs `System.out`, log TRACE laissé actif. |
| Tests | **F** | 1 test (`contextLoads`), aucune infrastructure de test isolée. |
| Performance | **C** | N+1 sur le tableau de bord, relecture des tables de connaissance à chaque diagnostic. |
| Hygiène du dépôt | **D** | 0 commit, secrets suivis, ~12 dépendances mortes, code mort. |

---

## 1. Constats détaillés

Classement par gravité : **P0** = casse une fonctionnalité ou expose un secret ·
**P1** = incohérence structurelle · **P2** = robustesse / performance · **P3** = hygiène.

### P0 — Bloquants

| # | Constat | Preuve | Effet |
|---|---|---|---|
| P0-1 | **`bilanga.ingest.device-key` n'existe nulle part** dans `application.yaml`. Le défaut est la chaîne vide, et `requireValidKey` lève `503` quand la clé est vide. | `IngestController.java:33` et `:52-55` ; absent de `application.yaml` | `POST /ingest/readings` répond **503 systématiquement**. Toute la chaîne d'ingestion IoT est injoignable sans surcharge manuelle. |
| P0-2 | **Aucun template Thymeleaf n'existe.** `src/main/resources/templates/` est vide ; aucun `.html` dans tout `src/`. Le contrôleur renvoie pourtant `auth/password-reset-form`, `auth/password-reset-error`, `auth/password-reset-success`. | `PasswordResetFormController.java:53,57,62,78,83,98,102,111` ; `ls src/main/resources/templates` → vide | Le lien de réinitialisation de mot de passe produit une **erreur 500** (`TemplateInputException`). Le flux est mort. |
| P0-3 | **`.env` est suivi par git** et contient `APP_JWT_SECRET`, `TOKEN_HASH_SECRET`, mots de passe PostgreSQL et pgAdmin. `.gitignore` ne contient aucune règle `env`. | `git ls-files` → `.env` ; `.gitignore` (aucune entrée) | Secrets versionnés. **Le dépôt n'a encore aucun commit** : la fenêtre pour corriger proprement est ouverte, elle se referme au premier `git commit`. |
| P0-4 | **`permitAll` fourre-tout sur `/**`** placé *avant* la règle d'autorisation admin, qui devient inatteignable. | `SecurityConfig.java:85` neutralise `:90` | L'autorisation par URL est court-circuitée. Seuls 4 contrôleurs sont encore protégés, par `@PreAuthorize`. |
| P0-5 | **Auto-admin activé par défaut** : une requête sans jeton est authentifiée comme `admin@bokati.com` avec tous ses droits. | `JWTFilter.java:36-37`, `:63-67`, `:118-133` | N'importe quel appel anonyme obtient les droits administrateur. |

### P1 — Incohérences structurelles

| # | Constat | Preuve | Effet |
|---|---|---|---|
| P1-1 | **`ApiResponse<T>` n'est utilisé par aucun contrôleur** (0/23), alors que c'est la convention documentée. Les réponses sont tantôt un DTO nu, tantôt un `PaginatedResponse`, tantôt une `String`. | `templateResponse/ApiResponse.java` sans référence dans `*/controller/*` | Contrat d'API incohérent. Le frontend doit gérer 4 formes d'enveloppe, et les erreurs (`ApiError`) n'ont pas la même forme que les succès. |
| P1-2 | **Dérive de configuration généralisée.** Les chemins yaml sont imbriqués sous `bilanga.risk.ml.*` alors que le code lit `bilanga.ml.*`, `bilanga.confidence.*`, `bilanga.agronomic.*`, `bilanga.overview.*`. | `application.yaml:32-52` vs. clés `@Value` du code | Les valeurs du yaml sont **ignorées** : ce sont les défauts codés en dur qui s'appliquent. Régler un seuil dans le yaml n'a aucun effet. |
| P1-3 | **`.env` n'est pas lu par Spring Boot** : aucune dépendance dotenv dans `pom.xml`. Le fichier n'alimente que Docker Compose. | `pom.xml` (aucun `spring-dotenv`/`dotenv-java`) | Les 30+ réglages de `.env` (JWT, rate-limit, auto-admin) sont **inertes**. Ils donnent l'illusion d'une configuration active. |
| P1-4 | **Trois sources de vérité contradictoires pour la base** : `application.yaml` port 55820 / mdp `bilanga25` ; `.env` port 5434 / mdp `bokati25` ; `compose.yaml` 55820→5432. | `application.yaml:6-8`, `.env`, `compose.yaml:17` | Démarrage imprévisible selon le mode de lancement. |
| P1-5 | **Entités JPA exposées nues** dans les réponses admin (`PaginatedResponse<AuditLog>`). | `AdminAuditController.java:30` | Viole la règle « ne pas renvoyer d'entités nues » ; risque de sérialisation en cascade et de fuite de colonnes. |
| P1-6 | **`AccessDeniedException` de Spring Security n'a pas de handler.** L'exception maison s'appelle `AccesDeniedException` (faute de frappe) — c'est une *autre* classe. | `GlobalExceptionHandler.java:90` (classe maison) ; aucun handler pour `org.springframework.security.access.AccessDeniedException` | Un refus de `@PreAuthorize` tombe dans le catch-all `:171` et renvoie **500 au lieu de 403**. |
| P1-7 | **`IllegalStateException` (microservice IA injoignable) n'a pas de handler.** | `GlobalExceptionHandler.java:171` catch-all | `POST /diagnosis/image/predict` renvoie **500 opaque** quand l'IA est down, au lieu d'un 503 explicite. Le chemin ingestion est protégé, pas le chemin direct. |
| P1-8 | **`enums/` non intégré** : `DiagnosticSource`, `Severity`, `SoilType` sont des énumérations **vides**. Tout le domaine est modélisé en `String` libre. | `enums/DiagnosticSource.java`, `Severity.java`, `SoilType.java` : corps vides | Aucune garantie sur les valeurs (`"CAPTEUR"`, `"HAUTE"`, `"ARGILEUX"` sont des littéraux dispersés). Fautes de frappe silencieuses. |
| P1-9 | **`Users.id` est un `long` primitif**, seule entité dans ce cas ; toutes les autres utilisent `Long`. | `Users.java:32` | Incompatible avec un identifiant absent (`0` au lieu de `null`) ; piste sérieuse pour le 500 connu sur `/admin/users`. |

### P2 — Robustesse & performance

| # | Constat | Preuve | Effet |
|---|---|---|---|
| P2-1 | **N+1 sur le tableau de bord global.** `forAllPlots()` exécute, par parcelle, ≥ 6 requêtes (dernier relevé, alertes, culture active, risques, dernier diagnostic, recommandations, boîtiers). Aucune pagination. | `OverviewServiceImpl.java:135-156` | Coût linéaire non borné : 50 parcelles ≈ 300+ requêtes par appel de dashboard. |
| P2-2 | **Les tables de connaissance sont relues à chaque diagnostic** (règles, maladies, conditions de risque, exigences, arbitrages). Aucun cache, alors que `spring-boot-starter-cache` est au classpath et qu'aucun `@EnableCaching` n'existe. | `pom.xml:49-52` ; aucun `@Cacheable`/`@EnableCaching` dans `src/main/java` | Données quasi statiques relues des dizaines de fois par requête d'ingestion. |
| P2-3 | **Aucun verrouillage optimiste** : pas un seul `@Version` dans le projet. | `grep @Version src/main/java` → 0 | Deux mises à jour concurrentes (statut d'alerte, batterie de boîtier, exigence agronomique) s'écrasent silencieusement. |
| P2-4 | **`DiagnosisThrottle` est du code mort** : la classe existe, elle n'est référencée nulle part. La clé `bilanga.diagnosis.min-interval-minutes` est donc sans effet. | `DiagnosisThrottle.java:30` ; aucune autre occurrence | Un boîtier émettant toutes les 10 s déclenche un diagnostic complet (appel IA + 5 moteurs + écritures) à chaque relevé. Pas de garde-fou. |
| P2-5 | **La régression de tendance ne mesure pas sa qualité d'ajustement.** Aucun R², aucun seuil de bruit ; `slope == 0` est un test d'égalité sur `double`. | `TrendAnalyzer.java:110`, `:146-173` | Une série bruitée produit une pente arbitraire et une projection annoncée comme fiable à l'exploitant. |
| P2-6 | **Aucune alerte ne se referme d'elle-même.** `raiseIfNeeded` déduplique par signature, mais rien ne résout une alerte quand la situation redevient normale. | `AlertServiceImpl.java:34-69` (aucun chemin de résolution automatique) | Les alertes s'accumulent en statut `NEW` ; `overallStatus` reste bloqué sur `ALERTE`/`CRITIQUE` indéfiniment. |
| P2-7 | **Aucune notification n'est émise.** Une alerte n'existe qu'en base et via `/overview`. Les envois e-mail/SMS sont commentés. | `AlertServiceImpl` (aucun canal) ; `PasswordResetNotifier` | L'exploitant doit consulter le dashboard pour découvrir une situation critique. La valeur d'une alerte temps réel est perdue. |
| P2-8 | **`IngestController` renvoie des `ResponseStatusException`**, dont le corps ne suit pas le format `ApiError`. | `IngestController.java:53`, `:57` | Le boîtier reçoit une forme d'erreur différente du reste de l'API. |
| P2-9 | **Aucune pagination sur les endpoints métier** (`/plots`, `/crops`, `/devices`, `/readings`, `/observations`, `/overview/plots`) : `List<T>` complet. | `PlotController.java:39-44` et équivalents | `GET /readings?plotId=` sur une série temporelle ramène l'intégralité de l'historique. |
| P2-10 | **Aucune contrainte `CHECK` en base** sur les domaines à valeurs fermées (`status`, `priority`, `source`, `level`, `soil_type`). | Migrations V1→V10 | La base accepte `"HAUT"`, `"haute"`, `"URGENT"` indifféremment. Corollaire de P1-8. |
| P2-11 | **Aucun `traceId` corrélé aux logs.** `ApiError` porte un `traceId`, mais rien ne l'injecte dans le MDC ni ne le propage. | `ApiError` / `ErrorResponse` ; aucun filtre MDC | Un identifiant remonté par un utilisateur ne permet pas de retrouver la trace serveur. |
| P2-12 | **Aucun timeout ni réessai déclarés sur les appels au microservice IA.** | `VisionClientImpl`, `TabularClientImpl` (JDK `HttpClient` sans `.timeout()`) | Une IA lente bloque le thread d'ingestion jusqu'au timeout par défaut du système. |

### P3 — Hygiène

| # | Constat | Preuve |
|---|---|---|
| P3-1 | **~12 dépendances mortes** : AMQP, Batch (×2), Quartz, JobRunr, HATEOAS, WebSocket, Freemarker, Session Redis, Session JDBC, Data JDBC, Cache — aucun code Java correspondant. Plus leurs 15 pendants `*-test`. | `pom.xml:37-143` |
| P3-2 | **Aucune documentation d'API générée** : ni springdoc/OpenAPI, ni collection de requêtes. | `pom.xml` |
| P3-3 | **`System.out.println` dans le générateur d'ID**, exécuté à chaque identifiant produit. | `GeneratorOfId.java:70` (+ 9 autres) |
| P3-4 | **Log TRACE laissé actif** sur `RequestMappingHandlerMapping` (diagnostic ponctuel oublié). | `application.yaml:54-56` |
| P3-5 | **`compose.yaml` monte `./init.sql`, qui n'existe pas.** | `compose.yaml:15` ; fichier absent |
| P3-6 | **Routes fintech résiduelles** déclarées publiques dans le filtre JWT : `pawapay/callback`, `pawaypay/return`, `/documents/**/signed-preview`, `/shares/`, `/client/catalog/plans`. Aucun contrôleur correspondant. | `JWTFilter.java:169-179` |
| P3-7 | **Import inutilisé** `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) dans la config de sécurité. | `SecurityConfig.java:22` |
| P3-8 | **Handlers 401/403 construisent un corps JSON qui n'est jamais écrit** dans la réponse. | `SecurityConfig.java:93-111` |
| P3-9 | **DTO en double** : `diagnosis/dto/response/SoilPrediction` duplique `diagnosis/client/dto/response/SoilPrediction`. Le premier est mort. | Arborescence `diagnosis/` |
| P3-10 | **Volume `esdata` déclaré sans service** (reliquat Elasticsearch). | `compose.yaml:63` |
| P3-11 | **Aucun commit dans le dépôt.** 313 fichiers en attente sur `master`, branche principale annoncée `main`. | `git log` → « does not have any commits yet » |

---

## 2. Le plan

Sept lots, ordonnés par dépendance. **Les lots 0 à 3 sont du redressement** (rendre vrai ce
qui est annoncé) ; **les lots 4 à 6 sont de l'enrichissement** (plus d'éventualités, plus de
détail). Chaque lot est livrable indépendamment et se termine par un état vérifiable.

> **Règle transverse** : après chaque lot, `mvn clean install` avec un PostgreSQL démarré,
> et vérification que `ddl-auto: validate` passe au démarrage.

---

### LOT 0 — Reprendre le contrôle du dépôt *(≈ 1 h, aucun risque)*

**Pourquoi d'abord** : il n'y a aucun commit. Chaque lot suivant doit être réversible, et les
secrets ne doivent pas entrer dans l'historique.

| # | Tâche | Fichiers |
|---|---|---|
| 0.1 | Ajouter `.env`, `*.env`, `!.env.example` à `.gitignore` | `.gitignore` |
| 0.2 | `git rm --cached .env` ; créer `.env.example` avec les clés et des valeurs factices | `.env.example` |
| 0.3 | **Régénérer** `APP_JWT_SECRET` et `TOKEN_HASH_SECRET` (les valeurs actuelles sont à considérer comme compromises) | `.env` local |
| 0.4 | Premier commit sur `main`, puis branche de travail | — |
| 0.5 | Créer `init.sql` (peut être vide avec un commentaire) ou retirer le montage | `init.sql` ou `compose.yaml:15` |

**Critère d'acceptation** : `git ls-files | grep env` ne renvoie que `.env.example`.

---

### LOT 1 — Rendre la configuration vraie *(≈ 3 h · corrige P0-1, P1-2, P1-3, P1-4)*

**Pourquoi** : tant que le yaml ment, tout réglage ultérieur est illusoire — et l'ingestion
reste morte.

| # | Tâche | Détail |
|---|---|---|
| 1.1 | **Réécrire `application.yaml`** en calquant exactement les 42 clés `@Value` relevées. Aplatir `bilanga.risk.ml.*` → `bilanga.ml.*`, `bilanga.confidence.*`, `bilanga.agronomic.*`, `bilanga.overview.*`. Ajouter **`bilanga.ingest.device-key`**. |
| 1.2 | **Substituer les variables d'environnement partout** : `${BILANGA_INGEST_DEVICE_KEY}`, `${APP_JWT_SECRET}`, `${SPRING_DATASOURCE_*}`… Aucune valeur sensible littérale dans le yaml. |
| 1.3 | **Introduire les profils** `dev` / `prod` (`application-dev.yaml`, `application-prod.yaml`). En `prod`, aucun défaut permissif ne doit exister. |
| 1.4 | **Migrer `@Value` épars vers `@ConfigurationProperties`** typés et validés : `BilangaMlProperties`, `BilangaDiagnosisProperties`, `BilangaTrendProperties`, `BilangaIngestProperties`. Annoter `@Validated` + `@NotBlank`/`@DecimalMin`. |
| 1.5 | **Aligner les trois sources base de données** sur un port et un mot de passe uniques (`55820`, `bilanga25`). Corriger `.env`. |
| 1.6 | **Faire échouer le démarrage en `prod`** si `bilanga.ingest.device-key` ou `app.security.jwt.secret` sont vides ou égaux au défaut codé en dur. |
| 1.7 | Retirer le log TRACE de `RequestMappingHandlerMapping` ; définir des niveaux de log par profil. |

**Critères d'acceptation**
- `POST /sni/api/v1/ingest/readings` avec le bon `X-Device-Key` répond **201**, sans clé **401** — plus jamais 503.
- Modifier `bilanga.confidence.high` dans le yaml change *effectivement* le niveau de confiance renvoyé.
- Le démarrage en profil `prod` échoue avec un message explicite tant que les secrets ne sont pas fournis.

---

### LOT 2 — Durcir la sécurité *(≈ 4 h · corrige P0-4, P0-5, P1-6)*

> ⚠️ Ce lot touche `SecurityConfig`, `JWTFilter` et la logique d'autorisation.
> **Validation explicite requise avant exécution** (règle §6 de `CLAUDE.md`).

| # | Tâche | Détail |
|---|---|---|
| 2.1 | **Supprimer `ApiPath.V1 + "/**"` de la liste `permitAll`** (`SecurityConfig.java:85`) et énumérer précisément les routes publiques : `/auth/login`, `/auth/refresh`, `/auth/ott/**`, `/auth/password-reset/**`, `/auth/unlock-account*`, `/auth/email/verify/resend`, `/ingest/**` (gardé par la clé boîtier). |
| 2.2 | **Rendre l'auto-admin `false` par défaut** et le restreindre au profil `dev`. Émettre un `WARN` au démarrage quand il est actif. |
| 2.3 | **Garder `/admin/provisioning/bootstrap-admin`** : autorisé uniquement si aucun administrateur n'existe encore en base, sinon `409`. |
| 2.4 | **Ajouter `@PreAuthorize`** sur les contrôleurs admin qui n'en ont pas : `AdminAuditController`, `AdminSettingsAuditController`, `AdminIdempotencyController`, `UserProvisioningController`. |
| 2.5 | **Définir la posture d'autorisation métier** : à ce jour `/plots`, `/crops`, `/devices`, `/readings`, `/diagnosis`, `/overview` sont ouverts et acceptent n'importe quel `userId`. Décider et implémenter le **cloisonnement par propriétaire** (voir Lot 4.1). |
| 2.6 | **CORS** : remplacer `*` par une liste d'origines par profil (front React de Rolle en dev, domaine réel en prod). |
| 2.7 | **Écrire réellement le corps JSON** des handlers 401/403 (`SecurityConfig.java:93-111`), au format `ApiError`. |
| 2.8 | **Ajouter un handler `AccessDeniedException`** (Spring Security) dans `GlobalExceptionHandler` → **403**. Renommer `AccesDeniedException` → `AccessDeniedBusinessException` pour lever l'ambiguïté. |
| 2.9 | **Nettoyer `JWTFilter`** : supprimer les routes pawapay / documents / shares / catalog (`:169-179`) et le champ `publicDocumentPreviewEnabled`. |

**Critères d'acceptation**
- Requête sans jeton sur `/admin/users` → **401** (et non une réponse 200 en tant qu'admin).
- Utilisateur authentifié sans la permission `SYSTEM:USERS` → **403** au format `ApiError` (et non 500).
- `AdminApiAuthorizationManager` est effectivement invoqué (vérifiable par log de debug).

---

### LOT 3 — Unifier le contrat d'API *(≈ 6 h · corrige P1-1, P1-5, P1-7, P2-8, P2-9)*

**Pourquoi maintenant** : c'est un changement de contrat qui impacte le frontend de Rolle.
Il doit être fait **une fois**, complètement, et annoncé.

| # | Tâche | Détail |
|---|---|---|
| 3.1 | **Envelopper les 23 contrôleurs dans `ApiResponse<T>`.** Réponses paginées : `ApiResponse<PaginatedResponse<T>>`. Le changement est mécanique mais doit être exhaustif — une exception rouvre l'incohérence. |
| 3.2 | **Aligner `ApiError` sur `ApiResponse`** : mêmes champs de tête (`success`, `message`, `errorCode`, `timestamp`), pour que le frontend n'ait qu'un seul discriminant. |
| 3.3 | **Introduire des DTO de réponse pour l'admin** : `AuditLogResponse`, `SettingsAuditLogResponse`, `IdempotencyRecordResponse`. Ne plus exposer d'entité. |
| 3.4 | **Paginer les endpoints métier** : `/plots`, `/crops`, `/devices`, `/sensors`, `/readings`, `/observations`, `/diagnosis`, `/alerts`, `/overview/plots`. Utiliser `@PageableDefault` et `PaginationConstant`. |
| 3.5 | **Enrichir le filtrage** : `/readings?plotId=&from=&to=&anomalyOnly=`, `/diagnosis?plotId=&source=&from=&to=&minConfidence=`, `/alerts?plotId=&level=&status=&from=&to=`. |
| 3.6 | **Remplacer les `ResponseStatusException` d'`IngestController`** par des exceptions maison passant par le handler global. |
| 3.7 | **Ajouter springdoc-openapi** + annoter les contrôleurs. Un contrat lisible est le livrable attendu par le frontend et par le mémoire. |
| 3.8 | **Rédiger `docs/API_CONTRACT.md`** : enveloppe, codes d'erreur, pagination, et la note explicite « tous les `Long` sont sérialisés en `String` », y compris `PageInfo.totalElements`. |

**Critères d'acceptation**
- `GET /sni/api/v1/plots` renvoie `{"success":true,"data":{"content":[...],"pageInfo":{...}}}`.
- Succès et erreur partagent la même structure de tête.
- Swagger UI accessible et complet en profil `dev`.

**⚠️ Impact frontend** : à signaler à Rolle avant démarrage du lot.

---

### LOT 4 — Robustesse du domaine *(≈ 8 h · corrige P1-8, P1-9, P2-3, P2-10, et P0-2)*

| # | Tâche | Détail |
|---|---|---|
| 4.1 | **Cloisonnement par propriétaire.** Aujourd'hui `GET /plots?userId=X` accepte n'importe quel `userId`. Déduire le propriétaire du `UserPrincipal` ; un `SUPER_ADMIN` peut passer outre. Répercuter sur `/crops`, `/devices`, `/readings`, `/diagnosis`, `/alerts`, `/overview`. |
| 4.2 | **Peupler les énumérations vides** : `SoilType` (ARGILEUX, LIMONEUX, SABLEUX), `Severity` (BASSE, MOYENNE, HAUTE), `DiagnosticSource` (IMAGE, CAPTEUR). Ajouter `GrowthStage`, `AlertLevel`, `AlertStatus`, `RecommendationType`, `RecommendationPriority`, `PlotStatus`, `CropStatus`. |
| 4.3 | **Migrer les entités vers ces énumérations** (`@Enumerated(EnumType.STRING)`), en remplaçant les littéraux dispersés. Chantier mécanique mais large — le faire domaine par domaine. |
| 4.4 | **Migration `V11` — contraintes `CHECK`** sur `diagnostics.source`, `recommendations.priority|status|recommendation_type`, `alerts.level|status`, `plots.soil_type|status`, `crops.status|growth_stage`. Verrouille l'invariant en base, pas seulement en Java. |
| 4.5 | **Migration `V12` — `version BIGINT NOT NULL DEFAULT 0`** + `@Version` sur `Alert`, `IotDevice`, `Plot`, `Crop`, `CropRequirement`, `Users`. |
| 4.6 | **`Users.id` : `long` → `Long`** et vérifier la disparition du 500 sur `/admin/users`. À faire dans un commit isolé pour pouvoir l'annuler. |
| 4.7 | **Créer les 3 templates Thymeleaf manquants** : `templates/auth/password-reset-form.html`, `password-reset-error.html`, `password-reset-success.html`. Formulaire sobre, CSRF double-submit déjà géré par le contrôleur. |
| 4.8 | **Écrire un `ErrorCode` par éventualité** aujourd'hui muette : `ML_SERVICE_UNAVAILABLE`, `NO_ACTIVE_CROP`, `NO_SENSOR_READING`, `DEVICE_NOT_REGISTERED`, `INVALID_DEVICE_KEY`, `IMPLAUSIBLE_MEASURE`, `UNKNOWN_CROP`. |
| 4.9 | **Handler `IllegalStateException` → 503** avec `ML_SERVICE_UNAVAILABLE`. Restreindre le handler `IllegalArgumentException` (aujourd'hui il transforme aussi les vrais bugs en 400) : introduire une `BusinessRuleException` et l'utiliser dans les services `knowledge`. |
| 4.10 | **Timeouts + réessai** sur `VisionClientImpl` / `TabularClientImpl` : `connectTimeout` 2 s, `requestTimeout` 10 s (30 s pour la vision), 1 réessai sur erreur réseau, jamais sur 4xx. |

**Critères d'acceptation**
- Un utilisateur A ne peut pas lire les parcelles de B.
- `INSERT` d'une priorité `"URGENT"` est rejeté par PostgreSQL.
- Le lien de réinitialisation affiche un formulaire fonctionnel.
- IA arrêtée → `POST /diagnosis/image/predict` répond **503 `ML_SERVICE_UNAVAILABLE`**, message en français.

---

### LOT 5 — Enrichissement fonctionnel *(≈ 12 h · corrige P2-1, P2-2, P2-4, P2-5, P2-6, P2-7)*

C'est le lot qui répond directement à « plus riche, plus d'éventualités, plus de détail ».

#### 5.1 Cycle de vie complet des alertes
- **Auto-résolution** : à chaque nouveau diagnostic, si la signature d'une alerte ouverte
  n'est plus reproduite et qu'aucune recommandation `HAUTE` ne subsiste, passer l'alerte en
  `RESOLVED` avec `resolution_reason = 'AUTO_SITUATION_NORMALISEE'`.
- **Escalade** : une alerte `NEW` non acquittée au-delà d'un délai configurable monte d'un
  niveau (`HIGH` → `CRITICAL`) et incrémente un `escalation_count`.
- **Historique** : table `alert_events` (transitions, acteur, horodatage) plutôt que trois
  colonnes de dates isolées.
- **Migration `V13`** : `resolution_reason`, `escalation_count`, `last_seen_at`, `alert_events`.

#### 5.2 Notifications
- Interface `NotificationChannel` avec implémentations `LogNotificationChannel` (dev),
  `EmailNotificationChannel` (activation du code aujourd'hui commenté), et un
  `WebhookNotificationChannel` prêt pour SMS/WhatsApp.
- Table `notification_outbox` + politique de réessai. **Sans démon ni file** : le déclenchement
  reste synchrone à la levée d'alerte, avec rattrapage au démarrage — cohérent avec l'absence
  assumée d'infrastructure asynchrone.
- Préférences par utilisateur : seuil de niveau à partir duquel notifier.

#### 5.3 Anti-emballement du diagnostic
- **Câbler `DiagnosisThrottle`** (aujourd'hui mort) dans `IngestServiceImpl`.
- Politique : diagnostic complet si l'intervalle minimal est écoulé **ou** si une mesure a
  varié au-delà des seuils `bilanga.diagnosis.threshold.*` (déjà présents et inutilisés) **ou**
  si une anomalie matérielle est détectée. Sinon, relevé enregistré, diagnostic ignoré.
- `IngestResult` gagne un champ `skipReason` — le boîtier sait *pourquoi* il n'a rien reçu.

#### 5.4 Qualité et détail des tendances
- **R² sur la régression** : en deçà d'un seuil (0.5 par défaut), la tendance n'est pas
  publiée. Exposer `rSquared` et `confidence` dans `TrendFinding` — l'exploitant voit la
  solidité de la projection.
- Étendre l'analyse à `azote`, `phosphore`, `potassium`, `luminosite` (aujourd'hui limitée à
  4 mesures sur 8).
- Remplacer `slope == 0` par un test de pente négligeable relatif à l'amplitude mesurée.

#### 5.5 Performance
- **`@EnableCaching`** + `@Cacheable` sur les lectures de `knowledge` (règles, maladies,
  conditions, exigences, arbitrages), avec éviction sur écriture dans les services admin
  correspondants. Le starter cache est déjà au classpath.
- **Supprimer le N+1 du dashboard** : requêtes agrégées (`@Query` avec `GROUP BY`) pour
  compter alertes ouvertes et récupérer le dernier relevé/diagnostic par lot de parcelles.
  Paginer `/overview/plots`.

#### 5.6 Restituer davantage de détail
- **`GET /plots/{id}/history?from=&to=&granularity=`** : série temporelle agrégée
  (min/moy/max par heure ou par jour) — indispensable aux graphiques du frontend.
- **`GET /diagnosis/{id}/explain`** : la trace complète d'un diagnostic — quelle règle, quel
  seuil, quelle valeur observée, quel moteur a produit chaque recommandation. Les colonnes
  de traçabilité existent déjà (`source_rule_id`, `measure_field`, `observed_value`,
  `threshold_value`, migration V9) et **ne sont exposées nulle part**. C'est le meilleur
  rapport valeur/effort du plan, et un argument fort pour le mémoire (IA explicable).
- **`GET /overview/farm`** : agrégat multi-parcelles (répartition des statuts, alertes par
  niveau, boîtiers silencieux, batteries faibles).
- **Suivi des recommandations** : statut `APPLIQUEE` / `IGNOREE` + `PATCH /recommendations/{id}`.
  Boucle de retour aujourd'hui absente — et matière à évaluation de la pertinence du moteur.
- **Santé du parc IoT** : `GET /devices/health` (silencieux, batterie faible, taux d'anomalie
  par boîtier sur 7 jours).

---

### LOT 6 — Tests, observabilité, hygiène *(≈ 10 h · corrige P2-11, P3-\*)*

#### 6.1 Infrastructure de test
- **Testcontainers PostgreSQL** — la seule option cohérente : Flyway possède le schéma, H2 ne
  peut pas rejouer les migrations (`pg_trgm`, `jsonb`).
- `AbstractIntegrationTest` avec conteneur partagé + `@ServiceConnection`.

#### 6.2 Couverture cible par ordre de valeur
1. **Moteurs `knowledge`** (tests unitaires purs, sans base) : `RiskEngine` (calcul pondéré,
   opérateurs, mesures manquantes), `AgronomicEngine` (sévérité, VPD, NPK), `TrendAnalyzer`
   (pente, projection, R²), `ConflictArbitrator`. **Le cœur du mémoire doit être prouvé.**
2. **`DiagnosisServiceImpl`** avec `VisionClient`/`TabularClient` mockés : déduplication,
   arbitrage, tri, corroboration, seuils de confiance.
3. **`IngestServiceImpl`** : relevé conservé quand l'IA tombe, plausibilité, throttle.
4. **`AlertServiceImpl`** : déduplication par signature, auto-résolution, escalade.
5. **Tests MockMvc** : enveloppe `ApiResponse`, codes d'erreur, cloisonnement par propriétaire,
   clé boîtier (200/401/503).
6. **Test de migration** : Flyway s'applique de zéro et Hibernate `validate` passe.

**Cible réaliste** : ~60 % de couverture des lignes, ~90 % sur `knowledge` et `diagnosis`.

#### 6.3 Observabilité
- Filtre `TraceIdFilter` : génère un identifiant par requête, le pose dans le MDC, l'expose en
  en-tête `X-Trace-Id` et le renseigne dans `ApiError.traceId`. **Rend enfin exploitable un
  champ déjà présent.**
- Motif de log structuré incluant `traceId` et `plotId`.
- Micrometer + `/actuator/metrics` : compteurs `bilanga.ingest.readings`,
  `bilanga.diagnosis.duration`, `bilanga.ml.failures`, `bilanga.alerts.raised`.
- Indicateur de santé personnalisé `mlServiceHealthIndicator` — savoir si l'IA répond sans
  lire les logs.
- **Remplacer les `System.out` de `GeneratorOfId`** par un logger en `DEBUG`.

#### 6.4 Nettoyage
- **Retirer les dépendances mortes** : AMQP, Batch ×2, Quartz, JobRunr, HATEOAS, WebSocket,
  Freemarker, Session Redis, Session JDBC, Data JDBC + les 15 `*-test` correspondants.
  Conserver `cache` (utilisé au Lot 5.5) et `thymeleaf` (utilisé au Lot 4.7).
  Bénéfice mesurable : temps de démarrage, taille du jar, surface d'attaque.
- **Retirer de `compose.yaml`** : `redis`, `minio`, volume `esdata` — aucun code ne les
  utilise. Retirer les blocs `.env` correspondants (MinIO, workers, mobile-money).
- **Supprimer le code mort** : `diagnosis/dto/response/SoilPrediction`, import `ObjectMapper`
  Jackson 2 dans `SecurityConfig`, champ `publicDocumentPreviewEnabled`.
- **Décider du sort du reliquat fintech** dans `AdminApiAuthorizationManager` (modules
  `BILLING`, `PAYMENT`, `KYC`, `DOCUMENTS`) : les remplacer par les modules réels
  (`FARM`, `IOT`, `DIAGNOSIS`, `KNOWLEDGE`, `SYSTEM`).
- **Reprendre `ValidationUtils`** : les regex NIU/téléphone Cameroun n'ont pas de sens ici.
- **Mettre à jour `docs/ARCHITECTURE.md`** : deux affirmations sont à corriger — seul
  `SoilPrediction` est dupliqué (pas `VisionPrediction`), et `AlertController` existe bien.

---

## 3. Séquencement et charge

| Lot | Intitulé | Charge | Dépend de | Risque | Impact frontend |
|---|---|---:|---|---|---|
| 0 | Contrôle du dépôt | 1 h | — | Nul | Non |
| 1 | Configuration vraie | 3 h | 0 | Faible | Non |
| 2 | Durcissement sécurité | 4 h | 1 | **Élevé** — validation requise | **Oui** (jeton désormais obligatoire) |
| 3 | Contrat d'API unifié | 6 h | 1 | Moyen | **Oui** (enveloppe + pagination) |
| 4 | Robustesse du domaine | 8 h | 3 | Moyen (migrations) | Partiel (énumérations, codes d'erreur) |
| 5 | Enrichissement fonctionnel | 12 h | 4 | Faible | **Oui** (endpoints ajoutés) |
| 6 | Tests, observabilité, hygiène | 10 h | 5 | Faible | Non |

**Total ≈ 44 h.** Les lots 0 et 1 sont à faire **immédiatement** : le premier ferme une fuite
de secrets pendant que c'est encore gratuit, le second rallume le pipeline d'ingestion.

### Deux points de décision à trancher avant exécution

1. **Lot 2 — quelle posture d'autorisation métier ?** Aujourd'hui `/plots`, `/crops`,
   `/devices`, `/readings`, `/diagnosis`, `/overview` sont entièrement ouverts. Trois options :
   (a) tout authentifié + cloisonnement par propriétaire *(recommandé)* ;
   (b) authentifié sans cloisonnement, coopérative à confiance mutuelle ;
   (c) statu quo en `dev`, durcissement en `prod` uniquement.
2. **Lot 3 — quand casser le contrat d'API ?** Le passage à `ApiResponse` + pagination
   impacte tout le frontend de Rolle. À caler avec lui, et à faire en une seule fois.

### Ordre alternatif si une démo approche

`Lot 0` → `Lot 1` → `Lot 4.7` (templates) → `Lot 5.6` (endpoint `/explain` + historique) →
`Lot 2`. On obtient une démonstration fonctionnelle et démonstrative (l'explicabilité du
diagnostic est l'argument le plus fort pour un jury), en repoussant le changement de contrat
d'API qui immobiliserait le frontend.

---

## 4. Ce qu'il ne faut pas casser

Points forts identifiés, à préserver tels quels lors des refactorings :

- **La séparation plausibilité matérielle / défavorabilité agronomique**
  (`IngestServiceImpl.hasImplausibleValue` vs. moteurs `knowledge`). Distinction juste et rare.
- **« Le relevé n'est jamais perdu »** : `IngestServiceImpl.diagnose` avale l'échec du
  diagnostic. Le comportement doit survivre à toute réécriture.
- **La corroboration image ↔ mesures** (`DiagnosisServiceImpl:207-234`) : croiser deux voies
  indépendantes est ce qui distingue ce backend d'un simple relais vers un modèle.
- **La déduplication d'alerte par signature situationnelle** — une alerte porte sur une
  situation, pas sur un relevé.
- **L'arbitrage qui ajoute au lieu de retrancher** (`ConflictArbitrator`) : la synthèse
  n'efface jamais les conseils qu'elle concilie ; la traçabilité est conservée.
- **`@IdGeneration` + sérialisation `Long` → `String`** : la protection contre l'arrondi
  JavaScript est correcte et vérifiée. Ne pas y toucher.
- **Les commentaires en français expliquant le *pourquoi*** des seuils et des choix. C'est de
  la documentation vivante, et une pièce du mémoire.

---

## 5. Suivi

| Lot | Statut | Date | Note |
|---|---|---|---|
| 0 — Contrôle du dépôt | ☐ À faire | | **Toujours 0 commit : la fenêtre pour sortir `.env` de l'index sans réécrire l'historique est encore ouverte.** |
| 1 — Configuration vraie | ☑ **Fait, intégralement** | 2026-07-29 | **Zéro `@Value` dans tout le projet** : 33 clés `bilanga.*` dans `BilangaProperties`, 25 clés `app.*` dans `AppProperties`, toutes deux typées et validées. Profils `dev`/`prod`. `ConfigurationGuard` refuse le démarrage en prod (secret absent, trop court, ou valeur de développement) et lit **les mêmes objets que l'application**, pas une relecture parallèle. `TokenHashProperties` et `SecurityPropertiesConfig` fondus dans `AppProperties`. Base alignée sur 55820/`bilanga25` (yaml + compose + `.env`). **Vérifié** : dev → « ingestion prête » ; prod sans secrets → refus avec 4 motifs nommés ; prod avec secrets → démarre. |
| 2 — Durcissement sécurité | ☐ À faire | | `permitAll("/**")` et auto-admin volontairement laissés. Faits en marge : handler 403, `@PreAuthorize` sur les journaux, chemins Swagger ouverts. |
| 3 — Contrat d'API unifié | ☑ **Fait** | 2026-07-29 | 23 contrôleurs enveloppés, paginés, filtrés ; DTO pour les entités admin et `knowledge` ; springdoc 3.0.0 + `OpenApiConfig` ; Swagger accessible. Contrat rédigé dans `docs/API_FRONTEND.md`. |
| 4 — Robustesse du domaine | ☑ **Fait** | 2026-07-29 | V11 (26 contraintes `CHECK`), V12 (`@Version` sur 13 entités), `Users.id` en objet, 17 énumérations, codes d'erreur métier, timeouts IA. **4.1** : `AccessGuard`, point de contrôle unique branché sur `PlotService.require` — inactif par défaut, les tables de sécurité étant vides. **4.7** : les 3 templates Thymeleaf, le reset password ne tombe plus en 500. |
| 5 — Enrichissement fonctionnel | ☑ **Fait, sauf canaux** | 2026-07-29 | V13 (cycle de vie des alertes), V14 (retour sur conseil), V15 (outbox). Régulateur câblé, R² sur les tendances, `/diagnosis/{id}/explain`, `/recommendations/uptake`. **5.5** : cache des tables de connaissance + `@EvictsKnowledgeCaches`. **5.6** : `/plots/{id}/history` agrégé et `/overview/farm` — requêtes validées contre PostgreSQL. **Reste** : les canaux de notification réels (§5.2), seul `LOG` existe. |
| 6 — Tests, observabilité, hygiène | ☐ À faire | | `traceId` fait. Reste : Testcontainers, tests, métriques, nettoyage des dépendances mortes. |