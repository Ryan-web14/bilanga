# Déploiement Heroku — prérequis et procédure

> **Établi le 2026-07-30**, après la V29.
> Vérifié contre le code, pas contre l'usage habituel d'Heroku : chaque affirmation
> ci-dessous renvoie à un fichier du dépôt.
>
> **Règle de travail inchangée** : l'assistant ne lance pas l'application. Tout ce qui
> suit s'exécute par vous.

---

## 0. En une page

La configuration était **déjà largement prête** : `application.yaml` préfère
`JDBC_DATABASE_URL` à toute autre source, lit `REDIS_URL`, et le profil `prod` refuse de
démarrer sur un secret manquant. Ce qui manquait tient en quatre fichiers et **un blocage
de conception**.

| | État |
|---|---|
| ✅ Ajouté | `Procfile`, `system.properties`, `.slugignore`, liaison sur `$PORT` |
| ✅ Vérifié | le jar se construit et ne contient **ni** devtools **ni** docker-compose |
| ✅ Corrigé | l'amorçage du premier compte, qui était **impossible** en production (§4) |
| ✅ Corrigé | les secrets bloquants, désormais pourvus de valeurs par défaut ⚠️ **publiques** (§3.2) |
| 🟠 À vérifier | Java 25 accepté par le buildpack (§2) |
| 🟠 À décider | **deux applications Heroku** — le microservice Python est un déploiement à part entière (§5) |
| 🟠 À faire | `.env` est dans l'index git — à retirer **avant le premier commit** (§7) |

---

## 1. Ce qui a été ajouté

### `Procfile`

```
web: java -Dserver.port=$PORT $JAVA_OPTS -jar target/*.jar
```

`target/*.jar` ne désigne **qu'un seul** fichier : le plugin Spring Boot renomme
l'artefact d'origine en `.jar.original`, qui ne correspond pas au motif. Vérifié après
`mvn package`.

`$JAVA_OPTS` est posé par le buildpack et porte le dimensionnement mémoire adapté au type
de dyno. Ne le remplacez pas par des options fixes.

### `system.properties`

```
java.runtime.version=25
```

Sans ce fichier, le buildpack retient sa version par défaut — une LTS ancienne — et la
compilation échoue sur une erreur de version de classe, loin de sa cause apparente.

### `.slugignore`

Écarte `docs/`, `docker/`, `compose.yaml`, `init.sql`, `src/test/` du slug déployé. Rien
n'y est lu à l'exécution, et la taille du slug pèse sur le démarrage d'un dyno — donc sur
le premier appel après une mise en veille.

### La liaison sur `$PORT`

```yaml
server:
  port: ${PORT:${SERVER_PORT:8080}}
```

> **C'est le piège le plus coûteux du lot.** Heroku expose `PORT` ; Spring Boot ne
> reconnaît que `SERVER_PORT` par liaison relâchée. L'application démarrait donc
> normalement — sur le port 8080 — et le dyno était tué au bout de soixante secondes avec
> un `R10 Boot timeout` qui ne dit rien de sa cause.
>
> Le `Procfile` passe déjà `-Dserver.port=$PORT` : les deux se recouvrent volontairement,
> pour qu'un démarrage sans Procfile (conteneur, `heroku run`, `java -jar` à la main) se
> comporte pareil.

### `spring.docker.compose.enabled: false`

Ceinture et bretelles. **Vérifié** : le plugin Spring Boot exclut déjà
`spring-boot-docker-compose` du jar exécutable, comme il exclut devtools. Le réglage ferme
la question pour un conteneur bâti à la main, où l'exclusion ne s'applique pas.

⚠️ **Effet local** : le démarrage automatique des conteneurs depuis l'IDE est désactivé.
Cela ne change rien à votre pratique — `CLAUDE.md` §4 documente déjà `docker compose up -d
postgres` comme la commande à lancer. Pour le rétablir : `SPRING_DOCKER_COMPOSE_ENABLED=true`.

---

## 2. 🟠 Java 25 — à vérifier au premier build

Le projet cible Java 25 (`pom.xml`), qui est récent. **Je ne peux pas vérifier hors ligne
que le buildpack Java d'Heroku l'accepte.**

