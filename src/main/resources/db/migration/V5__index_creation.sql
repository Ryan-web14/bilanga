

-- ---------- Domaine métier : clés étrangères ----------
CREATE INDEX IF NOT EXISTS idx_plots_user          ON plots(user_id);
CREATE INDEX IF NOT EXISTS idx_crops_plot          ON crops(plot_id);
CREATE INDEX IF NOT EXISTS idx_devices_plot        ON iot_devices(plot_id);
CREATE INDEX IF NOT EXISTS idx_sensors_device      ON sensors(device_id);
CREATE INDEX IF NOT EXISTS idx_observations_plot   ON observations(plot_id);
CREATE INDEX IF NOT EXISTS idx_observations_user   ON observations(user_id);
CREATE INDEX IF NOT EXISTS idx_readings_device     ON sensor_readings(device_id);
CREATE INDEX IF NOT EXISTS idx_diagnostics_model   ON diagnostics(ai_model_id);
CREATE INDEX IF NOT EXISTS idx_diagnostics_reading ON diagnostics(reading_id);
CREATE INDEX IF NOT EXISTS idx_alerts_plot         ON alerts(plot_id);
CREATE INDEX IF NOT EXISTS idx_alerts_diagnostic   ON alerts(diagnostic_id);

-- ---------- Domaine sécurité : clés étrangères ----------
CREATE INDEX IF NOT EXISTS idx_refresh_token_user  ON refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_user      ON password_reset_token(user_id);
CREATE INDEX IF NOT EXISTS idx_ott_user            ON one_time_token(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user  ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor     ON audit_log(actor_id);

-- ---------- Index composites : recherche + tri en une passe ----------
-- Dernier relevé d'une parcelle (résolution automatique du contexte capteurs)
CREATE INDEX IF NOT EXISTS idx_readings_plot_date
    ON sensor_readings(plot_id, recorded_at DESC);

-- Culture en cours d'une parcelle (résolution automatique de la culture)
CREATE INDEX IF NOT EXISTS idx_crops_plot_status_date
    ON crops(plot_id, status, planting_date DESC);

-- Historique des diagnostics d'une parcelle
CREATE INDEX IF NOT EXISTS idx_diagnostics_plot_date
    ON diagnostics(plot_id, diagnosed_at DESC);

-- Observations d'une parcelle, les plus récentes d'abord
CREATE INDEX IF NOT EXISTS idx_observations_plot_date
    ON observations(plot_id, observed_at DESC);