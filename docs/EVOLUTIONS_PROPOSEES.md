# Évolutions proposées — champs, détails et fonctionnalités

> **Date** : 2026-07-29. Établi à partir du code réel, après le redressement des sections
> D/E/F/G de `AUDIT_ET_PLAN_AMELIORATION.md`.
> **Objet** : ce qui manque au système pour être plus riche, plus détaillé et capable de
> traiter davantage de situations — classé par rapport valeur/effort, pas par ordre d'idée.
>
> ## ✅ **LES NEUF RANGS SONT LIVRÉS** (migrations V16 → V22, le 2026-07-29)
>
> Le détail d'implémentation — fichiers, décisions, ce qui reste à vérifier — est dans
> **`IMPLEMENTATION_V16_V22.md`**. Ce document-ci reste la trace du **raisonnement** qui a
> conduit à ces choix ; il n'est pas mis à jour au fil du code.

---

## 0. État de livraison

Coché = livré et compilé. ⚠️ = livré partiellement, avec la raison.

### Rangs du séquencement (§10)

| Rang | Proposition | Migration | État |
|:---:|---|:---:|:---:|
| 1 | Champs `plots` + `crops` + géolocalisation | V16 | ✅ |
| 2 | Détection de panne de sonde | V17 | ✅ |
| 3 | Canal SMS | V18 | ✅ |
| 4 | Chronologie + séries agrégées | — | ✅ |
| 5 | Journal d'interventions | V19 | ✅ |
| 6 | Météo | V20 | ✅ |
| 7 | Explication comparative | — | ✅ |
| 8 | Rendement et économie | V21 | ✅ |
| 9 | Exploitation / coopérative | V22 | ✅ |

### Détail par proposition