Le journal de build le dira explicitement (« Unsupported Java version »). Deux issues, par
ordre de préférence :

1. **Rétrograder** `system.properties` **et** `pom.xml` sur la dernière version acceptée.
   ⚠️ Rétrograder l'un sans l'autre est le pire des deux mondes : le build passe et le
   démarrage échoue.
2. **Déployer en conteneur** (`heroku.yml` + `Dockerfile`), ce qui affranchit du catalogue
   du buildpack au prix d'une image à entretenir.

> Rien dans le code n'exige Java 25 en propre — c'est la valeur héritée du squelette. Si la
> rétrogradation est nécessaire, elle devrait passer sans modification de source, mais
> `mvn test` fera foi.

---

## 3. Les variables d'environnement

### 3.1 Fournies par la plateforme — rien à faire

| Variable | Origine |
|---|---|
| `PORT` | le dyno |
| `JDBC_DATABASE_URL` · `JDBC_DATABASE_USERNAME` · `JDBC_DATABASE_PASSWORD` | buildpack Java + module Heroku Postgres |
| `REDIS_URL` | module Heroku Data for Redis, **si** vous en provisionnez un |

> **`DATABASE_URL` n'est pas utilisable telle quelle** : ce n'est pas une URL JDBC. Le
> buildpack Java expose en plus les trois `JDBC_DATABASE_*`, et c'est celles-là que
> `application.yaml` lit **en premier**. Rien à convertir.

### 3.2 Secrets — des valeurs par défaut existent désormais

> **Changement du 2026-07-30.** Ces quatre réglages **bloquaient le démarrage** quand ils
> manquaient. Ils portent maintenant des valeurs par défaut, et `ConfigurationGuard` se
> contente d'un avertissement appuyé. Motif : un déploiement de soutenance doit démarrer
> sans configuration préalable.

| Variable | Défaut | Ce que le défaut coûte |
|---|---|---|
| `APP_JWT_SECRET` | valeur de démonstration | **publique** — quiconque lit le dépôt peut forger un jeton pour n'importe quel compte, administrateur compris |
| `TOKEN_HASH_SECRET` | valeur de démonstration | idem pour les jetons de rafraîchissement stockés |
| `BILANGA_INGEST_DEVICE_KEY` | valeur de démonstration | quiconque peut déposer des relevés fabriqués — et chaque relevé déclenche un diagnostic |
| `APP_CORS_ALLOWED_ORIGINS` | `*` | toute page web peut appeler l'API depuis un navigateur. Borné par `allowCredentials=false` : aucune session n'est rejouable |

> ⚠️ **Le secret JWT est le seul point vraiment sensible.** Les trois autres ouvrent des
> abus de ressources ; celui-là ouvre l'**usurpation d'identité** — c'est la seule chose
> qui distingue un jeton émis par le serveur d'un jeton fabriqué. Sur des données de
> démonstration, c'est sans conséquence. Sur autre chose, non.

**Fermer, le jour venu — aucun changement de code :**

```bash
heroku config:set \
  APP_JWT_SECRET="$(openssl rand -base64 48)" \
  TOKEN_HASH_SECRET="$(openssl rand -base64 48)" \
  BILANGA_INGEST_DEVICE_KEY="$(openssl rand -hex 24)" \
  APP_CORS_ALLOWED_ORIGINS="https://votre-frontend.example"
```

### 3.2 bis — La seule variable qui reste indispensable

```bash
heroku config:set SPRING_PROFILES_ACTIVE=prod
```

Sans elle, le profil `dev` s'applique : **auto-admin actif** (toute requête sans jeton est
authentifiée comme administrateur), routes métier ouvertes, `DefaultAdminSeeder` qui crée
un compte au mot de passe connu. L'application démarre — et c'est bien le problème.

### 3.2 ter — Les deux leviers de secours

Le défaut reste la posture **fermée**. Si tout répond 403 le jour de la démonstration :

| Variable | Effet |
|---|---|
| `APP_SECURITY_OPEN_BUSINESS_ROUTES=true` | ouvre toutes les routes métier sans autorisation |
| `APP_SECURITY_OWNERSHIP_ENABLED=false` | lève le cloisonnement par propriétaire |

