-- ============================================================
-- V31 — journal de remise des courriels
--
-- LE MANQUE COMBLÉ. Le module d'envoi de courrier a été repris du projet
-- fintech et adapté à Microsoft Graph. Il porte une entité, EmailDeliveryLog,
-- qui n'avait aucune table : `ddl-auto: validate` aurait empêché le démarrage
-- de l'application au prochain déploiement.
--
-- ------------------------------------------------------------
-- POURQUOI CONSIGNER LES ENVOIS
--
-- Graph répond 202 « Accepted » : le message est ACCEPTÉ, pas remis. Aucune
-- API ne permet de savoir depuis le backend s'il a atteint la boîte du
-- destinataire.
--
-- Sans cette table, un code de connexion non reçu ne laisse aucune trace : on
-- ne peut ni dire s'il est parti, ni le renvoyer, ni distinguer « refusé par
-- Microsoft » de « jamais tenté ». Le journal ne prouve pas la remise — il
-- rend l'incident VISIBLE et REJOUABLE, ce qui est tout ce qu'on peut offrir.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_delivery_log (
    id              BIGINT       PRIMARY KEY,

    -- Référence lisible : MAIL-20260731-3F9A2C71. C'est elle qu'on dicte au
    -- téléphone quand on cherche pourquoi un message n'est pas arrivé — un
    -- Snowflake à dix-neuf chiffres ne se dicte pas.
    email_number    VARCHAR(100) NOT NULL UNIQUE,

    -- MICROSOFT_GRAPH aujourd'hui. Nommé plutôt que supposé : le jour où un
    -- second transport coexiste, distinguer leurs échecs est la première
    -- question qu'on se pose.
    provider        VARCHAR(60)  NOT NULL,

    from_email      VARCHAR(255),
    recipient_email VARCHAR(255) NOT NULL,
    subject         VARCHAR(255),

    body_type       VARCHAR(20)  NOT NULL,

    -- Le contenu est conservé : la reprise le relit au lieu de le
    -- reconstruire. Rejouer le gabarit demanderait des variables qu'on n'a
    -- plus, et produirait un message DIFFÉRENT de celui qu'on croit renvoyer.
    body_content    TEXT,

    status          VARCHAR(30)  NOT NULL DEFAULT 'QUEUED',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,

    -- À quoi se rattache l'envoi : OTT, réinitialisation, notification. Sans
    -- ces deux colonnes, un journal de courriels ne répond pas à la seule
    -- question qu'on lui pose vraiment — « le code que j'ai demandé est-il
    -- parti ? ».
    related_type    VARCHAR(80),
    related_code    VARCHAR(120),

    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at         TIMESTAMP,
    failed_at       TIMESTAMP,

    CONSTRAINT chk_email_delivery_status CHECK (status IN
        ('QUEUED',    -- écrit avant l'appel : un échec laisse une trace
         'SENDING',
         'SENT',      -- accepté par Graph — NON « reçu par le destinataire »
         'FAILED'))
);

-- Recherche par référence : le geste du support.
CREATE INDEX IF NOT EXISTS idx_email_delivery_number
    ON email_delivery_log (email_number);

-- « Qu'est-ce qui a échoué aujourd'hui ? » — l'écran d'exploitation.
CREATE INDEX IF NOT EXISTS idx_email_delivery_status
    ON email_delivery_log (status, created_at);

-- « Cet utilisateur a-t-il reçu quelque chose ? »
CREATE INDEX IF NOT EXISTS idx_email_delivery_recipient
    ON email_delivery_log (recipient_email);

CREATE INDEX IF NOT EXISTS idx_email_delivery_related
    ON email_delivery_log (related_type, related_code);

COMMENT ON COLUMN email_delivery_log.status IS
    'SENT signifie ACCEPTÉ PAR MICROSOFT, jamais « reçu par le destinataire ». '
    'Graph répond 202 et aucune API ne permet de savoir depuis ici si le '
    'message a atteint la boîte. Ne présentez pas SENT comme une preuve de '
    'remise.';

COMMENT ON COLUMN email_delivery_log.body_content IS
    'Conservé pour que la reprise relise le message au lieu de le '
    'reconstruire : rejouer le gabarit demanderait des variables perdues et '
    'produirait un message différent de celui qu''on croit renvoyer.';