| § | Élément | État | Note |
|---|---|:---:|---|
| **1.1** | `latitude`, `longitude` | ✅ | débloque §4 et la cartographie |
| 1.1 | `altitude` | ✅ | |
| 1.1 | `irrigationType` | ✅ | + `IrrigationAdapter` : les conseils sont **reformulés**, pas supprimés |
| 1.1 | `plotCode` | ✅ | auto-généré, séquence PostgreSQL |
| **1.2** | `expectedHarvestDate` | ✅ | calculée si absente |
| 1.2 | `cycleDurationDays` | ✅ | + **stade recalculé** (`GrowthStageResolver`) |
| 1.2 | `plantedArea`, `plantDensity` | ✅ | base du rendement (§6) |
| 1.2 | `seedLot` | ✅ | index dédié pour croiser les parcelles |
| **1.3** | `temperatureSol` | ✅ | `temperature` **non renommée** — décision §12.1 |
| 1.3 | `pluviometrie` | ✅ | |
| 1.3 | `conductiviteElectrique` | ✅ | |
| 1.3 | `signalStrength` | ✅ | |
| **1.4** | `lastSeenAt` | ✅ | mis à jour aussi sur `/ingest/health` |
| 1.4 | `firmwareVersion`, `installedAt`, `batteryVoltage` | ✅ | |
| **1.5** | `assignedTo`, `dueAt` | ✅ | + `PATCH /alerts/{id}/assign`, champ `overdue` |
| 1.5 | `estimatedCost` | ✅ | colonne + exposition ; **aucune règle ne la renseigne encore** |
| **2** | Valeur figée / dérive / décrochage | ✅ | `SensorHealthAnalyzer` |
| 2 | `sensorHealth` + alerte technique | ✅ | `DEFAILLANTE` **inhibe le diagnostic** |
| **3** | Entité `Intervention` | ✅ | `recommendation_id` nullable — décision assumée |
| 3 | Boucle conseil → intervention → effet | ✅ | `EffectAnalyzer`, fenêtres 48 h |
| 3 | Traçabilité des intrants, coûts | ✅ | |
| **4** | Client météo | ✅ | Open-Meteo, sans clé d'API |
| 4 | Cache `weather_forecast` | ✅ | TTL 60 min, purge des échéances dépassées |
| 4 | `WeatherEngine` (6ᵉ moteur) | ✅ | 3 règles ; **liste vide** si indisponible |
| **5** | Canal SMS | ✅ | passerelle HTTP configurable, inerte sans URL |
| 5 | Préférences par utilisateur | ✅ | seuil, canaux, langue |
| 5 | Heures de silence | ✅ | enjambent minuit ; `CRITIQUE` passe outre |
| 5 | Regroupement | ✅ | fenêtre 10 min |
| 5 | WhatsApp, e-mail | ❌ | hors périmètre — `NotificationChannel` les accueille sans changement |
| **6** | Entité `Harvest` | ✅ | `crop_id` **obligatoire** |
| 6 | Coûts agrégés depuis `Intervention` | ✅ | agrégation en base, par type |
| 6 | Marge par parcelle et campagne | ✅ | `MarginCalculator` |
| 6 | Comparaison entre parcelles | ✅ | `GET /overview/economics`, tri par marge/ha |
| 6 | Conseils suivis ↔ rendement | ✅ | **présenté comme un constat**, jamais une causalité |
| **7** | `Cooperative → Farm → Plot` | ✅ | **tous les rattachements nullables** |
| 7 | Appartenances avec rôle | ✅ | 4 rôles, 3 domaines d'accès |
| 7 | Élargissement d'`AccessGuard` | ✅ | l'organisation **ajoute** un accès, n'en retire jamais |
| **8.1** | Séries agrégées | ✅ | livré avant ces lots |
| **8.2** | Chronologie unifiée | ✅ | 7 sources, `TimelineComposer` |
| **8.3** | Vue exploitation | ✅ | livré avant ces lots |
| **8.4** | Export CSV | ✅ | point-virgule, virgule décimale, BOM UTF-8 |
| 8.4 | Export PDF | ❌ | hors périmètre |
| **9.1** | Explication comparative | ✅ | `ComparativeExplainer`, 4 cas distincts |
| **9.2** | Apprentissage du retour | ⚠️ | `GET /recommendations/uptake` existe ; **le signalement des règles à réviser reste à faire** |
| **9.3** | Seuils adaptatifs par parcelle | ❌ | non entrepris |
| **9.4** | Risque de voisinage | ❌ | non entrepris — les coordonnées et l'index existent désormais |
| **9.5** | Versionnement de la connaissance | ❌ | non entrepris — la traçabilité par colonnes tient lieu de palliatif |

### Les deux points à trancher (§12) — tranchés

| Question | Décision | Conséquence |
|---|---|---|
| `temperature` : renommer ou compléter ? | **Compléter.** `temperature` reste l'air, `temperature_sol` s'ajoute | Zéro rupture du contrat d'API et de la feature map ML. L'ambiguïté du nom persiste, levée par `COMMENT ON COLUMN` et la documentation |
| La coopérative est-elle dans la cible ? | **Oui**, avec exigence de non-blocage | Hiérarchie complète, mais **purement additive** : rien n'est obligatoire, à aucun niveau |

---

## Principe de sélection

Trois filtres ont été appliqués. Une proposition n'est retenue que si :

1. **elle sert l'agriculteur congolais**, pas la démonstration technique ;
2. **elle s'appuie sur ce qui existe déjà** plutôt que d'ouvrir un chantier parallèle ;
3. **elle renforce le mémoire** — c'est-à-dire qu'elle produit quelque chose de défendable
   devant un jury, pas une fonctionnalité de plus.

Ce qui ne passe pas ces filtres figure au §11, avec le motif du rejet. Une proposition
écartée avec sa raison vaut mieux qu'une liste où tout se vaut.

---

## 1. Champs manquants sur les entités existantes

Effort faible, valeur immédiate. Ce sont des colonnes, pas des fonctionnalités.

### 1.1 `plots` — la géolocalisation est le manque le plus coûteux

