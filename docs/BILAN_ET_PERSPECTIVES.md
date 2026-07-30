# Bilan, audit et perspectives

> **Établi le 2026-07-30**, après les migrations **V16 → V24**.
> Complète `IMPLEMENTATION_V16_V22.md` (ce qui a été fait, comment) par : **où en est le
> système**, **ce qui ne va pas**, et **ce qu'il reste à construire**.

---

## 1. Bilan

### 1.1 Ce que le système sait faire aujourd'hui

```
ESP32 → /ingest/readings
   │
   ├─ plausibilité de la MESURE          PlausibilityChecker
   ├─ santé de la SONDE                  SensorHealthAnalyzer     ← V17
   │    └─ défaillante ⇒ diagnostic SUSPENDU
   ├─ relevé persisté                    ← toujours, quoi qu'il arrive ensuite
   │
   └─ diagnostic
        ├─ stade RECALCULÉ               GrowthStageResolver      ← V16
        ├─ prédiction IA                 Vision | Tabular
        ├─ 6 moteurs de connaissance     dont WeatherEngine       ← V20
        ├─ arbitrage des conflits        ajoute, ne retire jamais
        ├─ adaptation à la parcelle      IrrigationAdapter        ← V16
        ├─ explication comparative       ComparativeExplainer     ← rang 7
        └─ alerte → outbox → SMS         RecipientResolver        ← V18
             │
             └─ intervention déclarée    ← V19
                  └─ EFFET MESURÉ        EffectAnalyzer
                       └─ récolte → MARGE                         ← V21
```

**La boucle est fermée** : mesure → diagnostic → conseil → action → effet → rendement. C'est ce
qui distingue le système d'une intégration de classifieur.

### 1.2 Décompte

| | Avant (2026-07-29 matin) | Aujourd'hui |
|---|---|---|
| Migrations | 15 | **24** |
| Modules métier | 6 | **10** |
| Moteurs de connaissance | 5 | **7** |
| Contrôleurs REST | 20 | **26** |
| Rôles / permissions en base | 0 / 0 | **5 / 36** |
| Tests | 1 | **1** ⚠️ |

### 1.3 Les cinq invariants tenus

1. **Le relevé n'est jamais perdu.** Persisté avant toute opération faillible.
2. **Une capacité indisponible retire une capacité, elle ne casse rien.** Météo, SMS, santé des
   sondes, microservice IA : chacun se tait proprement.
3. **L'organisation est purement additive.** Aucune parcelle n'a besoin d'exploitation ; une
   appartenance ajoute un accès, n'en retire jamais.
4. **On reformule, on n'efface pas.** `IrrigationAdapter` et `ConflictArbitrator` complètent.
5. **Jamais un chiffre sans réserve.** `limitation`, `missingData`, `dataQualityNote` sont
   systématiquement renseignés.

---

## 2. Audit — ce qui ne va pas

Classé par gravité réelle, pas par facilité de correction.

### 🔴 Critique

| # | Constat | Conséquence | Effort |
|:--:|---|---|:---:|
| **A1** | **Un seul test** (`contextLoads`) pour ~26 000 lignes | Aucune régression n'est détectable. Toute modification du moteur agronomique se vérifie à l'œil | moyen |
| **A2** | **`permitAll("/**")`** court-circuite tout le contrôle par URL | Le RBAC de la V24 est écrit et **inerte**. Chaque route métier est ouverte | faible |
| **A3** | **Auto-admin actif** — requête sans jeton authentifiée comme administrateur | Combiné à A2, il n'y a **aucune sécurité effective** en dev | faible |
| **A4** | **Secret JWT codé en dur** dans `JWTService` | Un jeton peut être forgé par quiconque lit le dépôt | faible |

> **A2 + A3 + A4 forment un ensemble.** Corriger A2 seul sans compte administrateur
> fonctionnel bloque tout le monde. L'ordre est décrit dans `RBAC_FRONTEND.md` §7.

### 🟠 Important

