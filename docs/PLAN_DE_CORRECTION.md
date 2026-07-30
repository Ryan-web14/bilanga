# Plan de correction et de construction

> **Établi le 2026-07-30.** Répond aux §3 (audit) et §4 (perspectives) de
> `BILAN_ET_PERSPECTIVES.md`.
>
> **Ce document est fait pour être exécuté lot par lot**, dans l'ordre. Chaque lot porte son
> objectif, ses actions, sa vérification et son risque. Un lot se termine par une vérification
> qui passe — pas par « le code est écrit ».

---

## 0. Deux corrections à l'audit

Avant de bâtir sur l'audit, deux constats vérifiés en base qui l'amendent :

| Réf | Ce que j'avais écrit | La réalité |
|---|---|---|
| **A17** | « Pas d'index sur `sensor_readings(device_id, …)` » | **Faux en partie.** `idx_readings_device` existe sur `(device_id)`. Il manque seulement le **composite** `(device_id, recorded_at DESC)`, que la requête de `SensorHealthAnalyzer` sait exploiter. Gravité revue à la baisse |
| **A8** | « ~15 dépendances inutilisées » | **19 artefacts** exactement (AMQP, Batch, Quartz, JobRunr, WebSocket, HATEOAS, Freemarker, Session Redis, Session JDBC — chacun avec sa variante `-test`) |

`@EnableAsync` est bien absent (A6 confirmé). `@EnableCaching` est présent.

---

## 1. Principes du plan

**Trois règles qui gouvernent l'ordre des lots.**

1. **Rien ne se corrige à l'aveugle.** Le lot 0 rend le système démarrable et peuplé ; sans
   données, aucune correction n'est vérifiable.
2. **Le filet avant la voltige.** Les tests (lot 2) précèdent les corrections risquées
   (lots 4 et 5). Corriger sans filet, c'est déplacer les bugs.
3. **La sécurité s'active dans un ordre précis, ou elle enferme.** Le lot 4 le détaille.

**Effort indiqué en jours-homme approximatifs**, pour un développeur qui connaît le code.

---

## LOT 0 — Débloquer · ½ jour · 🔴 prérequis à tout

> **Sans ce lot, aucun autre n'est vérifiable.** Le système ne démarre pas et la base n'a
> aucun compte.

### 0.1 Appliquer V23 et V24

```bash
mvn -o compile          # les migrations doivent être dans target/classes
mvn spring-boot:run     # ou depuis l'IDE
```

**Ce qui doit se produire** : Flyway applique V23 puis V24, et `ddl-auto: validate` s'exécute
**en entier** sur V16→V24 pour la première fois.

**Vérification** :

```sql
select version, description, success from flyway_schema_history where version::int >= 23;
select count(*) from role;        -- 5
select count(*) from permission;  -- 36
select count(*) from role_permission;  -- 36+36+17+6+21 = 116
```