| Champ | Type | Pourquoi |
|---|---|---|
| **`latitude`, `longitude`** | `DOUBLE` | **Débloque à lui seul quatre autres propositions** : météo (§4), risque de voisinage (§9.4), carte des parcelles, calcul de tournée. Aujourd'hui `location` est une chaîne libre : « Makotipoko » n'est pas exploitable par une machine. |
| `altitude` | `DOUBLE` | Une même température ne s'interprète pas à 300 m et à 900 m. Le Congo a un relief marqué. |
| **`irrigationType`** | `VARCHAR` — `PLUVIAL`, `GOUTTE_A_GOUTTE`, `ASPERSION`, `MANUEL` | Le moteur conseille « irriguer » sans savoir si c'est possible. Sur une parcelle **pluviale**, ce conseil est inapplicable et décrédibilise le système. Il faudrait dire « pailler pour retenir l'humidité » à la place. |
| `plotCode` | `VARCHAR` unique | Référence lisible (`PARC-2026-014`) pour la communication orale et le papier. `CodeComposer` existe déjà (héritage fintech), il est réutilisable. |

> **`irrigationType` est le champ le plus sous-estimé de cette liste.** Il ne coûte presque
> rien et évite au moteur de produire des conseils inapplicables — le défaut qui fait qu'un
> exploitant cesse de lire les recommandations.

### 1.2 `crops`

| Champ | Type | Pourquoi |
|---|---|---|
| `expectedHarvestDate` | `DATE` | Permet « J-18 avant récolte », et surtout de **faire avancer le stade automatiquement** au lieu d'attendre une saisie manuelle — aujourd'hui `growthStage` se périme en silence et le diagnostic raisonne sur un stade faux. |
| `cycleDurationDays` | `INTEGER` | Idem, par culture et variété. |
| `plantedArea`, `plantDensity` | `DOUBLE`, `INTEGER` | Base du calcul de rendement (§6) et du dosage des intrants. |
| `seedLot` | `VARCHAR` | Traçabilité : un lot défaillant se repère en croisant les parcelles. |

### 1.3 `sensor_readings` — deux ajouts qui changent la qualité du diagnostic

| Champ | Type | Pourquoi |
|---|---|---|
| **`temperatureSol`** | `DOUBLE` | Le champ actuel s'appelle `temperature` sans préciser air ou sol. Les moteurs le comparent aux seuils `tempMin/tempMax` de la culture — de fait, l'air. **La température du sol commande pourtant la germination et la tubérisation du manioc.** L'ambiguïté actuelle est un défaut de modélisation, pas seulement un champ manquant. |
| **`pluviometrie`** | `DOUBLE` | Au Congo, la saison des pluies structure tout. Sans elle, le moteur conseille d'irriguer un sol que la pluie va saturer dans l'heure. |
| `conductiviteElectrique` | `DOUBLE` | Mesure standard des sondes de sol : salinité et charge en engrais. Complète utilement N/P/K. |
| `signalStrength` | `INTEGER` | Diagnostic réseau du boîtier ; distingue « sonde en panne » de « couverture faible ». |

> Renommer `temperature` en `temperatureAir` clarifierait le modèle, mais **casse le contrat
> d'API et la feature map envoyée au microservice d'inférence**. À trancher : soit on
> renomme maintenant, tant que le frontend n'est pas figé, soit on ajoute `temperatureSol`
> et on documente que `temperature` désigne l'air.

### 1.4 `iot_devices` — gestion de parc

| Champ | Pourquoi |
|---|---|
| `lastSeenAt` | Aujourd'hui déduit du dernier relevé, ce qui confond « boîtier muet » et « parcelle sans relevé ». Une colonne dédiée, mise à jour à chaque contact (y compris `/ingest/health`), lève l'ambiguïté. |
| `firmwareVersion` | Indispensable dès qu'il y a plus de trois boîtiers sur le terrain. |
| `installedAt`, `batteryVoltage` | Âge du matériel ; la tension brute vieillit mieux que le pourcentage. |

### 1.5 `alerts` et `recommendations` — de l'information à l'action

| Champ | Pourquoi |
|---|---|
| `assignedTo` | Une alerte sans responsable désigné n'est traitée par personne. Le cycle de vie existe (§8.4 de la doc API) ; il lui manque un destinataire. |
| `dueAt` | « À traiter sous 48 h » transforme un conseil en engagement. |
| `estimatedCost` sur la recommandation | Un conseil chiffré se compare à son bénéfice ; c'est ce qui permet l'analyse de marge (§6). |

