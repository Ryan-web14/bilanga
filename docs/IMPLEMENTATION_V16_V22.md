# Implémentation V16 → V23 — document de reprise

> **Écrit pour être lu au démarrage d'une session, sans contexte préalable.**
> Séance du **2026-07-29**, complétée le **2026-07-30**.
> Objet : livrer les rangs 1 à 9 de `EVOLUTIONS_PROPOSEES.md` §10.
>
> **État : les neuf rangs sont livrés. `mvn clean compile` passe.
> V16 → V22 sont appliquées en base de développement ; V23 s'applique au prochain démarrage.**

---

## 1. Où en est-on, en trois phrases

Huit migrations (`V16` → `V23`) et quatre nouveaux modules Java (`intervention`, `harvest`,
`weather`, `organization`) ont été ajoutés, plus une trentaine de fichiers modifiés dans les
modules existants.

Le fil conducteur : **le système calcule et décide davantage, il ne demande pas davantage de
saisie.** Chaque colonne ajoutée est l'entrée d'un calcul — stade déduit, santé des sondes
inférée, destinataire résolu, effet d'une intervention mesuré, marge agrégée.

Le schéma est vérifié colonne par colonne contre la base de développement (§1 bis). Ce qui
n'a **pas** encore été fait : le parcours fonctionnel des neuf rangs (§7.2).

---

## 1 bis. L'incident V22 — à lire avant de toucher aux migrations

**Ce qui s'est passé.** La V22 a été écrite **trois fois dans la même séance**, au fil de
changements d'avis sur le périmètre : coopérative complète → exploitation seule → organisation
complète. La base de développement a reçu la **première** version. Le fichier local a ensuite
changé deux fois, et Flyway a refusé de démarrer :

```
Migration checksum mismatch for migration version 22
-> Applied to database : -302551347
-> Resolved locally    : -1483483187
```

**C'est précisément la règle « ne jamais éditer une migration appliquée », et elle a été
enfreinte.** Renommer le fichier n'y change rien : Flyway suit le **numéro de version**, pas
le nom.

**Ce qui a été fait pour réparer** (2026-07-30) :

1. **Diagnostic d'abord, réparation ensuite.** Les trois variantes de V22 ne produisaient pas
   le même schéma : un `repair` aveugle aurait déclaré la V22 conforme alors qu'il manquait des
   objets, et `ddl-auto: validate` aurait échoué juste après. L'inspection a montré que la
   version appliquée créait bien `cooperatives`, `farms`, `farm_membership` et `plots.farm_id`,
   mais qu'il manquait :

   | Manquant | Conséquence |
   |---|---|
   | `farms.contact_phone` | `validate` échouait sur `Farm.contactPhone` |
   | `cooperative_code_seq` | `nextCodeSequence()` aurait planté à la première coopérative créée |
   | `farm_code_seq` | idem pour une exploitation |
   | `idx_farms_owner` | cosmétique |

2. **Réalignement de l'historique** — l'équivalent exact de `flyway repair` :

   ```sql
   UPDATE flyway_schema_history
      SET checksum = -1483483187, script = 'V22__organization.sql', description = 'organization'
    WHERE version = '22';
   -- ancien checksum, si besoin de revenir en arrière : -302551347
   ```

3. **`V23__organization_completion.sql`** comble les quatre objets. **Tout y est conditionnel**
   (`IF NOT EXISTS`) : sur une base **neuve**, la V22 les crée déjà et la V23 ne fait rien. Les
   deux chemins convergent vers le même schéma — condition pour que ce rattrapage ne devienne
   pas lui-même une source de divergence.

> **La leçon, pour la suite.** Une migration cesse d'être modifiable **dès le premier
> démarrage de l'application**, pas au moment du commit. En cas de changement d'avis sur le
> périmètre en cours de séance, la bonne réponse est une migration **suivante**, jamais une
> réécriture — même si le fichier vient d'être écrit dix minutes plus tôt.

---

## 2. Ce qui a été livré, rang par rang

### Rang 1 — Champs métier et géolocalisation · `V16__business_fields.sql`

