# Service d'inférence — fichiers de déploiement

---

## ✅ Contrat vérifié — 2026-07-30, **après correction**

> Relevé en interrogeant `https://bilanga-ml-587151bad5cb.herokuapp.com`, pas en lisant du
> code. **Les cinq défauts constatés le matin sont corrigés.** Le service répond sur tous
> les cas que le backend peut réellement produire.

| Cas | Avant | Après |
|---|:-:|:-:|
| `type_sol: "ARGILEUX"` — ce que Java envoie **toujours** | 🔴 400 | ✅ **200** |
| cinq mesures numériques à `null` | 🔴 422 | ✅ **200** |
| `type_sol: null` — parcelle sans sol déclaré | 🔴 422 | ✅ **200** |
| `culture: "TOMATE"` | 🔴 400 | ✅ **200** |
| trois clés V16 en plus | ✅ 200 | ✅ **200** |
| `/predict/vision-b64` · tomate **et** manioc | ✅ | ✅ **200 en ~1,3 s** |

### La dégradation de confiance fonctionne — c'est le point qui compte

| Mesures imputées | Probabilité brute | `confidence` rendue | Lecture côté Java |
|:-:|:-:|:-:|---|
| 0 | 0,6852 | **0,6852** | fiable — une alerte peut être levée |
| 1 (`type_sol`) | 0,6852 | **0,5824** | sous 0,60 ⇒ **non fiable** |
| 5 sur 8 | 0,4515 | **0,1806** | plancher à 0,4 × brut — franchement non fiable |

`allProbabilities` porte la probabilité **brute** du modèle, `confidence` la valeur
**retenue**. Le backend ne lit que la seconde, et c'est elle qui décide de `reliable`,
donc de la levée d'une alerte.

> **Une seule valeur imputée suffit à faire basculer sous le seuil** (0,6852 → 0,5824).
> C'est sévère, et défendable : le système préfère se taire plutôt qu'alerter sur une
> mesure qu'il a fabriquée. À surveiller néanmoins — si trop de diagnostics légitimes
> deviennent non fiables faute d'une seule sonde, c'est le coefficient de 0,15 qu'il faut
> revoir, **pas le seuil côté Java**.

### 🟡 Un point resté ouvert : `outOfRangeFeatures`

Le service signale désormais les mesures hors de la plage d'entraînement
(`outOfRangeFeatures: ["luminosite"]` sur une luminosité de 21 000). **Ce signalement ne
dégrade pas la confiance** — vérifié : sans imputation, `confidence` reste égale à la
probabilité brute.

C'est une décision à prendre, pas un défaut. Une valeur hors plage n'est pas une valeur
absente : le modèle a vu une mesure **réelle**, il l'extrapole simplement au-delà de ce
qu'il a appris. La pénaliser serait cohérent ; ne pas le faire l'est aussi, tant que
l'information remonte. À trancher si les extrapolations s'avèrent fausses en pratique.

### 🔴 Ce qui reste à faire, et qui n'est pas dans le fichier Python

**`BILANGA_ML_BASE_URL` sans barre finale.**

```bash
heroku config:set BILANGA_ML_BASE_URL=https://bilanga-ml-587151bad5cb.herokuapp.com
```

---

## Historique — les cinq défauts, et pourquoi ils comptaient

> Conservé parce que les raisons expliquent le correctif.

| Test | Résultat du matin |
|---|---|
| `POST /predict/soil`, `type_sol: "ARGILEUX"` | 🔴 **400** — *Valeur inconnue pour 'type_sol'* |
| `POST /predict/soil`, mesures à `null` | 🔴 **422** — *Input should be a valid number* |
| `POST /predict/soil`, `type_sol: null` | 🔴 **422** |
| `POST /predict/soil`, `culture: "TOMATE"` | 🔴 **400** |
| URL avec `/` final ⇒ `//predict/soil` | 🔴 **404** |

