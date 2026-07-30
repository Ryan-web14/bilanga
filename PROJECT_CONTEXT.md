# PROJECT_CONTEXT.md : Contexte détaillé du projet

> Référencé par CLAUDE.md. Ce fichier explique **ce qui a été fait, pourquoi, comment
> et à quoi ça sert** — le raisonnement derrière les décisions. Il n'est pas chargé au
> démarrage : Claude Code le lit à la demande.
>
> Pour la **cartographie technique exhaustive** (modules, flux, classes pivots, endpoints,
> schéma table par table, dette), voir **`docs/ARCHITECTURE.md`**. La source de vérité du
> schéma reste les migrations Flyway (`src/main/resources/db/migration/`). Ce document-ci
> a été réaligné sur le code réel (Spring Boot 4.1, Java 25).

---

## 1. Contexte et raison d'être du projet

### Le problème
La République du Congo dispose d'un fort potentiel agricole (près de 10 millions
d'hectares de terres arables) mais d'une productivité faible : l'agriculture pèse
environ 5 % du PIB tout en employant près de 40 % de la population active. Le pays
dépend fortement des importations alimentaires.

Sur le terrain, l'exploitant décide « à l'œil », sans données objectives, et détecte
les maladies trop tard. Les solutions d'agriculture de précision existantes sont trop
coûteuses, dépendantes d'une bonne connexion, et mal adaptées aux cultures locales.

### La réponse
Une plateforme accessible combinant IoT (suivi temps réel) et IA (diagnostic +
recommandations), utilisable par petites et grandes exploitations, depuis un simple
navigateur. Hypothèse : suivi temps réel + diagnostic IA + recommandations = meilleure
décision = meilleure productivité. Cultures couvertes aujourd'hui : **tomate** et
**manioc**.

---

## 2. Démarche de conception : le modèle en spirale

**Ce qui a été fait :** développement par cycles itératifs, chaque cycle validant une
brique avant de l'enrichir.

**Pourquoi ce choix (et pas l'agile) :** le projet comportait de fortes incertitudes
techniques (intégration IoT, entraînement et fiabilité des modèles IA, communication
entre composants hétérogènes). Le modèle en spirale place la gestion des risques au
cœur de chaque cycle : on traite d'abord l'incertitude la plus forte via un prototype,
on la valide, puis on avance. Plus adapté qu'un agile centré sur la livraison continue
à un client, ici absent.

**À quoi ça sert :** avoir sécurisé les briques risquées (IA, IoT) avant d'investir
dans le reste.

---

## 3. Le backend Spring Boot

**Ce qui a été fait :** une API REST pure (aucune vue métier ; seules quelques pages
Thymeleaf servent le formulaire de réinitialisation de mot de passe), jouant le rôle
d'orchestrateur central. Elle reçoit les mesures, les valide, les persiste, gère la
sécurité, et dialogue avec le microservice IA.

**Comment :**
- **Spring Boot 4.1 / Java 25**, architecture en couches (controller / service (interface
  + impl) / repository / model), DTO et mappers MapStruct. Préfixe de routes `/sni/api/v1`.
- **PostgreSQL** via JPA/Hibernate en `ddl-auto: validate` ; le schéma appartient à
  **Flyway** (migrations `V1..V10`). IDs primaires = générateur **Snowflake** maison
  (`@IdGeneration`), sérialisés en chaîne côté JSON (précision JavaScript).
- Réception des mesures capteurs → validation → persistance → déclenchement du diagnostic.
- Sécurité par **Spring Security + JWT** (stateless), rôles et permissions, avec en plus
  du token hashing, des sessions, du rate-limiting.

**Au-delà du strict « recevoir/valider/persister », le backend porte une infrastructure
d'entreprise conséquente**, non triviale et souvent passée sous silence dans les premières
descriptions :
- **Audit** transversal par AOP (`@Audited` + aspect) journalisant qui fait quoi, d'où, avec
  quel résultat, dans `audit_log` (colonnes JSONB).
- **Idempotency** par AOP (`@Idempotent`) : rejeu sûr des opérations d'administration via une
  clé `Idempotency-Key` et une machine à états avec verrou pessimiste.
- Gestion fine des **rôles/permissions** (`MODULE:ACTION`), provisioning d'admin, OTT (code à
  usage unique), reset de mot de passe, refresh tokens rotatifs.