| Table | Colonnes ajoutées |
|---|---|
| `plots` | `latitude`, `longitude`, `altitude`, `irrigation_type`, `plot_code` (+ séquence `plot_code_seq`) |
| `crops` | `cycle_duration_days`, `expected_harvest_date`, `planted_area`, `plant_density`, `seed_lot` |
| `sensor_readings` | `temperature_sol`, `pluviometrie`, `conductivite_electrique`, `signal_strength` |
| `iot_devices` | `last_seen_at`, `firmware_version`, `installed_at`, `battery_voltage` |
| `alerts` | `assigned_to` (FK users), `due_at` |
| `recommendations` | `estimated_cost` |

**Nouveau code**

| Fichier | Rôle |
|---|---|
| `enums/IrrigationType` | `PLUVIAL` / `GOUTTE_A_GOUTTE` / `ASPERSION` / `MANUEL` + `cannotIrrigate()` |
| `farm/service/support/GrowthStageResolver` | Déduit le stade en **fraction du cycle** ; `stageTimeline()` reconstitue les dates de changement |
| `farm/service/support/PlotCodeGenerator` | `PARC-2026-000014` via séquence PostgreSQL |
| `knowledge/service/support/IrrigationAdapter` | Reformule les conseils d'arrosage sur parcelle pluviale |
| `diagnosis/dto/request/AlertAssignmentRequest` | `{ userId?, dueAt? }` |

**Branchements**

- `ContextResolver.resolve` appelle `cropService.refreshGrowthStage(crop)` — **le stade est
  réaligné là où le moteur va s'en servir**, pas par un ordonnanceur.
- `DiagnosisServiceImpl.build` appelle `knowledgeService.adaptToPlot(...)` **après**
  l'arbitrage et **avant** le tri.
- `IngestServiceImpl.touch(device, request)` remplace `updateBattery` : met à jour
  `lastSeenAt`, batterie, tension, firmware à chaque contact.
- `IngestController.health(?technicalId=)` → `ingestService.touchByTechnicalId(...)`.
- `OverviewServiceImpl.deviceStatus` se fonde désormais sur `lastSeenAt` (repli sur le dernier
  relevé pour les boîtiers antérieurs).
- `DiagnosisServiceImpl.toFeatureMap` ajoute `temperature_sol`, `pluviometrie`,
  `conductivite_electrique` — **clés ajoutées, aucune renommée**.
- `PlausibilityChecker` étendu : `temperature_sol ∈ [-10, 60]` (plus étroit que l'air : le sol
  tamponne les extrêmes), `pluviometrie ∈ [0, 500]`, conductivité ≥ 0.
- Nouveau `PATCH /alerts/{id}/assign`.

---

### Rang 2 — Détection de panne de sonde · `V17__sensor_health.sql`

```
iot_devices  + sensor_health (SAINE|SUSPECTE|DEFAILLANTE), sensor_health_reason,
               sensor_health_checked_at
alerts       + category (AGRONOMIQUE|TECHNIQUE)  NOT NULL DEFAULT 'AGRONOMIQUE'
```

| Fichier | Rôle |
|---|---|
| `enums/SensorHealth` | + `blocksDiagnosis()`, `warrantsCaution()`, `worst()` |
| `enums/AlertCategory` | `AGRONOMIQUE` / `TECHNIQUE` |
| `iot/service/support/SensorHealthAnalyzer` | Les trois règles + `Verdict` |
| `BilangaProperties.SensorHealth` | `stuck-readings` 6, `window-hours` 12, `drift-tolerance` 0.25, `decoupling-tolerance` 0.60 |

**Branchements**

- `IngestServiceImpl.assessHealth(device)` — **hors du chemin critique**, `try/catch` global
  qui retombe sur `SAINE`.
- `DEFAILLANTE` ⇒ `skipReason = SONDE_DEFAILLANTE`, le diagnostic n'est pas lancé.
- `AlertService.raiseTechnical(plot, deviceKey, reason, faulty)` — signature
  `TECHNIQUE:<technicalId>`, niveau `ELEVEE`, **se referme** quand le verdict repasse `SAINE`.
