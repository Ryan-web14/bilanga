 # BILANGA — Documentation de l'API

Référence destinée au développement de l'interface. Version du 24 juillet 2026.

---

## 1. Généralités

**Base des URL** : `http://localhost:8080/sni/api/v1`

Tous les chemins qui suivent sont relatifs à cette base.

**Format** : JSON en entrée comme en sortie, encodage UTF-8. Une seule exception, l'envoi d'image, qui se fait en `multipart/form-data`.

**Authentification** : les chemins documentés ici sont ouverts en configuration de démonstration. Le socle RBAC existe et pourra être activé ; prévoyez dès maintenant de faire passer les appels par une couche unique, de façon à pouvoir y ajouter un en-tête `Authorization` sans reprendre chaque écran.

Seul `/ingest/readings` fait exception : il exige un en-tête `X-Device-Key`, réservé au matériel de terrain. L'interface ne l'appelle jamais.

### Dates

Deux formats coexistent.

Les instants — `createdAt`, `recordedAt`, `diagnosedAt` — sont sérialisés en ISO 8601 UTC avec nanosecondes :

```
"2026-07-23T16:53:03.437411600Z"
```

Les dates calendaires — `plantingDate` uniquement — n'ont ni heure ni fuseau :

```
"2026-05-15"
```

### Codes de retour

| Code | Signification |
|---|---|
| 200 | Succès |
| 201 | Ressource créée |
| 204 | Suppression effectuée, sans contenu |
| 400 | Données invalides — le message indique quoi |
| 401 | Clé de boîtier absente ou erronée (ingestion) |
| 404 | Ressource introuvable |
| 500 | Erreur serveur |

En cas d'erreur, la réponse porte un `traceId` qu'il est utile de journaliser côté interface : il permet de retrouver l'incident exact dans les journaux du serveur.

---

## 2. Les identifiants sont des chaînes

Les identifiants sont produits par un générateur Snowflake. Ce sont des entiers sur 64 bits, à **19 chiffres** :

```
7277003285445382144
```

JavaScript ne sait pas représenter ces valeurs. Son type `Number` est un flottant double précision, dont le plus grand entier exactement représentable vaut 9 007 199 254 740 991 — seize chiffres. Au-delà, `JSON.parse` arrondit sans lever d'erreur :

```js
JSON.parse('{"id":7277003285445382144}').id
// 7277003285445382000  ← les trois derniers chiffres sont perdus
```

L'identifiant serait alors faux, et toute requête ultérieure sur cette ressource échouerait en 404 sans cause apparente.

**Le serveur transmet donc tous les entiers 64 bits sous forme de chaînes**, ce qui supprime la conversion numérique et donc la perte de précision :

```json
{
  "id": "7277003285445382144",
  "plotId": "7277003285445382144"
}
```

### Conséquences pratiques

Traitez les identifiants comme des **chaînes opaques**. Ne les convertissez jamais en nombre, ne faites aucune arithmétique dessus, comparez-les avec `===` entre chaînes. Utilisés comme clés de liste ou paramètres d'URL, ils fonctionnent tels quels.

En entrée, le serveur accepte indifféremment `"plotId": "7277003285445382144"` et `"plotId": "7277003285445382144"`. Renvoyez simplement la chaîne reçue.

Cette règle vaut pour tous les entiers 64 bits, y compris ceux qui ne sont pas des identifiants — s'il s'en trouve, convertissez-les à l'affichage avec `Number(...)`. Les durées de la vue de synthèse, `daysSincePlanting` et `readingAgeMinutes`, échappent volontairement à cette règle : elles sont typées en entier court et arrivent en nombres.

Les décimaux — mesures, scores de confiance, indicateurs — ne sont pas concernés et restent des nombres.

---

## 3. Référentiel des valeurs

Ces listes sont contrôlées par le serveur. Toute valeur hors liste est rejetée en 400.

### Cultures

`tomate` · `manioc`

Toujours en minuscules. Le serveur normalise ce qu'il reçoit, mais l'interface a intérêt à envoyer la forme canonique. Le joker `*` désigne toutes les cultures, uniquement dans les règles.

### Stades de croissance

| Tomate | Manioc |
|---|---|
| `LEVEE` | `LEVEE` |
| `CROISSANCE` | `CROISSANCE` |
| `FLORAISON` | `TUBERISATION` |
| `FRUCTIFICATION` | `MATURATION` |
| `MATURATION` | |

En majuscules. Un stade non décrit n'est pas une erreur : les seuils généraux de la culture s'appliquent alors.

### Types de sol

`argileux` · `limoneux` · `sableux`

