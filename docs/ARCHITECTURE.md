# ARCHITECTURE.md — Contexte technique complet du backend Bilanga

> Fichier « tout le contexte » référencé par `CLAUDE.md`. Décrit **comment le backend est
> réellement orchestré** : modules, flux de requêtes, schéma, endpoints, config, et la dette
> connue. Écrit à partir d'une lecture du code (Spring Boot 4.1, Java 25), pas des anciens
> documents idéalisés. Source de vérité = code + migrations Flyway.

---

## 0. Avertissement : docs historiques vs réalité

`PROJECT_CONTEXT.md` (racine) et l'ancien `CLAUDE.md` décrivaient une cible. Écarts majeurs
constatés dans le code :

| Ancien doc affirmait | Réalité |
|---|---|
| Java 17 | **Java 25**, Spring Boot **4.1.0** |
| « 18 tables » | ~**30 tables** (sécurité V1 + métier V2→V10) |
| Base de test H2 en mémoire | **Pas de H2** ; un seul test qui démarre le contexte sur Postgres |
| Tests unitaires JUnit/Mockito + intégration MockMvc + `@MockBean AiClient` | **Aucun** de ces tests n'existe |
| `AiClient` mocké | Pas de classe `AiClient` ; le client réel est `VisionClient`/`TabularClient` (JDK HttpClient) |
| `schema_complet.sql` | N'existe pas ; le schéma est dans les migrations Flyway |
| Notifications SMS/Email/WhatsApp fonctionnelles | Envois **commentés** dans le code |

En plus : un large **scaffolding fintech « bokati »** (dépendances + `.env`) n'est câblé à
aucun code (voir §9).

---

## 1. Vue d'ensemble & orchestration

Le backend est un **orchestrateur central**. Deux blocs dans un seul module Maven
(`com.sni.bilanga`) :

```
Métier agricole (le pipeline)                     Infrastructure transverse
  farm          parcelles, cultures                 organization  coopérative → exploitation (FACULTATIF)
  iot           devices, capteurs, INGESTION        security      auth, rôles, permissions, tokens, sessions
  diagnosis     orchestration IA + reco + alerte    audit         journalisation AOP + user sessions
  knowledge     moteur agronomique (6 moteurs)      idempotency   rejeu sûr des POST admin (AOP)
  weather       prévisions Open-Meteo (cache)       notification  outbox, canaux LOG et SMS, préférences
  intervention  journal des actions + effet         generator     IDs Snowflake (@IdGeneration)
  harvest       récoltes, marge, rendement          config        RestClient IA, Jackson, propriétés typées
  overview      dashboards + chronologie            exception     GlobalExceptionHandler, ErrorCode
  enums         vocabulaire du domaine              templateResponse  ApiResponse, PaginatedResponse, PageInfo
                                                    utils         ApiPath, validation, format, export CSV
```

> `organization` est le seul module dont **rien ne dépend** : une parcelle sans exploitation
> se comporte exactement comme avant la V22. Il n'ajoute jamais une contrainte, seulement une
> possibilité.

**Le flux nominal (capteur → conseil)** :

```
IoT (ESP32/Wokwi)
   │  POST /sni/api/v1/ingest/readings   header X-Device-Key   (JSON métriques)
   ▼
IngestController ── clé device constante, comparaison temps-constant ──► IngestServiceImpl
   │  1. résout le device par technicalId (404 si inconnu) → en déduit le Plot
   │  2. construit SensorReading (quality="TERRAIN"), valide la PLAUSIBILITÉ matérielle
   │     (pH∉[0,14], hum∉[0,100], temp∉[-20,70], N/P/K/lum <0 → anomalyDetected=true)
   │  3. met à jour la batterie du device
   │  4. PERSISTE la lecture (toujours, même si la suite échoue)
   │  5. déclenche le diagnostic CAPTEUR (synchrone)
   ▼
DiagnosisServiceImpl.diagnoseFromSensorReading(plotId, null, readingId)
   │  ContextResolver : déduit culture (crop actif du plot), stade, reading
   │  TabularClient.predict(featureMap) ──► microservice IA  POST /predict/soil
   │  persiste Diagnostic(source="CAPTEUR")
   │  assemble les recommandations depuis 5 moteurs knowledge  (voir §5)
   │  déduplique → arbitre les conflits → trie par priorité → persiste Recommendation(s)
   │  AlertService.raiseIfNeeded(...)  (alerte seulement si diagnostic fiable + reco HAUTE)
   ▼
IngestResult {readingId, plotId, anomalyDetected, diagnosed, result, recommendationCount}
```

Si l'IA est indisponible, `IngestServiceImpl` **attrape l'`IllegalStateException`** du client
et renvoie `diagnosed=false` : la lecture n'est jamais perdue.

