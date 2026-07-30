# API Bilanga — Conduite de campagne

> **Public** : Rolle (React) et toute personne qui consomme l'API.
> **Établi le 2026-07-30**, après la migration **V29**.
>
> **Ce document est le troisième, et il ne recouvre pas les deux autres.**
>
> | Document | Ce qu'il couvre |
> |---|---|
> | `API_FRONTEND.md` | le contrat général — enveloppe, pagination, erreurs, tout le socle |
> | `API_FRONTEND_CYCLES.md` | **clore** une campagne, la **relire** : `/close`, `/closure`, `/journal`, `/calendar`, `/diagnosis/at` |
> | **`API_FRONTEND_CONDUITE.md`** *(ce document)* | **conduire** une campagne : la suite des campagnes, l'itinéraire technique, le clonage, les seuils |
>
> Les trois se lisent dans cet ordre. Rien ici ne redéfinit ce qui est écrit ailleurs :
> l'enveloppe `ApiResponse`, la double imbrication `data.data`, les identifiants en
> chaînes et les codes d'erreur sont ceux de `API_FRONTEND.md` §1 et §4.

---

## Sommaire

| § | Sujet | Routes |
|---|---|---|
| [0](#0-la-question-à-laquelle-répond-chaque-écran) | La question à laquelle répond chaque écran | |
| [1](#1-la-suite-des-campagnes) | La suite des campagnes | `GET /plots/{id}/succession` |
| [2](#2-cette-campagne-contre-la-précédente) | Cette campagne contre la précédente | `GET /crops/{id}/compare-previous` |
| [3](#3-litinéraire-technique) | L'itinéraire technique | `GET\|POST /crops/{id}/itinerary` |
| [4](#4-le-rapprochement-prévu--réalisé) | Le rapprochement prévu ↔ réalisé | `POST …/itinerary/{opId}/match` |
| [5](#5-relancer-une-campagne--le-clonage) | Relancer une campagne : le clonage | `POST /crops/{id}/clone` |
| [6](#6-les-seuils-effectifs) | Les seuils effectifs | `GET /crops/{id}/thresholds` |
| [7](#7-vocabulaire-ajouté) | Vocabulaire ajouté | |
| [8](#8-permissions-et-masquage) | Permissions et masquage | |
| [9](#9-parcours-décran) | Parcours d'écran | |
| [10](#10-les-huit-pièges-de-ce-document) | Les huit pièges | |
| [11](#11-ce-qui-nexiste-pas) | Ce qui n'existe pas | |

---

## 0. La question à laquelle répond chaque écran

Cinq routes, cinq questions que le système ne savait pas traiter.

| Route | La question | Ce qui manquait |
|---|---|---|
| `/plots/{id}/succession` | « qu'est-ce qui a poussé ici, et dans quel ordre ? » | les campagnes étaient stockées, jamais présentées comme une **suite** |
| `/crops/{id}/compare-previous` | « ai-je fait mieux que l'an dernier ? » | aucun rapprochement à espèce constante |
| `/crops/{id}/itinerary` | « qu'avais-je **prévu**, et l'ai-je fait ? » | le système savait le **fait** et le **conseillé**, jamais le **prévu** |
| `/crops/{id}/clone` | « refaire comme l'an dernier » | dix champs et autant de lignes à recopier à la main |
| `/crops/{id}/thresholds` | « **sur quoi** le système me juge ? » | l'exploitant voyait le conseil, jamais le seuil |

> **Aucune de ces routes ne demande une saisie supplémentaire**, sauf l'itinéraire — qui
> est précisément le terme manquant. Tout le reste se dérive de données déjà présentes.

---

## 1. La suite des campagnes

### `GET /sni/api/v1/plots/{id}/succession`

Aucun paramètre. Non paginé : une parcelle compte quelques dizaines de campagnes au plus.

```jsonc
{
  "plotId": "1234567890123456789",
  "plotName": "Parcelle Nord",
  "plotCode": "PARC-2026-000014",

  "campaignCount": 3,

  "campaigns": [                       // de la plus RÉCENTE à la plus ancienne
    {
      "cropId": "998877",
      "cropName": "TOMATE",
      "variety": "Roma",
      "plantingDate": "2026-04-01",
      "endDate": "2026-08-01",
      "endDateIsEstimated": false,     // ⚠️ voir ci-dessous
      "durationDays": 122,
      "daysSincePrevious": 151,        // depuis la FIN de la précédente
      "status": "TERMINEE",
      "closureReason": "RECOLTE_NORMALE",
      "closureReasonLabel": "Récolte normale",
      "harvested": true,
      "plantedArea": 0.8,
      "frozenEconomics": { }           // bilan arrêté à la clôture, ou null
    }
  ],

  "fallowPeriods": [
    { "from": "2025-11-01", "to": "2026-04-01", "days": 151,
      "previousCrop": "MANIOC", "nextCrop": "TOMATE" }
  ],

  "monocultureWarnings": [
    "TOMATE a été cultivé 2 campagnes de suite sur cette parcelle. La monoculture épuise les mêmes réserves du sol et concentre les ravageurs propres à l'espèce — envisagez une rotation."
  ],

  "limitation": "Cet historique décrit ce qui a été SAISI, non ce qui a poussé…",
  "missingData": [],
  "generatedAt": "2026-07-30T09:14:22Z"
}
```

### Les quatre champs à ne pas ignorer

**`endDateIsEstimated: true`** — la campagne n'a pas de date de fin **réelle** ; la date de
récolte *prévue* est utilisée à sa place.

> Un intervalle calculé sur une date prévue n'a pas la même valeur qu'un intervalle
> calculé sur un constat. Marquez-le visuellement — un astérisque, une date en italique —
> et renvoyez vers `POST /crops/{id}/close` (`API_FRONTEND_CYCLES.md` §2), qui est ce qui
> comble ce manque.

**`monocultureWarnings`** — même espèce deux campagnes de suite ou plus. C'est un signal
agronomique réel, pas une coquetterie : la monoculture épuise les mêmes réserves du sol et
concentre les ravageurs propres à l'espèce. **Le système disposait de l'information depuis
toujours et ne la disait à personne.** À afficher en tête, pas en pied de page.

**`fallowPeriods`** — les périodes de sol nu. Seuls les intervalles de **21 jours ou plus**
y figurent : en deçà, ce n'est pas une jachère, c'est le temps de préparer le sol, et le
signaler noierait les vrais repos sous du bruit.

**`daysSincePrevious` négatif** — les deux campagnes se **chevauchent**. C'est une
incohérence de saisie sur des données héritées. Elle est **signalée, pas écrasée** : la
masquer à zéro ferait disparaître le seul indice qu'on en a. Affichez-la comme une alerte
de saisie, avec un lien vers l'édition des dates.

### Ce qui est exclu, et pourquoi

Les campagnes closes pour **`ERREUR_DE_SAISIE`** n'apparaissent **pas** — ni dans
`campaigns`, ni dans le calcul des jachères, ni dans la détection de monoculture.

> Une erreur de saisie n'a jamais occupé le sol. La compter fausserait le précédent
> cultural et fabriquerait une jachère qui n'a pas existé.

Tous les autres motifs restent : un `ABANDON` a bien occupé le sol et consommé des
intrants.

---

## 2. Cette campagne contre la précédente

### `GET /sni/api/v1/crops/{id}/compare-previous`

La précédente de la **même culture** sur la **même parcelle**.

> **Pourquoi la même culture.** Opposer une tomate à un manioc ne dit rien : ni les
> rendements, ni les cycles, ni les charges ne sont commensurables. C'est ce qui permet de
> lire « 2 300 kg/ha contre 1 900 l'an dernier » comme une information et non comme un
> artefact.

```jsonc
{
  "plotId": "…", "plotName": "Parcelle Nord", "cropName": "TOMATE",

  "current":  { /* Campaign, même forme qu'au §1 */ },
  "previous": { /* Campaign, ou null */ },

  "comparable": true,

  "metrics": [
    {
      "key": "yieldPerHectare",
      "label": "Rendement",
      "unit": "kg/ha",
      "previousValue": 1900.0,
      "currentValue": 2300.0,
      "change": 400.0,
      "changePercent": 21.05,
      "better": true,
      "statement": "Rendement : 2300,0 kg/ha contre 1900,0 kg/ha (+400,0 kg/ha, +21 %)."
    },
    { "key": "totalCost", "label": "Charges", "better": null /* ⚠️ */ }
  ],

  "summary": "Campagne de TOMATE plantée le 2026-04-01, comparée à celle du 2025-04-01 : 3 indicateur(s) en progrès, 1 en recul. Un écart constate une évolution, il n'en donne pas la cause.",
  "limitation": "Un écart entre deux campagnes constate une évolution, il n'en donne pas la cause…",
  "missingData": [],
  "generatedAt": "…"
}
```

Indicateurs comparés : `yieldPerHectare`, `marginPerHectare`, `grossRevenue`,
`totalCost`, `uptakeRate`.

### `better` est un **tri-état**, pas un booléen

| Valeur | Sens |
|---|---|
| `true` | l'évolution va dans le sens souhaitable |
| `false` | elle va dans l'autre sens |
| **`null`** | **la direction n'a pas de sens pour cet indicateur**, ou l'écart est nul |

> **`totalCost` porte toujours `better: null`, et c'est délibéré.** Des charges qui montent
> peuvent monter pour de bonnes raisons — un traitement de plus qui sauve la récolte. Les
> marquer « moins bien » serait un jugement que la donnée ne soutient pas.
>
> **Ne repliez pas `null` sur `false`** dans votre code d'affichage : ce sont trois états
> distincts, et le neutre est une information.

### `comparable: false` — première campagne

`previous` vaut `null`, `metrics` est vide, et **`summary` l'énonce** :

> « Première campagne de TOMATE enregistrée sur cette parcelle : il n'y a pas de campagne
> antérieure à laquelle la comparer. »

**Affichez la phrase, pas un tableau vide.** « Première campagne » est une information ; un
blanc n'en est pas une.

### `metrics` vide alors que `comparable: true`

Au moins une des deux campagnes n'a **pas de bilan arrêté** — elle a été close par
`DELETE /crops/{id}` avant l'existence de `POST /crops/{id}/close`, ou n'est pas encore
close. La ligne correspondante figure dans `missingData`.

> **La comparaison porte sur les bilans FIGÉS, jamais recalculés.** C'est ce qui la rend
> stable : recalculer les deux côtés à chaque appel les ferait bouger dès qu'une récolte
> est saisie, et deux consultations successives donneraient des écarts différents.

### `changePercent: null`

La valeur antérieure était nulle. Diviser par zéro afficherait « +∞ », ce qui ne se lit
pas — et un pourcentage inventé serait pire que son absence. **`change` reste renseigné** :
affichez l'écart absolu seul.

---

## 3. L'itinéraire technique

### Le terme qui manquait

Le système enregistrait ce qui a été **fait** (`/interventions`) et ce qu'il **conseille**
(`/recommendations`). Il ne savait rien de ce qui était **prévu**.

C'est pourtant ce troisième terme qui rend les deux autres lisibles :

- une opération **oubliée** est indiscernable d'une opération **jamais planifiée** ;
- le **coût prévisionnel** d'une campagne ne se calculait qu'après la récolte — trop tard
  pour arbitrer ;
- « il fallait traiter à l'entrée en floraison » restait dans la tête de l'exploitant.

### Les routes

| Méthode | Route | Notes |
|---|---|---|
| GET | `/crops/{id}/itinerary` | l'itinéraire complet, rapprochements compris |
| POST | `/crops/{id}/itinerary` | ajoute une opération — **201** |
| PUT | `/crops/{id}/itinerary/{operationId}` | **remplacement complet** |
| DELETE | `/crops/{id}/itinerary/{operationId}` | suppression **réelle** |
| POST | `/crops/{id}/itinerary/{operationId}/match?interventionId=` | §4 |

### Créer une opération

```jsonc
{
  "type": "FERTILISATION",          // OBLIGATOIRE — vocabulaire d'InterventionType
  "label": "Deuxième apport d'azote",

  "daysAfterPlanting": 45,          // ← PRÉFÉREZ CETTE FORME
  "plannedOn": null,                // ou une date ferme

  "growthStage": "FLORAISON",
  "product": "Urée 46 %",
  "dose": 12.5,
  "unit": "kg/ha",
  "estimatedCost": 18000,
  "status": null,                   // PREVUE par défaut
  "note": "…"
}
```

> **Seuls `type` et l'une des deux datations sont obligatoires.** Un itinéraire se saisit
> en amont, avec des produits et des doses qui ne seront arrêtés qu'au moment de faire :
> refuser la ligne parce que la dose manque reviendrait à n'avoir aucun plan plutôt qu'un
> plan incomplet.

**Sans aucune des deux datations → 400** : « une opération qu'on ne sait pas placer dans le
calendrier ne peut pas être suivie ».

#### ⚠️ Poussez `daysAfterPlanting` dans votre formulaire

C'est la forme qui **survit au clonage** (§5) : elle se reporte telle quelle sur une
campagne plantée un autre jour, là où une date ferme devrait être ressaisie ligne par
ligne. Une bascule « J+n / date fixe » avec **J+n par défaut** est le bon compromis.

### La réponse

```jsonc
{
  "cropId": "998877", "plotId": "…", "plotName": "Parcelle Nord",
  "cropName": "TOMATE",
  "plantingDate": "2026-04-01",

  "operationCount": 6,
  "matchedCount": 4,
  "lateCount": 1,
  "completionRate": 66.7,           // null si l'itinéraire est vide

  "totalEstimatedCost": 84000,
  "totalActualCost": 91500,
  "costVariance": 7500,             // positif = dépassement ; null si un côté manque

  "summary": "6 opération(s) planifiée(s), 4 rapprochée(s) d'une intervention réelle, 1 en retard. Coût prévu : 84000. Coût constaté : 91500, soit un dépassement de 7500.",
  "limitation": "Les rapprochements marqués « inférés » sont des hypothèses…",
  "missingData": [],
  "generatedAt": "…",

  "operations": [                   // triées sur resolvedDate ; non datables en FIN
    {
      "id": "445566",
      "cropId": "998877",

      "type": "FERTILISATION", "typeLabel": "Fertilisation",
      "label": "Deuxième apport d'azote",

      "plannedOn": null,
      "daysAfterPlanting": 45,
      "resolvedDate": "2026-05-16",   // ← CALCULÉ, n'existe pas en base

      "growthStage": "FLORAISON",
      "product": "Urée 46 %",
      "dose": 12.5, "unit": "kg/ha",
      "dosage": "12,50 kg/ha",        // déjà formaté
      "estimatedCost": 18000,

      "status": "PREVUE", "statusLabel": "Prévue",

      "late": false,                  // ← CALCULÉ
      "lateByDays": null,

      "interventionId": "778899",
      "interventionPerformedAt": "2026-05-18T07:30:00Z",
      "interventionCost": 19500,

      "matchConfidence": "EXACTE", "matchConfidenceLabel": "Exacte",
      "matchConfirmed": false,        // ⚠️ LE CHAMP LE PLUS IMPORTANT
      "matchGapDays": 2,              // SIGNÉ
      "matchedAt": null,
      "matchStatement": "Rapprochement quasi certain : une intervention du même type a eu lieu 2 jour(s) après la date prévue. Inféré par le système, non confirmé.",

      "createdAt": "…", "updatedAt": null
    }
  ]
}
```

### Trois champs calculés, qui n'existent **pas** en base

| Champ | Pourquoi il n'est pas stocké |
|---|---|
| `resolvedDate` | persistée, elle serait fausse dès qu'on corrige la date de plantation — la correction la plus fréquente |
| `late` / `lateByDays` | le backend n'a **ni ordonnanceur ni tâche de fond** : un statut « en retard » écrit en base serait faux dès le lendemain |
| le rapprochement inféré | voir §4 |

**Ne les mettez pas en cache côté client au-delà de la session** — ils sont vrais à
l'instant de l'appel.

### `resolvedDate: null`

L'opération est datée en `J+n` et la campagne **n'a pas de date de plantation**.
L'opération **figure quand même** dans la liste, en fin, et `missingData` dit pourquoi.

> La faire disparaître donnerait un itinéraire qui paraît complet. Affichez-la dans une
> section « non planifiables », avec un lien vers l'édition de la date de plantation.

### `late: null` ≠ `late: false`

| Valeur | Sens |
|---|---|
| `true` | date dépassée, rien de rapproché — **soit l'opération n'a pas été faite, soit elle n'a pas été saisie** |
| `false` | date à venir |
| **`null`** | **la question ne se pose pas** : opération déjà rapprochée, abandonnée, ou non datable |

`false` sur une opération abandonnée laisserait croire qu'elle a été traitée à temps.

### `completionRate: null`

L'itinéraire est **vide**. `0 %` laisserait croire que rien n'a été fait, alors que rien
n'a été planifié — ce sont deux situations opposées.

### `costVariance: null`

Un des deux côtés n'a aucun coût. Un écart calculé contre zéro ferait passer une absence
de saisie pour une économie : la lecture la plus flatteuse, et la plus fausse.

---

## 4. Le rapprochement prévu ↔ réalisé

### Deux natures, à distinguer **visuellement**

C'est le point le plus important de ce document.

| `matchConfirmed` | Ce que c'est | Durée de vie |
|:---:|---|---|
| **`true`** | un **fait** — quelqu'un l'a validé | écrit en base, permanent |
| **`false`** | une **hypothèse du système** | recalculée à **chaque** appel |
| `null` | aucun rapprochement | — |

> **Pourquoi c'est une inférence.** Rien, dans les données, n'établit qu'une fertilisation
> du 14 mai est celle qui était prévue le 12. On le suppose parce que c'est la lecture la
> plus économique.
>
> **Pourquoi elle n'est pas persistée.** Un mauvais appariement écrit en base se propage —
> au coût constaté, au taux de réalisation, au clonage — et devra être défait à la main. Un
> mauvais appariement recalculé disparaît de lui-même dès que la donnée s'améliore : une
> date corrigée, une intervention saisie après coup.

**Conséquence pour votre écran** : un rapprochement inféré **peut changer d'un appel à
l'autre**. Ne le présentez pas comme acquis. Un liseré, une icône « ~ », ou le mot
« probable » suffisent — mais quelque chose doit distinguer les deux.

### Les fenêtres

| `matchConfidence` | Écart | Sens |
|---|---|---|
| `EXACTE` | ≤ 2 jours | une opération se décale d'un jour pour une pluie, sans cesser d'être celle qui était prévue |
| `PROBABLE` | ≤ 10 jours | un report ordinaire |
| `MANUELLE` | — | confirmé par un humain, quel que soit l'écart |

**Au-delà de dix jours, il n'y a pas de rapprochement du tout** — pas un rapprochement
faible. Les confondre reviendrait à relier une irrigation de mars à une opération prévue en
août.

### `matchGapDays` est **signé**

Négatif = l'opération a été faite **en avance**. « Systématiquement en retard » et
« systématiquement en avance » ne se lisent pas de la même façon ; n'en prenez pas la
valeur absolue.

### Un pour un

Deux opérations du même type ne peuvent **jamais** se réclamer de la même intervention. Le
serveur résout l'appariement globalement, la paire la plus serrée d'abord.

> C'est exactement ce qu'un appariement naïf côté client — « pour chaque opération,
> l'intervention la plus proche » — produirait de faux : la liste paraîtrait complète, une
> intervention serait comptée deux fois, et une opération manquante passerait inaperçue.
> **Ne refaites pas ce calcul côté client.**

### Confirmer, et défaire

```http
POST /sni/api/v1/crops/{id}/itinerary/{operationId}/match?interventionId=778899
```

Bascule aussi le statut en `REALISEE` (sauf s'il valait déjà `PARTIELLE`, qui porte une
information que la confirmation ne remplace pas).

**Défaire** — `interventionId` omis ou vide :

```http
POST /sni/api/v1/crops/{id}/itinerary/{operationId}/match
```

L'opération retourne à l'inférence. **Prévoyez ce geste** : sans lui, une erreur de saisie
serait définitive.

### Les refus (400)

| Message | Cause |
|---|---|
| « Cette intervention n'appartient pas à la campagne… » | l'intervention est rattachée à un autre `cropId` — la rapprocher fausserait le coût constaté des deux |
| « Cette intervention est déjà rapprochée d'une autre opération prévue… » | une intervention ne satisfait qu'une opération. Défaites d'abord l'autre |

---

## 5. Relancer une campagne : le clonage

### `POST /sni/api/v1/crops/{id}/clone` → **201**

```jsonc
{
  "plantingDate": "2027-04-05",   // SEUL CHAMP OBLIGATOIRE
  "plotId": null,                 // omis = même parcelle
  "variety": null,                // omis = repris de la source
  "seedLot": "LOT-2027-B03",      // ⚠️ JAMAIS repris — voir ci-dessous
  "plantedArea": null,
  "plantDensity": null,
  "cycleDurationDays": null,
  "copyItinerary": true           // défaut
}
```

Renvoie un `CropResponse` ordinaire — la nouvelle campagne.

### Ce qui est repris, ce qui ne l'est pas

| Repris | Non repris, et pourquoi |
|---|---|
| `cropName`, `variety` | **`seedLot`** — un lot est **consommé**. Le reporter serait un mensonge de traçabilité, sur le champ précisément dont on a besoin le jour où l'on cherche l'origine d'un problème de levée |
| `cycleDurationDays` | **`growthStage`** — redérivé de la nouvelle date de plantation. Copié, la campagne démarrerait au stade où finissait l'ancienne |
| `plantedArea`, `plantDensity` | **les champs de clôture** — la nouvelle campagne n'est pas close. Copier un bilan lui attribuerait celui d'une autre |
| **l'itinéraire technique**, décalé | **le journal** — il appartient à la campagne d'origine. Une seule entrée `CLONAGE` est écrite, qui nomme la source |

> **Prévoyez le champ `seedLot` dans le formulaire de clonage, vide, avec la mention de son
> usage.** L'utilisateur qui clone s'attend à tout retrouver ; c'est le seul champ dont
> l'absence doit être expliquée plutôt que subie.

### Le décalage de l'itinéraire

| Datation de l'opération source | Ce qui arrive |
|---|---|
| `daysAfterPlanting` | **reportée telle quelle** — elle décrit une position dans le cycle, pas un jour |
| `plannedOn` (date ferme) | **décalée** du même nombre de jours que la plantation |

Les rapprochements, coûts constatés et statuts sont **remis à zéro** : ils décrivaient
l'autre campagne. Toutes les opérations clonées reviennent à `PREVUE`.

> C'est ici que `daysAfterPlanting` paie. Une opération en `J+45` reste en `J+45` quelle que
> soit la date de plantation ; une date ferme n'est juste que si le décalage l'est.

### Le refus (400)

> « Une culture est déjà en cours sur cette parcelle. Terminez-la avant d'en déclarer une
> nouvelle. »

La règle « une seule campagne `EN_COURS` par parcelle » vaut aussi pour le clonage — sans
quoi il serait la porte dérobée par laquelle on en déclare deux. **Proposez de clôturer la
précédente** (`POST /crops/{id}/close`), avec un enchaînement direct.

### Idempotence

La route accepte **`Idempotency-Key`** (`API_FRONTEND.md` §15). Générez l'UUID au **montage
du formulaire**, pas à la soumission : c'est ce qui protège du double-clic et du retour
arrière du navigateur. Un clone dupliqué serait une campagne fantôme dans l'historique de
succession.

---

## 6. Les seuils effectifs

### `GET /sni/api/v1/crops/{id}/thresholds`

**La question à laquelle cela répond** : *sur quoi le système me juge, en ce moment ?*

> Le moteur compare chaque mesure à des seuils, en tire une sévérité, et produit un
> conseil. L'exploitant voyait le conseil et **jamais le seuil**. Quand le système annonce
> un stress hydrique à 34 % d'humidité, rien ne lui disait que le minimum retenu est 35, ni
> qu'il avait changé au passage en fructification.

```jsonc
{
  "cropId": "998877", "plotId": "…", "plotName": "Parcelle Nord",
  "cropName": "TOMATE",

  "currentStage": "FRUCTIFICATION",
  "currentStageLabel": "Fructification",

  "stages": [                        // TOUS les stades, futurs compris
    {
      "stage": "FRUCTIFICATION",
      "stageLabel": "Fructification",
      "startsOn": "2026-06-13",
      "current": true,
      "hasStageOverride": true,      // une ligne existe dans crop_stage_requirement

      "measures": [
        {
          "measure": "humidite_sol",    // même clé que dans les relevés
          "label": "Humidité du sol",
          "unit": "%",
          "min": 45.0,
          "max": 70.0,
          "origin": "STADE",            // ⚠️ LE CHAMP À AFFICHER
          "originLabel": "Propre à ce stade",
          "statement": "Humidité du sol attendue entre 45,0 % et 70,0 %. Seuil propre à ce stade."
        },
        {
          "measure": "azote", "label": "Azote", "unit": "mg/kg",
          "min": 40.0, "max": null,     // les nutritifs n'ont pas de plafond
          "origin": "GENERALE", "originLabel": "Seuil général de la culture",
          "statement": "Azote : au moins 40,0 mg/kg. Seuil général de la culture."
        }
      ],

      "toleranceSecheresse": 0.2,
      "toleranceOrigin": "GENERALE"
    }
  ],

  "limitation": "Ces seuils sont ceux qu'applique le moteur agronomique, mais les valeurs semées à l'installation sont INDICATIVES…",
  "missingData": [],
  "generatedAt": "…"
}
```

Mesures possibles : `humidite_sol`, `temperature`, `ph`, `azote`, `phosphore`,
`potassium`.

### `origin` est le champ qui fait la valeur de cette vue

| Valeur | Sens |
|---|---|
| `GENERALE` | seuil de la culture, identique à tous les stades |
| `STADE` | **propre à cette phase** |

> **Sans lui, l'exploitant voit le système « changer d'avis »** : le même taux d'humidité
> déclenche un conseil en fructification et pas en levée. Dire que **le seuil lui-même a
> changé** transforme une incohérence apparente en information agronomique.

Une surcharge qui reprend la valeur générale reste `GENERALE` — c'est une comparaison de
**valeurs**, pas de présence. Annoncer « propre au stade » sur une valeur identique ferait
chercher une nuance qui n'existe pas.

**`hasStageOverride: false`** ⇒ les seuils sont exactement ceux de la culture pour ce
stade. À afficher : cela évite de chercher une nuance qui n'existe pas.

### Les stades **à venir** sont rendus, et c'est l'usage le plus utile

Lire les seuils de la phase suivante permet d'anticiper : « en fructification, le minimum
d'humidité passera de 35 à 45 % ». Couplé au calendrier (`GET /crops/{id}/calendar`,
`API_FRONTEND_CYCLES.md` §5), cela donne une date **et** une exigence.

### `max: null` sur les nutritifs

Un excès d'azote se lit sur le **déséquilibre NPK** (`indicators.nutrientImbalance` du
diagnostic), pas sur un plafond par élément. **N'affichez pas de borne haute** : inventer un
maximum donnerait un seuil que le moteur n'applique pas.

### Une mesure **absente** de `measures`

Elle n'a **aucune borne** — le moteur ne la juge pas. Ne la rendez pas à zéro : un pH
minimum de 0 déclencherait un conseil sur toute mesure.

### ⚠️ `limitation` doit être affiché

Les valeurs semées à l'installation (V3, V6, V7, V10) sont **indicatives** et n'ont pas été
validées par une source agronomique congolaise. Elles se règlent par
`/knowledge/crop-requirements` — et **une modification peut mettre jusqu'à trente minutes
à se refléter**, les tables de connaissance étant en cache. Les écritures via l'API
évincent le cache immédiatement ; une modification faite directement en base, non.

---

## 7. Vocabulaire ajouté

| Domaine | Valeurs |
|---|---|
| **`plannedOperation.status`** | `PREVUE` `REALISEE` `PARTIELLE` `ABANDONNEE` |
| **`matchConfidence`** | `EXACTE` `PROBABLE` `MANUELLE` |
| **`threshold.origin`** | `GENERALE` `STADE` |
| `plannedOperation.type` | **identique à `intervention.type`** — `IRRIGATION` `FERTILISATION` `TRAITEMENT` `DESHERBAGE` `SEMIS` `RECOLTE` `AUTRE` |

> **Le vocabulaire de `type` est délibérément celui des interventions.** Le rapprochement
> se fait sur `(campagne, type)` : deux vocabulaires distincts le rendraient impossible —
> et rien ne permettrait de s'en apercevoir, les listes seraient simplement toujours vides.

**`PARTIELLE` porte une information que `REALISEE` n'a pas** : faite, mais pas comme prévu
(dose réduite, produit substitué). Un traitement fait à demi-dose n'est pas un traitement
fait, et c'est ce qui expliquera un écart de résultat que rien d'autre n'expliquerait.
**Confirmer un rapprochement ne l'écrase pas.**

### Types Java → JSON, pour ce document

| Champ | Type JSON |
|---|---|
| `id`, `cropId`, `plotId`, `interventionId` | **chaîne** (Snowflake — `API_FRONTEND.md` §1.3) |
| `estimatedCost`, `interventionCost`, `totalEstimatedCost`, `costVariance` | **nombre** — ce sont des `BigDecimal`, non concernés par la règle des `Long` |
| `matchGapDays`, `lateByDays`, `durationDays`, `daysSincePrevious`, `days`, `operationCount`, `matchedCount`, `lateCount` | **nombre** |
| `completionRate`, `changePercent`, `min`, `max`, `toleranceSecheresse` | **nombre** |

---

## 8. Permissions et masquage

Toutes les routes de ce document sont sous **`/crops/**` ou `/plots/**`**, donc couvertes
par **`FARM:{action}`** (`RBAC_FRONTEND.md` §4.2) :

| Route | Permission effective |
|---|---|
| `GET /plots/{id}/succession` · `GET /crops/{id}/compare-previous` | `FARM:READ` |
| `GET /crops/{id}/itinerary` · `GET /crops/{id}/thresholds` | `FARM:READ` |
| `POST /crops/{id}/itinerary` · `POST /crops/{id}/clone` · `POST …/match` | `FARM:CREATE` |
| `PUT /crops/{id}/itinerary/{opId}` | `FARM:UPDATE` |
| `DELETE /crops/{id}/itinerary/{opId}` | `FARM:DELETE` |

### ⚠️ Un point de conception non tranché, à connaître

Trois de ces réponses exposent des **montants** — `frozenEconomics` et les `metrics` de la
comparaison, `estimatedCost` et `costVariance` de l'itinéraire — alors que
`/plots/{id}/economics` et `/overview/economics` relèvent, eux, de **`HARVEST:READ`**
précisément parce qu'ils exposent des marges.

**Aujourd'hui, un `TECHNICIEN` de plateforme n'a pas `FARM:READ`** et n'atteint donc aucune
de ces routes ; le trou n'est pas ouvert. Mais il le deviendrait si l'on accordait
`FARM:READ` à un rôle censé ne pas voir les marges.

> **Côté client, appliquez la règle prudente** : masquez les blocs de coût
> (`totalEstimatedCost`, `costVariance`, `frozenEconomics`, `metrics` économiques) sur
> `HARVEST:READ`, même si le serveur ne l'exige pas encore. Le durcissement est planifié et
> ne doit pas vous prendre au dépourvu.

Le cloisonnement par propriétaire (`AccessGuard`) s'applique normalement : ces routes
passent toutes par `PlotService.require`, donc un `TECHNICIEN` d'exploitation restera
limité au domaine `TECHNIQUE` le jour où `ownership.enabled` passera à `true`
(`RBAC_FRONTEND.md` §7).

---

## 9. Parcours d'écran

### 9.1 L'onglet « historique » d'une parcelle

```
GET /plots/{id}/succession
   ↓
bandeau     → monocultureWarnings, en tête
frise       → campaigns[], avec fallowPeriods intercalés
   ↓ clic sur une campagne
GET /crops/{cropId}/compare-previous     → « mieux ou moins bien que la précédente ? »
GET /crops/{cropId}/closure              → le bilan arrêté (API_FRONTEND_CYCLES.md §3)
```

Marquez les `endDateIsEstimated: true` — et proposez `POST /crops/{id}/close` dessus.

### 9.2 Planifier une campagne

```
POST /crops                              → la campagne
POST /crops/{id}/itinerary   × n         → les opérations, en J+n
   ↓
GET /crops/{id}/itinerary                → totalEstimatedCost = le budget prévisionnel
GET /crops/{id}/calendar                 → les dates de stade (API_FRONTEND_CYCLES.md §5)
GET /crops/{id}/thresholds               → les exigences par stade
```

**C'est le seul moment où le système permet d'arbitrer avant de dépenser.** Tout le reste
constate.

### 9.3 Suivre l'exécution

```
GET /crops/{id}/itinerary
   ├─ late: true              → « à faire, ou à saisir »
   ├─ matchConfirmed: false   → « est-ce bien celle-là ? » → bouton Confirmer
   └─ costVariance            → le dépassement, en cours de campagne
   ↓
POST /crops/{id}/itinerary/{opId}/match?interventionId=…
```

Le bouton **« Confirmer ce rapprochement »** sur chaque ligne inférée est le geste qui
transforme des hypothèses en faits. C'est peu coûteux pour l'utilisateur et cela fiabilise
tout ce qui en dépend.

### 9.4 Refaire l'an prochain

```
GET /crops/{id}/compare-previous   → décider si l'on reconduit
   ↓
POST /crops/{id}/clone   { plantingDate, seedLot }
   ↓
GET /crops/{nouveau}/itinerary     → vérifier le décalage avant de valider
```

Affichez l'itinéraire cloné **avant** de considérer l'opération terminée : c'est le moment
où une date ferme mal décalée se repère.

### 9.5 « Pourquoi ce conseil ? », jusqu'au bout

```
GET /diagnosis/{id}/explain     → le seuil qui a déclenché      (API_FRONTEND.md §8.4)
GET /crops/{id}/thresholds      → tous les seuils, et leur ORIGINE
   ↓
GET /knowledge/crop-requirements  → pour un AGRONOME : les ajuster
```

C'est la chaîne complète : du conseil au seuil, du seuil à la règle, de la règle à son
réglage.

---

## 10. Les huit pièges de ce document

1. **`matchConfirmed: false` est une hypothèse**, recalculée à chaque appel. Ne la
   présentez pas comme un fait, et ne la mettez pas en cache.
2. **`better: null` n'est pas `false`.** Trois états ; les charges portent toujours `null`.
3. **`late: null` n'est pas `false`.** La question ne se pose pas — abandonnée, déjà
   rapprochée, ou non datable.
4. **`completionRate: null` ≠ `0 %`.** Rien n'a été planifié, non « rien n'a été fait ».
5. **`costVariance: null` n'est pas une économie.** Un côté manque.
6. **`seedLot` n'est jamais cloné.** Prévoyez le champ, vide, avec sa mention.
7. **Ne refaites pas l'appariement côté client.** Il est global et un-pour-un ; un
   appariement naïf compterait une intervention deux fois.
8. **`daysSincePrevious` négatif est un chevauchement**, pas un bug d'affichage. Signalez
   l'incohérence de saisie.

---

## 11. Ce qui n'existe pas

Pour éviter de le chercher.

- **Pas de modèle d'itinéraire réutilisable** (« itinéraire type tomate »). Le clonage en
  tient lieu : on repart d'une campagne réelle, ce qui a l'avantage d'être une pratique
  éprouvée plutôt qu'un idéal.
- **Pas de statut `EN_RETARD` en base.** Il est calculé — le backend n'a ni ordonnanceur ni
  tâche de fond, un statut persisté serait faux dès le lendemain.
- **Pas de rapprochement automatique persisté.** Seules les confirmations humaines
  s'écrivent (§4).
- **Pas de rapprochement partiel** (une intervention satisfaisant deux opérations, ou
  l'inverse). C'est strictement un pour un.
- **Pas d'alerte sur une opération en retard.** `late: true` est rendu par l'API ; aucune
  notification n'est levée. Le moteur d'alertes ne couvre que l'agronomique et le technique.
- **Pas de versionnement des seuils.** `/crops/{id}/thresholds` rend les seuils
  **d'aujourd'hui**, pas ceux en vigueur au moment d'un diagnostic passé. Pour ces
  derniers, `GET /diagnosis/{id}/explain` reconstruit la justification depuis les colonnes
  de traçabilité (`API_FRONTEND.md` §8.4).
- **Pas de suggestion d'itinéraire** depuis la base de connaissance. Les règles produisent
  des conseils réactifs, pas un plan.
- **Pas de comparaison à plus de deux campagnes.** `/compare-previous` porte sur N et N−1 ;
  la série complète est dans `/succession`.
