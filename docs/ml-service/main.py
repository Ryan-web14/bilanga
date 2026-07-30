"""
BILANGA — service d'inférence.

⚠️ CE FICHIER PRÉCÈDE LE PASSAGE À TFLITE. Il charge les modèles avec
`tf.keras.models.load_model`, ce qui n'est plus la manière dont le service
déployé fonctionne — et TFLite est le meilleur choix : la vision répond en
1,3 s à chaud, là où TensorFlow complet aurait flirté avec la coupure à 30 s
d'Heroku.

NE REPRENEZ PAS le chargement des modèles d'ici. Reprenez-en `_coerce()` et
`_encode()` : ce sont eux qui traitent les deux défauts encore présents en
production, constatés le 2026-07-30 et détaillés dans README.md.

---

Version corrigée du service fourni le 2026-07-30. Trois défauts traités, et un
seul cassait le cas nominal.

  A. /predict/soil échouait sur toute mesure absente.  🔴 bloquant
  B. la casse de `type_sol` pouvait ne pas correspondre aux encodeurs.
  C. les deux modèles vision étaient chargés au démarrage — intenable sur un
     petit dyno, en mémoire comme en temps de boot.

Le CONTRAT avec le backend Java est inchangé : mêmes routes, mêmes noms de
champs. Rien à modifier côté Java.
"""

import base64
import io
import json
import logging
import os
from contextlib import asynccontextmanager

import joblib
import numpy as np
import pandas as pd
import tensorflow as tf
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from PIL import Image

log = logging.getLogger("bilanga.ml")
logging.basicConfig(level=logging.INFO)

# ============================================================
# Configuration
# ============================================================
MODELS_DIR = os.getenv("MODELS_DIR", "models")
IMG_SIZE = (224, 224)          # taille d'entrée EfficientNet-B0

# cassava = manioc, tomato = tomate
_SUBDIR = {"manioc": "cassava", "tomate": "tomato"}

_vision_models = {}            # {"tomate": (model, classes)} — UN SEUL À LA FOIS
_soil = {}                     # {"model", "feature_encoders", "target_encoder"}


# ============================================================
# Chargement
#
# ⚠️ C. Seul le TABULAIRE est chargé au démarrage : quelques mégaoctets, et il
# sert à chaque relevé. Les modèles vision sont chargés À LA DEMANDE.
#
# Pourquoi. Sur un dyno Heroku, deux contraintes indépendantes se cumulent :
#   · le processus doit écouter sur $PORT en 60 s, sinon il est tué (R10) ;
#   · un dyno Eco / Basic / Standard-1X offre 512 Mo, dont TensorFlow consomme
#     déjà 300 à 400 (R14 au-delà).
# Deux EfficientNet-B0 chargés avant l'ouverture du port échouent sur les deux.
# ============================================================
def _load_vision(crop: str):
    sub = _SUBDIR[crop]
    model = tf.keras.models.load_model(os.path.join(MODELS_DIR, sub, f"{sub}_final.keras"))
    with open(os.path.join(MODELS_DIR, sub, "classes.json"), "r", encoding="utf-8") as f:
        classes = json.load(f)

    # Un seul modèle vision en mémoire : le second chasse le premier.
    # Les deux ensemble ne tiennent pas dans 512 Mo.
    _vision_models.clear()
    _vision_models[crop] = (model, classes)
    log.info("modèle vision « %s » chargé (%d classes)", crop, len(classes))
    return _vision_models[crop]


def _get_vision(crop: str):
    if crop not in _vision_models:
        return _load_vision(crop)
    return _vision_models[crop]


def _load_soil():
    base = os.path.join(MODELS_DIR, "tabular")
    _soil["model"] = joblib.load(os.path.join(base, "soil_diagnosis_model.pkl"))
    _soil["feature_encoders"] = joblib.load(os.path.join(base, "label_encoders.pkl"))
    _soil["target_encoder"] = joblib.load(os.path.join(base, "target_encoder.pkl"))
    log.info("modèle tabulaire chargé · features attendues : %s",
             list(_soil["model"].feature_names_in_))

    # ⚠️ B. Trace les valeurs connues des encodeurs catégoriels.
    #
    # Le backend Java envoie `type_sol` EN MAJUSCULES (« ARGILEUX ») et
    # `culture` en minuscules (« tomate »). Si l'entraînement a employé une
    # autre casse, chaque appel lèverait « y contains previously unseen
    # labels » — un 500, donc un diagnostic perdu, sur TOUS les relevés.
    # _encode() ci-dessous s'en accommode ; ce journal permet de le vérifier
    # au démarrage plutôt que de le découvrir en production.
    for column, encoder in _soil["feature_encoders"].items():
        log.info("encodeur « %s » · valeurs connues : %s", column, list(encoder.classes_))


