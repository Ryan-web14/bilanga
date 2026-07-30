# API Bilanga — Documentation frontend

> **Public** : Rolle (React) et toute personne qui consomme l'API.
> **Établie le 2026-07-29** depuis le code, après les migrations **V16 → V22**.
> Remplace la version du matin, qui précédait ces neuf lots. Deux avertissements qu'elle
> portait sont **désormais faux** — voir §0.
>
> Vue interne (schéma, moteurs, orchestration, dette) : **`API_BACKEND.md`**.

---

## Sommaire

| § | Sujet |
|---|---|
| [0](#0-ce-qui-a-changé-depuis-la-version-précédente) | Ce qui a changé |
| [1](#1-les-six-règles-à-connaître-avant-tout) | Les six règles |
| [2](#2-authentification) | Authentification |
| [3](#3-vocabulaire-du-domaine) | Vocabulaire |
| [4](#4-codes-derreur) | Codes d'erreur |
| [5](#5-parcelles-et-cultures) | Parcelles, cultures, **calendrier prévisionnel** |
| [6](#6-boîtiers-capteurs-relevés-observations) | IoT |
| [7](#7-ingestion--pour-le-firmware) | Ingestion (firmware) |
| [8](#8-diagnostic-conseils-alertes) | Diagnostic, conseils, alertes, **rejeu** |
| [9](#9-les-vues-composées) | **Vues composées** — history, timeline, economics, CSV |
| [10](#10-journal-des-actions) | Interventions, récoltes |
| [11](#11-notifications) | Notifications, **langues** |
| [12](#12-base-de-connaissance) | Base de connaissance |
| [13](#13-organisation--exploitation-et-coopérative) | Organisation |
| [14](#14-administration-système) | Administration |
| [15](#15-idempotence) | Idempotence |
| [16](#16-parcours-décran) | Parcours d'écran |
| [17](#17-état-actuel-du-backend) | État actuel |
| [18](#18-les-dix-pièges) | Les dix pièges |

---

## 0. Ce qui a changé depuis la version précédente

> ## 📗 Document complémentaire — `API_FRONTEND_CYCLES.md`
>
> Les **cycles de culture** et le **diagnostic à un instant donné** font l'objet d'un
> document séparé : **`docs/API_FRONTEND_CYCLES.md`**. Sept routes neuves y sont
> décrites, ainsi qu'**un changement de comportement sur `PUT /crops/{id}`** qui peut
> concerner un client existant.
>
> | Route | Sujet |
> |---|---|
> | `GET /diagnosis/at` | ce que le système voyait et concluait à un instant donné |
> | `POST /crops/{id}/close` | clôture riche : date réelle, motif, bilan figé |
> | `GET /crops/{id}/closure` | bilan arrêté **et** vivant, avec leur écart |
> | `GET /crops/{id}/journal` | journal des révisions d'une campagne |
> | `GET /plots/{id}/crop-journal` | idem, toutes campagnes de la parcelle |
> | `GET /crops/{id}/calendar` | calendrier prévisionnel (aussi décrit en §5.3 ici) |
>
> 🔴 **`PUT /crops/{id}` n'efface plus les champs omis.** Il les écrasait
> inconditionnellement : un client qui n'envoyait que la variété effaçait la surface
> plantée — et rendait le bilan économique incomparable des semaines plus tard. Effacer
> se demande désormais par `clearFields`. Voir §0 du document complémentaire.

> ## 🔔 Mise à jour du 2026-07-30 — séance « lots 1 à 6 »
>
> **Neuf changements de contrat.** Aucun n'est cassant : ce sont des ajouts de
> champs, de routes et de valeurs de vocabulaire. Rien n'a été retiré ni renommé.
>
> | # | Changement | Où |
> |:-:|---|---|
> | 1 | **`recommendation.type`** gagne **`VOISINAGE`** — 8ᵉ moteur | §3, §8.2 |
> | 2 | `GET /crops/{id}/calendar` — **calendrier prévisionnel** | §5.3 |
> | 3 | `GET /diagnosis/{id}/replay` — **rejeu** d'un diagnostic | §8.6 |
> | 4 | `/plots/{id}/timeline` gagne `truncated`, `perSourceLimit`, `truncatedTypes` | §9.2 |
> | 5 | **`estimatedCost` cesse d'être toujours `null`** sur les conseils | §8.3 |
> | 6 | `language` accepte **`ln` (lingala)** et **`kg` (kituba)** | §11.1 |
> | 7 | Préférences : `languageLabel`, `availableLanguages`, `languageScopeNote` | §11.1 |
> | 8 | `availableChannels` peut contenir **`EMAIL`** | §11.1 |
> | 9 | **401 et 403 portent enfin un corps** — ils étaient vides | §4 |
>
> **Le point 9 est celui qui peut casser un client existant.** Si votre gestion
> d'erreur suppose un corps vide sur 401/403 — parce que c'est ce qu'elle recevait —
> elle recevra désormais du JSON avec `errorCode`, `status`, `message` et `path`.
> C'est une correction : les deux gestionnaires construisaient bien un corps depuis
> l'origine et ne l'écrivaient **jamais**.
>
> **Le point 5 mérite un mot.** `estimatedCost` figurait au contrat et sortait
> systématiquement à `null` : aucune source ne le renseignait. La chaîne est
> désormais complète (règle → conseil), mais **aucun prix n'a été semé** — les
> valeurs restent nulles jusqu'à ce qu'un agronome en fournisse de sourcées. La
> différence est qu'elles sont maintenant *saisissables* par
> `PUT /knowledge/rules/{id}`. Continuez donc à traiter `null` comme « non
> renseigné », jamais comme « gratuit » : **zéro est une valeur licite et
> distincte**.
>
> **Ce qui n'a PAS changé et pourrait le laisser croire** : le durcissement de la
> sécurité est *écrit* mais **non activé**. Les routes métier répondent toujours
> sans jeton. Continuez à envoyer le `Bearer` (§17).

### Deux avertissements devenus faux

1. **« Ne triez pas sur `priority`, `level` ni `status` — le tri est alphabétique. »**
   ❌ **Plus vrai.** Un tri **sémantique** est en place (`SemanticSort`).
   `?sort=level,desc` sur `/alerts` classe bien `CRITIQUE → ELEVEE → MOYENNE`, et
   `?sort=priority,asc` sur `/recommendations` classe `HAUTE → MOYENNE → BASSE`.
   **Supprimez vos tris de contournement côté client.**

2. **« Aucune notification ne sort du serveur. »**
   ❌ **Plus vrai.** Un canal SMS existe (§11). Il reste inerte tant que la passerelle n'est
   pas configurée, mais l'infrastructure est complète : destinataires, préférences par
   utilisateur, heures de silence, regroupement.

### Ce qui est nouveau

| Nouveauté | Où |
|---|---|
| Géolocalisation des parcelles, `plotCode`, type d'irrigation | §5.1 |
| Cycle cultural, **stade recalculé automatiquement**, `daysToHarvest` | §5.2 |
| `temperatureSol`, `pluviometrie`, conductivité, signal | §6 |
| **Santé des sondes** (`sensorHealth`) et alertes **techniques** | §6, §8.5 |
| **Explication comparative** : « pourquoi pas l'autre maladie ? » | §8.2, §8.4 |
| **Conseils météo** — 6ᵉ moteur, `type: "METEO"` | §8.2 |
| **Chronologie unifiée** d'une parcelle | §9.2 |
| **Bilan économique** : marge, rendement, taux de suivi | §9.3 |
| **Export CSV** des séries | §9.4 |
| **Interventions** + mesure de leur effet | §10.1 |
| **Récoltes** | §10.2 |
| **Préférences de notification** | §11 |
| **Exploitation / coopérative** (facultatif) | §13 |
| Affectation et échéance sur les alertes | §8.5 |

---

## 1. Les six règles à connaître avant tout

### 1.1 Préfixe

Toutes les routes commencent par **`/sni/api/v1`**. Dans ce document, les chemins sont écrits
sans ce préfixe. Un reverse-proxy sur `/sni` ne doit **pas** l'absorber : c'est un mapping
applicatif, pas un context-path servlet. L'actuator fait exception (`/actuator/health`,
`/actuator/info`).

### 1.2 Toutes les réponses sont enveloppées

```json
{
  "success": true,
  "message": "Parcelle créée.",
  "errorCode": null,
  "errorDescription": null,
  "timestamp": "2026-07-29T10:12:44.512",
  "data": { }
}
```

`message` est destiné à l'affichage, `data` porte la charge utile. Écrivez **un seul**
dépaquetage, dans un client HTTP unique.

**Deux exceptions** : `/ingest/*` (§7) et `/plots/{id}/history.csv` (§9.4).

### 1.3 ⚠️ Les `Long` sont sérialisés en **chaînes**

```json
{ "id": "1234567890123456789", "plotId": "987654321", "totalElements": "42" }
```

**Pourquoi.** Les identifiants sont des Snowflake à 19 chiffres. `Number.MAX_SAFE_INTEGER`
vaut ~9·10¹⁵ : au-delà, JavaScript arrondit **en silence**. Un identifiant arrondi désigne
une ressource inexistante → 404 fantômes indébogables.

**Portée globale, compteurs compris** : `pageable.totalElements` est une chaîne.

**Exceptions** — quelques compteurs restent des **nombres**, parce qu'ils ne risquent rien et
qu'on les additionne : `ageHours`, `plotCount`, `memberCount`, `farmCount`.

**En entrée**, les deux formes passent : `"plotId": "42"` et `"plotId": 42`.

> Typez tous les identifiants en `string`. Ne les passez jamais dans `parseInt` ni `Number()`.

### 1.4 Pagination : attention au `data.data`

```jsonc
{
  "success": true,
  "data": {                       // ← enveloppe ApiResponse
    "data": [ /* … */ ],          // ← PaginatedResponse.data : LA liste
    "pageable": {
      "page": 0, "size": 20,
      "totalElements": "137",     // chaîne
      "totalPages": 7,
      "first": true, "last": false, "hasNext": true, "hasPrevious": false,
      "sort": { "sorted": true, "direction": "DESC", "properties": ["createdAt"] }
    }
  }
}
```

**Double imbrication** : `response.data.data`. Isolez-la dans **un seul** helper — si elle
apparaît à trente endroits, un renommage futur coûtera trente corrections.

Paramètres : `?page=0&size=20&sort=createdAt,desc`. **`size` plafonné à 100.**

**Le tri sémantique fonctionne** sur `level` et `status` (`/alerts`), `priority` et `status`
(`/recommendations`).

### 1.5 Formats de date

| Type | Format | Où |
|---|---|---|
| Instant | ISO-8601 **UTC** — `2026-07-29T08:15:00Z` | horodatages, `from`/`to` de la plupart des filtres |
| Date | `yyyy-MM-dd` | `plantingDate`, `harvestedAt`, `from`/`to` de `/economics` et `/harvests` |

Le fuseau applicatif est `Africa/Lagos` (UTC+1) — il ne sert qu'aux heures de silence des
notifications. **Tout ce qui sort de l'API est en UTC.**

### 1.6 Ne recalculez pas ce que le backend fournit

Sont déjà calculés et **rédigés en français** : tous les `…Label`, `summary`, `statement`,
`rationale`, `limitation`, `daysSincePlanting`, `daysToHarvest`, `cycleProgress`, `ageHours`,
`readingAgeMinutes`, `minutesSinceLastSeen`, `overallStatus`, `grossRevenue`,
`yieldPerHectare`, `quietHoursLabel`.

---

## 2. Authentification

### 2.1 Principe

```
POST /auth/login  { email, password }
   → { accessToken, refreshToken }

Requêtes suivantes :  Authorization: Bearer <accessToken>

Sur 401 :  POST /auth/refresh  { refreshToken }
   → NOUVEAUX { accessToken, refreshToken }
```

### 2.2 Routes

| Méthode | Route | Corps | Réponse |
|---|---|---|---|
| POST | `/auth/login` | `{ email, password }` | `{ accessToken, refreshToken }` |
| POST | `/auth/refresh` | `{ refreshToken }` | idem, **tokens rotés** |
| POST | `/auth/logout` | en-tête `Authorization` | — |
| GET | `/auth/me` | — | `{ userId, email, accountEnabled, authorities[] }` |
| POST | `/auth/ott/request` | `{ email }` | code 6 chiffres + `verificationToken` |
| POST | `/auth/ott/validate` | `{ email, code, verificationToken }` | `{ accessToken, refreshToken }` |
| POST | `/auth/password-reset/request` | `{ email }` | — |
| POST | `/auth/password-reset/confirm` | `{ token, newPassword }` | — |
| POST | `/auth/unlock-account` | `{ email }` | code |
| POST | `/auth/unlock-account/confirm` | `{ email, code, verificationToken }` | — |
| POST | `/auth/email/verify/resend` | `{ email }` | code |

### 2.3 ⚠️ Rotation du jeton de rafraîchissement

**Le refresh token est à usage unique.** Chaque appel à `/auth/refresh` en émet un nouveau et
invalide l'ancien.

Deux conséquences :

- **Stockez toujours celui que vous venez de recevoir.** Réutiliser l'ancien échoue.
- **Sérialisez les rafraîchissements.** Deux onglets qui rafraîchissent en même temps en
  invalideront un. Un verrou ou une promesse partagée résout le cas.

### 2.4 Autorisations

`authorities` mélange `ROLE_<NOM>` et permissions `MODULE:ACTION` : `SYSTEM:USERS`,
`SYSTEM:ROLES`, `SYSTEM:PERMISSIONS`, `SYSTEM:AUDIT`. Servez-vous-en pour masquer les écrans
d'administration — le serveur refusera de toute façon.

### 2.5 Verrouillage de compte

Après **5** échecs, le compte est verrouillé → `423` / `ACCOUNT_LOCKED`. Le déverrouillage
passe par `/auth/unlock-account`. Prévoyez ce parcours : sans lui, l'utilisateur est bloqué.

---

## 3. Vocabulaire du domaine

Les énumérations sont **insensibles à la casse en entrée** (`"argileux"` passe). Une valeur
hors vocabulaire est refusée en **400**, avec la liste des valeurs acceptées dans le message.

| Domaine | Valeurs |
|---|---|
| `soilType` | `ARGILEUX` `LIMONEUX` `SABLEUX` |
| **`irrigationType`** | `PLUVIAL` `GOUTTE_A_GOUTTE` `ASPERSION` `MANUEL` |
| `cropName` | `TOMATE` `MANIOC` |
| `growthStage` | `LEVEE` `CROISSANCE` `FLORAISON` `FRUCTIFICATION` `MATURATION` `TUBERISATION` |
| `plot.status` | `ACTIVE` `ARCHIVEE` |
| `crop.status` | `EN_COURS` `TERMINEE` |
| `device.status` / `sensor.status` | `ACTIVE` `RETIRE` |
| **`sensorHealth`** | `SAINE` `SUSPECTE` `DEFAILLANTE` |
| `reading.quality` | `TERRAIN` `MANUELLE` `SIMULEE` |
| `diagnostic.source` | `IMAGE` `CAPTEUR` |
| `confidenceLevel` | `ELEVEE` `MOYENNE` `FAIBLE` |
| `recommendation.type` | `BASE` `AGRONOMIQUE` `RISQUE` `TENDANCE` `CORRELATION` `METEO` **`VOISINAGE`** `ARBITRAGE` |
| **`notification.language`** | `fr` `ln` (lingala) `kg` (kituba) |
| **`notification.channel`** | `LOG` `SMS` **`EMAIL`** |
| `recommendation.priority` | `HAUTE` `MOYENNE` `BASSE` |
| `recommendation.status` | `ACTIVE` `APPLIQUEE` `IGNOREE` |
| **`alert.category`** | `AGRONOMIQUE` `TECHNIQUE` |
| `alert.level` | `MOYENNE` `ELEVEE` `CRITIQUE` |
| `alert.status` | `NOUVELLE` `ACQUITTEE` `RESOLUE` |
| `overallStatus` | `SANS_DONNEES` `NORMAL` `VIGILANCE` `ALERTE` `CRITIQUE` |
| `deviceStatus` | `AUCUN` `ACTIF` `SILENCIEUX` |
| **`intervention.type`** | `IRRIGATION` `FERTILISATION` `TRAITEMENT` `DESHERBAGE` `SEMIS` `RECOLTE` `AUTRE` |
| **`harvest.quality`** | `EXCELLENTE` `BONNE` `MOYENNE` `MEDIOCRE` |
| **`timeline.type`** | `RELEVE` `DIAGNOSTIC` `ALERTE` `OBSERVATION` `STADE` `INTERVENTION` `RECOLTE` |
| **`membershipRole`** | `PROPRIETAIRE` `OUVRIER` `CONSEILLER` `TECHNICIEN` |
| **`accessScope`** | `AGRONOMIQUE` `ECONOMIQUE` `TECHNIQUE` |
| `granularity` | `HOUR` `DAY` `WEEK` `MONTH` |

Cultures et stades : tomate `LEVEE → CROISSANCE → FLORAISON → FRUCTIFICATION → MATURATION` ;
manioc `LEVEE → CROISSANCE → TUBERISATION → MATURATION`.

---

## 4. Codes d'erreur

Format unique, quel que soit le statut :

```json
{
  "success": false,
  "message": "Parcelle introuvable : 42",
  "errorCode": "RESOURCE_NOT_FOUND",
  "status": 404,
  "traceId": "b1f4…",
  "errors": ["name: Le nom de la parcelle est obligatoire"],
  "timestamp": "2026-07-29T10:12:44.512"
}
```

`errors` n'apparaît que sur les erreurs de validation, une ligne par champ.
`path`, `debugMessage`, `exceptionName` n'apparaissent qu'en mode développement.

| `errorCode` | HTTP | Réaction attendue |
|---|:---:|---|
| `VALIDATION_ERROR` | 400 | Afficher `errors` sous les champs concernés |
| `BAD_REQUEST` | 400 | Règle métier violée — afficher `message` tel quel |
| `UNAUTHORIZED` | 401 | Rafraîchir, puis reconnecter |
| `FORBIDDEN` / `ACCESS_DENIED` | 403 | **Ne pas réessayer.** Masquer l'action |
| `ACCOUNT_LOCKED` | 423 | Proposer `/auth/unlock-account` |
| `RESOURCE_NOT_FOUND` | 404 | L'entité n'existe pas |
| **`ENDPOINT_NOT_FOUND`** | 404 | **La route n'existe pas** — faute d'URL, jamais un problème de données |
| `DEVICE_NOT_REGISTERED` | 404 | Boîtier inconnu (ingestion) |
| `CONFLICT` | 409 | Doublon, ou clé d'idempotence rejouée avec un corps différent |
| `OPTIMISTIC_LOCK` | 409 | Modifié entre-temps → recharger et rejouer |
| `DEVICE_KEY_NOT_CONFIGURED` | 503 | Ingestion non configurée côté serveur |
| `ML_SERVICE_UNAVAILABLE` | 503 | Microservice d'inférence muet → « réessayer » |
| `SERVICE_UNAVAILABLE` | 503 | Autre dépendance externe (météo, SMS) |

> **Distinguer `ENDPOINT_NOT_FOUND` de `RESOURCE_NOT_FOUND` fait gagner des heures.**

> ### 🆕 Les refus 401 et 403 portent enfin un corps
>
> **C'est le seul changement de cette séance susceptible de casser un client
> existant.** Si votre gestion d'erreur suppose un corps **vide** sur 401 et 403 —
> parce que c'est ce qu'elle recevait — elle recevra désormais du JSON.
>
> Ce n'était pas une décision mais un **bogue** : les deux gestionnaires de Spring
> Security construisaient bien un corps depuis l'origine et ne l'écrivaient **jamais**
> dans la réponse. Le client recevait un refus muet, là où toutes les autres erreurs
> de l'API portent `errorCode`, `message` et `traceId`. Rien ne distinguait « jeton
> absent » de « permission manquante ».
>
> ```json
> {
>   "success": false,
>   "errorCode": "FORBIDDEN",
>   "status": 403,
>   "message": "Vous n'avez pas les droits requis pour cette ressource.",
>   "path": "/sni/api/v1/admin/users"
> }
> ```
>
> **Deux différences avec les autres erreurs**, parce que ces refus surviennent **en
> amont de la couche MVC**, donc hors de portée de `GlobalExceptionHandler` :
> - **pas de `traceId`** ni de `timestamp` ;
> - `errorCode` vaut `UNAUTHORIZED` ou `FORBIDDEN`, jamais `ACCESS_DENIED`.
>
> Le champ `success: false` et `errorCode` suffisent donc à traiter ces refus dans le
> même client HTTP que le reste — ce qui était impossible avec un corps vide.

---

## 5. Parcelles et cultures

### 5.1 Parcelles — `/plots`

| Méthode | Route | Notes |
|---|---|---|
| POST | `/plots` | 201 |
| PUT | `/plots/{id}` | |
| GET | `/plots/{id}` | |
| GET | `/plots` | `?userId=&status=&soilType=&q=` |
| DELETE | `/plots/{id}` | **Archivage** — `status → ARCHIVEE` |
| GET | `/plots/{id}/history` | §9.1 |
| GET | `/plots/{id}/history.csv` | §9.4 |
| GET | `/plots/{id}/timeline` | §9.2 |
| GET | `/plots/{id}/economics` | §9.3 |

**Requête** — seul `name` est obligatoire :

```jsonc
{
  "name": "Parcelle Nord",
  "location": "Makotipoko",
  "latitude": -2.7832, "longitude": 15.4211, "altitude": 320,
  "soilType": "ARGILEUX",
  "irrigationType": "PLUVIAL",
  "area": 1.5,
  "status": "ACTIVE",
  "userId": "123456789",
  "farmId": null                    // facultatif — voir §13
}
```

**Réponse** :

```jsonc
{
  "id": "1234567890123456789",
  "plotCode": "PARC-2026-000014",   // attribué par le serveur
  "name": "Parcelle Nord", "location": "Makotipoko",
  "latitude": -2.7832, "longitude": 15.4211, "altitude": 320.0,
  "geolocated": true,               // calculé
  "soilType": "ARGILEUX", "soilTypeLabel": "Argileux",
  "irrigationType": "PLUVIAL", "irrigationTypeLabel": "Pluvial",
  "waterOnDemand": false,           // calculé
  "area": 1.5,
  "status": "ACTIVE", "statusLabel": "Active",
  "userId": "123456789",
  "farmId": null, "farmName": null,
  "cooperativeId": null, "cooperativeName": null,
  "createdAt": "2026-03-02T09:00:00Z", "updatedAt": null
}
```

Trois champs calculés à exploiter :

- **`plotCode`** — référence lisible. **Affichez-la** : c'est elle qu'on dicte au téléphone
  et qu'on écrit sur un carnet, pas un identifiant à 19 chiffres.
- **`geolocated: false`** ⇒ **ni météo ni risque de voisinage** sur cette parcelle. Un
  bandeau « ajoutez les coordonnées pour activer les prévisions » a sa place.
- **`waterOnDemand`** — `false` (pluvial) change les conseils produits.
  **`null` ≠ `false`** : `null` = « on ne sait pas », et le moteur ne suppose alors rien.

Bornes : latitude ∈ [-90, 90], longitude ∈ [-180, 180], altitude ∈ [-500, 9000] m.

### 5.2 Cultures — `/crops`

| Méthode | Route | Notes |
|---|---|---|
| POST | `/crops` | 201 |
| PUT | `/crops/{id}` | |
| GET | `/crops/{id}` | |
| GET | `/crops` | `?plotId=&cropName=&status=&growthStage=` |
| DELETE | `/crops/{id}` | `status → TERMINEE` |
| GET | `/crops/{id}/calendar` | **§5.3** — calendrier prévisionnel |

> **Une seule culture `EN_COURS` par parcelle.** En déclarer une seconde renvoie **400** avec
> un message explicite. Le formulaire doit proposer de terminer la précédente.

```jsonc
{
  "plotId": "123", "cropName": "TOMATE", "variety": "Roma",
  "plantingDate": "2026-03-15",
  "cycleDurationDays": 120,         // facultatif — défaut : 120 tomate, 330 manioc
  "expectedHarvestDate": null,      // facultatif — calculé si absent
  "plantedArea": 0.8,               // hectares — nécessaire au rendement (§9.3)
  "plantDensity": 25000,
  "seedLot": "LOT-2026-A17",
  "growthStage": null,              // ⚠️ voir ci-dessous
  "status": "EN_COURS"
}
```

**Réponse** :

```jsonc
{
  "id": "…", "plotId": "123", "plotName": "Parcelle Nord",
  "cropName": "TOMATE", "cropNameLabel": "Tomate",
  "variety": "Roma", "seedLot": "LOT-2026-A17",
  "plantingDate": "2026-03-15",
  "cycleDurationDays": 120,
  "expectedHarvestDate": "2026-07-13",
  "plantedArea": 0.8, "plantDensity": 25000,
  "growthStage": "FRUCTIFICATION", "growthStageLabel": "Fructification",
  "growthStageAutoResolved": true,   // ← la valeur affichée a été recalculée
  "daysSincePlanting": 136,
  "daysToHarvest": -16,              // négatif = récolte en retard
  "cycleProgress": 1.0,
  "status": "EN_COURS", "statusLabel": "En cours",
  "createdAt": "…"
}
```

> ⚠️ **Ne construisez pas d'écran de mise à jour du stade.**
> `growthStage` est **recalculé** depuis la date de plantation à chaque diagnostic, parce
> qu'une colonne saisie une fois et jamais corrigée périme en silence — et que tout le moteur
> agronomique choisit ses seuils d'après elle.
>
> Le champ existe en entrée pour **corriger** une levée plus lente que prévu, pas pour un
> usage courant. **`growthStageAutoResolved: true`** signale que la valeur rendue diffère de
> celle enregistrée : affichez-la, mais ne la présentez pas comme une saisie de l'utilisateur.

**`daysToHarvest` négatif** = terme dépassé. C'est le cas à mettre en avant, pas à masquer.

**`plantedArea`** conditionne `yieldPerHectare` et `marginPerHectare` (§9.3). Sans elle, deux
parcelles ne sont pas comparables — un rappel dans le formulaire est justifié.

### 5.3 Calendrier prévisionnel — `GET /crops/{id}/calendar`

> 🆕 **La seule vue du système qui ANNONCE au lieu de constater.** Tout le reste est
> réactif par construction — une mesure, un symptôme, un écart. Ici :
> « floraison attendue dans 9 jours, prévoyez le traitement préventif ».

```jsonc
{
  "cropId": "…", "plotId": "…", "plotName": "Parcelle Nord",
  "cropName": "TOMATE", "variety": "Roma",
  "plantingDate": "2026-04-21",
  "cycleDurationDays": 120,
  "expectedHarvestDate": "2026-08-19",

  "currentStage": "FRUCTIFICATION", "currentStageLabel": "Fructification",
  "daysSincePlanting": 100,
  "daysToHarvest": 20,
  "cycleProgress": 0.83,

  "stages": [
    { "stage": "LEVEE",          "label": "Levée",
      "startsOn": "2026-04-21", "endsOn": "2026-05-03",
      "past": true,  "current": false, "daysUntil": -100 },
    { "stage": "CROISSANCE",     "label": "Croissance",
      "startsOn": "2026-05-04", "endsOn": "2026-06-08",
      "past": true,  "current": false, "daysUntil": -87 },
    { "stage": "FLORAISON",      "label": "Floraison",
      "startsOn": "2026-06-09", "endsOn": "2026-07-02",
      "past": true,  "current": false, "daysUntil": -51 },
    { "stage": "FRUCTIFICATION", "label": "Fructification",
      "startsOn": "2026-07-03", "endsOn": "2026-08-01",
      "past": false, "current": true,  "daysUntil": -27 },
    { "stage": "MATURATION",     "label": "Maturation",
      "startsOn": "2026-08-02", "endsOn": null,
      "past": false, "current": false, "daysUntil": 3 }
  ],

  "nextStage": { "stage": "MATURATION", "label": "Maturation",
                 "startsOn": "2026-08-02", "endsOn": null,
                 "past": false, "current": false, "daysUntil": 3 },

  "limitation": "Ces dates sont calculées à partir de la date de plantation et de la durée de cycle, selon des proportions indicatives non encore validées par une source agronomique. Ce sont des prévisions, pas un calendrier établi …"
}
```

- **`nextStage`** est extrait pour l'affichage : c'est lui qui porte le
  « dans 3 jours » d'un bandeau. **`null` quand le cycle est achevé** — il n'y a
  alors plus rien à annoncer, et fabriquer un stade suivant laisserait croire que la
  campagne continue.
- **`daysUntil` est négatif quand la phase a commencé.** Cela vous permet d'écrire
  « commencée depuis 27 jours » sans refaire le calcul.
- **`endsOn` est `null` sur la dernière phase** : elle court jusqu'à la récolte, dont
  la date est un objectif et non une borne du cycle.

> ⚠️ **`limitation` doit être affiché à côté des dates, pas dans un repli.** Ce sont
> des **projections** issues de proportions de cycle indicatives. Une levée lente,
> une sécheresse ou une variété mal identifiée les décalent **toutes**. Un
> exploitant qui prépare un traitement pour une date fausse perd le produit *et* la
> fenêtre — c'est un coût réel, pas une nuance de présentation.

**Aucun calcul nouveau côté serveur** : ces dates étaient déjà calculées depuis la
V16, et personne ne les lisait. Elles servaient au recalcul du stade courant et à la
chronologie, qui écarte explicitement les stades à venir pour ne pas mêler le
constaté au prévu.

---

## 6. Boîtiers, capteurs, relevés, observations

| Ressource | Routes | Filtres |
|---|---|---|
| Boîtiers | `POST\|PUT\|GET\|DELETE /devices` · `GET /devices/technical/{technicalId}` | `?plotId=&status=&maxBatteryLevel=&q=` |
| Capteurs | `POST\|PUT\|GET\|DELETE /sensors` | `?deviceId=&plotId=&status=&sensorType=` |
| Relevés | `POST\|GET\|DELETE /readings` | `?plotId=&deviceId=&from=&to=&anomalyOnly=&quality=` |
| Observations | `POST\|PUT\|GET\|DELETE /observations` | `?plotId=&userId=&from=&to=` |

`DELETE /devices/{id}` et `/sensors/{id}` **retirent du parc** (`status → RETIRE`).

### 6.1 Boîtier

```jsonc
{
  "id": "…", "plotId": "…", "plotName": "Parcelle Nord",
  "technicalId": "ESP32-A17", "deviceName": "Boîtier nord",
  "status": "ACTIVE",
  "batteryLevel": 62, "batteryVoltage": 3.81,
  "firmwareVersion": "1.4.2",
  "lastSeenAt": "2026-07-29T08:05:00Z",
  "minutesSinceLastSeen": 12,
  "installedAt": "2026-02-11T00:00:00Z",

  "sensorHealth": "SUSPECTE",
  "sensorHealthLabel": "Suspecte",
  "sensorHealthReason": "Écart persistant aux autres boîtiers de la parcelle sur l'humidité du sol (34,2 contre 51,7). Sonde vraisemblablement à étalonner ; les mesures restent utilisées, avec réserve.",
  "sensorHealthCheckedAt": "2026-07-29T08:05:03Z",

  "registeredAt": "…", "updatedAt": "…"
}
```

> **`sensorHealth` est une information de premier plan, pas un détail technique.**
>
> | Valeur | Effet réel |
> |---|---|
> | `SAINE` | rien à signaler |
> | `SUSPECTE` | les mesures **restent utilisées**, mais le diagnostic porte une réserve (`dataQualityNote`, §8.2) |
> | `DEFAILLANTE` | **le diagnostic est suspendu** sur cette parcelle |
>
> Sur `DEFAILLANTE`, l'exploitant verra ses relevés arriver **sans conseils** : il faut lui
> dire pourquoi, sinon il conclura à une panne du service. `sensorHealthReason` est rédigé
> pour être affiché tel quel et dit **quelle** sonde changer.
>
> **`status` et `sensorHealth` sont indépendants.** Un boîtier peut être parfaitement `ACTIVE`
> et remonter des mesures fausses — c'est justement le cas dangereux.

`lastSeenAt` est mis à jour à **chaque contact**, y compris un appel de liveness sans mesure.
Il distingue « boîtier muet » de « parcelle jamais instrumentée » — deux pannes qui n'appellent
pas la même intervention.

### 6.2 Relevé

```jsonc
{
  "id": "…", "plotId": "…", "plotName": "…", "deviceId": "…",
  "temperature": 28.4,              // ⚠️ AIR
  "temperatureSol": 24.1,           //    SOL
  "humiditeSol": 41.2, "humiditeAir": 78.0,
  "ph": 6.4, "azote": 42.0, "phosphore": 18.0, "potassium": 30.0,
  "luminosite": 21000.0,
  "pluviometrie": 0.0,
  "conductiviteElectrique": 1.2,
  "signalStrength": -71,            // dBm, donc négatif
  "quality": "TERRAIN",
  "anomalyDetected": false,
  "recordedAt": "2026-07-29T08:05:00Z"
}
```

> ⚠️ **`temperature` est la température de l'AIR.** Le nom ne le dit pas ; l'usage si — c'est
> elle que les moteurs comparent aux seuils de la culture, et elle que le microservice
> d'inférence reçoit. **`temperatureSol`** est la mesure du sol, qui commande la germination et
> la tubérisation du manioc.
>
> **Étiquetez-les distinctement** dans vos graphiques, sinon l'utilisateur lira deux courbes
> qu'il croira redondantes.

**`anomalyDetected: true`** = au moins une valeur **physiquement impossible** (pH 22,
humidité 130 %). Le relevé est **conservé** : c'est la trace de la panne, et c'est ce qui
alimente la chronologie (§9.2).

**`signalStrength`** distingue « sonde en panne » de « couverture réseau faible ».

### 6.3 Observation

Constat terrain saisi par un humain : `note`, `photoUrl`, `observedAt`, `userId`.
**`photoUrl` attend une URL déjà hébergée** — il n'y a pas d'upload d'image côté API.

---

## 7. Ingestion — pour le firmware

> **Ne concerne pas le frontend web.** Documenté pour Rolle et pour écrire un simulateur.

Authentification par **clé partagée**, pas par JWT — un microcontrôleur n'a ni la mémoire ni
l'horloge pour gérer un cycle de vie de jeton :

```
X-Device-Key: <clé>
```

### 7.1 `POST /ingest/readings`

Seul `technicalId` est obligatoire ; toutes les métriques sont facultatives. Les bornes de
validation sont **très larges** et n'écartent que l'absurde (trame corrompue) — une mesure
physiquement impossible est **acceptée puis marquée en anomalie**, jamais rejetée.

Champs à ne pas oublier :

- **`recordedAt`** — horodatage réel. **Impératif lors d'un rejeu après coupure**, sinon toute
  la série s'écrase sur l'instant de reconnexion et l'analyse de tendance devient fausse.
- `batteryLevel`, `batteryVoltage`, `firmwareVersion`, `signalStrength` — remontés avec le
  relevé, ils mettent à jour la fiche du boîtier.

**Réponse 201, sans enveloppe** :

```jsonc
{
  "readingId": "…", "plotId": "…", "plotName": "Makotipoko",
  "anomalyDetected": false,
  "anomalousMeasures": [],              // ex. ["pH", "humidité du sol"]
  "sensorHealth": "SAINE",
  "sensorHealthReason": null,
  "diagnosed": true,
  "diagnosis": "STRESS_HYDRIQUE",
  "skipReason": null,
  "message": null,
  "recommendationCount": 3,
  "recordedAt": "…"
}
```

**`skipReason`** dit pourquoi aucun diagnostic n'a été produit — le relevé est **toujours**
enregistré :

| Valeur | Sens |
|---|---|
| `CONDITIONS_STABLES` | régulateur : intervalle minimal non écoulé et aucune mesure n'a bougé |
| `CONTEXTE_ABSENT` | pas de culture en cours, ou relevé introuvable |
| `ML_INDISPONIBLE` | service d'analyse injoignable |
| **`SONDE_DEFAILLANTE`** | **la sonde est jugée hors service — mieux vaut ne rien conseiller que conseiller faux** |

Le régulateur est actif par défaut. Trois échappatoires forcent le diagnostic : intervalle
écoulé (5 min), franchissement d'un seuil de variation, ou anomalie matérielle.

### 7.2 `POST /ingest/readings/batch`

`{ "readings": [ …, … ] }`, **200 relevés maximum**. **Le lot n'est pas atomique** : un relevé
corrompu ne fait pas perdre les autres.

```jsonc
{
  "received": 42, "accepted": 41, "rejected": 1, "diagnosed": 6,
  "results": [ /* un IngestResult par relevé accepté */ ],
  "failures": [
    { "index": 17, "technicalId": "ESP32-A17",
      "errorCode": "DEVICE_NOT_REGISTERED", "message": "Boîtier non enregistré : ESP32-A17" }
  ]
}
```

`failures[].index` désigne la position dans le tableau envoyé — le boîtier sait exactement
quoi ne pas réémettre.

### 7.3 `GET /ingest/health?technicalId=`

```json
{
  "status": "UP",
  "serverTime": "2026-07-29T13:13:07.626740900Z",
  "deviceKnown": true,
  "ingestReady": false,
  "deviceKeyRequired": true
}
```

- **`serverTime`** — le boîtier cale son horloge avant de tamponner des relevés hors ligne.
- **`ingestReady: false`** ⇒ tout `POST /ingest/readings` répondra **503**. Inutile d'émettre.
- **`deviceKeyRequired`** — le boîtier sait s'il doit joindre l'en-tête, sans essuyer un 401
  pour l'apprendre.
- **`technicalId` en paramètre** (facultatif) : signale que le boîtier est vivant **même sans
  mesure à déposer**. Sans lui, un boîtier dont toutes les sondes sont débranchées serait
  compté parmi les muets.

---

## 8. Diagnostic, conseils, alertes

### 8.1 Produire un diagnostic

| Méthode | Route | Usage |
|---|---|---|
| POST | `/diagnosis/image/predict` | **multipart** — `plotId`, `image`, `cropName?`, `readingId?` |
| POST | `/diagnosis/sensor/predict` | paramètres — `plotId`, `cropName?`, `readingId?` |
| POST | `/diagnosis/image` | JSON, prédiction **déjà calculée** ailleurs |
| POST | `/diagnosis/sensor` | idem |
| GET | `/diagnosis/{id}` | historique d'un diagnostic |
| GET | `/diagnosis/{id}/explain` | **justification** — §8.4 |
| GET | `/diagnosis/{id}/replay` | 🆕 **rejeu** avec les seuils actuels — §8.6 |
| GET | `/diagnosis` | `?plotId=&source=&result=&minConfidence=&from=&to=` |

Image : JPEG, PNG ou WebP, **8 Mo maximum**.

### 8.2 `DiagnosisResult`

```jsonc
{
  "diagnosticId": "…", "source": "IMAGE", "result": "Late_blight",
  "confidenceScore": 0.97, "confidenceLevel": "ELEVEE", "reliable": true,
  "cropName": "TOMATE",

  "alternatives": [ { "diseaseCode": "Early_blight", "probability": 0.02 } ],

  "comparison": [ {
    "diseaseCode": "Early_blight", "displayName": "Alternariose",
    "modelProbability": 0.02, "riskScore": 0.18,
    "sharedConditions": ["température entre 18 et 28 °C"],
    "distinguishingConditions": ["humidité de l'air > 85 %"],
    "statement": "Mildiou retenu (97 %) plutôt qu'Alternariose (2 %) : les deux maladies partagent température entre 18 et 28 °C, mais les conditions mesurées réunissent humidité de l'air > 85 % — ce qui correspond à Mildiou (82 % des conditions réunies) et non à Alternariose (18 %)."
  } ],

  "advisory": "…",
  "corroboration": "Les conditions mesurées corroborent ce diagnostic : …",
  "dataQualityNote": null,

  "cropAutoResolved": true, "readingAutoResolved": true,

  "indicators": {
    "vpd": 0.42, "vpdInterpretation": "…",
    "rangePosition": { "humidite_sol": 0.18 },
    "nutrientRatio": { "azote": 1.05 }, "nutrientImbalance": 1.8
  },
  "risks": [ {
    "diseaseCode": "…", "displayName": "Mildiou", "riskScore": 0.72, "level": "MODERE",
    "satisfiedConditions": ["humidité de l'air > 85 %"], "statement": "…", "prevention": "…"
  } ],
  "trends": [ {
    "measureField": "humidite_sol", "slopePerHour": -1.2,
    "currentValue": 41.2, "thresholdValue": 30.0, "hoursToThreshold": 9.3,
    "sampleSize": 24, "rSquared": 0.88, "fitQuality": "…",
    "priority": "MOYENNE", "statement": "…"
  } ],

  "recommendations": [ {
    "content": "…", "type": "AGRONOMIQUE", "priority": "HAUTE",
    "category": "STRESS_HYDRIQUE", "sourceRuleId": "…",
    "measureField": "humidite_sol", "observedValue": 24.0, "thresholdValue": 35.0
  } ]
}
```

**Les cinq champs à ne pas ignorer** :

| Champ | Pourquoi |
|---|---|
| **`reliable: false`** | Le diagnostic **ne lève aucune alerte**. Affichez-le en retrait, avec `advisory`, jamais comme un verdict |
| **`comparison`** | Répond à « pourquoi pas l'autre maladie ? ». `statement` est prêt à afficher. **Vide sur la chaîne capteur** |
| **`corroboration`** | Croise modèle et mesures. Peut **nuancer** : « le symptôme résulte de conditions antérieures ». `null` = rien de concluant |
| **`dataQualityNote`** | Non nul ⇒ sonde **suspecte**. `confidenceLevel` mesure la certitude du modèle, **jamais** l'exactitude des mesures |
| `cropAutoResolved` / `readingAutoResolved` | Le serveur a déduit la culture ou le relevé — « d'après la culture en cours » |

`confidenceLevel` : `ELEVEE` (≥ 0,85) · `MOYENNE` · `FAIBLE` (< 0,60 ⇒ `reliable: false`).

**Les sept moteurs, à distinguer visuellement** :

| `type` | Origine |
|---|---|
| `BASE` | règle liée à la maladie diagnostiquée |
| `AGRONOMIQUE` | écart mesuré aux exigences de la culture |
| `RISQUE` | conditions d'apparition réunies **sur VOS mesures** (alerte précoce, sans symptôme) |
| `TENDANCE` | franchissement de seuil **projeté** |
| `CORRELATION` | croisement image / mesures |
| `METEO` | **prévision externe** — regarde devant |
| **`VOISINAGE`** 🆕 | **maladie détectée sur une parcelle VOISINE** — regarde à côté |
| `ARBITRAGE` | **synthèse** de deux conseils contradictoires |

> **`ARBITRAGE` se lit différemment.** Ce n'est pas un conseil de plus mais la conciliation de
> deux autres, qui **restent affichés**. Il arrive en tête à priorité égale. Un encadré ou un
> liseré évite que l'utilisateur croie à une contradiction du système.
>
> **`METEO` explique les conseils qui semblent contredire les mesures** : « humidité à 24 %,
> mais 18 mm de pluie d'ici 6 h — différez l'irrigation ». Sans étiquette visible, ce conseil
> paraît incohérent.

> ### 🆕 `VOISINAGE` — à ne surtout pas afficher comme un `RISQUE`
>
> C'est le seul conseil dont **rien n'est observable sur la parcelle de
> l'exploitant**, et c'est précisément son intérêt : il est **préventif**.
>
> | | `RISQUE` | `VOISINAGE` |
> |---|---|---|
> | Fondé sur | **vos** mesures | un diagnostic **ailleurs** |
> | Vérifiable chez soi | ✅ oui | ❌ non, et le texte le dit |
> | Ce qu'on en fait | corriger la condition | **inspecter**, et ne pas propager |
>
> Les confondre visuellement produit un conseil incompréhensible : l'exploitant lit
> « conditions favorables au mildiou », ne le retrouve pas dans ses mesures, et
> **cherche l'erreur dans ses sondes**. Le texte du conseil énonce explicitement
> « Aucun symptôme n'a été relevé sur votre parcelle : c'est une alerte de proximité,
> non un diagnostic » — **ne le tronquez pas**, c'est la phrase qui évite le
> malentendu.
>
> **La traçabilité est géographique, pas agronomique** : `measureField` vaut
> `"distance_km"`, `observedValue` la distance du foyer le plus proche et
> `thresholdValue` le rayon de recherche (2 km par défaut). **`sourceRuleId` est
> `null`** — ce conseil ne vient d'aucune règle de la base de connaissance, et
> `/explain` n'aura donc rien à en dire.
>
> La distance figure en clair dans le texte (« à 800 m ») : c'est ce qui rend le
> conseil actionnable, là où « à proximité » ne dit rien.
>
> **Il n'apparaît jamais** si la parcelle n'a pas de coordonnées, si aucun voisin n'a
> de diagnostic anormal récent (14 jours), ou si la maladie est **déjà** signalée par
> un conseil `RISQUE` local — auquel cas le voisinage se tait pour ne pas faire
> douter du système.

**Les conseils arrivent déjà triés.** N'y touchez pas.

**Parcelle pluviale** : les conseils d'irrigation sont **reformulés** en actions réalisables
(paillage, ombrage, binage). Le texte est plus long — prévoyez la place.

### 8.3 Conseils — `/recommendations`

| Méthode | Route | Notes |
|---|---|---|
| GET | `/recommendations/{id}` | |
| GET | `/recommendations` | `?plotId=&diagnosticId=&status=&priority=&type=&from=&to=` |
| PATCH | `/recommendations/{id}/feedback` | `{ status: "APPLIQUEE"\|"IGNOREE", feedbackNote? }` |
| GET | `/recommendations/uptake` | `?plotId=` |

La réponse porte aussi **`estimatedCost`** — coût indicatif de l'action, en devise
locale **par hectare**.

> 🆕 **Il cessait d'être toujours `null`.** Le champ figurait au contrat depuis la
> V16 et aucune source ne le renseignait : il sortait systématiquement vide, et vous
> pouviez raisonnablement en conclure que le backend était cassé. La chaîne est
> désormais complète — le coût descend de la règle (`/knowledge/rules`) jusqu'au
> conseil.
>
> **Mais aucun prix n'a été semé**, et c'est délibéré : les seuils agronomiques sont
> déjà « indicatifs et à valider », et y ajouter des prix inventés franchirait une
> ligne — un seuil approximatif oriente une *observation*, un prix approximatif
> oriente une *décision d'achat*. Les valeurs restent donc nulles jusqu'à ce qu'un
> agronome ou un fournisseur local en donne de sourcées, via
> `PUT /knowledge/rules/{id}`.
>
> ⚠️ **`null` signifie « non renseigné », jamais « gratuit ».** **Zéro est une valeur
> licite et distincte** : un binage manuel ne coûte que du temps. N'affichez pas
> « 0 XAF » pour un `null`.
>
> **Par hectare, et non en montant absolu** : la règle ignore quelle parcelle la
> déclenchera. La multiplication par la surface vous revient, ou passe par
> `/plots/{id}/economics` qui connaît `plantedArea`.

> **Un conseil déjà tranché ne se retranche pas** : rejouer renvoie 400. Grisez le bouton dès
> que `status ≠ ACTIVE`.
>
> **`feedbackNote` compte surtout sur `IGNOREE`** — c'est là que se trouve ce qui permettra
> d'amender la règle. Rendez-le visible sur le rejet, facultatif sur l'application.
>
> **Déclarer une intervention (§10.1) marque le conseil `APPLIQUEE` automatiquement.** Ne
> demandez pas les deux : le meilleur parcours est un bouton « j'ai appliqué ce conseil » qui
> pré-remplit le formulaire d'intervention.

**`/recommendations/uptake`** — par type de moteur : `total`, `applied`, `ignored`, `pending`,
`applicationRate`. Un type systématiquement ignoré signale une règle à réviser : c'est un
écran d'administration à part entière.

### 8.4 Justification — `GET /diagnosis/{id}/explain`

Renvoie la justification **du conseil tel qu'il a été émis**, reconstruite depuis les colonnes
de traçabilité — jamais recalculée sur les seuils d'aujourd'hui.

```jsonc
{
  "diagnosticId": "…", "plotId": "…", "plotName": "…",
  "cropName": "TOMATE", "source": "IMAGE", "sourceLabel": "Image",
  "result": "Late_blight", "confidenceScore": 0.97,
  "confidenceLevel": "ELEVEE", "reliable": true,
  "modelName": "EfficientNetB0-tomate",
  "readingId": "…", "readingRecordedAt": "…",
  "measures": { "temperature": 28.4, "humidite_air": 89.0, "ph": 6.4 },
  "limitation": null,
  "comparison": [ /* comme §8.2, mais modelProbability = null */ ],
  "recommendations": [ {
    "id": "…", "content": "…", "type": "AGRONOMIQUE", "typeLabel": "…",
    "priority": "HAUTE", "priorityLabel": "Haute", "status": "ACTIVE",
    "sourceRuleId": "…", "measureField": "humidite_sol",
    "observedValue": 24.0, "thresholdValue": 35.0, "deviation": -11.0,
    "rationale": "Déclenché parce que l'humidité du sol vaut 24,00, soit en deçà du seuil de 35,00 (écart de 11,00)."
  } ]
}
```

- **`rationale`** est rédigé pour l'affichage — c'est la réponse à « pourquoi ce conseil ? ».
- **`limitation`** non nul ⇒ diagnostic produit **sans relevé** : aucun moteur agronomique n'a
  tourné. **À afficher**, sinon le résultat paraît plus complet qu'il ne l'est.
- Dans `comparison`, **`modelProbability` est `null`** : les probabilités du classifieur ne
  sont pas conservées. La comparaison porte sur les seules conditions mesurées, exactement
  reproductibles — c'est ce qu'on attend d'une justification a posteriori.

### 8.5 Alertes — `/alerts`

| Méthode | Route | Notes |
|---|---|---|
| GET | `/alerts` | `?plotId=&category=&level=&status=&openOnly=&from=&to=` |
| GET | `/alerts/{id}` | |
| PATCH | `/alerts/{id}/acknowledge` | « je m'en occupe » |
| PATCH | `/alerts/{id}/resolve` | « c'est réglé » |
| PATCH | `/alerts/{id}/assign` | `{ userId?, dueAt? }` |

**Les alertes ne se créent pas par API** : le moteur les lève.

```jsonc
{
  "id": "…", "plotId": "…", "plotName": "…", "diagnosticId": "…",
  "category": "AGRONOMIQUE", "categoryLabel": "Agronomique",
  "level": "ELEVEE", "levelLabel": "Élevée",
  "message": "…",
  "status": "NOUVELLE", "statusLabel": "Nouvelle", "open": true,
  "assignedToUserId": null, "assignedToName": null,
  "dueAt": null, "overdue": null,
  "createdAt": "…", "acknowledgedAt": null, "resolvedAt": null,
  "lastSeenAt": "…",
  "ageHours": 14,                    // nombre, pas chaîne
  "resolutionReason": null,
  "escalationCount": 2
}
```

> **`category` sépare deux publics.** `AGRONOMIQUE` → l'exploitant. `TECHNIQUE` → le
> technicien (sonde figée, boîtier muet). **Faites-en deux listes**, ou au minimum un filtre
> visible : mêlées, chacun apprend à ignorer celles de l'autre, y compris les siennes.

Cycle : `NOUVELLE → ACQUITTEE → RESOLUE`. Une transition invalide renvoie 400.
Désactivez les boutons quand `open === false`. Sur **409**, rechargez : quelqu'un est passé avant.

- **`escalationCount`** — reconstats sans acquittement. Au-delà du seuil (3), l'alerte **monte
  d'un niveau** toute seule. Un badge « signalée 3 fois » a du sens.
- **`resolutionReason`** — à afficher différemment selon la valeur :
  `RESOLUE_MANUELLEMENT` (quelqu'un est intervenu) · `AUTO_SITUATION_NORMALISEE` (le problème
  a cessé seul) · `AUTO_SITUATION_REMPLACEE` (une autre alerte a pris le relais).
  Seule la première atteste d'une action.
- **`overdue: true`** — échéance dépassée, alerte encore ouverte. `null` = pas d'échéance.
- **`assign`** — une alerte sans destinataire reste dans la liste de tout le monde, donc de
  personne. `userId: null` retire l'affectation ; `dueAt` doit être **à venir**.

### 8.6 🆕 Rejeu — `GET /diagnosis/{id}/replay`

Ce que la base de connaissance **actuelle** conclurait sur le **même relevé**.

> **À qui cela sert.** À l'agronome, pas à l'exploitant. La base de connaissance est
> pilotable par API — on ajuste un seuil d'humidité de 35 à 32 % — mais rien ne
> disait *ce que cela change*. Il fallait modifier, puis attendre le prochain relevé
> pour voir : sur des conditions différentes de celles qui avaient soulevé la
> question, donc sans y répondre. Le rejeu rend le réglage **vérifiable**.
>
> C'est un écran d'administration de la connaissance, à placer près de
> `/knowledge/rules` et de `/recommendations/uptake`.

```jsonc
{
  "diagnosticId": "…", "plotId": "…", "plotName": "Parcelle Nord",
  "cropName": "TOMATE", "source": "CAPTEUR",

  "readingId": "…", "readingRecordedAt": "2026-07-12T08:05:00Z",
  "originalDiagnosedAt": "2026-07-12T08:05:03Z",

  "original": {
    "result": "STRESS_HYDRIQUE", "confidenceScore": 0.88,
    "confidenceLevel": "ELEVEE", "reliable": true,
    "recommendationCount": 3,
    "recommendations": [
      { "content": "…", "type": "AGRONOMIQUE", "priority": "HAUTE",
        "measureField": "humidite_sol", "observedValue": 24.0, "thresholdValue": 35.0 }
    ]
  },
  "replayed": {
    "result": "STRESS_HYDRIQUE", "confidenceScore": 0.88,
    "confidenceLevel": "ELEVEE", "reliable": true,
    "recommendationCount": 4,
    "recommendations": [ /* même forme */ ]
  },

  "differences": [
    { "kind": "SEUIL_MODIFIE", "kindLabel": "Seuil modifié",
      "category": "STRESS_HYDRIQUE",
      "statement": "Le seuil appliqué à humidite_sol est passé de 35,00 à 32,00." },
    { "kind": "CONSEIL_AJOUTE", "kindLabel": "Conseil ajouté",
      "category": "CARENCE_N",
      "statement": "La connaissance actuelle produit un conseil (agronomique) que le diagnostic d'origine ne portait pas : « … »" }
  ],
  "identical": false,
  "summary": "2 écart(s) entre les conseils émis et ceux que la connaissance actuelle produirait sur ce relevé. Un écart constate un changement, il ne l'approuve pas.",
  "limitation": "Ce rejeu compare ce qui a été conclu à ce que la base de connaissance actuelle conclurait sur le MÊME relevé …"
}
```

**`kind`** : `CONSEIL_AJOUTE` · `CONSEIL_RETIRE` · `SEUIL_MODIFIE` ·
`PRIORITE_MODIFIEE` · `REJEU_IMPOSSIBLE`.

- **`identical: true`** ⇒ `differences` est vide, et c'est l'information la plus
  fréquente **et la plus rassurante** : les ajustements faits ailleurs n'ont pas
  d'effet sur ce cas. Affichez-le explicitement plutôt qu'un tableau vide.
- **`REJEU_IMPOSSIBLE`** ⇒ le diagnostic a été produit **sans relevé** : aucun moteur
  agronomique n'avait tourné, il n'y a rien à rejouer. Un `differences` vide se
  lirait « rien n'a changé », ce qui serait faux.
- **`GET` et non `POST`** : rien n'est créé, rien n'est modifié. Vous pouvez le
  rejouer, le mettre en cache, le rafraîchir sans conséquence.

> ⚠️ **`limitation` doit être affiché.** Un écart constate que la connaissance a
> changé ; **il ne dit pas qu'elle a changé en mieux**. C'est à l'agronome d'en
> juger, et présenter le rejeu comme une validation inverserait le sens de l'outil.
>
> **Deux limites à énoncer dans l'écran** :
> - **le modèle d'image n'est pas rappelé** — la photo n'est pas conservée. Sur un
>   diagnostic `IMAGE`, seuls les moteurs agronomiques sont rejoués, et `result` est
>   donc identique des deux côtés par construction ;
> - **rien n'est enregistré** — aucun diagnostic, aucune recommandation, aucune
>   alerte. Le rejeu ne pollue ni la chronologie de la parcelle ni le taux de suivi
>   des conseils.

---

## 9. Les vues composées

Ce sont les écrans que le backend prépare. **Aucune ne demande de recomposition côté client** —
c'est leur raison d'être.

### 9.1 Série agrégée — `GET /plots/{id}/history`

`?from=&to=&granularity=HOUR|DAY|WEEK|MONTH` (défaut `DAY`).

```jsonc
{
  "plotId": "…", "plotName": "…",
  "from": "…", "to": "…",
  "granularity": "DAY", "granularityLabel": "Jour",
  "bucketCount": 30, "readingCount": 8640,
  "points": [ {
    "bucket": "2026-07-01T00:00:00Z",
    "sampleCount": 288, "anomalyCount": 0,
    "measures": {
      "humidite_sol": { "min": 38.1, "avg": 44.7, "max": 52.0 },
      "temperature":  { "min": 21.0, "avg": 27.3, "max": 33.8 }
    }
  } ]
}
```

> **Ne rapatriez plus les relevés bruts pour tracer une courbe.** Un mois représente plusieurs
> milliers de lignes ; cet endpoint en renvoie trente.
>
> **Tracez min/max en bande autour de la moyenne.** La moyenne seule masque les pics — or
> c'est un pic de température ou un creux d'humidité qui explique un diagnostic.
>
> Une mesure jamais relevée sur l'intervalle est **absente** de `measures`, pas à zéro :
> « pas de donnée » et « zéro » ne se confondent pas.

Mesures possibles : `temperature`, `humidite_sol`, `humidite_air`, `ph`, `azote`, `phosphore`,
`potassium`, `luminosite`.

### 9.2 Chronologie unifiée — `GET /plots/{id}/timeline`

`?from=&to=&types=ALERTE,INTERVENTION&page=&size=` (défaut 50).

```jsonc
{
  "plotId": "…", "plotName": "…", "plotCode": "PARC-2026-000014",
  "from": "…", "to": "…",
  "entryCount": 50, "totalEntries": 137,
  "countsByType": { "DIAGNOSTIC": 88, "ALERTE": 12, "INTERVENTION": 9, "STADE": 4 },
  "requestedTypes": [],

  "truncated": false,            // 🆕 voir l'encadré ci-dessous
  "perSourceLimit": 200,         // 🆕
  "truncatedTypes": [],          // 🆕
  "entries": [ {
    "occurredAt": "2026-07-28T14:02:00Z",
    "type": "INTERVENTION", "typeLabel": "Intervention",
    "title": "Fertilisation",
    "detail": "Urée 46 % · 12,50 kg/ha · suite au conseil 998877",
    "severity": "INFO",
    "refType": "Intervention", "refId": "…",
    "actor": "Joel M."
  } ]
}
```

**C'est la vue qui raconte l'histoire de la parcelle.** Sept sources fusionnées, triées **du
plus récent au plus ancien** : `RELEVE` (marquants seulement — anomalies), `DIAGNOSTIC`,
`ALERTE` (**levée et résolution**, deux entrées), `OBSERVATION`, `STADE`, `INTERVENTION`,
`RECOLTE`.

- **`types` n'est pas un filtre d'affichage** : chaque nature coûte une requête serveur.
  Transmettez-le si votre écran n'en montre qu'une partie.
> ### 🆕 `truncated` — la chronologie pouvait mentir par omission
>
> Chaque nature d'événement est plafonnée à **`perSourceLimit`** (200) par le
> serveur. Jusqu'ici rien ne le disait : une chronologie plafonnée se lisait
> **exactement** comme une chronologie complète, et `totalEntries` paraissait être le
> total réel alors qu'il ne comptait que ce qui avait franchi le plafond.
>
> Le client concluait « il n'y a eu que 200 diagnostics » — alors que c'est la
> **borne**, et non les faits, qui avait décidé de la dernière ligne. Sur une vue dont
> l'objet est de *raconter ce qui s'est passé*, laisser croire à l'exhaustivité est une
> erreur de fond, pas un détail de présentation.
>
> - **`truncated: true`** ⇒ la fenêtre demandée contient **plus** d'événements que ce
>   qui est rendu. À afficher : « affichage limité aux 200 événements les plus récents
>   par nature — resserrez la période pour tout voir ».
> - **`truncatedTypes`** dit **lesquelles** sont incomplètes (`["DIAGNOSTIC"]`).
>   Resserrer la fenêtre n'a d'intérêt que si l'on sait ce qui débordait.
> - `STADE` et `RECOLTE` ne sont **jamais** plafonnés : leur nombre est borné par la
>   culture et par la période, pas par une requête.

- **`countsByType` porte sur la fenêtre entière**, pas sur la page — c'est « douze alertes ce
  mois-ci » que vous affichez, pas « trois à l'écran ».
- **`severity`** (`INFO` / `ATTENTION` / `CRITIQUE`) est porté par l'entrée, pas déduit du
  type : une alerte moyenne et une alerte critique sont toutes deux des alertes.
- **`STADE`** est **reconstitué** depuis la date de plantation — le changement de stade n'est
  enregistré nulle part, mais c'est une fonction déterministe du temps.
- `refType` + `refId` mènent au détail.

### 9.3 Bilan économique — `GET /plots/{id}/economics`

`?cropId=&from=&to=` (dates `yyyy-MM-dd`). Sans `cropId`, la campagne en cours.
Sans bornes, les **deux dernières années**.

```jsonc
{
  "plotId": "…", "plotName": "…", "plotCode": "…",
  "cropId": "…", "cropName": "TOMATE",
  "from": "2024-07-29", "to": "2026-07-29",
  "currency": "XAF",

  "harvestCount": 3, "totalQuantity": 1840.0, "quantityUnit": "kg",
  "grossRevenue": 920000.00,

  "interventionCount": 11, "totalCost": 312500.00,
  "costByInterventionType": {
    "Fertilisation": 180000.00, "Traitement": 92500.00, "Irrigation": 40000.00
  },

  "margin": 607500.00,
  "plantedArea": 0.8,
  "marginPerHectare": 759375.00,
  "yieldPerHectare": 2300.0,
  "costRatio": 33.97,

  "recommendationCount": 47, "appliedRecommendationCount": 29, "uptakeRate": 61.7,

  "summary": "Produit brut 920000.00 XAF pour 312500.00 XAF de charges — marge positive de 607500.00 XAF. Soit 759375.00 XAF par hectare, pour un rendement de 2300.0 kg/ha. 29 conseils sur 47 ont été appliqués (62 %).",
  "limitation": "Le taux de suivi des conseils et le rendement sont présentés côte à côte : c'est un constat, pas une démonstration. …",
  "missingData": [],
  "generatedAt": "…"
}
```

> ⚠️ **`limitation` doit être affiché, jamais masqué.** Le rapprochement « conseils suivis /
> rendement » est **descriptif**. Une parcelle où les conseils ont été suivis et qui a mieux
> produit ne prouve pas que les conseils en soient la cause : le sol, la variété, la météo et
> l'attention portée à la parcelle varient ensemble. Un chiffre livré sans cette réserve sera
> lu comme une démonstration.
>
> ⚠️ **`missingData` explique les vides.** Exemple réel : « Aucune intervention enregistrée :
> les charges sont nulles par absence de saisie, la marge affichée est donc surestimée. »
> Encadrez-le — sans lui, l'utilisateur croit à une marge exceptionnelle.
>
> `marginPerHectare` et `yieldPerHectare` sont **`null`** sans `plantedArea` sur la culture.
> Ce sont les seuls chiffres comparables entre parcelles : un lien « compléter la fiche
> culture » a sa place ici.

**`costRatio`** = part du produit absorbée par les charges. Au-delà de 100, la campagne est
déficitaire. `null` si le produit est nul (parcelle pas encore récoltée).

**Comparaison entre parcelles** : `GET /overview/economics?userId=&from=&to=` renvoie la même
structure par parcelle, **triée par marge à l'hectare décroissante**. Les parcelles sans
surface passent en fin de liste.

### 9.4 Export CSV — `GET /plots/{id}/history.csv`

Mêmes paramètres que `/history`. Renvoie `text/csv; charset=UTF-8` avec
`Content-Disposition: attachment`.

**Séparateur point-virgule, virgule décimale, BOM UTF-8** — pour qu'un tableur francophone
l'ouvre correctement.

> **Servez le fichier en téléchargement direct.** Ne le repassez pas dans un parseur CSV
> standard côté client : il attendrait des virgules et lirait tout dans une seule colonne.

### 9.5 Tableaux de bord — `/overview`

| Route | Rend |
|---|---|
| `GET /overview/farm?userId=` | Synthèse **une requête**, tous plots confondus |
| `GET /overview/plots` | Une ligne par parcelle, paginée |
| `GET /overview/plots/{plotId}` | État complet d'une parcelle |
| `GET /overview/economics` | Bilan par parcelle, comparé (§9.3) |

**`/overview/farm`** — l'écran d'accueil :

```jsonc
{
  "plotCount": 12,
  "plotsByStatus": { "NORMAL": 8, "VIGILANCE": 2, "ALERTE": 1, "CRITIQUE": 1 },
  "plotsNeedingAttention": [
    { "plotId": "…", "plotName": "…", "overallStatus": "CRITIQUE",
      "openAlertCount": 2, "lastReadingAt": "…" }
  ],
  "openAlertCount": 5,
  "openAlertsByLevel": { "CRITIQUE": 1, "ELEVEE": 3, "MOYENNE": 1 },
  "deviceCount": 18, "lowBatteryDeviceCount": 2, "plotsWithoutReading": 1,
  "summary": "…",
  "limitation": "…",
  "generatedAt": "…"
}
```

> ⚠️ **`limitation`** : ici le statut est déduit d'agrégats. Une parcelle peut apparaître
> `NORMAL` dans cette vue et `VIGILANCE` dans son détail — la vigilance fondée sur un **risque**
> suppose d'exécuter le moteur, ce que seule la vue détaillée fait. **Ne présentez pas les
> deux comme contradictoires.**

**`/overview/plots`** (`PlotSummary`) : `plotId`, `plotName`, `cropName`, `overallStatus`,
`openAlertCount`, `lastReadingAt`, `deviceStatus`. **Un seul appel** — ne bouclez pas sur
`/plots` puis `/alerts` par parcelle.

**`/overview/plots/{plotId}`** (`PlotOverview`) suffit à l'écran complet : culture en cours,
boîtiers et batterie minimale, dernier relevé et son âge, indicateurs, risques, dernier
diagnostic, alertes ouvertes, `overallStatus`, `summary` en français.

**`overallStatus`**, par précédence décroissante :
`SANS_DONNEES` → `CRITIQUE` → `ALERTE` → `VIGILANCE` → `NORMAL`.

**`deviceStatus`** : `AUCUN` · `ACTIF` · `SILENCIEUX` (aucun contact depuis 15 min). Fondé sur
`lastSeenAt`, donc distinct de « aucun relevé ».

---

## 10. Journal des actions

### 10.1 Interventions — `/interventions`

| Méthode | Route | Notes |
|---|---|---|
| POST | `/interventions` | 201 |
| PUT | `/interventions/{id}` | |
| GET | `/interventions/{id}` | |
| GET | `/interventions` | `?plotId=&cropId=&type=&from=&to=` |
| GET | `/interventions/{id}/effect` | **mesure de l'effet** |
| DELETE | `/interventions/{id}` | suppression **réelle** |

```jsonc
{
  "plotId": "123",
  "cropId": null,                   // déduit : culture en cours
  "recommendationId": "998877",     // ← ferme la boucle
  "type": "FERTILISATION",
  "product": "Urée 46 %", "dose": 12.5, "unit": "kg/ha",
  "cost": 18000,
  "performedAt": "2026-07-28T14:02:00Z",
  "performedById": null,            // déduit : utilisateur authentifié
  "weatherNote": "Ciel couvert, sol ressuyé",
  "note": "…"
}
```

**Seuls `plotId` et `type` sont obligatoires.** C'est délibéré : une saisie faite le soir, de
mémoire, ne doit pas être refusée parce que le dosage exact a été oublié. Une intervention
consignée approximativement vaut infiniment mieux qu'une intervention non consignée.

> **`recommendationId` ferme la boucle conseil → action.** Le renseigner bascule le conseil en
> `APPLIQUEE` : ne demandez pas les deux. Le meilleur point d'entrée est un bouton
> « j'ai appliqué ce conseil » sur la recommandation, qui pré-remplit ce formulaire.

La réponse porte **`effectMeasurable`** : **`false`** pour `TRAITEMENT`, `DESHERBAGE`, `SEMIS`,
`RECOLTE`, `AUTRE`. **Masquez le bouton « mesurer l'effet »** dans ce cas — il ne pourrait rien
conclure, l'effet d'un fongicide ne se lit pas sur une sonde.

Elle porte aussi `dosage` (`"12,50 kg/ha"`, déjà formaté) et `recommendationContent` (extrait
du conseil suivi).

**`GET /interventions/{id}/effect`** — compare les **48 h avant** et les **48 h après** :

```jsonc
{
  "interventionId": "…", "type": "IRRIGATION", "typeLabel": "Irrigation",
  "performedAt": "…", "windowHours": 48,
  "targetMeasure": "humidite_sol", "targetMeasureLabel": "l'humidité du sol",
  "beforeFrom": "…", "beforeTo": "…", "afterFrom": "…", "afterTo": "…",
  "beforeSampleCount": 288, "afterSampleCount": 291,
  "beforeAverage": 24.1, "afterAverage": 43.8,
  "change": 19.7, "changePercent": 81.74,
  "verdict": "AMELIORATION", "verdictLabel": "Effet conforme à l'attendu",
  "statement": "L'humidité du sol est passée de 24,1 à 43,8 dans les 48 h qui ont suivi (+19,7, soit +82 %). L'évolution va dans le sens attendu d'irrigation.",
  "abnormalDiagnosesBefore": 3, "abnormalDiagnosesAfter": 0,
  "limitation": "Cet écart constate une évolution, il n'établit pas une cause. Une pluie, un changement de température ou une autre intervention survenus dans la même fenêtre produiraient le même chiffre."
}
```

`verdict` : `AMELIORATION` · `AUCUN_CHANGEMENT` · `DEGRADATION` · `INDETERMINE`.

> ⚠️ **`limitation` est toujours renseigné et doit toujours être affiché.** Une comparaison
> avant/après n'établit jamais une causalité.

### 10.2 Récoltes — `/harvests`

`POST | PUT | GET | DELETE /harvests`, liste `?plotId=&cropId=&from=&to=` (dates `yyyy-MM-dd`).

```jsonc
{
  "plotId": "123", "cropId": null,   // déduit, mais OBLIGATOIRE au final
  "quantity": 640, "unit": "kg",
  "quality": "BONNE",
  "harvestedAt": "2026-07-20",
  "unitPrice": 500, "currency": "XAF",
  "note": "…"
}
```

> **`cropId` est déduit de la culture en cours** — mais s'il n'y en a aucune, la requête est
> refusée en **400** : une récolte non rattachée à une campagne n'entre dans aucun bilan.
>
> **On saisit un prix unitaire, jamais un montant total.** Le total se recalcule
> (`grossRevenue`) ; un prix unitaire perdu ne se retrouve pas, et c'est lui qui permet de
> comparer deux campagnes de volumes différents.

La réponse ajoute `grossRevenue` et `yieldPerHectare` (`null` sans `plantedArea`).

---

## 11. Notifications

### 11.1 Préférences — `/notifications/preferences`

`GET` et `PUT`. **Aucun identifiant en paramètre** : ces réglages sont personnels, l'identité
vient du jeton.

```json
{
  "minLevel": "ELEVEE",
  "channels": ["SMS"],
  "language": "fr",
  "quietFromHour": 22,
  "quietToHour": 6
}
```

Le `PUT` est un **remplacement complet**, pas une fusion : c'est la seule forme qui laisse
l'utilisateur certain de ce qui s'applique ensuite.

**Réponse** :

```jsonc
{
  "userId": "…",
  "minLevel": "ELEVEE", "minLevelLabel": "Élevée",
  "minLevelInherited": false,
  "channels": ["SMS"],
  "availableChannels": ["LOG", "SMS", "EMAIL"],

  "language": "ln",
  "languageLabel": "Lingala",
  "availableLanguages": { "fr": "Français", "ln": "Lingala", "kg": "Kituba" },
  "languageScopeNote": "L'urgence, la parcelle et l'action à mener sont traduites. Le détail technique du diagnostic reste en français : il est composé automatiquement à partir des mesures, et une traduction approximative y donnerait un conseil faux. Votre conseiller peut vous l'expliquer.",

  "quietFromHour": 22, "quietToHour": 6,
  "quietHoursLabel": "Aucune notification de 22h à 06h le lendemain, sauf alerte critique — qui passe outre, sans quoi ce niveau ne voudrait rien dire.",
  "smsReachable": true,
  "updatedAt": "…"
}
```

- **`minLevelInherited: true`** ⇒ rien n'a été réglé, le seuil global s'applique. Pré-remplissez
  avec cette valeur, en la présentant comme un défaut.
- **`availableChannels`** ⇒ ce que le serveur sait réellement envoyer. Si `SMS` n'y figure pas,
  la passerelle n'est pas configurée — **ne le proposez pas**.
> ### 🆕 Langue des notifications — lingala et kituba
>
> **`language` était réglable et n'était lue par personne.** La colonne existe depuis
> la V18 ; le message était composé en français quelle qu'en soit la valeur.
> L'utilisateur pouvait la changer et constater que rien ne se passait — le pire des
> cas, puisqu'il en concluait que le réglage était décoratif plutôt qu'à un défaut.
>
> **Pourquoi seulement les notifications, et pas l'interface.** Ce sont les seuls
> messages que l'application adresse à quelqu'un qui **n'a pas choisi de la
> consulter** : sur un téléphone simple, au champ. C'est là — et seulement là — que la
> langue décide si le message est lu ou ignoré. L'administration est utilisée par des
> agronomes et des techniciens dont le français est la langue de travail.
>
> #### ⚠️ Ce qui est traduit, et ce qui ne l'est pas
>
> | | Traduit | Reste en français |
> |---|---|---|
> | **Sujet** — urgence, catégorie, parcelle | ✅ | |
> | **Amorce** — ce qu'il faut faire | ✅ | |
> | **Constat du diagnostic** — mesures, seuils, écarts | | ❌ |
>
> **C'est une décision, pas une paresse.** Le constat est une prose composée à la
> volée (« l'humidité du sol vaut 24,00, soit en deçà du seuil de 35,00 »). Le
> traduire exigerait de traduire chaque règle de la base de connaissance, chaque
> libellé de mesure et chaque gabarit de phrase, **à trois exemplaires**, alignés à
> chaque évolution du moteur. Une traduction qui dérive est **pire** qu'une absence de
> traduction : elle donne un conseil *faux* dans la langue que la personne comprend le
> mieux — donc celui qu'elle suivra.
>
> Est traduit ce qui **décide de l'action**, et c'est aussi ce qu'on lit en premier
> dans une liste de messages. Un pied de message annonce explicitement que le détail
> est en français et invite à s'appuyer sur son conseiller.
>
> #### Pour votre écran
>
> - **`availableLanguages`** est servi par le serveur : construisez le menu déroulant
>   avec, ne le codez pas en dur. Ajouter une langue doit être une modification serveur,
>   pas une livraison frontend.
> - **`languageScopeNote`** est rédigé pour être affiché **sous le sélecteur**. Sans
>   lui, un exploitant qui choisit le lingala et reçoit un détail français conclut à un
>   bogue.
> - **`language` est normalisée à l'écriture** : envoyez `fr-CG`, relisez `fr`. Une
>   valeur inconnue retombe sur le français **sans échouer** — mieux vaut un message en
>   français qu'aucun message.
> - ⚠️ **Les formulations lingala et kituba sont à faire relire par un locuteur
>   natif** avant mise en service, au même titre que les seuils agronomiques. Si vous
>   repérez une maladresse, signalez-la : elles sont écrites pour être corrigées.

> ### 🆕 `EMAIL` dans `availableChannels`
>
> Un canal SMTP existe désormais. Même contrat que le SMS : **hôte non configuré ⇒
> absent d'`availableChannels`**, donc à ne pas proposer.
>
> Ce qu'il apporte et que le SMS ne peut pas : **la place**. Un SMS est tronqué à 320
> caractères, et `sensorHealthReason` — le motif qui dit *quelle sonde changer* — y
> entre rarement. C'est le canal du **conseiller et du technicien** plus que celui de
> l'exploitant, et il convient donc particulièrement aux alertes `TECHNIQUE`.
>
> L'adresse utilisée est l'e-mail du compte, là où le SMS utilise `phone`. Un
> utilisateur peut donc être joignable sur l'un et pas sur l'autre.

- **`smsReachable: false`** ⇒ l'utilisateur **n'a pas de numéro**. Le canal aurait beau être
  coché, rien ne partirait. Un lien « ajoutez votre numéro » est la bonne réponse.
- **Les heures de silence peuvent enjamber minuit** (22 → 6). `quietHoursLabel` formule
  correctement les deux cas — utilisez-le plutôt que de le reconstruire.
- Les deux bornes vont **ensemble** : l'une sans l'autre renvoie 400.

**Une alerte `CRITIQUE` passe outre les heures de silence.** Dites-le dans l'interface : c'est
ce qui rassure sur le fait de les activer.

### 11.2 Regroupement

Plusieurs alertes de la même parcelle, du même niveau, dans une fenêtre de **10 minutes** sont
réunies en **un seul** message (« … (3 situations) »). Aucun effet côté client — cela explique
qu'on reçoive moins de messages que d'alertes.

### 11.3 Administration — `/admin/notifications`

`GET /admin/notifications?status=&channel=&plotId=` liste la file d'envoi.
`POST /admin/notifications/dispatch?batchSize=50` relance les envois en attente — utile après
avoir configuré la passerelle.

Statuts : `EN_ATTENTE` · `ENVOYEE` · `ECHOUEE` · `ABANDONNEE`.

---

## 12. Base de connaissance

Le moteur agronomique est **entièrement pilotable par API** : ajuster un seuil ne demande pas
de redéploiement.

| Ressource | Routes | Filtres |
|---|---|---|
| Seuils par culture | `POST\|PUT\|GET\|DELETE /knowledge/crop-requirements` | — |
| Seuils par stade | `POST\|PUT\|GET\|DELETE /knowledge/crop-requirements/stages` | `?cropName=` |
| Maladies | `POST\|PUT\|GET\|DELETE /knowledge/diseases` | `?cropName=` |
| Conditions de risque | `POST\|PUT\|GET\|DELETE /knowledge/diseases/conditions` | `?diseaseId=` |
| Règles de décision | `POST\|PUT\|GET\|DELETE /knowledge/rules` | `?category=&cropName=` |
| Corrélations | `POST\|PUT\|GET\|DELETE /knowledge/correlations` | — |
| Arbitrages | `POST\|PUT\|GET\|DELETE /knowledge/arbitrations` | — |

**Ces listes ne sont pas paginées** : quelques dizaines de lignes chacune.

Le joker **`'*'`** dans `cropName` désigne une règle valable quelle que soit la culture.

> Les services knowledge valident leurs invariants et lèvent des **400 avec un message
> métier** : seuils incohérents (min > max), doublon culture/stade, culture inconnue.
> **Affichez `message` tel quel** — il est rédigé pour l'utilisateur.
>
> ⚠️ Une modification de seuil peut mettre jusqu'à **30 minutes** à se refléter : les tables de
> connaissance sont en cache. Les écritures **via l'API** évincent le cache immédiatement ; une
> modification faite directement en base, non.
>
> ⚠️ Les valeurs agronomiques semées à l'installation sont **indicatives** et doivent être
> validées par des sources agronomiques avant exploitation réelle.

---

## 13. Organisation — exploitation et coopérative

> **Entièrement facultatif.** Une parcelle sans exploitation fonctionne exactement comme
> avant. **N'imposez pas** de choisir une exploitation dans le formulaire de parcelle.

| Méthode | Route | Notes |
|---|---|---|
| `POST\|PUT\|GET\|DELETE` | `/admin/cooperatives` | `?status=&q=` — `DELETE` **archive** |
| `POST\|PUT\|GET\|DELETE` | `/admin/farms` | `?cooperativeId=&ownerId=&status=&q=` |
| GET | `/admin/farms/{id}/members` | |
| POST | `/admin/farms/{id}/members` | `{ userId, role }` — **crée ou met à jour** |
| DELETE | `/admin/farms/{id}/members/{userId}` | |

Hiérarchie `Cooperative → Farm → Plot`, **chaque rattachement facultatif**.

**Rôles et domaines ouverts** :

| Rôle | Agronomique | Économique | Technique |
|---|:---:|:---:|:---:|
| `PROPRIETAIRE` | ✅ | ✅ | ✅ |
| `CONSEILLER` | ✅ | ❌ | ✅ |
| `OUVRIER` | ✅ | ❌ | ✅ |
| `TECHNICIEN` | ❌ | ❌ | ✅ |

La réponse d'appartenance porte **`scopes: ["AGRONOMIQUE", "TECHNIQUE"]`**. **Affichez-les**
dans l'écran d'attribution : « conseiller » ne dit pas de lui-même s'il donne accès aux marges,
et l'administrateur doit savoir ce qu'il ouvre.

**Conséquences pour vos écrans** :

- Un `TECHNICIEN` ou un `OUVRIER` reçoit **403** sur `/plots/{id}/economics`. **Masquez
  l'onglet** plutôt que de laisser l'appel échouer.
- `/overview/economics` **écarte silencieusement** les parcelles interdites au lieu d'échouer :
  la liste peut être plus courte qu'ailleurs. Prévoyez une mention.
- **Le propriétaire direct d'une parcelle voit tout, quoi qu'il arrive** — même s'il n'est pas
  membre de l'exploitation. Une appartenance **ajoute** un accès, elle n'en retire jamais.
- Retirer le propriétaire de référence d'une exploitation renvoie **400**.
- **Archiver** une exploitation laisse ses parcelles intactes : elles redeviennent
  indépendantes, leur propriétaire ne perd rien.

---

## 14. Administration système

| Ressource | Base | Permission |
|---|---|---|
| Utilisateurs | `/admin/users` | `SYSTEM:USERS` |
| Rôles | `/admin/roles` | `SYSTEM:ROLES` |
| Permissions | `/admin/permissions` | `SYSTEM:PERMISSIONS` |
| Attribution | `/admin/users/{userId}/roles` · `/admin/roles/{roleId}/permissions` | `SYSTEM:ROLES` / `:PERMISSIONS` |
| Journal d'audit | `/admin/audit-logs` | `SYSTEM:AUDIT` |
| Audit configuration | `/admin/settings-audit-logs` | `SYSTEM:AUDIT` |
| Idempotence | `/admin/idempotency-records` | `SYSTEM:AUDIT` |
| Notifications | `/admin/notifications` · `POST /dispatch` | admin |
| Provisionnement | `/admin/provisioning/bootstrap-admin` · `/staff` | ⚠️ **aucune garde** |

**Utilisateurs** — routes par `userCode` (pas par identifiant numérique) :
`POST /admin/users`, `GET /admin/users?email=`, `GET /admin/users/search/by-name?query=`,
`GET /admin/users/{userCode}`, `GET /admin/users/by-email?email=`,
`PUT /admin/users/{userCode}`,
`PATCH /admin/users/{userCode}/activate|deactivate|unlock|password/reset`,
`POST /admin/users/{userCode}/reset-password`, `DELETE /admin/users/{userCode}`.

> **`phone` est accepté en création et mise à jour** — c'est le **destinataire des alertes
> SMS**, et sans lui l'utilisateur reste injoignable au champ. Un champ visible, avec la
> mention de son usage, vaut mieux qu'un champ enfoui dans un accordéon.

**Rôles et permissions** — routes par `name` : `POST`, `PUT /{name}`, `GET /{name}`, `GET`,
`GET /search/by-name?query=`, `PATCH /{name}/activate|deactivate`, `DELETE /{name}`.

---

## 15. Idempotence

Les écritures d'administration acceptent **`Idempotency-Key`** :

```
POST /sni/api/v1/admin/users
Idempotency-Key: 7f3a…
```

Rejouer la **même clé** avec le **même corps** renvoie la réponse d'origine sans réexécuter.
La même clé avec un corps **différent** renvoie **409**.

> **Générez un UUID au montage du formulaire**, pas à la soumission : c'est ce qui protège du
> double-clic et du retour arrière du navigateur. Renouvelez-le après un succès.

---

## 16. Parcours d'écran

### 16.1 Tableau de bord

```
GET /overview/farm          → bandeau : combien, dans quel état, où aller
GET /overview/plots?page=0  → la liste
```

Ne bouclez **pas** sur `/plots` puis `/alerts` par parcelle.

### 16.2 Détail d'une parcelle

```
GET /overview/plots/{plotId}                        → l'écran principal
GET /plots/{id}/timeline?size=30                    → onglet « historique »
GET /plots/{id}/history?granularity=DAY&from=&to=   → onglet « courbes »
GET /plots/{id}/economics                           → onglet « économie » (si autorisé)
```

**Un seul appel par onglet.** La chronologie remplace à elle seule les quatre appels qu'il
fallait auparavant.

### 16.3 Diagnostic par photo

```
POST /diagnosis/image/predict   (multipart : plotId, image)
   ↓
afficher DiagnosisResult
   ├─ comparison[]      → « pourquoi pas l'autre maladie ? »
   ├─ dataQualityNote   → réserve sur la fiabilité des mesures
   └─ recommendations[] → déjà triées, ARBITRAGE en tête
   ↓ « Pourquoi ce conseil ? »
GET /diagnosis/{diagnosticId}/explain
```

Gérez `503 ML_SERVICE_UNAVAILABLE` par un bouton « réessayer » : **rien n'est perdu**, le relevé
est enregistré.

### 16.4 Traiter une alerte

```
GET   /alerts?openOnly=true&category=AGRONOMIQUE&sort=level,desc
PATCH /alerts/{id}/assign        { userId, dueAt }   → « c'est pour Untel, sous 48 h »
PATCH /alerts/{id}/acknowledge                       → « je m'en occupe »
PATCH /alerts/{id}/resolve                           → « c'est réglé »
```

Le tri sur `level` **fonctionne** (sémantique). Deux listes séparées pour `AGRONOMIQUE` et
`TECHNIQUE`.

### 16.5 Boucler conseil → action → effet

```
GET   /recommendations?plotId={id}&status=ACTIVE&sort=priority,asc
   ↓ bouton « j'ai appliqué ce conseil »
POST  /interventions   { plotId, type, recommendationId, … }
   ↓ le conseil passe APPLIQUEE tout seul
   ↓ 48 h plus tard
GET   /interventions/{id}/effect   → verdict chiffré + limitation
```

**C'est le parcours qui donne sa valeur au système** : il montre que le conseil a été suivi, et
ce qu'il a produit.

### 16.6 Santé du parc IoT

```
GET /devices?maxBatteryLevel=20                → batteries à remplacer
GET /devices                                   → filtrer sensorHealth !== "SAINE"
GET /alerts?category=TECHNIQUE&openOnly=true   → pannes signalées
GET /overview/plots                            → filtrer deviceStatus === "SILENCIEUX"
```

**`sensorHealthReason` est rédigé pour l'écran du technicien** : il dit quelle sonde changer.

### 16.7 Suivi économique

```
GET /overview/economics                → classement par marge/ha
GET /plots/{id}/economics?cropId=…     → détail d'une campagne
GET /plots/{id}/history.csv            → export pour tableur
```

Affichez `limitation` et `missingData` **à côté** des chiffres, pas dans un repli.

---

## 17. État actuel du backend

Points vérifiés le 2026-07-29. À connaître pour ne pas chercher des bugs côté frontend.

| Constat | Effet pour vous | Statut |
|---|---|---|
| **Sécurité permissive** : une requête sans jeton est authentifiée comme admin | les routes métier répondent sans `Authorization` | durcissement planifié — **codez comme si le jeton était exigé** |
| **Cloisonnement par propriétaire inactif** (`ownership.enabled=false`) | `?userId=` est pris au mot ; les rôles d'exploitation n'ont pas encore d'effet | s'activera avant toute démo |
| **Tables de sécurité à amorcer** | `/auth/login` ne peut réussir tant qu'aucun compte n'existe | via `/admin/provisioning/bootstrap-admin` |
| **`bilanga.ingest.device-key` à configurer** | `POST /ingest/readings` répond 503 ; `health` renvoie `ingestReady: false` | configuration |
| **Envoi d'e-mails désactivé** | codes OTT et de réinitialisation **dans la réponse API** | provisoire — ne l'exposez pas à l'utilisateur final |
| **Passerelle SMS non configurée** | `availableChannels` ne contient que `LOG` | l'infrastructure est prête, il manque l'URL de l'opérateur |
| **Météo active mais sans effet sans coordonnées** | aucun conseil `METEO` sur une parcelle non géolocalisée | invitez à renseigner latitude/longitude |
| **`data.data`** sur les listes paginées | double imbrication | isolez l'accès dans un helper |
| **Canal e-mail non configuré** | `EMAIL` absent d'`availableChannels` | il manque l'hôte SMTP (`MAIL_HOST`) |
| **Swagger** | devrait répondre : `/webjars/**` et `/v3/api-docs` manquaient au `permitAll` | ✅ corrigé, **à confirmer au démarrage** |
| **`estimatedCost` encore `null` partout** | la chaîne fonctionne, aucun prix n'est semé | volontaire — attend des valeurs sourcées (§8.3) |
| **Conseils `VOISINAGE` invisibles** | il faut ≥ 2 parcelles géolocalisées et un diagnostic anormal récent chez l'une | à prévoir dans votre jeu de démonstration |

### Recommandations de conception

1. **Un client HTTP unique** qui déballe l'enveloppe, teste `success`, et lève une exception
   typée portant `errorCode` et `traceId`.
2. **Un seul helper de pagination** — le `data.data` doit apparaître à un seul endroit.
3. **Les identifiants restent des chaînes**, de la réponse jusqu'à l'URL, sans exception.
4. **Envoyez le `Bearer` dès maintenant**, même si le serveur l'ignore encore : le durcissement
   est planifié et ne doit pas vous prendre au dépourvu.
5. **Ne recalculez rien de ce que le backend fournit** (§1.6).
6. **Affichez systématiquement `limitation`, `missingData` et `dataQualityNote`.** Ce sont des
   garde-fous d'honnêteté ; les masquer transforme un constat prudent en affirmation.

---

## 18. Les dix pièges

1. **Les identifiants sont des chaînes.** Typez-les `string`, ne les convertissez jamais.
2. **`ENDPOINT_NOT_FOUND` ≠ `RESOURCE_NOT_FOUND`.** Le premier est une faute d'URL.
3. **`data.data`** — double imbrication sur les listes paginées.
4. **Le refresh token est rotatif et à usage unique.** Sérialisez les rafraîchissements.
5. **`temperature` = air, `temperatureSol` = sol.** Étiquetez les deux courbes.
6. **`reliable: false`** ⇒ aucune alerte levée. Ce n'est pas un verdict.
7. **`dataQualityNote`** ⇒ mesures douteuses. La confiance du modèle ne dit rien de la sonde.
8. **Pas de formulaire pour `growthStage`** : il est recalculé.
9. **Déclarer une intervention marque le conseil `APPLIQUEE`.** Ne demandez pas deux fois.
10. **`/plots/{id}/history` remplace les relevés bruts.** Un mois = 30 points, pas 8 640 lignes.
11. 🆕 **`VOISINAGE` n'est pas un `RISQUE`.** Rien n'est observable chez l'exploitant —
    ne le présentez pas comme une détection sur sa parcelle (§8.2).
12. 🆕 **`truncated: true`** ⇒ la chronologie est **incomplète**. `totalEntries` n'est
    alors pas le total réel (§9.2).
13. 🆕 **`estimatedCost: null` = « non renseigné », jamais « gratuit ».** Zéro est une
    valeur licite et distincte (§8.3).
14. 🆕 **401 et 403 portent maintenant un corps.** Si votre client supposait le
    contraire, il faut l'adapter (§4).

---

## 19. Ce qui n'existe pas

Pour éviter de le chercher :

- **Pas de WebSocket ni de SSE.** Tout est en interrogation. Pour un tableau de bord vivant,
  interrogez `/overview/farm` toutes les 30 à 60 secondes.
- **Pas d'upload d'image** hors diagnostic : `photoUrl` attend une URL déjà hébergée.
- **Pas d'export PDF.** Le CSV existe (§9.4).
- **Pas de recherche plein texte globale.** Les `?q=` sont limités aux champs annoncés.
- **Pas de création d'alerte par API** : le moteur les lève seul.
- **Pas de carte** : les coordonnées sont exposées, le rendu cartographique vous appartient.
- **Pas de suppression physique** pour parcelles, cultures, boîtiers, capteurs, coopératives,
  exploitations : archivage ou retrait. **Interventions et récoltes font exception** — une
  saisie fautive y fausserait les calculs, qui sont précisément leur raison d'être.
