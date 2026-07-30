# RBAC Bilanga — guide frontend

> **Public** : Rolle (React) et toute personne qui construit une interface d'administration.
> **Établi le 2026-07-30**, migration **V24**.
> Complète `API_FRONTEND.md`, qui décrit les routes ; celui-ci décrit **qui a le droit de les
> appeler**.

---

## 0. En une page

Bilanga superpose **deux mécanismes de contrôle** qui ne font pas le même travail. Les
confondre est la première source d'erreur.

| | **Rôle de plateforme** | **Rôle d'exploitation** |
|---|---|---|
| Répond à | « quelles **routes** ce compte peut-il appeler ? » | « quelles **parcelles**, et quels **domaines de données** ? » |
| Valeurs | `SUPER_ADMIN` `ADMIN` `AGRONOME` `TECHNICIEN` `EXPLOITANT` | `PROPRIETAIRE` `OUVRIER` `CONSEILLER` `TECHNICIEN` |
| Porté par | le compte, globalement | l'appartenance à **une** exploitation |
| Exposé dans | `GET /auth/me` → `authorities[]` | `GET /admin/farms/{id}/members` → `scopes[]` |
| Refus | **403** sur la route | **403** sur l'action, ou données **absentes** de la liste |
| Migration | V24 | V22 |

**Ils se composent.** Un `EXPLOITANT` porte `FARM:READ`, donc `GET /plots` lui est ouvert ;
`AccessGuard` décide ensuite **lesquelles** parcelles lui reviennent. Aucun ne remplace
l'autre.

> ⚠️ **`TECHNICIEN` existe dans les deux listes.** Ce n'est pas une erreur : le premier dit
> « ce compte administre du matériel sur la plateforme », le second « cette personne intervient
> sur le matériel de cette exploitation-là ». Ils se cumulent naturellement.

---

## 1. État actuel — à lire avant de coder

> 🟡 **Le contrôle d'accès est semé et opérationnel, mais deux interrupteurs le neutralisent
> encore.** C'est un choix : le frontend n'envoie pas encore de jeton, et l'activer
> aujourd'hui bloquerait tout.

| Réglage | Valeur | Effet aujourd'hui |
|---|---|---|
| `permitAll("/**")` dans `SecurityConfig` | **présent** | toutes les routes métier passent sans autorisation |
| `app.security.auto-admin.enabled` | **`true`** | une requête **sans jeton** est authentifiée comme `admin@bilanga.cg`… **si ce compte existe**. Sinon elle est anonyme |
| `app.security.ownership.enabled` | **`false`** | `AccessGuard` ne cloisonne rien : `?userId=` est pris au mot |

**Ce qui marche donc déjà** — et que vous pouvez construire dès maintenant :

- les routes `/admin/**` gardées par `@PreAuthorize` exigent un **vrai compte** : elles
  répondent 403 tant que personne n'est connecté avec la bonne permission ;
- `GET /auth/me` renvoie les autorités réelles du compte connecté ;
- les écrans de gestion des rôles et permissions sont pleinement fonctionnels.

**Ce qui ne s'appliquera qu'après activation** : le refus des routes métier, et le
cloisonnement par propriétaire.

> **Écrivez comme si les deux étaient actifs.** Envoyez le `Bearer` dès maintenant, masquez
> les actions selon les autorités, et gérez les 403. Le durcissement est planifié et ne doit
> pas vous prendre au dépourvu — il tient en trois lignes de configuration, décrites au §7.

---

## 2. Le tout premier compte

Au premier démarrage, **aucun compte n'existe**. Une seule route permet d'en sortir :

```http
POST /sni/api/v1/admin/provisioning/bootstrap-admin
Content-Type: application/json

{
  "email": "admin@bilanga.cg",
  "firstname": "Joel",
  "lastname": "M.",
  "password": "…",
  "generatePassword": false
}
```

→ **201**, crée un compte au rôle `ADMIN` et l'active.

**Cette route est volontairement sans autorisation** — et c'est la seule du système. Exiger
une permission pour créer le compte qui les délivre serait un cercle sans issue.