@asynccontextmanager
async def lifespan(_: FastAPI):
    # `@app.on_event("startup")` est déprécié dans les versions récentes de
    # FastAPI ; `lifespan` est son remplaçant.
    _load_soil()
    yield
    _vision_models.clear()


app = FastAPI(title="BILANGA ML Service", version="1.1", lifespan=lifespan)


# ============================================================
# Vision
# ============================================================
def _prepare_image(raw: bytes) -> np.ndarray:
    """Charge, redimensionne, rend un tableau brut (0-255).

    Pas de normalisation manuelle : EfficientNet la fait en interne.
    """
    img = Image.open(io.BytesIO(raw)).convert("RGB").resize(IMG_SIZE)
    return np.expand_dims(np.array(img, dtype=np.float32), axis=0)


def _predict_vision(crop: str, raw: bytes) -> dict:
    crop = str(crop or "").lower().strip()
    if crop not in _SUBDIR:
        raise HTTPException(400, f"Culture inconnue : {crop} (attendu : manioc | tomate)")

    model, classes = _get_vision(crop)
    probs = model.predict(_prepare_image(raw), verbose=0)[0]
    idx = int(np.argmax(probs))

    # Noms de champs en camelCase : ce sont ceux des DTO Java (VisionPrediction).
    # Un `disease_class` en snake_case donnerait un diseaseClass NUL côté Java,
    # donc un diagnostic sans maladie — sans erreur visible nulle part.
    #
    # Les noms de classes sont rendus BRUTS (« Tomato___Late_blight ») :
    # normalizeDiseaseCode retire le préfixe côté Java.
    return {
        "crop": crop,
        "diseaseClass": classes[idx],
        "confidence": float(probs[idx]),
        "allProbabilities": {classes[i]: float(probs[i]) for i in range(len(classes))},
    }


@app.post("/predict/vision-b64")
async def predict_vision_b64(payload: dict):
    """Route appelée par le backend Java (VisionClientImpl)."""
    encoded = payload.get("imageBase64")
    if not encoded:
        raise HTTPException(400, "Champ manquant : imageBase64")
    try:
        raw = base64.b64decode(encoded)
    except Exception as exc:
        raise HTTPException(400, f"imageBase64 illisible : {exc}") from exc

    return _predict_vision(payload.get("crop"), raw)


@app.post("/predict/vision")
async def predict_vision(crop: str = Form(...), file: UploadFile = File(...)):
    """Variante multipart. Non employée par le backend — pratique pour tester."""
    return _predict_vision(crop, await file.read())


# ============================================================
# Tabulaire
# ============================================================
FEATURES = ["temperature", "humidite_sol", "humidite_air", "ph",
            "azote", "phosphore", "potassium", "luminosite",
            "culture", "type_sol"]

CATEGORICAL = {"culture", "type_sol"}

# ⚠️ A. Valeurs de repli, à REMPLACER par les médianes de votre jeu
# d'entraînement — ce sont les seules qui ne déplacent pas la distribution.
#
# Imputer 0 serait pire que ne rien faire : un pH de 0 est une acidité extrême,
# une humidité de 0 un sol mort. Le modèle produirait un diagnostic FAUX avec
# l'assurance d'un diagnostic juste.
NUMERIC_DEFAULTS = {
    "temperature": 26.0,
    "humidite_sol": 45.0,
    "humidite_air": 70.0,
    "ph": 6.5,
    "azote": 40.0,
    "phosphore": 20.0,
    "potassium": 30.0,
    "luminosite": 15000.0,
}
CATEGORICAL_DEFAULTS = {"culture": "tomate", "type_sol": "LIMONEUX"}