| # | Constat | Conséquence | Effort |
|:--:|---|---|:---:|
| **A5** | **Aucun parcours fonctionnel joué** sur les rangs 1→9 | Le schéma est vérifié, le comportement non. Des bugs de câblage restent probables | moyen |
| **A6** | **`@Async` sans `@EnableAsync`** sur `AuditServiceImpl.save` | L'audit s'exécute en synchrone et ralentit chaque écriture administrative | trivial |
| **A7** | **`AuditContext` jamais appelé** | `metadata_json` et `diff_json` restent vides : l'audit dit *qui* et *quoi*, jamais *quel changement* | faible |
| **A8** | **Scaffolding fintech toujours présent** — RabbitMQ, Batch, Quartz, JobRunr, WebSocket, HATEOAS, Freemarker, MinIO | ~15 dépendances inutilisées ; temps de démarrage, surface d'attaque, confusion à la lecture | faible |
| **A9** | **`.env` incohérent** — port 5434 vs 55820, deux mots de passe | Un démarrage sur la mauvaise base est possible | trivial |
| **A10** | **CORS `allowedOriginPatterns("*")`** | Acceptable en dev, à restreindre avant toute exposition | trivial |

### 🟡 À surveiller

| # | Constat | Conséquence |
|:--:|---|---|
| **A11** | `estimatedCost` exposé mais **aucune règle ne le renseigne** | Champ toujours `null` — promesse non tenue côté frontend |
| **A12** | Seuils agronomiques **indicatifs**, jamais validés par un agronome | Le cœur métier repose sur des valeurs non sourcées. **C'est le point le plus fragile devant un jury** |
| **A13** | `GeneratorOfId` fait des `System.out.println` | Bruit à chaque identifiant généré |
| **A14** | Purge de `weather_forecast` déclenchée au rafraîchissement seulement | Une parcelle qui cesse d'être interrogée conserve ses prévisions périmées |
| **A15** | Envoi d'e-mails commenté | Codes OTT et de réinitialisation renvoyés **dans la réponse API** |
| **A16** | Swagger inaccessible (401) | Le contrat n'est pas explorable |
| **A17** | Pas d'index sur `sensor_readings(device_id, recorded_at)` | `SensorHealthAnalyzer` interroge par boîtier à chaque ingestion |
| **A18** | `TimelineComposer` plafonne à 200 par source **sans le dire** | Une chronologie tronquée se lit comme une chronologie complète |

> **A12 mérite d'être traité comme un risque de soutenance, pas comme une dette technique.**
> Un jury demandera d'où viennent les seuils. « Indicatifs, à valider » est une réponse
> acceptable si elle est assumée dans le mémoire ; découverte à l'oral, elle est coûteuse.

---

## 3. Ce qu'il faut faire ensuite — par valeur

### 3.1 Avant toute démonstration

| Priorité | Action | Pourquoi | Effort |
|:---:|---|---|:---:|
| 1 | **Jouer les 9 parcours fonctionnels** (`IMPLEMENTATION_V16_V22.md` §7.2) | Le seul moyen de savoir si ça marche | 1 j |
| 2 | **Seed de démonstration** : 2 parcelles géolocalisées, **2 boîtiers sur la même** (sans voisin, pas de détection de dérive), 1 culture datée, interventions, 1 récolte | Sans données, rien n'est montrable | ½ j |
| 3 | **Tests unitaires du cœur** (§3.2) | Rend les corrections sûres | 1–2 j |
| 4 | **Durcir la sécurité** dans l'ordre du `RBAC_FRONTEND.md` §7 | A2+A3+A4 | ½ j |
| 5 | **Valider les seuils agronomiques** ou l'assumer explicitement dans le mémoire | A12 | variable |

### 3.2 Les tests à écrire en premier

Ces classes sont **sans état et sans transaction** : instanciables directement, **aucune base
requise**. C'est le meilleur rapport valeur/effort du projet.

