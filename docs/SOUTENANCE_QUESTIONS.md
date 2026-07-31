# Questions du jury, et réponses

> **Établi le 2026-08-01**, contre le code et la production peuplée.
> Compagnon de `SOUTENANCE_DEMONSTRATION.md` (ce qu'on montre) et de
> `SOUTENANCE_FICHE.md` (le catalogue des fonctions).
>
> **Comment s'en servir.** Chaque réponse tient en trois à six phrases, dites à voix
> haute. Les encadrés « si on insiste » portent l'argument de second niveau, celui qu'on
> ne sort que si la première réponse ne suffit pas. Les réponses marquées ⚠️ concernent
> des limites : **elles se disent d'elles-mêmes, pas sous la contrainte.**

---

## Sommaire

| § | Famille de questions |
|---|---|
| [1](#1-le-projet-et-sa-these) | Le projet et sa thèse |
| [2](#2-le-fonctionnel-au-quotidien) | Le fonctionnel au quotidien |
| [3](#3-lintelligence-artificielle) | L'intelligence artificielle |
| [4](#4-le-moteur-agronomique) | Le moteur agronomique |
| [5](#5-les-cas-alternatifs-et-degrades) | **Les cas alternatifs et dégradés** |
| [6](#6-larchitecture-et-les-choix-techniques) | L'architecture et les choix techniques |
| [7](#7-les-donnees-et-le-schema) | Les données et le schéma |
| [8](#8-la-securite) | La sécurité |
| [9](#9-la-qualite-et-les-tests) | La qualité et les tests |
| [10](#10-lexploitation-reelle) | L'exploitation réelle |
| [11](#11-les-limites-a-assumer) | ⚠️ Les limites à assumer |
| [12](#12-les-questions-pieges) | Les questions pièges |

---

## 1. Le projet et sa thèse

### Q. En une phrase, qu'est-ce que Bilanga ?

Un orchestrateur qui transforme des mesures de capteurs en conseils agronomiques
justifiés, et qui vérifie ensuite ce que ces conseils ont produit. Les cultures couvertes
sont la tomate et le manioc.

### Q. Qu'est-ce qui distingue ce travail d'une simple intégration de modèle ?

Cinq propriétés, dont la première est la principale.

**La boucle est fermée.** Mesure, diagnostic, conseil, action déclarée, effet mesuré,
rendement. Le système évalue ses propres conseils avec ses propres données. Un
classifieur branché sur une base s'arrête à la deuxième étape.

**Deux voies indépendantes se confrontent.** Un réseau de neurones entraîné sur des images
et un moteur déterministe appliqué à des mesures de sol. Ils n'ont aucune information en
commun. Quand ils concordent, la conclusion tient sur deux pieds ; quand ils divergent, le
système le dit.

**La fiabilité du capteur est distinguée de la confiance du modèle.** C'est le seul angle
mort capable de produire un conseil nuisible.

**Le système reconnaît ses limites.** `limitation`, `missingData`, `dataQualityNote`,
`reliable: false`. Un chiffre est toujours accompagné de ce qu'il ne prouve pas.

**Il apprend de son usage sans réentraîner de modèle.** Le retour sur conseil et le taux
d'application ouvrent la révision des règles, à coût nul en calcul.

### Q. Pourquoi la tomate et le manioc ?

Le manioc est la base alimentaire au Congo, la tomate est la culture maraîchère de rente
la plus répandue autour de Brazzaville. Elles ont des cycles très différents, 120 jours
contre 330, ce qui a obligé à modéliser le stade de croissance en **fraction du cycle**
plutôt qu'en jours fixes. Cette contrainte a rendu le modèle généralisable : ajouter une
culture demande une ligne de séquence de stades et des seuils, pas de code.

### Q. À qui s'adresse le système ?

Trois publics, et le cloisonnement le reflète. L'**exploitant** voit ses parcelles et ses
marges. Le **conseiller agronome** pilote la base de connaissance et suit les diagnostics,
sans accéder à la comptabilité. Le **technicien** voit les boîtiers et les alertes
techniques, ni l'agronomie ni l'économie.

> **Si on insiste.** Un technicien venu changer une sonde n'a aucune raison de voir les
> marges. Dans un milieu où tout le monde se connaît, ouvrir la comptabilité à quiconque
> intervient sur un boîtier est un problème social avant d'être technique.

---

## 2. Le fonctionnel au quotidien

### Q. Décrivez le parcours d'une mesure, du champ au conseil.

Le boîtier envoie un relevé sur `POST /ingest/readings` avec une clé partagée. Le serveur
résout la parcelle depuis l'identifiant matériel, contrôle la plausibilité des valeurs,
met à jour la fiche du boîtier, **écrit le relevé**, juge la santé de la sonde, puis lance
le diagnostic. Le diagnostic résout le contexte, recalcule le stade de croissance,
interroge le modèle, assemble les conseils depuis huit moteurs, les déduplique, arbitre
les contradictions, les adapte à la parcelle, les trie, les enregistre, et lève une alerte
si nécessaire.

**L'ordre compte** : l'écriture du relevé précède toute opération faillible.

### Q. Le système décide-t-il à la place de l'agriculteur ?

Non, et c'est une décision de conception. Il **propose** des conseils ordonnés, chacun
accompagné de sa justification chiffrée. L'exploitant déclare ce qu'il a fait, et peut
marquer un conseil comme ignoré avec un motif. Ce motif est ce qui permettra de réviser la
règle.

### Q. Que se passe-t-il si l'exploitant ignore systématiquement un type de conseil ?

`GET /recommendations/uptake` rend le taux d'application par type de moteur. Un type
systématiquement ignoré signale une règle à réviser, pas un exploitant négligent. C'est
l'écran d'administration de la connaissance.

### Q. Comment le stade de croissance est-il connu ?

Il est **recalculé**, pas saisi. C'est un défaut corrigé en cours de projet : la colonne
était renseignée à la main, personne ne revenait la modifier, et une tomate plantée en
mars restait « levée » jusqu'à la récolte. Or les seuils agronomiques dépendent du stade.
Le système raisonnait donc sur un stade faux, avec exactement la même assurance que sur un
stade juste.

Le recalcul a lieu dans le résolveur de contexte, **là où le stade est consommé**, et non
par un ordonnanceur : un stade recalculé que personne ne lit n'a aucune valeur.

### Q. Peut-on ajuster les seuils sans redéployer ?

Oui. Toute la base de connaissance est pilotable par API : seuils par culture, seuils par
stade, maladies, conditions de risque, règles de décision, corrélations, arbitrages. Une
écriture par l'API évince le cache immédiatement. Une modification faite directement en
base met jusqu'à trente minutes à se refléter, ce qui est la raison d'être du TTL.

### Q. Comment sait-on ce qu'un changement de seuil aurait donné ?

`GET /diagnosis/{id}/replay` rejoue un diagnostic passé avec la connaissance actuelle et
rend les écarts, en langage naturel : « le seuil appliqué à l'humidité du sol est passé de
35,00 à 32,00 ». Rien n'est écrit : ni diagnostic, ni conseil, ni alerte. Cela transforme
la base de connaissance en objet expérimentable.

> **Si on insiste.** Sans cette route, ajuster un seuil demandait de modifier puis
> d'attendre le prochain relevé, qui surviendrait dans des conditions différentes de
> celles qui avaient soulevé la question. On ne saurait donc jamais si l'ajustement était
> bon.

### Q. Que voit l'exploitant en ouvrant l'application ?

`GET /overview/farm` en une requête : combien de parcelles, dans quel état, lesquelles
demandent attention, combien d'alertes ouvertes et de quel niveau, combien de boîtiers en
batterie faible. Puis `GET /overview/plots` pour la liste. Aucune boucle côté client.

---

## 3. L'intelligence artificielle

### Q. Quels modèles utilisez-vous ?

Trois. Un **EfficientNetB0** par culture pour la vision, entraîné sur des images de
feuilles, et un **RandomForest** sur les mesures de sol. Ils sont hébergés dans un
microservice Python distinct, appelé en REST. Les modèles sont convertis en TFLite pour
tenir dans les contraintes de l'hébergement.

### Q. Pourquoi un microservice séparé et non des modèles embarqués dans le backend ?

Trois raisons, dans cet ordre.

**Le cycle de vie diffère.** Un modèle se réentraîne, se remplace, se versionne à un
rythme qui n'est pas celui du backend. Les coupler obligerait à redéployer l'un pour
l'autre.

**L'écosystème diffère.** TensorFlow et scikit-learn vivent en Python. Les porter en Java
coûterait plus que l'appel HTTP.

**La panne se contient.** Le backend traite l'indisponibilité du service comme un cas
prévu : le relevé est conservé, `skipReason` vaut `ML_INDISPONIBLE`. Un modèle embarqué
qui échoue emporte le processus.

### Q. Le backend peut-il piloter le réentraînement ?

Non, délibérément. Le microservice est un système tiers. Exporter un jeu de données annoté
pour le réentraîner ailleurs, oui ; piloter l'entraînement depuis le backend
contredirait la séparation qui fait la solidité de l'architecture.

### Q. Que vaut la précision de vos modèles ?

Les valeurs enregistrées dans le registre des modèles sont de 0,98 pour la tomate, 0,64
pour le manioc et 0,82 pour le modèle tabulaire.

> ⚠️ **À dire soi-même :** le 0,64 du manioc est faible, et c'est pourquoi la
> **corroboration** existe. Le système ne se fie pas à la seule probabilité du
> classifieur : il croise la maladie prédite avec le score de risque calculé sur les
> mesures. Au-dessus de 0,60, les conditions corroborent ; en dessous de 0,20, elles ne
> soutiennent pas la progression et le système le dit. C'est précisément quand le modèle
> est médiocre que cette seconde voie compte.

### Q. Comment gérez-vous une prédiction peu fiable ?

En dessous de 0,60 de confiance, `reliable` vaut faux, et **un diagnostic non fiable ne
lève aucune alerte**. Il ne sert pas non plus de motif pour en refermer une. L'exploitant
se déplacerait, ou cesserait de se déplacer, sur la foi d'une conclusion que le système
lui-même ne soutient pas.

### Q. Le modèle peut-il se tromper de maladie entre deux maladies proches ?

Oui, et le système répond à cette question explicitement. `ComparativeExplainer` croise
les probabilités du classifieur avec les conditions mesurées, et produit quatre énoncés
distincts. Le cas qui compte le plus est celui où **les mesures pencheraient pour
l'alternative** : le système le dit, et recommande un examen visuel de confirmation. Le
taire serait malhonnête.

### Q. Combien de temps prend un diagnostic ?

Environ une demi-seconde de bout en bout sur l'hébergement actuel, appel au microservice
compris. Mesuré sur des lots de vingt-cinq relevés lors du peuplement de la production.

---

## 4. Le moteur agronomique

### Q. Pourquoi un moteur de règles à côté d'un modèle statistique ?

Parce qu'ils répondent à des questions différentes. Le modèle dit « à quoi cela
ressemble ». Le moteur dit « ce que les mesures imposent, et pourquoi ». Le second est
**explicable** : chaque conseil porte la mesure, la valeur observée et le seuil franchi.
Un modèle ne peut pas fournir cela, et sans cela on ne peut ni justifier un conseil à un
exploitant, ni corriger une règle quand elle se révèle mauvaise.

### Q. Combien de moteurs, et que fait chacun ?

Huit.

| Moteur | Question à laquelle il répond |
|---|---|
| **Risque** | quelle fraction des conditions d'apparition d'une maladie est réunie, sur les seules mesures |
| **Agronomique** | de combien chaque mesure s'écarte des exigences de la culture, à ce stade |
| **Tendance** | quand un seuil va être franchi, par régression sur la série récente |
| **Corrélation** | ce que le croisement image/mesures ajoute |
| **Météo** | ce que le ciel annoncé change à l'action recommandée |
| **Voisinage** | ce qui se passe chez les voisins et qui n'est pas encore visible chez soi |
| **Arbitrage** | comment concilier deux conseils qui paraissent se contredire |
| **Adaptateur d'irrigation** | comment reformuler un conseil inapplicable sur cette parcelle |

### Q. Comment évitez-vous les conseils contradictoires ?

L'arbitrage **ajoute** une synthèse, il ne retire jamais. « Réduire l'humidité pour
contenir une maladie foliaire » et « irriguer pour lever un stress hydrique » paraissent
se contredire ; l'une vise l'air, l'autre vise le sol. La contradiction n'est
qu'apparente, et le moteur formule ce qu'un agronome dirait : irriguer au pied, tôt le
matin, jamais par aspersion.

> **Si on insiste : pourquoi ne pas supprimer l'un des deux ?** Effacer un conseil ferait
> disparaître le problème avec lui, ce qui est pire que de proposer une action
> difficile. Le constat reste vrai des deux côtés.

### Q. Et si les mesures montrent que la contradiction n'existe pas ?

C'est une correction apportée en cours de projet. L'arbitrage se déclenchait dès que deux
catégories coexistaient, même quand l'une des deux reposait sur un écart insignifiant. Il
exige désormais un **écart relatif minimal des deux côtés**, et la synthèse hérite de la
priorité du plus faible des deux conseils : concilier deux problèmes mineurs ne produit
pas une urgence.

### Q. Un conseil « irriguez » sur une parcelle sans irrigation, que devient-il ?

Il est **reformulé**, pas supprimé : paillage, ombrage, binage. Le constat reste vrai, le
sol manque d'eau ; seule la réponse change. La traçabilité, mesure et seuil, est
préservée intacte, sinon la justification a posteriori ne fonctionnerait plus.

Deux garde-fous : le rattachement se fait sur la catégorie **et** le libellé, car la même
catégorie porte aussi des conseils déjà compatibles avec le pluvial ; et une valeur
d'irrigation **absente n'est pas traitée comme pluviale**, car en l'absence d'information
mieux vaut laisser le conseil d'origine.

### Q. Sur quoi repose l'analyse de tendance ?

Une régression des moindres carrés sur une fenêtre récente, qui projette le franchissement
d'un seuil à douze heures. **Elle contrôle la qualité de l'ajustement**, faute de quoi une
série erratique produirait toujours une pente : la régression n'échoue jamais. Annoncer un
stress hydrique dans quatre heures sur la foi du bruit de mesure fait perdre la confiance
de l'exploitant plus vite que de ne rien annoncer.

### Q. Le conseil de voisinage, comment est-il calculé ?

Les parcelles dans un rayon de deux kilomètres, dont un diagnostic anormal date de moins
de quatorze jours. La pondération est le **produit** de la proximité et de la fraîcheur,
et non leur moyenne : un foyer très ancien doit être négligeable même s'il est mitoyen,
et un foyer lointain négligeable même s'il date d'hier. Une moyenne laisserait chacun des
deux facteurs compenser l'autre.

Il ne double jamais le moteur de risque : si les conditions locales signalent déjà cette
maladie, le voisinage se tait. Deux conseils pour un même problème font douter du système.

---

## 5. Les cas alternatifs et dégradés

> **C'est la famille de questions la mieux préparée du projet**, parce que ces cas ont été
> traités comme des exigences et non comme des exceptions.

### Q. Le service d'analyse tombe. Que se passe-t-il ?

Le relevé est **enregistré**, la réponse porte `diagnosed: false` et
`skipReason: "ML_INDISPONIBLE"`. Rien n'est perdu, rien n'échoue côté boîtier.

> **La règle générale :** perdre un diagnostic parce qu'un service tiers est muet est
> acceptable, il se recalculera. Perdre une mesure ne l'est pas : elle est irremplaçable,
> l'instant est passé.

### Q. La base de données tombe pendant l'ingestion ?

L'appel échoue, et le boîtier réémet. C'est le seul cas où le relevé peut être perdu, et
c'est pourquoi le lot d'ingestion accepte `recordedAt` : un boîtier qui a tamponné ses
mesures pendant une coupure les rejoue **avec leur heure réelle**, et non avec l'instant
de reconnexion.

### Q. Un relevé du lot est corrompu. Les autres sont-ils perdus ?

Non. **Le lot n'est pas transactionnel** : chaque relevé est traité pour son propre
compte, dans sa propre transaction. La réponse porte `accepted`, `rejected` et un tableau
`failures` indexé sur la position dans le tableau envoyé, pour que le boîtier sache
exactement quoi ne pas réémettre.

> **Si on insiste sur le pourquoi.** Un boîtier qui rentre après trois jours hors ligne ne
> doit pas perdre cent quatre-vingt-dix-neuf mesures valides parce que la deux-centième
> est corrompue.

### Q. Une sonde envoie des valeurs absurdes, pH 22 par exemple ?

Le relevé est **accepté et marqué** `anomalyDetected: true`, avec les mesures fautives
nommées. Il n'est jamais rejeté : c'est ainsi qu'on détecte une sonde qui dérive. Le
rejeter ferait disparaître la panne au lieu de la signaler.

> **La nuance à souligner.** Les bornes sont volontairement **très larges** à l'ingestion
> machine, et **strictes** sur la saisie manuelle. Un pH de 22 saisi à la main est une
> faute de frappe, qu'il faut refuser tout de suite ; le même pH remonté par une sonde est
> le symptôme d'une panne, qu'il faut enregistrer.

### Q. Et une sonde qui envoie des valeurs plausibles mais fausses ?

C'est le cas dangereux, et le seul capable de produire un conseil **nuisible**. Trois
règles le traitent :

| Signal | Détection | Verdict |
|---|---|---|
| Valeur figée | même valeur **exacte** sur six relevés consécutifs, dans une fenêtre de douze heures | `DEFAILLANTE` |
| Décrochage | écart massif à la **médiane** des boîtiers voisins | `DEFAILLANTE` |
| Dérive | écart modéré à cette même médiane | `SUSPECTE` |

Sur `DEFAILLANTE`, le **diagnostic est suspendu** et une alerte technique est levée. Sur
`SUSPECTE`, les mesures restent utilisées mais le diagnostic porte une réserve.

### Q. Pourquoi la médiane et non la moyenne des voisins ?

Avec trois boîtiers dont un déjà en panne, la moyenne serait tirée par le fautif et
disculperait celui qu'on examine.

### Q. Et s'il n'y a qu'un seul boîtier sur la parcelle ?

Limite assumée : sans témoin, une dérive lente est rigoureusement indiscernable d'une
évolution réelle du sol. Seule la règle de la valeur figée reste applicable, et c'est déjà
la plus fréquente en pratique.

### Q. Pourquoi une égalité exacte, et pas un intervalle ?

Une mesure physique réelle varie toujours au moins sur sa dernière décimale. Deux relevés
identiques arrivent ; six d'affilée ne sont plus un phénomène naturel.

### Q. La parcelle n'a pas de coordonnées. Que perd-on ?

La météo et le voisinage, rien d'autre. Les deux moteurs rendent une **liste vide**, le
diagnostic sort normalement, et les vues concernées disent pourquoi elles sont vides
plutôt que de faire silence. La parcelle Est de la démonstration est exactement ce cas.

### Q. Aucune culture n'est déclarée sur la parcelle ?

`skipReason: "CONTEXTE_ABSENT"`. Sans culture, il n'y a ni seuils applicables ni stade, et
conseiller sans référentiel serait inventer.

### Q. La passerelle SMS n'est pas configurée ?

Le canal se déclare **indisponible**, rien n'est mis en file, aucun échec n'est compté, et
il n'apparaît pas dans `availableChannels`. Le système est démontrable sans compte
opérateur, et le jour où l'on branche une vraie passerelle il n'y a rien à nettoyer.

### Q. Trois interrupteurs, un même principe ?

Oui : la météo, la santé des sondes et le SMS. **Désactivé, le système fonctionne, il fait
juste moins.** Aucun ne peut casser quoi que ce soit en étant absent. C'est la même
posture que pour le microservice d'inférence.

### Q. Deux utilisateurs modifient la même parcelle en même temps ?

Verrouillage optimiste par version. Le second reçoit un **409** avec le code
`OPTIMISTIC_LOCK`, et le client doit recharger puis rejouer. Perdre silencieusement la
modification du premier serait pire qu'un conflit visible.

### Q. Un client rejoue deux fois la même création ?

Les écritures d'administration acceptent un en-tête `Idempotency-Key`. Rejouer la même clé
avec le même corps rend la réponse d'origine **sans réexécuter**. La même clé avec un
corps différent rend un 409. La machine à états utilise un verrou pessimiste et des
transactions séparées.

### Q. Une alerte reste ouverte alors que la situation est réglée ?

Trois fermetures automatiques existent. La situation observée a changé sur cette voie :
`AUTO_SITUATION_REMPLACEE`. Plus rien d'urgent : `AUTO_SITUATION_NORMALISEE`. Et
manuellement : `RESOLUE_MANUELLEMENT`. **Seule la dernière atteste d'une action humaine**,
et le client doit les afficher différemment.

### Q. Une alerte est constatée à nouveau sans être acquittée ?

Le compteur d'escalade augmente, et au-delà de trois l'alerte **monte d'un niveau**. Une
alerte ignorée qui reste au même rang finit par se confondre avec le bruit de fond.

### Q. Le boîtier émet toutes les trente secondes. Deux mille diagnostics par jour ?

Non. Un régulateur exige soit qu'un délai minimal soit écoulé, soit qu'une mesure ait
bougé de façon significative. Sinon `skipReason: "CONDITIONS_STABLES"`. Le relevé, lui,
est toujours enregistré : c'est la donnée brute, on ne la jette pas.

### Q. Une récolte est saisie sans prix ?

Elle est comptée pour zéro **et signalée dans `missingData`**. L'ignorer silencieusement
donnerait une marge fausse que rien ne distinguerait d'une marge juste.

### Q. Une parcelle sans surface plantée ?

`marginPerHectare` et `yieldPerHectare` valent **`null`**, pas zéro, et l'absence est
expliquée. Ce sont les seuls chiffres comparables entre parcelles.

### Q. Le produit brut est nul, que vaut le ratio de charges ?

`null`. Diviser par zéro afficherait « charges à l'infini » pour une parcelle simplement
pas encore récoltée.

---

## 6. L'architecture et les choix techniques

### Q. Pourquoi un monolithe et non des microservices ?

Deux développeurs, un domaine. Le découpage coûterait en complexité, en latence et en
exploitation ce qu'il ne rapporterait ni en performance ni en clarté. La seule frontière
qui se justifie est celle du microservice d'inférence, et elle existe, pour les raisons
données plus haut.

### Q. Quelle est l'organisation du code ?

`controller → service (interface + implémentation) → repository → model`, avec les DTO
séparés en requête et réponse. La logique métier isolée vit dans `service/support` :
classes **sans état et sans transaction**, donc directement instanciables et testables
sans base. C'est là que se trouvent les huit moteurs, le résolveur de stade, l'analyseur
d'effet, le calculateur de marge.

### Q. Pourquoi tous les identifiants sont-ils des chaînes en JSON ?

Ce sont des Snowflake sur 64 bits, donc dix-neuf chiffres. La limite des entiers sûrs en
JavaScript est autour de neuf millions de milliards : au-delà, le langage arrondit **en
silence**. Un identifiant arrondi désigne une ressource inexistante, ce qui produit des
404 impossibles à déboguer.

> **Si on insiste : pourquoi Snowflake et non une séquence ?** Un identifiant fourni par
> l'application permet de connaître la clé avant l'écriture, et ne crée pas de point de
> contention sur une séquence unique. Le prix est cette précaution de sérialisation.

### Q. Pourquoi Flyway et non la génération de schéma par Hibernate ?

Parce que le schéma appartient à la migration, pas à l'entité. Hibernate est en mode
**validation** : il vérifie au démarrage que le schéma correspond aux entités, et refuse
de démarrer sinon. C'est un filet, et il vaut mieux le déclencher en développement qu'en
production.

> ⚠️ **La règle qui a coûté un incident :** une migration cesse d'être modifiable **au
> premier démarrage de l'application**, pas au moment du commit. Elle a été enfreinte une
> fois sur ce projet, et Flyway a refusé de démarrer sur un écart d'empreinte. La
> réparation a demandé un diagnostic avant tout, car les variantes ne produisaient pas le
> même schéma : une réparation aveugle aurait déclaré la migration conforme alors qu'il
> manquait des objets.

### Q. Comment le contrôle d'accès est-il implémenté ?

Deux mécanismes qui ne font pas le même travail. Le **rôle de plateforme** décide quelles
routes un compte peut appeler, par permissions `MODULE:ACTION`. Le **rôle d'exploitation**
décide quelles parcelles et quels domaines de données, par appartenance à une
exploitation. Ils se composent.

Le second tient dans **une seule classe**, parce que tous les domaines passent par
`PlotService.require(id)`. C'est un pari fait tôt : le cloisonnement pourrait s'écrire
dans chaque service, il serait alors à réécrire partout le jour où la notion de
propriétaire s'élargit. Ce jour est arrivé avec l'ajout des exploitations, et
l'élargissement a tenu dans cette classe.

### Q. L'organisation en coopératives est-elle obligatoire ?

Non, et c'est sa propriété la plus importante. Tous les rattachements sont facultatifs.
Une parcelle sans exploitation se comporte exactement comme avant. Une appartenance
**ajoute** un accès, elle n'en retire jamais : le propriétaire direct garde le sien en
toutes circonstances. Une exploitation mal configurée ne peut enfermer personne dehors.

### Q. Pourquoi pas de WebSocket pour le temps réel ?

Le besoin réel est un tableau de bord rafraîchi toutes les trente à soixante secondes.
Une interrogation périodique le couvre, sans maintenir des connexions ouvertes sur un
hébergement qui endort ses processus. Le projet a hérité de dépendances WebSocket d'un
scaffolding antérieur : elles ont été **retirées**, avec dix-huit autres artefacts non
utilisés.

### Q. Y a-t-il des tâches de fond ?

Non, et le système est conçu pour s'en passer. L'outbox de notification écrit l'intention
d'envoi **dans la transaction** de l'alerte, et tente la remise **après le commit**. Un
canal muet laisse la ligne en attente au lieu de faire échouer le diagnostic. La reprise
est bornée et déclenchée après l'ingestion, ou manuellement.

De même, le statut « en retard » d'une opération planifiée n'est **pas stocké** : il est
calculé à la lecture. Sans ordonnanceur, un statut persisté serait faux dès le lendemain.

---

## 7. Les données et le schéma

### Q. Combien de tables, et comment sont-elles organisées ?

Une quarantaine, réparties en deux blocs. La sécurité, l'audit et l'idempotence d'un côté,
le métier agricole de l'autre : parcelles, cultures, boîtiers, capteurs, relevés,
observations, connaissance, diagnostics, recommandations, alertes, interventions,
récoltes, prévisions météo, organisation.

### Q. Pourquoi ne pas stocker les totaux économiques ?

Un total mis en cache diverge dès la première correction de saisie, et personne ne sait
plus lequel des deux chiffres croire. Tout se recalcule.

### Q. Mais un bilan de campagne qui bouge n'est plus un bilan de campagne ?

Exactement, et c'est la tension que la clôture résout. Le bilan est **figé une seule
fois**, à la clôture, et jamais rafraîchi. Puis la route de lecture rend **les deux côte à
côte**, le figé et le recalculé, avec leur écart expliqué en français. Personne ne se
demande lequel croire : les deux sont là, datés, et l'écart **devient** le signal d'audit.

> **Le cas concret que cela attrape.** La suppression d'une récolte est réelle dans ce
> projet, pas un archivage. Une récolte supprimée après clôture rend le bilan figé faux,
> et la ligne de divergence est exactement ce qui le rend visible.

### Q. Pourquoi la suppression réelle pour les récoltes et les interventions, alors que tout le reste est archivé ?

Parce qu'une saisie fautive y fausserait les calculs, qui sont leur raison d'être. Une
parcelle archivée reste consultable et utile ; une récolte fantôme ne fait que mentir sur
la marge.

### Q. Comment gérez-vous le vocabulaire fermé, les statuts et les catégories ?

Des deux côtés. En Java, le DTO de requête est typé par une énumération : une valeur hors
vocabulaire est refusée à la désérialisation, avec la liste des valeurs acceptées dans le
message. En base, une contrainte `CHECK` garantit l'invariant même pour une écriture
directe.

> **Pourquoi les deux :** l'énumération protège l'API, la contrainte protège les données.
> Une faute de frappe dans un service passerait sinon inaperçue jusqu'à ce qu'une
> comparaison de chaînes échoue silencieusement, très loin de la cause.

### Q. Un piège que vous avez rencontré sur ce point ?

Oui, deux fois. Ajouter un nouveau type de recommandation sans étendre la contrainte
`CHECK` fait échouer l'insertion **au cœur du diagnostic**, ce qui fait perdre le
diagnostic entier. Une valeur permise et inutilisée coûte zéro ; l'inverse coûte un
diagnostic. C'est devenu une règle écrite en tête du guide de développement.

### Q. Comment sont indexées les séries temporelles ?

Index composites sur `(parcelle, date décroissante)` pour les relevés, les cultures et les
diagnostics, plus un index composite `(boîtier, date décroissante)` ajouté pour la requête
de santé des sondes, qui s'exécute à chaque ingestion.

### Q. Pourquoi vos filtres de date n'utilisent-ils pas des bornes nullables ?

Écrire « paramètre nul ou colonne supérieure au paramètre » paraissait naturel, mais
posait deux problèmes. PostgreSQL ne peut pas inférer le type d'un paramètre qui
n'apparaît que dans une comparaison à nul. Et même typé, ce prédicat **interdit au
planificateur d'utiliser l'index** sur la colonne de date, précisément celui qui compte
sur une série temporelle. Les bornes absentes sont donc remplacées par des valeurs
extrêmes, ce qui rend la clause simple, typée et indexable.

---

## 8. La sécurité

### Q. Comment l'authentification fonctionne-t-elle ?

JWT signé en HMAC-SHA256, sans état. Trois types de jetons : accès, rafraîchissement,
vérification. Le jeton de rafraîchissement est **à usage unique et roté** à chaque appel,
et seule son empreinte est stockée. Un verrouillage de compte se déclenche après cinq
échecs.

### Q. Et les boîtiers ?

Clé partagée dans un en-tête, comparée en **temps constant** pour ne pas fuir
d'information par la durée. Pas de JWT : un microcontrôleur n'a ni la mémoire ni l'horloge
pour gérer un cycle de vie de jeton, et lui en donner un obligerait à gérer son
renouvellement au champ.

### Q. Comment le premier compte administrateur est-il créé ?

Par une route d'amorçage, la seule du système sans autorisation. Exiger une permission
pour créer le compte qui les délivre serait un cercle sans issue. Ce qui rend l'ouverture
acceptable : **elle refuse de s'exécuter une seconde fois**, et rend un 409 dès qu'un
administrateur existe.

### Q. ⚠️ Quelle est la posture de sécurité actuelle ?

**Permissive, et je l'assume.** Trois réglages sont ouverts pour la démonstration : une
autorisation par URL court-circuitée, un administrateur implicite sans jeton, et un
cloisonnement par propriétaire inactif.

Ce qui est important : **le durcissement est écrit, testé, et pilotable par
configuration**. Ce ne sont pas trois chantiers, ce sont trois lignes de configuration, et
l'ordre de bascule est documenté et journalisé au démarrage. La matrice route vers
permission est couverte par quarante-quatre tests. Le secret de signature est obligatoire
et le démarrage échoue en production s'il manque.

> **L'ordre de bascule, si on le demande :** le cloisonnement d'abord, car il a l'impact
> le plus faible et se rétablit en une ligne ; l'administrateur implicite ensuite ;
> l'autorisation par URL en dernier, une fois qu'un compte administrateur fonctionne
> réellement. L'inverse enfermerait tout le monde dehors, à commencer par nous.

### Q. Les mots de passe ?

Hachés, jamais stockés en clair, avec une politique de robustesse vérifiée à la création
comme à la réinitialisation. Le jeton de réinitialisation est un UUID dont seule
l'empreinte est conservée : une fuite de base n'expose aucun lien utilisable.

### Q. Y a-t-il un audit ?

Oui, par aspect. Chaque écriture d'administration enregistre l'acteur, la route, la
méthode, l'adresse, le client, la session et le statut. Trois opérations sensibles
enregistrent en plus le **diff** des champs modifiés.

> ⚠️ **La limite :** l'audit ne trace que les actions abouties, pas les refus. Un 403
> n'apparaît pas dans le journal.

---

## 9. La qualité et les tests

### Q. Combien de tests, et de quelle nature ?

**550 tests**, en sept secondes, **sans base de données**. Ils portent sur les classes
sans état de `service/support` et sur la matrice d'autorisation. Le test qui démarre le
contexte Spring est marqué comme test d'intégration et écarté par défaut.

> **Pourquoi cela compte :** une commande de test qui exige un PostgreSQL joignable ne se
> lance pas, donc les tests ne s'écrivent pas. C'était l'état du projet au départ : un
> seul test.

### Q. Les tests ont-ils trouvé de vrais défauts ?

Oui, et c'est l'argument. Cinq défauts invisibles à la relecture, dont deux qui faisaient
perdre un diagnostic entier :

- une carte immuable qui **lève une exception** sur une clé nulle, là où une carte
  ordinaire rend nul. Une culture hors des deux connues faisait échouer le recalcul du
  stade, appelé à chaque diagnostic ;
- deux vues du même changement de stade qui se contredisaient d'un jour ;
- la couche d'autorisation par URL **plus stricte** que le contrat documenté sur deux
  routes ;
- un utilitaire de diff qui lève sur une valeur nulle, c'est-à-dire au premier champ
  passant de nul à une valeur ;
- des métadonnées d'audit figées **avant** l'exécution de la méthode auditée, donc
  systématiquement perdues.

### Q. Et les tests d'intégration ?

⚠️ C'est la limite principale. Il n'y a pas d'infrastructure de test avec base. Trois
défauts trouvés en peuplant la production le montrent : un retour de requête mal typé qui
rendait une route inopérante, une conversion temporelle incomplète qui vidait les dates
des courbes, et un décompte qui comptait les lignes au lieu des parcelles.

**Aucun des trois n'était détectable par un test unitaire**, parce que la couche fautive
est précisément celle que le bouchon remplace. La réponse est **Testcontainers**, et c'est
le prochain chantier. Tester sur une base en mémoire ne conviendrait pas : les migrations
utilisent des fonctions propres à PostgreSQL, on testerait donc un autre schéma.

### Q. Comment vérifiez-vous qu'une modification ne casse rien ?

La suite de tests, puis la validation du schéma au démarrage, qui empêche l'application de
démarrer sur un écart entre entité et migration. Et un fichier de parcours fonctionnel
versionné, à rejouer contre une instance.

---

## 10. L'exploitation réelle

### Q. Le système est-il déployé ?

Oui, backend et microservice d'inférence, avec une base PostgreSQL, et la production est
peuplée : quatre parcelles, six boîtiers, plus de trois cents relevés sur douze jours,
deux cents diagnostics, un millier et demi de conseils, une vingtaine d'alertes.

### Q. Combien de relevés le système peut-il absorber ?

Le lot accepte deux cents relevés par appel, et le coût mesuré est d'environ une
demi-seconde par relevé **avec diagnostic**. Le régulateur ramène le nombre de diagnostics
bien en dessous du nombre de relevés dès que les conditions sont stables, ce qui est le
cas la plupart du temps.

### Q. Comment un exploitant sans smartphone reçoit-il une alerte ?

Par SMS, sur un téléphone simple. Le destinataire est résolu par le système :
**l'affecté prime sur le propriétaire**, car si quelqu'un s'est vu confier le traitement,
c'est lui qu'il faut prévenir. Chaque utilisateur a un seuil personnel, des heures de
silence, et les messages de la même parcelle et du même niveau sont regroupés sur dix
minutes.

**Une alerte critique passe outre les heures de silence** : la reporter la viderait de son
sens.

### Q. Pourquoi une passerelle générique et non un SDK d'opérateur ?

Africa's Talking, Twilio et les passerelles locales exposent toutes la même chose : une
URL, un corps portant un numéro et un texte, un en-tête d'autorisation. Un client par
opérateur reviendrait à réécrire trois fois le même appel, et à devoir **livrer du code
pour changer de fournisseur**, au moment précis où l'ancien ne marche plus. Ici, changer
d'opérateur est une modification de configuration.

### Q. Les notifications sont-elles traduites ?

Partiellement, et c'est une décision. L'**enveloppe** est traduite en lingala et en
kituba : l'urgence, la parcelle, l'action à mener. Le **constat agronomique reste en
français**.

> **Pourquoi ce partage.** Le constat est une prose composée à la volée à partir des
> mesures et des seuils. Le traduire exigerait de traduire chaque règle, chaque libellé et
> chaque gabarit de phrase, à trois exemplaires, alignés à chaque évolution du moteur. Une
> traduction qui dérive est **pire** qu'une absence de traduction : elle donne un conseil
> faux dans la langue que la personne comprend le mieux, donc celui qu'elle suivra.
>
> Est traduit ce qui **décide de l'action**, et c'est aussi ce qu'on lit en premier sur
> l'écran d'un téléphone simple. Un pied de message annonce que le détail est en français
> et invite à s'appuyer sur son conseiller.

⚠️ Les formulations en lingala et en kituba sont à faire relire par un locuteur natif
avant toute mise en service, au même titre que les seuils agronomiques.

### Q. Quel est le coût d'exploitation ?

L'hébergement actuel est un palier gratuit, dont la contrainte visible est le
ralentissement au réveil. Le coût réel d'une mise en service serait dominé par les SMS et
par l'hébergement du microservice d'inférence, qui a besoin de mémoire pour ses modèles.

---

## 11. ⚠️ Les limites à assumer

> **Ces réponses se donnent d'elles-mêmes, pas sous la contrainte.** Une limite annoncée
> avec sa méthode de correction est une limite maîtrisée. Découverte à l'oral, elle coûte
> cher.

### Q. D'où viennent vos seuils agronomiques ?

**C'est la limite principale du travail, et je la mets en avant.** Les valeurs semées à
l'installation sont **indicatives** : elles viennent de références générales et n'ont pas
été validées par une source agronomique congolaise. Elles sont pourtant au cœur du
raisonnement, puisque le moteur agronomique et le moteur de risque en dépendent entièrement.

**Trois choses limitent la portée de ce défaut.** Les seuils sont **corrigibles par API
sans redéploiement**. Le rejeu de diagnostic permet de mesurer ce qu'une correction change
sur un cas réel, avant de la valider. Et les vues qui les exposent portent une mention
explicite de leur caractère indicatif.

**La méthode de validation que je propose** : faire valider les seuils de la tomate et du
manioc par un agronome de l'IRAD ou du ministère, et citer la source dans la base de
connaissance elle-même, colonne par colonne.

### Q. Le taux de suivi des conseils prouve-t-il l'efficacité du système ?

**Non, et le système le dit lui-même.** Chaque bilan économique porte une réserve
constante : le rapprochement entre conseils suivis et rendement est descriptif, jamais
causal. Le sol, la variété, la météo et l'attention portée à la parcelle varient ensemble,
et une exploitation ne fournit pas l'échantillon qui permettrait de les démêler.

Établir l'efficacité demanderait un protocole comparatif sur plusieurs exploitations et
plusieurs campagnes. C'est une perspective, pas un résultat de ce mémoire.

### Q. Avez-vous validé le système sur le terrain ?

Non. La chaîne complète a été validée techniquement, de l'ingestion au bilan, avec une
simulation matérielle. La validation agronomique au champ est la suite naturelle, et elle
demande une campagne entière, donc plusieurs mois.

### Q. Que manque-t-il pour une mise en service réelle ?

Quatre choses, par ordre de dépendance : la validation des seuils par un agronome ; le
durcissement de la sécurité, qui est écrit et attend une décision ; une infrastructure de
test avec base, pour couvrir la couche que les tests unitaires ne peuvent pas atteindre ;
et une campagne pilote sur quelques exploitations.

### Q. Le système fonctionne-t-il hors connexion ?

Non côté client. Le boîtier, lui, sait tamponner ses relevés pendant une coupure et les
rejouer avec leur heure réelle, ce qui couvre le cas le plus fréquent au champ.
L'infrastructure d'idempotence côté serveur est en place et rendrait une application
hors-ligne possible, mais elle n'est pas construite.

---

## 12. Les questions pièges

### Q. « Votre système remplace-t-il l'agronome ? »

Non, il l'outille et il le rend plus rapide. Le moteur agronomique **est** de la
connaissance d'agronome, écrite sous forme de règles et corrigible par un agronome. Le
système applique cette connaissance à des mesures continues que personne n'a le temps de
lire, et il rend chaque conseil traçable jusqu'au chiffre qui l'a déclenché. Ce qu'il ne
fait pas : décider à la place de quelqu'un, ni juger un contexte qu'il ne mesure pas.

### Q. « Pourquoi ne pas avoir utilisé un modèle de langage ? »

Parce que la valeur est dans le conseil juste et sa justification, pas dans son emballage.
Un conseil agronomique doit être **traçable** jusqu'à la mesure et au seuil qui l'ont
déclenché, et corrigible par un agronome qui modifie une règle. Un modèle génératif ne
fournit ni l'un ni l'autre, et rendrait un conseil faux impossible à corriger autrement
qu'en changeant de modèle.

### Q. « Votre précision de 0,64 sur le manioc n'est-elle pas rédhibitoire ? »

Elle serait rédhibitoire si le système se fiait au seul classifieur. C'est précisément
pourquoi il ne le fait pas. La corroboration croise la maladie prédite avec le score de
risque calculé sur les mesures de sol, et ces deux voies n'ont aucune information en
commun. Quand la confiance tombe sous 0,60, le diagnostic est marqué non fiable et **ne
lève aucune alerte**. Une précision médiocre dégrade le service, elle ne produit pas de
faux conseil urgent.

### Q. « Que se passe-t-il si vos seuils sont faux ? »

Le système produit des conseils inadaptés, et il n'a aucun moyen de le savoir seul. C'est
la limite que j'annonce. Ce qu'il offre en réponse : les seuils se corrigent par API, le
rejeu mesure ce qu'une correction aurait changé sur des cas réels, et le taux
d'application par type de conseil signale les règles que les exploitants ignorent, ce qui
est le meilleur indice disponible qu'une règle est mauvaise.

### Q. « Ce n'est pas beaucoup de code pour un mémoire, ou c'est trop ? »

Une quarantaine de tables, une trentaine de contrôleurs, huit moteurs de connaissance,
550 tests. Le volume n'est pas l'argument : ce qui l'est, c'est que chaque ajout de
colonne est **l'entrée d'un calcul** et non un formulaire de plus. C'est la ligne
directrice assumée du projet : le système calcule et décide davantage, il ne demande pas
davantage de saisie.

### Q. « Montrez-moi quelque chose qui ne marche pas. »

Volontiers, il y en a trois, et ils sont documentés.

Le bilan économique a répondu 500 pour toute parcelle jusqu'à hier, parce qu'une signature
de requête faisait interpréter un tableau de colonnes comme un tableau de lignes. Les
points de courbe sortaient sans date, parce qu'une conversion temporelle ne couvrait pas
la forme que le pilote rend réellement. Et un conseil de voisinage annonçait « 24
parcelles voisines » sur une exploitation qui en compte quatre, parce qu'il comptait les
diagnostics.

**Les trois ont été trouvés en peuplant la production, pas en relisant le code**, et c'est
exactement pourquoi je place l'infrastructure de test avec base en tête des prochains
chantiers.

### Q. « Et si le jury vous demande une fonctionnalité que vous n'avez pas ? »

La réponse honnête est de dire où elle se brancherait. Un nouveau canal de notification
demande d'implémenter une interface à trois méthodes. Un nouveau moteur de connaissance
demande une classe sans état, une catégorie existante réutilisée, et l'extension d'une
contrainte de base. Une nouvelle culture demande une séquence de stades et des seuils.
**C'est le meilleur indicateur de la qualité d'une architecture : le coût de ce qu'on n'a
pas encore fait.**

---

## 13. Les cinq phrases à ne pas rater

1. « Perdre un diagnostic parce qu'un service tiers est muet est acceptable. Perdre une
   mesure ne l'est pas : elle est irremplaçable, l'instant est passé. »

2. « La confiance du modèle mesure la certitude de la prédiction, jamais la fiabilité de
   la mesure qui l'a nourrie. C'est le seul angle mort capable de produire un conseil
   nuisible, et c'est celui que traite l'analyse de santé des sondes. »

3. « Une capacité indisponible retire une capacité, elle ne casse rien. La météo, le
   voisinage, le SMS et le service d'inférence suivent tous cette règle. »

4. « Effacer un conseil ferait disparaître le problème avec lui. C'est pourquoi
   l'arbitrage ajoute une synthèse et n'enlève rien, et pourquoi un conseil d'irrigation
   inapplicable est reformulé plutôt que supprimé. »

5. « Les seuils agronomiques sont indicatifs, corrigibles par API sans redéploiement, et
   le rejeu de diagnostic permet de mesurer ce qu'une correction change avant de la
   valider. Leur validation par un agronome est la première étape d'une mise en service
   réelle. »
