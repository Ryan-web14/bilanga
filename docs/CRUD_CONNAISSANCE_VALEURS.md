# Enseigner quelque chose au système : sept créations, dans l'ordre

> **Établi le 2026-08-01.** Valeurs prêtes à envoyer pour créer **un élément dans chacun
> des sept CRUD de la base de connaissance**.
>
> Les sept s'enchaînent et racontent une seule chose : **on ajoute une culture et une
> maladie au système sans écrire une ligne de code ni redéployer**. C'est l'argument que
> porte cette page, au-delà de la démonstration d'un CRUD.

---

## L'ordre est imposé par le système, pas par ce document

```
1. Seuils par culture        ──┐  rien d'autre n'est possible avant :
   POST /knowledge/crop-requirements │  « Culture inconnue. Enregistrez d'abord
                                 │    ses seuils agronomiques. »
2. Seuils par stade          ←──┤
3. Maladie                   ←──┘
   └── 4. Condition d'apparition   ← exige la maladie du 3

5. Règle de décision         ┐
6. Corrélation               ├── acceptent le joker « * », donc indépendants
7. Arbitrage                 ┘
```

**Les trois derniers acceptent une culture absente**, qui vaut alors joker. Les quatre
premiers non : ils exigent une culture réellement décrite.

> **Pourquoi cette contrainte existe.** Un seuil par stade, une maladie ou une condition
> de risque n'ont aucun sens sans les seuils généraux de la culture : le moteur les
> fusionne, et une surcharge sans base à surcharger serait ignorée en silence. Le refus au
> moment de l'écriture vaut mieux qu'une règle qui ne se déclenche jamais sans qu'on sache
> pourquoi.

---

## Le vocabulaire fermé, à connaître avant de saisir

Toute valeur hors de ces listes rend un **400** avec le message métier et la liste des
valeurs admises.

| Champ | Valeurs admises |
|---|---|
| **`measureField`** | `temperature` `humidite_sol` `humidite_air` `ph` `azote` `phosphore` `potassium` `luminosite` |
| **`operator`** | `>` `<` `>=` `<=` `==` `BETWEEN` |
| **`priority`** | `HAUTE` `MOYENNE` `BASSE` |
| **`category`** | `NORMAL` `STRESS_HYDRIQUE` `EXCES_EAU` `SOL_ACIDE` `SOL_ALCALIN` `CARENCES_NUTRITIVES` `RISQUE_MALADIE` `STRESS_THERMIQUE` `MALADIE_FOLIAIRE` |
| **`growthStage`** | `LEVEE` `CROISSANCE` `FLORAISON` `FRUCTIFICATION` `MATURATION` `TUBERISATION` |
| **`cropName`** | une culture décrite, ou `*` là où le joker est admis |

> ⚠️ **`temperature_sol`, `pluviometrie` et `conductivite_electrique` existent dans les
> relevés mais ne sont PAS des champs de mesure de la connaissance.** Le moteur de risque
> ne sait pas les lire. Les employer rend un 400, et c'est mieux qu'une condition
> silencieusement ignorée.

**Trois règles de cohérence** que le système vérifie en plus :

- `BETWEEN` exige une borne haute ; les autres opérateurs l'ignorent.
- `BETWEEN` est **interdit dans une corrélation** : il faut deux règles, l'une en `>=`,
  l'autre en `<=`.
- Un arbitrage concilie deux domaines **distincts** : `categoryA` et `categoryB`
  identiques rendent un 400.

---

## Préparation

```bash
HOST=https://bilanga-c65c6649bf37.herokuapp.com
API=$HOST/sni/api/v1

TOKEN=$(curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
        -d '{"email":"admin@bilanga.cg","password":"Bilanga@Prod2026"}' \
        | python -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"
```

**Notez l'identifiant rendu à chaque création** : il sert au retrait en fin de page.

---

## 1. Seuils par culture

`POST /knowledge/crop-requirements`

On décrit une **troisième culture**, le piment, qui n'existait pas dans le système. Les
deux seules cultures connues étaient la tomate et le manioc.