Ces valeurs sont celles que connaissent les encodeurs du modèle tabulaire. Une valeur inédite fait échouer le diagnostic capteur.

### Priorités

`HAUTE` · `MOYENNE` · `BASSE`

### Catégories de diagnostic

`NORMAL` · `STRESS_HYDRIQUE` · `EXCES_EAU` · `SOL_ACIDE` · `SOL_ALCALIN` · `CARENCES_NUTRITIVES` · `RISQUE_MALADIE` · `STRESS_THERMIQUE` · `MALADIE_FOLIAIRE`

Les sept premières sont produites par le modèle tabulaire. Les deux dernières n'existent que dans les règles.

### Champs de mesure

`temperature` · `humidite_sol` · `humidite_air` · `ph` · `azote` · `phosphore` · `potassium` · `luminosite`

En minuscules avec tirets bas, sans accents. C'est la source d'erreur la plus fréquente à la saisie des règles.

### Codes maladie

Sans préfixe de culture. Le classifieur produit `Tomato___Late_blight` ; l'API attend et renvoie `Late_blight`.

| Tomate | Manioc |
|---|---|
| `Early_blight` | `bacterial_blight` |
| `Late_blight` | `brown_streak_disease` |
| `Leaf_Mold` | `green_mottle` |
| `Tomato_Yellow_Leaf_Curl_Virus` | `mosaic_disease` |
| `Tomato_mosaic_virus` | `healthy` |
| `healthy` | |

`healthy` existe pour les deux cultures : ne l'utilisez jamais seul comme clé.

### Autres énumérations

| Champ | Valeurs |
|---|---|
| Source de diagnostic | `IMAGE` · `CAPTEUR` |
| Niveau de confiance | `ELEVEE` · `MOYENNE` · `FAIBLE` |
| Type de recommandation | `BASE` · `CORRELATION` · `AGRONOMIQUE` · `RISQUE` · `TENDANCE` · `ARBITRAGE` |
| Niveau de risque | `ELEVE` · `MODERE` · `FAIBLE` |
| Niveau d'alerte | `CRITIQUE` · `ELEVEE` · `MOYENNE` |
| Statut d'alerte | `NOUVELLE` · `ACQUITTEE` · `RESOLUE` |
| Statut de parcelle | `ACTIVE` · `ARCHIVEE` |
| Statut de culture | `EN_COURS` · `TERMINEE` |
| Statut de matériel | `ACTIVE` · `RETIRE` |
| État de synthèse | `SANS_DONNEES` · `NORMAL` · `VIGILANCE` · `ALERTE` · `CRITIQUE` |
| État de boîtier | `ACTIF` · `SILENCIEUX` · `AUCUN` |
| Opérateurs de règle | `>` · `<` · `>=` · `<=` · `==` · `BETWEEN` |

---

## 4. Parcelles

### Créer une parcelle

`POST /plots` → **201**

```json
{
  "name": "Parcelle Nord",
  "location": "Brazzaville, Djiri",
  "soilType": "argileux",
  "area": 1.5,
  "status": "ACTIVE",
  "userId": null
}
```

| Champ | Type | Obligatoire | Longueur | Note |
|---|---|---|---|---|
| `name` | chaîne | oui | 150 | |
| `location` | chaîne | non | 255 | |
| `soilType` | chaîne | non | 50 | référentiel des types de sol |
| `area` | décimal | non | | hectares, doit être positif |
| `status` | chaîne | non | 30 | `ACTIVE` par défaut |
| `userId` | identifiant | non | | propriétaire |

Réponse :

```json
{
  "id": "7277003285445382144",
  "name": "Parcelle Nord",
  "location": "Brazzaville, Djiri",
  "soilType": "argileux",
  "area": 1.5,
  "status": "ACTIVE",
  "userId": null,
  "createdAt": "2026-07-23T16:53:03.437411600Z",
  "updatedAt": null
}
```

### Autres opérations

| Méthode | Chemin | Effet |
|---|---|---|
| `PUT` | `/plots/{id}` | Modification, même corps que la création |
| `GET` | `/plots/{id}` | Détail |
| `GET` | `/plots` | Liste complète |
| `GET` | `/plots?userId={id}` | Parcelles d'un utilisateur |
| `DELETE` | `/plots/{id}` | **Archivage**, non suppression |

Le `DELETE` passe le statut à `ARCHIVEE` et renvoie **204**. Les relevés et diagnostics rattachés sont conservés : ils constituent l'historique agronomique de la parcelle. L'interface doit donc filtrer les parcelles archivées à l'affichage plutôt que d'attendre leur disparition.