**Si `validate` proteste** : ce sera sur un détail de type (précision d'un `NUMERIC`, longueur
d'un `VARCHAR`), pas sur une colonne absente — celles-là ont toutes été vérifiées. Correction
par une **V25**, jamais en retouchant une migration appliquée.

### 0.2 Créer le premier administrateur

```http
POST /sni/api/v1/admin/provisioning/bootstrap-admin
{ "email": "admin@bilanga.cg", "firstname": "…", "lastname": "…",
  "password": "…", "generatePassword": false }
```

> **Utilisez `admin@bilanga.cg`** — c'est l'adresse de `app.security.auto-admin.email`. Avec
> une autre, l'auto-admin ne retrouvera pas le compte et `/admin/**` répondra 403 tant que
> vous ne vous connecterez pas vraiment.

**Vérification** : un second appel doit renvoyer **409**. C'est le garde-fou qui joue.

### 0.3 Seed de démonstration

À écrire comme **script SQL hors migration** (`scripts/demo-seed.sql`) — pas une migration :
des données de démonstration n'ont rien à faire dans l'historique de schéma d'une production.

Contenu minimal pour que **tout** soit démontrable :

| Objet | Quantité | Pourquoi cette quantité |
|---|:---:|---|
| Utilisateurs | 4 | un par rôle : agronome, technicien, exploitant (+ l'admin du 0.2) |
| Parcelles | 3 | dont **2 géolocalisées** (météo) et **1 en `PLUVIAL`** (`IrrigationAdapter`) |
| Boîtiers | 4 | dont **2 sur la même parcelle** — ⚠️ **sans voisin, `SensorHealthAnalyzer` ne peut rien conclure** |
| Cultures | 3 | plantées à des dates **différentes**, pour que les stades calculés diffèrent |
| Relevés | ~200 | sur 7 jours, dont une série **figée** sur un boîtier (déclenche `DEFAILLANTE`) |
| Interventions | 4 | dont **une liée à une recommandation** (bouclage) |
| Récoltes | 2 | avec `unitPrice`, pour que la marge soit calculable |
| Exploitation | 1 | avec 2 membres de rôles différents (cloisonnement) |

**Vérification** : `GET /overview/farm` renvoie des statuts variés, pas 3 × `SANS_DONNEES`.

---

## LOT 1 — Corrections triviales · ½ jour · 🟢 sans risque

> Sept corrections indépendantes, aucune ne touche à la logique métier. À faire d'un bloc :
> elles réduisent le bruit qui masquera les vrais problèmes au lot 3.

| Réf | Action | Fichier | Détail |
|---|---|---|---|
| **A6** | Ajouter `@EnableAsync` | nouveau `config/AsyncConfig.java` | `AuditServiceImpl.save` est annoté `@Async` mais s'exécute en synchrone. ⚠️ **Vérifier que `@Transactional(REQUIRES_NEW)` tient toujours** une fois vraiment asynchrone — le contexte de sécurité ne suit pas le fil par défaut |
| **A13** | Retirer les `System.out.println` | `generator/GeneratorOfId.java` | Un log par identifiant généré. Remplacer par un `log.trace` ou rien |
| **A9** | Aligner `.env` sur `application.yaml` | `.env` | Port 5434 → 55820 ; un seul mot de passe (`bilanga25`) |
| **A16** | Ouvrir Swagger en développement | `security/config/SecurityConfig.java` | Les chemins sont déjà en `permitAll` (lignes 95-96) — **vérifier pourquoi le 401 persiste**. Piste : `springdoc` non exposé, ou filtre en amont |
| **A18** | Signaler la troncature | `overview/dto/response/PlotTimeline.java` + `TimelineComposer` | Ajouter `truncated: boolean` et `perSourceLimit: int`. Une chronologie tronquée se lit aujourd'hui comme complète |
| **A10** | Restreindre le CORS | `SecurityConfig` | `allowedOriginPatterns("*")` → liste par profil. Acceptable en dev, à faire **avant** toute exposition |
| **A17** | Index composite | **migration V25** | `CREATE INDEX idx_readings_device_date ON sensor_readings (device_id, recorded_at DESC);` — `idx_readings_device` seul ne couvre pas le tri |

**Vérification du lot** : `mvn compile`, démarrage, et `GET /plots/{id}/timeline` renvoie
`truncated`.

> **A6 est le seul à surveiller.** Activer l'asynchrone change le comportement d'une classe
> qui écrit en base : si l'audit passe réellement sur un autre fil, `SecurityAuditContextProvider`
> ne trouvera plus l'utilisateur courant. Testez qu'un `AuditLog` porte bien son acteur après
> le changement — sinon, il faut propager le contexte
> (`DelegatingSecurityContextAsyncTaskExecutor`).

---

## LOT 2 — Le filet · 1 à 2 jours · 🔴 conditionne les lots 4 et 5

> **Le meilleur rapport valeur/effort du projet.** Ces classes sont **sans état et sans
> transaction** : instanciables directement, **aucune base requise, aucun contexte Spring**.

### 2.1 Neuf classes, par ordre de rentabilité

| # | Classe | Cas à couvrir en priorité |
|:--:|---|---|
| 1 | **`AdminApiAuthorizationManager`** | **chaque route → sa permission** · `/ingest` sans jeton · route non cartographiée → refus · `SUPER_ADMIN` passe partout · `/plots/{id}/economics` → `HARVEST:READ` et non `FARM:READ` |
| 2 | `GrowthStageResolver` | bornes de stade · cycle dépassé · plantation future · culture inconnue → `null` · `stageTimeline` |
| 3 | `SensorHealthAnalyzer` | valeur figée · dérive · décrochage · **absence de voisin → `SAINE`** · série trop courte |
| 4 | `PlausibilityChecker` | chaque borne · **mesure absente ≠ mesure fausse** · bornes du sol plus étroites que l'air |
| 5 | `IrrigationAdapter` | pluvial reformulé · **`null` non traité comme pluvial** · traçabilité préservée · catégorie hors périmètre intacte |
| 6 | `ComparativeExplainer` | les **4 énoncés**, dont « les mesures penchent pour l'alternative » |
| 7 | `EffectAnalyzer` | sens de l'amélioration par type · seuil de bruit 5 % · type non mesurable → `INDETERMINE` |
| 8 | `MarginCalculator` | récolte sans prix → `missingData` · surface absente → `null` · produit nul → `costRatio` `null` |
| 9 | `CsvSeriesWriter` | point-virgule · virgule décimale · **cellule vide ≠ zéro** · BOM |

> **Le n°1 d'abord.** Il fige la matrice d'autorisation, et c'est là qu'une erreur coûte le
> plus cher — un test y vaut mieux qu'une relecture.

### 2.2 Infrastructure de test

Aucune n'est nécessaire pour ces neuf classes : JUnit 5 seul, déjà présent via
`spring-boot-starter-test`.

**Pour aller plus loin** (services, repositories), deux options :

| Option | Coût | Verdict |
|---|---|---|
| **Testcontainers PostgreSQL** | 1 dépendance, démarrage ~5 s par classe | ✅ **recommandé** — le schéma est possédé par Flyway, tester sur H2 testerait un autre schéma |
| H2 en mémoire | rapide | ❌ Les migrations utilisent `date_trunc`, `filter (where …)`, index partiels, `pg_trgm` — H2 ne les supporte pas |

**Vérification du lot** : `mvn test` passe, et la couverture des neuf classes est significative.

---

## LOT 3 — Parcours fonctionnel · 1 jour · 🟠 révèle les vrais bugs

> Les neuf rangs sont écrits, compilés, leur schéma est vérifié. **Aucun n'a jamais été
> exercé.** C'est ici que se trouvent les bugs de câblage.

Le détail est dans `IMPLEMENTATION_V16_V22.md` §7.2. Les **cinq scénarios qui comptent le
plus**, parce qu'ils croisent plusieurs lots :

| # | Scénario | Ce qu'il valide |
|:--:|---|---|
| 1 | 6 relevés identiques → `DEFAILLANTE` + alerte `TECHNIQUE` + `skipReason` → puis un relevé normal → **l'alerte se referme** | Le cycle complet du rang 2, y compris la réconciliation |
| 2 | Parcelle `PLUVIAL` en stress hydrique → **aucun « irriguez » nu** | `IrrigationAdapter` dans le pipeline réel |
| 3 | Parcelle géolocalisée + météo activée → conseil `METEO` **inséré sans erreur** | Que `chk_recommendations_type` a bien été étendue en V20 |
| 4 | Intervention avec `recommendationId` → conseil `APPLIQUEE` → `/effect` → verdict chiffré | Le bouclage conseil → action → effet |
| 5 | `TECHNICIEN` → 403 sur `/economics`, **mais propriétaire non membre garde son accès** | Que l'organisation n'enferme personne |

> **Le scénario 5 est le plus important.** C'est la garantie de non-blocage du rang 9, et la
> seule qui ne se vérifie pas en lisant le code.

**Livrable** : une collection Postman ou un fichier `.http` versionné, réutilisable à chaque
modification.

---

## LOT 4 — Sécurité · ½ jour · 🔴 ordre impératif

> **A2 + A3 + A4 forment un ensemble.** Corriger A2 seul, sans compte administrateur
> fonctionnel, bloque tout le monde — vous compris.

### 4.1 L'ordre, et pourquoi

| Étape | Changement | Impact | Réversible ? |
|:---:|---|---|:---:|
| **1** | `app.security.ownership.enabled: true` | `AccessGuard` cloisonne. Un exploitant ne voit que ses parcelles | ✅ oui, 1 ligne |
| **2** | `app.security.auto-admin.enabled: false` | Plus d'admin implicite. Il **faut** se connecter | ✅ oui |
| **3** | Retirer `ApiPath.V1 + "/**"` du `permitAll` (ligne 85 de `SecurityConfig`) | Les routes métier exigent une permission | ✅ oui |
| **4** | Secret JWT hors du code (A4) | Un jeton ne peut plus être forgé depuis le dépôt | ⚠️ invalide les jetons émis |

**Impact croissant, réversibilité décroissante.** À chaque étape, rejouer le lot 3.

### 4.2 Prérequis absolus avant l'étape 3

- [ ] Un compte `ADMIN` existe et **la connexion fonctionne** (`POST /auth/login` renvoie un jeton)
- [ ] Le frontend envoie le `Bearer` sur **toutes** les requêtes
- [ ] Le lot 3 passe intégralement
- [ ] Un compte de chaque rôle existe, pour vérifier les refus

### 4.3 A4 — le secret JWT

`JWTService` porte un secret par défaut codé en dur. Trois actions :

1. Retirer la valeur par défaut : `app.security.jwt.secret` doit être **obligatoire**.
2. `ConfigurationGuard` refuse déjà le démarrage en `prod` sur certaines clés vides —
   **y ajouter le secret JWT**, et l'étendre au profil `dev` avec un avertissement.
3. Générer un secret par environnement (≥ 256 bits pour HMAC-SHA256).

**Vérification** : démarrer sans `APP_JWT_SECRET` doit échouer en `prod`, avertir en `dev`.

---

## LOT 5 — Dette · 1 à 2 jours · 🟡 après le filet

### 5.1 A8 — retirer le scaffolding fintech

**19 artefacts** dans `pom.xml` : AMQP, Batch (+ jdbc), Quartz, JobRunr, WebSocket, HATEOAS,
Freemarker, Session Redis, Session JDBC — chacun avec sa variante `-test`.

**Méthode, en une passe par famille** :

```
1. commenter la dépendance
2. mvn -o clean compile
3. si ça compile → supprimer définitivement
   sinon → noter ce qui l'utilise réellement, et le documenter
```

> ⚠️ **Freemarker et WebSocket demandent une vérification manuelle.** Le projet sert des pages
> Thymeleaf (formulaire de réinitialisation) et `SecurityConfig` porte une règle `/ws/**`.
> Vérifier qu'aucun n'en dépend avant de retirer.

**Gain attendu** : démarrage plus rapide, surface d'attaque réduite, et surtout un `pom.xml`
qui décrit ce que le projet fait vraiment.

**Nettoyer aussi** : les clés `.env` orphelines (MinIO, outbox/document/contract workers,
rate-limits mobile-money, pawaPay).

### 5.2 A7 — remplir l'audit

`AuditContext` (ThreadLocals `putMeta`/`setDiff`) est **câblé mais jamais appelé** :
`metadata_json` et `diff_json` restent vides. L'audit dit *qui* et *quoi*, jamais *quel
changement*.

**Action minimale et utile** : sur les trois opérations les plus sensibles —
`AdminUserController.update`, `RoleAssignmentAdminController.replaceRolePermissions`,
`CooperativeController.update` — appeler `AuditDiffUtil` (qui existe déjà) et
`AuditContext.setDiff(...)`.

> Ne le faites pas partout d'un coup. Trois exemples bien faits valent mieux qu'une
> instrumentation systématique dont personne ne relit le résultat.

### 5.3 Les quatre restants

| Réf | Action |
|---|---|
| **A11** | `estimatedCost` : ajouter la colonne sur `knowledge_rules` et `disease_knowledge` (**V26**), la saisir dans les DTO knowledge, la reporter dans `RecommendationItem` → `Recommendation`. **Sans cela le champ reste `null` et la promesse faite au frontend n'est pas tenue** |
| **A14** | Purge de `weather_forecast` : aujourd'hui déclenchée au rafraîchissement seulement. Ajouter une purge globale dans `NotificationService.dispatchPending` (déjà appelé après ingestion) ou accepter la dette et la documenter |
| **A15** | Envoi d'e-mails : décommenter et brancher un `EmailNotificationChannel` sur l'interface existante. **Les codes OTT ne doivent plus revenir dans la réponse API** |
| **A16 bis** | Si Swagger reste inaccessible après le lot 1, investiguer `springdoc` (dépendance présente ? `OpenApiConfig` actif ?) |

---

## LOT 6 — Construire · effort variable · 🟢 valeur métier

Par rapport valeur/effort décroissant. **Chacun est indépendant** : prenez-les dans l'ordre
qui sert le mémoire.

### 6.1 Risque de voisinage · 1 jour · ●●● valeur

**Le prochain candidat naturel.** Coordonnées et index géographique existent depuis V16, tous
les diagnostics sont en base. Il ne manque que la requête.

```
knowledge/service/support/NeighbourhoodEngine     ← 8ᵉ moteur, patron des sept autres
  · parcelles dans un rayon (défaut 2 km), via idx_plots_coordinates
  · diagnostics non-NORMAL récents chez les voisins
  · pondération par distance ET fraîcheur du diagnostic
  · RecommendationItem type=RISQUE, catégorie RISQUE_MALADIE
```

**Trois décisions à prendre en l'écrivant** :

1. **Distance à vol d'oiseau ou PostGIS ?** La formule de Haversine en SQL suffit pour 2 km ;
   ajouter PostGIS pour cela seul serait disproportionné.
2. **Fenêtre de fraîcheur.** Un mildiou détecté il y a trois semaines n'annonce plus rien.
   14 jours est un point de départ défendable.
3. **Ne pas doubler `RiskEngine`.** Si les conditions locales réunissent déjà le risque, le
   voisinage ne fait que le renforcer — ne créez pas deux conseils pour la même maladie.

> ⚠️ **Nouveau type de recommandation ⇒ étendre `chk_recommendations_type`** (migration).
> C'est le piège qui a failli coûter cher au rang 6.

**Pour le mémoire** : le système passe de « diagnostiquer une parcelle » à « raisonner sur un
territoire ». Contribution originale, effort faible.

### 6.2 Signalement des règles à réviser · ½ jour · ●●● valeur

`GET /recommendations/uptake` fournit **déjà** la donnée. Il manque :

- un **seuil** configurable (`bilanga.knowledge.review-threshold`, ex. taux d'application < 20 % sur ≥ 10 conseils) ;
- une route `GET /knowledge/rules/to-review` qui croise `uptake` et `knowledge_rules` ;
- le regroupement des `feedbackNote` par règle — **c'est là que se trouve le pourquoi**.

> **Le système apprend de son usage sans réentraîner de modèle.** C'est un angle original et
> peu coûteux, qui distingue nettement le travail d'une intégration de classifieur.

### 6.3 Rejeu de diagnostic · 1 jour · ●●● valeur, non catalogué

Reprendre un relevé passé et relancer le raisonnement avec les seuils **actuels**, pour
comparer avec ce qui avait été conclu.

```
POST /diagnosis/{id}/replay   → { original: DiagnosisResult, replayed: DiagnosisResult, differences: [] }
```

Tout est déjà en base : le relevé, le diagnostic, les recommandations tracées. Cela transforme
la base de connaissance en objet **expérimentable** — « qu'aurait dit le système si ce seuil
avait été à 32 % ? ».

C'est aussi un pas vers §9.5 (versionnement) sans en payer le coût.

### 6.4 Le reste, par ordre

| Fonctionnalité | Valeur | Effort | Note |
|---|:---:|:---:|---|
| Canal e-mail | ●● | ● | `NotificationChannel` l'accueille sans rien changer |
| Calendrier prévisionnel | ●● | ● | `stageTimeline` inclut déjà les stades à venir — pure restitution |
| Comparaison inter-parcelles sur les mesures | ●● | ●● | « votre parcelle Nord est 12 % plus humide, à stade égal » |
| Seuils adaptatifs par parcelle (§9.3) | ●●● | ●● | distinguer « ce sol est à 30 % » de « ce sol s'assèche » |
| Export PDF (§8.4) | ●● | ●● | annexes du mémoire |
| Jeu de données annoté exportable | ●●● | ●● | réentraîner **sans** coupler le backend au cycle des modèles |
| Canal WhatsApp | ●● | ●● | image possible, très répandu en périurbain |
| Versionnement de la connaissance (§9.5) | ●●● | ●●● | rendrait tout diagnostic rejouable — chantier réel |
| PWA hors ligne | ●●● | ●●● | l'idempotence côté serveur existe déjà |

---

## LOT 7 — Le mémoire · 🔴 risque de soutenance

### A12 — les seuils agronomiques

**Ce n'est pas de la dette technique, c'est un risque de soutenance.** Les valeurs semées par
V3, V6, V7 et V10 sont **indicatives** — le commentaire de V10 le dit — et n'ont jamais été
validées. Or elles sont le cœur du raisonnement : tout `AgronomicEngine` et tout `RiskEngine`
en dépendent.

**Un jury demandera d'où elles viennent.** Trois réponses possibles, par ordre de solidité :

| Réponse | Effort | Solidité |
|---|:---:|:---:|
| Faire valider les seuils tomate et manioc par un agronome, et citer la source | 1–2 j (dépend d'un tiers) | ●●● |
| Sourcer depuis la littérature (FAO, IRAD, INERA) et citer les références dans le mémoire | 1 j | ●● |
| Assumer explicitement : « valeurs indicatives, méthode de validation proposée en perspective » | 2 h | ● |

> **La troisième reste acceptable si elle est assumée dans le mémoire.** Découverte à l'oral,
> elle coûte cher. Écrite noir sur blanc avec la méthode de validation qu'on propose, elle
> devient une limite maîtrisée — ce qui est tout autre chose.

**Action minimale, à faire aujourd'hui** : ajouter une section « Limites » au mémoire qui
énonce A12, et un commentaire en tête de V3 renvoyant vers elle.

---

## 2. Enchaînement

```
LOT 0  Débloquer ─────────────────────────────► prérequis absolu
   │
   ├──► LOT 1  Corrections triviales  (indépendant, à faire tôt)
   │
   ├──► LOT 2  Filet de tests ──────┐
   │                                 │
   └──► LOT 3  Parcours fonctionnel ─┤
                                     ▼
                            LOT 4  Sécurité   (ordre impératif interne)
                                     │
                                     ▼
                            LOT 5  Dette
                                     │
                                     ▼
                            LOT 6  Construire   (indépendants entre eux)

LOT 7  Mémoire  ─── à mener EN PARALLÈLE, il ne dépend de rien
```

**Chemin critique** : 0 → 2 → 3 → 4. Les lots 1, 6 et 7 s'insèrent où il y a de la place.

---

## 3. Si le temps est compté

**Trois jours disponibles** — l'essentiel, dans cet ordre :

| Jour | Lot | Pourquoi celui-là |
|:---:|---|---|
| 1 | **LOT 0** entier + **LOT 1** | Sans le 0, rien n'est démontrable. Le 1 est rapide et enlève le bruit |
| 2 | **LOT 3** (parcours) | C'est ce qui révèle si les neuf rangs marchent vraiment |
| 3 | **LOT 7** + **LOT 6.1** (voisinage) | L'un protège la soutenance, l'autre lui donne un argument neuf |

**Ce qu'on sacrifie alors** : les tests (lot 2) et le durcissement (lot 4). C'est un choix
défendable **si** on l'assume : une démonstration qui marche vaut mieux qu'une sécurité active
et un produit qu'on n'a jamais fait tourner.

**Ce qu'on ne sacrifie jamais** : le lot 0. Un système qui ne démarre pas n'a rien à montrer.

---

## 4. Suivi

### Audit

> **Séance d'exécution du 2026-07-30 — lots 1 à 6.** État : `mvn test` passe,
> **324 tests**, en 7 secondes et **sans base de données**. Cinq migrations neuves
> (V25, V26, V27) attendent un démarrage.

| Réf | Objet | Lot | Fait |
|---|---|:---:|:---:|
| A1 | Un seul test | 2 | ✅ **324 tests**, 10 classes, aucune base requise |
| A2 | `permitAll("/**")` | 4 | 🟡 **écrit, non activé** — `app.security.open-business-routes.enabled` |
| A3 | Auto-admin actif | 4 | 🟡 inchangé en `dev`, forcé à `false` en `prod` |
| A4 | Secret JWT en dur | 4 | ✅ *déjà corrigé avant la séance* — vérifié : plus aucun repli dans `JWTService` |
| A5 | Parcours jamais joué | 3 | 🟡 **`docs/parcours-fonctionnel.http`** livré — à jouer par vous |
| A6 | `@Async` sans `@EnableAsync` | 1 | ✅ `config/AsyncConfig` + propagation du contexte de sécurité |
| A7 | `AuditContext` jamais appelé | 5 | ✅ 3 opérations instrumentées + **2 défauts corrigés** (voir ci-dessous) |
| A8 | 19 artefacts fintech | 5 | ✅ les 19 retirés, plus le code mort associé |
| A9 | `.env` incohérent | 1 | ✅ **l'audit était faux** : port et mot de passe déjà alignés. Clés orphelines retirées |
| A10 | CORS `*` | 1 | ✅ `app.security.cors.allowed-origin-patterns`, énuméré en `prod` |
| A11 | `estimatedCost` toujours nul | 5 | ✅ **V26** + chaîne complète règle → conseil. Aucun prix semé (voir §5.3) |
| A12 | Seuils non validés | **7** | ☐ **non traité** — hors du périmètre demandé |
| A13 | `System.out.println` | 1 | ✅ passé en `log.trace`, derrière un test d'activation |
| A14 | Purge météo partielle | 5 | ✅ purge globale auto-limitée à une par heure, sans ordonnanceur |
| A15 | E-mails commentés | 5 | 🟡 **canal SMTP livré** ; le rebranchement des codes OTT reste à faire |
| A16 | Swagger 401 | 1 | 🟡 `/webjars/**` et `/v3/api-docs` ajoutés, springdoc déclaré — **à vérifier au démarrage** |
| A17 | Index composite manquant | 1 | ✅ **V25** `idx_readings_device_date` |
| A18 | Troncature silencieuse | 1 | ✅ `truncated`, `perSourceLimit`, `truncatedTypes` |

### Deux corrections à l'audit, constatées en exécutant

| Réf | Ce que l'audit annonçait | La réalité |
|---|---|---|
| **A4** | « Secret JWT codé en dur dans `JWTService` » | **Déjà corrigé.** `JWTService` lit `AppProperties` sans repli, et `ConfigurationGuard` refuse le démarrage en `prod`. Rien à faire |
| **A9** | « Port 5434 ≠ 55820, deux mots de passe » | **Déjà corrigé.** `.env` pointe sur 55820 avec `bilanga25`, comme `application.yaml` et `compose.yaml`. Seules les clés orphelines (MinIO, workers) restaient |

### Quatre défauts trouvés par les tests, invisibles à la relecture

Ce sont eux qui justifient le lot 2 à eux seuls.

| Où | Le défaut | Ce qu'il coûtait |
|---|---|---|
| `GrowthStageResolver` | `Map.of(...).get(null)` **lève** une `NullPointerException` — les cartes immuables du JDK ne tolèrent pas la clé nulle, là où `HashMap` rend `null` | Une culture hors {tomate, manioc} — `cropName` est un `VARCHAR` libre — faisait échouer `refreshGrowthStage`, appelé à **chaque diagnostic**. L'exception remontait au cœur du pipeline et faisait perdre le diagnostic entier |
| `GrowthStageResolver` | `stageTimeline` datait chaque changement de stade **un jour avant** celui que `stageFor` employait réellement | Deux vues du même fait se contredisaient d'un jour, sans que rien ne le dise. Trouvé en confrontant les deux méthodes sur tout le cycle |
| `AdminApiAuthorizationManager` | `/admin/roles/{id}/permissions` exigeait `SYSTEM:ROLES` alors que le contrôleur et la documentation exigent `SYSTEM:PERMISSIONS` (idem `/admin/users/{id}/roles`) | La couche URL était **plus stricte** que le contrat : un rôle « gestionnaire de droits » se verrait refuser son écran avant même que `@PreAuthorize`, qui l'accepte, ne soit consulté. Invisible tant que `permitAll` court-circuite la classe |
| `AuditDiffUtil` | `Map.of("before", b, "after", a)` **lève** sur une valeur nulle | Le brancher tel quel aurait fait échouer l'audit dès le premier champ passant de `null` à une valeur — c'est-à-dire tout de suite. Corrigé *avant* de l'appeler (A7) |
| `AspectAudit` | les métadonnées étaient figées **avant** `pjp.proceed()` | Tout `AuditContext.putMeta` appelé par la méthode auditée — le seul endroit qui connaisse l'objet concerné — était **perdu**. Les hooks étaient câblés, appelables, et sans effet |

### Construction

| Fonctionnalité | Lot | Fait |
|---|:---:|:---:|
| Risque de voisinage | 6.1 | ✅ `NeighbourhoodEngine` (8ᵉ moteur) + **V27** + 24 tests |
| Règles à réviser | 6.2 | ☐ non demandé cette séance |
| Rejeu de diagnostic | 6.3 | ✅ `GET /diagnosis/{id}/replay` — n'écrit rien |
| Canal e-mail | 6.4 | ✅ `EmailNotificationChannel` (SMTP, sans dépendance nouvelle) |
| **Notifications en lingala et kituba** | 6.4 | ✅ `NotificationLanguage` + `NotificationMessages` — **ajout demandé en séance** |
| Calendrier prévisionnel | 6.4 | ✅ `GET /crops/{id}/calendar` — pure restitution de `stageTimeline` |
| Seuils adaptatifs | 6.4 | ☐ non demandé cette séance |
| Export PDF | 6.4 | ☐ non demandé cette séance |

### Ce qui a été livré en plus du plan

| Objet | Pourquoi |
|---|---|
| **Notifications en lingala et kituba** | Demandé en séance. Le lingala et le kituba sont les deux véhiculaires du Congo, et les notifications sont les **seuls** messages que l'application adresse à quelqu'un qui n'a pas choisi de la consulter, sur un téléphone simple, au champ. C'est là — et seulement là — que la langue décide si le message est lu. La colonne `notification_preference.language` existait depuis la V18 et **n'était lue par personne** : l'utilisateur pouvait la régler et constater que rien ne changeait |
| Corps des réponses 401/403 | Les deux gestionnaires construisaient un corps et ne l'écrivaient **jamais** : le client recevait un refus vide, là où toutes les autres erreurs portent `errorCode` et `message` |
| `mvn test` sans base | `contextLoads()` est marqué `@Tag("integration")` et écarté par défaut. La commande échouait sur tout poste sans PostgreSQL, ce qui dissuadait de la lancer — et donc d'écrire des tests |

> ### ⚠️ Le partage retenu pour la traduction, et pourquoi
>
> **L'enveloppe est traduite ; le constat agronomique reste en français.** C'est une
> décision, pas une paresse.
>
> Le texte des moteurs est une prose composée à la volée — « l'humidité du sol vaut
> 24,00, soit en deçà du seuil de 35,00 ». La traduire exigerait de traduire chaque
> règle de la base de connaissance, chaque libellé de mesure et chaque gabarit de
> phrase, **à trois exemplaires**, en les maintenant alignés à chaque évolution du
> moteur. Une traduction qui dérive est **pire** qu'une absence de traduction : elle
> donne un conseil *faux* dans la langue que la personne comprend le mieux, donc
> celui qu'elle suivra. C'est la même règle qu'ailleurs dans ce projet — mieux vaut
> ne rien conseiller que conseiller faux.
>
> Ce qui est traduit est donc ce qui **décide de l'action** : l'urgence, la parcelle,
> l'appel à agir — et c'est aussi ce qu'on lit en premier sur l'écran d'un téléphone
> simple. Un pied de message annonce explicitement que le détail est en français et
> invite à s'appuyer sur son conseiller ; `languageScopeNote` le dit sous le
> sélecteur.
>
> **Les formulations lingala et kituba sont à faire relire par un locuteur natif**
> avant toute mise en service, au même titre que les seuils agronomiques (A12).
> Elles sont écrites pour être corrigées, pas pour être crues.

---

## 5. Trois règles à ne pas oublier en exécutant ce plan

1. **Une migration cesse d'être modifiable au premier démarrage**, pas au commit. Toute
   correction passe par une migration suivante. Cette règle a déjà coûté un incident (V22).
2. **Un nouveau type de recommandation exige d'étendre `chk_recommendations_type`.** L'oubli
   fait échouer l'insertion **au cœur du diagnostic**, et fait perdre le diagnostic entier.
3. **Les cinq invariants** (`IMPLEMENTATION_V16_V22.md` §3) priment sur tout ajout. Un lot qui
   rend l'organisation obligatoire, qui fait échouer un diagnostic faute de météo, ou qui
   retire un `limitation` de l'affichage, annule plus qu'il n'apporte.