```json
{
  "cropName": "PIMENT",
  "phMin": 5.5,
  "phMax": 7.0,
  "humSolMin": 55.0,
  "humSolMax": 75.0,
  "tempMin": 20.0,
  "tempMax": 32.0,
  "azoteMin": 45.0,
  "phosphoreMin": 20.0,
  "potassiumMin": 40.0,
  "toleranceSecheresse": 0.25
}
```

```bash
curl -s -X POST "$API/knowledge/crop-requirements" -H "$JSON" -H "$AUTH" -d '{
  "cropName":"PIMENT","phMin":5.5,"phMax":7.0,"humSolMin":55.0,"humSolMax":75.0,
  "tempMin":20.0,"tempMax":32.0,"azoteMin":45.0,"phosphoreMin":20.0,
  "potassiumMin":40.0,"toleranceSecheresse":0.25}'
```

| Ce qu'il faut savoir | |
|---|---|
| Seul champ obligatoire | `cropName` |
| Unicité | une seule ligne par culture, un doublon rend 400 |
| Casse | l'API accepte `PIMENT`, la base stocke `piment` |
| `toleranceSecheresse` | **entre 0 et 1**, c'est une fraction et non un pourcentage |
| Nutritifs | **pas de maximum**. Un excès d'azote se lit sur le déséquilibre NPK, pas sur un plafond par élément |

> **Ce que cette création change.** La culture devient utilisable : le moteur agronomique
> sait désormais comparer une mesure aux exigences du piment, et les six autres créations
> deviennent possibles pour cette culture.

---

## 2. Seuils par stade

`POST /knowledge/crop-requirements/stages`

Le piment demande plus d'eau et plus de potassium en floraison qu'en croissance. On ne
redéclare **que les écarts** au seuil général.

```json
{
  "cropName": "PIMENT",
  "growthStage": "FLORAISON",
  "label": "Floraison du piment",
  "humSolMin": 65.0,
  "humSolMax": 80.0,
  "tempMin": 22.0,
  "tempMax": 30.0,
  "potassiumMin": 55.0,
  "toleranceSecheresse": 0.15
}
```

```bash
curl -s -X POST "$API/knowledge/crop-requirements/stages" -H "$JSON" -H "$AUTH" -d '{
  "cropName":"PIMENT","growthStage":"FLORAISON","label":"Floraison du piment",
  "humSolMin":65.0,"humSolMax":80.0,"tempMin":22.0,"tempMax":30.0,
  "potassiumMin":55.0,"toleranceSecheresse":0.15}'
```

| Ce qu'il faut savoir | |
|---|---|
| Obligatoires | `cropName` **et** `growthStage` |
| Unicité | une ligne par couple culture/stade |
| **Les champs omis ne valent pas zéro** | ils signifient « ce stade n'infléchit pas ce seuil », et le seuil général s'applique |
| Prérequis | la culture doit exister, sinon 400 « Culture inconnue » |

> **Le point à commenter.** C'est le mécanisme qui rend le diagnostic sensible au stade.
> Une même humidité de 60 % est correcte en croissance et insuffisante en floraison. Sans
> lui, le système appliquerait le même barème du semis à la récolte, et se tromperait avec
> exactement la même assurance.

**Pour le vérifier tout de suite**, une fois une culture de piment plantée :
`GET /crops/{id}/thresholds` rend les seuils appliqués **stade par stade**, avec pour
chaque valeur son origine : `GENERALE` ou `STADE`.

---

## 3. Maladie

`POST /knowledge/diseases`

On enseigne au système une maladie qu'il ne connaissait pas : la fusariose vasculaire de
la tomate.

```json
{
  "cropName": "TOMATE",
  "diseaseCode": "Fusarium_wilt",
  "displayName": "Fusariose vasculaire de la tomate",
  "symptoms": "Jaunissement unilatéral des feuilles basses, flétrissement aux heures chaudes avec reprise nocturne, brunissement des vaisseaux visible en coupant la tige en biais.",
  "favorableConditions": "Sol chaud au-delà de 26 °C, sol acide, excès d'azote, replantation répétée de solanacées sur la même parcelle.",
  "treatment": "Aucun traitement curatif n'existe. Arracher et brûler les plants atteints avec leurs racines. Désinfecter les outils de taille entre chaque rang.",
  "prevention": "Variétés résistantes, rotation d'au moins quatre ans hors solanacées, chaulage des sols acides, éviter les excès d'azote.",
  "priority": "HAUTE",
  "estimatedCost": 15000
}
```