**Pourquoi API REST pure et découplée :** permet à n'importe quel client (web aujourd'hui,
mobile demain) de consommer les mêmes services. Permet le développement parallèle : Rolle
avance sur le frontend pendant que le backend progresse, tant que le contrat d'API tient.

**À quoi ça sert :** c'est le chef d'orchestre — il coordonne le terrain, l'IA, la base et
l'utilisateur.

---

## 4. Les modèles d'intelligence artificielle

**Ce qui a été fait côté backend :** le backend **consomme** un microservice IA distinct
(Python/FastAPI) par REST. Il n'héberge ni n'importe aucun modèle. Deux chaînes d'inférence :
- **Chaîne image** : `VisionClient.predict(culture, image)` → `POST {ml}/predict/vision-b64`
  (image en base64) → renvoie une **classe de maladie** + une distribution de probabilités.
  Modèles sous-jacents : **EfficientNetB0** (un classifieur par culture — manioc, tomate).
- **Chaîne capteur** : `TabularClient.predict(features)` → `POST {ml}/predict/soil` → renvoie
  une catégorie d'état du sol + une confiance. Modèle sous-jacent : **Random Forest**.

Le registre `ai_models` trace ces modèles (seed : EfficientNet manioc ≈0.64, EfficientNet
tomate ≈0.98, Random Forest sol ≈0.82), pour relier chaque diagnostic au modèle qui l'a produit.

**Pourquoi cette combinaison :** le Random Forest exploite les données chiffrées des capteurs ;
l'analyse d'image juge la santé visible de la plante. EfficientNetB0 répond à « quelle
maladie ? » (classification précise et légère).

**Note d'honnêteté (localisation / YOLO) :** la vision d'ensemble du mémoire prévoit un
troisième modèle **YOLO** pour *localiser* la zone atteinte sur l'image. Aujourd'hui, **le
contrat d'API côté backend ne porte pas de localisation** : la réponse vision se limite à une
classe + des probabilités (pas de boîte englobante). YOLO, s'il existe, reste dans le
microservice IA sans être exploité par le backend — c'est une perspective, pas un acquis.

**Pourquoi un microservice séparé :** faire monter l'IA en charge et réentraîner les modèles
sans interrompre la plateforme. **Comment le découplage est tenu :** appels via `java.net.http.
HttpClient` du JDK, derrière des interfaces `VisionClient`/`TabularClient` — aucun import du
code des modèles, transport swappable. URL : `bilanga.ml.base-url` (défaut `http://localhost:8000`).

**À quoi ça sert :** transformer une donnée brute (mesure ou image) en diagnostic, puis en
recommandation.

---

## 5. Recommandations et raisonnement agronomique

**Ce qui a été fait :** après diagnostic, le système ne se contente pas de règles simples : il
fait tourner un **moteur agronomique déterministe et explicable** (paquet `knowledge`),
complémentaire du modèle statistique. Chaque diagnostic assemble ses recommandations depuis
**cinq sources**, puis les déduplique, arbitre les conflits, et les trie par priorité.

**Comment (les cinq moteurs, `knowledge/service/support`) :**
- **RiskEngine** : part des *mesures seules* pour estimer, par maladie, la part de conditions
  d'apparition réunies (score = poids satisfait / poids total sur `disease_risk_condition`) —
  une alerte précoce indépendante du modèle image.
- **AgronomicEngine** : compare chaque mesure aux plages optimales (`crop_requirement`, affinées
  par stade via `crop_stage_requirement`) et calcule une sévérité, plus des indicateurs dérivés
  (VPD, déséquilibre NPK).
- **TrendAnalyzer** : régression par moindres carrés sur une fenêtre récente et projection du
  moment où un seuil sera franchi (anticipation).
- **CorrelationEngine** : croise la maladie détectée avec les mesures (`correlation_rules`).
- **ConflictArbitrator** : quand deux conseils se contredisent (ex. « baisser l'humidité » vs
  « irriguer »), *ajoute* une synthèse d'arbitrage (`recommendation_arbitration`) expliquant
  comment concilier les deux, sans jamais en supprimer.

La base porte aussi `knowledge_rules` (règles mesure→action) et `disease_knowledge`
(traitement/prévention par maladie). L'orchestration vit dans `DiagnosisServiceImpl` +
`KnowledgeServiceImpl` (le controller ne raisonne pas). Les recommandations sont tracées
(règle source, champ mesuré, valeur observée, seuil).

**À quoi ça sert :** livrer non pas une donnée brute mais un conseil actionnable, ordonné, et
justifiable.

---