- `DiagnosisResult.dataQualityNote` renseigné quand la sonde est `SUSPECTE`.
- `AlertRepository.search` gagne un paramètre `category` ; `AlertController` aussi.
- Deux nouvelles requêtes dans `SensorReadingRepository` :
  `findByDevice_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc`, `findPeerReadings`.

---

### Rang 3 — Canal SMS · `V18__notification_delivery.sql`

```
users                 + phone
notification_preference   (nouvelle table)
notification_outbox   + group_key, deferred_until
DROP INDEX uq_notification_alert_channel      ← remplacé par un index simple
```

| Fichier | Rôle |
|---|---|
| `notification/channel/HttpSmsChannel` | Passerelle générique, pilotée par configuration |
| `notification/model/NotificationPreference` | + repository |
| `notification/service/RecipientResolver` | Destinataire, seuil personnel, report, tranche de regroupement |
| `notification/service/NotificationPreferenceService` | + DTO requête/réponse |
| `notification/controller/NotificationPreferenceController` | `GET`/`PUT /notifications/preferences` |
| `BilangaProperties.Sms` | `url` vide ⇒ canal indisponible |

**Modifications**

- `NotificationService.enqueue` réécrite : résout le destinataire, filtre par canal accepté,
  calcule `deferredUntil`, regroupe via `appendTo(...)`.
- `dispatchPending` utilise `findDispatchable(statuses, now, page)` qui écarte les reports.
- `AdminCreateUserRequest`, `AdminUpdateUserRequest`, `AdminUserResponse`,
  `UserAdminServiceImpl` : champ `phone`.

**Pourquoi l'index unique de V15 est supprimé** : il datait d'un temps où une alerte donnait au
plus un envoi par canal. Le regroupement change cela, et `group_key` déduplique mieux — il
couvre plusieurs alertes, là où l'index n'en couvrait qu'une.

---

### Rang 4 — Chronologie unifiée · aucune migration

| Fichier | Rôle |
|---|---|
| `enums/TimelineEventType` | 7 natures |
| `overview/dto/response/PlotTimeline` | + `TimelineEntry` |
| `overview/service/support/TimelineComposer` | Fusion des 7 sources |

`GET /plots/{id}/timeline?from=&to=&types=&page=&size=` sur `PlotController`.
`OverviewService.timelineForPlot(...)` pagine **après** la fusion.

§8.1 (séries agrégées) et §8.3 (vue exploitation) existaient déjà.

---

### Rang 5 — Journal d'interventions · `V19__interventions.sql`

Module `intervention/` complet : `model`, `repository`, `dto/request|response`,
`service/interfaces|implementation`, `service/support/{InterventionMapper, EffectAnalyzer}`,
`controller`.

| Fichier | Rôle |
|---|---|
| `enums/InterventionType` | 7 types, portant `targetMeasure` et `expectsIncrease` |
| `EffectAnalyzer` | Comparaison 48 h avant / 48 h après |

**Bouclage** : `InterventionServiceImpl.markRecommendationApplied` bascule le conseil en
`APPLIQUEE`, sans toucher à un conseil déjà tranché.

`RecommendationRepository.uptakeSummary(plotId, from, to)` ajoutée pour le rang 8.
`TimelineComposer` gagne la source `INTERVENTION`.

---

### Rang 6 — Météo · `V20__weather_forecast.sql`

```
weather_forecast (nouvelle table, UNIQUE (plot_id, forecast_at))
ALTER chk_recommendations_type  → ajoute 'METEO'
```

Module `weather/` : `model`, `repository`, `client/{interfaces,implementation,dto}`, `service`.

| Fichier | Rôle |
|---|---|
| `weather/client/interfaces/WeatherClient` | Contrat neutre — les noms de champs d'Open-Meteo ne remontent pas |
| `weather/client/implementation/OpenMeteoClient` | JDK `HttpClient`, patron de `MlHttpExchange`, `timezone=UTC` |
| `weather/service/WeatherService` | Cache, rafraîchissement à la demande, `REQUIRES_NEW` |
| `knowledge/service/support/WeatherEngine` | 6ᵉ moteur, 3 règles |

**Branchement** : `knowledgeService.assessWeather(ctx.getPlot())` dans les **quatre** entrées
de `DiagnosisServiceImpl`.