```bash
curl -s -X POST "$API/knowledge/diseases" -H "$JSON" -H "$AUTH" -d '{
  "cropName":"TOMATE","diseaseCode":"Fusarium_wilt",
  "displayName":"Fusariose vasculaire de la tomate",
  "symptoms":"Jaunissement unilatéral des feuilles basses, flétrissement aux heures chaudes avec reprise nocturne, brunissement des vaisseaux visible en coupant la tige en biais.",
  "favorableConditions":"Sol chaud au-delà de 26 °C, sol acide, excès d azote, replantation répétée de solanacées.",
  "treatment":"Aucun traitement curatif. Arracher et brûler les plants atteints avec leurs racines. Désinfecter les outils entre chaque rang.",
  "prevention":"Variétés résistantes, rotation de quatre ans hors solanacées, chaulage des sols acides.",
  "priority":"HAUTE","estimatedCost":15000}'
```

| Ce qu'il faut savoir | |
|---|---|
| Obligatoires | `cropName`, `diseaseCode`, `treatment` |
| Unicité | une ligne par couple culture/code |
| **Le code doit être normalisé** | `Fusarium_wilt`, jamais `Tomato___Fusarium_wilt`. Le préfixe de culture du modèle de vision est retiré en amont, et un code préfixé ne serait donc jamais retrouvé |
| `displayName` | c'est **le nom français affiché** partout : diagnostic, alternatives, message d'alerte, chronologie |
| `estimatedCost` | par hectare, en devise locale. **`null` veut dire « non renseigné », jamais « gratuit »** |

> **Deux effets immédiats et démontrables.**
>
> **Le nom français.** Le modèle rend des classes anglaises issues des jeux
> d'entraînement publics. C'est cette colonne qui fait que l'exploitant lit « Fusariose
> vasculaire de la tomate » et non « Fusarium_wilt ». Un code absent de la base n'est pas
> traduit, seulement rendu lisible : inventer un nom français produirait une maladie qui
> n'existe pas, sous une forme que rien ne distinguerait d'un nom validé.
>
> **Le conseil de base.** Le champ `treatment` **devient un conseil de type `BASE`** dès
> que cette maladie est diagnostiquée. C'est pourquoi il est obligatoire : une maladie
> décrite sans traitement serait une entrée d'encyclopédie, pas une règle de décision.

---

## 4. Condition d'apparition

`POST /knowledge/diseases/conditions`

On dit au système **à quoi reconnaître les conditions favorables**, à partir des seules
mesures, sans photo.

```json
{
  "cropName": "TOMATE",
  "diseaseCode": "Fusarium_wilt",
  "measureField": "temperature",
  "operator": ">=",
  "threshold": 28.0,
  "weight": 0.4,
  "label": "Température de l'air supérieure ou égale à 28 °C",
  "active": true
}
```

```bash
curl -s -X POST "$API/knowledge/diseases/conditions" -H "$JSON" -H "$AUTH" -d '{
  "cropName":"TOMATE","diseaseCode":"Fusarium_wilt","measureField":"temperature",
  "operator":">=","threshold":28.0,"weight":0.4,
  "label":"Température de l air supérieure ou égale à 28 °C","active":true}'
```

**Une seconde condition, pour que le score soit intéressant** (le sol acide est le second
facteur de la fusariose) :

```json
{
  "cropName": "TOMATE",
  "diseaseCode": "Fusarium_wilt",
  "measureField": "ph",
  "operator": "<",
  "threshold": 6.0,
  "weight": 0.6,
  "label": "pH du sol inférieur à 6,0",
  "active": true
}
```

| Ce qu'il faut savoir | |
|---|---|
| Obligatoires | `cropName`, `diseaseCode`, `measureField`, `operator`, `label` |
| Prérequis | **la maladie doit exister**, sinon 400 « Aucune connaissance enregistrée pour … Décrivez d'abord la maladie » |
| `BETWEEN` | exige `thresholdMax`, les autres opérateurs l'ignorent |
| `weight` | le poids de cette condition dans le score de la maladie |
| **Mesure absente** | la condition est **ignorée**, elle ne compte ni comme satisfaite ni comme manquée |

