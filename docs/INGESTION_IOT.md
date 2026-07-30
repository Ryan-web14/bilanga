# Envoyer des relevés à Bilanga — note pour le module IoT

> Tout ce qu'il faut pour qu'un boîtier ou un simulateur dépose une mesure.
> Établi le 2026-07-30, vérifié contre l'instance en ligne.

---

## En une minute

```http
POST https://bilanga-c65c6649bf37.herokuapp.com/sni/api/v1/ingest/readings
Content-Type: application/json
X-Device-Key: bilanga-demo-device-key-soutenance-2026

{ "technicalId": "WOKWI-01", "temperature": 31.5, "humiditeSol": 19.0, "ph": 6.4 }
```

→ **201**, sans enveloppe. Un seul champ obligatoire : `technicalId`.

**Pas de jeton JWT.** L'ingestion s'authentifie par clé partagée : un microcontrôleur n'a
ni la mémoire ni l'horloge pour gérer un cycle de vie de token. C'est une authentification
distincte, pas une absence d'authentification.

---

## Le corps

**Toutes les mesures sont facultatives.** Une sonde débranchée : retirez la ligne, le
relevé passe quand même.

```jsonc
{
  "technicalId": "WOKWI-01",        // ← SEUL champ obligatoire

  "temperature":            31.5,   // °C — AIR, pas le sol
  "temperatureSol":         27.0,   // °C
  "humiditeSol":            19.0,   // %
  "humiditeAir":            44.0,   // %
  "ph":                      6.4,
  "azote":                  24.0,   // mg/kg
  "phosphore":              16.0,
  "potassium":              28.0,
  "luminosite":          22000.0,   // lux
  "pluviometrie":            0.0,   // mm
  "conductiviteElectrique":  1.1,

  "batteryLevel":     91,           // %
  "batteryVoltage":  3.9,           // V
  "signalStrength":  -68,           // dBm, donc négatif
  "firmwareVersion": "wokwi-1.0",
  "recordedAt":      null           // voir « rejeu après coupure »
}
```

### ⚠️ Trois pièges

**1. camelCase, pas snake_case.** `humiditeSol`, `temperatureSol`,
`conductiviteElectrique`. Un `humidite_sol` serait ignoré en silence — pas d'erreur, la
mesure disparaît simplement.

**2. `temperature` est l'AIR.** Le nom ne le dit pas, l'usage si : c'est elle que les
moteurs comparent aux seuils de la culture. `temperatureSol` est une mesure distincte.

**3. L'URL n'a pas de barre finale** avant `/sni`.

### Les huit qui décident du diagnostic

`temperature`, `humiditeSol`, `humiditeAir`, `ph`, `azote`, `phosphore`, `potassium`,
`luminosite`. Les autres enrichissent le relevé sans entrer dans la prédiction.

Une valeur absente n'échoue pas : elle est imputée, **et la confiance est dégradée de 15 %
par mesure manquante**. Au-delà de trois, le diagnostic passe sous le seuil de fiabilité et
**aucune alerte n'est levée** — le système refuse de conseiller sur des chiffres qu'il a
fabriqués.

### Bornes de plausibilité

Hors bornes, le relevé est **accepté** mais marqué `anomalyDetected: true`. Il n'est jamais
rejeté : c'est la trace de la panne.

| Mesure | Bornes |
|---|---|
| `ph` | 0 – 14 |
| `humiditeSol`, `humiditeAir` | 0 – 100 |
| `temperature` (air) | −20 – 70 |
| `temperatureSol` | −10 – 60 |
| `pluviometrie` | 0 – 500 |
| N, P, K, luminosité, conductivité | ≥ 0 |

---

## La réponse

```jsonc
{
  "readingId": "7279576271738470400",
  "plotId": "7279573782111453184",
  "plotName": "Parcelle Nord",
  "anomalyDetected": false,
  "anomalousMeasures": [],
  "sensorHealth": "SAINE",
  "diagnosed": true,
  "diagnosis": "STRESS_HYDRIQUE",
  "skipReason": null,
  "recommendationCount": 3,
  "recordedAt": "2026-07-30T19:05:00Z"
}
```

### `skipReason` — le relevé est **toujours** enregistré

| Valeur | Sens | Action |
|---|---|---|
| `null` | diagnostic produit | — |
| `CONDITIONS_STABLES` | rien n'a bougé depuis 5 min | **normal**, ne rien faire |
| `CONTEXTE_ABSENT` | la parcelle n'a **aucune culture en cours** | en déclarer une |
| `ML_INDISPONIBLE` | service d'inférence injoignable | réessayer plus tard |
| `SONDE_DEFAILLANTE` | sonde jugée hors service, diagnostic **inhibé** | changer la sonde |

> **`CONDITIONS_STABLES` n'est pas un échec.** Un régulateur écarte le diagnostic quand
> l'intervalle minimal (5 min) n'est pas écoulé *et* qu'aucune mesure n'a bougé. Avec une
> émission toutes les 60 s, la plupart des relevés le porteront. C'est le comportement
> attendu.

### Codes d'erreur

| Code | Cause |
|---|---|
| **400** | `technicalId` absent — il est obligatoire, la validation passe avant tout le reste |
| **401** | clé invalide ou en-tête absent |
| **404** | aucune parcelle n'existe pour accueillir le boîtier |
| **503** | ingestion non configurée côté serveur |

---

## Le boîtier n'a pas à être enregistré

Un `technicalId` inconnu **crée** le boîtier à son premier relevé et le rattache à une
parcelle. Vous pouvez donc lancer autant de simulations que vous voulez, avec des
identifiants différents, sans rien enregistrer à la main.