Les deux étaient jusqu'ici codés en dur et **non surchargeables** ; les activer faisait en
outre échouer `ConfigurationGuard`. Les deux verrous ont sauté ensemble — un levier qu'on
ne peut pas actionner n'est pas un levier. Chaque activation est journalisée en `ERROR` à
chaque démarrage.

### 3.3 À régler selon l'usage

| Variable | Défaut | Remarque |
|---|---|---|
| `BILANGA_ML_BASE_URL` | `http://localhost:8000` | **inexistant sur un dyno** — voir §5 |
| `APP_ADMIN_ACCESS_URL` | `http://localhost:3000` | lien des courriels d'administration |
| `APP_TIME_ZONE` | `Africa/Lagos` | ne sert qu'aux heures de silence des notifications |
| `BILANGA_WEATHER_ENABLED` | `true` | Open-Meteo, sans clé d'API. Sortant HTTPS requis |
| `BILANGA_NEIGHBOURHOOD_ENABLED` | `true` | aucun appel externe |
| `BILANGA_SMS_URL` · `BILANGA_SMS_AUTH_HEADER` | vides | vide ⇒ canal indisponible, rien n'est enfilé |
| `MAIL_HOST` · `MAIL_USERNAME` · `MAIL_PASSWORD` | vides | idem pour le courriel |
| `SPRINGDOC_ENABLED` | `false` en prod | Swagger décrit aussi les routes d'administration |

---

## 4. ✅ L'amorçage du premier compte — corrigé

> **Ce paragraphe décrivait un blocage réel, levé le 2026-07-30.** Il est conservé parce
> que la conjonction qui l'avait produit mérite d'être connue.

Sur une base de production neuve, aucun utilisateur n'existe — et les trois chemins
d'entrée étaient fermés **simultanément** :

| Chemin | Pourquoi il était fermé |
|---|---|
| `DefaultAdminSeeder` | porte `@Profile("dev")` — le composant **n'existe pas** en prod |
| `app.security.bootstrap-admin.enabled` | `false` en prod, non surchargeable |
| `POST /admin/provisioning/bootstrap-admin` | tombait sur `AdminApiAuthorizationManager`, qui exige `SYSTEM:USERS` ⇒ **403 pour un anonyme** |

> Chaque décision était juste prise isolément. Leur conjonction produisait un déploiement
> où **personne ne pouvait jamais se connecter**, avec un symptôme trompeur : l'application
> démarre proprement, `/actuator/health` répond, et toute autre route renvoie 403.

**Correctif retenu** : `POST /admin/provisioning/bootstrap-admin` est désormais en
`permitAll` dans `SecurityConfig` — en POST uniquement, et cette route seule. `/staff`
reste gardée par `SYSTEM:USERS`.

Ce qui rend l'ouverture acceptable : **la route refuse de s'exécuter une seconde fois**.
`UserProvisioningServiceImpl` lève un `ConflictException` (409) dès qu'un compte ADMIN
existe. Exiger une permission pour créer le compte qui délivre les permissions était un
cercle sans issue.

```bash
curl -X POST https://bilanga-api.herokuapp.com/sni/api/v1/admin/provisioning/bootstrap-admin   -H 'Content-Type: application/json'   -d '{"email":"admin@bilanga.cg","firstname":"...","lastname":"...",
       "password":"...","generatePassword":false}'
```

> ⚠️ **À faire immédiatement après le premier déploiement.** Tant qu'aucun administrateur
> n'existe, cette route est ouverte à tous — le premier arrivé obtient le compte qui détient
> tous les droits. Le second appel répond 409 : ce n'est pas une panne, c'est le garde-fou.

---

## 5. Le microservice d'inférence Python

`BILANGA_ML_BASE_URL` vaut `http://localhost:8000` par défaut — ce qui marche tant que
FastAPI tourne dans PyCharm à côté du backend. **Sur un dyno, il n'y a rien sur ce port** :
chaque dyno est un conteneur isolé, `localhost` n'y désigne que lui-même.

Le système se dégrade proprement — c'est un invariant tenu : les relevés sont enregistrés,
`skipReason: ML_INDISPONIBLE` est rendu, rien n'est perdu. Mais **aucun diagnostic ne
sort**, donc aucune recommandation et aucune alerte.