> **Comment le score se calcule, si le jury le demande.**
> Score = poids des conditions satisfaites / poids total des conditions évaluables.
> Avec les deux conditions ci-dessus, une parcelle à 30 °C et pH 5,7 donne
> `(0,4 + 0,6) / 1,0 = 1,00`, donc un risque `ELEVE`. À 30 °C et pH 6,5 :
> `0,4 / 1,0 = 0,40`, donc `FAIBLE`.
>
> Les seuils de niveau sont 0,85 pour `ELEVE` et 0,60 pour `MODERE`.
>
> **Pourquoi une condition à mesure absente est ignorée plutôt que comptée comme
> manquée.** Un boîtier sans sonde de pH ferait sinon chuter tous les scores de maladies
> liées au pH, et le système conclurait à l'absence de risque là où il n'a simplement pas
> l'information. C'est le dénominateur qui s'ajuste, pas le numérateur.

**C'est ce moteur qui fait la corroboration.** Le score est calculé sur les seules
mesures, indépendamment du modèle de vision. Quand les deux concordent, la conclusion
tient sur deux pieds ; quand elles divergent, le système le dit au lieu de trancher.

---

## 5. Règle de décision

`POST /knowledge/rules`

Une règle relie une **catégorie de situation** à une action, sans passer par une maladie.

```json
{
  "category": "SOL_ACIDE",
  "cropName": "TOMATE",
  "conditionText": "pH du sol inférieur à 5,8 pendant la fructification",
  "proposedAction": "Apporter de la chaux dolomitique à raison de 300 kg/ha, en dehors des périodes de fertilisation azotée. Un sol acide bloque l'assimilation du phosphore et favorise les fusarioses.",
  "priority": "MOYENNE",
  "validated": true,
  "estimatedCost": 42000
}
```

```bash
curl -s -X POST "$API/knowledge/rules" -H "$JSON" -H "$AUTH" -d '{
  "category":"SOL_ACIDE","cropName":"TOMATE",
  "conditionText":"pH du sol inférieur à 5,8 pendant la fructification",
  "proposedAction":"Apporter de la chaux dolomitique à 300 kg/ha, en dehors des périodes de fertilisation azotée. Un sol acide bloque l assimilation du phosphore et favorise les fusarioses.",
  "priority":"MOYENNE","validated":true,"estimatedCost":42000}'
```

| Ce qu'il faut savoir | |
|---|---|
| Obligatoires | `category`, `proposedAction` |
| **`cropName` accepte le joker** | omis ou `*`, la règle vaut pour toutes les cultures |
| `category` | doit appartenir à la liste fermée |
| `conditionText` | **descriptif, non exécutable.** Il documente la règle pour l'agronome ; ce sont les seuils qui déclenchent |
| `estimatedCost` | c'est **ici** qu'on renseigne un coût, et il descend jusqu'au conseil |

> **Le point honnête à faire.** `estimatedCost` figurait au contrat depuis longtemps et
> sortait systématiquement à `null` : aucune source ne le renseignait. La chaîne est
> maintenant complète, de la règle au conseil. Mais **aucun prix n'a été semé**, et c'est
> délibéré : les seuils agronomiques sont déjà indicatifs, y ajouter des prix inventés
> franchirait une ligne. Un seuil approximatif oriente une observation, un prix
> approximatif oriente une **décision d'achat**.

---

## 6. Corrélation

`POST /knowledge/correlations`

Une corrélation **enrichit un diagnostic d'image** avec ce que les mesures ajoutent. Elle
ne se déclenche que sur la chaîne image.

```json
{
  "cropName": "TOMATE",
  "diseaseCode": "Fusarium_wilt",
  "measureField": "ph",
  "operator": "<",
  "threshold": 5.8,
  "extraRecommendation": "Le sol mesuré est acide, ce qui favorise la survie du champignon dans le sol et aggrave la fusariose diagnostiquée. Un chaulage est recommandé en complément de l'arrachage des plants atteints.",
  "priority": "HAUTE"
}
```

