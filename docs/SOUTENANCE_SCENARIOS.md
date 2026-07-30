# Quatre scénarios de soutenance — en retenir deux

> Chacun tient en 4 à 8 minutes et démontre **une propriété que le jury ne verra pas
> ailleurs**. Ils sont classés par originalité de l'argument, pas par facilité.
>
> **Choisissez-en deux.** Trois, c'est déjà un catalogue : on retient mieux deux
> démonstrations menées jusqu'au bout qu'une visite guidée de tout le produit.

---

## Comment choisir

| | Ce que ça prouve | Durée | Risque en direct |
|---|---|:-:|:-:|
| **S1 — La sonde qui ment** | le seul angle mort capable de produire un conseil **nuisible** | 5 min | 🟢 faible |
| **S2 — Deux voies indépendantes** | la conclusion tient sur **deux pieds** qui n'ont rien en commun | 6 min | 🟠 moyen |
| **S3 — La boucle fermée** | le système **évalue ses propres conseils** | 8 min | 🟢 faible |
| **S4 — Le voisinage** | il raisonne sur un **territoire**, pas sur une parcelle | 4 min | 🟢 faible |

**Ma recommandation : S1 + S3.** Le premier est l'argument le plus original et le plus
court ; le second est celui qui montre un *produit* et non une intégration de modèle. Ils
ne se recouvrent pas et se répondent : l'un dit pourquoi le système se tait, l'autre
pourquoi il vaut la peine qu'on l'écoute.

**Si le jury est plutôt scientifique**, remplacez S3 par S2.

---

## S1 — La sonde qui ment

> ⏱ 5 min · 🟢 risque faible · **l'argument le plus original du projet**

### Ce que cela prouve

Une sonde en panne **ne renvoie presque jamais une valeur absurde**. Elle se fige sur sa
dernière lecture, ou elle dérive à mesure que l'électrode s'encrasse — en restant tout du
long dans des valeurs parfaitement crédibles.

C'est le seul angle mort capable de produire un conseil **nuisible** : un diagnostic fondé
sur une sonde qui dérive est faux, et il est présenté avec exactement la même assurance
qu'un diagnostic juste.

> **La phrase qui porte** : « La confiance du modèle mesure la certitude de la prédiction.
> Elle ne dit rien de la fiabilité de la mesure qui l'a nourrie. Ce sont deux questions
> différentes, et le système les traite séparément. »

### Préparation

Une parcelle, une culture en cours, le simulateur Wokwi avec **`JITTER = 0`**.

### Déroulé

1. **Montrez un relevé normal** — `diagnosed: true`, des recommandations.
2. **Lancez la simulation à `JITTER = 0`.** Les valeurs deviennent strictement identiques.
3. **Au sixième relevé** : `sensorHealth: DEFAILLANTE`, `skipReason: SONDE_DEFAILLANTE`.
   Le diagnostic est **inhibé**.
4. **Montrez l'alerte** — `category: TECHNIQUE`, `level: ELEVEE`.
5. **Repassez `JITTER` à `0.03`.** Au relevé suivant, l'alerte **se referme d'elle-même**.

### Les trois points à commenter

- **Égalité exacte, six fois.** Une mesure physique réelle varie toujours au moins sur sa
  dernière décimale. Deux relevés identiques arrivent ; six d'affilée ne sont plus un
  phénomène naturel.
- **`ELEVEE`, pas `CRITIQUE`.** La parcelle n'est pas en danger — c'est la surveillance qui
  l'est. Réserver le critique à ce qui menace la culture préserve son sens.
- **L'alerte se referme seule.** Sans cela, une sonde remplacée laisserait un signalement
  que plus rien ne justifie, et le technicien apprendrait à ignorer une liste qui ne se
  vide jamais.

### Si le jury pousse

> « Et si vous n'avez qu'un seul boîtier ? »