Ce qui rend cette ouverture acceptable : **elle refuse de s'exécuter une seconde fois.**

```json
{
  "success": false,
  "errorCode": "CONFLICT",
  "status": 409,
  "message": "Un administrateur existe déjà : cette route ne sert qu'au tout premier amorçage. Les comptes suivants se créent via POST /admin/users, authentifié et gardé par la permission SYSTEM:USERS."
}
```

> 💡 **Conseil pratique pour le développement.** Si vous amorcez avec l'adresse
> `admin@bilanga.cg` (celle de `app.security.auto-admin.email`), l'auto-admin retrouvera ce
> compte et vos requêtes sans jeton seront authentifiées comme administrateur. Avec une autre
> adresse, l'auto-admin échoue silencieusement et `/admin/**` répondra 403 tant que vous ne
> vous connecterez pas vraiment.

Ensuite, tout passe par `POST /admin/users` (§5), authentifié et gardé.

---

## 3. Les cinq rôles

| Rôle | Pour qui | Ce qu'il peut |
|---|---|---|
| **`SUPER_ADMIN`** | un seul compte technique | **tout**, sans vérification de permission. Seul rôle capable d'atteindre une route non cartographiée |
| **`ADMIN`** | administrateur de la plateforme | tout, mais par les permissions — donc traçable et réductible |
| **`AGRONOME`** | expert métier | pilote la base de connaissance, suit les diagnostics. **Aucun droit système** |
| **`TECHNICIEN`** | responsable du parc | boîtiers, capteurs, relevés. **Ni agronomie, ni économie** |
| **`EXPLOITANT`** | l'agriculteur — **rôle par défaut** | tout le métier sur **ses** parcelles |

> **Renommages.** `STAFF` et `USER`, hérités d'un ancien projet, sont devenus `AGRONOME` et
> `EXPLOITANT`. Si votre code porte encore les anciens noms, ils ne correspondent plus à rien
> en base.

**`EXPLOITANT` est le rôle par défaut** : un compte créé sans `roleNames` le reçoit. C'est le
moindre privilège, et c'est délibéré.

---

## 4. Les permissions

Format **`MODULE:ACTION`**. Actions dérivées du verbe HTTP : `GET`→`READ`, `POST`→`CREATE`,
`PUT`/`PATCH`→`UPDATE`, `DELETE`→`DELETE`.

### 4.1 Matrice complète

| Permission | SUPER_ADMIN | ADMIN | AGRONOME | TECHNICIEN | EXPLOITANT |
|---|:---:|:---:|:---:|:---:|:---:|
| `SYSTEM:USERS` | ✅ | ✅ | | | |
| `SYSTEM:ROLES` | ✅ | ✅ | | | |
| `SYSTEM:PERMISSIONS` | ✅ | ✅ | | | |
| `SYSTEM:AUDIT` | ✅ | ✅ | | | |
| `SYSTEM:SETTINGS` | ✅ | ✅ | | | |
| `SYSTEM:NOTIFICATIONS` | ✅ | ✅ | | | |
| `ADMIN:ACCESS` | ✅ | ✅ | | | |
| `ORGANIZATION:READ` | ✅ | ✅ | ✅ | | ✅ |
| `ORGANIZATION:CREATE\|UPDATE\|DELETE` | ✅ | ✅ | | | |
| `FARM:READ` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `FARM:CREATE` | ✅ | ✅ | ✅ | | ✅ |
| `FARM:UPDATE` | ✅ | ✅ | ✅ | | ✅ |
| `FARM:DELETE` | ✅ | ✅ | | | ✅ |
| `IOT:READ` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `IOT:CREATE` | ✅ | ✅ | | ✅ | ✅ |
| `IOT:UPDATE` | ✅ | ✅ | | ✅ | ✅ |
| `IOT:DELETE` | ✅ | ✅ | | ✅ | |
| `DIAGNOSIS:READ` | ✅ | ✅ | ✅ | | ✅ |
| `DIAGNOSIS:CREATE` | ✅ | ✅ | ✅ | | ✅ |
| `DIAGNOSIS:UPDATE` | ✅ | ✅ | ✅ | | ✅ |
| `DIAGNOSIS:DELETE` | ✅ | ✅ | | | |
| `KNOWLEDGE:READ` | ✅ | ✅ | ✅ | | ✅ |
| `KNOWLEDGE:CREATE\|UPDATE\|DELETE` | ✅ | ✅ | ✅ | | |
| `INTERVENTION:READ\|CREATE\|UPDATE` | ✅ | ✅ | ✅ | | ✅ |
| `INTERVENTION:DELETE` | ✅ | ✅ | | | ✅ |
| `HARVEST:READ` | ✅ | ✅ | ✅ | | ✅ |
| `HARVEST:CREATE\|UPDATE\|DELETE` | ✅ | ✅ | | | ✅ |
| `OVERVIEW:READ` | ✅ | ✅ | ✅ | ✅ | ✅ |