---

## 5. Cultures

### Créer une culture

`POST /crops` → **201**

```json
{
  "plotId": "7277003285445382144",
  "cropName": "tomate",
  "variety": "Roma",
  "plantingDate": "2026-05-15",
  "growthStage": "FLORAISON",
  "status": "EN_COURS"
}
```

| Champ | Type | Obligatoire | Longueur |
|---|---|---|---|
| `plotId` | identifiant | oui | |
| `cropName` | chaîne | oui | 50 |
| `variety` | chaîne | non | 100 |
| `plantingDate` | date | non | |
| `growthStage` | chaîne | non | 50 |
| `status` | chaîne | non | 30 |

**Renseignez toujours `plantingDate`.** La résolution automatique de la culture trie par date de plantation décroissante ; une date absente remonte en tête du classement sous PostgreSQL et fausse la sélection.

Réponse :

```json
{
  "id": "7277003712643633152",
  "plotId": "7277003285445382144",
  "plotName": "Parcelle Nord",
  "cropName": "tomate",
  "variety": "Roma",
  "plantingDate": "2026-05-15",
  "growthStage": "FLORAISON",
  "status": "EN_COURS",
  "createdAt": "2026-07-23T16:54:45.286500800Z"
}
```

### Autres opérations

| Méthode | Chemin | Effet |
|---|---|---|
| `PUT` | `/crops/{id}` | Modification |
| `GET` | `/crops/{id}` | Détail |
| `GET` | `/crops?plotId={id}` | Cultures d'une parcelle |
| `DELETE` | `/crops/{id}` | Passe le statut à `TERMINEE` |

Une seule culture doit rester `EN_COURS` par parcelle à un instant donné. Le serveur ne le contrôle pas : c'est à l'interface de clôturer la précédente avant d'en créer une nouvelle.

---

## 6. Matériel et capteurs

### Boîtiers

`POST /devices` → **201**

```json
{
  "plotId": "7277003285445382144",
  "technicalId": "ESP32-BILANGA-01",
  "deviceName": "Boîtier nord",
  "status": "ACTIVE",
  "batteryLevel": 87
}
```

| Champ | Type | Obligatoire | Longueur | Note |
|---|---|---|---|---|
| `plotId` | identifiant | oui | | |
| `technicalId` | chaîne | oui | 100 | **unique**, gravé sur le boîtier |
| `deviceName` | chaîne | non | 100 | |
| `status` | chaîne | non | 30 | |
| `batteryLevel` | entier | non | | pourcentage |

Un `technicalId` déjà pris renvoie **400**. C'est cet identifiant que le matériel transmet à chaque relevé ; il doit correspondre exactement à celui programmé dans le boîtier.

| Méthode | Chemin | Effet |
|---|---|---|
| `PUT` | `/devices/{id}` | Modification |
| `GET` | `/devices/{id}` | Détail |
| `GET` | `/devices/technical/{technicalId}` | Recherche par numéro de série |
| `GET` | `/devices?plotId={id}` | Boîtiers d'une parcelle |
| `GET` | `/devices` | Tous |
| `DELETE` | `/devices/{id}` | Passe le statut à `RETIRE` |

### Sondes

`POST /sensors` → **201**

```json
{
  "deviceId": "7277005365539147776",
  "sensorType": "HUMIDITE_SOL",
  "status": "ACTIVE",
  "defaultValue": null
}
```

`sensorType` est libre, 50 caractères. Consultation par `GET /sensors?deviceId={id}`.

---

## 7. Relevés

### Enregistrer un relevé

`POST /readings` → **201**

```json
{
  "plotId": "7277003285445382144",
  "deviceId": null,
  "temperature": 28.0,
  "humiditeSol": 20.0,
  "humiditeAir": 50.0,
  "ph": 6.5,
  "azote": 40.0,
  "phosphore": 20.0,
  "potassium": 40.0,
  "luminosite": 4500.0,
  "quality": null
}
```

Attention à la casse : les champs sont en **camelCase** ici (`humiditeSol`), alors que les règles de la base de connaissance emploient la forme avec tirets bas (`humidite_sol`). Ce sont deux registres distincts.

| Champ | Type | Obligatoire | Contrainte |
|---|---|---|---|
| `plotId` | identifiant | oui | |
| `deviceId` | identifiant | non | |
| `temperature` | décimal | non | °C |
| `humiditeSol` | décimal | non | 0 à 100 |
| `humiditeAir` | décimal | non | 0 à 100 |
| `ph` | décimal | non | 0 à 14 |
| `azote` | décimal | non | |
| `phosphore` | décimal | non | |
| `potassium` | décimal | non | |
| `luminosite` | décimal | non | lux |
| `quality` | chaîne | non | 30 caractères |