---

## 2. Détection de panne de sonde par cohérence

**Effort : faible. Valeur : élevée. Aucune dépendance.**

Le contrôle actuel (`PlausibilityChecker`) n'attrape que l'absurde : pH 22, humidité 130 %.
Or **une sonde qui tombe en panne renvoie rarement une valeur absurde** — elle se fige, elle
dérive lentement, ou elle décroche de ses voisines. Trois règles suffisent :

| Signal | Détection | Conclusion |
|---|---|---|
| **Valeur figée** | même valeur exacte sur N relevés consécutifs | sonde bloquée |
| **Dérive** | moyenne mobile qui s'éloigne durablement des autres boîtiers de la parcelle | sonde à étalonner |
| **Décrochage** | écart persistant à la médiane des sondes voisines du même type | sonde défaillante |

Le résultat alimenterait un champ `sensorHealth` (`SAINE`, `SUSPECTE`, `DEFAILLANTE`) et une
alerte de niveau technique, distincte des alertes agronomiques.

> **Pourquoi c'est important au-delà du confort** : un diagnostic fondé sur une sonde qui
> dérive est un diagnostic faux présenté avec la même assurance qu'un diagnostic juste. La
> confiance du modèle ne mesure pas la fiabilité du capteur. C'est le seul angle mort du
> système qui puisse produire un conseil nuisible.

---

## 3. Journal d'interventions — le chaînon manquant

**Effort : moyen. Valeur : très élevée. Débloque §6 et §9.2.**

Le système conseille, et ne sait pas ce qui a été fait. `Observation` ne porte qu'une note
libre ; `RecommendationStatus.APPLIQUEE` dit qu'un conseil a été suivi, sans dire comment.

Nouvelle entité `Intervention` :

```
id, plot_id, crop_id, recommendation_id (nullable)
type          IRRIGATION | FERTILISATION | TRAITEMENT | DESHERBAGE | SEMIS | RECOLTE | AUTRE
product       nom du produit (engrais, fongicide)
dose, unit    quantité appliquée
cost          coût réel
performed_at  date d'exécution
performed_by  utilisateur
weather_note  conditions au moment de l'intervention
```

Ce que cela ouvre :

- **Boucle complète** : conseil → intervention → mesure de l'effet. Aujourd'hui la chaîne
  s'arrête au conseil.
- **Efficacité réelle des traitements** : comparer l'évolution des mesures et des
  diagnostics avant/après une intervention. C'est une contribution originale et défendable
  pour le mémoire — le système ne se contente plus de conseiller, il évalue ses conseils.
- **Traçabilité des intrants**, condition de toute démarche de certification.
- **Coûts**, base de l'analyse économique.

> `Intervention` doit pouvoir exister **sans** recommandation associée : les exploitants
> agissent aussi de leur propre chef, et ces actions-là expliquent souvent une évolution
> que le système ne comprendrait pas autrement.

---

## 4. Météo — le levier le plus fort

**Effort : moyen. Valeur : très élevée. Dépend de §1.1 (géolocalisation).**

Le moteur raisonne exclusivement sur le **passé mesuré**. Il ignore ce qui arrive.

### Ce que la prévision change concrètement

| Situation actuelle | Avec la prévision |
|---|---|
| « Humidité du sol à 24 %, irriguez sans délai » | « …mais 18 mm de pluie sont attendus d'ici 6 h : différez l'irrigation. » |
| Risque de mildiou calculé sur l'humidité mesurée | Risque **projeté** : 3 jours d'humidité > 85 % annoncés ⇒ alerte préventive avant l'apparition |
| Conseil de traitement | « Ne traitez pas aujourd'hui : pluie annoncée dans 2 h, le produit sera lessivé. » |

### Implémentation

- Client REST vers un fournisseur météo, sur le modèle de `MlHttpExchange` déjà écrit
  (timeouts, réessai, `ServiceUnavailableException`) — le patron est en place.
