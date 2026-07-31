# Fiche de soutenance — toutes les fonctionnalités, et pourquoi elles existent

> Pour chaque fonction : **le manque qu'elle comble**, ce qu'elle fait, comment elle
> marche, et ce qui la rend défendable devant un jury.
>
> Écrite depuis le code, pas depuis l'intention.

---

## Sommaire

| § | |
|---|---|
| [0](#0-la-thèse-en-cinq-arguments) | La thèse en cinq arguments |
| [1](#1-la-chaîne-dingestion) | La chaîne d'ingestion |
| [2](#2-le-diagnostic) | Le diagnostic |
| [3](#3-les-huit-moteurs-de-connaissance) | Les huit moteurs de connaissance |
| [4](#4-alertes-et-notifications) | Alertes et notifications |
| [5](#5-le-cycle-de-culture) | Le cycle de culture |
| [6](#6-économie-et-boucle-de-retour) | Économie et boucle de retour |
| [7](#7-les-vues-composées) | Les vues composées |
| [8](#8-sécurité-et-cloisonnement) | Sécurité et cloisonnement |
| [9](#9-infrastructure-transverse) | Infrastructure transverse |
| [10](#10-les-limites-à-assumer) | Les limites à assumer |

---

## 0. La thèse en cinq arguments

Ce qui distingue ce travail d'une intégration de classifieur. **Si vous ne retenez que
cinq phrases, ce sont celles-là.**

**1. La boucle est fermée.** Mesure → diagnostic → conseil → action → **effet mesuré** →
rendement. Le système évalue ses propres conseils avec ses propres données.

**2. Deux voies indépendantes se confrontent.** Un réseau convolutif sur images, un moteur
déterministe sur mesures de sol. **Aucune information en commun** — c'est ce qui donne du
poids à leur accord, et de l'information à leur désaccord.

**3. Le système reconnaît ses limites.** `limitation`, `missingData`, `dataQualityNote`,
`reliable: false`. Un chiffre y est toujours accompagné de ce qu'il ne prouve pas.

**4. La fiabilité du capteur est distinguée de la confiance du modèle.** C'est le seul
angle mort capable de produire un conseil *nuisible*, et il est traité séparément.

**5. Le système apprend de son usage sans réentraîner.** Retour sur conseil et taux
d'application ouvrent la révision des règles, à coût nul en calcul.

---

## 1. La chaîne d'ingestion

### 1.1 Ingestion par clé partagée

**Le manque.** Un microcontrôleur n'a ni la mémoire ni l'horloge pour gérer un cycle de vie
de jeton JWT — expiration, rafraîchissement, rotation.

**Comment.** En-tête `X-Device-Key`, comparé en **temps constant**. Une comparaison
ordinaire s'arrête au premier caractère divergent, ce qui laisse deviner la clé caractère
par caractère.

**Défendable parce que** c'est une authentification *distincte*, pas une absence
d'authentification. Le boîtier **ne choisit pas sa parcelle** : elle se déduit de son
identité matérielle. Un intrus peut fabriquer des mesures, il ne peut pas les placer où il
veut.

### 1.2 Le relevé n'est jamais perdu

**Le manque.** Perdre un diagnostic parce qu'un service tiers est muet est acceptable.
Perdre une mesure ne l'est pas : elle est irremplaçable, **l'instant est passé**.

**Comment.** Le relevé est écrit dans sa **propre transaction**, validée sur-le-champ. Tout
ce qui suit — mise à jour du boîtier, verdict de sonde, diagnostic — ne peut plus l'emporter
en tombant. Chaque échec en aval est converti en `skipReason`.

> **L'anecdote qui vaut d'être racontée.** Ce n'était pas le cas au départ : le diagnostic
> partageait la transaction de l'ingestion, et toute exception — *même rattrapée* — marquait
> la transaction `rollback-only`. Le journal affichait « Relevé conservé » pendant que la
> base n'en gardait rien. Le défaut était **invisible en développement** et n'apparaissait
> qu'en production.

### 1.3 Deux contrôles distincts, à ne jamais confondre

| | `PlausibilityChecker` | `SensorHealthAnalyzer` |
|---|---|---|
| **Juge** | la **mesure** | la **sonde** |
| **Détecte** | l'absurde : pH 22, humidité 130 % | le figé, la dérive, le décrochage |
| **Fenêtre** | le relevé seul | 12 h de série + boîtiers voisins |
| **Effet** | `anomalyDetected: true` | verdict + alerte technique + **inhibition** |

**Pourquoi le second existe.** Une sonde qui tombe en panne renvoie **rarement** une valeur
absurde. Elle se fige sur sa dernière lecture, elle dérive à mesure que l'électrode
s'encrasse, ou elle décroche de ses voisines — **en restant tout du long dans des valeurs
parfaitement crédibles**.

**Les trois règles :**

| Signal | Détection | Verdict |
|---|---|---|
| valeur figée | même valeur **exacte** sur 6 relevés | `DEFAILLANTE` |
| décrochage | écart > 0,60 à la **médiane** des voisins | `DEFAILLANTE` |
| dérive | écart > 0,25 à cette médiane | `SUSPECTE` |

**Trois décisions à défendre :**

- **Égalité exacte.** Une mesure physique réelle varie toujours au moins sur sa dernière
  décimale. Deux relevés identiques arrivent ; six d'affilée ne sont plus un phénomène
  naturel.
- **Médiane, pas moyenne.** Avec trois boîtiers dont un en panne, la moyenne serait tirée
  par le fautif et **disculperait celui qu'on examine**.
- **Écart rapporté à l'étendue observée.** Un écart de 2 sur un pH est considérable, le même
  sur l'azote est négligeable.

**La limite assumée** : sans voisin, une dérive lente est **rigoureusement indiscernable**
d'une évolution réelle du sol. Seule la règle de la valeur figée reste applicable.

### 1.4 Le régulateur de diagnostic

**Le manque.** Un boîtier émettant toutes les 30 s produirait 2 880 diagnostics par jour,
identiques à 99 %.

**Comment.** Intervalle minimal de 5 min **et** aucune variation notable ⇒
`CONDITIONS_STABLES`. Trois échappatoires forcent le diagnostic : intervalle écoulé,
franchissement d'un seuil de variation, ou anomalie matérielle.

### 1.5 Auto-enregistrement des boîtiers

**Le manque.** Le firmware s'authentifie par clé, pas par jeton : il **ne peut pas** appeler
`POST /devices`, qui exige une permission. Chaque nouveau montage imposait un enregistrement
manuel préalable.

**Comment.** Un `technicalId` inconnu crée le boîtier à son premier relevé.

**Le garde-fou** : le 404 subsiste s'il n'existe **aucune** parcelle. Un relevé doit se
rattacher quelque part, et inventer une parcelle serait fabriquer une donnée métier à partir
d'un paquet réseau.

---

## 2. Le diagnostic

### 2.1 Deux chaînes, un pipeline commun

```
IMAGE    → VisionClient  → /predict/vision-b64  ─┐
                                                 ├→ 8 moteurs → dédup → arbitrage
CAPTEUR  → TabularClient → /predict/soil        ─┘   → adaptation → tri → alerte
```

**Le microservice d'inférence est un système tiers**, joint en REST. Jamais d'import direct.
Le backend ne dépend que des interfaces `VisionClient` / `TabularClient`.

**Défendable parce que** cela a été mis à l'épreuve : le microservice a changé d'hébergeur
et de format de modèle (TFLite) sans qu'une ligne de Java ne bouge. **Une variable de
configuration.**

### 2.2 Confiance et fiabilité

Seuils : `high = 0,85`, `low = 0,60`. **En deçà de `low`, `reliable = false` — et un
diagnostic non fiable ne lève aucune alerte.**

**Pourquoi.** Un diagnostic peu fiable ne conclut rien : il ne doit ni lever d'alerte, ni
servir de motif pour en refermer une. L'exploitant se déplacerait — ou cesserait de se
déplacer — sur la foi d'une conclusion que le système lui-même ne soutient pas.

### 2.3 La corroboration — l'argument le plus solide

**Ce que ça fait.** Croise la maladie prédite par le **modèle vision** avec le score du
**moteur de risque**, calculé sur les seules mesures.

| Score de risque | Conclusion rendue |
|---|---|
| ≥ 0,60 | les conditions **corroborent** |
| ≤ 0,20 | les conditions **ne soutiennent pas** la progression : symptôme d'un passé |
| entre | rien de concluant — `null` |

> **La force de l'argument tient à l'indépendance.** La probabilité vient d'un réseau
> convolutif entraîné sur des images ; le score de risque d'un moteur déterministe appliqué
> à des mesures de sol. **Elles n'ont aucune information en commun.**

### 2.4 L'explication comparative

**Le manque.** « Pourquoi cette maladie, et pas l'autre ? » — un classifieur ne répond pas.

**Comment.** Croise les probabilités du classifieur et les conditions mesurées, et produit
**quatre énoncés distincts** :

| Cas | Ce qu'on dit |
|---|---|
| les mesures départagent en faveur du retenu | l'argument le plus fort : deux voies concordent |
| **les mesures pencheraient pour l'alternative** | **on le dit** : « un examen visuel de confirmation est recommandé » |
| conditions communes aux deux | « le départage repose uniquement sur l'aspect des lésions » |
| aucune condition réunie | idem, formulé autrement |

> **Le deuxième cas est celui qui compte.** Les confondre sous une formule unique donnerait
> une phrase toujours vraie et jamais informative. **Le taire serait malhonnête.**

### 2.5 La justification — `/explain`

**Rien n'est recalculé.** Tout provient des colonnes de traçabilité posées au moment du
diagnostic : `sourceRuleId`, `measureField`, `observedValue`, `thresholdValue`.

**Pourquoi.** Recalculer donnerait la justification d'*aujourd'hui*, pas celle du conseil tel
qu'il a été émis — et les deux divergeraient dès qu'un seuil serait modifié.

C'est aussi la raison pour laquelle `IrrigationAdapter` **préserve** ces colonnes en
reformulant le texte.

### 2.6 Le rejeu — `/replay`

**Le manque.** La base de connaissance est pilotable par API : on ajuste un seuil de 35 à
32 %. Mais rien ne disait **ce que cela change**. Il fallait modifier, puis attendre le
prochain relevé — sur des conditions différentes de celles qui avaient soulevé la question.

**Comment.** Rejoue le raisonnement sur le **même relevé** avec la connaissance actuelle, et
liste les écarts : conseil ajouté, retiré, seuil modifié, priorité modifiée.

**N'écrit rien** : ni diagnostic, ni recommandation, ni alerte. Le rejeu ne pollue ni la
chronologie ni le taux de suivi.

> ⚠️ **Un écart constate que la connaissance a changé ; il ne dit pas qu'elle a changé en
> mieux.** C'est à l'agronome d'en juger.

### 2.7 Le diagnostic à un instant donné — `/diagnosis/at`

**Le manque.** `/history` rend des intervalles agrégés : un point de courbe ne porte ni
`readingId` ni `diagnosticId`. **Cliquer sur un creux d'humidité du 12 mars ne menait nulle
part** — or c'est exactement le geste qu'on fait pour comprendre un incident.

**La distinction qui fait la valeur de la vue :**

| `alignment` | Sens | Fréquence |
|---|---|---|
| `SUR_CE_RELEVE` | le diagnostic a été **produit par** ces mesures | rare |
| `EN_VIGUEUR` | le **dernier antérieur** — celui qui s'affichait alors | **le cas ordinaire** |
| `AUCUN` | aucune conclusion n'existait | fréquent |

**Pourquoi cette distinction est nécessaire.** Le système ne conclut pas à chaque relevé :
entre le régulateur, la sonde défaillante et le contexte absent, **9 relevés sur 10 n'ont
aucun diagnostic**. Les confondre attribuerait à une mesure une conclusion qu'elle n'a pas
produite.

---

## 3. Les huit moteurs de connaissance

Couche **experte et explicable**, complémentaire des modèles statistiques. Les règles
supportent le joker `'*'` (valable quelle que soit la culture).

### 3.1 `RiskEngine` — l'alerte précoce

Estime, par maladie, la **fraction pondérée des conditions d'apparition réunies**, à partir
des **seules mesures**.

`score = poids satisfait / poids total`. Niveaux : ≥ 0,85 `ELEVE`, ≥ 0,60 `MODERE`.

**Utile parce que** c'est une alerte **avant tout symptôme**, et **indépendante du modèle
vision** — donc disponible sans photo, et exploitable pour la corroboration.

Une condition dont la mesure est absente est **ignorée**, pas comptée comme fausse.

### 3.2 `AgronomicEngine` — l'écart aux exigences

Compare chaque mesure aux plages de la culture, **affinées par stade**.

`sévérité = (écart / amplitude) × (1 − tolérance)`, clampée 0–1.

**Pourquoi normaliser par l'amplitude** et non par le seuil : une culture à exigences
étroites voit ses dépassements pesés plus lourdement qu'une culture tolérante — ce qui est
agronomiquement juste.

Calcule aussi le **VPD** (déficit de pression de vapeur) et le **déséquilibre NPK**.

### 3.3 `TrendAnalyzer` — l'anticipation

Régression des **moindres carrés** sur une fenêtre récente, projette le franchissement de
seuil à 12 h.

**Le garde-fou qui compte : le contrôle du R².** Une série erratique produit **toujours** une
pente — la régression n'échoue jamais.

> Annoncer un stress hydrique dans quatre heures sur la foi du bruit de mesure fait perdre
> la confiance de l'exploitant plus vite que de ne rien annoncer.

### 3.4 `CorrelationEngine` — le croisement image / mesures

Filtre les règles de corrélation par la valeur mesurée. Chaîne image uniquement.

### 3.5 `WeatherEngine` — le seul qui regarde devant, dehors

Trois règles depuis Open-Meteo : différer l'irrigation si la pluie annoncée dépasse un
seuil ; refuser un traitement si une averse tombe sous 6 h (**produit lessivé**) ; alerte
préventive sur humidité annoncée.

**Open-Meteo et non OpenWeatherMap** : aucune clé d'API. Le système est démontrable sans
compte à gérer ni abonnement susceptible d'expirer avant la soutenance.

**Rend une liste vide** si la météo est désactivée, si la parcelle n'a pas de coordonnées,
ou si le fournisseur ne répond pas.

### 3.6 `NeighbourhoodEngine` — raisonner sur un territoire

**Le seul moteur dont l'information ne peut venir d'AUCUNE mesure locale.**

> Une sonde parfaite ne dira jamais qu'un mildiou progresse à huit cents mètres.

Rayon 2 km, fraîcheur 14 jours, pondération par **distance × fraîcheur**.

**Deux garde-fous :**
- le texte dit lui-même « aucun symptôme n'a été relevé sur votre parcelle : c'est une
  alerte de proximité, non un diagnostic » — sans quoi l'exploitant **cherche l'erreur dans
  ses sondes** ;
- le moteur **se tait** si la maladie est déjà signalée localement : deux conseils pour un
  même problème font douter du système, pas de la maladie.

### 3.7 `ConflictArbitrator` — concilier sans effacer

« Réduire l'humidité pour contenir une maladie foliaire » et « irriguer pour lever un stress
hydrique » paraissent contradictoires. **L'une vise l'air, l'autre le sol : la contradiction
n'est qu'apparente.**

Le moteur **ajoute** la synthèse qu'un agronome formulerait — il ne retire jamais.

**Le raffinement récent, et il se raconte bien :** il se déclenchait sur la seule
*coprésence de catégories*, sans regarder les mesures. Une humidité à 58 % pour un seuil de
60 déclenchait une synthèse rédigée comme si les deux problèmes étaient sérieux. Il exige
désormais **15 % d'écart des deux côtés**, hérite la priorité du **moins urgent**, et porte
sa traçabilité.

> **Le défaut n'était pas d'ajouter, mais d'ajouter trop tôt.**

### 3.8 `IrrigationAdapter` — reformuler l'irréalisable

Sur une parcelle `PLUVIAL`, un conseil demandant d'irriguer est **complété** d'une
alternative réalisable : paillage, ombrage, binage.

> **Effacer le conseil ferait disparaître le problème avec lui**, ce qui est pire que de
> proposer une action irréalisable. Le constat reste vrai : le sol manque d'eau. Seule la
> réponse change.

**Deux garde-fous** : le rattachement se fait sur la **catégorie ET le libellé** (la même
catégorie porte des conseils déjà compatibles avec le pluvial), et **`null` n'est pas
`PLUVIAL`** — en l'absence d'information, mieux vaut laisser le conseil d'origine.

---

## 4. Alertes et notifications

### 4.1 La signature porte la **situation**

Une alerte porte sur une *situation* (`source:culture:résultat`), pas sur un relevé. Tant
qu'une alerte ouverte porte la même empreinte, aucune nouvelle n'est créée.

**Réconciliation automatique :**
- la situation a changé ⇒ `AUTO_SITUATION_REMPLACEE` ;
- plus rien d'urgent ⇒ `AUTO_SITUATION_NORMALISEE` ;
- reconstatée sans acquittement ⇒ **escalade**, et au-delà de 3 l'alerte monte d'un niveau.

> Une alerte ignorée qui reste au même rang finit par se confondre avec le bruit de fond.

### 4.2 Les alertes techniques

Signature `TECHNIQUE:<technicalId>`, niveau `ELEVEE` et **non `CRITIQUE`** : la parcelle
n'est pas en danger, c'est la surveillance qui l'est. Réserver le critique à ce qui menace
la culture **préserve son sens**.

Elles **se referment** quand le verdict revient à `SAINE` — sans quoi le technicien
apprendrait à ignorer une liste qui ne se vide jamais.

### 4.3 L'outbox — le maximum que l'infrastructure permet

```
1. l'intention d'envoi est écrite DANS la transaction de l'alerte
2. la tentative a lieu APRÈS le commit
```

> Perdre un diagnostic pour un serveur de courriel muet serait absurde.

### 4.4 `RecipientResolver` — à qui, et quand

- **l'affecté prime sur le propriétaire** : si quelqu'un s'est vu confier le traitement,
  c'est lui qu'il faut prévenir ;
- **seuil personnel** > seuil global. Notifier tout le monde de la même façon revient à ne
  notifier personne : celui qu'on réveille à 3 h pour une situation qui pouvait attendre
  coupe ses notifications, et n'apprendra pas non plus la critique du lendemain ;
- **heures de silence**, plage pouvant enjamber minuit. **Une alerte `CRITIQUE` passe
  outre** : la reporter la viderait de son sens ;
- **regroupement** `parcelle:niveau:tranche de 10 min`. Le niveau fait partie de l'empreinte
  délibérément — réunir une alerte moyenne et une critique ferait passer la seconde pour
  une ligne parmi d'autres.

### 4.5 Les canaux, et les langues

**Passerelle SMS générique**, pas de SDK d'opérateur. Trois opérateurs exposent la même
chose : une URL, un corps, un en-tête.

> Un client par opérateur obligerait à **livrer du code pour changer de fournisseur** — au
> moment précis où l'ancien ne marche plus.

**Lingala et kituba** : ce sont les seuls messages que l'application adresse à quelqu'un qui
**n'a pas choisi de la consulter** — sur un téléphone simple, au champ.

> ⚠️ **L'enveloppe est traduite, le constat agronomique reste en français.** C'est une
> décision, pas une paresse : le constat est une prose composée à la volée. Une traduction
> qui dérive donne un conseil *faux* dans la langue que la personne comprend le mieux —
> donc celui qu'elle suivra.

---

## 5. Le cycle de culture

### 5.1 Le stade se **déduit**, il ne se saisit pas

**Le manque.** `growth_stage` est une colonne saisie à la main que personne ne revient
corriger : une tomate plantée en mars reste « LEVEE » jusqu'à la récolte. Or les seuils
agronomiques applicables en dépendent.

> Le système raisonnait sur un stade faux — **avec exactement la même assurance** que sur un
> stade juste, ce qui est le pire des deux mondes.

**Comment.** En **fraction du cycle**, pas en jours fixes : une variété précoce et une
tardive traversent les mêmes phases dans les mêmes proportions.

**Pas d'ordonnanceur** : le recalcul a lieu là où le stade est consommé. Un stade recalculé
que personne ne lit n'a aucune valeur.

### 5.2 Le calendrier prévisionnel

**La seule vue du système qui ANNONCE au lieu de constater.** Tout le reste est réactif : une
mesure, un symptôme, un écart. Ici : « floraison attendue dans 9 jours, prévoyez le
traitement préventif ».

Aucun calcul nouveau : ces dates étaient déjà calculées, et personne ne les lisait.

### 5.3 La clôture riche et le bilan figé

**Le manque.** L'archivage se contentait de passer le statut à `TERMINEE`. On ne savait ni
**quand** la campagne s'était achevée, ni **pourquoi**, ni **ce qu'elle avait rapporté**.

**Le motif est obligatoire, et c'est structurant** : un rendement nul après
`RECOLTE_NORMALE` signale un problème agronomique à chercher ; le même après
`PERTE_CLIMATIQUE` ne signale que la météo.

**La tension résolue.** Le projet pose que les totaux se recalculent toujours et ne se
stockent jamais. Un bilan de campagne qui bouge n'est pourtant pas un bilan de campagne. La
réponse : rendre **les deux, datés, avec leur écart expliqué**.

> **L'écart devient le signal d'audit** au lieu d'être une ambiguïté.

### 5.4 Le journal des révisions

Répond à « qui a changé la surface plantée, et quand ? ».

`humanAction: false` distingue les recalculs de stade — **c'est le temps qui passe, pas une
décision**. Les afficher au même rang ferait porter à un utilisateur des changements qui ne
sont pas les siens.

### 5.5 Succession, jachères, monoculture

Une parcelle n'est pas une suite de campagnes indépendantes : ce qui y a poussé l'an dernier
conditionne ce qui y pousse cette année.

**La monoculture est un signal agronomique réel** — elle épuise les mêmes réserves du sol et
concentre les ravageurs propres à l'espèce. **Le système disposait de l'information depuis
toujours et ne la disait à personne.**

Les campagnes closes pour `ERREUR_DE_SAISIE` sont **exclues** : elles n'ont jamais occupé le
sol, et les compter fabriquerait une jachère qui n'a pas existé.

### 5.6 L'itinéraire technique — le terme qui manquait

Le système savait ce qui a été **fait** et ce qu'il **conseille**. Il ne savait rien de ce
qui était **prévu**.

> Sans ce terme, une opération **oubliée** est indiscernable d'une opération **jamais
> planifiée**, et le coût d'une campagne ne se connaît qu'après la récolte — trop tard pour
> arbitrer.

**Le rapprochement prévu ↔ réalisé est une inférence, et le système le dit.** Rien
n'établit qu'une fertilisation du 14 mai est celle qui était prévue le 12.

**Il n'est jamais persisté** : un mauvais appariement écrit en base se propage et doit être
défait à la main ; recalculé, il disparaît dès que la donnée s'améliore.

Appariement **un pour un**, glouton sur l'écart croissant — un appariement naïf compterait
une intervention deux fois.

### 5.7 Le clonage

`seedLot` n'est **jamais** repris : un lot est **consommé**. Le reporter serait un mensonge
de traçabilité, sur le champ précisément dont on a besoin le jour où l'on cherche l'origine
d'un problème de levée.

Les opérations en `J+n` se reportent telles quelles ; les dates fermes sont décalées.

### 5.8 Les seuils effectifs

Répond à **« sur quoi le système me juge ? »**.

`origin` distingue un seuil général d'un seuil propre au stade. **Sans lui, l'exploitant
voit le système « changer d'avis »** : le même taux d'humidité déclenche un conseil en
fructification et pas en levée. Dire que le seuil lui-même a changé transforme une
incohérence apparente en information agronomique.

---

## 6. Économie et boucle de retour

### 6.1 `EffectAnalyzer` — le système évalue ses propres conseils

Compare les **48 h avant** et les **48 h après** une intervention.

**Pourquoi 48 h** : assez pour lisser le cycle jour/nuit — sinon une irrigation faite le
matin serait comparée à un après-midi, et l'écart mesurerait la météo — et assez court pour
que l'effet domine encore.

**Le sens de l'amélioration dépend du type** : une irrigation doit faire *monter*
l'humidité.

**`TRAITEMENT` n'a pas de mesure cible.** Produire un écart d'humidité pour un fongicide
donnerait un chiffre sans rapport, **avec l'apparence de la rigueur** — pire que de ne rien
dire.

**Seuil de bruit à 5 %** : une sonde varie de quelques pour cent sans que rien ne se soit
passé.

> ⚠️ `limitation` toujours renseigné : **une comparaison avant/après n'établit pas une
> causalité.** Une pluie survenue dans la même fenêtre produirait le même chiffre.

### 6.2 `MarginCalculator` — tout recalculé, rien stocké

> Un total mis en cache diverge dès la première correction de saisie, et personne ne sait
> plus lequel des deux chiffres croire.

- une récolte sans prix est comptée pour zéro **et signalée** dans `missingData` ;
- `costRatio` est `null` si le produit est nul : diviser par zéro afficherait « charges à
  l'infini » pour une parcelle simplement pas encore récoltée ;
- `marginPerHectare` et `yieldPerHectare` sont `null` sans surface plantée — **ce sont les
  seuls chiffres comparables entre parcelles**.

> ⚠️ `limitation` est une **constante** : « conseils suivis / rendement » est descriptif,
> jamais causal. Le sol, la variété, la météo et l'attention portée à la parcelle varient
> ensemble, et une exploitation ne fournit pas l'échantillon qui permettrait de les démêler.

### 6.3 Le taux de suivi — apprendre sans réentraîner

`/recommendations/uptake` : par type de moteur, combien de conseils appliqués, ignorés,
en attente.

**Un type systématiquement ignoré signale une règle à réviser.** Et `feedbackNote` sur un
rejet porte le *pourquoi*. C'est l'apprentissage à coût nul en calcul.

---

## 7. Les vues composées

### 7.1 La série agrégée

Un mois représente plusieurs milliers de relevés ; cet endpoint en renvoie trente
(min/moy/max par intervalle).

Une mesure jamais relevée est **absente**, pas à zéro : « pas de donnée » et « zéro » ne se
confondent pas.

### 7.2 La chronologie unifiée

Sept sources fusionnées **en mémoire** et triées. Une union SQL sur sept tables aux colonnes
différentes serait illisible et fragile.

**Relevés marquants seulement** (anomalies) : une parcelle instrumentée produit un relevé
toutes les quelques minutes ; les verser tous noierait ce qu'on y cherche. **Un relevé
nominal n'est pas un événement.**

**Pagination après la fusion** : « les vingt derniers événements » ne veut rien dire si
chaque source rend ses vingt derniers séparément.

**`truncated`** : une chronologie plafonnée se lisait **exactement** comme une chronologie
complète. Sur une vue dont l'objet est de raconter ce qui s'est passé, laisser croire à
l'exhaustivité est une erreur de fond.

### 7.3 Les tableaux de bord

`/overview/farm` : l'écran d'accueil en **une requête**. `overallStatus` par précédence :
`SANS_DONNEES` → `CRITIQUE` → `ALERTE` → `VIGILANCE` → `NORMAL`.

> ⚠️ Une parcelle peut apparaître `NORMAL` dans la vue d'ensemble et `VIGILANCE` dans son
> détail — la vigilance fondée sur un **risque** suppose d'exécuter le moteur, ce que seule
> la vue détaillée fait. `limitation` le dit.

---

## 8. Sécurité et cloisonnement

### 8.1 Deux mécanismes qui ne font pas le même travail

| | **Rôle de plateforme** | **Rôle d'exploitation** |
|---|---|---|
| Répond à | quelles **routes** ? | quelles **parcelles**, quels **domaines** ? |
| Valeurs | `SUPER_ADMIN` `ADMIN` `AGRONOME` `TECHNICIEN` `EXPLOITANT` | `PROPRIETAIRE` `OUVRIER` `CONSEILLER` `TECHNICIEN` |

**Ils se composent.** L'un ouvre la route, l'autre décide des données.

### 8.2 `AccessGuard` — le pari qui a tenu

**Le manque.** Rien ne rattachait une requête à un propriétaire : `GET /plots?userId=42`
renvoyait les parcelles de l'utilisateur 42 à quiconque le demandait.

**Le pari.** Le cloisonnement pourrait s'écrire dans chaque service ; il serait alors à
réécrire partout le jour où la notion de propriétaire s'élargit.

> **Ce jour est arrivé avec l'organisation, et le pari a tenu** : l'élargissement aux
> exploitations tient dans cette seule classe. Parce que **tous** les domaines passent par
> `PlotService.require(id)`.

### 8.3 L'organisation n'est jamais bloquante

- `plots.farm_id` et `farms.cooperative_id` **nullables** ;
- une appartenance **ajoute** un accès, n'en retire jamais ;
- archiver une exploitation laisse ses parcelles intactes.

> Une exploitation mal configurée **ne peut enfermer personne dehors**.

**Le rôle module le domaine** : un technicien venu changer une sonde n'a aucune raison de
voir les marges. Confondre les deux reviendrait à ouvrir la comptabilité à quiconque
intervient sur un boîtier — ce qui, **dans un milieu où tout le monde se connaît, est un
problème social avant d'être technique**.

---

## 9. Infrastructure transverse

| Fonction | Le manque comblé |
|---|---|
| **Identifiants Snowflake en chaînes** | 19 chiffres dépassent le safe-integer JS : au-delà, arrondi **silencieux** ⇒ 404 fantômes indébogables |
| **`ApiResponse` / `PaginatedResponse`** | un seul dépaquetage côté client, au lieu de trente |
| **`ENDPOINT_NOT_FOUND` ≠ `RESOURCE_NOT_FOUND`** | « la route n'existe pas » vs « l'entité n'existe pas ». Cette distinction fait gagner des heures |
| **`SemanticSort`** | trier sur `priority` donnait un ordre **alphabétique** : `BASSE` avant `HAUTE`, soit l'inverse de l'urgence |
| **`TimeRange`** | un prédicat `(:from is null or …)` **interdit au planificateur d'utiliser l'index** — précisément celui qui compte sur une série temporelle |
| **Idempotence** | rejouer un POST d'administration renvoie la réponse d'origine ; même clé + corps différent ⇒ 409 |
| **Audit AOP** | qui, quoi, quand, depuis où, sur les écritures d'administration |
| **Cache à deux niveaux** | Redis partagé, Caffeine devant. **Bascule sur Caffeine dès qu'un appel Redis échoue** : une lenteur ne doit pas se propager à l'ingestion |
| **`ConfigurationGuard`** | une posture permissive cesse d'être invisible : elle est journalisée à chaque démarrage |
| **`BilangaProperties`** | trente `@Value` dispersés avaient divergé des clés du fichier — **un seuil qu'on croit régler et qui ne bouge pas est pire qu'un seuil absent** |

---

## 10. Les limites à assumer

**Dites-les vous-même.** Un jury qui les découvre seul conclut que vous ne les aviez pas
vues ; le même jury, à qui vous les présentez encadrées, conclut que vous avez su vous
arrêter au bon endroit.

### 10.1 Les seuils agronomiques ne sont pas validés

C'est **la** limite du travail. Les valeurs semées à l'installation sont **indicatives** et
n'ont pas été validées par une source agronomique congolaise — le commentaire de la
migration le dit explicitement. Or tout `AgronomicEngine` et tout `RiskEngine` en dépendent.

**La méthode de validation à proposer** : faire relire les seuils tomate et manioc par un
agronome, ou les sourcer depuis la littérature (FAO, IRAD, INERA) et citer les références.

### 10.2 Les coûts sont du même ordre

`estimatedCost` porte des ordres de grandeur non validés.

> **Un seuil approximatif oriente une observation ; un prix approximatif oriente une
> décision d'achat.** Le risque n'est pas symétrique.

### 10.3 Sans voisin, pas de détection de dérive

Assumé et écrit dans le code : seule la règle de la valeur figée reste applicable.

### 10.4 Les formulations lingala et kituba ne sont pas relues

À faire valider par un locuteur natif, au même titre que les seuils.

### 10.5 La causalité n'est jamais établie

Ni l'effet d'une intervention, ni le rapprochement conseils/rendement. **Le système le dit
partout où il rend un chiffre** — c'est précisément ce qui rend ces chiffres utilisables.

---

## La phrase de conclusion

> « Le système ne se contente pas de diagnostiquer. Il dit **pourquoi** il conclut, il
> reconnaît **quand il ne sait pas**, et il mesure **ce que ses conseils ont produit**.
> C'est cela que je défends — pas la précision d'un classifieur. »