### 🔴 1. `type_sol` — le service attend des minuscules, Java envoie des MAJUSCULES

Ce n'est pas négociable côté Java, et ce n'est pas un choix de style :

```java
plot.setSoilType(DomainEnums.nameOf(request.getSoilType()));   // → .name() → MAJUSCULES
```
```sql
-- V11__constraints.sql
UPDATE plots SET soil_type = upper(trim(soil_type)) WHERE soil_type IS NOT NULL;
ALTER TABLE plots ADD CONSTRAINT chk_plots_soil_type
    CHECK (soil_type IS NULL OR soil_type IN ('ARGILEUX', 'LIMONEUX', 'SABLEUX'));
```

**La base de données ne PEUT PAS contenir de minuscules.** Une contrainte `CHECK` l'interdit
depuis la V11. Le correctif est donc nécessairement Python.

> **Conséquence si rien n'est fait : 100 % des diagnostics capteur échouent.** Pas une
> fraction — tous. Et l'échec se lit côté backend comme `ML_INDISPONIBLE`, c'est-à-dire
> comme une panne réseau, ce qui enverra chercher au mauvais endroit.

**Correctif** — normaliser à l'entrée, sans toucher aux encodeurs :

```python
def _normalise_categorical(column: str, value, encoder):
    """Le backend envoie type_sol en MAJUSCULES (contrainte CHECK de la V11) et
    culture en minuscules (forme de stockage). L'entraînement a pu employer une
    autre casse : on cherche la correspondance plutôt que d'exiger la nôtre."""
    known = list(encoder.classes_)
    raw = str(value)
    for candidate in (raw, raw.lower(), raw.upper(), raw.capitalize()):
        if candidate in known:
            return candidate
    raise HTTPException(400, f"Valeur inconnue pour '{column}' : {raw!r} "
                             f"(attendu : {' | '.join(known)})")
```

### 🔴 2. Les mesures absentes sont refusées en **422**

La validation Pydantic typant les champs en `float` **non optionnel**, un `null` est rejeté
avant même d'atteindre le modèle. C'est le même défaut qu'avant, remonté d'un cran : il
échouait à l'encodage, il échoue maintenant à la validation.

> **Or c'est le cas NORMAL.** `IngestReadingRequest` déclare toutes les métriques
> facultatives — seul `technicalId` est obligatoire. Un boîtier sans sonde de luminosité,
> ou dont une sonde est débranchée, produit exactement cette requête. Et le backend envoie
> **toujours les treize clés**, y compris à `null` : Jackson sérialise les valeurs nulles
> d'une `Map`.

**Correctif** — rendre les champs optionnels, imputer, et **dégrader la confiance** :

```python
from typing import Optional

class SoilPayload(BaseModel):
    temperature:  Optional[float] = None
    humidite_sol: Optional[float] = None
    humidite_air: Optional[float] = None
    ph:           Optional[float] = None
    azote:        Optional[float] = None
    phosphore:    Optional[float] = None
    potassium:    Optional[float] = None
    luminosite:   Optional[float] = None
    culture:      str
    type_sol:     Optional[str] = None

    model_config = {"extra": "ignore"}   # le backend envoie 3 clés de plus (V16)
```

Puis, avant la prédiction — reprendre `_coerce()` de `main.py`, et surtout :

```python
confidence = float(np.max(proba))
if imputed:
    confidence *= max(0.4, 1.0 - 0.15 * len(imputed))
```

> **C'est ce dernier point qui ferme la boucle.** Sous 0,60, `ConfidenceEvaluator` marque
> le diagnostic non fiable côté Java et **aucune alerte n'est levée**. Le système refuse de
> conseiller sur des chiffres qu'il a fabriqués, sans que personne ait à y penser. Imputer
> sans dégrader la confiance serait pire que de refuser : on obtiendrait un diagnostic faux
> présenté avec l'assurance d'un diagnostic juste.

### 🔴 3. `BILANGA_ML_BASE_URL` — **sans barre finale**