## 6. Le dispositif IoT (simulé sous Wokwi) et l'ingestion

**Ce qui a été fait :** firmware PlatformIO (C++) pilotant des capteurs (sol, air, luminosité,
pH), envoyant les mesures au backend en JSON via HTTP, **simulé sous Wokwi**. Côté backend,
l'ingestion est une verticale dédiée :
- `POST /sni/api/v1/ingest/readings`, authentifiée par une **clé de device partagée**
  (header `X-Device-Key`, comparaison en temps constant) — pas de JWT, car un microcontrôleur
  ne gère pas de cycle de vie de token.
- Le device est résolu par son `technicalId` (il porte son plot : un boîtier déplacé n'a rien à
  reprogrammer). La lecture est **toujours persistée**, puis le diagnostic capteur est déclenché
  de façon synchrone mais **isolée des pannes** : si l'IA est indisponible, la mesure n'est pas
  perdue.
- **Deux validations distinctes** : la *plausibilité matérielle* (pH∉[0,14], humidité∉[0,100],
  etc. → `anomalyDetected`, signe d'une sonde défaillante) est séparée de la *défavorabilité
  agronomique* (rôle des moteurs knowledge).

**Pourquoi la simulation (choix assumé) :** absence de matériel physique. Wokwi valide toute la
chaîne d'acquisition sans coût. Le même firmware est portable vers un ESP32 réel.

**À quoi ça sert :** valider le flux capteur → serveur de bout en bout.

---

## 7. Le frontend React

**Ce qui a été fait :** interface React hébergée séparément du backend, donnant accès à l'état
des parcelles, l'historique des mesures, les diagnostics, les alertes et les recommandations
(endpoints `overview`, `plots`, `crops`, `readings`, `diagnosis`).

**Pourquoi l'hébergement séparé :** déploiement indépendant (mettre à jour l'UI n'impose pas de
redéployer le backend) et meilleures performances.

**À quoi ça sert :** rendre la complexité technique lisible pour un utilisateur non technicien.

---

## 8. Base de données

**Ce qui a été fait :** schéma PostgreSQL d'environ **30 tables**, géré par **Flyway**
(migrations `V1..V10`, JPA en `validate`). Organisé en deux blocs :
- **Sécurité / audit / idempotency** (V1) : `users`, `role`, `permission`, `role_user`,
  `role_permission`, `refresh_token`, `password_reset_token`, `one_time_token`, `audit_log`,
  `user_sessions`, `setting_audit_logs`, `idempotency_record` — plus l'extension `pg_trgm` et
  des fonctions de recherche floue (`search_admin_user/role/permission`).
- **Métier agricole** (V2→V10) : `plots`, `crops`, `iot_devices`, `sensors`, `sensor_readings`,
  `observations`, `crop_requirement`, `crop_stage_requirement`, `knowledge_rules`,
  `disease_knowledge`, `correlation_rules`, `disease_risk_condition`,
  `recommendation_arbitration`, `ai_models`, `diagnostics`, `recommendations`, `alerts`.

**Décisions notables :**
- **PostgreSQL seul, sans TimescaleDB.** Les données sont majoritairement relationnelles ; pour
  les séries temporelles des capteurs, des index composites suffisent aux volumes visés — le
  principal étant `sensor_readings(plot_id, recorded_at DESC)` (et non un `sensor_id`, la lecture
  étant un relevé complet de parcelle). Éviter une base spécialisée = moins de complexité.
  Perspective si le volume explose : TimescaleDB ou partitionnement.
- **Relations N..N** matérialisées par tables de liaison à clé composite (`role_user`,
  `role_permission`).
- **Traçabilité** : `ai_models` relie chaque diagnostic à son modèle ; les `recommendations`
  gardent la règle source et la mesure déclenchante ; les `alerts` portent une signature de
  situation et un cycle de vie.

> ⚠️ Les seuils agronomiques seedés (`crop_requirement`, `crop_stage_requirement`,
> `disease_risk_condition`, etc.) sont **indicatifs** : à faire valider par des sources
> agronomiques avant toute exploitation en production (l'avertissement figure dans V10).

---

## 9. Tests — état réel (chantier ouvert, pas un acquis)

**Ce qui existe aujourd'hui :** un **seul** test, `BilangaApplicationTests.contextLoads()`
(`@SpringBootTest`), qui vérifie que le contexte Spring démarre. Il **nécessite un PostgreSQL
joignable** (pas de H2, `ddl-auto: validate` + Flyway).

**À corriger (honnêteté vis-à-vis des anciens documents) :** les descriptions antérieures
évoquaient une base de test H2 en mémoire, des tests unitaires JUnit/Mockito sur la couche
service, des tests d'intégration MockMvc, et un `@MockBean AiClient`. **Rien de tout cela
n'existe** dans le code (il n'y a même pas de classe `AiClient` — le client réel est
`VisionClient`/`TabularClient`).

