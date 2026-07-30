# Service d'inférence — fichiers de déploiement

---

## ⚠️ Constats sur le service DÉPLOYÉ — 2026-07-30

> Relevés en interrogeant `https://bilanga-ml-587151bad5cb.herokuapp.com`, pas en lisant du
> code. **Le service est en ligne et répond**, les modèles TFLite sont chargés, et le
> contrat de réponse est bon.
>
> **Mais dans son état actuel il ne peut servir aucun diagnostic capteur réel.** Deux
> défauts, tous deux dans la requête, tous deux corrigeables côté Python seulement.

| Test | Résultat |
|---|---|
| `GET /health` | ✅ 200 · `visionModels: [manioc, tomate]` · `soilLoaded: true` |
| `POST /predict/vision-b64` | ✅ 200 en 1,3 s · `diseaseClass` en camelCase · noms de classes bruts |
| `POST /predict/soil`, `type_sol: "argileux"` | ✅ 200 · `{category, confidence, allProbabilities}` |
| `POST /predict/soil`, `type_sol: "ARGILEUX"` | 🔴 **400** — *Valeur inconnue pour 'type_sol'* |
| `POST /predict/soil`, mesures à `null` | 🔴 **422** — *Input should be a valid number* |
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