### 5.1 Ce sont deux applications, pas deux processus d'une même application

Un dyno = un processus web. On ne fait **pas** tourner Java et Python dans le même dyno :
un seul port est exposé, et le `Procfile` ne lance qu'un `web:`.

```
┌────────────────────────┐          ┌─────────────────────────┐
│  bilanga-api (Java)    │  HTTPS   │  bilanga-ml (Python)    │
│  Procfile: java -jar   │─────────▶│  Procfile: uvicorn      │
│  heroku-postgresql     │  REST    │  aucune base            │
└────────────────────────┘          └─────────────────────────┘
        BILANGA_ML_BASE_URL=https://bilanga-ml-….herokuapp.com
```

Le couplage se réduit à **une variable de configuration**. C'est exactement ce que
l'architecture prévoyait : le backend ne dépend que des interfaces `VisionClient` /
`TabularClient`, jamais du transport ni du code des modèles.

### 5.2 Le contrat, tel que le code Java l'attend

⚠️ **Relevé dans `MlHttpExchange`, `TabularClientImpl` et `VisionClientImpl` — pas dans une
documentation.** Si votre FastAPI diverge sur un seul nom de champ, la désérialisation rend
`null` et le diagnostic est perdu **en silence**.

**`POST /predict/soil`** — `Content-Type: application/json`

Corps envoyé (les valeurs peuvent être `null` : un boîtier ne porte pas forcément toutes
les sondes) :

```jsonc
{
  "temperature": 28.4,              // AIR — et non le sol
  "humidite_sol": 41.2,
  "humidite_air": 78.0,
  "ph": 6.4,
  "azote": 42.0,
  "phosphore": 18.0,
  "potassium": 30.0,
  "luminosite": 21000.0,
  "culture": "tomate",              // MINUSCULES — forme de stockage
  "type_sol": "ARGILEUX",
  "temperature_sol": 24.1,          // ajoutés en V16
  "pluviometrie": 0.0,
  "conductivite_electrique": 1.2
}
```

Réponse attendue — **exactement ces deux champs** :

```json
{ "category": "STRESS_HYDRIQUE", "confidence": 0.88 }
```

**`POST /predict/vision-b64`**

```json
{ "crop": "tomate", "imageBase64": "<base64 sans préfixe data:>" }
```

```json
{
  "crop": "tomate",
  "diseaseClass": "Tomato___Late_blight",
  "confidence": 0.97,
  "allProbabilities": { "Tomato___Late_blight": 0.97, "Tomato___Early_blight": 0.02 }
}
```

> **`diseaseClass` en `camelCase`, pas en `snake_case`.** C'est le nom du champ Java, et
> Jackson le lit tel quel. Un FastAPI qui rend `disease_class` produirait un
> `diseaseClass: null` — donc un diagnostic sans maladie, sans erreur visible nulle part.
> Si votre service expose déjà du `snake_case`, posez un alias Pydantic
> (`Field(serialization_alias="diseaseClass")`) plutôt que de modifier le Java : le nom
> `camelCase` est celui du contrat, et deux DTO Java en dépendent.
>
> Le préfixe `Tomato___` est **normalisé côté Java** (`normalizeDiseaseCode`) : renvoyez le
> nom de classe brut de votre modèle, sans le nettoyer.

**Trois règles de comportement** que le backend suppose :

| Règle | Où c'est lu |
|---|---|
| **Tout code ≠ 200 lève une exception** — pas de dégradation sur un 4xx/5xx | `MlHttpExchange` ligne 70 |
| **Deux tentatives**, 250 ms d'écart, uniquement sur erreur réseau | `ml.max-attempts`, `retry-backoff-millis` |
| **Délais** : 2 s à la connexion, 10 s pour le sol, **30 s pour l'image** | `bilanga.ml.*-timeout-seconds` |

⚠️ Le délai de 30 s sur la vision est à confronter au **délai de requête d'Heroku, fixé à
30 s** et non configurable. Un premier appel après réveil du dyno (chargement de
TensorFlow + poids du modèle) le dépasse presque à coup sûr.