Les bornes sont vérifiées et renvoient **400** si elles sont franchies.

Réponse :

```json
{
  "id": "7277005365539147776",
  "plotId": "7277003285445382144",
  "plotName": "Parcelle Nord",
  "deviceId": null,
  "temperature": 28.0,
  "humiditeSol": 20.0,
  "humiditeAir": 50.0,
  "ph": 6.5,
  "azote": 40.0,
  "phosphore": 20.0,
  "potassium": 40.0,
  "luminosite": 4500.0,
  "quality": null,
  "anomalyDetected": false,
  "recordedAt": "2026-07-23T17:01:19.370389500Z"
}
```

`anomalyDetected` signale une valeur physiquement impossible, donc une sonde probablement défaillante. À ne pas confondre avec une valeur agronomiquement mauvaise, qui relève du diagnostic.

| Méthode | Chemin | Effet |
|---|---|---|
| `GET` | `/readings/{id}` | Détail |
| `GET` | `/readings?plotId={id}` | Historique, du plus récent au plus ancien |
| `DELETE` | `/readings/{id}` | Suppression définitive |

### Observations de terrain

`POST /observations` → **201**

```json
{
  "plotId": "7277003285445382144",
  "userId": null,
  "note": "Taches brunes sur les feuilles basses",
  "photoUrl": "https://..."
}
```

`note` est un texte libre sans limite, `photoUrl` fait 255 caractères. Consultation par `GET /observations?plotId={id}`.

---

## 8. Diagnostics

Quatre points d'entrée. Deux emploient les modèles d'apprentissage, deux acceptent un diagnostic déjà connu — ces derniers servent aux tests et n'ont pas vocation à être appelés par l'interface.

### Diagnostic par image

`POST /diagnosis/image/predict` → **200**

**Corps en `multipart/form-data`**, jamais en JSON.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `plotId` | texte | oui | |
| `cropName` | texte | non | déduit de la culture en cours si absent |
| `image` | fichier | oui | JPEG ou PNG |
| `readingId` | texte | non | dernier relevé si absent |

En JavaScript :

```js
const form = new FormData();
form.append("plotId", plotId);
form.append("image", file);
// cropName et readingId sont facultatifs

const res = await fetch(`${BASE}/diagnosis/image/predict`, {
  method: "POST",
  body: form
});
```

Ne fixez pas l'en-tête `Content-Type` : le navigateur l'ajoute avec la délimitation nécessaire au multipart. Le poser manuellement casse la requête.

### Diagnostic par capteurs

`POST /diagnosis/sensor/predict` → **200**

Paramètres en chaîne de requête, **sans corps** :

```
POST /diagnosis/sensor/predict?plotId=7277003285445382144
```

| Paramètre | Obligatoire | Note |
|---|---|---|
| `plotId` | oui | |
| `cropName` | non | déduit |
| `readingId` | non | dernier relevé |

Envoyer un corps en même temps que ces paramètres provoque une duplication de valeur côté serveur. Veillez à ce que la requête n'en porte aucun.

### La réponse de diagnostic

Identique pour les deux chaînes. C'est la structure la plus riche de l'API.

```json
{
  "diagnosticId": "7277010620700069888",
  "source": "IMAGE",
  "result": "Leaf_Mold",
  "confidenceScore": 0.9994978904724121,
  "cropName": "tomate",
  "confidenceLevel": "ELEVEE",
  "reliable": true,
  "alternatives": [
    { "diseaseCode": "Leaf_Mold", "probability": 0.9994978904724121 },
    { "diseaseCode": "Late_blight", "probability": 0.00031645328272134066 }
  ],
  "advisory": null,
  "corroboration": "Les conditions mesurées ne soutiennent pas la progression...",
  "cropAutoResolved": false,
  "readingAutoResolved": false,
  "indicators": {
    "vpd": 3.936,
    "vpdInterpretation": "Stress évaporatif — la plante perd plus d'eau qu'elle n'en absorbe",
    "rangePosition": { "humidite_sol": 0.5, "ph": 0.5, "temperature": 1.5 },
    "nutrientRatio": { "azote": 1.143, "phosphore": 1.111, "potassium": 1.143 },
    "nutrientImbalance": 1.03
  },
  "risks": [
    {
      "diseaseCode": "Tomato_Yellow_Leaf_Curl_Virus",
      "displayName": "Virus des feuilles jaunes en cuillère (TYLCV)",
      "riskScore": 1.0,
      "level": "ELEVE",
      "satisfiedConditions": ["température supérieure à 28 °C (mesuré : 35,0)"],
      "statement": "Risque élevé pour...",
      "prevention": "Variétés résistantes, filets anti-insectes..."
    }
  ],
  "trends": [
    {
      "measureField": "humidite_sol",
      "slopePerHour": -4.2,
      "currentValue": 68.0,
      "thresholdValue": 60.0,
      "hoursToThreshold": 1.9,
      "sampleSize": 9,
      "priority": "HAUTE",
      "statement": "Humidité du sol en baisse de 4,20 % par heure..."
    }
  ],
  "recommendations": [
    {
      "content": "Les besoins en eau et la lutte contre la maladie foliaire se concilient...",
      "type": "ARBITRAGE",
      "priority": "HAUTE",
      "category": "MALADIE_FOLIAIRE+STRESS_HYDRIQUE",
      "sourceRuleId": null,
      "measureField": null,
      "observedValue": null,
      "thresholdValue": null
    }
  ]
}
```

