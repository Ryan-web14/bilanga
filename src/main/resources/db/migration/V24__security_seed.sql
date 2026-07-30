-- ============================================================
-- V24 — Amorçage des rôles et permissions
--
-- POURQUOI. Les tables `role`, `permission`, `role_permission` et `role_user`
-- existent depuis la V1 mais sont restées VIDES. Vérifié en base le 2026-07-30 :
-- 0 rôle, 0 permission, 0 utilisateur. Conséquences concrètes :
--
--   · aucune route @PreAuthorize('SYSTEM:…') ne pouvait réussir ;
--   · `/admin/provisioning/bootstrap-admin` échouait — il attribue ADMIN, et ce
--     rôle n'existait pas ;
--   · tout compte créé recevait un rôle inexistant.
--
-- Le contrôle d'accès était entièrement écrit et entièrement inopérant.
--
-- ============================================================
-- CE QUI EST SEMÉ — ET CE QUI A ÉTÉ RETIRÉ
-- ============================================================
--
-- Le vocabulaire est repris tel quel de `AppPermission` et `SecurityRole`
-- (paquet security/authorization), qui font foi côté Java. Toute divergence
-- entre ces fichiers et cette migration rendrait le contrôle d'accès faux : les
-- garder alignés est un invariant.
--
-- ⚠️ RENOMMAGES. Les rôles `STAFF` et `USER`, hérités d'un projet de finance,
-- ne voulaient rien dire pour une plateforme agricole. Ils deviennent
-- `AGRONOME` et `EXPLOITANT` — des métiers réels, dont les droits découlent.
--
-- ⚠️ SUPPRESSIONS. L'ancien `AdminApiAuthorizationManager` dérivait des
-- permissions BILLING, PAYMENT, KYC, CASH, BOOKING, DOCUMENT, SUBSCRIPTION,
-- INVENTORY, RESOURCE, REPORT, VISITOR, CRM, SUPPORT, TASK — une trentaine de
-- modules dont AUCUN n'a de route dans Bilanga. Ils ne sont pas semés : semer
-- une permission sans route derrière donne à lire un modèle de droits qui
-- n'existe pas, et masque l'absence de celui qui manque vraiment.
--
-- ⚠️ NE PAS CONFONDRE avec `MembershipRole` (PROPRIETAIRE / OUVRIER /
-- CONSEILLER / TECHNICIEN, V22). Ces rôles-ci gouvernent l'accès aux ROUTES ;
-- ceux de la V22 gouvernent l'accès aux DONNÉES d'une exploitation. Les deux se
-- composent : une permission ouvre la route, AccessGuard filtre ce qu'on y voit.
--
-- IDEMPOTENCE : tout est en INSERT … WHERE NOT EXISTS. La migration ne remplace
-- jamais un rôle ou une permission qu'un administrateur aurait ajusté depuis —
-- ce serait le meilleur moyen de défaire en silence une décision réfléchie.
-- ============================================================


