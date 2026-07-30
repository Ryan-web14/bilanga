# API Bilanga — Documentation backend

> **Public** : quiconque modifie ce code — vous dans six mois, un relecteur, un jury.
> **Établie le 2026-07-29** depuis le code, après les migrations **V16 → V22**.
>
> Ce document explique **pourquoi** le backend est construit ainsi. Le contrat exposé aux
> clients est dans **`API_FRONTEND.md`** ; l'architecture d'ensemble dans **`ARCHITECTURE.md`**.
> Ici : les décisions, les invariants, et les pièges qu'on ne voit pas en lisant une signature.

---

## Sommaire

| § | Sujet |
|---|---|
| [1](#1-la-carte-du-code) | La carte du code |
| [2](#2-les-conventions-non-négociables) | Conventions non négociables |
| [3](#3-la-chaîne-dingestion) | La chaîne d'ingestion |
| [4](#4-le-hub-de-diagnostic) | Le hub de diagnostic |
| [5](#5-les-sept-moteurs-de-knowledge) | Les sept moteurs de `knowledge` |
| [6](#6-les-calculs-automatiques) | Les calculs automatiques |
| [7](#7-alertes-et-notifications) | Alertes et notifications |
| [8](#8-cloisonnement-et-organisation) | Cloisonnement et organisation |
| [9](#9-infrastructure-transverse) | Infrastructure transverse |
| [10](#10-schéma-de-base) | Schéma de base |
| [11](#11-configuration) | Configuration |
| [12](#12-ajouter-quelque-chose--réflexes) | Ajouter quelque chose : réflexes |
| [13](#13-dette-et-pièges-connus) | Dette et pièges connus |

---

## 1. La carte du code

```
com.sni.bilanga
├── farm          parcelles, cultures, stade calculé, code de parcelle
├── iot           boîtiers, capteurs, INGESTION, plausibilité, santé des sondes
├── diagnosis     hub d'orchestration, client IA, recommandations, alertes, explication
├── knowledge     7 moteurs déterministes + base de connaissance pilotable
├── weather       client Open-Meteo, cache de prévisions
├── intervention  journal des actions au champ + mesure de leur effet
├── harvest       récoltes, marge, rendement
├── overview      tableaux de bord, chronologie unifiée
├── notification  outbox, canaux, destinataires, préférences
├── organization  coopérative → exploitation → parcelle (FACULTATIF)
│
├── security      auth JWT, rôles, permissions, AccessGuard
├── audit         journalisation AOP
├── idempotency   rejeu sûr des POST admin (AOP)
├── config        propriétés typées, Jackson, cache, RestClient
├── exception     GlobalExceptionHandler, ErrorCode
├── generator     identifiants Snowflake
├── templateResponse  ApiResponse, PaginatedResponse, PageInfo
├── enums         vocabulaire du domaine
└── utils         ApiPath, TimeRange, SemanticSort, CodeComposer, CsvSeriesWriter
```

**Un seul module Maven.** Deux développeurs, un domaine : le découpage en microservices
coûterait en complexité ce qu'il ne rapporterait ni en performance ni en clarté.

**Le module `organization` est le seul dont rien ne dépend.** C'est intentionnel — il ajoute
une possibilité, jamais une contrainte.

---

## 2. Les conventions non négociables

### 2.1 Couches

```
controller → service (interface + impl) → repository → model
                ↘ service/support (logique pure, testable, sans transaction)
```

- **DTO** en `dto/request` et `dto/response`. **Jamais d'entité nue en réponse.**
- **Mappers** en `service/support` (composants Spring, pas MapStruct pour les nouveaux —
  la conversion explicite se lit mieux qu'une annotation).
- **`service/support`** porte la logique métier isolée : `AgronomicEngine`, `EffectAnalyzer`,
  `MarginCalculator`, `TimelineComposer`, `GrowthStageResolver`. Ces classes sont sans état et
  sans transaction — c'est ce qui les rend lisibles et testables.

### 2.2 Le schéma appartient à Flyway

`ddl-auto: validate`. Hibernate **valide**, ne crée rien.

> **Toute évolution de schéma = une nouvelle migration `Vn__*.sql`**, alignée sur les entités
> du même lot. Une divergence fait **échouer le démarrage** — c'est le filet, et il vaut mieux
> le déclencher en développement qu'en production.
>
> **Ne jamais éditer une migration appliquée.** Flyway compare les empreintes.

### 2.3 Vocabulaire fermé : énumération Java **et** contrainte `CHECK`

Les colonnes restent en `VARCHAR`. La frontière est tenue des deux côtés :

- **Java** — le DTO de requête est typé par l'énumération. Une valeur hors vocabulaire est
  refusée à la désérialisation, avec la liste des valeurs acceptées dans le message.
- **Base** — une contrainte `CHECK` garantit l'invariant même pour une écriture directe.

Pourquoi les deux : l'énumération protège l'API, la contrainte protège les données. Une faute
de frappe dans un service passerait sinon inaperçue jusqu'à ce qu'une comparaison de chaînes
échoue silencieusement, très loin de la cause.

**`DomainEnums`** est le point de passage unique : `parse` (tolérant à la casse, rend `null`
sur inconnu), `nameOf`, `matches`, `accepted`.

> **`Culture` est un cas à part** : la base et le microservice d'inférence stockent les
> cultures en **minuscules** (`tomate`), l'API expose la constante en **majuscules** (`TOMATE`).
> La conversion est portée par l'énumération (`storageName()`, `canonical()`), à un seul
> endroit. Le joker `'*'` désigne une règle valable quelle que soit la culture.

### 2.4 Identifiants Snowflake, sérialisés en chaînes

`GeneratorOfId` : 64 bits = timestamp + 10 bits machine + 12 bits séquence.
Branché par `@IdGeneration`.

`JacksonConfig` sérialise **tous les `Long`/`long` en String** (module **Jackson 3**,
`tools.jackson`, auto-détecté par Boot 4).

> **Portée globale, et c'est un point de contrat frontend.** Tout `Long` sort en chaîne, y
> compris `PageInfo.totalElements`. Les compteurs qui doivent rester des nombres portent
> `@JsonSerialize(using = CounterSerializer.class)` : `ageHours`, `plotCount`, `memberCount`,
> `farmCount`.
>
> ⚠️ **Piège historique** : un module **Jackson 2** (`com.fasterxml`) est silencieusement
> ignoré par le mapper Jackson 3 de Spring Boot 4. C'est ce qui avait fait sortir les
> identifiants en nombres pendant un temps. Vérifiez toujours l'import.

### 2.5 Réglages : `BilangaProperties`, jamais `@Value`

Un objet unique, typé et **validé** (`@Validated`, contraintes Bean Validation).

> **Le défaut que cela corrige.** Une trentaine d'annotations `@Value` disséminées, chacune
> avec sa valeur par défaut. Rien ne garantissait que la clé écrite dans le fichier soit celle
> que le code lisait — et de fait elles avaient divergé : le fichier déclarait
> `bilanga.risk.ml.base-url`, le code demandait `bilanga.ml.base-url`. Le réglage était donc
> ignoré en silence, et la valeur codée en dur s'appliquait.
>
> **Un seuil qu'on croit régler et qui ne bouge pas est pire qu'un seuil absent** : on cherche
> l'erreur ailleurs.

Avec un objet unique, la structure du fichier **est** la structure de la classe. Une clé mal
placée ne se lie à rien, et les contraintes font échouer le démarrage plutôt que de laisser
tourner un système mal réglé.

`PropertiesConfig` expose chaque groupe comme un bean : une classe injecte
`BilangaProperties.Trend` et ne peut pas lire un réglage d'un autre domaine.

### 2.6 Français pour le domaine

Libellés, messages d'erreur, `statement`, `rationale`, `summary`, `limitation` : **en
français**, rédigés pour être affichés tels quels. Le domaine est francophone, et faire
traduire côté client garantit trois traductions divergentes.

Le **code** (noms de classes, de méthodes, de variables) reste en anglais.

---

## 3. La chaîne d'ingestion

`IngestController` → `IngestServiceImpl` → `DiagnosisServiceImpl`

```
POST /ingest/readings          X-Device-Key
   │
   1. résout le boîtier par technicalId    → 404 si inconnu, en déduit la parcelle
   2. construit le SensorReading
   3. PlausibilityChecker → anomalyDetected + mesures fautives nommées
   4. PERSISTE le relevé                    ← toujours, quoi qu'il arrive ensuite
   5. touch(device) : lastSeenAt, batterie, tension, firmware
   6. SensorHealthAnalyzer → SAINE | SUSPECTE | DEFAILLANTE
   │     ├─ persiste le verdict sur le boîtier
   │     └─ lève / referme une alerte TECHNIQUE
   │
   ├─ DEFAILLANTE  → skipReason = SONDE_DEFAILLANTE, on s'arrête là
   ├─ DiagnosisThrottle dit « rien n'a bougé » → CONDITIONS_STABLES
   └─ sinon → diagnosisService.diagnoseFromSensorReading(...)
```

### 3.1 Le relevé n'est jamais perdu

C'est l'invariant central. La persistance a lieu **avant** toute opération susceptible
d'échouer, et chaque échec en aval est attrapé :

| Échec | Attrapé en | Résultat |
|---|---|---|
| Microservice IA muet | `ServiceUnavailableException` | `skipReason: ML_INDISPONIBLE` |
| Pas de culture déclarée | `ResourceNotFoundException` | `skipReason: CONTEXTE_ABSENT` |
| Analyse de santé en échec | `Exception` générique | verdict `SAINE` par défaut, ingestion poursuivie |
| Quoi que ce soit d'autre | `Exception` | `skipReason: ML_INDISPONIBLE` |

> **Perdre un diagnostic pour un service tiers muet est acceptable. Perdre une mesure ne
> l'est pas** : elle est irremplaçable, l'instant est passé.

### 3.2 Deux contrôles distincts, à ne jamais mélanger

| | `PlausibilityChecker` | `SensorHealthAnalyzer` |
|---|---|---|
| **Juge** | la **mesure** | la **sonde** |
| **Détecte** | l'absurde : pH 22, humidité 130 % | le figé, la dérive, le décrochage |
| **Fenêtre** | le relevé seul | 12 h de série + boîtiers voisins |
| **Effet** | `anomalyDetected: true` | verdict + alerte technique + inhibition |

> **Pourquoi le second existe.** Une sonde qui tombe en panne renvoie **rarement** une valeur
> absurde. Elle se fige sur sa dernière lecture, elle dérive à mesure que l'électrode
> s'encrasse, ou elle décroche de ses voisines — en restant tout du long dans des valeurs
> parfaitement crédibles.
>
> **C'est le seul angle mort qui puisse produire un conseil nuisible.** Un diagnostic fondé sur
> une sonde qui dérive est faux, et présenté avec exactement la même assurance qu'un diagnostic
> juste : la confiance du modèle mesure la certitude de la prédiction, jamais la fiabilité de
> la mesure qui l'a nourrie.

**Les trois règles de `SensorHealthAnalyzer`** :

| Signal | Détection | Verdict |
|---|---|---|
| **Valeur figée** | même valeur **exacte** sur N relevés consécutifs (défaut 6) | `DEFAILLANTE` |
| **Décrochage** | écart massif (> 0,60) à la médiane des boîtiers voisins | `DEFAILLANTE` |
| **Dérive** | écart modéré (> 0,25) à cette même médiane | `SUSPECTE` |

Trois décisions à connaître :

- **Égalité exacte** pour la valeur figée. Une mesure physique réelle varie toujours au moins
  sur sa dernière décimale. Deux relevés identiques arrivent ; six d'affilée ne sont plus un
  phénomène naturel.
- **Médiane** et non moyenne pour les voisins : avec trois boîtiers dont un déjà en panne, la
  moyenne serait tirée par le fautif et disculperait celui qu'on examine.
- **Écart rapporté à l'étendue observée**, pas à la valeur absolue : un écart de 2 sur un pH
  est considérable, le même sur une concentration d'azote est négligeable.

**La luminosité est exclue** de la comparaison : deux boîtiers distants de quelques mètres,
l'un à l'ombre et l'autre au soleil, relèvent légitimement des valeurs très différentes.

**Sans voisin, pas de comparaison.** Limite assumée : en l'absence de témoin, une dérive lente
est rigoureusement indiscernable d'une évolution réelle du sol. Seule la règle de la valeur
figée reste applicable — et c'est déjà la plus fréquente en pratique.

### 3.3 Le lot n'est pas transactionnel

`ingestBatch` traite chaque relevé **pour son propre compte**, via le proxy Spring
(`ObjectProvider<IngestService> self`) et non `this` — un appel direct court-circuiterait
l'intercepteur transactionnel.

> Un boîtier qui rentre après trois jours hors ligne ne doit pas perdre cent
> quatre-vingt-dix-neuf mesures valides parce que la deux-centième est corrompue.

---

## 4. Le hub de diagnostic

`DiagnosisServiceImpl` — quatre entrées, un pipeline commun.

```
resolve(contexte)                     ContextResolver
   ├─ culture      = culture en cours de la parcelle
   ├─ STADE        = RECALCULÉ ici (CropService.refreshGrowthStage)
   └─ relevé       = fourni, ou le dernier disponible

prédiction                            VisionClient | TabularClient   (ou fournie)
normalizeDiseaseCode                  "Tomato___Late_blight" → "Late_blight"
persistDiagnostic

assemblage des recommandations        6 sources
   ├─ maladie / catégorie             recommendForDisease | recommendForSensorDiagnostic
   ├─ agronomique                     analyzeAgronomic
   ├─ risque                          recommendForRisks
   ├─ tendance                        recommendForTrends
   └─ MÉTÉO                           assessWeather                   ← V20

deduplicate                           même conseil, deux règles → une fois
arbitrate                             AJOUTE une synthèse, ne retire rien
adaptToPlot                           IrrigationAdapter               ← V16
sortByPriority                        ARBITRAGE en tête à priorité égale
persist Recommendation[]
alertService.raiseIfNeeded
```

### 4.1 Confiance et fiabilité

`ConfidenceEvaluator` : seuils `high = 0,85`, `low = 0,60`.
**En deçà de `low`, `reliable = false` — et un diagnostic non fiable ne lève aucune alerte.**

> Un diagnostic peu fiable ne conclut rien : il ne doit ni lever d'alerte, ni servir de motif
> pour en refermer une. L'exploitant se déplacerait — ou cesserait de se déplacer — sur la foi
> d'une conclusion que le système lui-même ne soutient pas.

### 4.2 Corroboration : deux voies indépendantes

`corroborationFor` croise la maladie prédite par le **modèle vision** avec le score du
**moteur de risque**, calculé sur les seules mesures.

| Score de risque | Conclusion |
|---|---|
| ≥ 0,60 | les conditions **corroborent** le diagnostic |
| ≤ 0,20 | les conditions **ne soutiennent pas** la progression : symptôme d'un passé, extension peu probable |
| entre | rien de concluant — `null` |

La force de l'argument tient à l'indépendance : la probabilité vient d'un réseau convolutif
entraîné sur des images, le score de risque d'un moteur déterministe appliqué à des mesures de
sol. **Elles n'ont aucune information en commun.**

### 4.3 Explication comparative — `ComparativeExplainer`

Répond à « pourquoi cette maladie, et pas l'autre ? ».

Croise `alternatives` (probabilités du classifieur) et `riskFor(...)` (conditions mesurées),
et produit quatre énoncés distincts :

| Cas | Ce qu'on dit |
|---|---|
| Les mesures départagent en faveur du retenu | l'argument le plus fort : deux voies concordent |
| Les mesures pencheraient pour l'alternative | **on le dit** : « un examen visuel de confirmation est recommandé » |
| Conditions communes aux deux | « le départage repose uniquement sur l'aspect des lésions » |
| Aucune condition réunie | idem, formulé différemment |

> Les confondre sous une formule unique donnerait une phrase toujours vraie et jamais
> informative. Le second cas est celui qui compte le plus : **le taire serait malhonnête**.

**Chaîne image uniquement** — la chaîne capteur n'a pas d'alternatives à départager.

Sur `/explain`, les probabilités du modèle **ne sont pas conservées** : la comparaison est
reconstruite depuis les seules conditions mesurées, exactement reproductibles depuis le relevé
enregistré. C'est la même règle que celle posée en tête de `DiagnosisExplainer`.

### 4.4 `DiagnosisExplainer` — rien n'est recalculé

Tout provient des **colonnes de traçabilité** des recommandations (`source_rule_id`,
`measure_field`, `observed_value`, `threshold_value`).

> Recalculer donnerait la justification d'aujourd'hui, pas celle du conseil tel qu'il a été
> émis — et les deux divergeraient dès qu'un seuil agronomique serait modifié. C'est aussi la
> raison pour laquelle `IrrigationAdapter` **préserve** ces colonnes en reformulant le texte.

---

## 5. Les sept moteurs de `knowledge`

`KnowledgeServiceImpl` est la façade dont dépend `diagnosis`. Les règles supportent le joker
`'*'` (valable quelle que soit la culture).

| # | Moteur | Rôle |
|:---:|---|---|
| 1 | **`RiskEngine`** | Fraction pondérée des conditions d'apparition réunies, **à partir des seules mesures**. Alerte précoce, indépendante du modèle vision. Opérateurs `> < >= <= == BETWEEN` ; condition à mesure manquante **ignorée**. Niveau : ≥0,85 `ELEVE`, ≥0,60 `MODERE`, sinon `FAIBLE` |
| 2 | **`AgronomicEngine`** | Compare aux plages de `CropRequirement`, affinées **par stade** (`CropRequirementResolver`). Sévérité = `(écart/amplitude)·(1−tolérance)`, clampée 0–1. Calcule aussi VPD et déséquilibre NPK |
| 3 | **`TrendAnalyzer`** | Régression des **moindres carrés** sur une fenêtre récente, projette le franchissement de seuil (horizon 12 h) |
| 4 | **`CorrelationEngine`** | Chaîne image : filtre les `CorrelationRule` par la valeur mesurée |
| 5 | **`WeatherEngine`** | **V20** — prévisions Open-Meteo : différer l'irrigation, refuser un traitement avant la pluie, alerte préventive sur humidité annoncée |
| 6 | **`ConflictArbitrator`** | Réconcilie les conseils contradictoires : **ajoute** une synthèse, ne retire **jamais** |
| 7 | **`IrrigationAdapter`** | **V16** — reformule les conseils inapplicables sur la parcelle |

### 5.1 Pourquoi `AgronomicEngine` normalise par l'amplitude

L'écart est rapporté à l'**amplitude de la plage optimale**, pas au seuil lui-même : une
culture à exigences étroites voit ses dépassements pesés plus lourdement qu'une culture
tolérante — ce qui est agronomiquement juste. À défaut d'amplitude (seuils nutritifs, sans
maximum), on retombe sur le seuil.

### 5.2 Pourquoi `TrendAnalyzer` contrôle le R²

Une série erratique produit **toujours** une pente : la régression n'échoue jamais. Sans
contrôle de la qualité d'ajustement, elle donnait lieu à une projection présentée comme fiable.

> Annoncer un stress hydrique dans quatre heures sur la foi du bruit de mesure fait perdre la
> confiance de l'exploitant plus vite que de ne rien annoncer.

### 5.3 Pourquoi `ConflictArbitrator` n'enlève rien

« Réduire l'humidité pour contenir une maladie foliaire » et « irriguer pour lever un stress
hydrique » paraissent contradictoires. L'une vise l'air, l'autre le sol : **la contradiction
n'est qu'apparente**. Le moteur ajoute la synthèse qu'un agronome formulerait, qui dit comment
appliquer les deux ensemble.

### 5.4 `IrrigationAdapter` : reformuler, pas supprimer

Sur une parcelle `PLUVIAL`, un conseil `STRESS_HYDRIQUE` demandant d'irriguer est **complété**
d'une alternative réalisable (paillage, ombrage, binage).

> **Effacer le conseil ferait disparaître le problème avec lui**, ce qui est pire que de
> proposer une action irréalisable. Le constat reste vrai : le sol manque d'eau. Seule la
> réponse change.

Deux garde-fous :

- Le rattachement se fait sur la **catégorie ET le libellé** : la même catégorie porte aussi
  des conseils déjà compatibles avec le pluvial, qu'il serait absurde de réécrire.
- **`null` n'est pas `PLUVIAL`.** `IrrigationType.cannotIrrigate` ne répond vrai que sur une
  valeur explicitement pluviale : en l'absence d'information, mieux vaut laisser le conseil
  d'origine que le réécrire sur une hypothèse.

### 5.5 `WeatherEngine` — dégradation propre obligatoire

Rend une **liste vide** si : la météo est désactivée, la parcelle n'a pas de coordonnées, ou le
fournisseur ne répond pas.

> C'est la règle appliquée au microservice d'inférence, et elle vaut ici : **une capacité
> indisponible retire une capacité, elle ne casse rien.**

Le conseil « différer l'irrigation » emprunte la catégorie `PLUIE_ANNONCEE`, que
`ConflictArbitrator` sait concilier avec `STRESS_HYDRIQUE` — sans traitement particulier.
Le risque projeté emprunte `RISQUE_MALADIE`, la même que `RiskEngine`, pour que la
déduplication et l'arbitrage les traitent comme relevant du même domaine.

### 5.6 Cache des tables de connaissance

Redis partagé, Caffeine local devant (`TwoLevelCache`).

- TTL partagé **30 min**, local **5 min** — plus court, car rien ne peut vider le cache local
  depuis l'extérieur : cette durée borne l'écart possible entre deux instances.
- **Le TTL n'est pas un confort.** Sans lui, une modification faite directement en base — au
  `psql` ou au pgAdmin, ce qui est le cas courant pour ajuster un seuil — ne serait **jamais
  vue** : l'éviction ne se déclenche que sur les écritures passant par l'API. L'administrateur
  verrait sa modification enregistrée sans effet sur les diagnostics.
- Les clés contiennent le nom de la culture, qui vient de la requête : `knowledgeMaxEntries`
  borne le cache local, sans quoi interroger avec des cultures arbitraires le ferait grossir
  indéfiniment.

---

## 6. Les calculs automatiques

C'est la ligne directrice des lots V16 → V22 : **le système calcule et décide davantage, il ne
demande pas davantage de saisie.** Chaque champ ajouté est l'entrée d'un calcul, pas un
formulaire de plus.

### 6.1 `GrowthStageResolver` — le stade se déduit

**Le défaut corrigé.** `growth_stage` est une colonne saisie à la main. Personne ne revient la
modifier : une tomate plantée en mars reste « LEVEE » jusqu'à la récolte. Or
`CropRequirementResolver` infléchit les seuils agronomiques selon ce stade.

> Le système raisonnait sur un stade faux — **avec exactement la même assurance** que sur un
> stade juste, ce qui est le pire des deux mondes.

**Proportion du cycle, pas jours fixes.** Une variété précoce et une tardive traversent les
mêmes phases dans les mêmes proportions. `cycleDurationDays` ajuste tout d'un coup, sans table
de correspondance par variété.

| Culture | Phases (fraction du cycle) | Défaut |
|---|---|:---:|
| Tomate | LEVEE 0,10 · CROISSANCE 0,40 · FLORAISON 0,60 · FRUCTIFICATION 0,85 · MATURATION 1,00 | 120 j |
| Manioc | LEVEE 0,08 · CROISSANCE 0,35 · TUBERISATION 0,75 · MATURATION 1,00 | 330 j |

Ces séquences reprennent **exactement** celles semées par V10 dans `crop_stage_requirement` :
s'en écarter produirait un stade sans seuils associés, silencieusement ignoré.

**Pas d'ordonnanceur.** Le recalcul a lieu dans `ContextResolver.resolve`, là où le stade est
consommé. Un stade recalculé que personne ne lit n'a aucune valeur ; ce qui compte est qu'il
soit juste au moment où le moteur s'en sert.

`stageFor` rend `null` pour « je ne sais pas » (culture inconnue, pas de date de plantation) —
jamais « aucun stade ». L'appelant conserve alors la valeur enregistrée.

### 6.2 `EffectAnalyzer` — le système évalue ses propres conseils

Compare les **48 h avant** et les **48 h après** une intervention.

**Pourquoi 48 h** : assez pour lisser le cycle jour/nuit — sans quoi une irrigation faite le
matin serait comparée à un après-midi, et l'écart mesurerait la météo plutôt que l'action — et
assez court pour que l'effet de l'intervention domine encore.

**Le sens de l'amélioration dépend du type** : une irrigation doit faire *monter* l'humidité.
`InterventionType` porte `targetMeasure` et `expectsIncrease`.

**`TRAITEMENT` n'a pas de mesure cible.** Produire un écart d'humidité pour un fongicide
donnerait un chiffre sans rapport, **avec l'apparence de la rigueur** — pire que de ne rien
dire. Le verdict est alors `INDETERMINE`, et l'analyse porte sur le nombre de diagnostics
anormaux.

**Seuil de bruit** : en deçà de 5 % d'écart relatif, `AUCUN_CHANGEMENT`. Une sonde d'humidité
varie de quelques pour cent sans que rien ne se soit passé ; qualifier cela d'amélioration
décrédibiliserait tous les autres verdicts.

**`limitation` est toujours renseigné.** Une comparaison avant/après n'établit pas une
causalité : une pluie survenue dans la même fenêtre produirait le même chiffre.

### 6.3 `MarginCalculator` — tout est recalculé, rien n'est stocké

> Un total mis en cache diverge dès la première correction de saisie, et personne ne sait plus
> lequel des deux chiffres croire.

- Produit brut = `Σ quantité × prix unitaire`. Une récolte sans prix est comptée pour zéro
  **et signalée dans `missingData`** : l'ignorer silencieusement donnerait une marge fausse que
  rien ne distinguerait d'une marge juste.
- Charges agrégées **en base** par type (`aggregateCostByType`).
- `costRatio` est `null` si le produit est nul : diviser par zéro afficherait « charges à
  l'infini » pour une parcelle simplement pas encore récoltée.
- `marginPerHectare` et `yieldPerHectare` sont `null` sans `plantedArea` — et l'absence est
  expliquée dans `missingData`.

**`limitation` est une constante, toujours renvoyée** : le rapprochement « conseils suivis /
rendement » est descriptif, jamais causal. Le sol, la variété, la météo et l'attention portée
à la parcelle varient ensemble, et une exploitation ne fournit pas l'échantillon qui
permettrait de les démêler.

> **Le dire est plus solide que de le taire** — un jury le demanderait de toute façon.

### 6.4 `TimelineComposer` — fusion en mémoire, assumée

Sept sources, tri en mémoire. Une union SQL sur sept tables aux colonnes différentes serait
illisible et fragile ; sur une fenêtre bornée, avec chaque source plafonnée à 200 lignes, le
volume reste de l'ordre de la centaine.

**Relevés marquants seulement** (anomalies). Une parcelle instrumentée produit un relevé toutes
les quelques minutes : les verser tous rendrait la chronologie illisible et noierait ce qu'on y
cherche. Un relevé nominal n'est pas un événement — c'est le fonctionnement normal, et il a
déjà sa vue (la série agrégée).

**Pagination après la fusion**, pas par source : « les vingt derniers événements » ne veut rien
dire si chaque source rend ses vingt derniers séparément.

**Les changements de stade sont reconstitués** depuis la date de plantation — ils ne sont
enregistrés nulle part, mais c'est une fonction déterministe du temps. Un stade est daté à
minuit, ce qui le place **avant** les événements horodatés du même jour : c'est l'ordre juste,
le stade étant le contexte dans lequel ils surviennent.

### 6.5 `PlotCodeGenerator` — séquence, pas comptage

`CodeComposer.refWithYear("PARC", …, nextval('plot_code_seq'))`.

Une **séquence PostgreSQL** et non un `count()` : deux créations simultanées liraient le même
total et produiraient le même code, que l'index unique rejetterait ensuite.

Rend `null` après 5 tentatives infructueuses plutôt que d'échouer : une parcelle sans code
reste parfaitement utilisable, et refuser sa création pour un détail de présentation serait
disproportionné.

---

## 7. Alertes et notifications

### 7.1 `AlertServiceImpl` — la signature porte la situation

**Déduplication par signature**, pas par relevé : une alerte porte sur une *situation*
(`<source>:<culture>:<résultat>`), et tant qu'une alerte ouverte porte la même empreinte sur la
même parcelle, aucune nouvelle n'est créée.

**Réconciliation automatique** :

- `closeStaleAlerts` — la situation observée sur cette voie a changé : ce qu'on y signalait n'a
  plus lieu d'être → `AUTO_SITUATION_REMPLACEE`.
- Plus rien d'urgent → `AUTO_SITUATION_NORMALISEE`.
- Situation reconstatée sans acquittement → `escalationCount++`, et au-delà du seuil (3),
  **l'alerte monte d'un niveau**. Une alerte ignorée qui reste au même rang finit par se
  confondre avec le bruit de fond.

**Alertes techniques** (V17) : signature `TECHNIQUE:<technicalId>`, niveau `ELEVEE` et non
`CRITIQUE` — la parcelle n'est pas en danger, c'est la surveillance qui l'est ; réserver le
niveau critique aux situations qui menacent la culture préserve son sens.

Elles **se referment** quand le verdict revient à `SAINE` : sans cela, une sonde remplacée
laisserait un signalement que plus rien ne justifie, et le technicien apprendrait à ignorer une
liste qui ne se vide jamais.

### 7.2 L'outbox — le maximum que l'infrastructure permet

Le projet n'a **ni ordonnanceur, ni file de messages, ni exécution asynchrone**. L'outbox
(V15) réduit le motif à ce que cela autorise :

```
1. l'intention d'envoi est écrite DANS la transaction de l'alerte
      → rien n'est notifié pour une alerte dont la transaction échouerait
2. la tentative a lieu APRÈS le commit (TransactionSynchronization.afterCommit)
      → un canal muet laisse la ligne en attente au lieu de faire échouer le diagnostic
```

> Perdre un diagnostic pour un serveur de courriel muet serait absurde.

Reprise : `dispatchPending(batchSize)`, borné, déclenché après l'ingestion et exposé sur
`POST /admin/notifications/dispatch`.

### 7.3 `RecipientResolver` — le manque que cela comble

`notification_outbox.recipient` existait depuis V15 et **n'avait jamais été renseigné**. Le seul
canal implémenté écrivait dans les journaux et n'avait besoin de personne — ce qui a masqué le
fait que le système ne savait pas à qui il s'adressait.

- **L'affecté prime sur le propriétaire** : si quelqu'un s'est vu confier le traitement, c'est
  lui qu'il faut prévenir, pas le titulaire du titre foncier.
- **Seuil personnel** > seuil global. Notifier tout le monde de la même façon revient à ne
  notifier personne : celui qu'on réveille à 3 h pour une situation qui pouvait attendre coupe
  ses notifications, et n'apprendra pas non plus la critique du lendemain.
- **Heures de silence** — plage pouvant enjamber minuit, traitée explicitement.
  **Une alerte `CRITIQUE` passe outre** : la reporter la viderait de son sens.
- **Regroupement** — `plotId:level:tranche-de-10-min`. Le niveau fait partie de l'empreinte
  délibérément : réunir une alerte moyenne et une critique ferait passer la seconde pour une
  ligne parmi d'autres.
- **Pas d'adresse ⇒ rien n'est enfilé.** Une ligne sans destinataire échouerait à chaque
  reprise sans jamais aboutir ; mieux vaut le dire une fois dans les journaux.

### 7.4 `HttpSmsChannel` — pourquoi aucun SDK d'opérateur

Africa's Talking, Twilio et les passerelles locales exposent toutes la même chose : une URL, un
corps portant un numéro et un texte, un en-tête d'autorisation.

> Un client par opérateur reviendrait à réécrire trois fois le même appel HTTP — et à devoir
> **livrer du code pour changer de fournisseur**, au moment précis où l'ancien ne marche plus.

Ici, changer d'opérateur est une modification du fichier de configuration.

**`isAvailable()` répond faux si l'URL est vide** : `NotificationService` ne lui enfile rien,
aucun échec n'est compté, rien à nettoyer le jour où l'on branche une vraie passerelle. Le
système est démontrable sans compte opérateur.

Détails qui comptent : échappement JSON du message (accents, guillemets, retours à la ligne),
normalisation du numéro en forme internationale (`06 123 45 67` → `+24261234567`), troncature
à 320 caractères (un SMS long est refacturé et arrive parfois découpé dans le désordre).

**Un non-2xx n'est pas réessayé** : c'est une décision de la passerelle, pas un incident de
transport. La répéter ne ferait que consommer du crédit.

---

## 8. Cloisonnement et organisation

### 8.1 `AccessGuard` — le pari qui a tenu

**Le défaut corrigé.** Rien ne rattachait une requête à un propriétaire :
`GET /plots?userId=42` renvoyait les parcelles de l'utilisateur 42 à quiconque le demandait.

**Le pari posé à l'écriture.** Le cloisonnement pourrait s'écrire dans chaque service ; il
serait alors à réécrire partout le jour où la notion de propriétaire s'élargit.

> **Ce jour est arrivé avec V22, et le pari a tenu** : l'élargissement aux exploitations tient
> dans cette seule classe. Parce que **tous** les domaines — cultures, boîtiers, relevés,
> diagnostics, observations, interventions, récoltes — passent par `PlotService.require(id)`,
> qui l'appelle.

### 8.2 La règle appliquée

```
privilégié (ADMIN, SUPER_ADMIN)        → aucune restriction
propriétaire direct de la parcelle     → accès complet, INCONDITIONNEL
membre de l'exploitation               → modulé par le rôle (AccessScope)
sinon                                  → 403
```

### 8.3 L'organisation n'est jamais bloquante

C'est la propriété la plus importante de V22, et elle est intentionnelle :

| Garantie | Comment elle est tenue |
|---|---|
| Une parcelle **sans** exploitation se comporte comme avant | `roleOn()` court-circuite sur `farm == null` — **aucune requête supplémentaire n'est émise** |
| Une appartenance **ajoute** un accès, n'en retire aucun | `hasAnyAccess()` = propriétaire direct **OU** membre |
| Une exploitation mal configurée n'enferme personne dehors | corollaire du précédent |
| Archiver une exploitation ne prive personne | les parcelles redeviennent indépendantes ; leur propriétaire ne l'a jamais tenu de l'exploitation |
| Une exploitation naît consultable | le propriétaire est ajouté comme `PROPRIETAIRE` à la création |

**Nullabilité en cascade** : `plots.farm_id` nullable, `farms.cooperative_id` nullable. Aucune
donnée existante n'est invalidée.

### 8.4 `AccessScope` — le rôle module le domaine

| Rôle | `AGRONOMIQUE` | `ECONOMIQUE` | `TECHNIQUE` |
|---|:---:|:---:|:---:|
| `PROPRIETAIRE` | ✅ | ✅ | ✅ |
| `CONSEILLER` | ✅ | ❌ | ✅ |
| `OUVRIER` | ✅ | ❌ | ✅ |
| `TECHNICIEN` | ❌ | ❌ | ✅ |

> Un technicien venu changer une sonde n'a aucune raison de voir les marges. Confondre les deux
> reviendrait à ouvrir la comptabilité à quiconque intervient sur un boîtier — ce qui, **dans un
> milieu où tout le monde se connaît, est un problème social avant d'être technique**.

Deux formes d'application :

- **`requireScope(plot, scope)`** — lève un 403 explicite. Employée sur
  `/plots/{id}/economics` : l'appelant connaît déjà la parcelle, il n'y a plus rien à lui
  cacher de son existence, et lui dire pourquoi lui évite de croire à une panne.
- **`canRead(plot, scope)`** — non levante. Employée sur `/overview/economics` : les parcelles
  interdites sont **écartées** de la comparaison plutôt que de la faire échouer.

**Un seul rôle par personne et par exploitation** : deux rôles simultanés rendraient
indécidable le niveau applicable, et « lequel l'emporte ? » n'a pas de bonne réponse.

### 8.5 Recherche élargie

`PlotRepository.search` accepte `hasFarmScope` + `farmIds`. Un `in ()` sur collection vide n'est
pas du SQL valide : un drapeau explicite et une valeur muette (`List.of(-1L)`) valent mieux
qu'un test de taille caché dans la requête.

---

## 9. Infrastructure transverse

### 9.1 Réponses et erreurs

`ApiResponse<T>`, `PaginatedResponse<T>` + `PageInfo`, `ApiError`.
`GlobalExceptionHandler` (`@RestControllerAdvice`) couvre une trentaine de cas.

Deux mappings à connaître :

- **`NoResourceFoundException → ENDPOINT_NOT_FOUND`** — « la route n'existe pas », à distinguer
  de `RESOURCE_NOT_FOUND` (« la route existe, l'entité non »). Cette distinction fait gagner
  des heures de débogage.
- **`IllegalArgumentException → 400 BAD_REQUEST`** avec le message métier — les services
  knowledge lèvent cette exception sur violation de règle (seuils incohérents, doublon,
  culture inconnue).

**Exceptions maison** sous `exception/customs/`, toutes `extends BaseException` et porteuses
d'un `errorCode`. Vocabulaire dans `utils/error/ErrorCode`.

### 9.2 `TimeRange` — pourquoi des bornes ouvertes

Écrire `(:from is null or col >= :from)` paraissait naturel mais posait deux problèmes :

1. PostgreSQL ne peut pas inférer le type d'un paramètre qui n'apparaît que dans une
   comparaison à `NULL` → `could not determine data type of parameter`.
2. Même typé, ce prédicat **interdit au planificateur d'utiliser l'index** sur la colonne de
   date — précisément celui qui compte sur une série temporelle.

`TimeRange.from(null)` → `Instant.EPOCH`, `TimeRange.to(null)` → `9999-12-31`. La clause devient
un simple encadrement, typé et indexable.

### 9.3 `SemanticSort` — trier par urgence, pas par alphabet

Trier sur les colonnes en clair donnait un ordre alphabétique : `?sort=priority,asc` renvoyait
`BASSE, BASSE, …, HAUTE` — **l'inverse de l'urgence**, et la première page n'était pas la plus
urgente.

`SemanticSort.rankExpression("a.level", "CRITIQUE", "ELEVEE", "MOYENNE")` produit une expression
`CASE` que `rewrite(pageable, ranks)` substitue à la propriété demandée.

Appliqué sur : `alerts.level`, `alerts.status`, `recommendations.priority`,
`recommendations.status`.

### 9.4 Audit (AOP)

`@Audited(module, action, ressource?)` + `AspectAudit` (`@Around`). Capture acteur, URI,
méthode, IP, user-agent, session, statut. Persiste dans `audit_log` (colonnes `jsonb`).

> ⚠️ `AuditServiceImpl.save` est annoté `@Async` **mais `@EnableAsync` est absent** :
> l'exécution est **synchrone** de fait. `AuditContext` (ThreadLocals `putMeta`/`setDiff`) est
> câblé mais **jamais appelé** → metadata et diff quasi vides.

### 9.5 Idempotence (AOP)

`@Idempotent(operation, requestBodyArgIndex, keyHeader, required)` + `IdempotencyAspect`.

Machine à états avec **verrou pessimiste** (`PESSIMISTIC_WRITE`) et transactions
`REQUIRES_NEW` séparées (claim / complete / fail). Hash SHA-256 du payload.
Rejeu d'un `COMPLETED` = la réponse stockée est renvoyée **sans réexécuter**. Même clé + payload
différent → `ConflictException`.

Sérialise via **Jackson 3** (`tools.jackson`).

### 9.6 Cache à deux niveaux

`TwoLevelCacheManager` : Redis partagé, Caffeine local devant.
**Bascule sur Caffeine dès qu'un appel Redis échoue** — une lenteur de Redis ne doit pas se
propager à l'ingestion. Timeouts courts (2 s).

`CacheInvalidationBroadcaster` propage les évictions entre instances.
`@EvictsKnowledgeCaches` marque les écritures qui doivent vider le cache.

### 9.7 `ConfigurationGuard`

Vérifie au démarrage la cohérence des réglages sensibles. En profil `prod`, une clé d'ingestion
vide ou l'auto-admin activé **empêchent le démarrage**.

---

## 10. Schéma de base

Migrations dans `src/main/resources/db/migration/`. Conventions d'identifiant :
`@IdGeneration` (Snowflake, fourni par l'application) → `BIGINT` ;
`@GeneratedValue(IDENTITY)` → `BIGSERIAL` (uniquement `role` et `permission`).

| Migration | Contenu |
|---|---|
| **V1** | sécurité, audit, idempotence · extension `pg_trgm` + recherche floue |
| **V2** | métier : plots, crops, iot_devices, sensors, sensor_readings, observations, knowledge, ai_models, diagnostics, recommendations, alerts |
| **V3** | seed : 3 modèles IA, seuils tomate/manioc, 7 règles, 11 maladies, 5 corrélations |
| **V4** | `ai_models.crop_name` |
| **V5** | index FK et composites séries temporelles |
| **V6** | `disease_risk_condition` + seed |
| **V7** | `recommendation_arbitration` + seed |
| **V8** | cycle de vie des alertes (signature, acquittement, résolution) |
| **V9** | traçabilité des recommandations |
| **V10** | `crop_stage_requirement` — seuils **par stade** |
| **V11** | contraintes `CHECK` sur tout le vocabulaire fermé + bornes de plausibilité |
| **V12** | verrous optimistes (`@Version`) |
| **V13** | complétion du cycle de vie des alertes (escalade, motif de résolution) |
| **V14** | retour d'usage sur recommandation |
| **V15** | `notification_outbox` |
| **V16** | **champs métier & géolocalisation** — voir ci-dessous |
| **V17** | **santé des sondes** — `iot_devices.sensor_health`, `alerts.category` |
| **V18** | **acheminement** — `users.phone`, `notification_preference`, `group_key`, `deferred_until` |
| **V19** | **`interventions`** |
| **V20** | **`weather_forecast`** + `chk_recommendations_type` étendue à `METEO` |
| **V21** | **`harvests`** |
| **V22** | **organisation** — `cooperatives`, `farms`, `farm_membership`, `plots.farm_id` |

### 10.1 Décisions de schéma à connaître

**V16 — `temperature` n'est pas renommée.** Elle désigne l'air ; le contrat d'API et la feature
map du microservice en dépendent. `temperature_sol` s'ajoute à côté, et deux `COMMENT ON COLUMN`
lèvent l'ambiguïté en base.

**V16 — index partiel sur `plot_code`.** `CREATE UNIQUE INDEX … WHERE plot_code IS NOT NULL` :
les parcelles héritées n'ont pas de code, et `NULL` ne doit pas entrer en collision avec `NULL`.

**V18 — l'unicité `(alert_id, channel)` de V15 est supprimée.** Elle datait d'un temps où une
alerte donnait au plus un envoi par canal. Le regroupement change cela, et `group_key`
déduplique mieux — elle couvre plusieurs alertes, là où l'index ne couvrait qu'une.

**V19 — `interventions.recommendation_id` est nullable, et c'est essentiel.** Les exploitants
agissent aussi de leur propre chef, et ce sont précisément ces actions-là qui expliquent des
évolutions que le système ne comprendrait pas autrement : une irrigation non consignée fait
remonter l'humidité, et le moteur conclurait à une pluie qui n'a pas eu lieu.

**V21 — `harvests.crop_id` est obligatoire**, contrairement à `interventions.crop_id`. Une
récolte qui ne se rattache à aucune campagne n'entre dans aucun calcul de marge : l'accepter
reviendrait à enregistrer une donnée sans usage.

**V21 — prix unitaire, pas montant total.** Le total se recalcule ; un prix unitaire perdu ne
se retrouve pas, et c'est lui qui permet de comparer deux campagnes de volumes différents.

**V22 — tout est nullable.** Voir §8.3.

> ⚠️ **Les valeurs agronomiques semées (V3, V6, V7, V10) sont indicatives.** Le commentaire de
> V10 le dit explicitement. Elles doivent être validées par des sources agronomiques
> congolaises avant exploitation réelle.

---

## 11. Configuration

Structure du fichier = structure de `BilangaProperties`. Toute clé qui ne se lie pas est
**ignorée en silence** — d'où la règle §2.5.

```yaml
bilanga:
  ml:            base-url, timeouts, max-attempts, retry-backoff
  ingest:        device-key, require-device-key
  diagnosis:     min-interval-minutes, throttle, image.max-size-bytes, threshold.*
  confidence:    high 0.85, low 0.60
  risk:          min-score 0.60, high-score 0.85
  agronomic:     min-severity 0.05
  trend:         window-hours, min-points, horizon-hours, min-r-squared, …
  sensor-health: enabled, stuck-readings 6, window-hours 12, drift-tolerance 0.25,
                 decoupling-tolerance 0.60                                     # V17
  weather:       enabled, base-url, cache-ttl-minutes 60, horizon-hours 48,
                 rain-threshold-mm 5, treatment-rain-window-hours 6,
                 high-humidity-threshold 85                                    # V20
  overview:      device-silence-minutes 15
  alert:         escalation-threshold 3
  notification:  enabled, min-level, max-attempts, dispatch-batch-size,
                 grouping-window-minutes 10, sms.*                             # V18
  cache:         knowledge-ttl-minutes 30, local-ttl-minutes 5, knowledge-max-entries 500

app:
  time-zone: Africa/Lagos            # ne sert QU'aux heures de silence
  security:
    jwt.*, token-hash.*, failed-login.max-attempts 5
    auto-admin.enabled                # ⚠️ true en dev
    ownership.enabled                 # ⚠️ false en dev
    rate-limit.*
```

### 11.1 Les trois interrupteurs à connaître

| Clé | Défaut | Effet |
|---|---|---|
| `bilanga.notification.sms.url` | **vide** | canal SMS **indisponible** — rien n'est enfilé, aucun échec compté |
| `bilanga.weather.enabled` | `true` | à `false`, `WeatherEngine` rend une liste vide |
| `bilanga.sensor-health.enabled` | `true` | à `false`, verdict `SAINE` systématique |

Ces trois-là suivent le même principe : **désactivé, le système fonctionne, il fait juste
moins.** Aucun ne peut casser quoi que ce soit en étant absent.

---

## 12. Ajouter quelque chose : réflexes

### 12.1 Avant d'écrire

**Ouvrez un équivalent existant et copiez ses conventions.** Le meilleur modèle « CRUD scopé
par parcelle » est `ObservationController` + son service ; `intervention/` et `harvest/` en
sont deux répliques récentes.

### 12.2 Une nouvelle ressource métier

```
1. migration Vn__*.sql          contraintes CHECK, index sur (plot_id, date DESC)
2. entité                       @IdGeneration, @Version, @PrePersist
3. repository                   search() à critères facultatifs, bornes TimeRange
4. dto/request + dto/response   énumérations typées en entrée, …Label en sortie
5. service/support/…Mapper      calculs dérivés ICI, pas côté client
6. service interface + impl     accès via PlotService.require(id) → AccessGuard
7. controller                   ApiResponse, PaginatedResponse, @PageableDefault
8. mvn compile                  puis vérifier entité ↔ migration à la relecture
```

### 12.3 Un nouveau moteur de connaissance

1. Classe dans `knowledge/service/support`, sans état, sans transaction.
2. Émet des `RecommendationItem` avec **la traçabilité renseignée** (`measureField`,
   `observedValue`, `thresholdValue`) — sans quoi `DiagnosisExplainer` ne saura pas justifier.
3. Réutilise une **catégorie existante** si le domaine est le même : c'est ce qui permet à
   `ConflictArbitrator` et à la déduplication de faire leur travail.
4. Nouveau `type` ⇒ **étendre `chk_recommendations_type`** dans une migration. Sans cela, la
   première recommandation ferait échouer son insertion — et, survenant au cœur du diagnostic,
   ferait perdre le diagnostic entier.
5. Exposer via `KnowledgeService`, appeler dans les **quatre** entrées de `DiagnosisServiceImpl`.
6. **Prévoir le silence** : liste vide si la source est indisponible.

### 12.4 Un nouveau canal de notification

Une seule chose à faire : implémenter `NotificationChannel` (`name`, `isAvailable`, `send`).

`isAvailable()` doit répondre **faux tant que le canal n'est pas configuré** — c'est ce qui
évite d'accumuler des échecs pour un canal qui n'existe pas encore. Un échec lève simplement
une exception : **la reprise est la charge de l'expéditeur, pas du canal.**

### 12.5 Ce qu'il ne faut pas faire

| ❌ | Pourquoi |
|---|---|
| Coupler le backend au code des modèles IA | Rester en REST derrière `VisionClient`/`TabularClient` |
| Piloter l'entraînement depuis le backend | Le microservice est un système tiers. Exporter un jeu annoté, oui ; piloter, non |
| Réactiver le scaffolding fintech (RabbitMQ, Batch, Quartz, JobRunr) | Dépendances mortes, à **retirer**. Un `@Scheduled` suffirait le jour venu |
| Mettre en cache un total calculé | Il diverge dès la première correction de saisie |
| Lire un réglage par `@Value` | Voir §2.5 |
| Renvoyer une entité nue | Toujours un DTO |
| Éditer une migration appliquée | Flyway compare les empreintes |
| Ajouter un champ sans se demander s'il peut être calculé | C'est la ligne directrice de V16→V22 |

### 12.6 Vérifier

```bash
mvn -q compile                 # Java 25, Lombok, MapStruct
mvn -q -DskipTests package     # livrable complet
docker compose up -d postgres  # base de dev (port 55820)
```

> **Règle de travail sur ce dépôt : l'assistant ne lance pas l'application.** C'est
> l'utilisateur qui la fait tourner dans son IDE. `mvn compile` est la seule vérification
> automatisée.
>
> **Le vrai filet est `ddl-auto: validate` au démarrage** : une divergence entité ↔ migration
> empêche l'application de démarrer.

---

## 13. Dette et pièges connus

### 13.1 Ce qui n'existe pas, malgré les apparences

| Apparence | Réalité |
|---|---|
| `@Async` sur `AuditServiceImpl.save` | **`@EnableAsync` absent** → exécution synchrone |
| Dépendances RabbitMQ, Batch, Quartz, JobRunr, WebSocket, HATEOAS, Freemarker | **Aucun code Java** ne les utilise — scaffolding fintech « bokati » résiduel |
| Réglages MinIO, outbox/document workers, rate-limits mobile-money dans `.env` | idem |
| `AdminApiAuthorizationManager` mappe `BILLING`, `PAYMENT`, `KYC`, `DOCUMENTS` | Modules **inexistants** côté métier |
| `diagnosis/dto/response/SoilPrediction` et `VisionPrediction` | **Doublons morts** — utiliser ceux de `client/dto/response` |
| Code GeoIP MaxMind dans `UserSessionServiceImpl` | Commenté |
| Envoi d'e-mails | **Commenté** — les codes OTT et de réinitialisation reviennent dans la réponse API |

### 13.2 Sécurité — posture actuelle

> ⚠️ **Permissive, et à durcir avant toute démonstration.**

| Point | État |
|---|---|
| `SecurityConfig` : `permitAll("/**")` **avant** `adminApiAuthorizationManager` | l'autorisation par URL est court-circuitée ; seul `@PreAuthorize` protège encore |
| `JWTFilter` **auto-admin** (`auto-admin.enabled=true`) | une requête **sans jeton** est authentifiée comme `admin@bokati.com` |
| `ownership.enabled=false` | le cloisonnement d'`AccessGuard` est **inactif** |
| CORS `allowedOriginPatterns("*")` | avec `allowCredentials(false)` |
| Secret JWT par défaut codé en dur | dans `JWTService` ; secrets en clair dans `.env` |
| `/admin/provisioning/bootstrap-admin` | **aucune annotation d'autorisation** |
| Handlers 401/403 | construisent un corps **jamais écrit** |

**Demander confirmation avant de modifier** : `SecurityConfig`, `JWTFilter`, JWT/secrets,
logique de rôles/permissions, migrations de sécurité (V1).

### 13.3 Tests

**Un seul test** : `BilangaApplicationTests.contextLoads()`, `@SpringBootTest`, qui exige un
PostgreSQL joignable. Ni H2, ni Testcontainers.

> Les sections « H2 / tests unitaires / MockMvc / `@MockBean AiClient` » des anciens documents
> sont **fictives**. Écrire de vrais tests est un chantier ouvert, pas un acquis.

**Ce qui se teste sans base** — les classes de `service/support` sont sans état et sans
transaction, donc directement instanciables : `GrowthStageResolver`, `PlausibilityChecker`,
`IrrigationAdapter`, `ConfidenceEvaluator`, `DerivedIndicators`, `CsvSeriesWriter`,
`ComparativeExplainer`. **C'est par là qu'il faut commencer.**

### 13.4 Points ouverts

| Point | Détail |
|---|---|
| `GeneratorOfId` fait des `System.out.println` | bruit à chaque identifiant généré — à nettoyer |
| `.env` incohérent | `SPRING_DATASOURCE_URL` (5434) ≠ `application.yaml` (55820) ≠ `compose.yaml` ; deux mots de passe (`bilanga25` / `bokati25`) |
| Swagger inaccessible | `/v3/api-docs` et `/swagger-ui/**` répondent 401 |
| Migrations V16→V22 **non exécutées** | validées à la compilation seulement ; le premier démarrage sur un Postgres réel est la vérification qui manque |
| `AlertService.findOpen()` / `findByPlot()` | plus utilisés depuis l'ajout de `search()` — candidats au retrait |
| Purge de `weather_forecast` | déclenchée au rafraîchissement seulement : une parcelle qui cesse d'être interrogée conserve ses prévisions périmées |

### 13.5 Documents à ne pas croire sur parole

`PROJECT_CONTEXT.md` et les anciennes versions de `CLAUDE.md` décrivaient une **cible**, pas le
code. Écarts constatés : « Java 17 » (c'est Java 25), « 18 tables » (~40), « base H2 »
(inexistante), « tests unitaires et d'intégration » (aucun), « `schema_complet.sql` »
(inexistant), « notifications SMS/Email/WhatsApp fonctionnelles » (envois commentés).

> **La source de vérité est, dans cet ordre : le code, les migrations Flyway,
> `ARCHITECTURE.md`, puis ce document.**
