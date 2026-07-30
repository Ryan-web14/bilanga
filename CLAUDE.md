# CLAUDE.md — Backend Bilanga (plateforme d'agriculture intelligente)

> Mémoire de projet chargée à chaque session. Concis et **factuel** : décrit le code
> tel qu'il est, pas tel qu'on aimerait qu'il soit. Le détail complet (orchestration,
> flux, schéma, endpoints, dette) est dans **@docs/ARCHITECTURE.md**.
>
> ⚠️ Les fichiers `PROJECT_CONTEXT.md` (racine) et l'ancienne version de ce fichier
> décrivaient une cible idéalisée. Plusieurs de leurs affirmations sont **fausses** vis-à-vis
> du code réel (voir §7). En cas de doute, la source de vérité est : le code +
> les migrations Flyway (`src/main/resources/db/migration/`) + @docs/ARCHITECTURE.md.

---

## 1. Ce qu'est le projet

Backend Spring Boot, **API REST pure** (aucune vue métier ; quelques pages Thymeleaf
uniquement pour le formulaire de reset password). Orchestrateur central d'une plateforme
d'agriculture intelligente pour le Congo : capteurs IoT → ingestion → diagnostic
(IA + moteur agronomique déterministe) → recommandations → alertes → dashboards.
Cultures couvertes aujourd'hui : **tomate** et **manioc** uniquement.

Deux grands blocs cohabitent dans le même module Maven :
- **Métier agricole** : `farm`, `iot`, `diagnosis`, `knowledge`, `overview`, `intervention`,
  `harvest`, `weather`, `notification`, `organization`, `enums`.

> **`organization` (V22) n'est jamais obligatoire.** `Cooperative → Farm → Plot`, tous les
> rattachements nullables. Une parcelle sans exploitation fonctionne comme avant. Une
> appartenance **ajoute** un accès dans `AccessGuard`, elle n'en retire jamais : le
> propriétaire direct garde le sien en toutes circonstances.
- **Infrastructure d'entreprise** : `security` (auth/rôles/permissions), `audit`,
  `idempotency`, `generator`, `config`, `exception`, `utils`, `templateResponse`.

Le microservice IA (Python/FastAPI, modèles RandomForest + EfficientNetB0 + YOLO) est un
**système tiers** appelé en REST sur `http://localhost:8000`. Jamais d'import direct.

Projet de mémoire de fin d'études. Binôme : Joel (backend + IA), Rolle (IoT + frontend React).

---

## 2. Stack technique RÉELLE

- **Java 25** (`pom.xml` : `<java.version>25</java.version>`), **Spring Boot 4.1.0**.
- **PostgreSQL** (dev via `compose.yaml`, port hôte **55820** ; pgAdmin, Redis, MinIO aussi
  déclarés dans compose mais voir §7). **Pas de H2.** Les tests ont besoin d'un Postgres réel.
- **Flyway** possède le schéma. JPA/Hibernate en `ddl-auto: validate` (valide, ne crée rien).
- **Lombok + MapStruct** (mappers `*Mapper` + décorateurs).
- **Jackson 3** (`tools.jackson`) — c'est Spring Boot 4, pas le `com.fasterxml` d'avant.
- IDs primaires : générateur **Snowflake** maison (`GeneratorOfId`) via `@IdGeneration`.
- Client IA : **JDK `HttpClient`** (pas de Feign/RestTemplate) derrière des interfaces.

---

## 3. Architecture en un coup d'œil

