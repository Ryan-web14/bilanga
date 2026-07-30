# API Bilanga — Cycles de culture & historique des mesures

> **Public** : Rolle (React) et toute personne qui consomme l'API.
> **Établi le 2026-07-30.** Complément de **`API_FRONTEND.md`**, qui reste la
> référence pour tout le reste — enveloppe `ApiResponse`, pagination `data.data`,
> identifiants sérialisés en chaînes, codes d'erreur, authentification.
>
> Ce document couvre **sept routes neuves** et **un changement de comportement**
> sur une route existante. Il est séparé pour une raison simple : ce sont des
> fonctionnalités qui se lisent ensemble, et les noyer dans un document de 1 800
> lignes garantirait qu'on les découvre trop tard.

---

## Sommaire

| § | Sujet | Route |
|---|---|---|
| [0](#0-ce-qui-change-pour-un-client-existant) | ⚠️ **Ce qui change pour un client existant** | `PUT /crops/{id}` |
| [1](#1-diagnostic-à-un-instant-donné) | Diagnostic à un instant donné | `GET /diagnosis/at` |
| [2](#2-clôture-dune-campagne) | Clôture d'une campagne | `POST /crops/{id}/close` |
| [3](#3-bilan-arrêté-et-divergence) | Bilan arrêté et divergence | `GET /crops/{id}/closure` |
| [4](#4-journal-des-révisions) | Journal des révisions | `GET /crops/{id}/journal` · `GET /plots/{id}/crop-journal` |
| [5](#5-calendrier-prévisionnel) | Calendrier prévisionnel | `GET /crops/{id}/calendar` |
| [6](#6-vocabulaire-ajouté) | Vocabulaire ajouté | |
| [7](#7-permissions) | Permissions et masquage | |
| [8](#8-parcours-décran-recommandés) | Parcours d'écran recommandés | |

---

## 0. Ce qui change pour un client existant

### 🔴 `PUT /crops/{id}` n'efface plus les champs omis

**C'est le seul changement de comportement de cette livraison, et il vous concerne
directement.**

**Avant.** La route écrasait *inconditionnellement* tous les champs. Un client qui
n'envoyait que la variété effaçait la surface plantée, la densité, le lot de semence
et la date de plantation. Silencieusement : `200 OK`, aucune erreur, aucune trace.

**Pourquoi c'était grave, et pas seulement gênant.** `plantedArea` conditionne
`yieldPerHectare` et `marginPerHectare` — les deux seuls chiffres comparables entre
parcelles. Une mise à jour partielle rendait donc la campagne incomparable, et
`/plots/{id}/economics` affichait `null` **des semaines plus tard**, sans que rien ne
relie l'effet à sa cause.

**Maintenant.** Un champ **absent ou `null` n'est pas touché**.

```jsonc
// Ne change que la variété. Tout le reste est PRÉSERVÉ.
PUT /sni/api/v1/crops/7
{ "plotId": "42", "cropName": "TOMATE", "variety": "Marmande" }
```

**Si vous envoyez l'objet complet** — le cas d'un formulaire — **rien ne change pour
vous** : tous les champs sont renseignés, donc tous s'appliquent, exactement comme
avant.

### Effacer un champ : `clearFields`

En JSON, un champ *absent* et un champ *`null`* sont indiscernables. Sans mécanisme
dédié, la règle ci-dessus rendrait tout effacement impossible — on aurait troqué une
perte de données silencieuse contre une donnée indélébile.

```jsonc
PUT /sni/api/v1/crops/7
{
  "plotId": "42",
  "cropName": "TOMATE",
  "clearFields": ["variety", "seedLot"]     // vidés explicitement
}
```

**Huit champs effaçables** : `variety`, `plantingDate`, `cycleDurationDays`,
`expectedHarvestDate`, `plantedArea`, `plantDensity`, `seedLot`, `growthStage`.

- Insensible à la casse et aux espaces.
- **Un nom inconnu répond 400**, il n'est jamais ignoré — un effacement qui n'a pas
  lieu et ne le dit pas serait le défaut qu'on vient de corriger, en sens inverse.
- Un champ à la fois renseigné **et** listé finit **vidé** : l'intention explicite
  l'emporte sur la valeur.
- `cropName`, `plotId` et `status` ne sont **pas** effaçables : les deux premiers sont
  obligatoires, le troisième se pilote par la clôture (§2).

> ⚠️ **`cycleDurationDays`, `expectedHarvestDate` et `growthStage` sont *redérivés*
> après effacement.** Les vider revient à demander leur **recalcul**, non à les laisser
> vides. C'est d'ailleurs l'usage principal : forcer un recalcul après correction de la
> date de plantation.

---

## 1. Diagnostic à un instant donné

### `GET /sni/api/v1/diagnosis/at`

> **Le chaînon manquant de l'historique.** `GET /plots/{id}/history` rend des
> intervalles agrégés : un point de courbe porte un `bucket`, un décompte et des
> min/moy/max — mais **ni `readingId` ni `diagnosticId`**. Cliquer sur un creux
> d'humidité du 12 mars ne menait nulle part, alors que c'est exactement le geste qu'on
> fait pour comprendre un incident.

| Paramètre | Obligatoire | Rôle |
|---|:---:|---|
| `plotId` | ✅ | |
| `at` | ✅ | Instant ISO-8601 UTC. **Le `bucket` d'un point d'historique s'envoie tel quel.** |
| `cropName` | | Impose la culture ; à défaut, déduite du diagnostic d'époque |
| `toleranceMinutes` | | Écart maximal accepté (défaut : 1440, soit 24 h) |

### ⚠️ La chose à comprendre avant d'afficher quoi que ce soit

**Le système ne conclut pas à chaque relevé.** Entre le régulateur (intervalle minimal
de 5 minutes + aucune variation), `SONDE_DEFAILLANTE`, `CONTEXTE_ABSENT` et
`ML_INDISPONIBLE`, **un boîtier qui émet toutes les 30 secondes laisse 9 relevés sur 10
sans aucun diagnostic** — le relevé étant conservé dans tous les cas.

C'est le cas **nominal**, pas l'exception. D'où deux notions distinctes, exposées
séparément dans `alignment` :

| `alignment` | Signification | Fréquence |
|---|---|---|
| `SUR_CE_RELEVE` | Le diagnostic a été **produit par** ces mesures | rare |
| `EN_VIGUEUR` | Le diagnostic est le **dernier antérieur** — celui qui s'affichait alors. `diagnosticAgeMinutes` en donne l'âge | **le cas ordinaire** |
| `AUCUN` | Aucune conclusion n'existait | fréquent |

**Les confondre attribuerait à une mesure une conclusion qu'elle n'a pas produite.**
Affichez la nuance : « diagnostic issu de ce relevé » ou « diagnostic en vigueur,
établi 25 min plus tôt ».

### Réponse

```jsonc
{
  "plotId": "42", "plotName": "Parcelle Nord", "cropName": "TOMATE",
  "requestedAt": "2026-07-12T08:00:00Z",

  "reading": {
    "id": "1234567890123456789",
    "recordedAt": "2026-07-12T07:55:00Z",
    "offsetMinutes": -5,                    // SIGNÉ : négatif = avant
    "measures": { "temperature": 28.4, "humidite_sol": 24.0, "ph": 6.4 },
    "anomalyDetected": false
  },
  "readingSelection": "AVANT",              // EXACT | AVANT | APRES | AUCUN

  "diagnosedThen": {
    "id": "…", "source": "CAPTEUR", "result": "STRESS_HYDRIQUE",
    "confidenceScore": 0.88, "confidenceLevel": "ELEVEE", "reliable": true,
    "diagnosedAt": "2026-07-12T07:35:00Z",
    "readingId": "…",                       // ≠ reading.id ⇒ EN_VIGUEUR
    "recommendationCount": 3,
    "recommendations": [ /* même forme que /replay */ ]
  },
  "alignment": "EN_VIGUEUR",
  "diagnosticAgeMinutes": 25,               // nombre, pas chaîne

  "nowWouldConclude": {                     // ce que la connaissance ACTUELLE dirait
    "result": "STRESS_HYDRIQUE", "confidenceScore": 0.88,
    "confidenceLevel": "ELEVEE", "reliable": true,
    "recommendationCount": 4,
    "recommendations": [ /* … */ ]
  },

  "differences": [
    { "kind": "SEUIL_MODIFIE", "kindLabel": "Seuil modifié",
      "statement": "Le seuil appliqué à humidite_sol est passé de 35,00 à 32,00." }
  ],
  "identical": false,
  "summary": "Relevé le plus proche : 5 minute(s) avant l'instant demandé. Ce relevé n'a produit aucun diagnostic ; celui qui s'affichait alors datait de 25 minute(s) plus tôt. 1 écart(s) avec ce que la connaissance actuelle produirait sur ces mesures.",
  "limitation": "…",
  "generatedAt": "…"
}
```

### Les points de détail qui comptent

- **`offsetMinutes` est signé** : négatif si le relevé précède l'instant demandé,
  positif s'il le suit. « 40 minutes plus tôt » et « 40 minutes plus tard » ne se valent
  pas quand on cherche la cause d'un événement.
- **À écart égal, le passé gagne** — déterministe : deux appels identiques rendent le
  même relevé.
- **`readingSelection: "AUCUN"`** ⇒ `reading`, `nowWouldConclude` et `differences` sont
  vides. Ce n'est **pas** une erreur : la parcelle ne transmettait pas, ou l'écart
  dépasse la tolérance. `limitation` invite à élargir `toleranceMinutes`.
- **`differences` vide avec `alignment: "AUCUN"`** ne veut **pas** dire « rien n'a
  changé » : il n'y avait rien à comparer. `limitation` le dit explicitement — ne
  l'affichez pas comme une comparaison réussie.
- **Une mesure absente est omise** de `measures`, jamais rendue à zéro : un boîtier ne
  porte pas forcément toutes les sondes, et « pH 0 » se lirait comme une acidité extrême.

> ⚠️ **`limitation` est toujours renseigné et doit être affiché.** Cette vue superpose
> trois choses d'inégale solidité : des mesures **enregistrées** (exactes), une
> conclusion peut-être seulement **contemporaine**, et un **recalcul partiel**. Les
> présenter au même rang ferait passer une reconstitution pour un enregistrement.
>
> En particulier : **le modèle d'image n'est jamais rappelé**. Sur un relevé sans
> diagnostic, seuls les moteurs agronomiques déterministes tournent — le modèle a pu
> être réentraîné depuis, et le rejouer mêlerait deux variables.

---

## 2. Clôture d'une campagne

### `POST /sni/api/v1/crops/{id}/close`

> **`DELETE /crops/{id}` n'est pas remplacé.** Il continue d'archiver (`status →
> TERMINEE`) sans rien demander. La clôture riche arrive **à côté** : casser une route
> n'est pas additif.
>
> Ce que le `DELETE` ne dit pas : **quand** la campagne s'est réellement achevée,
> **pourquoi**, et **ce qu'elle a rapporté** au moment où on l'a close.

```jsonc
POST /sni/api/v1/crops/7/close
{
  "reason": "RECOLTE_NORMALE",           // OBLIGATOIRE
  "actualEndDate": "2026-08-19",         // facultatif — aujourd'hui à défaut
  "note": "Rendement conforme, deux rangs perdus à la grêle du 12 juin."
}
```

### Le motif est obligatoire, et voici pourquoi

C'est lui qui rend l'historique **interprétable**. Un rendement nul après
`RECOLTE_NORMALE` signale un problème agronomique à chercher ; le même rendement nul
après `PERTE_CLIMATIQUE` ne signale que la météo. Sans motif, comparer deux campagnes
revient à comparer deux chiffres dont on ignore ce qu'ils mesurent.

| Motif | A produit ? | Note |
|---|:---:|---|
| `RECOLTE_NORMALE` | ✅ | Terme atteint |
| `RECOLTE_ANTICIPEE` | ✅ | Rendement légitimement inférieur au potentiel |
| `PERTE_MALADIE` | ❌ | |
| `PERTE_CLIMATIQUE` | ❌ | |
| `PERTE_RAVAGEURS` | ❌ | |
| `ABANDON` | ❌ | Non récoltée, laissée en place |
| `RETOURNEE` | ❌ | Sol retravaillé |
| `ERREUR_DE_SAISIE` | ❌ | ⚠️ **La seule à exclure de l'historique** — la campagne n'a jamais existé |

La réponse porte `harvested` (booléen) : il sépare « rendement nul parce que rien n'a
poussé » de « rendement nul parce que la campagne n'a jamais été menée à terme ». Sans
lui, une moyenne de rendements mêlerait des campagnes et des accidents.

### Les refus (400)

| Cas | Message |
|---|---|
| Campagne **déjà terminée** | « Une clôture ne se rejoue pas : elle fige un bilan à une date… » |
| Motif absent | « …sans lui, un rendement faible ne se distingue pas d'une perte » |
| `actualEndDate` **dans le futur** | « une campagne se clôt quand elle est finie, pas quand on prévoit qu'elle le sera » |
| `actualEndDate` **avant la plantation** | dates citées dans le message |

> **La clôture n'est pas rejouable, et c'est structurant.** Elle **fige** un bilan à
> une date ; le réécrire en ferait un total mis en cache, c'est-à-dire exactement ce que
> l'architecture du projet interdit. Grisez le bouton dès que `status === "TERMINEE"`.

Accepte `Idempotency-Key` (§15 de `API_FRONTEND.md`) — recommandé, la route n'étant pas
rejouable.

---

## 3. Bilan arrêté et divergence

### `GET /sni/api/v1/crops/{id}/closure`

> **Rend DEUX bilans côte à côte, et c'est tout l'intérêt.**
>
> Le projet pose que les totaux économiques se recalculent toujours et ne se stockent
> jamais — au motif juste qu'« un total en cache diverge dès la première correction de
> saisie, et personne ne sait plus lequel croire ».
>
> Un bilan de campagne qui bouge n'est pourtant pas un bilan de campagne. La réponse
> n'est donc pas de renoncer au figé, mais de **rendre les deux, datés, avec leur écart
> expliqué**. Le chiffre arrêté est la référence ; l'écart **devient** le signal
> d'audit.

```jsonc
{
  "cropId": "7", "plotId": "42", "plotName": "Parcelle Nord",
  "cropName": "TOMATE", "variety": "Roma",

  "plantingDate": "2026-04-21",
  "actualEndDate": "2026-08-19",
  "expectedHarvestDate": "2026-08-19",
  "daysVersusExpected": 0,                  // négatif = achevée en avance

  "closureReason": "RECOLTE_NORMALE",
  "closureReasonLabel": "Récolte normale",
  "harvested": true,
  "closureNote": "…",
  "closedAt": "2026-08-19T16:04:00Z",
  "closedByEmail": "admin@bilanga.cg",

  "frozenEconomics": {                      // ARRÊTÉ, écrit une fois, jamais rafraîchi
    "grossRevenue": "920000.00", "totalCost": "312500.00", "margin": "607500.00",
    "harvestCount": 3, "totalQuantity": 1840.0,
    "scope": "PARCELLE", "zoneId": null,    // ← voir la note ci-dessous
    "generatedAt": "2026-08-19T16:04:00Z"
  },
  "currentEconomics": { /* PlotEconomics recalculé maintenant */ },

  "diverged": true,
  "divergenceChanges": [
    "le produit brut est passé de 920000.00 à 880000.00",
    "la marge est passée de 607500.00 à 567500.00"
  ],
  "divergenceStatement": "Le bilan a divergé depuis la clôture : le produit brut est passé de 920000.00 à 880000.00 ; la marge est passée de 607500.00 à 567500.00. Le chiffre arrêté reste la référence de la campagne ; l'écart signale des saisies ou des corrections postérieures — une récolte ajoutée, une intervention renseignée, ou une ligne supprimée.",

  "limitation": "…",
  "generatedAt": "…"
}
```

### Pour votre écran

- **`divergenceStatement` n'est JAMAIS nul**, y compris quand rien n'a bougé :
  « identique à celui arrêté à la clôture » est une information rassurante, et un blanc
  obligerait l'utilisateur à l'interpréter. **Affichez-le systématiquement.**
- **La comparaison est numérique**, pas textuelle : `412000.00` et `412000.0` ne
  déclenchent aucune divergence. Un faux positif sur un écran d'audit apprend à ignorer
  le signal.
- **Le cas concret que cela attrape** : la suppression d'une récolte est **réelle** dans
  ce projet (pas d'archivage). Une récolte supprimée après clôture rend le bilan figé
  faux — et cette ligne est exactement ce qui le rend visible.
- **Campagne close par l'ancien `DELETE`** ⇒ `frozenEconomics` absent, et
  `divergenceStatement` dit « close avant que le système ne fige un bilan ». Ce n'est
  pas une donnée manquante à corriger : l'information n'a jamais été demandée.
- ⚠️ **`scope` et `zoneId`** figurent dans `frozenEconomics` et valent toujours
  `"PARCELLE"` / `null` aujourd'hui. Ils préparent le zonage de parcelle : ignorez-les
  pour l'instant, mais **ne les filtrez pas** — le jour où des bilans de zone existeront,
  c'est le seul champ qui les distinguera.

---

## 4. Journal des révisions

### `GET /sni/api/v1/crops/{id}/journal` — une campagne
### `GET /sni/api/v1/plots/{id}/crop-journal` — toutes les campagnes, **paginé**

> Répond à « qui a changé la surface plantée, et quand ? ». Le premier n'est pas paginé
> — quelques dizaines d'entrées par campagne. Le second l'est : une parcelle cumule les
> journaux de toutes ses campagnes successives, et le volume croît sans borne.
>
> **À ne pas confondre avec `/plots/{id}/timeline`** : la chronologie raconte ce qui est
> *arrivé* à la parcelle (relevés, diagnostics, alertes) ; ce journal dit ce que des
> *humains y ont changé*. Deux lectures distinctes.

```jsonc
[
  {
    "id": "…", "cropId": "7", "plotId": "42",
    "eventType": "MODIFICATION", "eventLabel": "Modification",
    "humanAction": true,
    "changes": {
      "plantedArea": { "before": 0.8, "after": 1.2 },
      "variety":     { "before": "Roma", "after": "Marmande" }
    },
    "changeCount": 2,
    "reason": null,
    "cropVersion": 4,
    "changedBy": "…", "changedByEmail": "agronome@bilanga.cg",
    "changedAt": "2026-06-14T09:22:00Z"
  }
]
```

**Cinq natures** : `CREATION`, `MODIFICATION`, `STADE_RECALCULE`, `CLOTURE`, `CLONAGE`.

- **`humanAction: false`** distingue les `STADE_RECALCULE` : c'est le temps qui passe,
  pas une décision. Les afficher au même rang ferait porter à un utilisateur des
  changements qui ne sont pas les siens. **Repliez-les par défaut.** Leur volume est
  borné — quatre ou cinq par campagne, une par changement de stade.
- Sur `CREATION` et `CLONAGE`, `before` vaut systématiquement `null` : c'est l'état
  initial, exprimé sous la même forme qu'un diff pour qu'**un seul gabarit d'affichage
  suffise**.
- **`changeCount`** permet de replier une entrée volumineuse.
- **`changedByEmail` reste renseigné même si le compte a été supprimé** : un journal qui
  perd le nom de son auteur perd sa raison d'être.
- **`cropVersion`** est le verrou optimiste *avant* l'écriture : il ordonne deux entrées
  portant le même horodatage.
- ⚠️ Les identifiants dans `changes` sont des **chaînes**, comme partout.

> **Ce que vous n'y verrez pas**, et c'est voulu : `id`, `version`, `plot`, `createdAt`,
> `updatedAt`, `economicsSnapshot`. Les cinq premiers ne décrivent pas ce que
> l'utilisateur a changé ; le dernier est déjà tracé par l'événement `CLOTURE`.

---

## 5. Calendrier prévisionnel

### `GET /sni/api/v1/crops/{id}/calendar`

> **La seule vue du système qui ANNONCE au lieu de constater.** Tout le reste est
> réactif : une mesure, un symptôme, un écart. Ici : « floraison attendue dans 3 jours,
> prévoyez le traitement préventif ».

Détaillé au **§5.3 de `API_FRONTEND.md`**. Les trois points à retenir :

- **`nextStage`** porte le « dans N jours » d'un bandeau. **`null` quand le cycle est
  achevé** — il n'y a alors plus rien à annoncer.
- **`daysUntil` est négatif** quand la phase a commencé : « commencée depuis 27 jours »
  sans refaire le calcul.
- ⚠️ **`limitation` doit être affiché à côté des dates.** Ce sont des **projections** sur
  des proportions de cycle indicatives. Un exploitant qui prépare un traitement pour une
  date fausse perd le produit **et** la fenêtre.

---

## 6. Vocabulaire ajouté

| Domaine | Valeurs |
|---|---|
| **`closureReason`** | `RECOLTE_NORMALE` `RECOLTE_ANTICIPEE` `PERTE_MALADIE` `PERTE_CLIMATIQUE` `PERTE_RAVAGEURS` `ABANDON` `RETOURNEE` `ERREUR_DE_SAISIE` |
| **`journal.eventType`** | `CREATION` `MODIFICATION` `STADE_RECALCULE` `CLOTURE` `CLONAGE` |
| **`readingSelection`** | `EXACT` `AVANT` `APRES` `AUCUN` |
| **`alignment`** | `SUR_CE_RELEVE` `EN_VIGUEUR` `AUCUN` |
| **`difference.kind`** | `CONSEIL_AJOUTE` `CONSEIL_RETIRE` `SEUIL_MODIFIE` `PRIORITE_MODIFIEE` `REJEU_IMPOSSIBLE` |

Nouveaux champs sur `CropResponse` : `actualEndDate`, `closureReason`, `closureNote`,
`closedAt`, `updatedAt`.

Un type de recommandation `ITINERAIRE` est **autorisé en base** mais **rien ne l'émet
encore** — il prépare l'itinéraire technique. Ne le traitez pas comme un moteur actif.

---

## 7. Permissions

| Route | Permission serveur | À masquer sur |
|---|---|---|
| `GET /diagnosis/at` | `DIAGNOSIS:READ` | `DIAGNOSIS:READ` |
| `POST /crops/{id}/close` | `FARM:CREATE` | 🔴 **`HARVEST:READ`** — voir ci-dessous |
| `GET /crops/{id}/closure` | `FARM:READ` | 🔴 **`HARVEST:READ`** |
| `GET /crops/{id}/journal` | `FARM:READ` | `FARM:READ` |
| `GET /plots/{id}/crop-journal` | `FARM:READ` | `FARM:READ` |
| `GET /crops/{id}/calendar` | `FARM:READ` | `FARM:READ` |

> 🔴 **`/crops/{id}/closure` expose des marges et des prix de vente.** Le serveur ne
> l'exige aujourd'hui que sous `FARM:READ` — mais un `TECHNICIEN` porte `FARM:READ` et
> **n'a aucune raison de voir la comptabilité**. Masquez ces deux écrans sur
> `HARVEST:READ`, exactement comme `/plots/{id}/economics` (§4.2 de
> `RBAC_FRONTEND.md`).
>
> C'est le même raisonnement que celui appliqué au bilan de parcelle : dans un milieu où
> tout le monde se connaît, ouvrir les marges à quiconque intervient sur une parcelle est
> un problème social avant d'être technique.

---

## 8. Parcours d'écran recommandés

### 8.1 Comprendre un incident depuis la courbe

```
GET /plots/{id}/history?granularity=HOUR&from=&to=
   ↓ l'utilisateur clique sur un creux d'humidité
GET /diagnosis/at?plotId={id}&at={bucket du point cliqué}
   ↓
afficher : mesures d'alors · alignment · différences avec aujourd'hui
```

Le `bucket` du point cliqué s'envoie **tel quel** comme `at`. Affichez `alignment` et
`limitation` : c'est ce qui distingue « le système a conclu ceci sur ces mesures » de
« voici ce qui s'affichait à ce moment-là ».

### 8.2 Clore une campagne

```
GET  /crops/{id}                     → vérifier status === "EN_COURS"
POST /crops/{id}/close               → motif OBLIGATOIRE
   ↓
GET  /crops/{id}/closure             → bilan arrêté + vivant + divergence
```

Griser le bouton dès que `status === "TERMINEE"` : la route n'est pas rejouable.

### 8.3 Auditer une campagne close

```
GET /crops/{id}/closure   → afficher divergenceStatement À CÔTÉ des chiffres
GET /crops/{id}/journal   → replier les humanAction === false
```

Si `diverged === true`, la ligne de divergence explique **pourquoi** les deux chiffres
diffèrent. Ne présentez jamais le bilan figé seul : c'est précisément le cas où
l'utilisateur se demanderait lequel croire.

### 8.4 Suivre une campagne en cours

```
GET /crops/{id}/calendar      → « floraison dans 3 jours »
GET /crops/{id}/journal       → ce qui a été modifié, et par qui
```

---

## 9. Les cinq pièges de ce document

1. **`PUT /crops/{id}` n'efface plus les champs omis.** Si vous comptiez dessus pour
   vider un champ, utilisez `clearFields` (§0).
2. **`alignment: "EN_VIGUEUR"` est le cas ORDINAIRE**, pas l'exception. Le diagnostic
   affiché n'a alors *pas* été produit par les mesures affichées (§1).
3. **`differences` vide ≠ « rien n'a changé »** quand `alignment === "AUCUN"` : il n'y
   avait rien à comparer (§1).
4. **La clôture n'est pas rejouable** — 400 au second appel. Ce n'est pas une panne
   (§2).
5. **`divergenceStatement` n'est jamais nul** et doit toujours être affiché, y compris
   quand il dit « identique » (§3).

---

## 10. Ce qui n'existe pas encore

Ces fonctionnalités sont **planifiées mais non livrées**. Ne construisez rien qui les
suppose.

| Prévu | Impact frontend attendu |
|---|---|
| Succession des campagnes + comparaison N vs N-1 | 2 routes neuves, aucun champ modifié |
| Itinéraire technique planifié | 5 routes neuves sous `/crops/{id}/itinerary` |
| Clonage de campagne | 1 route, `POST /crops/{id}/clone` |
| Seuils effectifs par stade | 1 route, `GET /crops/{id}/thresholds` |
| **Zonage de parcelle** | ⚠️ Reporté. Touchera `plotId` sur de nombreuses vues — c'est pourquoi `scope`/`zoneId` apparaissent déjà dans `frozenEconomics` |