#### Lecture des champs

`confidenceLevel` et `reliable` disent si la conclusion est exploitable. Quand `reliable` vaut `false`, `advisory` porte un message à afficher en évidence et `alternatives` liste les hypothèses concurrentes. Une interface honnête présente alors le diagnostic comme une hypothèse, non comme un fait.

`corroboration` a trois états. Renseigné avec un texte de concordance, les conditions mesurées appuient le diagnostic. Renseigné avec un texte de divergence, elles ne soutiennent pas sa progression — le symptôme est réel mais l'environnement actuel ne l'aggrave pas. Nul, rien de concluant.

`cropAutoResolved` et `readingAutoResolved` indiquent ce que le serveur a déduit. Utile pour signaler discrètement à l'utilisateur sur quelle culture et quel relevé le diagnostic a porté.

`indicators.rangePosition` situe chaque mesure dans la plage optimale : 0 correspond au seuil bas, 1 au seuil haut. En dessous de 0 il y a déficit, au-dessus de 1 il y a excès. C'est la donnée idéale pour une jauge.

`risks` liste les maladies dont les conditions d'apparition sont réunies, calculées à partir des seules mesures. `riskScore` va de 0 à 1. Sur la chaîne image, la maladie diagnostiquée est écartée de cette liste.

`trends` annonce les franchissements de seuil à venir. `hoursToThreshold` est le délai estimé.

`recommendations` est **déjà triée** : priorités hautes en tête, synthèses d'arbitrage avant les conseils qu'elles concilient. Ne la retriez pas. Les quatre derniers champs portent la justification et sont nuls pour les recommandations qui n'en ont pas — arbitrages et risques notamment.

### Historique

| Méthode | Chemin | Effet |
|---|---|---|
| `GET` | `/diagnosis/{id}` | Un diagnostic avec ses recommandations |
| `GET` | `/diagnosis?plotId={id}&limit=20` | Historique, du plus récent au plus ancien |

`limit` vaut 20 par défaut.

```json
{
  "id": "7277010620700069888",
  "plotId": "7277003285445382144",
  "plotName": "Parcelle Nord",
  "source": "IMAGE",
  "result": "Leaf_Mold",
  "confidenceScore": 0.999,
  "confidenceLevel": "ELEVEE",
  "cropName": "tomate",
  "imageUrl": null,
  "readingId": "7277005365539147776",
  "modelName": "Classifieur Tomate EfficientNet-B0",
  "diagnosedAt": "2026-07-23T17:15:00.000Z",
  "recommendations": [ ... ]
}
```

---

## 9. Alertes

| Méthode | Chemin | Effet |
|---|---|---|
| `GET` | `/alerts` | Alertes ouvertes, toutes parcelles |
| `GET` | `/alerts?plotId={id}` | Alertes ouvertes d'une parcelle |
| `GET` | `/alerts?plotId={id}&openOnly=false` | Historique complet |
| `PATCH` | `/alerts/{id}/acknowledge` | Prise en compte |
| `PATCH` | `/alerts/{id}/resolve` | Clôture |

Les deux `PATCH` n'attendent aucun corps et renvoient l'alerte mise à jour.

```json
{
  "id": "7277011111111111111",
  "plotId": "7277003285445382144",
  "plotName": "Parcelle Nord",
  "diagnosticId": "7277010620700069888",
  "level": "CRITIQUE",
  "message": "Situation critique sur la parcelle Parcelle Nord : STRESS_HYDRIQUE détecté par les capteurs. 2 actions à mener sans délai.",
  "status": "NOUVELLE",
  "createdAt": "2026-07-23T17:15:00.000Z",
  "acknowledgedAt": null,
  "resolvedAt": null
}
```