- Table `weather_forecast` en cache, une ligne par parcelle et par échéance.
- Un sixième moteur dans `knowledge`, `WeatherEngine`, aligné sur les cinq autres.
- **Le système doit rester utilisable sans météo** : c'est la règle appliquée au
  microservice d'inférence, elle vaut ici aussi.

> C'est la proposition qui fait le plus basculer le système du **constat** vers
> l'**anticipation** — l'intention déjà portée par `TrendAnalyzer`, mais limitée à
> l'extrapolation des mesures internes.

---

## 5. Notifications SMS — le canal qui compte réellement

**Effort : faible à moyen. L'infrastructure est déjà écrite.**

L'outbox, la reprise, l'interface `NotificationChannel` et l'accroche au commit existent
(migration V15). Seul le canal `LOG` est implémenté : **aucune alerte ne sort du serveur**.

Priorité des canaux dans le contexte congolais :

1. **SMS** — fonctionne sur téléphone simple, sans données, avec une couverture bien
   supérieure à l'internet mobile. C'est le canal qui atteint réellement l'exploitant.
2. **WhatsApp** — très répandu en milieu urbain et périurbain, permet l'image.
3. **E-mail** — pour les administrateurs et les rapports, pas pour l'urgence terrain.

À ajouter par-dessus :

- **Préférences par utilisateur** : niveau minimal, canaux retenus, langue.
- **Heures de silence** : ne pas réveiller quelqu'un à 3 h pour une alerte `ELEVEE` ;
  `CRITIQUE` passe outre.
- **Regroupement** : cinq alertes en dix minutes doivent faire un message, pas cinq.

---

## 6. Rendement et économie

**Effort : moyen. Dépend de §1.2 et §3.**

Le système dit ce qu'il faut faire. Il ne dit pas si cela a rapporté.

- Entité `Harvest` : quantité, qualité, date, prix de vente.
- Coûts agrégés depuis `Intervention`.
- **Marge par parcelle et par campagne**, comparaison entre parcelles et entre saisons.
- Mise en regard : les parcelles où les conseils ont été suivis ont-elles mieux produit ?

> C'est ce qui transforme l'outil d'un assistant technique en outil de gestion — et, pour
> le mémoire, ce qui permet de **quantifier l'apport de la plateforme** au lieu de le
> postuler. Une évaluation chiffrée vaut mieux qu'une démonstration fonctionnelle.

---

## 7. Exploitation et coopérative

**Effort : élevé. À décider tôt, car cela touche le modèle de données.**

Aujourd'hui : `Plot → Users`. Un exploitant, des parcelles. Ce modèle ne couvre pas :

- une **exploitation** regroupant des parcelles dispersées ;
- une **coopérative** — forme dominante de l'agriculture congolaise ;
- un **agronome-conseil** suivant plusieurs exploitations ;
- un **technicien** intervenant sur le matériel sans accès aux données économiques.

Proposition : `Cooperative → Farm → Plot`, avec des appartenances portant un rôle
(`PROPRIETAIRE`, `OUVRIER`, `CONSEILLER`, `TECHNICIEN`).

> **Décision à prendre maintenant plutôt que plus tard.** Introduire un niveau
> d'organisation après coup impose de reprendre le cloisonnement de chaque route. Si la
> coopérative est dans la cible du mémoire, il faut au minimum réserver la place dans le
> schéma dès à présent.

---

## 8. Restitution et détail

**Effort : faible. Valeur : élevée pour le frontend.**

### 8.1 Séries temporelles agrégées

```
GET /plots/{id}/history?from=&to=&granularity=hour|day|week&measures=ph,humidite_sol
```

Renvoie min / moyenne / max par intervalle. Aujourd'hui le frontend doit rapatrier des
milliers de relevés bruts pour tracer une courbe. L'agrégation appartient à la base.

### 8.2 Chronologie unifiée

```
GET /plots/{id}/timeline?from=&to=
```

