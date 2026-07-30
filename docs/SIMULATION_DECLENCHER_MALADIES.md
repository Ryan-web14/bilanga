# Quelles valeurs envoyer pour déclencher chaque situation

> Extrait des migrations **V3** (seuils agronomiques) et **V6** (conditions d'apparition
> des maladies), pas de mémoire. Ce sont les valeurs que le moteur applique réellement.
>
> Pour le format de la requête, voir `docs/INGESTION_IOT.md`.

---

## Ce que le simulateur peut, et ne peut pas, déclencher

| | Déclenchable par les mesures ? |
|---|---|
| **Risques de maladie** (`RiskEngine`) | ✅ oui — c'est l'objet de ce document |
| Écarts agronomiques (`AgronomicEngine`) | ✅ oui |
| Tendances (`TrendAnalyzer`) | ✅ oui, avec une **série** |
| Santé de sonde | ✅ oui, en répétant |
| **Maladie diagnostiquée** (`VisionClient`) | ❌ **non** — il faut une **photo** |

> ⚠️ **La distinction la plus importante du document.** Une sonde ne voit pas une maladie.
> Elle mesure les conditions dans lesquelles une maladie **peut apparaître**.
>
> Le `RiskEngine` répond à « les conditions d'apparition sont-elles réunies ? » — une alerte
> **précoce**, avant tout symptôme. Le `result` d'un diagnostic capteur reste une catégorie
> agronomique (`STRESS_HYDRIQUE`, `RISQUE_MALADIE`…), jamais un nom de maladie.
>
> Pour obtenir `"result": "Late_blight"`, il faut passer par `POST /diagnosis/image/predict`
> avec une photo. **C'est ce qui fait la valeur de la corroboration** : les deux voies sont
> indépendantes, et c'est leur accord qui vaut quelque chose.

---

## Comment le score de risque se calcule

```
score = poids des conditions SATISFAITES / poids TOTAL
```

| Score | Niveau | Effet |
|---|---|---|
| ≥ 0,85 | **ELEVE** | conseil `RISQUE` de priorité haute |
| ≥ 0,60 | `MODERE` | conseil produit |
| < 0,60 | `FAIBLE` | **non signalé** |

**Une condition dont la mesure est absente est ignorée** — elle ne compte ni au numérateur
ni au dénominateur. Envoyer `humiditeAir: null` sur une maladie qui en dépend fait donc
monter le score des autres conditions, ce qui n'est pas le comportement recherché : envoyez
toutes les mesures citées.

> En pratique, **satisfaire toutes les conditions** est le seul moyen fiable d'atteindre
> `ELEVE`.

---

## 🍅 TOMATE

### Mildiou — `Late_blight`

| Condition | Poids |
|---|:-:|
| `humiditeAir` **> 80** | 0,5 |
| `temperature` **entre 15 et 25** | 0,5 |

```json
{ "technicalId": "WOKWI-01", "temperature": 20.0, "humiditeAir": 88.0,
  "humiditeSol": 65.0, "ph": 6.4, "azote": 40.0, "phosphore": 20.0,
  "potassium": 40.0, "luminosite": 12000.0 }
```
→ score **1,0** · `ELEVE`

> Le plus démontrable des cinq : deux conditions, toutes deux faciles à tenir, et c'est la
> maladie la plus connue de la tomate.

### Alternariose — `Early_blight`

| Condition | Poids |
|---|:-:|
| `temperature` **> 24** | 0,5 |
| `humiditeAir` **> 75** | 0,5 |

```json
{ "temperature": 27.0, "humiditeAir": 82.0, "humiditeSol": 65.0,
  "ph": 6.4, "azote": 40.0, "phosphore": 20.0, "potassium": 40.0 }
```
→ score **1,0** · `ELEVE`

⚠️ **Ces valeurs déclenchent aussi le mildiou à 0,5** (humidité > 80 satisfaite, température
hors 15–25). C'est voulu : plusieurs maladies partagent des conditions, et c'est exactement
ce que `ComparativeExplainer` sert à départager sur la chaîne image.

### Cladosporiose — `Leaf_Mold`

