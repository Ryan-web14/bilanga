-- ============================================================
-- V15 — Acheminement des notifications
--
-- Une alerte n'existait qu'en base. Pour la découvrir, l'exploitant devait
-- ouvrir le tableau de bord — ce qui vide de sa substance la notion même
-- d'alerte : le système savait qu'il fallait intervenir sans jamais le dire.
--
-- Le projet n'a ni ordonnanceur, ni file de messages, ni exécution asynchrone
-- (aucun @EnableAsync, aucun @Scheduled). Cette table les remplace par le
-- minimum qui garantisse qu'un envoi ne se perde pas : l'intention d'envoi est
-- écrite dans la même transaction que l'alerte, puis tentée dans la foulée.
-- Si la tentative échoue, la ligne reste EN_ATTENTE et sera reprise — au
-- démarrage, ou par la route d'administration prévue à cet effet.
--
-- C'est le principe de l'outbox, réduit à ce que l'infrastructure permet.
-- ============================================================

CREATE TABLE IF NOT EXISTS notification_outbox (
    id             BIGINT PRIMARY KEY,
    version        BIGINT      NOT NULL DEFAULT 0,

    -- Ce qui a motivé la notification. L'alerte peut disparaître (parcelle
    -- supprimée) sans que la trace de l'envoi ait à disparaître avec elle.
    alert_id       BIGINT REFERENCES alerts(id) ON DELETE SET NULL,
    plot_id        BIGINT REFERENCES plots(id) ON DELETE SET NULL,

    channel        VARCHAR(30)  NOT NULL,
    recipient      VARCHAR(255),
    subject        VARCHAR(255),
    body           TEXT         NOT NULL,
    level          VARCHAR(20),

    status         VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),

    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMP,
    sent_at        TIMESTAMP,

    CONSTRAINT chk_notification_status
        CHECK (status IN ('EN_ATTENTE', 'ENVOYEE', 'ECHOUEE', 'ABANDONNEE')),
    CONSTRAINT chk_notification_attempts CHECK (attempts >= 0)
);

-- La reprise cherche les envois non aboutis, du plus ancien au plus récent.
CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending
    ON notification_outbox (status, created_at);

-- Une même alerte ne doit pas être notifiée deux fois sur le même canal.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_alert_channel
    ON notification_outbox (alert_id, channel)
    WHERE alert_id IS NOT NULL;