Chaîne d'ingestion (synchrone, mais isolée des pannes) :
`IngestController` (clé device `X-Device-Key`) → `IngestServiceImpl` (persiste toujours la
lecture, valide la plausibilité matérielle) → `DiagnosisServiceImpl.diagnoseFromSensorReading`
→ moteurs `knowledge` → `Recommendation`s + `Alert` éventuelle. Si l'IA tombe, la lecture
est quand même sauvegardée (l'échec de diagnostic est avalé proprement).

Diagnostic = 2 chaînes indépendantes : **IMAGE** (`VisionClient` → `/predict/vision-b64`)
et **CAPTEUR** (`TabularClient` → `/predict/soil`). Chaque diagnostic assemble ses
recommandations depuis **6 sources knowledge** (maladie, agronomique, risque, tendance,
**météo**, arbitrage des conflits), les **adapte à la parcelle** (`IrrigationAdapter` :
pas de « irriguez » sur une parcelle pluviale) puis peut lever une alerte.

Deux garde-fous en amont : `SensorHealthAnalyzer` **inhibe** le diagnostic quand la sonde est
`DEFAILLANTE` (mieux vaut ne rien conseiller que conseiller faux), et `GrowthStageResolver`
réaligne le stade de croissance sur la date de plantation au moment où le moteur s'en sert.

Détail complet des couches, classes pivots et endpoints : **@docs/ARCHITECTURE.md**.

---

## 4. Commandes

```bash
mvn clean install        # build + le test unique (contextLoads) — nécessite un Postgres up
mvn test                 # idem, besoin de la base
mvn spring-boot:run      # lance le backend sur :8080
docker compose up -d postgres   # base de dev (compose.yaml)
```

Préfixe de toutes les routes : **`/sni/api/v1`** (`ApiPath.V1`). Un reverse-proxy `/sni`
ne doit pas manger le préfixe.

---

## 5. Conventions non négociables

- **API REST pure.** Réponses enveloppées dans `ApiResponse<T>` (succès/erreur) ; listes
  paginées dans `PaginatedResponse<T>` (+ `PageInfo`). Erreurs via `GlobalExceptionHandler`
  (`@RestControllerAdvice`) → `ApiError` + `ErrorCode`. Ne pas renvoyer d'entités nues.
- **IDs = Snowflake (`@IdGeneration`, `Long`), sérialisés en STRING.** `JacksonConfig` enregistre
  un module **Jackson 3** (`tools.jackson.databind.module.SimpleModule`, auto-détecté par Boot 4)
  qui mappe tous les `Long`/`long`→String pour la précision JS. ⚠️ **Portée globale** : tout `Long`
  sort en chaîne, y compris les compteurs comme `PageInfo.totalElements` — c'est un point de contrat
  frontend. En entrée, Jackson accepte les deux formes.
- **Schéma = Flyway uniquement.** Toute évolution de schéma = **nouvelle migration
  `Vn__*.sql`** alignée sur les entités (car `ddl-auto: validate` échoue au démarrage sinon).
  Ne jamais éditer une migration déjà appliquée.
- **Couches** : `controller → service (interface + impl) → repository → model`, DTO en
  `dto/request|response`, mappers MapStruct en `service/support` (ou `mapper/`).
- **IA découplée** : dépendre des interfaces `VisionClient`/`TabularClient`, jamais du
  transport. URL via `bilanga.ml.base-url` (défaut `http://localhost:8000`).
- **Validation double et distincte** : plausibilité matérielle (ingestion,
  `hasImplausibleValue`) ≠ défavorabilité agronomique (moteurs knowledge). Ne pas les mélanger.
- Java 25, conventions Java standard, **pas de champ public**. Français pour les libellés
  métier et messages utilisateur (le domaine est francophone).
- Séparateur de permission : `MODULE:ACTION` (ex. `SYSTEM:USERS`).

---

## 6. Sécurité — état réel (à connaître avant d'y toucher)

- Spring Security **stateless + JWT** (jjwt HMAC-SHA256), filtres `RateLimitingFilter` →
  `JWTFilter`. Rôles + permissions ; méthode-sécurité via `@PreAuthorize('MODULE:ACTION')`.
- **Posture actuellement PERMISSIVE et à durcir avant toute démo/prod** :
  - `SecurityConfig` a un `permitAll()` fourre-tout `ApiPath.V1 + "/**"` **avant** le
    `adminApiAuthorizationManager` → l'autorisation par URL est court-circuitée ; seul
    `@PreAuthorize` protège encore les contrôleurs admin.
  - `JWTFilter` **auto-admin** activé par défaut (`app.security.auto-admin.enabled=true`) :
    une requête **sans token** est authentifiée comme `admin@bokati.com`.
  - CORS `allowedOriginPatterns("*")` avec `allowCredentials(false)`.
  - Secret JWT par défaut **codé en dur** dans `JWTService` ; secrets en clair dans `.env`.
  - `/admin/provisioning/bootstrap-admin` n'a **aucune** annotation d'autorisation.
  - Envoi d'email OTT / reset **commenté** : les codes reviennent dans la réponse API.
- **Demander confirmation avant** de modifier : `SecurityConfig`, `JWTFilter`, JWT/secrets,
  logique de rôles/permissions, ou les migrations de sécurité (`V1`).

---

## 7. Pièges & dette connus (NE PAS supposer que ça marche)

> **⚠️ Mise à jour du 2026-07-30 — séance « lots 1 à 6 » de `docs/PLAN_DE_CORRECTION.md`.**
> Plusieurs points de cette section sont **corrigés**. Le suivi complet est dans
> `docs/PLAN_DE_CORRECTION.md` §4. En résumé :
>
> | Ce que disait cette section | Aujourd'hui |
> |---|---|
> | « Aucun async réel · `@Async` sans `@EnableAsync` » | ✅ `config/AsyncConfig`, avec propagation du contexte de sécurité |
> | « Dérive de config `@Value` ↔ yaml » | ✅ **déjà corrigé avant** : tout passe par `BilangaProperties`/`AppProperties` |
> | « Tests quasi inexistants : un seul » | ✅ **324 tests**, 10 classes. `mvn test` tourne **sans base** (`contextLoads` est `@Tag("integration")`, écarté par défaut) |
> | « `GeneratorOfId` fait des `System.out.println` » | ✅ passé en `log.trace` |
> | « Scaffolding bokati : dépendances inutilisées » | ✅ **19 artefacts retirés** du `pom.xml`, plus le code mort (routes pawaPay/documents de `JWTFilter`, règle `/ws/**`) |
> | « `enums/` : `SoilType`/`Severity`/`DiagnosticSource` vides » | ⚠️ **à revérifier** — ils sont peuplés et `DomainEnums` les utilise |
> | « Capacités non exposées : `AlertService` sans contrôleur » | ✅ `AlertController` existe (voir `docs/ARCHITECTURE.md` §12) |
>
> **Nouveau vocabulaire introduit cette séance** — à connaître avant d'y toucher :
> - `RecommendationType.VOISINAGE` (8ᵉ moteur, **V27**). ⚠️ Tout nouveau type exige
>   d'étendre `chk_recommendations_type` par migration, sinon l'insertion échoue
>   **au cœur du diagnostic** et fait perdre le diagnostic entier.
> - `NotificationLanguage` (FR / LN lingala / KG kituba) : **l'enveloppe des
>   notifications est traduite, le constat agronomique reste en français** — décision
>   documentée dans `NotificationMessages`, à ne pas « compléter » sans la lire.
> - `app.security.open-business-routes.enabled` : le `permitAll` fourre-tout est
>   désormais **pilotable** et non plus codé en dur. Ordre de bascule dans
>   `ConfigurationGuard.logHardeningPath`, journalisé au démarrage.
> - Migrations neuves **V25, V26, V27** : jamais appliquées, elles s'exécuteront au
>   prochain démarrage.


- **Scaffolding « bokati » (fintech) résiduel.** Beaucoup de dépendances `pom.xml`
  (RabbitMQ/AMQP, Spring Batch, JobRunr, Quartz, Redis/JDBC session, WebSocket, HATEOAS,
  Freemarker) et de réglages `.env` (MinIO, outbox/document/contract workers, rate-limits
  mobile-money, `bokati-documents`) **ne sont utilisés par AUCUN code Java**. Ce sont des
  restes d'un projet fintech. Ne pas documenter ni bâtir dessus comme si c'était en place.
- **Aucun worker / outbox / scheduler / async réel.** Pas de `@Scheduled`, `@EnableScheduling`
  ni `@EnableAsync`. `AuditServiceImpl.save` est `@Async` mais **s'exécute en synchrone**
  faute de `@EnableAsync`.
- **Dérive de config** : plusieurs clés `@Value` (`bilanga.ml.base-url`,
  `bilanga.confidence.*`, `bilanga.risk.high-score`, `bilanga.agronomic.min-severity`,
  `bilanga.overview.device-silence-minutes`) ne correspondent PAS aux chemins imbriqués de
  `application.yaml` (`bilanga.risk.ml.*`, `bilanga.risk.max-score`,
  `bilanga.diagnosis.threshold.overview.*`) → les **valeurs par défaut du code** s'appliquent.
- **Tests quasi inexistants** : un seul test, `BilangaApplicationTests.contextLoads()`
  (`@SpringBootTest`, nécessite la base). Les sections « H2 / tests unitaires / MockMvc /
  @MockBean AiClient » des anciens docs sont **fictives**. Écrire de vrais tests est un
  chantier ouvert, pas un acquis.
- `GeneratorOfId` fait des `System.out.println` à chaque ID généré (bruit en prod).
- Reste à vérifier : `/admin/users` renvoie un **500** préexistant (cause distincte, non liée à
  la sérialisation ; `Users` est la seule entité à `id` primitif `long`). À investiguer.
- `enums/` : seul `Culture` (TOMATE, MANIOC) est peuplé ; `SoilType`/`Severity`/
  `DiagnosticSource` sont vides — le code modélise tout en `String`. Ne pas s'appuyer dessus.
- Capacités non exposées : `AlertService.acknowledge/resolve/findOpen` n'ont pas de
  contrôleur ; DTO `SoilPrediction`/`VisionPrediction` dupliqués (`diagnosis/dto` = morts,
  `client/dto` = utilisés).

---

## 8. Quand demander / planifier / s'arrêter

- **Planifier avant d'agir** sur : nouvelle migration Flyway / évolution de schéma,
  changement de contrat d'API (impacte le frontend de Rolle → le signaler), refactoring
  transverse.
- **Demander confirmation avant** : supprimer colonnes/tables, éditer une migration
  existante, toucher à la sécurité (§6), modifier la logique des appels au microservice IA.
- **Ne jamais** coupler directement le backend au code des modèles IA (rester en REST).
- **Après toute modif** : `mvn test` (ou au moins `mvn clean install`) avec un Postgres up,
  et vérifier que Hibernate `validate` passe au démarrage.

---

## 9. Références

- **@docs/ARCHITECTURE.md** — contexte technique complet : orchestration, modules, flux,
  schéma, endpoints, config, dette. À lire pour tout travail non trivial.
- **@docs/IMPLEMENTATION_V16_V22.md** — **document de reprise**. Ce qui a été livré rang par
  rang, les décisions et leurs raisons, les cinq invariants à ne pas casser, et ce qui reste
  à faire par priorité. **À lire en premier au démarrage d'une session.**
- **@docs/API_BACKEND.md** — le *pourquoi* du backend : décisions, invariants, réflexes pour
  ajouter une ressource / un moteur / un canal, pièges. À jour V16→V22.
- **@docs/API_FRONTEND.md** — le contrat exposé aux clients (Rolle). À jour V16→V22.
- **@docs/API_FRONTEND_CYCLES.md** — *clore et relire* une campagne : `/crops/{id}/close`,
  `/closure`, `/journal`, `/calendar`, `/diagnosis/at`. V28.
- **@docs/API_FRONTEND_CONDUITE.md** — *conduire* une campagne : succession, comparaison
  N vs N−1, itinéraire technique, clonage, seuils effectifs. V29. Les trois docs frontend
  ne se recouvrent pas ; celle-ci est la plus récente.
- **@docs/RBAC_FRONTEND.md** — rôles, permissions, matrice route→permission, écrans
  d'administration. Vocabulaire semé par la V24.
- **@docs/BILAN_ET_PERSPECTIVES.md** — bilan, audit priorisé (A1→A18) et fonctionnalités
  à venir par rapport valeur/effort.
- **@docs/PLAN_DE_CORRECTION.md** — **le plan d'exécution** : 8 lots ordonnés par dépendance,
  avec vérification et risque par lot. À suivre plutôt qu'à improviser.
- `src/main/resources/db/migration/V1..V21__*.sql` — le schéma réel (source de vérité).
- `docs/EVOLUTIONS_PROPOSEES.md` — feuille de route. **Rangs 1 à 9 livrés** (V16→V22).
- `PROJECT_CONTEXT.md` (racine) — narratif « pourquoi » du mémoire (vision), à lire avec
  l'avertissement du haut : conceptuellement utile, techniquement daté.
- `compose.yaml`, `.env` — infra dev (attention au scaffolding résiduel, §7).