| Classe | Ce qu'un test attraperait |
|---|---|
| `GrowthStageResolver` | Bornes de stade, cycle dépassé, plantation future, culture inconnue |
| `PlausibilityChecker` | Chaque borne, mesure absente ≠ mesure fausse |
| `SensorHealthAnalyzer` | Valeur figée, dérive, décrochage, **absence de voisin** |
| `IrrigationAdapter` | Pluvial reformulé, `null` **non** traité comme pluvial, traçabilité préservée |
| `ComparativeExplainer` | Les **4 cas** d'énoncé, dont « les mesures penchent pour l'alternative » |
| `EffectAnalyzer` | Sens de l'amélioration par type, seuil de bruit, type non mesurable |
| `MarginCalculator` | Récolte sans prix, surface absente, produit nul (division) |
| `CsvSeriesWriter` | Séparateur, virgule décimale, cellule vide ≠ zéro |
| `AdminApiAuthorizationManager` | **Chaque route → permission**, `/ingest` sans jeton, route non cartographiée |

> Le dernier est le plus rentable : il fige la matrice d'autorisation, et c'est là qu'une
> erreur coûte le plus cher.

### 3.3 Corrections rapides

| Action | Effort |
|---|:---:|
| Ajouter `@EnableAsync` (A6) | 1 ligne |
| Retirer les `System.out.println` de `GeneratorOfId` (A13) | 2 lignes |
| Aligner `.env` sur `application.yaml` (A9) | 5 min |
| Index `sensor_readings(device_id, recorded_at DESC)` (A17) — **V25** | 10 min |
| Exposer `truncated: true` dans `PlotTimeline` (A18) | 15 min |
| Retirer les dépendances fintech du `pom.xml` (A8) | 1 h + vérification |
| Ouvrir Swagger en dev (A16) | 15 min |

---

## 4. Fonctionnalités implémentables

### 4.1 Le prochain candidat naturel

**Risque de voisinage** (§9.4 de `EVOLUTIONS_PROPOSEES.md`) — *effort faible, valeur élevée*.

Les coordonnées **et** l'index géographique existent depuis la V16, et tous les diagnostics
sont en base. Il ne manque que la requête :

> Une maladie détectée sur une parcelle **élève le risque** sur les parcelles proches. La
> propagation est un fait agronomique que le système ignore, alors qu'il dispose de tout pour
> le voir.

Un huitième moteur, `NeighbourhoodEngine`, sur le patron des sept autres. Rayon configurable
(2 km par défaut), pondération par la distance et la fraîcheur du diagnostic.

**Pour le mémoire**, c'est une contribution originale peu coûteuse : le système passe de
« diagnostiquer une parcelle » à « raisonner sur un territoire ».

### 4.2 Les autres, par rapport valeur/effort

| Fonctionnalité | Valeur | Effort | Débloque / remarque |
|---|:---:|:---:|---|
| **Risque de voisinage** | ●●● | ● | tout est prêt |
| **Signalement des règles à réviser** (§9.2) | ●●● | ● | `GET /recommendations/uptake` fournit déjà la donnée ; il manque le seuil et l'écran |
| **Canal e-mail** | ●● | ● | `NotificationChannel` l'accueille sans rien changer |
| **`estimatedCost` sur les règles** (A11) | ●● | ● | colonne + exposition faites ; il manque la saisie côté knowledge |
| **Seuils adaptatifs par parcelle** (§9.3) | ●●● | ●● | apprendre une ligne de base : distinguer « ce sol est à 30 % » de « ce sol s'assèche » |
| **Export PDF** (§8.4) | ●● | ●● | annexes du mémoire |
| **Canal WhatsApp** | ●● | ●● | image possible, très répandu en périurbain |
| **Versionnement de la connaissance** (§9.5) | ●●● | ●●● | rendrait tout diagnostic **rejouable** — argument fort, chantier réel |
| **Jeu de données annoté exportable** | ●●● | ●● | permettrait de réentraîner les modèles **sans** coupler le backend à leur cycle |
| **PWA hors ligne** | ●●● | ●●● | l'infrastructure d'idempotence existe déjà côté serveur |

### 4.3 Trois idées qui n'étaient pas au catalogue