**Un seul prérequis : au moins une parcelle doit exister.** Sinon 404 — un relevé doit se
rattacher quelque part, et inventer une parcelle serait fabriquer une donnée métier à
partir d'un paquet réseau.

⚠️ **Un identifiant par simulation.** Deux simulations partageant le même `technicalId` se
marchent dessus, et la détection de sonde figée voit des valeurs incohérentes venir du
« même » appareil.

---

## Sonder avant d'émettre

```http
GET /sni/api/v1/ingest/health?technicalId=WOKWI-01
```
```json
{
  "status": "UP",
  "serverTime": "2026-07-30T19:13:12Z",
  "deviceKnown": true,
  "ingestReady": true,
  "deviceKeyRequired": true
}
```

- **`serverTime`** — calez l'horloge avant de tamponner des relevés hors ligne.
- **`ingestReady: false`** ⇒ tout `POST` répondra 503. Inutile d'émettre.
- **`deviceKeyRequired`** — savoir s'il faut joindre l'en-tête, sans essuyer un 401 pour
  l'apprendre. Brancher `addHeader` dessus rend le firmware indifférent au réglage serveur.
- **`technicalId` en paramètre** signale que le boîtier est vivant **même sans mesure à
  déposer** — sondes débranchées, cycle de veille. Sans lui, il serait compté parmi les
  muets et quelqu'un chercherait une panne de communication inexistante.

---

## Rejeu après coupure

Un boîtier revenu après trois jours hors ligne :

```http
POST /sni/api/v1/ingest/readings/batch
{ "readings": [ … ] }          — 200 relevés maximum
```

**Le lot n'est pas atomique** : un relevé corrompu ne fait pas perdre les autres.

```jsonc
{
  "received": 42, "accepted": 41, "rejected": 1, "diagnosed": 6,
  "results": [ /* un résultat par relevé accepté */ ],
  "failures": [ { "index": 17, "technicalId": "…", "errorCode": "…", "message": "…" } ]
}
```

`failures[].index` désigne la position dans le tableau envoyé — le boîtier sait exactement
quoi ne pas réémettre.

> ⚠️ **Renseignez `recordedAt`** en ISO-8601 UTC sur chaque relevé rejoué. Sans lui, toute
> la série s'écrase sur l'instant de reconnexion, et l'analyse de tendance — qui projette
> un franchissement de seuil par régression — devient fausse.

---

## Sans clé, si besoin

Ce n'est pas un choix du boîtier mais un réglage serveur :

```bash
heroku config:set BILANGA_INGEST_REQUIRE_DEVICE_KEY=false
```

L'en-tête peut alors être omis. **`technicalId` reste obligatoire.**

> ⚠️ N'importe qui peut alors déposer des relevés sur n'importe quelle parcelle. Une mesure
> fabriquée n'est pas inerte : elle déclenche un diagnostic, produit des recommandations,
> peut lever une alerte, et fausse l'analyse de tendance durablement. Utile pendant
> l'intégration ; à remettre à `true` ensuite.

---

## Deux montages qui valent la démonstration

**La sonde figée.** Émettez six relevés **strictement identiques** — pas de bruit, pas une
décimale de différence. Au sixième : `sensorHealth: DEFAILLANTE`, une alerte
`category: TECHNIQUE` est levée, et le diagnostic est **inhibé**. Envoyez ensuite un relevé
normal : l'alerte **se referme toute seule**.

> Une mesure physique réelle varie toujours au moins sur sa dernière décimale. Six valeurs
> rigoureusement identiques ne sont plus un phénomène naturel — et une sonde en panne
> renvoie rarement une valeur absurde, elle se fige en restant parfaitement crédible.

**Deux boîtiers voisins.** Lancez `WOKWI-01` et `WOKWI-02` sur la même parcelle, et faussez
franchement l'humidité du sol sur l'une. Le système compare chaque boîtier à la **médiane**
de ses voisins — pas à la moyenne, qui serait tirée par le fautif — et détecte le
décrochage.

> Sans voisin, une dérive lente est indiscernable d'une évolution réelle du sol. Deux
> boîtiers valent donc bien mieux qu'un pour montrer ce contrôle.

---

## Croquis prêt à l'emploi

`docs/wokwi/bilanga-esp32.ino` — ESP32, aucun composant à câbler, valeurs déjà calées pour
produire un stress hydrique franc (donc des recommandations à montrer).

Trois constantes en tête de fichier :

| | Défaut | Effet |
|---|---|---|
| `TECHNICAL_ID` | `WOKWI-01` | **un par simulation** |
| `JITTER` | `0.03` | bruit relatif. **À `0`, la sonde figée se déclenche au 6ᵉ relevé** |
| `WINDOW_MS` | 60 s | sous 5 min, plus de relevés mais pas plus de diagnostics |

⚠️ Uvicorn/ESP32 : `client.setInsecure()` lève la vérification du certificat — acceptable
en simulation, à remplacer par `setCACert(...)` sur du matériel réel.

---

## Questions fréquentes

**« J'ai un 404 alors que mon boîtier existe. »** Aucune parcelle n'existe côté serveur.

**« `diagnosed` est toujours `false` avec `CONTEXTE_ABSENT`. »** La parcelle n'a pas de
culture `EN_COURS`. Le relevé est enregistré, mais rien ne peut être diagnostiqué sans
savoir ce qui pousse.

**« Le premier appel met 30 secondes. »** Le service dormait. C'est le démarrage du
conteneur, pas une lenteur de l'API — un appel de réveil régulier est en place côté
serveur.

**« Mes mesures n'apparaissent pas dans le diagnostic. »** Vérifiez la casse des noms de
champs : `humidite_sol` est ignoré, `humiditeSol` est lu.