```bash
curl -s -X POST "$API/knowledge/correlations" -H "$JSON" -H "$AUTH" -d '{
  "cropName":"TOMATE","diseaseCode":"Fusarium_wilt","measureField":"ph",
  "operator":"<","threshold":5.8,
  "extraRecommendation":"Le sol mesuré est acide, ce qui favorise la survie du champignon dans le sol et aggrave la fusariose diagnostiquée. Un chaulage est recommandé en complément de l arrachage des plants atteints.",
  "priority":"HAUTE"}'
```

| Ce qu'il faut savoir | |
|---|---|
| Obligatoires | `measureField`, `operator`, `extraRecommendation` |
| Joker | `cropName` **et** `diseaseCode` acceptent `*` |
| **`BETWEEN` est refusé** | message explicite : « Employez deux règles, l'une avec `>=` et l'autre avec `<=` » |

> **Pourquoi `BETWEEN` est refusé ici et admis dans une condition de risque.** Une
> condition de risque **pondère** : un intervalle y est une notion naturelle, la
> température favorable à une maladie étant bornée des deux côtés. Une corrélation
> **déclenche un texte** : un intervalle y masquerait le fait que les deux bornes
> justifient rarement le même conseil. Deux règles explicites se relisent, et se corrigent
> séparément.

**La différence avec la condition d'apparition, en une phrase :** la condition dit « les
conditions du sol sont réunies pour cette maladie, avant tout symptôme » ; la corrélation
dit « la maladie est constatée sur la photo, et voici ce que les mesures y ajoutent ».

---

## 7. Arbitrage

`POST /knowledge/arbitrations`

Un arbitrage réconcilie deux conseils qui paraissent se contredire.

```json
{
  "cropName": "TOMATE",
  "categoryA": "SOL_ACIDE",
  "categoryB": "CARENCES_NUTRITIVES",
  "synthesis": "Corrigez l'acidité avant d'apporter l'engrais : en sol acide, le phosphore reste bloqué et une part de l'apport est perdue. Chaulez d'abord, attendez deux à trois semaines, puis fertilisez.",
  "priority": "HAUTE",
  "active": true
}
```

```bash
curl -s -X POST "$API/knowledge/arbitrations" -H "$JSON" -H "$AUTH" -d '{
  "cropName":"TOMATE","categoryA":"SOL_ACIDE","categoryB":"CARENCES_NUTRITIVES",
  "synthesis":"Corrigez l acidité avant d apporter l engrais : en sol acide, le phosphore reste bloqué et une part de l apport est perdue. Chaulez d abord, attendez deux à trois semaines, puis fertilisez.",
  "priority":"HAUTE","active":true}'
```

| Ce qu'il faut savoir | |
|---|---|
| Obligatoires | `categoryA`, `categoryB`, `synthesis` |
| **Les deux catégories doivent différer** | sinon 400 : « Un arbitrage concilie deux domaines distincts » |
| Joker | `cropName` accepte `*` |
| L'ordre | indifférent, l'arbitrage se déclenche dans les deux sens |

> **La propriété à défendre : un arbitrage AJOUTE, il ne retire jamais.** Les deux
> conseils qu'il concilie restent affichés, et la synthèse arrive en tête à priorité
> égale. Effacer l'un des deux ferait disparaître le problème avec lui, ce qui est pire
> que de proposer deux actions à ordonner.
>
> **Deuxième garde-fou, ajouté après coup :** l'arbitrage exige désormais un **écart
> relatif minimal des deux côtés**. Il se déclenchait dès que deux catégories
> coexistaient, même quand l'une reposait sur un dépassement insignifiant, et produisait
> alors une synthèse pour un conflit qui n'existait pas. La synthèse hérite en outre de la
> priorité du **plus faible** des deux conseils : concilier deux problèmes mineurs ne
> produit pas une urgence.

---

## Vérifier, puis retirer

### Relire ce qui vient d'être créé