**Pourquoi c'est important :** il n'y a aujourd'hui **aucun filet de sécurité automatisé** ; les
refactorings ne sont pas couverts. Écrire de vrais tests (unitaires sur les moteurs knowledge et
la logique de diagnostic ; intégration sur l'ingestion et les endpoints, avec le microservice IA
mocké ; base de test isolée type Testcontainers) est un **chantier prioritaire ouvert**.

---

## 10. Sécurité, déploiement et points d'attention connus

**Sécurité — posture actuelle permissive, à durcir avant toute démo/prod :**
- `SecurityConfig` contient un `permitAll("/sni/api/v1/**")` fourre-tout **avant** le gestionnaire
  d'autorisation admin, qui court-circuite l'autorisation par URL ; seul `@PreAuthorize` protège
  encore les contrôleurs d'administration.
- `JWTFilter` a un mode **auto-admin activé par défaut** : une requête *sans token* est
  authentifiée comme `admin@bokati.com`.
- Secret JWT par défaut **codé en dur** ; secrets en clair dans `.env` ; endpoint
  `/admin/provisioning/bootstrap-admin` non protégé par une autorisation.
- Envoi d'emails OTT / reset de mot de passe **commenté** : les codes reviennent dans la réponse
  API (pratique en dev, à ne pas laisser en prod).

**CORS :** configuration actuelle `allowedOriginPatterns("*")` avec `allowCredentials(false)`.
Dès qu'on passe `allowCredentials(true)`, il faudra des origines explicites (`*` interdit avec
credentials par le navigateur). En dev, JWT dans le header `Authorization` (pas de cookie).

**ngrok (tests) :** les URLs gratuites peuvent insérer une page d'avertissement ; ajouter
`ngrok-skip-browser-warning: true`. Un en-tête custom déclenche un preflight OPTIONS, déjà
autorisé côté Spring.

**Reverse-proxy :** vérifier qu'un proxy avec préfixe `/sni` ne mange pas les en-têtes CORS ni
les requêtes OPTIONS (le préfixe applicatif est déjà `/sni/api/v1`).

**Cohérence de configuration à surveiller :**
- **Scaffolding fintech « bokati » résiduel** : de nombreuses dépendances (`RabbitMQ`, Spring
  Batch, JobRunr, Quartz, Redis/JDBC session, WebSocket, HATEOAS, Freemarker) et réglages `.env`
  (MinIO, `outbox/document/contract worker`, callbacks mobile-money) **ne sont câblés à aucun
  code Java**. À ignorer ou nettoyer, pas à bâtir dessus.
- **Aucun traitement asynchrone réel** : pas de `@EnableAsync`/`@EnableScheduling` ; le `@Async`
  sur la sauvegarde d'audit s'exécute donc en synchrone.
- **Dérive de config** : plusieurs clés `@Value` (ex. `bilanga.ml.base-url`, `bilanga.confidence.*`)
  ne correspondent pas aux chemins imbriqués d'`application.yaml` → ce sont les **valeurs par
  défaut du code** qui s'appliquent. À réconcilier si l'on veut piloter par le YAML.
- **`.env` incohérent** (ports et mots de passe DB divergents entre `.env`, `application.yaml` et
  `compose.yaml`) : vérifier la configuration effective avant de lancer.

---

## 11. Perspectives (non encore faites)

- **Écrire une vraie couverture de tests** (cf. §9) — priorité, car aucun filet aujourd'hui.
- **Durcir la sécurité** (cf. §10) : retirer l'auto-admin, corriger l'ordre `permitAll`,
  externaliser les secrets, réactiver l'envoi d'emails.
- **Nettoyer le scaffolding fintech** et **réconcilier la config** (`@Value` ↔ YAML, `.env`).
- Portage du dispositif IoT sur matériel réel (ESP32) ; exploitation de la localisation image
  (YOLO) si le microservice l'expose.
- Enrichissement de la base de connaissances agronomiques et extension à d'autres cultures.
- Application mobile ; optimisation base de données si le volume de mesures augmente fortement.