Assumez la limite : **sans voisin, une dérive lente est rigoureusement indiscernable d'une
évolution réelle du sol.** Seule la règle de la valeur figée reste applicable — et c'est
déjà la plus fréquente en pratique. C'est écrit dans le code, pas rattrapé après coup.

---

## S2 — Deux voies indépendantes qui se confrontent

> ⏱ 6 min · 🟠 dépend du microservice · **l'argument le plus solide scientifiquement**

### Ce que cela prouve

Le diagnostic ne repose pas sur un classifieur. Deux chaînes **sans aucune information en
commun** produisent chacune un avis :

- un **réseau convolutif** entraîné sur des images de feuilles ;
- un **moteur déterministe** appliqué à des mesures de sol.

Quand elles concordent, la conclusion tient sur deux pieds. Quand elles divergent, le
système **le dit** au lieu de le taire.

> **La phrase qui porte** : « Ces deux voies n'ont aucune information en commun. C'est ce
> qui fait la valeur de leur accord — et ce qui rend leur désaccord informatif. »

### Préparation

⚠️ **Réveillez le microservice d'inférence avant de commencer** (`GET /health` dessus) : un
dyno endormi met 20 à 30 s à répondre, et le premier appel expirerait devant le jury.

Une parcelle avec un relevé récent, et une photo de feuille de tomate malade.

### Déroulé

1. `POST /diagnosis/image/predict` — photo **et** `readingId`.
2. Montrez **`corroboration`** : « les conditions mesurées corroborent ce diagnostic ».
3. Montrez **`comparison[]`** : « Mildiou retenu (97 %) plutôt qu'Alternariose (2 %) : les
   deux partagent une température entre 18 et 28 °C, mais les conditions réunissent une
   humidité de l'air > 85 % — ce qui correspond au premier et non au second. »

### Le point qui fait la différence

`corroboration` peut **nuancer** au lieu de confirmer. Si le score de risque est ≤ 0,20 :
« les conditions ne soutiennent pas la progression — symptôme d'un passé, extension peu
probable ».

> **Un système qui ne sait que confirmer n'apporte rien.** Celui-ci sait dire que les
> mesures penchent pour l'autre maladie — et il le dit, parce que le taire serait
> malhonnête.

### Ce qui peut mal tourner

| Risque | Parade |
|---|---|
| microservice endormi | le réveiller **avant**, et le redire au jury si le premier appel traîne |
| `corroboration: null` | c'est un cas légitime — « rien de concluant ». Préparez une photo dont vous avez vérifié le résultat |
| pas de relevé sur la parcelle | aucun moteur agronomique ne tourne, `limitation` le dit — montrez-le plutôt que de le subir |

---

## S3 — La boucle fermée

> ⏱ 8 min · 🟢 risque faible · **c'est ce qui en fait un produit**

### Ce que cela prouve

La chaîne va jusqu'au bout : **mesure → diagnostic → conseil → action → effet mesuré →
rendement**. Le système évalue ses propres conseils avec ses propres données.

> **La phrase qui porte** : « Le système ne se contente pas de conseiller. Il enregistre ce
> qui a été fait, et il mesure ce que cela a produit. »

### Déroulé

1. **Le conseil** — `GET /recommendations?plotId=…&status=ACTIVE`, trié par priorité.
2. **La justification** — `GET /diagnosis/{id}/explain`. Chaque conseil porte un
   `rationale` rédigé : « déclenché parce que l'humidité du sol vaut 24,00, soit en deçà du
   seuil de 35,00 ».
3. **L'action** — `POST /interventions` avec `recommendationId`. **Le conseil bascule en
   `APPLIQUEE` tout seul.**
4. **L'effet** — `GET /interventions/{id}/effect`. Comparaison 48 h avant / 48 h après,
   avec un verdict chiffré.
5. **Le bilan** — `GET /plots/{id}/economics` : marge, marge/ha, taux de suivi des conseils.

### Les deux réserves à montrer, pas à cacher

C'est le moment le plus important du scénario.