`MlHttpExchange` concatène : `baseUrl + "/predict/soil"`. Une barre finale produit
`//predict/soil`, et Starlette ne normalise pas les doubles barres — **404 vérifié**.

```bash
# ✅
heroku config:set BILANGA_ML_BASE_URL=https://bilanga-ml-587151bad5cb.herokuapp.com
# 🔴 404 sur chaque appel
heroku config:set BILANGA_ML_BASE_URL=https://bilanga-ml-587151bad5cb.herokuapp.com/
```

### ✅ Ce qui est bon, et qu'il ne faut pas toucher

- **`diseaseClass` en camelCase** et noms de classes bruts (`Tomato___Late_blight`) — le
  backend normalise le préfixe lui-même.
- **`allProbabilities` sur `/predict/soil`** : le backend l'ignore sans broncher. Vérifié
  par `MlContractTest` — le mapper de `MlHttpExchange` tolère les champs inconnus.
- **TFLite** : bon choix. La vision répond en 1,3 s à chaud, là où le chargement de
  TensorFlow complet aurait flirté avec la coupure à 30 s d'Heroku.

### 🔴 4. Parcelle sans type de sol — **422**

`plots.soil_type` est **nullable** : seul `name` est obligatoire à la création d'une
parcelle. Java envoie alors `"type_sol": null`, et la validation Pydantic le refuse.

### 🟡 5. `culture` est strict sur la casse, lui aussi

`"TOMATE"` → 400. Ce n'est **pas** un risque actif — Java envoie la forme de stockage,
en minuscules — mais les deux colonnes catégorielles partagent le même défaut, et une
seule normalisation les couvre toutes les deux.

---

## Le correctif complet

Remplace la validation et le corps de `/predict/soil`. Testé contre les cinq cas relevés
ci-dessus.