def _coerce(payload: dict) -> tuple[dict, list[str]]:
    """Complète les mesures absentes, et dit lesquelles.

    ⚠️ A. LE DÉFAUT CORRIGÉ. La version précédente refusait la requête si une
    clé manquait. Le contrôle ne protégeait de rien : le backend Java envoie
    TOUJOURS les treize clés, y compris avec la valeur `null` — Jackson
    sérialise les valeurs nulles d'une Map. La clé était donc présente, le
    contrôle passait, et l'échec survenait à l'encodage :

      · numérique  → NaN   → « Input contains NaN »              → 500
      · catégoriel → "None" → « previously unseen labels »        → 500

    Or un 500 fait perdre le DIAGNOSTIC ENTIER côté Java, pas seulement la
    prédiction — et les mesures absentes sont le cas NORMAL : le backend
    déclare toutes les métriques facultatives, seul `technicalId` est requis.
    Un boîtier dépourvu de sonde de luminosité faisait tomber le service à
    chaque relevé.
    """
    row, imputed = {}, []

    for feature in FEATURES:
        value = payload.get(feature)
        missing = value is None or (isinstance(value, str) and not value.strip())

        if missing:
            defaults = CATEGORICAL_DEFAULTS if feature in CATEGORICAL else NUMERIC_DEFAULTS
            row[feature] = defaults[feature]
            imputed.append(feature)
        else:
            row[feature] = value

    return row, imputed


def _encode(column: str, value, encoder):
    """Encode une valeur catégorielle en tolérant la casse.

    ⚠️ B. Le backend envoie `type_sol` en MAJUSCULES et `culture` en
    minuscules. Si l'entraînement a employé l'autre casse, un
    `encoder.transform` direct lèverait sur chaque appel. On cherche donc la
    correspondance parmi les valeurs connues, puis on se rabat sur la première
    d'entre elles — un repli discutable vaut mieux qu'un diagnostic perdu, et
    le journal de démarrage montre les valeurs attendues.
    """
    known = list(encoder.classes_)
    raw = str(value)

    for candidate in (raw, raw.upper(), raw.lower(), raw.capitalize()):
        if candidate in known:
            return int(encoder.transform([candidate])[0]), candidate != raw

    log.warning("valeur inconnue pour « %s » : %r (connues : %s) — repli sur %r",
                column, raw, known, known[0])
    return int(encoder.transform([known[0]])[0]), True


@app.post("/predict/soil")
async def predict_soil(payload: dict):
    if not _soil:
        raise HTTPException(503, "Modèle tabulaire non chargé.")

    model = _soil["model"]
    feature_encoders = _soil["feature_encoders"]
    target_encoder = _soil["target_encoder"]

    row, imputed = _coerce(payload)
    substituted = []

    for column, encoder in feature_encoders.items():
        if column in row:
            row[column], changed = _encode(column, row[column], encoder)
            if changed:
                substituted.append(column)

    df = pd.DataFrame([row])[FEATURES]

    pred = model.predict(df)[0]
    proba = model.predict_proba(df)[0]
    label = target_encoder.inverse_transform([pred])[0]

    confidence = float(np.max(proba))

    # ⚠️ LE POINT QUI COMPTE. Une prédiction fondée sur des valeurs inventées ne
    # mérite pas la confiance d'une prédiction complète — et côté Java c'est
    # `confidence` qui décide de `reliable`, donc de la levée d'une alerte.
    #
    # Sous 0,60, ConfidenceEvaluator marque le diagnostic NON FIABLE et aucune
    # alerte n'est levée. C'est exactement le comportement voulu quand la moitié
    # des mesures ont été imputées : mieux vaut ne rien conseiller que
    # conseiller sur des chiffres qu'on a fabriqués.
    penalised = len(imputed)
    if penalised:
        confidence *= max(0.4, 1.0 - 0.15 * penalised)
        log.info("prédiction sol avec %d valeur(s) imputée(s) %s · confiance %.2f → %.2f",
                 penalised, imputed, float(np.max(proba)), confidence)

    if substituted:
        log.info("valeurs catégorielles réécrites pour correspondre aux encodeurs : %s",
                 substituted)

    # Le backend ne lit QUE ces deux champs (SoilPrediction). Les autres sont
    # ignorés en silence par Jackson — utiles au diagnostic humain, sans risque.
    return {
        "category": str(label),
        "confidence": confidence,
        "imputedFeatures": imputed,
    }


# ============================================================
# Santé
# ============================================================
@app.get("/health")
def health():
    return {
        "status": "UP",
        "soilLoaded": bool(_soil),
        "visionLoaded": list(_vision_models.keys()),   # chargement paresseux
        "visionAvailable": list(_SUBDIR.keys()),
    }