-- ------------------------------------------------------------
-- 1. Permissions — 36, une par couple (module, action) réellement contrôlé
--
-- `name` et `module:action` sont volontairement identiques : UserPrincipal
-- accorde les DEUX comme autorités distinctes. Les faire diverger doublerait le
-- vocabulaire sans rien apporter, et rendrait indécidable celui qu'un
-- @PreAuthorize doit citer.
-- ------------------------------------------------------------
INSERT INTO permission (name, display_name, module, action, is_system_permission, is_active)
SELECT v.module || ':' || v.action, v.display_name, v.module, v.action, true, true
FROM (VALUES
    -- Système
    ('SYSTEM', 'USERS',         'Gestion des utilisateurs'),
    ('SYSTEM', 'ROLES',         'Gestion des rôles'),
    ('SYSTEM', 'PERMISSIONS',   'Gestion des permissions'),
    ('SYSTEM', 'AUDIT',         'Consultation des journaux'),
    ('SYSTEM', 'SETTINGS',      'Configuration du système'),
    ('SYSTEM', 'NOTIFICATIONS', 'Supervision des envois'),
    ('ADMIN',  'ACCESS',        'Accès à l''administration'),
    -- Organisation
    ('ORGANIZATION', 'READ',   'Consulter les exploitations'),
    ('ORGANIZATION', 'CREATE', 'Créer une exploitation'),
    ('ORGANIZATION', 'UPDATE', 'Modifier une exploitation'),
    ('ORGANIZATION', 'DELETE', 'Archiver une exploitation'),
    -- Parcelles et cultures
    ('FARM', 'READ',   'Consulter parcelles et cultures'),
    ('FARM', 'CREATE', 'Créer une parcelle ou une culture'),
    ('FARM', 'UPDATE', 'Modifier une parcelle ou une culture'),
    ('FARM', 'DELETE', 'Archiver une parcelle ou une culture'),
    -- Matériel de terrain
    ('IOT', 'READ',   'Consulter boîtiers, capteurs et relevés'),
    ('IOT', 'CREATE', 'Enregistrer un boîtier, un capteur, un relevé'),
    ('IOT', 'UPDATE', 'Modifier le parc de terrain'),
    ('IOT', 'DELETE', 'Retirer du matériel'),
    -- Diagnostic
    ('DIAGNOSIS', 'READ',   'Consulter diagnostics, conseils et alertes'),
    ('DIAGNOSIS', 'CREATE', 'Lancer un diagnostic'),
    ('DIAGNOSIS', 'UPDATE', 'Acquitter, résoudre, affecter, donner suite'),
    ('DIAGNOSIS', 'DELETE', 'Supprimer un diagnostic'),
    -- Base de connaissance
    ('KNOWLEDGE', 'READ',   'Consulter les seuils et les règles'),
    ('KNOWLEDGE', 'CREATE', 'Ajouter un seuil, une maladie, une règle'),
    ('KNOWLEDGE', 'UPDATE', 'Ajuster les seuils agronomiques'),
    ('KNOWLEDGE', 'DELETE', 'Retirer une règle'),
    -- Interventions
    ('INTERVENTION', 'READ',   'Consulter les interventions'),
    ('INTERVENTION', 'CREATE', 'Déclarer une intervention'),
    ('INTERVENTION', 'UPDATE', 'Corriger une intervention'),
    ('INTERVENTION', 'DELETE', 'Supprimer une intervention'),
    -- Récoltes et économie
    ('HARVEST', 'READ',   'Consulter récoltes, rendements et marges'),
    ('HARVEST', 'CREATE', 'Enregistrer une récolte'),
    ('HARVEST', 'UPDATE', 'Corriger une récolte'),
    ('HARVEST', 'DELETE', 'Supprimer une récolte'),
    -- Tableaux de bord
    ('OVERVIEW', 'READ', 'Consulter les tableaux de bord')
) AS v(module, action, display_name)
WHERE NOT EXISTS (
    SELECT 1 FROM permission p WHERE p.name = v.module || ':' || v.action);


-- ------------------------------------------------------------
-- 2. Rôles — cinq métiers, pas des étiquettes
-- ------------------------------------------------------------
INSERT INTO role (name, display_name, description, is_system_role, is_active, version, created_at)
SELECT v.name, v.display_name, v.description, true, true, 0, now()
FROM (VALUES
    ('SUPER_ADMIN', 'Super administrateur',
     'Accès total, sans contrôle de permission. Un seul compte devrait le porter.'),
    ('ADMIN', 'Administrateur',
     'Administre la plateforme : comptes, rôles, permissions, journaux, exploitations.'),
    ('AGRONOME', 'Agronome',
     'Pilote la base de connaissance et suit les diagnostics. Aucun droit système.'),
    ('TECHNICIEN', 'Technicien',
     'Administre le parc de boîtiers et de capteurs. Ni agronomie, ni économie.'),
    ('EXPLOITANT', 'Exploitant',
     'Agriculteur. Accède à ses propres parcelles et à tout leur suivi.')
) AS v(name, display_name, description)
WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.name = v.name);