**La chaîne IMAGE** est parallèle : `POST /diagnosis/image/predict` (multipart) →
`VisionClient.predict(crop, image)` → `/predict/vision-b64` → même pipeline de recommandations,
plus une **corroboration** (croise la maladie prédite avec les conditions mesurées).

---

## 2. Domaine `farm` — parcelles & cultures

- **`Plot`** (`plots`) : appartient à un `Users`, `name`, `location`, `soilType` (String :
  ARGILEUX|LIMONEUX|SABLEUX), `area` (ha), `status`. Contrat clé : `PlotService.require(id)`.
- **`Crop`** (`crops`) : planting daté sur un plot, `cropName` (tomate|manioc), `variety`,
  `growthStage` (LEVEE|CROISSANCE|FLORAISON|FRUCTIFICATION|MATURATION|TUBERISATION…), `status`.
  Contrat clé : `CropService.findActiveCrop(plotId) → Optional<Crop>` (contexte du diagnostic).
- Couches : `PlotController`/`CropController` → services (+impl) → repos → models, DTO
  `dto/request|response`, mappers `service/support/PlotMapper|CropMapper`.
- Endpoints : `POST|PUT|GET|DELETE /plots` (`GET /plots?userId=`),
  `POST|PUT|GET|DELETE /crops` (`GET /crops?plotId=`).

## 3. Domaine `iot` — devices, capteurs, ingestion

- **`IotDevice`** (`iot_devices`) : `technicalId` unique (identité matérielle), rattaché à un
  plot. Un boîtier déplacé n'a rien à reprogrammer (la lecture suit le plot du device).
- **`SensorReading`** (`sensor_readings`) : **relevé multi-métrique complet à l'instant t** —
  `temperature, humiditeSol, humiditeAir, ph, azote, phosphore, potassium, luminosite,
  quality, anomalyDetected, recordedAt`. C'est l'entrée du modèle tabulaire et des moteurs.
- `Sensor`, `Observation` : CRUD standard. Mapper partagé `iot/service/support/IotMapper`.
- **Ingestion** (le point sensible) :
  - `IngestController` `/sni/api/v1/ingest` : `POST /readings` (header `X-Device-Key`),
    `GET /health` (liveness du boîtier). Auth par **clé device partagée** (pas de JWT —
    un microcontrôleur ne gère pas de cycle de vie de token). Clé attendue :
    `bilanga.ingest.device-key`. `requireValidKey` : 503 si non configurée côté serveur,
    401 si absente/mauvaise, comparaison **temps-constant** anti-timing.
  - `IngestReadingRequest` : seul `technicalId` est `@NotBlank`, toutes les métriques sont
    optionnelles.
- Autres endpoints : `/devices` (CRUD, `GET /devices/technical/{technicalId}`, `?plotId=`),
  `/sensors` (`?deviceId=`), `/readings` (`?plotId=`), `/observations` (`?plotId=`).

## 4. Domaine `diagnosis` — orchestration IA, recommandations, alertes

### 4.1 Client du microservice IA (`diagnosis/client/`)
- **Fait main sur le JDK `java.net.http.HttpClient`** (HTTP/1.1) + Jackson. Pas de Feign/
  RestTemplate/WebClient. Interfaces `client/interfaces`, impls `@Service`.
- `VisionClient.predict(crop, MultipartFile)` → POST JSON `{crop, imageBase64}` sur
  **`/predict/vision-b64`** → `VisionPrediction {crop, diseaseClass, confidence,
  allProbabilities}`.
- `TabularClient.predict(Map features)` → POST sur **`/predict/soil`** →
  `SoilPrediction {category, confidence}`.
- Feature map tabulaire : `temperature, humidite_sol, humidite_air, ph, azote, phosphore,
  potassium, luminosite, culture, type_sol`.
- URL : `@Value bilanga.ml.base-url` défaut `http://localhost:8000`. Non-200 →
  `IllegalStateException` (attrapée en amont pour ne jamais perdre une lecture).