Un seul flux chronologique : relevés marquants, diagnostics, alertes, interventions,
observations, changements de stade. **C'est la vue qui raconte l'histoire de la parcelle**,
et elle est aujourd'hui impossible à composer sans quatre appels et un tri côté client.

### 8.3 Vue exploitation

```
GET /overview/farm
```

Agrégat multi-parcelles : répartition des statuts, alertes par niveau, boîtiers
silencieux, batteries faibles, risques dominants. `PlotSummary` existe déjà par parcelle ;
il manque le niveau au-dessus.

### 8.4 Export

Rapport de parcelle en PDF, séries en CSV. Utile à l'exploitant, à l'agronome — et pour
les annexes du mémoire.

---

## 9. Enrichissement du moteur — l'angle mémoire

Ces propositions ne sont pas des fonctionnalités mais des **contributions défendables
devant un jury**.

### 9.1 Explication comparative

`DiagnosisResult` porte déjà `alternatives` et `risks`. En les croisant :

> « Mildiou retenu (97 %) plutôt qu'alternariose (2 %) : les deux partagent les taches
> foliaires, mais l'humidité mesurée (89 %) réunit les conditions du mildiou et non celles
> de l'alternariose. »

Répondre à « pourquoi pas l'autre maladie ? » est le propre d'un système explicable.
Les données sont là ; il manque la mise en regard.

### 9.2 Apprentissage du retour

Le retour sur conseil et `GET /recommendations/uptake` viennent d'être livrés. Exploités :

- une règle **systématiquement ignorée** est signalée à l'administrateur comme candidate à
  révision ;
- les motifs de rejet saisis (`feedbackNote`) sont regroupés par règle ;
- à terme, pondération des règles par leur taux d'application.

> **Le système apprend de son usage sans réentraîner de modèle.** C'est un angle
> original, peu coûteux, et qui distingue nettement le travail d'une simple intégration de
> classifieur.

### 9.3 Seuils adaptatifs par parcelle

Les seuils de `crop_requirement` sont génériques et, la migration V10 le dit,
**indicatifs**. Apprendre une ligne de base par parcelle permettrait de distinguer « ce sol
est naturellement à 30 % d'humidité » de « ce sol s'assèche ».

### 9.4 Risque de voisinage

*(dépend de §1.1)* Une maladie détectée sur une parcelle élève le risque sur les parcelles
proches. La propagation est un fait agronomique que le système ignore aujourd'hui, alors
qu'il dispose de tous les diagnostics.

### 9.5 Versionnement de la base de connaissance

Un diagnostic est justifié par les règles **du moment où il a été produit**.
`DiagnosisExplainer` s'appuie déjà sur les colonnes de traçabilité pour cette raison
précise. Versionner les règles rendrait tout diagnostic rejouable — et le raisonnement
auditable a posteriori.

---

## 10. Séquencement recommandé

L'ordre suit les dépendances et le rapport valeur/effort, pas l'ordre d'exposé.

| Rang | Proposition | Effort | Débloque | État |
|:---:|---|:---:|---|:---:|
| 1 | **Champs `plots` + `crops` + géolocalisation** (§1.1, §1.2) | faible | §4, §9.4, cartographie | ✅ V16 |
| 2 | **Détection de panne de sonde** (§2) | faible | fiabilité de tout le reste | ✅ V17 |
| 3 | **Canal SMS** (§5) | faible | l'alerte atteint enfin quelqu'un | ✅ V18 |
| 4 | **Chronologie + séries agrégées** (§8.1, §8.2) | faible | le frontend de Rolle | ✅ |
| 5 | **Journal d'interventions** (§3) | moyen | §6, §9.2 | ✅ V19 |
| 6 | **Météo** (§4) | moyen | l'anticipation | ✅ V20 |
| 7 | **Explication comparative** (§9.1) | faible | argument mémoire | ✅ |
| 8 | **Rendement et économie** (§6) | moyen | évaluation chiffrée | ✅ V21 |
| 9 | **Exploitation / coopérative** (§7) | élevé | à arbitrer tôt | ✅ V22 |