⚠️ **La contrainte `chk_recommendations_type` devait être étendue** : sans cela la première
recommandation `METEO` ferait échouer son insertion — et, survenant au cœur du diagnostic,
ferait perdre le diagnostic entier. **Même réflexe pour tout nouveau moteur.**

---

### Rang 7 — Explication comparative · aucune migration

| Fichier | Rôle |
|---|---|
| `diagnosis/dto/response/AlternativeComparison` | |
| `diagnosis/service/support/ComparativeExplainer` | `compare(...)` et `compareFromMeasurements(...)` |

`DiagnosisResult.comparison` et `DiagnosisExplanation.comparison` ajoutés.
`DiagnosisExplainer` prend `ComparativeExplainer` en dépendance.

Sur `/explain`, `modelProbability` est `null` : les probabilités du classifieur ne sont pas
conservées. La comparaison porte alors sur les seules conditions mesurées, exactement
reproductibles depuis le relevé enregistré.

---

### Rang 8 — Rendement et économie · `V21__harvest.sql`

Module `harvest/` complet + `enums/HarvestQuality` + `utils/export/CsvSeriesWriter`.

| Route | Rend |
|---|---|
| `POST\|PUT\|GET\|DELETE /harvests` | CRUD |
| `GET /plots/{id}/economics` | `PlotEconomics` |
| `GET /overview/economics` | Liste triée par marge/ha |
| `GET /plots/{id}/history.csv` | Export |

`MarginCalculator` recalcule tout à la demande — rien n'est stocké.

---

### Rang 9 — Organisation · `V22__organization.sql`

```
cooperatives      (+ séquence cooperative_code_seq)
farms             (+ séquence farm_code_seq)   cooperative_id NULLABLE
farm_membership   UNIQUE (farm_id, user_id)
plots + farm_id   NULLABLE
```

Module `organization/` : 3 entités, 3 repositories, DTO, `OrganizationService` (un seul service
pour les trois niveaux), `OrganizationMapper`, `CooperativeController`, `FarmController`.

| Fichier | Rôle |
|---|---|
| `enums/MembershipRole` | 4 rôles, `allows(AccessScope)` |
| `enums/AccessScope` | `AGRONOMIQUE` / `ECONOMIQUE` / `TECHNIQUE` |

**`AccessGuard` réécrit** — c'est la pièce centrale du rang :

```java
requireAccess(plot)          // propriétaire direct OU membre de l'exploitation
requireScope(plot, scope)    // levant   — employé sur /plots/{id}/economics
canRead(plot, scope)         // non levant — employé sur /overview/economics
visibleFarmIds()             // périmètre des recherches
```

`PlotRepository.search` gagne `hasFarmScope` + `farmIds`.
`PlotServiceImpl` définit `NO_FARM_SCOPE = List.of(-1L)` : `in ()` sur collection vide n'est
pas du SQL valide.

---

## 3. Les invariants à ne pas casser

Cinq propriétés ont guidé toute la séance. Les rompre annulerait l'essentiel du travail.

### 3.1 Le relevé n'est jamais perdu

La persistance a lieu **avant** toute opération faillible. Tout échec en aval est attrapé et
converti en `skipReason`.

> Perdre un diagnostic pour un service tiers muet est acceptable. Perdre une mesure ne l'est
> pas : elle est irremplaçable, l'instant est passé.

### 3.2 Une capacité indisponible retire une capacité, elle ne casse rien

Trois interrupteurs, même comportement : désactivé, **le système fonctionne, il fait juste
moins**.

| Réglage | Défaut | Effet à l'arrêt |
|---|---|---|
| `bilanga.notification.sms.url` | **vide** | canal indisponible, rien n'est enfilé, aucun échec compté |
| `bilanga.weather.enabled` | `true` | `WeatherEngine` rend une liste vide |
| `bilanga.sensor-health.enabled` | `true` | verdict `SAINE` systématique |

Idem sans coordonnées sur la parcelle, sans boîtier voisin, sans microservice d'inférence.

### 3.3 L'organisation est purement additive

- `plots.farm_id` et `farms.cooperative_id` **nullables**.
- `AccessGuard.roleOn()` **court-circuite** sur `farm == null` — le cas majoritaire ne coûte
  aucune requête supplémentaire.