### 5.2 bis — Revue du service existant

> Relecture du `main.py` fourni le 2026-07-30. **Le contrat est bon** : `/predict/soil` rend
> bien `{category, confidence}`, `/predict/vision-b64` rend bien `crop`, `diseaseClass`,
> `confidence`, `allProbabilities` en camelCase, et les noms de classes bruts
> (`Tomato___Late_blight`) sont ceux que `normalizeDiseaseCode` attend. **Rien à changer
> côté Java.**
>
> Trois défauts en revanche, dont **un qui casse le cas nominal**.

#### 🔴 A. `/predict/soil` échoue sur toute mesure absente

```python
for f in SoilInput.FEATURES:
    if f not in payload:
        raise HTTPException(400, f"Champ manquant : {f}")
```

**Ce contrôle ne protège de rien.** Java envoie **toujours les treize clés**, y compris
celles dont la valeur est `null` — Jackson sérialise les valeurs nulles d'une `Map`. La clé
est donc présente, le contrôle passe, et l'échec survient deux lignes plus bas :

| Colonne | Ce qui arrive avec `null` |
|---|---|
| numérique (`ph`, `azote`…) | `NaN` dans le DataFrame → `ValueError: Input contains NaN` → **500** |
| catégorielle (`type_sol`) | `astype(str)` → `"None"` → `y contains previously unseen labels` → **500** |

Et un 500 lève côté Java (`MlHttpExchange` traite tout code ≠ 200 comme un échec) : **le
diagnostic entier est perdu**.

> **Or les mesures absentes sont le cas NORMAL, pas l'exception.** `IngestReadingRequest`
> déclare *toutes* les métriques facultatives : seul `technicalId` est obligatoire. Un
> boîtier qui ne porte pas de sonde de luminosité, ou dont une sonde est débranchée, est la
> situation ordinaire — et c'est exactement celle qui fait tomber le service aujourd'hui.

**Correctif** — imputer plutôt que refuser, et le dire dans la réponse :

```python
# Médianes du jeu d'entraînement. Imputer 0 pour un pH donnerait une acidité
# extrême et un diagnostic faux — pire qu'une valeur manquante.
NUMERIC_DEFAULTS = {
    "temperature": 26.0, "humidite_sol": 45.0, "humidite_air": 70.0,
    "ph": 6.5, "azote": 40.0, "phosphore": 20.0,
    "potassium": 30.0, "luminosite": 15000.0,
}
CATEGORICAL_DEFAULTS = {"culture": "tomate", "type_sol": "LIMONEUX"}

def _coerce(payload: dict) -> tuple[dict, list[str]]:
    row, imputed = {}, []
    for f in SoilInput.FEATURES:
        value = payload.get(f)
        if value is None or (isinstance(value, str) and not value.strip()):
            defaults = CATEGORICAL_DEFAULTS if f in CATEGORICAL_DEFAULTS else NUMERIC_DEFAULTS
            row[f] = defaults[f]
            imputed.append(f)
        else:
            row[f] = value
    return row, imputed
```

⚠️ **`confidence` doit refléter l'imputation.** Une prédiction fondée sur quatre valeurs
inventées ne mérite pas la même confiance qu'une prédiction complète — et côté Java, c'est
`confidence` qui décide de `reliable`, donc de la levée d'une alerte :

```python
row, imputed = _coerce(payload)
...
confidence = float(np.max(proba))
if imputed:
    confidence *= max(0.4, 1 - 0.15 * len(imputed))
return {"category": str(label), "confidence": confidence}
```

> Sous 0,60, `ConfidenceEvaluator` marque le diagnostic non fiable et **aucune alerte n'est
> levée**. C'est le comportement voulu : mieux vaut ne rien conseiller que conseiller sur
> des valeurs inventées.

#### 🟠 B. Vérifier la casse de `type_sol`

Java envoie `plot.getSoilType()` **en majuscules** : `"ARGILEUX"`, `"LIMONEUX"`,
`"SABLEUX"`. Si vos encodeurs ont été entraînés sur des valeurs en minuscules, chaque appel
lève `unseen labels` → 500 → diagnostic perdu.

