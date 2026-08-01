# Trois démonstrations de bout en bout

> **Établi le 2026-08-01**, contre la production peuplée.
> Complète `SOUTENANCE_FICHE.md` (le *quoi* et le *pourquoi* de chaque fonction) et
> `SOUTENANCE_QUESTIONS.md` (les questions du jury et leurs réponses).
>
> Ici : **ce qu'on fait devant le jury**, dans l'ordre, avec les appels exacts, les
> réponses attendues, ce qu'on dit pendant que ça tourne, et ce qu'on fait si ça casse.

---

## Sommaire

| § | Démonstration | Durée | Ce qu'elle prouve |
|---|---|:---:|---|
| [0](#0-avant-dentrer-dans-la-salle) | Préparation | 10 min la veille | |
| [1](#1-la-chaîne-nominale--de-la-mesure-au-conseil-justifié) | **La chaîne nominale** | 7 min | Le système analyse, conclut, conseille, et sait dire pourquoi |
| [2](#2-quand-la-donnée-est-mauvaise--le-refus-de-conseiller-faux) | **La donnée mauvaise** | 6 min | Il distingue une mesure fausse d'une situation grave, et ne perd jamais le relevé |
| [3](#3-la-boucle-qui-se-referme--conseil--action--effet--marge) | **La boucle fermée** | 7 min | Il évalue ses propres conseils avec ses propres données |
| [4](#4-le-plan-de-repli) | Plan de repli | | Ce qu'on fait si le réseau, l'inférence ou l'hébergement lâchent |

**Vingt minutes au total.** Les trois s'enchaînent et racontent une seule histoire :
le système conclut, il refuse de conclure quand il ne peut pas, et il vérifie après coup
ce que ses conclusions ont produit.

---

## 0. Avant d'entrer dans la salle

### 0.1 Ce qui doit être déployé

> ⚠️ **Trois correctifs livrés le 2026-08-01 changent ce que le jury verra.** S'ils ne
> sont pas déployés, trois choses cassent en public.

| Correctif | Sans lui, en démonstration |
|---|---|
| `SqlTemporal` | les points de courbe sortent **sans date**, et le client affiche le 1ᵉʳ janvier 1970 |
| `uptakeSummary` | `/plots/{id}/economics` répond **500**, et la démonstration 3 s'arrête net |
| `DiseaseLabeller` | le diagnostic s'affiche « Tomato_Yellow_Leaf_Curl_Virus » à côté de conseils en français |

Commit à déployer : **`8d45c03`**. Vérification en trois requêtes, à faire la veille :

```bash
# 1. les courbes portent une date
curl -s "$API/plots/7279573782111453184/history?granularity=DAY" -H "$AUTH" | grep -o '"bucket":"[^"]*"' | head -3

# 2. le bilan économique répond
curl -s -o /dev/null -w "%{http_code}\n" "$API/plots/7279573782111453184/economics" -H "$AUTH"   # attendu : 200

# 3. les maladies sont en français
curl -s "$API/diagnosis?plotId=7279573782111453184&size=1" -H "$AUTH" | grep -o '"resultLabel":"[^"]*"'
```

### 0.2 Les constantes à avoir sous la main

```bash
HOST=https://bilanga-c65c6649bf37.herokuapp.com
API=$HOST/sni/api/v1
KEY="X-Device-Key: bilanga-demo-device-key-soutenance-2026"

# jeton (à refaire le matin même, il expire en 24 h)
TOKEN=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
        -d '{"email":"admin@bilanga.cg","password":"Bilanga@Prod2026"}' \
        | python -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
```

| Parcelle | Identifiant | Code | Particularité | Culture en cours |
|---|---|---|---|---|
| **Nord-Est** | `7279573782111453184` | PARC-2026-000002 | goutte-à-goutte, géolocalisée | tomate Roma, `FRUCTIFICATION` |
| **Nord** | `7279572997105516544` | PARC-2026-000001 | **pluviale** | tomate Marmande, `FLORAISON` |
| **Sud** | `7279837295486328832` | PARC-2026-000003 | à 1,3 km de Nord-Est | tomate Roma, `CROISSANCE` |
| **Est** | `7279837302478233600` | PARC-2026-000004 | **sans coordonnées** | manioc Mvuazi, `TUBERISATION` |

| Boîtier | Parcelle | État |
|---|---|---|
| `ESP32-PROD-01` | Nord-Est | sain, c'est celui qu'on pilote |
| `ESP32-NE-02` | Nord-Est | sain, sert de **témoin** à la comparaison entre voisins |
| `ESP32-N-01` | Nord | sain |
| `ESP32-S-01` | Sud | sain |
| `ESP32-E-01` | Est | sain |
| `ESP32-E-02` | Est | **`DEFAILLANTE`**, valeur figée |

### 0.3 Réveiller l'inférence, dix minutes avant

Le microservice d'inférence est hébergé sur un dyno qui s'endort. Le premier appel après
une inactivité met une trentaine de secondes et **échoue**. Le relevé est conservé, c'est
l'invariant du projet, mais le diagnostic est sauté et la démonstration démarre sur un
`skipReason`.

```bash
curl -s -X POST "$API/ingest/readings" -H "Content-Type: application/json" -H "$KEY" \
  -d '{"technicalId":"ESP32-PROD-01","temperature":27,"humiditeSol":45,"humiditeAir":70,
       "ph":6.4,"azote":42,"phosphore":19,"potassium":32,"luminosite":14000}'
```

Recommencez jusqu'à obtenir `"diagnosed": true`. **Deux ou trois fois suffisent.**

---

## 1. La chaîne nominale : de la mesure au conseil justifié

> **Ce qu'on prouve.** Une mesure entre, une conclusion sort, et chaque conseil peut être
> ramené au chiffre qui l'a déclenché. C'est ce qui sépare un système exploitable d'un
> classifieur branché sur une base.

**Durée : 7 minutes.** Cinq étapes, chacune une requête.

### Étape 1.1 : la mesure entre

Le boîtier s'authentifie par une clé partagée, jamais par un jeton : un microcontrôleur
n'a ni la mémoire ni l'horloge pour gérer un cycle de vie de jeton.

```bash
curl -s -X POST "$API/ingest/readings" -H "Content-Type: application/json" -H "$KEY" -d '{
  "technicalId": "ESP32-PROD-01",
  "temperature": 30.4, "temperatureSol": 25.8,
  "humiditeSol": 19.5, "humiditeAir": 92.0,
  "ph": 6.05, "azote": 36.0, "phosphore": 16.5, "potassium": 26.0,
  "luminosite": 21000, "pluviometrie": 0, "conductiviteElectrique": 1.5,
  "signalStrength": -68
}'
```

**Réponse attendue, en HTTP 201 :**

```jsonc
{
  "readingId": "…", "plotId": "7279573782111453184", "plotName": "Parcelle Nord-Est",
  "anomalyDetected": false, "anomalousMeasures": [],
  "sensorHealth": "SAINE", "sensorHealthReason": null,
  "diagnosed": true, "diagnosis": "STRESS_HYDRIQUE", "skipReason": null,
  "recommendationCount": 13,
  "recordedAt": "…"
}
```

> **Ce que vous dites pendant que ça tourne.**
> « Le boîtier n'envoie qu'un identifiant matériel et des mesures. Il ignore tout de la
> parcelle : c'est le serveur qui la résout. Un boîtier déplacé d'une parcelle à l'autre
> n'a rien à reprogrammer.
>
> Ce qui vient de se passer tient en cinq temps : le relevé a été contrôlé pour sa
> plausibilité **matérielle**, la sonde a été jugée saine sur une fenêtre de douze heures,
> le relevé a été **écrit en base avant toute opération faillible**, puis le diagnostic a
> été lancé. Treize conseils en sont sortis. »

**Le point à ne pas laisser passer :** `anomalyDetected` et `sensorHealth` sont deux
contrôles **distincts**, et cette distinction est le cœur du travail. Le premier juge la
**mesure**, le second juge la **sonde**. Une sonde en panne renvoie rarement une valeur
absurde : elle se fige, elle dérive, elle décroche de ses voisines, en restant tout du
long dans des valeurs parfaitement crédibles.

### Étape 1.2 : ce que le système a conclu, et avec quelle assurance

```bash
curl -s "$API/diagnosis?plotId=7279573782111453184&size=1" -H "$AUTH"
```

Reprenez l'identifiant du diagnostic, puis :

```bash
curl -s "$API/diagnosis/$DIAG" -H "$AUTH"
```

À montrer à l'écran, dans cet ordre :

| Champ | Ce qu'on en dit |
|---|---|
| `result` / **`resultLabel`** | le code du modèle, **et son nom français**. Le modèle rend « Late_blight », l'exploitant lit « Mildiou de la tomate » |
| `confidenceScore`, `confidenceLevel` | la certitude du modèle, jamais la fiabilité de la mesure |
| **`reliable`** | en deçà de 0,60, **le diagnostic ne lève aucune alerte** |
| `risks[]` | les conditions d'apparition réunies, calculées **sur les seules mesures** |
| `trends[]` | la projection de franchissement de seuil, avec son `rSquared` |
| `indicators` | VPD et déséquilibre NPK, calculés et non mesurés |

> **La phrase qui porte la thèse.**
> « Deux voies indépendantes concluent ici. Le modèle statistique regarde des mesures de
> sol ; le moteur agronomique déterministe compare ces mêmes mesures aux exigences de la
> tomate à ce stade précis. Elles n'ont aucune information en commun. Quand elles
> concordent, la conclusion tient sur deux pieds. Quand elles divergent, le système le
> dit au lieu de trancher. »

### Étape 1.3 : les treize conseils, et pourquoi ils sont dans cet ordre

Les conseils arrivent **déjà triés**. Faites défiler et commentez les types :

```
ARBITRAGE    HAUTE     Les besoins en eau et la lutte contre la maladie foliaire se
                       concilient par la technique d'arrosage : irriguez au pied,
                       jamais par aspersion, tôt le matin…
BASE         HAUTE     Arracher les plants atteints. Lutter contre les aleurodes…
AGRONOMIQUE  HAUTE     Humidité du sol : 30,0 % ⟶ seuil bas de la culture tomate :
                       70,0 % (déficit de 40,0 %). Irriguer la parcelle…
AGRONOMIQUE  HAUTE     Potassium : 30,0 ⟶ seuil bas : 50,0 (déficit de 20,0)…
AGRONOMIQUE  MOYENNE   Déséquilibre nutritif : l'azote est 2,2 fois mieux pourvu que
                       le potassium au regard des besoins de la culture…
VOISINAGE    MOYENNE   RISQUE MALADIE détecté sur 2 parcelles voisines à 1,3 km sur
                       tomate. Aucun symptôme n'a été relevé sur votre parcelle :
                       c'est une alerte de proximité, non un diagnostic…
```

> **Les trois choses à dire, et rien de plus.**
>
> **1. L'arbitrage est en tête, et ce n'est pas un conseil de plus.** « Réduire l'humidité
> pour contenir une maladie foliaire » et « irriguer pour lever un stress hydrique »
> paraissent se contredire. L'une vise l'air, l'autre vise le sol : la contradiction n'est
> qu'apparente. Le moteur **ajoute** la synthèse qu'un agronome formulerait, et ne retire
> jamais les deux conseils qu'il concilie. Effacer un conseil ferait disparaître le
> problème avec lui.
>
> **2. Le conseil de voisinage n'est observable nulle part sur cette parcelle**, et c'est
> exactement son intérêt. Il est préventif. Le texte le dit lui-même : « aucun symptôme
> n'a été relevé sur votre parcelle ». Sans cette phrase, l'exploitant chercherait
> l'erreur dans ses sondes.
>
> **3. Chaque conseil agronomique porte son chiffre.** « 30,0 % contre un seuil de
> 70,0 % ». Ce n'est pas de la mise en forme : ces valeurs sont **stockées** avec le
> conseil, et c'est ce qui permet l'étape suivante.

### Étape 1.4 : pourquoi ce conseil, et pas un autre

```bash
curl -s "$API/diagnosis/$DIAG/explain" -H "$AUTH"
```

```jsonc
{
  "result": "Late_blight", "resultLabel": "Mildiou de la tomate",
  "measures": { "temperature": 30.4, "humidite_air": 92.0, "humidite_sol": 19.5, "ph": 6.05 },
  "comparison": [ {
    "diseaseCode": "Early_blight", "displayName": "Alternariose (brûlure précoce)",
    "sharedConditions": ["température entre 18 et 28 °C"],
    "distinguishingConditions": ["humidité de l'air > 85 %"],
    "statement": "Mildiou retenu plutôt qu'Alternariose : les deux maladies partagent…"
  } ],
  "recommendations": [ {
    "measureField": "humidite_sol", "observedValue": 19.5, "thresholdValue": 70.0,
    "deviation": -50.5,
    "rationale": "Déclenché parce que l'humidité du sol vaut 19,50, soit en deçà du seuil de 70,00 (écart de 50,50)."
  } ]
}
```

> **Le point de la démonstration 1.**
> « Rien n'est recalculé ici. Cette justification est reconstruite depuis les colonnes de
> traçabilité enregistrées **au moment où le conseil a été émis**. Recalculer donnerait la
> justification d'aujourd'hui, et les deux divergeraient dès qu'un agronome ajuste un
> seuil. On saurait alors ce que le système *dirait*, jamais ce qu'il *a dit*. »

### Étape 1.5 : l'alerte, et son destinataire

```bash
curl -s "$API/alerts?plotId=7279573782111453184&openOnly=true" -H "$AUTH"
```

Trois choses à commenter :

- **`category`** sépare deux publics. `AGRONOMIQUE` va à l'exploitant, `TECHNIQUE` va au
  technicien. Mêlées, chacun apprend à ignorer celles de l'autre, y compris les siennes.
- **La signature** est `<source>:<culture>:<résultat>`. Une alerte porte sur une
  *situation*, pas sur un relevé. Deux cents relevés successifs aboutissant au même
  constat ne produisent **qu'une** alerte.
- **`escalationCount`** compte les reconstats sans acquittement. Au-delà de trois,
  l'alerte monte d'un niveau toute seule : une alerte ignorée qui reste au même rang finit
  par se confondre avec le bruit de fond.

**Si le jury demande à voir le courriel**, montrez `GET /notifications/preferences` :
`availableChannels` énumère ce que le serveur sait réellement envoyer. Le canal n'est
proposé que s'il est configuré, jamais « au cas où ».

---

## 2. Quand la donnée est mauvaise : le refus de conseiller faux

> **Ce qu'on prouve.** Le système distingue une **mesure fausse** d'une **situation
> grave**, refuse de conseiller quand il sait la mesure douteuse, et ne perd jamais le
> relevé. C'est la partie du travail qui traite le seul angle mort capable de produire un
> conseil **nuisible**.

**Durée : 6 minutes.** Trois cas, joués en direct.

### Cas 2.1 : la sonde qui se fige

Une sonde qui tombe en panne ne renvoie presque jamais une valeur absurde. Elle se fige
sur sa dernière lecture pendant que l'électrode s'encrasse.

Envoyez **six relevés strictement identiques**, espacés d'une heure, sur `ESP32-E-01` :

```bash
for h in 6 5 4 3 2 1; do
  WHEN=$(python -c "import datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(hours=$h)).replace(microsecond=0).isoformat().replace('+00:00','Z'))")
  curl -s -X POST "$API/ingest/readings" -H "Content-Type: application/json" -H "$KEY" -d "{
    \"technicalId\":\"ESP32-E-01\",\"recordedAt\":\"$WHEN\",
    \"temperature\":26.0,\"temperatureSol\":27.0,\"humiditeSol\":38.0,\"humiditeAir\":60.0,
    \"ph\":5.5,\"azote\":14.0,\"phosphore\":9.0,\"potassium\":23.0,\"luminosite\":11000}"
  echo
done
```

**Ce que le sixième renvoie :**

```jsonc
{
  "sensorHealth": "DEFAILLANTE",
  "sensorHealthReason": "Valeur figée sur 6 relevés consécutifs pour l'humidité du sol, l'humidité de l'air, la température de l'air…",
  "diagnosed": false,
  "skipReason": "SONDE_DEFAILLANTE",
  "message": "Diagnostic suspendu : Valeur figée sur 6 relevés consécutifs…"
}
```

> **Les trois décisions à défendre, si on vous les demande.**
>
> **Égalité exacte, pas un intervalle.** Une mesure physique réelle varie toujours au
> moins sur sa dernière décimale. Deux relevés identiques arrivent ; six d'affilée ne sont
> plus un phénomène naturel.
>
> **Une valeur figée sur une valeur plausible.** Regardez les chiffres : 38 % d'humidité,
> pH 5,5. Rien d'anormal. C'est précisément le cas dangereux, et la raison d'être de cette
> règle : un contrôle de plausibilité ne l'attraperait jamais.
>
> **Le diagnostic est suspendu, le relevé est gardé.** Mieux vaut ne rien conseiller que
> conseiller à partir d'une mesure dont on sait qu'elle est fausse. Mais le relevé reste :
> c'est la trace de la panne, et c'est lui qui permettra de dater le début de la dérive.

**L'alerte produite est `TECHNIQUE`, de niveau `ELEVEE` et non `CRITIQUE` :**

```bash
curl -s "$API/alerts?category=TECHNIQUE&openOnly=true" -H "$AUTH"
```

> « La parcelle n'est pas en danger, c'est la surveillance qui l'est. Réserver le niveau
> critique aux situations qui menacent la culture préserve son sens. »

### Cas 2.2 : la sonde revient, l'alerte se referme seule

Un seul relevé aux valeurs variables suffit :

```bash
curl -s -X POST "$API/ingest/readings" -H "Content-Type: application/json" -H "$KEY" \
  -d '{"technicalId":"ESP32-E-01","temperature":26.4,"temperatureSol":27.3,
       "humiditeSol":39.2,"humiditeAir":61.5,"ph":5.47,"azote":14.6,
       "phosphore":9.3,"potassium":23.4,"luminosite":11800}'
```

Puis relisez les alertes techniques : elle porte maintenant
`resolutionReason: "AUTO_SITUATION_NORMALISEE"`.

> « Sans cette fermeture automatique, une sonde remplacée laisserait un signalement que
> plus rien ne justifie. Le technicien apprendrait à ignorer une liste qui ne se vide
> jamais, et ce jour-là il ignorerait aussi la vraie panne. »

### Cas 2.3 : le service d'inférence est muet

**Ne coupez rien en direct.** Montrez le contrat, il est plus convaincant qu'une panne
mise en scène.

```bash
curl -s "$API/ingest/health?technicalId=ESP32-PROD-01" -H "$KEY"
```

| `skipReason` | Sens | Le relevé |
|---|---|:---:|
| `ML_INDISPONIBLE` | service d'analyse injoignable | **conservé** |
| `CONTEXTE_ABSENT` | aucune culture déclarée sur la parcelle | **conservé** |
| `SONDE_DEFAILLANTE` | la sonde est jugée hors service | **conservé** |
| `CONDITIONS_STABLES` | régulateur : rien n'a bougé depuis le dernier diagnostic | **conservé** |

> **La phrase à retenir.**
> « Perdre un diagnostic parce qu'un service tiers est muet est acceptable : il se
> recalculera. Perdre une mesure ne l'est pas : elle est irremplaçable, l'instant est
> passé. C'est pour cela que la persistance a lieu **avant** toute opération faillible,
> et non après. »

**Le cas dégradé de la parcelle Est**, à montrer en dix secondes : elle n'a **pas de
coordonnées**. Ni météo, ni voisinage. Le diagnostic sort quand même, et les vues
concernées disent pourquoi elles sont vides plutôt que de faire silence. Une capacité
indisponible retire une capacité, elle ne casse rien.

---

## 3. La boucle qui se referme : conseil → action → effet → marge

> **Ce qu'on prouve.** Le système ne se contente pas de conseiller : il enregistre ce qui
> a été fait, mesure ce que cela a produit, et le rapporte au rendement. **C'est
> l'argument qui distingue ce travail d'une intégration de classifieur.**

**Durée : 7 minutes.**

### Étape 3.1 : un conseil devient une action

Prenez un conseil `ACTIVE` de priorité `HAUTE` sur Nord-Est :

```bash
curl -s "$API/recommendations?plotId=7279573782111453184&status=ACTIVE&sort=priority,asc&size=3" -H "$AUTH"
```

> **Au passage, un détail qui compte.** Le tri sur `priority` est **sémantique**, pas
> alphabétique. `?sort=priority,asc` rend `HAUTE, MOYENNE, BASSE`. Un tri alphabétique
> rendrait `BASSE` en premier, et la première page ne serait pas la plus urgente.

Déclarez l'intervention, en la rattachant au conseil :

```bash
curl -s -X POST "$API/interventions" -H "Content-Type: application/json" -H "$AUTH" -d "{
  \"plotId\": \"7279573782111453184\",
  \"recommendationId\": \"$RECO\",
  \"type\": \"IRRIGATION\",
  \"cost\": 22000,
  \"weatherNote\": \"Ciel couvert, sol ressuyé\",
  \"note\": \"Arrosage au pied, tôt le matin, comme l'arbitrage le demandait.\"
}"
```

Relisez le conseil : il est passé en **`APPLIQUEE`** tout seul.

> « Le rattachement ferme la boucle. On ne demande pas deux fois la même information à
> l'exploitant : déclarer l'action marque le conseil comme suivi. Et c'est ce lien qui
> rend le taux de suivi calculable. »

**Seuls `plotId` et `type` sont obligatoires**, et c'est délibéré : une saisie faite le
soir, de mémoire, ne doit pas être refusée parce que le dosage exact a été oublié. Une
intervention consignée approximativement vaut infiniment mieux qu'une intervention non
consignée.

### Étape 3.2 : le système évalue son propre conseil

```bash
curl -s "$API/interventions/$INTERVENTION/effect" -H "$AUTH"
```

```jsonc
{
  "type": "IRRIGATION", "windowHours": 48,
  "targetMeasure": "humidite_sol", "targetMeasureLabel": "l'humidité du sol",
  "beforeSampleCount": 18, "afterSampleCount": 21,
  "beforeAverage": 24.1, "afterAverage": 43.8,
  "change": 19.7, "changePercent": 81.74,
  "verdict": "AMELIORATION", "verdictLabel": "Effet conforme à l'attendu",
  "statement": "L'humidité du sol est passée de 24,1 à 43,8 dans les 48 h qui ont suivi…",
  "limitation": "Cet écart constate une évolution, il n'établit pas une cause. Une pluie, un changement de température ou une autre intervention survenus dans la même fenêtre produiraient le même chiffre."
}
```

> **Quatre décisions, si on vous interroge.**
>
> **Pourquoi 48 heures.** Assez pour lisser le cycle jour/nuit, sans quoi une irrigation
> faite le matin serait comparée à un après-midi et l'écart mesurerait la météo. Assez
> court pour que l'effet de l'intervention domine encore.
>
> **Pourquoi le sens dépend du type.** Une irrigation doit faire *monter* l'humidité, un
> drainage la faire *baisser*. `InterventionType` porte la mesure cible et le sens
> attendu.
>
> **Pourquoi un traitement rend `INDETERMINE`.** Produire un écart d'humidité pour un
> fongicide donnerait un chiffre sans rapport, **avec l'apparence de la rigueur**. C'est
> pire que de ne rien dire.
>
> **Pourquoi un seuil de bruit à 5 %.** Une sonde d'humidité varie de quelques pour cent
> sans que rien ne se soit passé. Qualifier cela d'amélioration décrédibiliserait tous les
> autres verdicts.

> ⚠️ **`limitation` est toujours renseigné, et doit toujours être affiché.** Une
> comparaison avant/après n'établit jamais une causalité. **Dites-le avant que le jury ne
> le demande** : c'est ce qui transforme une faiblesse en maîtrise.

### Étape 3.3 : du conseil au chiffre

```bash
curl -s "$API/plots/7279573782111453184/economics" -H "$AUTH"
```

**Valeurs relevées en production le 2026-08-01**, après déploiement du correctif :

```jsonc
{
  "harvestCount": 2, "totalQuantity": 860.0, "quantityUnit": "kg",
  "grossRevenue": 460800.00, "currency": "XAF",
  "interventionCount": 2, "totalCost": 53000.00,
  "costByInterventionType": { "Fertilisation": 31000.00, "Irrigation": 22000.00 },
  "margin": 407800.00, "plantedArea": 0.8,
  "marginPerHectare": 509750.00, "yieldPerHectare": 1075.0,
  "costRatio": 11.5,
  "recommendationCount": 981, "appliedRecommendationCount": 7, "uptakeRate": 0.71,
  "limitation": "Le taux de suivi des conseils et le rendement sont présentés côte à côte : c'est un constat, pas une démonstration…",
  "missingData": []
}
```

> ⚠️ **Un chiffre à commenter avant que le jury ne le relève :** le taux de suivi est de
> **0,71 %**, sept conseils appliqués sur neuf cent quatre-vingt-un. C'est l'effet du jeu
> de démonstration, où douze jours de relevés ont produit des centaines de diagnostics
> sans que personne ne saisisse les actions correspondantes.
>
> **Dites-le vous-même, et servez-vous-en** : « ce chiffre mesure exactement ce qu'il
> annonce, la part des conseils dont on sait qu'ils ont été suivis. Il est bas parce que
> la saisie des interventions est le maillon humain de la chaîne, et c'est précisément ce
> que le système rend visible au lieu de le supposer. »

> **Trois points, dans cet ordre.**
>
> **Rien n'est stocké.** Tous ces totaux sont recalculés à la demande. Un total mis en
> cache diverge dès la première correction de saisie, et personne ne sait plus lequel des
> deux chiffres croire.
>
> **`missingData` explique les vides.** Une récolte sans prix est comptée pour zéro **et
> signalée**. L'ignorer donnerait une marge fausse que rien ne distinguerait d'une marge
> juste.
>
> **`limitation` est une constante, toujours renvoyée.** Le rapprochement « conseils
> suivis / rendement » est descriptif, jamais causal. Le sol, la variété, la météo et
> l'attention portée à la parcelle varient ensemble, et une exploitation ne fournit pas
> l'échantillon qui permettrait de les démêler. **Le dire est plus solide que de le
> taire.**

### Étape 3.4 : la campagne dans son histoire

Deux requêtes pour clore, chacune commentée en une phrase :

```bash
curl -s "$API/plots/7279573782111453184/timeline?size=20" -H "$AUTH"
curl -s "$API/crops/7279612518246408192/calendar" -H "$AUTH"
```

- **La chronologie** fusionne sept sources en un seul flux daté : relevés marquants,
  diagnostics, alertes levées **et** résolues, observations, changements de stade,
  interventions, récoltes. Les changements de stade n'y sont enregistrés nulle part : ils
  sont **reconstitués** depuis la date de plantation, parce que c'est une fonction
  déterministe du temps.
  Si `truncated` vaut vrai, dites-le : la vue annonce elle-même qu'elle est incomplète
  plutôt que de laisser croire à l'exhaustivité.

- **Le calendrier prévisionnel** est **la seule vue du système qui annonce au lieu de
  constater**. Tout le reste est réactif par construction : une mesure, un symptôme, un
  écart. Ici : « floraison attendue dans neuf jours, prévoyez le traitement préventif ».
  Son `limitation` doit être affiché à côté des dates : ce sont des projections sur des
  proportions de cycle indicatives.

---

## 4. Le plan de repli

### 4.1 Si l'inférence ne répond pas

**Ne vous excusez pas, servez-vous-en.** C'est un cas prévu et documenté :

> « Le service d'analyse ne répond pas. Regardez ce que le système en fait : le relevé est
> enregistré, `skipReason` vaut `ML_INDISPONIBLE`, et rien n'est perdu. C'est exactement
> le comportement prévu, et c'est plus intéressant à montrer que le cas nominal. »

Puis enchaînez sur la démonstration 2, qui ne dépend pas de l'inférence, et sur la
démonstration 3, qui n'en dépend pas non plus.

### 4.2 Si l'hébergement est lent

Le dyno met une trentaine de secondes à se réveiller. **Lancez une requête à blanc
pendant que vous présentez l'architecture**, avant d'avoir besoin du résultat.

### 4.3 Si le réseau est coupé

Ayez les réponses JSON des trois démonstrations **capturées dans un fichier**, la veille.
Montrez-les en annonçant que ce sont des captures. Un jury pardonne un réseau, jamais une
capture présentée comme du direct.

### 4.4 Ce qu'il ne faut pas tenter en direct

| Ne pas faire | Pourquoi |
|---|---|
| Le diagnostic par image | Il dépend d'un modèle de vision et d'un envoi de fichier. Deux points de rupture pour une démonstration que la chaîne capteur fait déjà |
| Activer le cloisonnement pendant la soutenance | `ownership.enabled` change le comportement de toutes les routes. À basculer avant, ou pas du tout |
| Modifier un seuil et attendre l'effet | Les tables de connaissance sont en cache, jusqu'à trente minutes. Utilisez `/diagnosis/{id}/replay`, qui répond immédiatement |

---

## 5. La limite à annoncer d'entrée

**Dites-le vous-même, dans les deux premières minutes.**

> « Les seuils agronomiques semés à l'installation sont **indicatifs**. Ils viennent de
> valeurs de référence générales, et n'ont pas été validés par une source agronomique
> congolaise. Le système est construit pour qu'ils soient **corrigés par API sans
> redéploiement**, et `/diagnosis/{id}/replay` permet de voir ce qu'un seuil modifié aurait
> changé sur un relevé passé. La validation par un agronome est la première étape d'une
> mise en service réelle. »

Découverte à l'oral, cette limite coûte cher. Annoncée avec la méthode de correction
qu'on propose, elle devient une limite maîtrisée, ce qui est tout autre chose.

---

## 6. Aide-mémoire, une page

```
AVANT             réveiller l'inférence (2-3 relevés), obtenir le jeton, vérifier
                  bucket ≠ null, /economics = 200, resultLabel présent

DÉMO 1   7 min    POST /ingest/readings              → 201, diagnosed, 13 conseils
                  GET  /diagnosis?plotId=…&size=1    → resultLabel, reliable, risks
                  GET  /diagnosis/{id}               → conseils déjà triés
                  GET  /diagnosis/{id}/explain       → rationale chiffré
                  GET  /alerts?openOnly=true         → catégorie, signature, escalade

DÉMO 2   6 min    6 × POST /ingest/readings identiques → DEFAILLANTE, SONDE_DEFAILLANTE
                  GET  /alerts?category=TECHNIQUE      → ELEVEE, pas CRITIQUE
                  1 × POST relevé variable             → AUTO_SITUATION_NORMALISEE

DÉMO 3   7 min    GET  /recommendations?status=ACTIVE  → tri sémantique
                  POST /interventions {recommendationId} → conseil APPLIQUEE
                  GET  /interventions/{id}/effect      → verdict + limitation
                  GET  /plots/{id}/economics          → marge, taux de suivi, limitation
                  GET  /plots/{id}/timeline           → sept sources fusionnées
                  GET  /crops/{id}/calendar           → la seule vue qui annonce

CLÔTURE           « les seuils sont indicatifs, corrigeables par API, et le rejeu
                  permet de mesurer ce qu'une correction change »
```