```python
from typing import Optional
from pydantic import BaseModel

FEATURES = ["temperature", "humidite_sol", "humidite_air", "ph",
            "azote", "phosphore", "potassium", "luminosite",
            "culture", "type_sol"]


class SoilPayload(BaseModel):
    """Tout est facultatif, DÉLIBÉRÉMENT.

    Le backend déclare toutes les métriques facultatives à l'ingestion : un
    boîtier qui ne porte pas toutes les sondes est le cas ORDINAIRE. Et
    `type_sol` est nullable en base — une parcelle n'a pas à déclarer son sol.

    extra=ignore : le backend envoie trois clés de plus depuis la V16
    (temperature_sol, pluviometrie, conductivite_electrique). Les refuser
    casserait tout ; les ignorer est le bon comportement tant que le modèle
    n'est pas réentraîné avec elles.
    """
    temperature:  Optional[float] = None
    humidite_sol: Optional[float] = None
    humidite_air: Optional[float] = None
    ph:           Optional[float] = None
    azote:        Optional[float] = None
    phosphore:    Optional[float] = None
    potassium:    Optional[float] = None
    luminosite:   Optional[float] = None
    culture:      Optional[str]   = None
    type_sol:     Optional[str]   = None

    model_config = {"extra": "ignore"}


# ⚠️ À REMPLACER par les médianes de VOTRE jeu d'entraînement : ce sont les
# seules valeurs qui ne déplacent pas la distribution. Imputer 0 serait pire
# que ne rien faire — un pH de 0 est une acidité extrême, et le modèle rendrait
# un diagnostic FAUX avec l'assurance d'un diagnostic juste.
NUMERIC_DEFAULTS = {
    "temperature": 26.0, "humidite_sol": 45.0, "humidite_air": 70.0,
    "ph": 6.5, "azote": 40.0, "phosphore": 20.0,
    "potassium": 30.0, "luminosite": 15000.0,
}
CATEGORICAL_DEFAULTS = {"culture": "tomate", "type_sol": "limoneux"}


def _match_category(value, encoder):
    """Rend une valeur CONNUE de l'encodeur, ou None.

    Le backend envoie `type_sol` en MAJUSCULES — la contrainte CHECK de la V11
    le lui impose — et `culture` en minuscules. Exiger notre casse ferait
    échouer 100 % des diagnostics capteur.
    """
    if value is None:
        return None
    known = list(encoder.classes_)
    raw = str(value).strip()
    for candidate in (raw, raw.lower(), raw.upper(), raw.capitalize()):
        if candidate in known:
            return candidate
    return None


@app.post("/predict/soil")
async def predict_soil(payload: SoilPayload):
    if not _soil:
        raise HTTPException(503, "Modèle tabulaire non chargé.")

    model, feat_enc = _soil["model"], _soil["feature_encoders"]
    target_enc = _soil["target_encoder"]

    data = payload.model_dump()
    row, imputed = {}, []

    for feature, default in NUMERIC_DEFAULTS.items():
        value = data.get(feature)
        if value is None:
            row[feature] = default
            imputed.append(feature)
        else:
            row[feature] = float(value)

    for feature in ("culture", "type_sol"):
        encoder = feat_enc[feature]
        matched = _match_category(data.get(feature), encoder)
        if matched is None:
            # Repli plutôt que refus : une valeur discutable vaut mieux qu'un
            # diagnostic perdu, et l'imputation dégrade déjà la confiance.
            matched = (_match_category(CATEGORICAL_DEFAULTS[feature], encoder)
                       or list(encoder.classes_)[0])
            imputed.append(feature)
        row[feature] = int(encoder.transform([matched])[0])

    df = pd.DataFrame([row])[FEATURES]

    pred = model.predict(df)[0]
    proba = model.predict_proba(df)[0]
    label = target_enc.inverse_transform([pred])[0]

    confidence = float(np.max(proba))

    # ⚠️ LE POINT QUI FERME LA BOUCLE. Sous 0,60, ConfidenceEvaluator marque le
    # diagnostic NON FIABLE côté Java et AUCUNE alerte n'est levée. Imputer sans
    # dégrader la confiance serait pire que refuser : on obtiendrait un
    # diagnostic faux, présenté avec l'assurance d'un diagnostic juste.
    if imputed:
        confidence *= max(0.4, 1.0 - 0.15 * len(imputed))

    return {
        "category": str(label),
        "confidence": confidence,
        "imputedFeatures": imputed,   # ignoré par le backend, utile à l'humain
    }
```

### Rejouer ces tests après correction

```bash
B=https://bilanga-ml-587151bad5cb.herokuapp.com

# doit passer de 400 à 200
curl -s -X POST "$B/predict/soil" -H 'Content-Type: application/json' -d '{
 "temperature":28.4,"humidite_sol":41.2,"humidite_air":78.0,"ph":6.4,
 "azote":42.0,"phosphore":18.0,"potassium":30.0,"luminosite":21000.0,
 "culture":"tomate","type_sol":"ARGILEUX",
 "temperature_sol":24.1,"pluviometrie":0.0,"conductivite_electrique":1.2}'

# doit passer de 422 à 200, avec une confidence NETTEMENT plus basse
curl -s -X POST "$B/predict/soil" -H 'Content-Type: application/json' -d '{
 "temperature":28.4,"humidite_sol":null,"humidite_air":null,"ph":6.4,
 "azote":null,"phosphore":null,"potassium":30.0,"luminosite":null,
 "culture":"tomate","type_sol":"ARGILEUX"}'
```

---

> Ce dossier **ne fait pas partie du backend Java**. C'est le service Python/FastAPI,
> déployé séparément, que `BILANGA_ML_BASE_URL` désigne.
>
> Il est versionné ici pour que le contrat des deux côtés vive au même endroit — les DTO
> `SoilPrediction` / `VisionPrediction` et ce fichier doivent bouger ensemble.

## Ce qu'il faut copier