```python
print(_soil["feature_encoders"]["type_sol"].classes_)
print(_soil["feature_encoders"]["culture"].classes_)
```

Si la casse diverge, normalisez **côté Python** (`str(value).upper()`) — ne changez pas le
Java : `soilType` est exposé en majuscules dans tout le contrat frontend.

#### 🟠 C. Le chargement au démarrage ne passera pas sur un petit dyno

```python
@app.on_event("startup")
def load_all_models():
    _load_vision("manioc", "cassava")
    _load_vision("tomate", "tomato")
```

Deux EfficientNetB0 Keras **plus** le runtime TensorFlow, chargés avant que le port ne soit
ouvert. Deux conséquences distinctes :

| Contrainte Heroku | Conséquence |
|---|---|
| le processus doit écouter sur `$PORT` en **60 s** | dépassement ⇒ `R10 Boot timeout`, le dyno est tué |
| dyno Eco / Basic / Standard-1X = **512 Mo** | TensorFlow seul en consomme 300–400 ⇒ `R14 Memory quota exceeded` |

`@app.on_event` est par ailleurs **déprécié** dans les FastAPI récents — à migrer vers
`lifespan` à l'occasion.

**Chargement paresseux, un modèle à la fois** :

```python
_vision_models = {}
_SUBDIR = {"manioc": "cassava", "tomate": "tomato"}

def _get_vision(crop: str):
    if crop not in _vision_models:
        # Un seul modèle vision en mémoire : le second chasse le premier.
        # Deux EfficientNetB0 simultanés ne tiennent pas dans 512 Mo.
        _vision_models.clear()
        _load_vision(crop, _SUBDIR[crop])
    return _vision_models[crop]
```

⚠️ **Cela déplace le coût vers le premier appel**, qui paiera le chargement — et le délai
de vision est réglé à 30 s côté Java, exactement la limite de requête d'Heroku, qui n'est
pas configurable. Chargez donc le **tabulaire** au démarrage (léger, quelques Mo) et
réservez le paresseux à la vision.

### 5.3 Déployer le service Python

```
bilanga-ml/
├── Procfile           web: uvicorn main:app --host 0.0.0.0 --port $PORT
├── requirements.txt
├── runtime.txt        python-3.12
├── main.py
└── models/
    ├── cassava/  cassava_final.keras · classes.json
    ├── tomato/   tomato_final.keras  · classes.json
    └── tabular/  soil_diagnosis_model.pkl · label_encoders.pkl · target_encoder.pkl
```

`requirements.txt` — **`tensorflow-cpu`, jamais `tensorflow`** :

```
fastapi
uvicorn[standard]
tensorflow-cpu          # ~200 Mo ; « tensorflow » en fait 600+ et dépasse la limite de slug
pillow
numpy
pandas
scikit-learn==<version d'entraînement>
joblib
```