### 4.2 `DiagnosisServiceImpl` (le hub)
Entrées : `diagnoseFromImage`, `diagnoseFromSensorReading` (appellent l'IA) ;
`processImageDiagnosis`, `processSensorDiagnosis` (acceptent une prédiction déjà calculée
par le client). Étapes : `ContextResolver.resolve` (déduit crop/stade/reading, nettoie
`"tomate,tomate"`→`tomate`) → prédiction → `normalizeDiseaseCode` (retire le préfixe
`Tomato___`) → `persistDiagnostic` → assemblage des 5 sources de reco →
`deduplicate`+`arbitrate`+`sortByPriority` → persist → `raiseIfNeeded`. Renvoie un
`DiagnosisResult` riche (confiance, fiabilité, alternatives, corroboration, indicateurs,
risques, tendances, recommandations ordonnées).

**Confiance** (`ConfidenceEvaluator`) : seuils `high=0.85`, `low=0.60` → ELEVEE/MOYENNE/
FAIBLE ; fiable si ≥ low. **Corroboration** (image) : croise la maladie avec `riskFor(...)` ;
≥0.60 les conditions confirment, ≤0.20 elles divergent (symptôme d'un passé), entre : rien.

### 4.3 Alertes (`AlertServiceImpl`)
`raiseIfNeeded` : **seuls les diagnostics fiables** lèvent une alerte, et il faut ≥1 reco
`HAUTE`. Déduplication par **signature `source:cropName:result`** (une alerte porte sur une
*situation*, pas un relevé). Niveau `CRITICAL` si ≥2 recos urgentes, sinon `HIGH`.
`acknowledge/resolve/findOpen/findByPlot` existent **mais aucun `AlertController`** — les
alertes ne sortent que par `overview`.

### 4.4 Entités & endpoints
- `Diagnostic` (`diagnostics`, source IMAGE|CAPTEUR), `Recommendation` (BASE|CORRELATION,
  colonnes de traçabilité `source_rule_id`/`measure_field`/`observed_value`/`threshold_value`),
  `Alert` (signature + lifecycle), `AiModel` (registre : 3 modèles seedés en V3).
- `DiagnosisController` `/sni/api/v1/diagnosis` : `POST /image`, `POST /sensor` (pré-calculés) ;
  `POST /image/predict` (multipart), `POST /sensor/predict` (params) ; `GET /{id}` ;
  `GET /diagnosis?plotId=&limit=20`.

## 5. Domaine `knowledge` — moteur agronomique déterministe

Couche « experte » explicable, complémentaire des modèles statistiques. `KnowledgeServiceImpl`
est la façade dont dépend `diagnosis`. Les règles supportent le wildcard `'*'` (crop-agnostique).
Cinq moteurs dans `knowledge/service/support` :

1. **`RiskEngine`** (cœur du score de risque) — part des **mesures seules** pour estimer, par
   maladie, la fraction de conditions d'apparition réunies (alerte précoce, indépendante du
   modèle vision). **Score = poids satisfait / poids total** sur les `DiseaseRiskCondition`
   (`operator` ∈ `> < >= <= == BETWEEN`, `weight`, `measureField`). Conditions à mesure
   manquante = ignorées. Niveau : ≥0.85 ELEVE, ≥0.60 MODERE, sinon FAIBLE.
2. **`AgronomicEngine`** — compare chaque mesure aux plages de `CropRequirement` (affinées par
   stade via `CropStageRequirement`/`CropRequirementResolver`). Sévérité =
   `(écart/amplitude)·(1−tolérance)` clampée 0–1. Calcule aussi VPD (déficit de pression de
   vapeur) et déséquilibre NPK (`DerivedIndicators`). Priorité selon sévérité.
3. **`TrendAnalyzer`** — anticipatif : régression **moindres carrés** (pente/heure) sur une
   fenêtre récente (hum sol/air, temp, pH) et projette le franchissement du seuil
   (horizon 12 h). Ignore les mesures déjà hors plage (c'est le rôle de l'agronomic).
4. **`CorrelationEngine`** — chaîne image : filtre `CorrelationRule` par la valeur mesurée
   (catégorie `MALADIE_FOLIAIRE`).
5. **`ConflictArbitrator`** — réconcilie les conseils contradictoires (ex. « baisser
   l'humidité » vs « irriguer ») : quand `categoryA` et `categoryB` coexistent, **ajoute**
   (ne retire jamais) une synthèse `ARBITRAGE` depuis `RecommendationArbitration`.

6. **`WeatherEngine`** *(V20)* — sixième moteur, le seul qui regarde **devant** à partir
   d'une source externe (`TrendAnalyzer` extrapole les mesures internes, sans rien savoir du
   ciel). Trois règles : différer l'irrigation si la pluie cumulée annoncée dépasse
   `rain-threshold-mm` ; refuser un traitement si une averse tombe sous
   `treatment-rain-window-hours` (produit lessivé) ; alerte préventive si l'humidité annoncée
   dépasse `high-humidity-threshold` sur ≥12 h. **Rend une liste vide** si la météo est
   désactivée, si la parcelle n'a pas de coordonnées ou si Open-Meteo ne répond pas — le
   système reste utilisable sans météo, comme sans microservice d'inférence.
7. **`IrrigationAdapter`** *(V16)* — dernière étape avant le tri : sur une parcelle
   `PLUVIAL`, un conseil `STRESS_HYDRIQUE` demandant d'irriguer est **reformulé** (paillage,
   ombrage, binage) et non supprimé. Le constat reste vrai ; seule la réponse change.
   La traçabilité (`measureField`/`observedValue`/`thresholdValue`) est conservée intacte.