Une alerte reste ouverte tant qu'elle n'est ni acquittée ni résolue. Tant qu'elle l'est, la même situation ne produit pas de nouvelle alerte — un stress hydrique persistant en donne une, pas deux cents.

---

## 10. Synthèse

Ces deux points d'entrée sont ceux sur lesquels bâtir l'interface : ils évitent d'orchestrer plusieurs appels et d'en réconcilier les résultats.

### Tableau de bord

`GET /overview/plots` → **200**

```json
[
  {
    "plotId": "7277003285445382144",
    "plotName": "Parcelle Nord",
    "cropName": "tomate",
    "overallStatus": "ALERTE",
    "openAlertCount": 1,
    "lastReadingAt": "2026-07-23T17:01:19.370Z",
    "deviceStatus": "ACTIF"
  }
]
```

Une ligne par parcelle. `overallStatus` suffit à colorer une pastille.

### État d'une parcelle

`GET /overview/plots/{plotId}` → **200**

```json
{
  "plotId": "7277003285445382144",
  "plotName": "Parcelle Nord",
  "location": "Brazzaville, Djiri",
  "soilType": "argileux",
  "area": 1.5,
  "plotStatus": "ACTIVE",

  "cropName": "tomate",
  "variety": "Roma",
  "plantingDate": "2026-05-15",
  "growthStage": "FLORAISON",
  "daysSincePlanting": 69,

  "deviceCount": 1,
  "lowestBatteryLevel": 87,
  "deviceStatus": "ACTIF",

  "latestReading": { ... },
  "readingAgeMinutes": 3,
  "indicators": { ... },

  "latestDiagnostic": { ... },
  "risks": [ ... ],

  "openAlertCount": 1,
  "alerts": [ ... ],

  "overallStatus": "ALERTE",
  "summary": "Parcelle Parcelle Nord (tomate) : 1 alerte ouverte. Intervention requise...",
  "generatedAt": "2026-07-23T17:20:00.000Z"
}
```

`readingAgeMinutes` mérite d'être affiché : un diagnostic fondé sur une mesure de trois heures n'a pas la valeur d'un diagnostic sur une mesure de la minute.

`deviceStatus` à `SILENCIEUX` signale un boîtier installé mais muet depuis plus de quinze minutes. C'est la situation la plus trompeuse : la parcelle paraît surveillée alors qu'elle ne l'est plus. À signaler visuellement.

---

## 11. Administration de la base de connaissance

Cette section permet à un agronome d'enrichir le système sans intervention sur le code. Toutes les saisies sont contrôlées ; un rejet en **400** porte un message explicite indiquant les valeurs admises.

### Seuils par culture

`POST` `PUT` `GET` `DELETE` sur `/knowledge/crop-requirements`

```json
{
  "cropName": "tomate",
  "phMin": 6.0, "phMax": 6.8,
  "humSolMin": 60, "humSolMax": 80,
  "tempMin": 20, "tempMax": 30,
  "azoteMin": 35, "phosphoreMin": 18, "potassiumMin": 35,
  "toleranceSecheresse": 0.0
}
```

`toleranceSecheresse` va de 0 à 1. Chaque plage doit être ordonnée, sans quoi la saisie est rejetée.

### Seuils par stade

`POST` `PUT` `GET` `DELETE` sur `/knowledge/crop-requirements/stages`

```json
{
  "cropName": "tomate",
  "growthStage": "FRUCTIFICATION",
  "label": "Grossissement des fruits",
  "humSolMin": 70, "humSolMax": 85,
  "potassiumMin": 50
}
```

**Seuls les écarts sont à renseigner.** Un champ omis signifie que le stade n'infléchit pas ce seuil, et la valeur générale de la culture continue de s'appliquer. Un formulaire qui pré-remplirait tous les champs avec les valeurs de la culture ferait perdre cette distinction.

`GET /knowledge/crop-requirements/stages?cropName=tomate` filtre par culture.

### Maladies

`POST` `PUT` `GET` `DELETE` sur `/knowledge/diseases`

```json
{
  "cropName": "tomate",
  "diseaseCode": "Late_blight",
  "displayName": "Mildiou de la tomate",
  "symptoms": "Taches huileuses brun-noir...",
  "favorableConditions": "Forte humidité de l'air...",
  "treatment": "Retirer et détruire les parties atteintes...",
  "prevention": "Variétés résistantes...",
  "priority": "HAUTE"
}
```