-- ------------------------------------------------------------
-- 3. SUPER_ADMIN et ADMIN — toutes les permissions
--
-- SUPER_ADMIN les reçoit bien qu'AdminApiAuthorizationManager le laisse passer
-- sans les vérifier : l'écran d'administration doit pouvoir afficher ses droits,
-- et un rôle dont la fiche est vide se lit comme un rôle sans pouvoir.
-- ------------------------------------------------------------
INSERT INTO role_permission (role_id, permission_id, created_by, is_active)
SELECT r.id, p.id, 'SYSTEM', true
FROM role r CROSS JOIN permission p
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ------------------------------------------------------------
-- 4. AGRONOME
--
-- Pilote la base de connaissance — c'est lui qui ajuste les seuils dont dépend
-- CHAQUE diagnostic, d'où le droit de suppression sur KNOWLEDGE.
-- Lit les récoltes (le rendement est une donnée agronomique) mais le bilan
-- économique reste filtré en second par AccessGuard.requireScope(ECONOMIQUE).
-- Aucun droit système : il ne crée pas de comptes et ne lit pas les journaux.
-- ------------------------------------------------------------
INSERT INTO role_permission (role_id, permission_id, created_by, is_active)
SELECT r.id, p.id, 'SYSTEM', true
FROM role r CROSS JOIN permission p
WHERE r.name = 'AGRONOME'
  AND p.name IN (
      'FARM:READ', 'FARM:CREATE', 'FARM:UPDATE',
      'IOT:READ',
      'DIAGNOSIS:READ', 'DIAGNOSIS:CREATE', 'DIAGNOSIS:UPDATE',
      'KNOWLEDGE:READ', 'KNOWLEDGE:CREATE', 'KNOWLEDGE:UPDATE', 'KNOWLEDGE:DELETE',
      'INTERVENTION:READ', 'INTERVENTION:CREATE', 'INTERVENTION:UPDATE',
      'HARVEST:READ',
      'ORGANIZATION:READ',
      'OVERVIEW:READ')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ------------------------------------------------------------
-- 5. TECHNICIEN
--
-- Le parc, et rien d'autre. Il lit les parcelles pour savoir où se trouve un
-- boîtier ; il ne voit ni diagnostic, ni conseil, ni récolte. Réparer une sonde
-- ne demande pas de savoir ce qu'elle mesure, encore moins ce que la parcelle
-- rapporte.
-- ------------------------------------------------------------
INSERT INTO role_permission (role_id, permission_id, created_by, is_active)
SELECT r.id, p.id, 'SYSTEM', true
FROM role r CROSS JOIN permission p
WHERE r.name = 'TECHNICIEN'
  AND p.name IN (
      'IOT:READ', 'IOT:CREATE', 'IOT:UPDATE', 'IOT:DELETE',
      'FARM:READ',
      'OVERVIEW:READ')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ------------------------------------------------------------
-- 6. EXPLOITANT — rôle par défaut de tout compte créé sans rôle explicite
--
-- Tout le métier sur SES parcelles, le cloisonnement étant assuré par
-- AccessGuard. Il LIT la base de connaissance sans pouvoir la modifier : un
-- seuil engage toutes les exploitations, pas seulement la sienne.
-- ------------------------------------------------------------
INSERT INTO role_permission (role_id, permission_id, created_by, is_active)
SELECT r.id, p.id, 'SYSTEM', true
FROM role r CROSS JOIN permission p
WHERE r.name = 'EXPLOITANT'
  AND p.name IN (
      'FARM:READ', 'FARM:CREATE', 'FARM:UPDATE', 'FARM:DELETE',
      'IOT:READ', 'IOT:CREATE', 'IOT:UPDATE',
      'DIAGNOSIS:READ', 'DIAGNOSIS:CREATE', 'DIAGNOSIS:UPDATE',
      'KNOWLEDGE:READ',
      'INTERVENTION:READ', 'INTERVENTION:CREATE', 'INTERVENTION:UPDATE', 'INTERVENTION:DELETE',
      'HARVEST:READ', 'HARVEST:CREATE', 'HARVEST:UPDATE', 'HARVEST:DELETE',
      'ORGANIZATION:READ',
      'OVERVIEW:READ')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ------------------------------------------------------------
-- 7. Ce que cette migration ne fait PAS
--
-- Elle ne crée AUCUN utilisateur, et surtout aucun administrateur avec un mot
-- de passe connu d'avance. Un compte semé par migration porte un mot de passe
-- qui finit dans le dépôt Git et que personne ne pense à changer : c'est la
-- porte dérobée la plus courante et la plus durable.
--
-- Le premier administrateur se crée par
--   POST /sni/api/v1/admin/provisioning/bootstrap-admin
-- qui refuse désormais de s'exécuter une seconde fois (409). Voir
-- docs/RBAC_FRONTEND.md §2.
-- ------------------------------------------------------------