| Condition | Poids |
|---|:-:|
| `humiditeAir` **> 85** | **0,7** |
| `temperature` **entre 20 et 25** | 0,3 |

```json
{ "temperature": 22.0, "humiditeAir": 90.0, "humiditeSol": 65.0,
  "ph": 6.4, "azote": 40.0, "phosphore": 20.0, "potassium": 40.0 }
```
→ score **1,0** · `ELEVE`

> **Les poids sont inégaux, délibérément.** L'humidité est le facteur déterminant : à elle
> seule (0,7) elle place déjà le risque en `MODERE`. Une bonne illustration du fait que le
> moteur pondère au lieu de compter.

### TYLCV — `Tomato_Yellow_Leaf_Curl_Virus`

| Condition | Poids |
|---|:-:|
| `temperature` **> 28** | **0,7** |
| `humiditeAir` **< 70** | 0,3 |

```json
{ "temperature": 32.0, "humiditeAir": 55.0, "humiditeSol": 65.0,
  "ph": 6.4, "azote": 40.0, "phosphore": 20.0, "potassium": 40.0 }
```
→ score **1,0** · `ELEVE`

> **Le cas contre-intuitif, et le plus intéressant à montrer** : ici c'est l'air **sec** qui
> aggrave. Le virus est transmis par l'aleurode, dont l'activité suit la chaleur et la
> sécheresse. Un système qui n'associerait « maladie » qu'à « humidité » manquerait
> entièrement celui-ci.

---

## 🥔 MANIOC

### Bactériose — `bacterial_blight`

| Condition | Poids |
|---|:-:|
| `humiditeAir` **> 80** | 0,5 |
| `temperature` **> 28** | 0,5 |

```json
{ "temperature": 31.0, "humiditeAir": 86.0, "humiditeSol": 55.0,
  "ph": 6.0, "azote": 25.0, "phosphore": 15.0, "potassium": 30.0 }
```
→ score **1,0** · `ELEVE`

### Mosaïque africaine — `mosaic_disease`

| Condition | Poids |
|---|:-:|
| `temperature` **> 30** | **0,7** |
| `humiditeAir` **< 75** | 0,3 |

```json
{ "temperature": 34.0, "humiditeAir": 60.0, "humiditeSol": 55.0,
  "ph": 6.0, "azote": 25.0, "phosphore": 15.0, "potassium": 30.0 }
```
→ score **1,0** · `ELEVE`

### Striure brune — `brown_streak_disease`

| Condition | Poids |
|---|:-:|
| `temperature` **> 27** | **1,0** |

```json
{ "temperature": 31.0, "humiditeAir": 70.0, "humiditeSol": 55.0,
  "ph": 6.0, "azote": 25.0, "phosphore": 15.0, "potassium": 30.0 }
```
→ score **1,0** · `ELEVE`

> **Condition unique** : toute température supérieure à 27 °C place cette maladie en risque
> élevé. C'est agronomiquement défendable — la striure brune est la menace majeure du manioc
> en Afrique centrale — mais cela signifie qu'elle sera signalée **très souvent** au Congo.
> Un jury peut le relever ; c'est un bon exemple de seuil à faire valider.

---

## Les catégories agronomiques

Elles viennent d'`AgronomicEngine`, qui compare aux plages de `crop_requirement` (V3),
affinées par stade (V10).

### Seuils de référence

| | pH | Humidité sol | Température | N | P | K |
|---|---|---|---|---|---|---|
| **tomate** | 6,0 – 6,8 | **60 – 80 %** | 20 – 30 °C | ≥ 35 | ≥ 18 | ≥ 35 |
| **manioc** | 5,5 – 6,5 | **40 – 70 %** | 22 – 32 °C | ≥ 20 | ≥ 12 | ≥ 25 |

⚠️ **Ces plages sont celles de la culture en général.** Le stade en cours les infléchit
(V10) : en fructification, la tomate exige davantage d'eau. `GET /crops/{id}/thresholds`
donne les seuils **effectifs**, avec l'origine de chaque valeur.

### Ce qu'il faut envoyer