Tables knowledge : `crop_requirement`, `crop_stage_requirement`, `knowledge_rules`,
`disease_knowledge`, `disease_risk_condition`, `correlation_rules`,
`recommendation_arbitration`. Seul **`CropRequirementController`**
(`/sni/api/v1/knowledge/crop-requirements` + `/stages`) est exposé ; les autres services
knowledge sont internes.

## 6. Domaine `overview` — dashboards

`OverviewServiceImpl` (lecture seule, ne possède pas d'entité) compose farm+iot+diagnosis+
knowledge. `forPlot(id)` → `PlotOverview` (crop actif, devices + batterie mini + statut
ACTIF/SILENCIEUX via `device-silence-minutes`, dernière lecture + âge, indicateurs, risques,
dernier diagnostic, alertes ouvertes, `overallStatus`, résumé FR). **Précédence
`overallStatus`** : aucune donnée → SANS_DONNEES ; alerte critique → CRITIQUE ; alerte
ouverte → ALERTE ; risque ELEVE → VIGILANCE ; dernier diagnostic ≠ NORMAL → VIGILANCE ;
sinon NORMAL. Endpoints : `GET /overview/plots`, `GET /overview/plots/{plotId}`.

---

## 7. Infrastructure transverse

### 7.1 Réponses & erreurs
- `ApiResponse<T>` : `{success, message, errorCode, errorDescription, timestamp, data}` +
  fabriques `success(...)` / `error(...)`.
- `PaginatedResponse<T>` (à partir d'un `Page<T>`) + `PageInfo` (métadonnées + `SortInfo`).
  Défauts pagination : `PaginationConstant` (page 0, size 10, sortBy id, max 100).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) → `ApiError {errorCode, status, message,
  traceId, errors?, (path/debugMessage/exceptionName si app.dev-mode=true)}`. Exceptions
  maison sous `exception/customs/` (toutes `extends BaseException`, portent un `errorCode`) ;
  vocabulaire dans `utils/error/ErrorCode`.

### 7.2 IDs — Snowflake + sérialisation string
- `GeneratorOfId` (Hibernate `IdentifierGenerator`) : 64 bits = timestamp + 10 bits machine +
  12 bits séquence, epoch custom. Machine id = hash de `user.name` mod 1024. **Émet des
  `System.out.println` à chaque génération** (à nettoyer). Branché par `@IdGeneration`
  (`@IdGeneratorType`).
- `JacksonConfig` sérialise **tous les `Long`/`long` en String** (`ToStringSerializer`) pour
  protéger les IDs Snowflake à 19 chiffres du safe-integer JS (au-delà, arrondi silencieux →
  404 fantômes). ✅ Corrigé le 2026-07-28 : le bean déclare désormais un module **Jackson 3**
  (`tools.jackson.databind.module.SimpleModule`) — Spring Boot 4 sérialise le web avec Jackson 3,
  et `JacksonAutoConfiguration` auto-détecte les beans `JacksonModule` et les applique au
  `JsonMapper` HTTP. Vérifié : `"id":"1"` en réponse. Auparavant le bean était un module Jackson 2
  (`com.fasterxml`), ignoré par le mapper Jackson 3, d'où des IDs en nombres. ⚠️ Portée **globale** :
  tout `Long` sort en chaîne (IDs mais aussi `PageInfo.totalElements`) — point de contrat frontend.
  Seule exception non vérifiable : `Users.id` (primitif `long`), car `/admin/users` a un 500 propre.
- `GeneratorOfVerificationCode` : codes numériques `SecureRandom` (flux OTT/vérif).

### 7.3 Audit (AOP)
- `@Audited(module, action, ressource?)` + `AspectAudit` (`@Around @annotation`) : capture
  acteur (`SecurityAuditContextProvider` → `UserPrincipal`), requête (URI, méthode, IP,
  user-agent, session), statut SUCCESS/FAILURE, persiste `AuditLog` (colonnes `jsonb`
  `metadata_json`/`diff_json`). Posé sur les contrôleurs admin.
- `AuditContext` (ThreadLocals `putMeta`/`setDiff`) : hooks d'enrichissement **câblés mais
  jamais appelés** aujourd'hui → metadata/diff quasi vides. `AuditDiffUtil` (diff par
  réflexion) existe mais n'est pas invoqué.
- `AuditServiceImpl.save` est `@Async @Transactional(REQUIRES_NEW)` **mais `@EnableAsync`
  absent** → exécution **synchrone** de fait.
- `SettingsAuditLogs` : flux d'audit de configuration séparé (`setting_audit_logs`).
- `UserSession` (`user_sessions`) : cycle de vie des sessions (crée access+refresh JWT, hashe
  le refresh, parse le device via `ua_parser`, IP via `X-Forwarded-For`). Code GeoIP MaxMind
  commenté. Lit `AdminAuditController` / `AdminSettingsAuditController` (GET paginés).

