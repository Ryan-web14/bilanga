# Service d'inférence — fichiers de déploiement

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