| Catégorie visée | Tomate | Manioc |
|---|---|---|
| `STRESS_HYDRIQUE` | `humiditeSol: 25` | `humiditeSol: 20` |
| `EXCES_EAU` | `humiditeSol: 92` | `humiditeSol: 88` |
| `SOL_ACIDE` | `ph: 4.8` | `ph: 4.5` |
| `SOL_ALCALIN` | `ph: 8.0` | `ph: 7.8` |
| `CARENCES_NUTRITIVES` | `azote: 10, phosphore: 5, potassium: 12` | `azote: 6, phosphore: 4, potassium: 8` |

**Le stress hydrique est le meilleur choix pour une démonstration** : c'est le plus lisible,
il produit un conseil de priorité `HAUTE`, donc une **alerte**, et sur une parcelle
`PLUVIAL` il déclenche la reformulation par `IrrigationAdapter`.

---

## Le jeu de valeurs à retenir pour la soutenance

Un seul relevé qui déclenche **plusieurs moteurs à la fois** — c'est ce qui donne une
réponse riche plutôt qu'un conseil isolé.

```json
{
  "technicalId": "WOKWI-01",
  "temperature": 22.0,
  "temperatureSol": 21.0,
  "humiditeSol": 24.0,
  "humiditeAir": 88.0,
  "ph": 6.4,
  "azote": 12.0,
  "phosphore": 20.0,
  "potassium": 40.0,
  "luminosite": 14000.0,
  "pluviometrie": 0.0,
  "conductiviteElectrique": 1.1
}
```

Sur une **tomate**, ce relevé produit d'un coup :

| Moteur | Ce qu'il signale |
|---|---|
| `AgronomicEngine` | `STRESS_HYDRIQUE` — 24 % contre 60 % attendus |
| `AgronomicEngine` | `CARENCES_NUTRITIVES` — azote à 12 contre 35 |
| `RiskEngine` | **mildiou à 1,0** — humidité 88 > 80, température 22 dans 15–25 |
| `RiskEngine` | cladosporiose à 0,7 — humidité > 85 |
| `ConflictArbitrator` | une **synthèse** : « réduire l'humidité » vs « irriguer » |

> **C'est l'arbitrage qui vaut la démonstration.** « Réduire l'humidité pour contenir une
> maladie foliaire » et « irriguer pour lever un stress hydrique » paraissent
> contradictoires. L'une vise l'**air**, l'autre le **sol** : la contradiction n'est
> qu'apparente. Le moteur ajoute la synthèse qu'un agronome formulerait, **sans retirer**
> les deux conseils qu'elle concilie.

---

## Quatre pièges au déclenchement

**1. Le régulateur écarte les diagnostics identiques.** Intervalle de 5 min **et** aucune
variation ⇒ `CONDITIONS_STABLES`. Pour enchaîner deux démonstrations, **changez les
valeurs**, ne rejouez pas le même relevé.

**2. Aucune culture en cours ⇒ rien ne se déclenche.** `CONTEXTE_ABSENT` : le relevé est
enregistré, mais le moteur ne sait pas ce qui pousse, donc à quels seuils comparer.

**3. Le manioc tolère la sécheresse.** `tolerance_secheresse: 0.6` atténue la sévérité
calculée — un stress hydrique y sera signalé plus tard, et plus faiblement, que sur une
tomate. C'est voulu, et c'est agronomiquement juste.

**4. Six relevés identiques inhibent tout.** `sensorHealth: DEFAILLANTE` et le diagnostic
est suspendu. Si vous répétez un relevé pour insister, **variez la dernière décimale**.

---

## Ce que ces valeurs ne prouvent pas

Elles déclenchent les moteurs, elles ne valident pas les seuils.

Les valeurs de V3, V6, V7 et V10 sont **indicatives** et n'ont pas été validées par une
source agronomique congolaise — le commentaire de la V10 le dit explicitement. Une
démonstration réussie montre que la **mécanique** est juste : que le bon moteur se déclenche
au bon moment, que les conseils contradictoires sont conciliés, que la traçabilité permet de
justifier chaque conseil.

**Elle ne montre pas que 60 % d'humidité est le bon seuil pour une tomate.** Cette question
appartient à un agronome, et il vaut mieux le dire soi-même.