```bash
curl -s "$API/knowledge/crop-requirements"                 -H "$AUTH"
curl -s "$API/knowledge/crop-requirements/stages?cropName=PIMENT" -H "$AUTH"
curl -s "$API/knowledge/diseases?cropName=TOMATE"          -H "$AUTH"
curl -s "$API/knowledge/diseases/conditions?diseaseId=…"   -H "$AUTH"
curl -s "$API/knowledge/rules?category=SOL_ACIDE"          -H "$AUTH"
curl -s "$API/knowledge/correlations"                      -H "$AUTH"
curl -s "$API/knowledge/arbitrations"                      -H "$AUTH"
```

**Ces listes ne sont pas paginées** : quelques dizaines de lignes chacune.

### Retirer, dans l'ordre inverse

```bash
for r in arbitrations correlations rules diseases/conditions diseases \
         crop-requirements/stages crop-requirements; do
  echo "retirer $r/{id}"     # remplacez par l'identifiant noté à la création
done
```

L'ordre inverse n'est pas décoratif : une maladie ne se supprime pas tant que ses
conditions existent.

---

## Les quatre erreurs à provoquer volontairement

Si le jury demande « et si on saisit n'importe quoi ? », ces quatre appels répondent mieux
qu'un discours. Chacun rend un **400 avec un message rédigé pour l'utilisateur**.

| Ce qu'on envoie | Ce que le système répond |
|---|---|
| `"measureField": "temperature_sol"` sur une condition | « Champ de mesure inconnu : temperature_sol. Valeurs admises : … » |
| `"operator": "BETWEEN"` sur une corrélation | « L'opérateur BETWEEN n'est pas admis pour une corrélation. Employez deux règles, l'une avec >= et l'autre avec <=. » |
| `categoryA` et `categoryB` identiques | « Un arbitrage concilie deux domaines distincts : les deux catégories sont identiques. » |
| Un stade sur une culture inexistante | « Culture inconnue : basilic. Enregistrez d'abord ses seuils agronomiques. » |

> **Le point de conception derrière ces quatre messages.** La frontière du vocabulaire est
> tenue **des deux côtés** : l'énumération Java refuse la valeur à la désérialisation, et
> une contrainte `CHECK` en base garantit l'invariant même pour une écriture directe.
> L'énumération protège l'API, la contrainte protège les données. Une faute de frappe dans
> un service passerait sinon inaperçue jusqu'à ce qu'une comparaison de chaînes échoue
> silencieusement, très loin de la cause.

---

## Deux pièges à connaître avant de démontrer

### Le cache

Une modification faite **par l'API** évince le cache immédiatement. Une modification faite
**directement en base**, au `psql` ou au pgAdmin, met jusqu'à **trente minutes** à se
refléter.

> **Le TTL n'est pas un confort.** Sans lui, un ajustement fait en base ne serait *jamais*
> vu : l'éviction ne se déclenche que sur les écritures passant par l'API. L'administrateur
> verrait sa modification enregistrée, sans effet sur les diagnostics, et chercherait
> l'erreur ailleurs.

### Mesurer l'effet d'un changement sans attendre

Ne modifiez pas un seuil puis n'attendez pas le prochain relevé : il surviendra dans des
conditions différentes de celles qui avaient soulevé la question, et ne répondra donc pas.

```bash
curl -s "$API/diagnosis/{id}/replay" -H "$AUTH"
```

Le rejeu applique la connaissance **actuelle** au **même relevé**, et rend les écarts
rédigés : « le seuil appliqué à humidite_sol est passé de 35,00 à 32,00 », « la
connaissance actuelle produit un conseil que le diagnostic d'origine ne portait pas ».

**Rien n'est écrit** : ni diagnostic, ni conseil, ni alerte. Le rejeu ne pollue ni la
chronologie de la parcelle ni le taux de suivi.

> ⚠️ **`limitation` doit être affiché.** Un écart constate que la connaissance a changé ;
> **il ne dit pas qu'elle a changé en mieux.** C'est à l'agronome d'en juger, et présenter
> le rejeu comme une validation inverserait le sens de l'outil.

---

## Ce que ces sept créations démontrent, en une phrase

Le moteur agronomique est **de la connaissance d'agronome écrite sous forme de règles**,
et un agronome peut la corriger sans développeur et sans redéploiement. C'est ce que ne
permet pas un modèle statistique seul, et c'est la raison pour laquelle les deux
coexistent dans ce système.