> **Rang 9 livré (2026-07-29) — et volontairement non bloquant.** `Cooperative → Farm → Plot`
> avec `farm_membership` porteuse d'un rôle. Le principe qui gouverne toute la migration :
> **l'organisation est purement additive**.
>
> - `plots.farm_id` et `farms.cooperative_id` sont **nullables** ; une parcelle sans
>   exploitation se comporte exactement comme avant la V22, et aucune requête supplémentaire
>   n'est même émise pour elle (`AccessGuard.roleOn` court-circuite sur `farm == null`).
> - Une appartenance **ajoute** un accès, elle n'en retire aucun : le propriétaire direct
>   garde le sien même s'il n'est pas membre de l'exploitation à laquelle sa parcelle est
>   rattachée. Une exploitation mal configurée ne peut donc enfermer personne dehors.
> - Archiver une exploitation laisse ses parcelles intactes : elles redeviennent
>   indépendantes, et leur propriétaire n'a rien perdu.
>
> Le pari posé à l'écriture d'`AccessGuard` a tenu : l'élargissement tient dans cette seule
> classe, parce que `PlotService.require(id)` est le passage obligé de tous les domaines —
> cultures, boîtiers, relevés, diagnostics, observations, interventions, récoltes.
>
> `MembershipRole` module l'accès par domaine (`AccessScope`) : `TECHNICIEN` voit le
> matériel, pas l'agronomie ni l'économie ; `OUVRIER` et `CONSEILLER` voient l'agronomie mais
> pas les marges ; `PROPRIETAIRE` voit tout. Appliqué aujourd'hui au bilan économique
> (`/plots/{id}/economics`, `/overview/economics`).

### Si le temps est compté

Les rangs **1, 2, 3 et 7** tiennent en une poignée de jours, ne dépendent de rien, et
couvrent les trois besoins exprimés : plus de détail (champs, explication comparative),
plus d'éventualités traitées (panne de sonde), plus de portée (SMS).

Le rang **5** (interventions) est celui qui apporte le plus au mémoire pour un effort
raisonnable : il ferme la boucle conseil → action → effet, seul moyen de démontrer que le
système sert à quelque chose.

---

## 11. Ce que je ne recommande pas

Ces pistes reviennent naturellement mais coûteraient plus qu'elles ne rapportent ici.

| Piste | Motif du rejet |
|---|---|
| **Réactiver le scaffolding fintech** (RabbitMQ, Batch, Quartz, JobRunr) | Ces dépendances sont mortes et devraient être **retirées**. Les réactiver ferait entrer une infrastructure qu'aucun besoin ne justifie. Un simple `@Scheduled` suffirait le jour venu. |
| **Microservices** | Un module Maven, deux développeurs. Le découpage coûterait en complexité ce qu'il ne rapporterait ni en performance ni en clarté. |
| **Réentraînement des modèles depuis le backend** | Le microservice d'inférence est un système tiers ; le coupler à son cycle d'entraînement contredit la séparation qui fait la solidité de l'architecture. Exporter un jeu de données annoté, oui ; piloter l'entraînement, non. |
| **Chat / assistant conversationnel** | Effet de mode. La valeur est dans le conseil juste et son explication, pas dans son emballage. À reconsidérer une fois le reste solide. |
| **Application mobile native** | Une PWA couvre le besoin terrain à une fraction du coût, et le hors-ligne se traite côté backend (l'infrastructure d'idempotence existe déjà). |
| **Multilingue complet** | Le lingala et le kituba auraient du sens pour les messages d'alerte — mais traduire toute l'interface d'administration serait disproportionné. À cibler sur les notifications, si cela se fait. |

---

## 12. Deux points à trancher avant de commencer

1. **`temperature` : renommer ou compléter ?** (§1.3) Renommer en `temperatureAir` clarifie
   le modèle mais casse le contrat d'API et la feature map du microservice. C'est
   maintenant ou jamais — le frontend n'est pas encore figé.
2. **La coopérative est-elle dans la cible ?** (§7) Si oui, il faut réserver la place dans
   le schéma dès à présent ; l'introduire après coup imposerait de reprendre le
   cloisonnement de chaque route.