- `hasAnyAccess()` = propriétaire direct **OU** membre. Une appartenance **ajoute** un accès,
  n'en retire jamais.
- Archiver une exploitation laisse ses parcelles intactes.

> Une exploitation mal configurée ne peut enfermer personne dehors.

### 3.4 On reformule, on n'efface pas

`IrrigationAdapter` **complète** le conseil d'arrosage au lieu de le supprimer ;
`ConflictArbitrator` **ajoute** une synthèse sans retirer les conseils qu'elle concilie.

> Effacer le conseil ferait disparaître le problème avec lui, ce qui est pire que de proposer
> une action irréalisable.

Corollaire technique : `IrrigationAdapter` **préserve** `measureField`, `observedValue`,
`thresholdValue`, `sourceRuleId` — sinon `DiagnosisExplainer` ne saurait plus justifier.

### 3.5 Un chiffre sans réserve est lu comme une démonstration

Trois champs sont **toujours** renseignés et destinés à l'affichage :

| Champ | Où | Dit quoi |
|---|---|---|
| `limitation` | `InterventionEffect` | une comparaison avant/après n'établit pas une cause |
| `limitation` | `PlotEconomics` | « conseils suivis / rendement » est un constat, pas une causalité |
| `missingData` | `PlotEconomics` | pourquoi tel chiffre est nul — absence de saisie, pas absence de fait |
| `dataQualityNote` | `DiagnosisResult` | la confiance du modèle ne dit rien de la fiabilité de la sonde |

---

## 4. Décisions prises, et pourquoi

| Décision | Alternative écartée | Raison |
|---|---|---|
| `temperature` **non renommée** | `temperature_air` | Casserait le contrat d'API **et** la feature map du microservice d'inférence. `temperature_sol` s'ajoute, deux `COMMENT ON COLUMN` lèvent l'ambiguïté |
| **Coopérative incluse**, mais non bloquante | Farm seul, ou rien | Demandé explicitement. Le non-blocage est tenu par la nullabilité en cascade et par `AccessGuard` qui n'élargit jamais qu'en ajoutant |
| **Open-Meteo** | OpenWeatherMap | Aucune clé d'API : démontrable sans compte à gérer, ni abonnement susceptible d'expirer avant la soutenance |
| **Passerelle SMS générique** | SDK Twilio ou Africa's Talking | Trois opérateurs, un seul appel HTTP. Un client par opérateur obligerait à **livrer du code pour changer de fournisseur** — au moment précis où l'ancien ne marche plus |
| Stade en **fraction du cycle** | Table de correspondance par variété | Une variété précoce et une tardive traversent les mêmes phases dans les mêmes proportions. `cycleDurationDays` ajuste tout d'un coup |
| Recalcul du stade dans `ContextResolver` | `@Scheduled` | Le projet n'a pas d'ordonnanceur, et un stade recalculé que personne ne lit n'a aucune valeur |
| **Médiane** des voisins | Moyenne | Avec trois boîtiers dont un en panne, la moyenne serait tirée par le fautif et disculperait celui qu'on examine |
| Fenêtre d'effet **48 h** | 24 h, ou 7 j | Assez pour lisser le cycle jour/nuit, assez court pour que l'effet domine encore |
| Fusion de la chronologie **en mémoire** | Union SQL sur 7 tables | Illisible et fragile ; volume borné à ~100 lignes par la fenêtre et le plafond par source |
| Marge **recalculée**, jamais stockée | Colonne de total | Un total en cache diverge dès la première correction de saisie, et personne ne sait plus lequel croire |
| `interventions.recommendation_id` **nullable** | Obligatoire | Les exploitants agissent aussi de leur propre chef, et ce sont ces actions-là qui expliquent des évolutions incompréhensibles autrement |
| `harvests.crop_id` **obligatoire** | Nullable | Une récolte sans campagne n'entre dans aucun bilan : l'accepter serait enregistrer une donnée sans usage |
| Suppression **réelle** des interventions et récoltes | Archivage | Une saisie fautive y fausserait les calculs, qui sont leur raison d'être |

---

## 5. Corrections apportées à la documentation existante