**Rejeu de diagnostic** — reprendre un relevé passé et relancer le raisonnement avec les
seuils actuels, pour comparer. Cela transforme la base de connaissance en objet
expérimentable : « qu'aurait dit le système si ce seuil avait été à 32 % ? ». Peu coûteux
(tout est déjà en base), et directement exploitable dans un mémoire.

**Calendrier cultural prévisionnel** — `GrowthStageResolver` sait déjà projeter les stades à
venir (`stageTimeline` les inclut). Les exposer donnerait « floraison attendue dans 9 jours,
prévoyez le traitement préventif ». C'est du travail de restitution, pas de moteur.

**Comparaison inter-parcelles pour une même culture** — `/overview/economics` compare déjà les
marges. Le même croisement sur les **mesures** dirait « votre parcelle Nord est 12 % plus
humide que vos autres parcelles en tomate à stade égal ». Utile, et purement calculatoire.

### 4.4 Ce qu'il ne faut toujours pas faire

| Piste | Motif |
|---|---|
| Réactiver le scaffolding fintech | À **retirer**, pas à réveiller. Un `@Scheduled` suffirait le jour venu |
| Microservices | Un module, deux développeurs. Coût en complexité sans gain |
| Réentraînement piloté depuis le backend | Contredit la séparation qui fait la solidité de l'architecture. Exporter un jeu annoté, oui |
| Assistant conversationnel | La valeur est dans le conseil juste et son explication, pas son emballage |
| Application mobile native | Une PWA couvre le besoin à une fraction du coût |
| Multilingue complet | Cibler les **notifications** (lingala, kituba) aurait du sens ; traduire l'administration, non |

---

## 5. Pour le mémoire — les cinq arguments défendables

Ce qui distingue ce travail d'une intégration de classifieur :

1. **La boucle est fermée** — conseil → action → effet mesuré. `EffectAnalyzer` évalue les
   conseils du système avec ses propres données.
2. **Deux voies indépendantes qui se confrontent** — un réseau convolutif sur images, un moteur
   déterministe sur mesures de sol. Aucune information en commun : quand elles concordent, la
   conclusion tient sur deux pieds ; quand elles divergent, `ComparativeExplainer` le dit.
3. **Le système reconnaît ses limites** — `limitation`, `missingData`, `dataQualityNote`,
   `reliable: false`. Un chiffre y est toujours accompagné de ce qu'il ne prouve pas.
4. **La fiabilité du capteur est distinguée de la confiance du modèle** — `SensorHealthAnalyzer`
   traite le seul angle mort capable de produire un conseil *nuisible*.
5. **Le système apprend de son usage sans réentraîner** — le retour sur conseil et le taux
   d'application ouvrent la révision des règles, à coût nul en calcul.

**La faiblesse à assumer plutôt qu'à masquer** : les seuils agronomiques sont indicatifs
(A12). Le dire, et expliquer comment ils seraient validés, vaut mieux que d'attendre la
question.

---

## 6. Documents du projet

| Document | Contenu |
|---|---|
| `CLAUDE.md` | Mémoire de projet, chargée à chaque session |
| `ARCHITECTURE.md` | Contexte technique complet : modules, flux, schéma |
| **`IMPLEMENTATION_V16_V22.md`** | **Document de reprise** — à lire en premier |
| `API_FRONTEND.md` | Contrat exposé aux clients |
| `API_BACKEND.md` | Le *pourquoi* du backend : décisions, invariants, réflexes |
| **`RBAC_FRONTEND.md`** | Rôles, permissions, écrans d'administration |
| **`BILAN_ET_PERSPECTIVES.md`** | Ce document |
| `EVOLUTIONS_PROPOSEES.md` | Raisonnement à l'origine des rangs 1→9 |
| `AUDIT_ET_PLAN_AMELIORATION.md` | Audit antérieur, partiellement daté |
| ~~`Documentation_API_BILANGA (3).md`~~ | **Obsolète** (24 juillet) — à archiver, il contredit les autres |