| Fichier | Destination |
|---|---|
| `main.py` | remplace le vôtre |
| `Procfile` | racine du projet Python |
| `requirements.txt` | racine |
| `runtime.txt` | racine |

L'arborescence `models/` reste inchangée :

```
models/
├── cassava/  cassava_final.keras · classes.json
├── tomato/   tomato_final.keras  · classes.json
└── tabular/  soil_diagnosis_model.pkl · label_encoders.pkl · target_encoder.pkl
```

## Les trois corrections

### 🔴 A — `/predict/soil` tombait sur toute mesure absente

Le garde `if f not in payload` ne protégeait de rien : le backend envoie **toujours** les
treize clés, y compris avec la valeur `null` — Jackson sérialise les valeurs nulles d'une
`Map`. La clé était présente, le contrôle passait, l'échec survenait à l'encodage
(`NaN` → *Input contains NaN*, ou `"None"` → *unseen labels*). Un 500 fait perdre le
**diagnostic entier** côté Java.

Or c'est le cas **normal** : `IngestReadingRequest` déclare toutes les métriques
facultatives. Un boîtier sans sonde de luminosité faisait tomber le service à chaque
relevé.

`_coerce()` impute désormais, et **`confidence` est dégradée proportionnellement**. Sous
0,60, le backend marque le diagnostic non fiable et ne lève aucune alerte — le bon
comportement quand la moitié des valeurs ont été fabriquées.

> ⚠️ **Remplacez `NUMERIC_DEFAULTS` par les médianes de votre jeu d'entraînement.** Les
> valeurs livrées sont plausibles, pas mesurées : ce sont les seules du fichier qui
> demandent votre connaissance des données.

### 🟠 B — la casse de `type_sol`

Java envoie `type_sol` en **majuscules** (`ARGILEUX`) et `culture` en **minuscules**
(`tomate`). Si l'entraînement a employé une autre casse, chaque appel levait *unseen
labels*.

`_encode()` cherche la correspondance parmi `encoder.classes_` en essayant les casses, et
le démarrage **journalise les valeurs connues** de chaque encodeur — de quoi vérifier en
dix secondes plutôt que de le découvrir en production.

### 🟠 C — les deux modèles vision chargés au démarrage

Deux contraintes Heroku se cumulaient : le port doit être ouvert en **60 s** (sinon `R10`),
et un dyno Eco / Basic / Standard-1X offre **512 Mo** dont TensorFlow consomme déjà 300 à
400 (`R14` au-delà).

Désormais : le **tabulaire au démarrage** (léger, sollicité à chaque relevé), la **vision à
la demande**, un seul modèle en mémoire — le second chasse le premier.

> ⚠️ Le premier appel vision paie le chargement. Le délai côté Java est de 30 s, et Heroku
> coupe à 30 s sans que ce soit configurable. Si le chargement d'EfficientNet dépasse ce
> budget, il faudra soit un dyno plus grand, soit un autre hébergeur (§5.3 de
> `../DEPLOIEMENT_HEROKU.md`).

## Ce qui n'a pas changé

**Le contrat.** Mêmes routes, mêmes noms de champs, `diseaseClass` en camelCase, noms de
classes bruts (`Tomato___Late_blight`). **Rien à modifier côté Java.**

`/predict/soil` rend un champ `imputedFeatures` en plus — Jackson l'ignore en silence,
`SoilPrediction` ne lit que `category` et `confidence`. Il sert au diagnostic humain.

## Vérifier avant de pousser

```bash
uvicorn main:app --port 8000
curl localhost:8000/health

# le cas qui cassait : la moitié des mesures à null
curl -X POST localhost:8000/predict/soil -H 'Content-Type: application/json' -d '{
  "temperature": 28.4, "humidite_sol": null, "humidite_air": null, "ph": 6.4,
  "azote": null, "phosphore": null, "potassium": 30.0, "luminosite": null,
  "culture": "tomate", "type_sol": "ARGILEUX"
}'
# attendu : 200, et une confidence NETTEMENT dégradée
```