Deux affirmations de `API_FRONTEND.md` (version du matin) étaient devenues fausses et ont été
corrigées :

1. **« Ne triez pas sur `priority`, `level`, `status` — le tri est alphabétique. »**
   Faux : `SemanticSort` est en place sur `alerts.level|status` et
   `recommendations.priority|status`. Le frontend a peut-être encore des contournements à
   supprimer.
2. **« Aucune notification ne sort du serveur. »**
   Faux depuis le rang 3.

`docs/Documentation_API_BILANGA (3).md` (24 juillet) **n'a pas été touché** et contredit
désormais les deux nouveaux documents. À archiver ou supprimer — voir §7.

---

## 6. Inventaire des fichiers

### Créés (48)

```
db/migration/  V16__business_fields.sql   V17__sensor_health.sql
               V18__notification_delivery.sql   V19__interventions.sql
               V20__weather_forecast.sql   V21__harvest.sql   V22__organization.sql

enums/         IrrigationType  SensorHealth  AlertCategory  InterventionType
               HarvestQuality  TimelineEventType  MembershipRole  AccessScope

farm/service/support/       GrowthStageResolver  PlotCodeGenerator
iot/service/support/        SensorHealthAnalyzer
knowledge/service/support/  IrrigationAdapter  WeatherEngine
diagnosis/                  dto/request/AlertAssignmentRequest
                            dto/response/AlternativeComparison
                            service/support/ComparativeExplainer
overview/                   dto/response/PlotTimeline
                            service/support/TimelineComposer
utils/export/               CsvSeriesWriter

notification/   channel/HttpSmsChannel   model/NotificationPreference
                repository/NotificationPreferenceRepository
                service/{RecipientResolver, NotificationPreferenceService}
                controller/NotificationPreferenceController
                dto/request/NotificationPreferenceRequest
                dto/response/NotificationPreferenceResponse

intervention/   model  repository  dto/request  dto/response(×2)
                service/{interfaces, implementation}
                service/support/{InterventionMapper, EffectAnalyzer}  controller

harvest/        model  repository  dto/request  dto/response(×2)
                service/{interfaces, implementation}
                service/support/{HarvestMapper, MarginCalculator}  controller

weather/        model  repository  client/{interfaces, implementation, dto}  service

organization/   model(×3)  repository(×3)  dto/request(×3)  dto/response(×3)
                service/{interfaces, implementation}  service/support/OrganizationMapper
                controller/{CooperativeController, FarmController}

docs/           IMPLEMENTATION_V16_V22.md  (ce fichier)
```

### Modifiés — les points d'attention

| Fichier | Nature de la modification |
|---|---|
| **`security/access/AccessGuard`** | **Réécrit** — cœur du rang 9 |
| `NotificationService` | `enqueue` réécrite (destinataire, report, regroupement) |
| `DiagnosisServiceImpl` | 6ᵉ moteur ×4 entrées, `adaptToPlot`, `comparison`, `dataQualityNote`, feature map |
| `IngestServiceImpl` | `touch()`, `assessHealth()`, `touchByTechnicalId()`, `SKIP_FAULTY_SENSOR` |
| `AlertServiceImpl` | `raiseTechnical()`, `assign()`, `category`, `overdue` |
| `ContextResolver` | Appelle `refreshGrowthStage` |
| `KnowledgeService`/`Impl` | `assessWeather`, `adaptToPlot` |
| `OverviewServiceImpl` | `timelineForPlot`, `deviceStatus` sur `lastSeenAt` |
| `PlotController` | `/timeline`, `/economics`, `/history.csv` |
| Entités | `Plot`, `Crop`, `SensorReading`, `IotDevice`, `Alert`, `Recommendation`, `Users` |
| Config | `BilangaProperties` (+3 blocs), `PropertiesConfig`, `application.yaml` |
| Docs | `CLAUDE.md`, `ARCHITECTURE.md`, `EVOLUTIONS_PROPOSEES.md`, `API_FRONTEND.md` (réécrit), `API_BACKEND.md` (créé) |

---

## 7. Ce qui reste à faire — par ordre de priorité

### 7.1 ✅ Fait — migrations appliquées et schéma vérifié