### 7.4 Idempotency (AOP)
- `@Idempotent(operation, requestBodyArgIndex=0, keyHeader="Idempotency-Key", required=false)`
  + `IdempotencyAspect`. `IdempotencyServiceImpl` : machine à états avec **verrou pessimiste**
  (`PESSIMISTIC_WRITE`) et transactions `REQUIRES_NEW` séparées (claim/complete/fail), hash
  SHA-256 du payload. Statuts PROCESSING/COMPLETED/FAILED (`idempotency_record`, unique
  `(operation, idempotency_key)`). Rejeu d'un COMPLETED = la réponse stockée est renvoyée sans
  ré-exécuter la méthode ; même clé + payload différent → `ConflictException`. Sérialise via
  **Jackson 3 `tools.jackson`**. Vue admin : `AdminIdempotencyController` (GET paginé).

### 7.5 Config (`config/`, 3 classes seulement)
- `MlClientConfig` : `RestClient` bean `mlRestClient()` sur `bilanga.ml.base-url`.
- `JacksonConfig` : voir 7.2.
- `ApplicationConfig` : `app.error.verbose`, `app.time-zone` (défaut `Africa/Lagos`), bean
  `isDevEnvironment()`. **Toute la sécurité est configurée hors de ce package**, dans
  `security/config/` (`SecurityConfig`, `PasswordEncoderConfig`, `SecurityPropertiesConfig`,
  `TokenHashProperties`).

### 7.6 Utils
`ApiPath.V1 = "/sni/api/v1"` ; `ValidationUtils` (regex email/téléphone/NIU — **héritage
fintech Cameroun**) ; `DateTimeUtils` ; `Normalization` + `CodeComposer` (génération de codes
métier lisibles — héritage fintech) ; `SystemActors.SYSTEM_USER_ID=0L`.

---

## 8. Sécurité (détail)

- **Modèle** : `Users` (soft-delete `deleted`, `failed_login_attempts`, lock), `Role`,
  `Permission` (`getFullPermissionName()` = `module:action`), liaisons `role_user` &
  `role_permission` (clés composites). Autorités résolues par `UserPrincipal` :
  `ROLE_<name>` + `permission.name` + `module:action`.
- **Auth** (`AuthenticationController` `/sni/api/v1/auth`) : `login`, `refresh` (rotation du
  refresh token, seul le **hash HMAC** est stocké — `TokenHashService`), `ott/request|validate`
  (code 6 chiffres + token de vérification), `password-reset/request|confirm` (token = UUID,
  **hash SHA-256 nu** stocké), `me`, `logout`, `unlock-account(/confirm)`,
  `email/verify/resend`. `PasswordResetFormController` sert un formulaire Thymeleaf (CSRF
  double-submit + CSP par page). Lockout : `failed-login.max-attempts` (défaut 5).
- **JWT** (`JWTService`, jjwt HMAC-SHA256) : types `access` (porte `sessionId`+`roles`),
  `refresh`, `verification`. Filtres : `RateLimitingFilter` (fenêtre fixe en mémoire par IP)
  → `JWTFilter` → `UsernamePasswordAuthenticationFilter`. Stateless, CSRF off.
- **Autorisation** : `@PreAuthorize('MODULE:ACTION')` sur les contrôleurs admin (méthode-
  sécurité) + `AdminApiAuthorizationManager` (SUPER_ADMIN bypass ; sinon ADMIN + permission
  dérivée du path/méthode). Contrôleurs admin : `/admin/users`, `/admin/roles`,
  `/admin/permissions`, `/admin/*/roles|permissions`, `/admin/provisioning`, plus les vues
  audit/idempotency.
- **⚠️ Faiblesses à durcir** (voir aussi CLAUDE.md §6) : `permitAll("/**")` fourre-tout qui
  court-circuite `AdminApiAuthorizationManager` ; `JWTFilter` **auto-admin** par défaut
  (`admin@bokati.com` sans token) ; CORS `*` ; secret JWT par défaut en dur ; `bootstrap-admin`
  non gardé ; emails OTT/reset commentés (codes renvoyés dans la réponse) ; handlers 401/403
  qui construisent un body jamais écrit.

---

## 9. Dette, scaffolding résiduel & pièges

- **Scaffolding « bokati » (fintech) non câblé** : dépendances `pom.xml` (RabbitMQ/AMQP,
  Spring Batch, JobRunr, Quartz, Redis/JDBC session, WebSocket, HATEOAS, Freemarker) et
  réglages `.env` (MinIO, `outbox/document/contract worker`, rate-limits mobile-money,
  `bokati-documents`, pawaPay) **n'ont AUCUN code Java** correspondant. `AdminApiAuthorizationManager`
  mappe même des modules `BILLING/PAYMENT/KYC/DOCUMENTS/...` inexistants côté métier.
  → Ne rien documenter ni construire dessus comme si c'était en place.
