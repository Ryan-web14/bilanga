# Peupler Bilanga — ce qu'on saisit, ce que le système fabrique

> Un système d'agriculture vide se démontre mal : les courbes sont plates, les
> chronologies tiennent en trois lignes, et la moitié des écrans annoncent honnêtement
> qu'ils n'ont rien à dire. Ce document liste **tout ce qu'on peut charger**, dans quel
> ordre, et surtout **ce qui reste vide si on ne le fait pas**.

---

## Le principe qu'il faut comprendre en premier

**On ne charge pas un diagnostic.** La moitié de ce qui donne vie au logiciel n'est pas
saisissable : c'est le pipeline qui la produit.

| Se saisit par API | **Fabriqué par le système** |
|---|---|
| utilisateurs, coopérative, exploitations | **diagnostics** |
| parcelles, cultures, boîtiers, sondes | **recommandations** |
| relevés (par l'ingestion) | **alertes** (agronomiques et techniques) |
| interventions, récoltes | **verdicts de santé de sonde** |
| itinéraire technique | **notifications** (outbox) |
| préférences de notification | **stades de croissance** (recalculés) |
| seuils agronomiques | **codes de parcelle** (`PARC-2026-000001`) |

> **Conséquence pratique** : pour obtenir des diagnostics, il faut **envoyer des relevés**.
> Insérer des lignes directement en base donnerait des courbes, mais aucun diagnostic,
> aucune recommandation, aucune alerte — donc un système qui paraît muet.

---

## 🔑 Le levier n°1 : la série historique

**C'est de loin ce qui change le plus.** Sans profondeur temporelle, six fonctionnalités
sont vides ou inertes :

| Fonction | Ce qu'il lui faut |
|---|---|
| `GET /plots/{id}/history` (courbes) | plusieurs jours de relevés |
| `TrendAnalyzer` (projection de seuil) | **≥ 4 relevés sur 6 h** |
| `SensorHealthAnalyzer` (dérive) | **fenêtre de 12 h**, et un **boîtier voisin** |
| `GET /interventions/{id}/effect` | **48 h avant ET 48 h après** l'intervention |
| `/plots/{id}/timeline` | des événements étalés, sinon trois lignes |
| `/crops/{id}/compare-previous` | une campagne **close** antérieure |

### Combien, concrètement

| Ambition | Relevés | Comment |
|---|:-:|---|
| minimum vital | ~50 sur 3 jours | `POST /ingest/readings/batch`, un appel |
| **recommandé** | ~300 sur 10 jours | 2 appels de lot par parcelle |
| confortable | ~900 sur 30 jours | 5 appels de lot |

`POST /ingest/readings/batch` accepte **200 relevés par appel**, et le lot **n'est pas
atomique** : un relevé corrompu ne fait pas perdre les autres.

> ⚠️ **Renseignez `recordedAt` sur chaque relevé du lot.** Sans lui, toute la série
> s'écrase sur l'instant d'envoi : la courbe devient un point, et l'analyse de tendance
> — qui régresse sur le temps — n'a plus rien à régresser.

### Ce qu'il faut faire varier

Une série plate ne déclenche rien. Faites **descendre l'humidité du sol** sur les derniers
jours : c'est ce qui produit un stress hydrique, donc un diagnostic, donc des conseils,
donc une alerte. Un jeu de données « tout va bien » ne montre rien du système.

---

## L'ordre de chargement

Chaque étape dépend de la précédente.

```
1. compte administrateur          POST /admin/provisioning/bootstrap-admin
2. utilisateurs (3 rôles)         POST /admin/users
3. coopérative + exploitation     POST /admin/cooperatives, /admin/farms
4. membres de l'exploitation      POST /admin/farms/{id}/members
5. parcelles (3)                  POST /plots
6. cultures (3)                   POST /crops
7. boîtiers (4)                   POST /devices
8. sondes                         POST /sensors
9. RELEVÉS HISTORIQUES            POST /ingest/readings/batch      ← le levier
       ↓ le système fabrique diagnostics, conseils, alertes
10. interventions                 POST /interventions
11. récoltes                      POST /harvests
12. itinéraire technique          POST /crops/{id}/itinerary
13. clôture de campagnes          POST /crops/{id}/close
```

---

## Le jeu minimal qui rend tout démontrable

### Utilisateurs — 4

L'administrateur, plus un par rôle : **agronome**, **technicien**, **exploitant**. Le rôle
par défaut est `EXPLOITANT` si `roleNames` est omis.

> Sans eux, impossible de montrer le cloisonnement — ni qu'un technicien ne voit pas les
> marges.

### Parcelles — 3, et chacune a une raison d'être

| Parcelle | Particularité | Ce qu'elle permet de montrer |
|---|---|---|
| **Nord** | géolocalisée, `PLUVIAL` | météo, voisinage, **reformulation des conseils d'irrigation** |
| **Sud** | géolocalisée **à moins de 2 km** de Nord | **le moteur de voisinage** |
| **Est** | sans coordonnées | la dégradation propre : ni météo ni voisinage, et le système le dit |

> ⚠️ **Deux parcelles géolocalisées à moins de 2 km** sont la seule condition pour que le
> huitième moteur produise quoi que ce soit.

### Cultures — 3 plantées à des dates **différentes**

C'est ce qui fait que les stades calculés diffèrent — et donc que les seuils appliqués
diffèrent.

| Culture | Plantée | Stade obtenu |
|---|---|---|
| tomate (Nord) | il y a ~100 j | `FRUCTIFICATION` |
| tomate (Sud) | il y a ~30 j | `CROISSANCE` |
| manioc (Est) | il y a ~200 j | `TUBERISATION` |

**Renseignez `plantedArea`** : sans elle, `yieldPerHectare` et `marginPerHectare` sont
`null`, et ce sont les **seuls chiffres comparables entre parcelles**.

### Boîtiers — 4, dont **deux sur la même parcelle**

> ⚠️ **Sans voisin, `SensorHealthAnalyzer` ne peut détecter ni dérive ni décrochage.** Il
> compare chaque boîtier à la **médiane** de ses voisins. Deux boîtiers sur la parcelle
> Nord sont la condition du scénario « la sonde qui ment » dans sa version complète.

### Campagnes closes — au moins 2 sur la même parcelle

C'est ce qui active trois vues d'un coup :

- `/plots/{id}/succession` — la suite des campagnes, les jachères ;
- **les avertissements de monoculture** — deux tomates de suite sur la même parcelle ;
- `/crops/{id}/compare-previous` — « mieux ou moins bien que l'an dernier ? ».

> ⚠️ Clôturez par **`POST /crops/{id}/close`** et non par `DELETE`. Seule la clôture riche
> fige un bilan économique — sans lui, la comparaison N vs N−1 rend `metrics: []` et dit
> pourquoi dans `missingData`.
>
> Variez les **motifs** : une `RECOLTE_NORMALE` et une `PERTE_CLIMATIQUE` rendent
> l'historique interprétable. Un rendement nul ne se lit pas de la même façon selon le
> motif.

### Récoltes — 2, **avec `unitPrice`**

Sans prix unitaire, la récolte est comptée pour zéro et signalée dans `missingData`. Avec,
tout le bilan économique s'anime : produit brut, marge, marge/ha, taux de charges.

### Interventions — 4, dont **une liée à une recommandation**

Le `recommendationId` bascule le conseil en `APPLIQUEE` : c'est ce qui ferme la boucle et
alimente le **taux de suivi** du bilan économique.

⚠️ Renseignez `cost` — sans charges, la marge est surestimée, et `missingData` le dit.

⚠️ Datez-en une **48 h après le début de votre série de relevés**, sinon
`/interventions/{id}/effect` rendra `INDETERMINE` faute de fenêtre.

---

## Ce qui reste vide si vous ne faites rien

| Écran | Reste vide sans… |
|---|---|
| courbes `/history` | série historique |
| `/interventions/{id}/effect` | 48 h de relevés **de part et d'autre** |
| `/plots/{id}/economics` | récoltes **avec prix** + interventions **avec coût** |
| `/crops/{id}/compare-previous` | deux campagnes closes par `/close` |
| `/plots/{id}/succession` | plusieurs campagnes sur la même parcelle |
| conseils `VOISINAGE` | deux parcelles géolocalisées < 2 km + un diagnostic anormal |
| conseils `METEO` | des coordonnées sur la parcelle |
| dérive de sonde | **deux boîtiers** sur la même parcelle |
| `/crops/{id}/itinerary` | opérations planifiées |
| notifications | un numéro de téléphone sur l'utilisateur |
| `estimatedCost` sur les conseils | un prix saisi sur les règles de connaissance |

---

## Trois pièges au chargement

**1. Le régulateur écarte la plupart des diagnostics.** Intervalle minimal de 5 min **et**
aucune variation notable ⇒ `CONDITIONS_STABLES`. Sur une série dense, la plupart des
relevés ne produiront pas de diagnostic — c'est normal. Pour en obtenir davantage, **faites
varier les mesures** entre deux relevés.

**2. Une culture `EN_COURS` est obligatoire** pour qu'un relevé produise un diagnostic.
Sinon : `CONTEXTE_ABSENT`. Le relevé est enregistré, mais rien n'en sort.

**3. Une seule culture `EN_COURS` par parcelle.** En déclarer une seconde renvoie 400. Pour
créer un historique, **clôturez la précédente** avant d'ouvrir la suivante.

---

## Deux façons de charger, et ce qu'elles coûtent

| | Par l'API (`/ingest/readings/batch`) | En SQL direct |
|---|---|---|
| Diagnostics produits | ✅ oui | ❌ **aucun** |
| Recommandations, alertes | ✅ oui | ❌ aucune |
| Santé des sondes évaluée | ✅ oui | ❌ non |
| Vitesse | ~200 relevés/appel | instantané |
| Cache de connaissance | évincé correctement | ⚠️ **jusqu'à 30 min de décalage** |

> **Passez par l'API.** Le SQL direct donne des courbes et rien d'autre — et c'est
> précisément ce qui manque à un système qui doit paraître vivant. Il ne se justifie que
> pour des volumes que l'API ne peut pas absorber, ce qui n'est pas le cas ici.

---

## Un ordre de grandeur réaliste

Pour un jeu complet et démontrable :

| | Quantité | Temps |
|---|:-:|:-:|
| utilisateurs, organisation | 4 + 2 | 5 min |
| parcelles, cultures, boîtiers, sondes | 3 + 3 + 4 + 11 | 10 min |
| **relevés historiques** | ~600 (2 parcelles × 10 j) | **15 min** |
| interventions, récoltes, itinéraire | 4 + 2 + 6 | 10 min |
| clôtures de campagnes | 2 | 5 min |

**Moins d'une heure**, en réutilisant les blocs de `docs/parcours-production.http`.

> Le poste le plus long est aussi le plus rentable : c'est la série historique qui fait la
> différence entre un logiciel qui répond et un logiciel qui a une histoire.

---

## Ce qu'il ne faut pas inventer

**Les prix.** `estimatedCost` sur les règles de connaissance est resté vide
délibérément : les seuils agronomiques sont déjà « indicatifs et à valider », et y ajouter
des prix inventés franchirait une ligne — un seuil approximatif oriente une *observation*,
un prix approximatif oriente une *décision d'achat*.

Si vous voulez montrer ce champ, saisissez un prix **sourcé** auprès d'un fournisseur
local, et dites d'où il vient.

**Les rendements.** Une récolte de démonstration doit rester plausible pour la culture et
la surface. Un jury agronome le verra immédiatement, et un chiffre invraisemblable jette le
doute sur tout le reste.