> ⚠️ **Épinglez la version de scikit-learn sur celle qui a produit les `.pkl`.** Un
> `joblib.load` sur une version différente lève un avertissement — ou échoue franchement à
> partir d'un écart majeur. C'est la panne la plus déroutante du lot : elle ne se manifeste
> qu'au démarrage, en production, sur une machine que vous ne voyez pas. `pip freeze | grep
> scikit-learn` sur le poste d'entraînement donne la réponse.
>
> Aucun GPU n'est disponible sur un dyno : `tensorflow-cpu` ne retire donc **rien** en
> pratique.

`MODELS_DIR` reste à sa valeur par défaut (`models`) — le dossier est dans le slug.

**`--host 0.0.0.0 --port $PORT` est obligatoire.** Uvicorn écoute par défaut sur
`127.0.0.1`, ce qui est invisible depuis l'extérieur du conteneur : le dyno serait tué au
bout de soixante secondes. C'est le même piège que `server.port` côté Java.

```bash
heroku create bilanga-ml
git push heroku master
heroku config:set BILANGA_ML_BASE_URL=https://bilanga-ml-xxxx.herokuapp.com --app bilanga-api
```

#### ⚠️ Les trois obstacles réels, par ordre de probabilité

| Obstacle | Pourquoi il se produit | Issue |
|---|---|---|
| **Taille du slug** | limite de 500 Mo. `tensorflow` seul en fait ~600 Mo, avant les poids des modèles | `tensorflow-cpu` au lieu de `tensorflow` ; ou conteneur ; ou héberger ailleurs |
| **Mémoire** | un dyno de base offre 512 Mo. EfficientNetB0 + YOLO chargés simultanément n'y tiennent pas | charger les modèles **paresseusement**, un seul à la fois ; ou un dyno plus grand |
| **Poids des modèles dans git** | des `.keras` de plusieurs centaines de Mo alourdissent le dépôt et le slug | les télécharger au démarrage depuis un stockage externe, ou Git LFS |

**Mesurez avant de déployer** — c'est ce qui décide de la suite :

```bash
du -sh models/                     # les poids
pip download tensorflow-cpu -d /tmp/tf && du -sh /tmp/tf
```

Budget : **500 Mo de slug**, dont TensorFlow-CPU prend déjà ~200. Au-delà de ~250 Mo de
modèles, Heroku n'est plus tenable par buildpack.

> ### Et si ça ne rentre pas — la recommandation honnête
>
> **Déployez le service d'inférence sur HuggingFace Spaces plutôt que sur Heroku.**
>
> | | Heroku (dyno Basic) | HF Spaces (gratuit) |
> |---|---|---|
> | RAM | 512 Mo | **16 Go** |
> | Disque | slug 500 Mo | plusieurs Go |
> | Mise en veille | oui | oui |
> | Conçu pour | applications web | **héberger des modèles** |
>
> Un dyno capable de porter deux EfficientNetB0 est un Standard-2X (1 Go), payant au mois —
> une dépense réelle pour un projet de mémoire, et pour le seul composant que la plateforme
> gère le moins bien.
>
> **Le backend ne verra aucune différence** : `BILANGA_ML_BASE_URL` pointe sur l'URL du
> Space, et rien d'autre ne change. C'est précisément ce que le découplage par interface
> (`VisionClient` / `TabularClient`) permettait de faire sans y penser.
>
> Render et Railway sont des intermédiaires acceptables si vous préférez rester sur un
> hébergeur généraliste.

### 5.4 Sécuriser l'appel

Le service Python sera **publiquement joignable**. Le backend n'envoie aujourd'hui
**aucune authentification** — en local, `localhost` faisait office de frontière.

Trois options, par effort croissant :

1. **Ne rien faire.** Le service n'expose que de l'inférence, sans donnée personnelle. Le
   risque est la consommation de ressources par un tiers.
2. **Une clé partagée** dans un en-tête. ⚠️ `MlHttpExchange` ne pose aujourd'hui que
   `Content-Type` et `Accept` : cela demande une modification Java — petite, mais réelle.
3. **Heroku Private Spaces / réseau privé.** Hors budget d'un projet de mémoire.

**Pour une soutenance, l'option 1 est défendable** à condition de la nommer comme une
limite assumée plutôt que de la laisser découvrir.

### 5.5 Et si on ne déploie pas Python du tout ?

C'est une option **sérieuse pour une première mise en ligne** : elle valide tout le
reste — schéma V1→V29, sécurité, ingestion, les huit moteurs déterministes, les
notifications — sans dépendre d'un second déploiement et de ses contraintes de taille.

Ce qui continue de fonctionner sans le microservice : `RiskEngine`, `AgronomicEngine`,
`TrendAnalyzer`, `WeatherEngine`, `NeighbourhoodEngine`, `ConflictArbitrator`,
`IrrigationAdapter` — soit **sept moteurs sur huit**. Ce qui s'arrête : la classification
d'image et la catégorisation tabulaire, donc `result` et la corroboration.

> À condition de le **dire**. Un jury qui découvre seul que l'IA ne tourne pas conclura
> qu'elle ne marche pas.

---

## 6. Redis — facultatif, mais à trancher

Le cache à deux niveaux bascule sur Caffeine dès qu'un appel Redis échoue, et
`CacheConfig` attrape les erreurs de canal d'invalidation. **Sans module Redis**,
`REDIS_URL` est absente, le repli vise `localhost:6379`, et chaque appel échoue avant de
retomber sur le cache local.

Deux conséquences à connaître :

- **cela fonctionne**, mais chaque lecture de connaissance paie un aller-retour en échec
  (timeout 2 s à la connexion) avant de se rabattre ;
- **avec un seul dyno**, Redis n'apporte rien : son rôle est de partager le cache et de
  propager les évictions **entre instances**.

> **Pour une mise en ligne à un dyno, ne provisionnez pas Redis.** Mais posez alors
> `SPRING_DATA_REDIS_CONNECT_TIMEOUT=200ms` pour que l'échec soit immédiat plutôt que de
> coûter deux secondes. À revoir si vous passez à plusieurs dynos.

---

## 7. ✅ `.env` — retiré de l'index avant le premier commit

Il était **ajouté à l'index** et absent de `.gitignore` : le premier `git push` l'aurait
publié sur GitHub avec ses mots de passe.

```bash
git rm --cached -f .env
```

`.gitignore` porte désormais `.env`, `.env.*` et l'exception `!.env.example`.

> ✅ **Vérifié avant le push : le dépôt ne comptait aucun commit.** `.env` n'était donc que
> dans l'index, jamais dans l'historique — **aucun secret n'a besoin d'être régénéré**. Ce
> n'aurait plus été vrai d'un seul commit.
>
> Le fichier reste sur votre disque : `--cached` ne touche pas la copie de travail.

Sur Heroku, `.env` n'est de toute façon jamais lu : la configuration passe par
`heroku config:set`.

---

## 8. Procédure

```bash
# 1. Application et modules
heroku create bilanga-api
heroku addons:create heroku-postgresql:essential-0