**V16 → V22 sont appliquées** en base de développement (`bilanga_dev_db`, conteneur
`bilanga_dev_db`, port hôte 55820), toutes en `success = t`.

Le schéma a été **confronté colonne par colonne** aux entités le 2026-07-30 :

| Vérifié | Résultat |
|---|---|
| `plots` : `latitude`, `longitude`, `altitude`, `irrigation_type`, `plot_code`, `farm_id` | ✅ |
| `crops` : `cycle_duration_days`, `expected_harvest_date`, `planted_area`, `plant_density`, `seed_lot` | ✅ |
| `sensor_readings` : `temperature_sol`, `pluviometrie`, `conductivite_electrique`, `signal_strength` | ✅ |
| `iot_devices` : `last_seen_at`, `firmware_version`, `installed_at`, `battery_voltage`, `sensor_health` ×3 | ✅ |
| `alerts` : `assigned_to`, `due_at`, `category` | ✅ |
| `recommendations` : `estimated_cost` | ✅ |
| `users.phone`, `notification_outbox` : `group_key`, `deferred_until` | ✅ |
| Tables `interventions`, `harvests`, `weather_forecast`, `notification_preference`, `farm_membership`, `cooperatives`, `farms` | ✅ colonnes conformes |
| `farms.contact_phone`, `cooperative_code_seq`, `farm_code_seq`, `idx_farms_owner` | ⚠️ **manquants → comblés par V23** |

**Reste à faire, une seule chose** : redémarrer l'application. Flyway applique V23, puis
`ddl-auto: validate` s'exécute **pour la première fois** sur l'ensemble V16→V23.

```bash
mvn spring-boot:run     # à lancer par l'utilisateur, dans son IDE
```