**Trois choix qui méritent une explication** :

- **L'`AGRONOME` peut supprimer une règle de connaissance, mais pas une parcelle.** Il est
  l'expert des seuils — c'est son métier — et n'a pas à disposer du patrimoine foncier.
- **L'`EXPLOITANT` lit la base de connaissance sans pouvoir la modifier.** Un seuil engage
  **toutes** les exploitations, pas seulement la sienne.
- **Le `TECHNICIEN` ne voit ni diagnostic ni récolte.** Réparer une sonde ne demande pas de
  savoir ce qu'elle mesure, encore moins ce que la parcelle rapporte.

### 4.2 Correspondance route → permission

| Route | Permission |
|---|---|
| `/admin/users`, `/admin/provisioning` | `SYSTEM:USERS` |
| `/admin/roles` | `SYSTEM:ROLES` |
| `/admin/permissions` | `SYSTEM:PERMISSIONS` |
| `/admin/audit-logs`, `/admin/settings-audit-logs`, `/admin/idempotency-records` | `SYSTEM:AUDIT` |
| `/admin/notifications` | `SYSTEM:NOTIFICATIONS` |
| `/admin/cooperatives`, `/admin/farms` | `ORGANIZATION:{action}` |
| `/plots`, `/crops` | `FARM:{action}` |
| 🆕 `/crops/{id}/calendar` | `FARM:READ` — couvert par le préfixe, aucune règle spécifique |
| **`/plots/{id}/economics`** | **`HARVEST:READ`** — ce sont des marges, pas des parcelles |
| `/devices`, `/sensors`, `/readings`, `/observations` | `IOT:{action}` |
| `/diagnosis` | `DIAGNOSIS:{action}` |
| 🆕 `/diagnosis/{id}/replay` | `DIAGNOSIS:READ` — c'est un `GET`, couvert par le préfixe |
| `/alerts`, `/recommendations` | `DIAGNOSIS:READ` en `GET`, `DIAGNOSIS:UPDATE` sinon |
| `/knowledge/**` | `KNOWLEDGE:{action}` |
| `/interventions` | `INTERVENTION:{action}` |
| `/harvests` | `HARVEST:{action}` |
| `/overview` | `OVERVIEW:READ` |
| **`/overview/economics`** | **`HARVEST:READ`** |
| `/ingest/**` | **aucune** — clé `X-Device-Key`, pas de jeton |
| `/auth/me`, `/auth/logout`, `/notifications/preferences` | **aucune** — tout compte authentifié |

> **Acquitter une alerte est un `UPDATE`, pas un `CREATE`.** `PATCH /alerts/{id}/acknowledge`
> exige `DIAGNOSIS:UPDATE`. Seul le lancement d'un diagnostic crée quelque chose.

---

## 5. Construire les écrans

### 5.1 Lire les droits du compte connecté

```http
GET /sni/api/v1/auth/me
```

```json
{
  "userId": "…",
  "email": "agronome@bilanga.cg",
  "accountEnabled": true,
  "authorities": [
    "ROLE_AGRONOME",
    "FARM:READ", "FARM:CREATE", "FARM:UPDATE",
    "IOT:READ",
    "DIAGNOSIS:READ", "DIAGNOSIS:CREATE", "DIAGNOSIS:UPDATE",
    "KNOWLEDGE:READ", "KNOWLEDGE:CREATE", "KNOWLEDGE:UPDATE", "KNOWLEDGE:DELETE",
    "INTERVENTION:READ", "INTERVENTION:CREATE", "INTERVENTION:UPDATE",
    "HARVEST:READ", "ORGANIZATION:READ", "OVERVIEW:READ"
  ]
}
```