**`limitation` sur l'effet** : « cet écart constate une évolution, il n'établit pas une
cause. Une pluie survenue dans la même fenêtre produirait le même chiffre. »

**`limitation` sur l'économie** : « le rapprochement conseils suivis / rendement est un
constat, pas une démonstration. »

> **Montrez-les vous-même, avant qu'on vous les demande.** Un jury qui découvre seul qu'un
> chiffre est présenté sans réserve conclut que vous ne l'aviez pas vu. Le même jury, à qui
> vous montrez la réserve, conclut que vous avez su vous arrêter au bon endroit.

### Préparation

⚠️ Ce scénario a besoin de **données historiques** : sans 48 h de relevés de part et
d'autre de l'intervention, le verdict sera `INDETERMINE`. Voir
`docs/DONNEES_DE_DEMONSTRATION.md`.

---

## S4 — Le voisinage

> ⏱ 4 min · 🟢 risque faible · **contribution originale, et courte**

### Ce que cela prouve

Une maladie détectée sur une parcelle **élève le risque sur les parcelles proches**. La
propagation est un fait agronomique — et c'est le seul moteur dont l'information ne peut
venir d'**aucune mesure locale**.

> **La phrase qui porte** : « Une sonde parfaite ne dira jamais qu'un mildiou progresse à
> huit cents mètres. »

### Préparation

Deux parcelles géolocalisées à moins de 2 km, un diagnostic anormal récent (< 14 jours) sur
la première.

### Déroulé

1. Diagnostic de maladie sur la parcelle A.
2. Relevé **parfaitement normal** sur la parcelle B.
3. Le diagnostic de B porte un conseil **`type: VOISINAGE`**, avec la distance en clair
   (« à 800 m »).

### Le point à commenter

Le texte du conseil dit lui-même : « **Aucun symptôme n'a été relevé sur votre parcelle :
c'est une alerte de proximité, non un diagnostic.** »

> Sans cette phrase, l'exploitant lit « conditions favorables au mildiou », ne le retrouve
> pas dans ses mesures, et **cherche l'erreur dans ses sondes**. Un conseil préventif qui
> ne dit pas qu'il est préventif se retourne contre le système.

Et le moteur **se tait** si la maladie est déjà signalée localement : deux conseils pour un
même problème font douter du système, pas de la maladie.

---

## Le bonus de 90 secondes

À greffer sur n'importe quel scénario, sur une parcelle déclarée **`PLUVIAL`** :

Un conseil de stress hydrique n'y dit **jamais « irriguez »** tout court. Il est reformulé
en paillage, ombrage, binage.

> « Le constat reste vrai : le sol manque d'eau. Seule la réponse change. **Effacer le
> conseil ferait disparaître le problème avec lui**, ce qui est pire que de proposer une
> action irréalisable. »

Et la traçabilité est **préservée** — mesure, valeur observée, seuil — donc `/explain` sait
toujours justifier le conseil reformulé.

---

## Ce qu'il faut avoir vérifié la veille

- [ ] Les deux applications répondent (`/actuator/health`, `/health` du microservice)
- [ ] Un compte administrateur fonctionne, jeton en main
- [ ] Le jeu de démonstration est chargé (`docs/DONNEES_DE_DEMONSTRATION.md`)
- [ ] Le scénario retenu a été **joué en entier**, pas seulement lu
- [ ] Le microservice est **réveillé** juste avant de commencer

> **Le dyno s'endort après 30 minutes.** Si votre passage est en fin de session, réveillez
> les deux services pendant que le binôme précédent parle.

---

## La limite à assumer d'entrée

Les seuils agronomiques semés à l'installation sont **indicatifs** et n'ont pas été validés
par une source agronomique congolaise.

**Dites-le vous-même**, avec la méthode de validation que vous proposez. Un jury le
demandera de toute façon ; découvert à l'oral, c'est coûteux — annoncé et encadré, c'est
une limite maîtrisée.
