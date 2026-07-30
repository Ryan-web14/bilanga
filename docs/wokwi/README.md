# Simulateur Wokwi — n'importe quelle simulation touche le backend

## Ce qui bloquait, et qui ne bloque plus

Un relevé portant un `technicalId` **inconnu** était refusé en 404
`DEVICE_NOT_REGISTERED`.

C'est juste en exploitation — un boîtier fantôme fausserait le parc — mais c'était un mur
en simulation. Le firmware s'authentifie par **clé partagée**, pas par jeton : il ne peut
pas appeler `POST /devices`, qui exige `IOT:CREATE`. Chaque nouveau montage Wokwi imposait
donc un enregistrement manuel préalable, avec un jeton d'administration que le simulateur
n'a pas.

**Désormais, le premier relevé d'un identifiant inconnu crée le boîtier** et le rattache à
une parcelle. Le relevé suit ensuite le chemin ordinaire : plausibilité, santé de sonde,
diagnostic. Rien d'autre ne change.

```yaml
bilanga.ingest.auto-register:
  enabled: true              # BILANGA_INGEST_AUTO_REGISTER
  plot-id:                   # vide ⇒ la parcelle la plus récemment créée
  device-name-prefix: "Boîtier auto"
```

> **Le seul prérequis : au moins UNE parcelle doit exister.** Le 404 subsiste quand il n'y
> en a aucune, et c'est volontaire — un relevé doit se rattacher quelque part, et inventer
> une parcelle serait fabriquer une donnée métier à partir d'un paquet réseau. Le message
> d'erreur le dit et donne la marche à suivre.

> ⚠️ **Ce que cela ouvre.** Quiconque détient la clé d'ingestion peut créer des boîtiers.
> Il pouvait déjà déposer des relevés sur tout boîtier existant, donc déclencher des
> diagnostics et des alertes : l'ajout élargit le bruit possible, il n'ouvre pas une porte
> qui était fermée. À repasser à `false` quand le parc est stabilisé.

## Démarrer

1. **Créer une parcelle** — une seule fois, depuis `docs/parcours-production.http` §3.1.
2. Nouveau projet Wokwi → **ESP32**.
3. Coller `bilanga-esp32.ino` dans `sketch.ino`, `diagram.json` dans l'onglet du même nom.
4. Changer **`TECHNICAL_ID`** si vous lancez plusieurs simulations en parallèle.
5. ▶️ Démarrer, et lire le moniteur série.

Aucun composant à câbler : les valeurs sont simulées. Pour de vraies sondes, remplacez le
corps de la construction du JSON.

## Lire les réponses

| Réponse | Sens |
|---|---|
| `diagnosed:true` + `recommendationCount ≥ 1` | ✅ la chaîne complète a tourné |
| `skipReason: CONDITIONS_STABLES` | ✅ **normal** — rien n'a bougé depuis 5 min. Le relevé est enregistré |
| `skipReason: SONDE_DEFAILLANTE` | six valeurs identiques ⇒ diagnostic inhibé. Mettez `JITTER` > 0 |
| `skipReason: ML_INDISPONIBLE` | le microservice dort. Réveillez-le : `GET .../health` |
| `skipReason: CONTEXTE_ABSENT` | la parcelle n'a **aucune culture en cours** |
| **401** | mauvaise `DEVICE_KEY` |
| **503** | `bilanga.ingest.device-key` vide côté serveur |
| **404** | aucune parcelle n'existe |

> **`CONDITIONS_STABLES` n'est pas un échec.** Le régulateur écarte le diagnostic quand
> l'intervalle minimal n'est pas écoulé *et* qu'aucune mesure n'a bougé. Avec `WINDOW_MS`
> à 60 s et un régulateur à 5 min, la plupart des relevés le porteront — c'est le
> comportement attendu, pas un dysfonctionnement.

## Les trois réglages qui changent ce que vous verrez

**`TECHNICAL_ID`** — un par simulation. Deux simulations partageant le même identifiant se
marcheraient dessus, et la détection de sonde figée verrait des valeurs incohérentes venir
du « même » appareil.

**`JITTER`** (défaut `0.03`) — bruit relatif appliqué à chaque mesure. À `0`, les valeurs
deviennent strictement identiques et la **sonde figée** se déclenche au sixième relevé.
C'est le moyen d'exercer ce contrôle volontairement.

**`WINDOW_MS`** (défaut 60 s) — descendre sous 5 min ne produira pas plus de diagnostics,
seulement plus de `CONDITIONS_STABLES`.

## Deux montages qui valent la démonstration

**Une sonde en panne.** Lancez une simulation avec `JITTER = 0`. Au sixième relevé :
`sensorHealth: DEFAILLANTE`, une alerte `category: TECHNIQUE` est levée, et le diagnostic
est **inhibé** — mieux vaut ne rien conseiller que conseiller faux. Repassez `JITTER` à
`0.03` : l'alerte **se referme toute seule**.

**Deux boîtiers sur la même parcelle.** Lancez deux simulations, `WOKWI-01` et `WOKWI-02`,
et faussez franchement l'humidité du sol sur l'une. `SensorHealthAnalyzer` compare chaque
boîtier à la **médiane** de ses voisins et détecte le décrochage.

> Sans voisin, une dérive lente est rigoureusement indiscernable d'une évolution réelle du
> sol : seule la règle de la valeur figée reste applicable. C'est pourquoi deux boîtiers
> valent bien mieux qu'un pour montrer ce contrôle.

## Sur du matériel réel

`client.setInsecure()` lève la vérification du certificat — acceptable en simulation, où
Wokwi n'embarque pas de magasin de racines. Sur un vrai boîtier, remplacez-le par
`client.setCACert(...)` : sans cela, rien ne distingue votre backend d'un intermédiaire qui
s'y substituerait.

Et **envoyez `recordedAt`** si vous rejouez une série après une coupure réseau. Sans lui,
toute la série s'écrase sur l'instant de reconnexion, et l'analyse de tendance — qui
projette un franchissement de seuil par régression — devient fausse.
