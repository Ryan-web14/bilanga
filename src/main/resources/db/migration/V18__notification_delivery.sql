-- ============================================================
-- V18 — Acheminement réel des notifications
--
-- La V15 a posé l'outbox, la reprise et l'accroche au commit. Il y manquait
-- deux choses sans lesquelles rien ne sort du serveur :
--
--   1. UN DESTINATAIRE. La colonne notification_outbox.recipient existe depuis
--      la V15 et n'a jamais été renseignée — pour cause : aucune table ne porte
--      de numéro de téléphone. Le seul canal implémenté (LOG) n'en avait pas
--      besoin, ce qui a masqué le manque.
--   2. DES PRÉFÉRENCES. Notifier tout le monde de la même façon revient à ne
--      notifier personne : celui qui reçoit une alerte MOYENNE à 3 h du matin
--      coupe ses notifications, et n'apprendra pas non plus la CRITIQUE du
--      lendemain.
--
-- Le SMS est le canal qui compte ici : il fonctionne sur téléphone simple, sans
-- données, avec une couverture bien supérieure à celle de l'internet mobile.
-- C'est lui qui atteint réellement l'exploitant.
-- ============================================================

-- ------------------------------------------------------------
-- 1. users — le numéro, qui n'existait nulle part
-- ------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);

-- Pas d'unicité : un chef d'exploitation et son ouvrier peuvent partager le
-- seul téléphone du village. L'imposer bloquerait un usage réel.
CREATE INDEX IF NOT EXISTS idx_users_phone
    ON users (phone) WHERE phone IS NOT NULL;

-- ------------------------------------------------------------
-- 2. notification_preference — à qui, par quoi, à quelle heure
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_preference (
    id              BIGINT PRIMARY KEY,
    version         BIGINT      NOT NULL DEFAULT 0,

    user_id         BIGINT      NOT NULL UNIQUE
                                REFERENCES users(id) ON DELETE CASCADE,

    -- Niveau propre à l'utilisateur, qui prime sur le seuil global. Un agronome
    -- veut tout voir ; un exploitant ne veut être dérangé que pour l'urgent.
    min_level       VARCHAR(20),

    -- Liste courte séparée par des virgules plutôt qu'une table de liaison :
    -- trois canaux au maximum, jamais interrogés autrement qu'en bloc pour un
    -- utilisateur donné. Une table de liaison ajouterait une jointure sans rien
    -- rendre possible.
    channels        VARCHAR(120),

    -- Le lingala et le kituba auraient du sens pour les messages d'alerte ;
    -- la colonne réserve la place sans engager la traduction.
    language        VARCHAR(10) NOT NULL DEFAULT 'fr',

    -- Heures de silence, bornes locales incluses/exclues. La plage peut
    -- enjamber minuit (22 → 6), ce que le code traite explicitement.
    quiet_from_hour INTEGER,
    quiet_to_hour   INTEGER,

    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP,

    CONSTRAINT chk_notif_pref_min_level
        CHECK (min_level IS NULL OR min_level IN ('MOYENNE', 'ELEVEE', 'CRITIQUE')),
    CONSTRAINT chk_notif_pref_quiet_from
        CHECK (quiet_from_hour IS NULL OR (quiet_from_hour >= 0 AND quiet_from_hour <= 23)),
    CONSTRAINT chk_notif_pref_quiet_to
        CHECK (quiet_to_hour IS NULL OR (quiet_to_hour >= 0 AND quiet_to_hour <= 23)),

    -- Une borne sans l'autre ne décrit aucune plage : les deux ou aucune.
    CONSTRAINT chk_notif_pref_quiet_pair
        CHECK ((quiet_from_hour IS NULL) = (quiet_to_hour IS NULL))
);

-- ------------------------------------------------------------
-- 3. notification_outbox — report et regroupement
-- ------------------------------------------------------------

-- Empreinte de regroupement : cinq alertes en dix minutes doivent faire un
-- message, pas cinq. Sans cela, une parcelle qui bascule d'un coup vide le
-- crédit SMS de l'exploitation et sature son téléphone.
ALTER TABLE notification_outbox ADD COLUMN IF NOT EXISTS group_key VARCHAR(120);

-- Report jusqu'à la fin des heures de silence. Une alerte ELEVEE attend ;
-- une CRITIQUE passe outre, et c'est ce qui garde son sens au niveau critique.
ALTER TABLE notification_outbox ADD COLUMN IF NOT EXISTS deferred_until TIMESTAMP;

-- La reprise saute les lignes dont le report court encore.
CREATE INDEX IF NOT EXISTS idx_notification_outbox_deferred
    ON notification_outbox (status, deferred_until);

-- Le regroupement cherche une ligne encore en attente sur la même empreinte.
CREATE INDEX IF NOT EXISTS idx_notification_outbox_group
    ON notification_outbox (group_key, status) WHERE group_key IS NOT NULL;

-- ------------------------------------------------------------
-- 4. L'unicité (alert_id, channel) doit sauter
--
-- Elle datait de la V15, quand une alerte donnait au plus un envoi par canal.
-- Le regroupement change cela : une même alerte peut être portée par un message
-- groupé puis, si elle se répète après acquittement, par un second envoi. La
-- déduplication est désormais du ressort de group_key, qui la traite mieux —
-- elle couvre plusieurs alertes, là où l'index ne couvrait qu'une.
-- ------------------------------------------------------------
DROP INDEX IF EXISTS uq_notification_alert_channel;

CREATE INDEX IF NOT EXISTS idx_notification_alert_channel
    ON notification_outbox (alert_id, channel) WHERE alert_id IS NOT NULL;