# 2. Configuration — voir §3.2
heroku config:set SPRING_PROFILES_ACTIVE=prod ...

# 3. Déploiement
git push heroku master

# 4. Vérifier le démarrage
heroku logs --tail
```

### Ce qu'il faut lire dans les journaux, dans l'ordre

| Attendu | Ce que cela prouve |
|---|---|
| `Migrating schema "public" to version 29` | Flyway applique **V1 → V29 d'un trait**, sur une base vierge |
| aucune erreur d'Hibernate au démarrage | `ddl-auto: validate` passe — **c'est la première fois** que la chaîne complète est validée bout à bout |
| `Configuration vérifiée · profil prod …` | `ConfigurationGuard` a accepté secrets et posture |
| `Tomcat started on port <PORT>` | la liaison sur `$PORT` fonctionne |
| `Posture de sécurité : durcie` | cloisonnement actif, auto-admin coupé, routes métier fermées |

> **Le premier déploiement est aussi le premier vrai test du schéma.** La base de
> développement porte un historique Flyway réparé à la main (incident V22,
> `IMPLEMENTATION_V16_V22.md` §1 bis) ; une base neuve applique la séquence propre. Si un
> écart entité ↔ migration subsiste, c'est ici qu'il apparaîtra — et ce sera un détail de
> type, les colonnes ayant toutes été vérifiées.

### Puis, une fois le compte d'administration créé (§4)

```bash
curl https://bilanga-api.herokuapp.com/actuator/health
curl -X POST https://bilanga-api.herokuapp.com/sni/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"...","password":"..."}'
```

Puis rejouer `docs/parcours-fonctionnel.http` contre l'URL déployée.

---

## 9. Ce qui n'est pas traité ici

- **Le dimensionnement mémoire.** Spring Boot 4 + JPA + Hibernate tient sur un dyno de
  512 Mo, mais sans marge. Surveillez les `R14 Memory quota exceeded` avant de conclure à
  un problème applicatif.
- **La mise en veille.** Un dyno de type éco s'endort ; le premier appel après réveil paie
  le démarrage complet du contexte Spring. Ce n'est pas une lenteur de l'API.
- **Le stockage de fichiers.** Le système de fichiers d'un dyno est éphémère. Vérifié :
  **aucune écriture de fichier** dans `src/main/java`. `photoUrl` attend déjà une URL
  hébergée ailleurs (`API_FRONTEND.md` §6.3), et l'export CSV est produit en mémoire.
- **Les sauvegardes.** `heroku pg:backups:schedule` — à poser avant la première donnée
  réelle, pas après.
- **Le domaine et TLS.** HSTS est déjà activé dans `SecurityConfig`.