Le `diseaseCode` est refusé s'il contient `___`, avec le code normalisé suggéré dans le message. Longueurs : culture 50, code 80, nom d'usage 150. Les textes descriptifs sont libres.

Supprimer une maladie supprime ses conditions d'apparition.

`GET /knowledge/diseases?cropName=tomate` filtre par culture.

### Conditions d'apparition

`POST` `PUT` `GET` `DELETE` sur `/knowledge/diseases/conditions`

```json
{
  "cropName": "tomate",
  "diseaseCode": "Late_blight",
  "measureField": "temperature",
  "operator": "BETWEEN",
  "threshold": 15,
  "thresholdMax": 25,
  "weight": 0.5,
  "label": "température comprise entre 15 et 25 °C",
  "active": true
}
```

`BETWEEN` exige `thresholdMax`, les autres opérateurs le refusent. Le poids doit être strictement positif. La maladie doit exister au préalable.

Le risque est calculé comme la part du poids total dont les conditions sont satisfaites. Deux conditions de poids 0,5 chacune donnent 100 % si toutes deux sont remplies, 50 % si une seule l'est.

### Règles attachées à un diagnostic capteur

`POST` `PUT` `GET` `DELETE` sur `/knowledge/rules`

```json
{
  "category": "STRESS_HYDRIQUE",
  "cropName": "*",
  "conditionText": "humidite_sol sous le seuil bas de la culture",
  "proposedAction": "Irriguer la parcelle rapidement, au pied des plants...",
  "priority": "HAUTE",
  "validated": true
}
```

`conditionText` est purement descriptif : il n'est pas évalué. La condition réelle est portée par le moteur agronomique.

`GET /knowledge/rules?category=STRESS_HYDRIQUE` filtre par catégorie.

### Corrélations

`POST` `PUT` `GET` `DELETE` sur `/knowledge/correlations`

```json
{
  "cropName": "tomate",
  "diseaseCode": "Late_blight",
  "measureField": "humidite_air",
  "operator": ">",
  "threshold": 80,
  "extraRecommendation": "Les conditions actuelles très humides favorisent...",
  "priority": "HAUTE"
}
```

`BETWEEN` n'est pas admis ici : le moteur de corrélation compare à un seuil unique. Pour un intervalle, créez deux règles avec `>=` et `<=`. Laisser `cropName` ou `diseaseCode` vide équivaut au joker `*`.

### Arbitrages

`POST` `PUT` `GET` `DELETE` sur `/knowledge/arbitrations`

```json
{
  "cropName": "*",
  "categoryA": "MALADIE_FOLIAIRE",
  "categoryB": "STRESS_HYDRIQUE",
  "synthesis": "Les besoins en eau et la lutte contre la maladie foliaire se concilient...",
  "priority": "HAUTE",
  "active": true
}
```

Les deux catégories doivent différer. Quand des recommandations des deux domaines coexistent, la synthèse est ajoutée en tête sans qu'aucune ne soit supprimée.

---

## 12. Communication avec le simulateur Wokwi

Cette section décrit le fonctionnement du matériel de terrain. **L'interface n'intervient pas dans cette chaîne** ; elle en observe les effets par les points d'entrée précédents.

### Le trajet

Un ESP32 simulé dans Wokwi émet une requête HTTPS réelle — Wokwi met à disposition une passerelle internet véritable. Cette requête traverse un tunnel ngrok, qui expose le poste de développement sur une adresse publique, et parvient au backend.

```
ESP32 (Wokwi, navigateur)
   ↓  HTTPS
tunnel ngrok (adresse publique)
   ↓
backend, port 8080
   ↓
vérification de la clé de boîtier
   ↓
relevé enregistré, parcelle résolue
   ↓
régulation : diagnostiquer ou non
   ↓
service ML, port 8000
   ↓
accusé compact renvoyé au boîtier
```

### Le point d'entrée

`POST /ingest/readings` → **201**

En-tête obligatoire : `X-Device-Key`

```json
{
  "technicalId": "ESP32-BILANGA-01",
  "temperature": 28.4,
  "humiditeSol": 22.1,
  "humiditeAir": 87.5,
  "ph": 6.4,
  "azote": 38,
  "phosphore": 19,
  "potassium": 41,
  "luminosite": 4800,
  "batteryLevel": 87
}
```

Le boîtier ne transmet **jamais** d'identifiant de parcelle. Il ne connaît que son numéro de série ; c'est le serveur qui résout la parcelle. Déplacer physiquement un boîtier ne demande donc aucune reprogrammation, seulement une réaffectation par `PUT /devices/{id}`.