- **Aucun async/worker/scheduler réel** (cf. 7.3).
- **Dérive de config** `@Value` ↔ `application.yaml` (cf. CLAUDE.md §7) → défauts du code.
- **`.env` incohérent** : `SPRING_DATASOURCE_URL` (port 5434) ≠ `application.yaml` (55820) ≠
  `compose.yaml` (55820→5432). Deux mots de passe DB différents (`bilanga25` vs `bokati25`).
  Vérifier la config effective avant de lancer.
- **Tests** : un seul, `contextLoads()`. Pas d'infra de test isolée (ni H2, ni Testcontainers).
  Tout nouveau test a besoin d'un Postgres joignable. Chantier ouvert.
- **`enums/` non intégré** : seul `Culture` peuplé ; le reste du domaine est en `String`.
- **Doublons morts** : `diagnosis/dto/response/SoilPrediction|VisionPrediction` (utiliser
  ceux de `client/dto/response`).

---

## 10. Schéma de base (Flyway, source de vérité)

Migrations dans `src/main/resources/db/migration/`. Conventions d'ID :
`@IdGeneration` (Snowflake, fourni par l'app) → `BIGINT` ; `@GeneratedValue(IDENTITY)` →
`BIGSERIAL` (uniquement `role` et `permission`).

- **V1 — sécurité/audit/idempotency** : `users`, `role`, `permission`, `role_permission`,
  `role_user`, `refresh_token`, `password_reset_token`, `one_time_token`, `audit_log`,
  `user_sessions`, `setting_audit_logs`, `idempotency_record`. Extension **`pg_trgm`** +
  fonctions `search_admin_user|role|permission` (recherche floue trigram, appelées en JOIN
  par les repos) + index GIN trigram.
- **V2 — métier** : `plots`, `crops`, `iot_devices`, `sensors`, `sensor_readings`,
  `observations`, `crop_requirement`, `knowledge_rules`, `disease_knowledge`,
  `correlation_rules`, `ai_models`, `diagnostics`, `recommendations`, `alerts`.
- **V3 — seed** : 3 `ai_models` (EfficientNet manioc 0.64 / tomate 0.98, RandomForest sol
  0.82), `crop_requirement` tomate+manioc, 7 `knowledge_rules`, `disease_knowledge`
  (5 manioc + 6 tomate, codes maladie normalisés), 5 `correlation_rules`.
- **V4** : `ai_models.crop_name` (manioc/tomate ; le tabulaire reste NULL).
- **V5** : index FK + composites séries temporelles, dont
  `sensor_readings(plot_id, recorded_at DESC)`, `crops(plot_id, status, planting_date DESC)`,
  `diagnostics(plot_id, diagnosed_at DESC)`.
- **V6** : `disease_risk_condition` (+ seed tomate/manioc) — moteur de risque.
- **V7** : `recommendation_arbitration` (+ seed) — synthèses de conflits.
- **V8** : lifecycle alertes (`signature`, `acknowledged_at`, `resolved_at`, index).
- **V9** : traçabilité recommandations (`source_rule_id`, `measure_field`, `observed_value`,
  `threshold_value`).
- **V10** : `crop_stage_requirement` — seuils agronomiques **par stade** (tomate 5 stades,
  manioc 4). Ne porte que les écarts au `crop_requirement` général.
- **V16** — champs métier & géolocalisation : `plots` (`latitude`, `longitude`, `altitude`,
  `irrigation_type`, `plot_code` + séquence `plot_code_seq`), `crops` (`cycle_duration_days`,
  `expected_harvest_date`, `planted_area`, `plant_density`, `seed_lot`), `sensor_readings`
  (`temperature_sol`, `pluviometrie`, `conductivite_electrique`, `signal_strength`),
  `iot_devices` (`last_seen_at`, `firmware_version`, `installed_at`, `battery_voltage`),
  `alerts` (`assigned_to`, `due_at`), `recommendations` (`estimated_cost`).
  ⚠️ `temperature` **n'est pas renommée** : elle désigne l'air, et le contrat d'API comme la
  feature map ML en dépendent. `temperature_sol` s'ajoute à côté.
- **V17** — santé des sondes : `iot_devices.sensor_health|_reason|_checked_at`,
  `alerts.category` (AGRONOMIQUE | TECHNIQUE).
- **V18** — acheminement des notifications : `users.phone`, table
  `notification_preference`, `notification_outbox.group_key|deferred_until`. L'unicité
  `(alert_id, channel)` de la V15 est **remplacée** par un index simple : le regroupement
  déduplique désormais mieux qu'elle.
- **V19** — `interventions` (journal des actions au champ ; `recommendation_id` nullable).
- **V20** — `weather_forecast` (cache par parcelle et échéance) + `chk_recommendations_type`
  étendue à `METEO`.
- **V21** — `harvests` (quantité, qualité, prix unitaire ; `crop_id` **obligatoire**).
- **V22** — organisation : `cooperatives`, `farms`, `farm_membership` (rôle), `plots.farm_id`.
  ⚠️ **Purement additive** : `plots.farm_id` et `farms.cooperative_id` sont nullables, une
  parcelle sans exploitation se comporte comme avant, et une appartenance **ajoute** un accès
  sans jamais en retirer au propriétaire direct.
- **V23** — complément de la V22 : `farms.contact_phone`, séquences `cooperative_code_seq` et
  `farm_code_seq`, `idx_farms_owner`. Née d'un incident de checksum — la V22 avait été réécrite
  après avoir été appliquée. **Tout y est conditionnel** : sans effet sur une base neuve, où la
  V22 crée déjà ces objets. Voir `IMPLEMENTATION_V16_V22.md` §1 bis.

> ⚠️ Les valeurs agronomiques seedées sont **indicatives** (le commentaire de V10 le dit
> explicitement) : à faire valider par des sources agronomiques avant exploitation réelle.

---

## 11. Config utile (référence rapide)

- Routes : préfixe `/sni/api/v1`. Serveur `:8080`.
- IA : `bilanga.ml.base-url` (défaut `http://localhost:8000`), endpoints `/predict/vision-b64`
  et `/predict/soil`.
- Ingestion : header `X-Device-Key`, clé `bilanga.ingest.device-key`.
- Seuils (défauts code) : confiance high 0.85 / low 0.60 ; risque min 0.60 / high 0.85 ;
  agronomic min-severity 0.05 ; overview device-silence 15 min ; diagnosis min-interval 5 min.
- Sécurité (défauts `@Value`) : `app.security.jwt.*` (secret en dur !), `...failed-login.
  max-attempts=5`, `...rate-limit.*`, `...auto-admin.enabled=true` / `.email=admin@bokati.com`.
- Dev : `docker compose up -d postgres` (55820), pgAdmin (:45210), Redis (:6379) et MinIO
  (:9000/:9001) déclarés mais non utilisés par le code.

---

## 12. Inventaire des contrôleurs & réflexes de développement

### 12.1 Inventaire REST (tous préfixés `/sni/api/v1`)

`ApiPath.V1` n'est qu'un **préfixe de mapping**, pas un context-path servlet : l'actuator
reste sur `/actuator/*` (seuls `/actuator/health` et `/actuator/info` sont permis).

> Inventaire recoupé avec la table de routage runtime (`RequestMappingHandlerMapping` en
> TRACE, relevé du 2026-07-28) : conforme. Pour régénérer cette vérité terrain, voir §12.2-3.

| Contrôleur | Base | Garde | Notes |
|---|---|---|---|
| `AuthenticationController` | `/auth` | public | login, refresh, logout, me, ott/*, password-reset/*, unlock-account/*, email/verify/resend |
| `PasswordResetFormController` | `/auth/password-reset/form` | public | pages **Thymeleaf** (CSRF + CSP par page) |
| `UserProvisioningController` | `/admin/provisioning` | ⚠️ aucune | `bootstrap-admin`, `staff` |
| `AdminUserController` | `/admin/users` | `SYSTEM:USERS` | CRUD + activate/deactivate/unlock/reset-password ; `@Audited` + `@Idempotent` |
| `RoleAdminController` | `/admin/roles` | `SYSTEM:ROLES` | CRUD rôles |
| `PermissionAdminController` | `/admin/permissions` | `SYSTEM:PERMISSIONS` | CRUD permissions |
| `RoleAssignmentAdminController` | `/admin/...` | `SYSTEM:ROLES`/`:PERMISSIONS` | user↔role, role↔permission |
| `AdminAuditController` | `/admin/audit-logs` | admin | lecture paginée du journal d'audit |
| `AdminSettingsAuditController` | `/admin/settings-audit-logs` | admin | lecture de l'audit de configuration |
| `AdminIdempotencyController` | `/admin/idempotency-records` | admin | lecture paginée des enregistrements d'idempotence |
| `PlotController` | `/plots` | (voir §8) | CRUD parcelles (`?userId=`) ; `/{id}/history`, `/{id}/history.csv`, `/{id}/timeline`, `/{id}/economics` |
| `CropController` | `/crops` | | CRUD cultures (`?plotId=`) |
| `IotDeviceController` | `/devices` | | CRUD (`/technical/{technicalId}`, `?plotId=`) |
| `SensorController` | `/sensors` | | CRUD (`?deviceId=`) |
| `SensorReadingController` | `/readings` | | create/delete/findById/findByPlot |
| `ObservationController` | `/observations` | | CRUD scopé par plot |
| `IngestController` | `/ingest` | clé `X-Device-Key` | `readings`, `health` |
| `DiagnosisController` | `/diagnosis` | | image/sensor (+ `/predict`), findById, `?plotId=` |
| `AlertController` | `/alerts` | | GET (`?plotId=&category=&level=&status=&openOnly=`), `PATCH /{id}/acknowledge|resolve|assign` — pas de create/update (alertes levées par le moteur) |
| `OverviewController` | `/overview` | | `plots`, `plots/{plotId}`, `farm`, `economics` (comparaison entre parcelles) |
| `CooperativeController` | `/admin/cooperatives` | admin | CRUD ; archivage, jamais suppression |
| `FarmController` | `/admin/farms` | admin | CRUD exploitations (`?cooperativeId=&ownerId=`) + `GET|POST /{id}/members`, `DELETE /{id}/members/{userId}` |
| `InterventionController` | `/interventions` | | CRUD journal des actions au champ ; `GET /{id}/effect` = comparaison avant/après |
| `HarvestController` | `/harvests` | | CRUD récoltes (`?plotId=&cropId=`) |
| `NotificationPreferenceController` | `/notifications/preferences` | utilisateur courant | `GET`/`PUT` seuil, canaux, heures de silence |
| `CropRequirementController` | `/knowledge/crop-requirements` | | CRUD seuils (+ `/stages`) |
| `DiseaseKnowledgeController` | `/knowledge/diseases` | | CRUD maladies (+ `/conditions`) |
| `DecisionRuleController` | `/knowledge` | | CRUD `/rules`, `/correlations`, `/arbitrations` |

**Ces routes existent désormais** (implémentées le 2026-07-28, vérifiées HTTP 200 sur
l'instance de dev). Ce n'étaient que des **contrôleurs manquants** : les services admin
existaient déjà en entier (`DiseaseKnowledgeService` couvre maladies + conditions,
`DecisionRuleService` couvre règles + corrélations + arbitrages), seule la couche REST
manquait. Nouveaux contrôleurs, minces (aucun nouveau service/DTO/mapper) :

| Route | Contrôleur | Service délégué |
|---|---|---|
| `/alerts` (GET liste ouverte ou `?plotId=&openOnly=`, `PATCH /{id}/acknowledge`, `PATCH /{id}/resolve`) | `AlertController` (diagnosis) | `AlertService` — lecture + cycle de vie seulement (les alertes sont **levées par le moteur**, pas créées par API) |
| `/knowledge/diseases` (+ `/conditions`) CRUD | `DiseaseKnowledgeController` | `DiseaseKnowledgeService` |
| `/knowledge/rules`, `/correlations`, `/arbitrations` CRUD | `DecisionRuleController` | `DecisionRuleService` |

> ✅ Corrigé le 2026-07-28 : `GlobalExceptionHandler` a désormais un
> `@ExceptionHandler(IllegalArgumentException.class)` → **400 `BAD_REQUEST`** avec le message
> métier (les services knowledge lèvent `IllegalArgumentException` sur violation de règle :
> seuils incohérents, doublon, culture inconnue). Vérifié : POST doublon → 400. Vaut pour tous
> les contrôleurs knowledge.

### 12.2 Réflexes de dev dans ce dépôt

1. **Avant d'écrire un contrôleur/service, ouvrir un équivalent existant et copier ses
   conventions** (enveloppe `ApiResponse`, scoping par `plotId`/user, pagination, gestion
   d'erreurs). `ObservationController` (+ son service/DTO) est un bon modèle « CRUD scopé par
   parcelle » — notamment pour livrer le futur `AlertController` (une `Alert` ressemble à une
   `Observation` : rattachée à un plot, avec FK nullables vers `reading` et `diagnostic`).
2. **Un 404 avec `errorCode: "ENDPOINT_NOT_FOUND"`** (message « The requested endpoint does
   not exist. ») = **route non implémentée**, pas un bug de sécurité ni de base. C'est le
   mapping `NoResourceFoundException → ENDPOINT_NOT_FOUND` de `GlobalExceptionHandler`.
   Distinguer de `RESOURCE_NOT_FOUND` (404 métier : la route existe, l'entité est introuvable).
3. **Vérifier la table de routage réelle** en cas de doute : logger
   `org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping` en
   `TRACE`, ou exposer l'endpoint actuator `mappings`
   (`management.endpoints.web.exposure.include=mappings`, non exposé par défaut).
4. **Schéma** : toute évolution = nouvelle migration Flyway `Vn__*.sql` alignée sur les
   entités (`ddl-auto: validate` échoue au démarrage sinon). Ne jamais éditer une migration
   appliquée.
5. **IA** : toujours passer par `VisionClient`/`TabularClient`, jamais coupler le code des
   modèles. Prévoir le cas « service ML indisponible » (`IllegalStateException`).
6. **Ne pas se fier aux anciens docs** pour les faits techniques (cf. §0) ; le code + les
   migrations font foi.