Si `validate` signale encore un écart, il portera sur un détail de **type** (précision d'un
`NUMERIC`, longueur d'un `VARCHAR`) et non sur une colonne absente — celles-là ont toutes été
vérifiées.

### 7.2 🟠 Important — vérifications fonctionnelles

Une fois l'application démarrée. Préfixe `/sni/api/v1`.

| Rang | À vérifier |
|:---:|---|
| 1 | `POST /plots` avec coordonnées → `plotCode` généré, `geolocated: true`. Sur parcelle `PLUVIAL` en stress hydrique : **aucun conseil « irriguez » nu**, une alternative de paillage. `GET /crops/{id}` → `growthStageAutoResolved` |
| 2 | Rejouer 6 relevés identiques → `sensorHealth: DEFAILLANTE`, alerte `category: TECHNIQUE`, relevé suivant `skipReason: SONDE_DEFAILLANTE`. Puis un relevé normal → l'alerte **se referme** |
| 3 | `sms.url` vide → aucune ligne `SMS` dans `/admin/notifications`. Pointée sur un récepteur local → `ENVOYEE`. Alerte `ELEVEE` en heure de silence → `deferred_until` ; `CRITIQUE` → immédiat |
| 4 | `GET /plots/{id}/timeline` → flux trié mêlant les 7 natures ; `countsByType` sur la fenêtre entière |
| 5 | `POST /interventions` avec `recommendationId` → le conseil passe `APPLIQUEE`. `GET /{id}/effect` sur une irrigation → verdict chiffré |
| 6 | `weather.enabled: false` → diagnostic identique à avant. À `true` sur parcelle géolocalisée → conseil `METEO` **et insertion réussie** (contrainte étendue) |
| 7 | `GET /diagnosis/{id}/explain` sur un diagnostic image → `comparison` non vide |
| 8 | `GET /plots/{id}/economics` après récolte + interventions → marge, marge/ha, `missingData` cohérent |
| 9 | Avec `ownership.enabled: true` : `CONSEILLER` membre lit les parcelles de la ferme ; `TECHNICIEN` sur `/economics` → **403** ; **propriétaire non membre garde son accès** |

**Le dernier point du rang 9 est le plus important** : c'est la garantie de non-blocage.

### 7.3 🟡 Souhaitable

| Tâche | Pourquoi |
|---|---|
| **Premiers tests unitaires** | Les classes de `service/support` sont sans état ni transaction, donc directement instanciables : `GrowthStageResolver`, `PlausibilityChecker`, `IrrigationAdapter`, `ComparativeExplainer`, `CsvSeriesWriter`, `MarginCalculator`. **C'est par là qu'il faut commencer** — aucune base requise |
| **Archiver `Documentation_API_BILANGA (3).md`** | Daté du 24 juillet, contredit `API_FRONTEND.md`. Quelqu'un le lira |
| **Signaler à Rolle les deux corrections** du §5 | Il a peut-être des tris de contournement à supprimer |
| **Seed de démonstration** | Une parcelle géolocalisée, deux boîtiers (pour que la comparaison de sondes fonctionne), une culture datée, quelques interventions et une récolte |
| **Nettoyer `GeneratorOfId`** | `System.out.println` à chaque identifiant généré |
| **Purge de `weather_forecast`** | Déclenchée au rafraîchissement seulement : une parcelle qui cesse d'être interrogée conserve ses prévisions périmées |

### 7.4 ⚪ Non entrepris — hors périmètre des neuf rangs

| Item | Note |
|---|---|
| §9.2 signalement des règles à réviser | `GET /recommendations/uptake` fournit la donnée ; il manque l'écran et le seuil |
| §9.3 seuils adaptatifs par parcelle | |
| §9.4 risque de voisinage | **Les coordonnées et l'index géographique existent maintenant** — c'est le prochain candidat naturel |
| §9.5 versionnement de la connaissance | La traçabilité par colonnes tient lieu de palliatif |
| §8.4 export PDF | Le CSV existe |
| Canaux WhatsApp et e-mail | `NotificationChannel` les accueille sans changer le reste |
| `estimatedCost` sur les règles | La colonne existe et est exposée ; aucune règle ne la renseigne |
| Durcissement de la sécurité | **Chantier distinct**, volontairement non touché — voir §8 |

---

## 8. Ce qui n'a délibérément pas été touché

**La posture de sécurité.** `SecurityConfig`, `JWTFilter`, l'auto-admin, le `permitAll("/**")`,
le secret JWT en dur : tout est resté en l'état. C'est un chantier distinct qui demande une
décision, pas une modification opportuniste au fil d'un autre travail.

`app.security.ownership.enabled` reste à **`false`** : le cloisonnement du rang 9 est donc
écrit, compilé, mais **inerte**. C'est voulu — l'activer sans comptes en base rendrait l'API
inutilisable.

**Le scaffolding fintech** (RabbitMQ, Batch, Quartz, JobRunr, WebSocket, HATEOAS, Freemarker,
MinIO, réglages mobile-money) n'a été ni utilisé ni retiré. Le retirer est une bonne idée, mais
c'est un lot en soi.

---

## 9. Pour reprendre — les cinq choses à savoir

1. **`mvn clean compile` passe. V16→V22 sont appliquées, V23 attend un redémarrage.**
   Le schéma est vérifié colonne par colonne ; le parcours **fonctionnel** des neuf rangs
   (§7.2) reste entier.

2. **Ne jamais éditer une migration appliquée** — et « appliquée » veut dire « l'application a
   démarré une fois », pas « c'est commité ». Cette règle a été enfreinte sur la V22 et a coûté
   un incident (§1 bis). Toute correction passe par une migration **suivante**, ici une V24.

3. **Un nouveau type de recommandation exige d'étendre `chk_recommendations_type`.** C'est le
   piège qui a failli coûter cher au rang 6 : la contrainte de V11 énumérait les moteurs
   d'alors, et l'insertion aurait échoué **au cœur du diagnostic**, faisant perdre le
   diagnostic entier.

4. **Les cinq invariants du §3 sont le cœur du travail.** Un ajout qui rend l'organisation
   obligatoire, qui fait échouer un diagnostic faute de météo, ou qui supprime un `limitation`
   à l'affichage, annule plus qu'il n'apporte.

5. **La source de vérité, dans cet ordre** : le code → les migrations Flyway →
   `ARCHITECTURE.md` → `API_BACKEND.md` → ce document. `PROJECT_CONTEXT.md` et
   `Documentation_API_BILANGA (3).md` décrivent une cible ancienne et sont **faux** sur
   plusieurs points techniques.