Réponse :

```json
{
  "readingId": "7277005365539147776",
  "plotId": "7277003285445382144",
  "plotName": "Parcelle Nord",
  "anomalyDetected": false,
  "diagnosed": true,
  "diagnosis": "STRESS_HYDRIQUE",
  "message": null,
  "recommendationCount": 4,
  "recordedAt": "2026-07-23T17:01:19.370Z"
}
```

### La régulation des diagnostics

Le boîtier émet toutes les trente secondes. **Chaque relevé est enregistré, mais un diagnostic n'est produit que si quelque chose a changé** : cinq minutes écoulées depuis le précédent, ou une mesure ayant franchi son seuil de variation — cinq points d'humidité, deux degrés, 0,3 de pH.

Sans cette régulation, une parcelle produirait cent vingt diagnostics identiques par heure et l'historique deviendrait illisible.

Conséquence pour l'interface : `GET /readings?plotId=` renvoie beaucoup de lignes, `GET /diagnosis?plotId=` très peu. C'est le comportement attendu. Un tableau de bord affichant les relevés bruts doit donc échantillonner ou agréger.

Lorsque le diagnostic n'a pas lieu, la réponse porte `diagnosed: false` et un `message` explicite.

### Limites à connaître

La simulation Wokwi **se met en pause quand l'onglet perd le focus**. Il doit rester visible pendant toute démonstration.

L'URL du tunnel **change à chaque redémarrage** de ngrok en version gratuite, ce qui impose de rééditer le croquis du boîtier.

La communication est **à sens unique**. Le boîtier pousse ses mesures ; le serveur ne peut lui adresser aucune commande. Il n'y a donc pas d'ordre d'arrosage à distance ni de changement de cadence.

Le boîtier **ne mémorise rien**. Si le réseau tombe, le relevé est perdu.

---

## 13. Parcours de mise en route

L'ordre compte : chaque étape suppose la précédente.

**Un.** Créer la parcelle par `POST /plots`. Conserver l'identifiant retourné.

**Deux.** Créer la culture par `POST /crops`, avec sa date de plantation et son stade.

**Trois.** Déclarer le boîtier par `POST /devices`, avec le `technicalId` programmé dans le matériel.

**Quatre.** À partir de là, les relevés arrivent seuls et les diagnostics se déclenchent. L'interface n'a plus qu'à consulter.

Pour un diagnostic par image, il suffit du `plotId` et de la photographie : le reste est déduit.

---

## 14. Points d'attention pour l'interface

**Les identifiants sont des chaînes opaques.** Aucune conversion numérique, aucune arithmétique, comparaison entre chaînes. Voir la section 2.

**Ne fixez jamais l'en-tête `Content-Type` sur l'envoi d'image.** Le multipart exige une chaîne de délimitation que le navigateur génère lui-même en la joignant à l'en-tête. La poser à la main la remplace par une valeur sans délimiteur, et le serveur reçoit un corps qu'il ne sait pas découper — il répond alors que tous les champs manquent, y compris ceux que vous avez bien envoyés. Passez l'objet `FormData` à `fetch` sans toucher aux en-têtes.

**Les relevés sont nombreux, les diagnostics rares.** Le boîtier émet toutes les trente secondes, mais un diagnostic n'est produit que si les conditions ont changé. Une même parcelle accumule donc des centaines de relevés pour quelques diagnostics. Ce n'est pas un défaut mais une régulation délibérée, décrite en section 12. Un graphique de mesures doit échantillonner ou agréger ; une liste de diagnostics peut afficher tout ce qu'elle reçoit.

**Les recommandations sont déjà triées et dédoublonnées.** Affichez-les dans l'ordre reçu.

**Une recommandation peut être longue.** Les constats agronomiques cumulent un énoncé chiffré et une action ; certains dépassent trois cents caractères. Prévoyez un repliement plutôt qu'une troncature, qui masquerait la justification.

**`reliable: false` doit se voir.** Présenter un diagnostic peu fiable comme un fait établi serait le principal défaut d'usage de ce système.

**Les tableaux peuvent être vides.** `risks`, `trends`, `alternatives` sont fréquemment vides. `indicators`, `latestDiagnostic`, `latestReading` peuvent être nuls sur une parcelle neuve.

**Le format des nombres.** Les décimaux arrivent avec le point comme séparateur. À l'affichage en français, la virgule s'impose — les textes générés par le serveur l'emploient déjà, ce qui produirait une incohérence si les valeurs numériques restaient au format anglo-saxon.