**Un seul tableau, deux natures** : les entrées préfixées `ROLE_` sont des rôles, les autres
`MODULE:ACTION` des permissions.

```ts
const roles       = authorities.filter(a => a.startsWith('ROLE_'));
const permissions = new Set(authorities.filter(a => a.includes(':')));

const can = (p: string) =>
  roles.includes('ROLE_SUPER_ADMIN') || permissions.has(p);
```

> **N'oubliez pas le `SUPER_ADMIN`** : il passe **sans porter** les permissions au niveau du
> serveur. Si votre `can()` ne teste que le `Set`, son interface serait vide alors qu'il a tous
> les droits. (La V24 lui sème néanmoins toutes les permissions, précisément pour que
> l'affichage reste juste — mais ne comptez pas dessus.)

### 5.2 Masquer, ne pas laisser échouer

```tsx
{can('KNOWLEDGE:UPDATE') && <button>Ajuster les seuils</button>}
{can('HARVEST:READ')     && <Tab label="Économie" />}
```

**Masquer n'est pas sécuriser** — le serveur refuse de toute façon. Mais laisser un bouton qui
répond 403 apprend à l'utilisateur que l'application est cassée.

**Ce qu'il faut masquer en priorité**, parce que le refus y est le plus déroutant :

| Écran | Condition |
|---|---|
| Onglet « Économie » d'une parcelle | `HARVEST:READ` |
| Menu « Administration » | `SYSTEM:USERS` ∨ `SYSTEM:ROLES` ∨ `SYSTEM:PERMISSIONS` ∨ `SYSTEM:AUDIT` |
| Édition des seuils agronomiques | `KNOWLEDGE:UPDATE` |
| Suppression d'une parcelle | `FARM:DELETE` |
| Retrait d'un boîtier | `IOT:DELETE` |
| Gestion des exploitations | `ORGANIZATION:CREATE` |
| 🆕 **Rejeu de diagnostic** (`/diagnosis/{id}/replay`) | `KNOWLEDGE:UPDATE` — voir la note ci-dessous |

> ### 🆕 Le rejeu : masquez sur `KNOWLEDGE:UPDATE`, pas sur `DIAGNOSIS:READ`
>
> **Le serveur n'exige que `DIAGNOSIS:READ`** — c'est un `GET` sous `/diagnosis`, et
> la route ne divulgue rien de plus que `/diagnosis/{id}`. Ce niveau est correct côté
> serveur : refuser la lecture d'un rejeu à qui peut déjà lire le diagnostic n'aurait
> aucun sens.
>
> **Mais l'écran ne s'adresse pas au même public.** Le rejeu répond à « qu'aurait dit
> le système si ce seuil avait été à 32 % ? » — une question d'agronome qui règle la
> base de connaissance, pas d'exploitant qui suit sa parcelle. Un `EXPLOITANT` porte
> `DIAGNOSIS:READ` et pourrait donc l'appeler ; lui montrer le bouton l'inviterait à
> un outil qu'il n'a aucune raison d'utiliser, et dont la lecture demande de savoir ce
> qu'est un seuil agronomique.
>
> **Masquez donc sur `KNOWLEDGE:UPDATE`** — la permission de ceux qui *modifient* les
> seuils, donc les seuls que l'écart intéresse. Placez-le près de `/knowledge/rules`
> et de `/recommendations/uptake`, pas dans le détail d'une parcelle.
>
> C'est le cas général énoncé au §5.2 : **masquer n'est pas sécuriser**. Ici on ne
> protège rien, on évite d'exposer un outil hors contexte.

### 5.3 Toujours gérer le 403

Un droit peut être retiré entre le chargement de la page et l'action. Sur **403** :
n'insistez pas, ne réessayez pas — rechargez les droits et informez.

```ts
if (error.status === 403) {
  await refreshMe();           // les droits ont peut-être changé
  toast("Vous n'avez pas les droits pour cette action.");
}
```

---

## 6. Administrer les rôles et permissions

### 6.1 Comptes — `/admin/users` · `SYSTEM:USERS`

Les routes s'adressent par **`userCode`**, pas par identifiant numérique.

| Méthode | Route |
|---|---|
| POST | `/admin/users` |
| GET | `/admin/users?email=&enabled=&locked=&deleted=` |
| GET | `/admin/users/search/by-name?query=` |
| GET | `/admin/users/{userCode}` · `/admin/users/by-email?email=` |
| PUT | `/admin/users/{userCode}` |
| PATCH | `/{userCode}/activate` · `/deactivate` · `/unlock` · `/password/reset` |
| POST | `/admin/users/{userCode}/reset-password` |
| DELETE | `/admin/users/{userCode}` |

```json
{
  "email": "agronome@bilanga.cg",
  "firstname": "Aline", "lastname": "N.",
  "phone": "06 123 45 67",
  "generatePassword": true,
  "roleNames": ["AGRONOME"]
}
```

- **`roleNames` omis ⇒ `EXPLOITANT`.** Moindre privilège par défaut.
- **`generatePassword: true`** ⇒ la réponse porte `generatedPassword`. **Affichez-le une fois**
  et n'en gardez aucune trace : il n'est plus jamais rendu.
- **`phone`** est le destinataire des alertes SMS. Sans lui, l'utilisateur est injoignable au
  champ — un libellé le disant vaut mieux qu'un champ nu.

### 6.2 Rôles — `/admin/roles` · `SYSTEM:ROLES`

Adressés par **`name`** : `POST`, `PUT /{name}`, `GET /{name}`, `GET`,
`GET /search/by-name?query=`, `PATCH /{name}/activate|deactivate`, `DELETE /{name}`.

> ⚠️ **Les cinq rôles semés portent `isSystemRole: true`.** Grisez leur suppression : les
> désactiver ou les effacer briserait l'amorçage et le rôle par défaut.

### 6.3 Permissions — `/admin/permissions` · `SYSTEM:PERMISSIONS`

Mêmes formes. Les 36 permissions semées portent `isSystemPermission: true` — même
recommandation.

> **Créer une permission depuis l'interface est presque toujours une erreur.** Le vocabulaire
> est défini côté serveur (`AppPermission`), et une permission qu'aucune route n'exige n'a
> aucun effet. Elle apparaîtrait dans les écrans en donnant l'illusion d'un droit.

### 6.4 Attributions

| Méthode | Route | Permission |
|---|---|---|
| GET | `/admin/users/{userId}/roles` | `SYSTEM:ROLES` |
| POST | `/admin/users/{userId}/roles` | `SYSTEM:ROLES` |
| DELETE | `/admin/users/{userId}/roles/{roleId}` | `SYSTEM:ROLES` |
| GET | `/admin/roles/{roleId}/permissions` | `SYSTEM:PERMISSIONS` |
| PATCH | `/admin/roles/{roleId}/permissions` | `SYSTEM:PERMISSIONS` |
| PATCH | `/admin/roles/{roleId}/permission-names` | `SYSTEM:PERMISSIONS` |
| DELETE | `/admin/roles/{roleId}/permissions` | `SYSTEM:PERMISSIONS` |

**`PATCH /permissions` remplace l'ensemble**, il n'ajoute pas. Envoyez la liste complète.
`permission-names` accepte les libellés `MODULE:ACTION` plutôt que les identifiants — plus
lisible dans une interface à cases à cocher.

### 6.5 Appartenances aux exploitations — `/admin/farms/{id}/members`

C'est le **second** mécanisme (§0). `POST` crée **ou met à jour** :

```json
{ "userId": "…", "role": "CONSEILLER" }
```

La réponse énumère les domaines ouverts :

```json
{
  "id": "…", "farmId": "…", "farmName": "Exploitation Nord",
  "userId": "…", "userName": "Aline N.", "userEmail": "…",
  "role": "CONSEILLER", "roleLabel": "Conseiller",
  "scopes": ["AGRONOMIQUE", "TECHNIQUE"],
  "joinedAt": "…"
}
```

> **Affichez `scopes`.** « Conseiller » ne dit pas de lui-même s'il donne accès aux marges, et
> celui qui attribue le rôle doit savoir ce qu'il ouvre.

| Rôle d'exploitation | `AGRONOMIQUE` | `ECONOMIQUE` | `TECHNIQUE` |
|---|:---:|:---:|:---:|
| `PROPRIETAIRE` | ✅ | ✅ | ✅ |
| `CONSEILLER` | ✅ | ❌ | ✅ |
| `OUVRIER` | ✅ | ❌ | ✅ |
| `TECHNICIEN` | ❌ | ❌ | ✅ |

**Le propriétaire direct d'une parcelle voit tout, quoi qu'il arrive** — même s'il n'est pas
membre de l'exploitation. Une appartenance **ajoute** un accès, jamais n'en retire.
Retirer le propriétaire de référence renvoie **400**.

---

## 7. Le jour de l'activation

Trois changements côté serveur, et ce qu'ils impliquent pour vous :

| Changement | Effet | Ce que le frontend doit avoir prêt |
|---|---|---|
| Retirer `ApiPath.V1 + "/**"` du `permitAll` de `SecurityConfig` | les routes métier exigent une permission | `Bearer` sur **toutes** les requêtes ; 403 gérés |
| `app.security.auto-admin.enabled: false` | plus d'admin implicite sans jeton | une vraie connexion, y compris en développement |
| `app.security.ownership.enabled: true` | `AccessGuard` cloisonne par propriétaire | `?userId=` cesse d'être respecté pour un non-privilégié : n'en dépendez pas |

**Ordre recommandé** : `ownership` d'abord (impact le plus faible), puis `auto-admin`, puis le
`permitAll` en dernier.

**Avant de retirer le `permitAll`, un compte administrateur doit exister et fonctionner** —
sinon plus personne n'entre.

### Ce qui changera pour vos écrans

- `GET /plots` d'un `EXPLOITANT` ne renverra **que ses parcelles**, plus toutes. Vos jeux de
  test le refléteront.
- `/plots/{id}/economics` répondra **403** à un `TECHNICIEN` : masquez l'onglet.
- `/overview/economics` **écartera silencieusement** les parcelles interdites : la liste peut
  être plus courte qu'ailleurs — prévoyez une mention plutôt qu'un décompte trompeur.

---

## 8. Les six pièges

1. **`SUPER_ADMIN` passe sans permission** au niveau du serveur. Testez le rôle en plus du
   `Set` de permissions.
2. **`STAFF` et `USER` n'existent plus** — `AGRONOME` et `EXPLOITANT` les remplacent.
3. **`/plots/{id}/economics` relève de `HARVEST:READ`, pas de `FARM:READ`.** C'est un onglet à
   masquer séparément.
4. **Deux mécanismes, deux refus.** Un 403 peut venir d'une permission manquante (rôle de
   plateforme) **ou** d'un domaine fermé (rôle d'exploitation). Le message du second est
   explicite : « votre rôle sur cette exploitation (technicien) ne donne pas accès à ces
   données ».
5. **Une route non cartographiée est refusée**, pas ouverte. Si une route neuve répond 403 pour
   tout le monde sauf `SUPER_ADMIN`, c'est côté serveur qu'il manque une ligne — signalez-le
   plutôt que de contourner.
6. **`bootstrap-admin` ne fonctionne qu'une fois.** Un 409 sur cette route n'est pas une panne :
   c'est le garde-fou qui joue son rôle.

---

## 9. Ce qui n'existe pas encore

- **Pas de permission par parcelle.** Le grain le plus fin est l'exploitation.
- **Pas d'expiration d'appartenance.** Un membre le reste jusqu'à retrait explicite.
- **Pas de délégation temporaire** ni de « se connecter en tant que ».
- **Pas de journal des refus** : les 403 ne sont pas tracés dans `audit_log`, seules les
  actions abouties le sont.
- **Pas d'auto-inscription.** Tout compte est créé par un administrateur.
