package com.sni.bilanga.utils.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La conversion que trois classes faisaient chacune de son côté, chacune
 * incomplètement.
 *
 * <p>Le cas qui a mordu en production n'est pas exotique : PostgreSQL rend un
 * {@link LocalDateTime} pour une colonne {@code timestamp} sans fuseau, et aucune des
 * trois implémentations ne le couvrait. Le repli sur {@code null} était silencieux, et
 * un client qui désérialise une date absente affiche le 1ᵉʳ janvier 1970. La courbe
 * n'était donc pas vide : elle était fausse.
 */
@DisplayName("SqlTemporal : une forme temporelle non reconnue produisait une date fausse")
class SqlTemporalTest {

    private static final Instant REFERENCE =
            LocalDateTime.of(2026, 7, 20, 14, 30, 0).toInstant(ZoneOffset.UTC);

    @Test
    @DisplayName("LocalDateTime, la forme que PostgreSQL rend et qui manquait partout")
    void localDateTimeIsReadAsUtc() {
        assertThat(SqlTemporal.toInstant(LocalDateTime.of(2026, 7, 20, 14, 30)))
                .as("colonne sans fuseau, application qui n'écrit que de l'UTC")
                .isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("java.sql.Timestamp")
    void sqlTimestamp() {
        assertThat(SqlTemporal.toInstant(java.sql.Timestamp.from(REFERENCE)))
                .isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("Instant rendu tel quel")
    void instantPassesThrough() {
        assertThat(SqlTemporal.toInstant(REFERENCE)).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("OffsetDateTime et ZonedDateTime ramènent au même instant")
    void offsetAndZonedAgree() {
        OffsetDateTime offset = REFERENCE.atOffset(ZoneOffset.ofHours(1));
        ZonedDateTime zoned = REFERENCE.atZone(ZoneOffset.ofHours(-5));

        assertThat(SqlTemporal.toInstant(offset)).isEqualTo(REFERENCE);
        assertThat(SqlTemporal.toInstant(zoned)).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("LocalDate est datée à minuit UTC, jamais au fuseau du serveur")
    void localDateStartsTheDayInUtc() {
        assertThat(SqlTemporal.toInstant(LocalDate.of(2026, 7, 20)))
                .as("relire dans le fuseau du serveur décalerait la série selon la machine")
                .isEqualTo(LocalDateTime.of(2026, 7, 20, 0, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("java.util.Date")
    void utilDate() {
        assertThat(SqlTemporal.toInstant(java.util.Date.from(REFERENCE))).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("un entier est lu comme des millisecondes depuis l'époque")
    void epochMillis() {
        assertThat(SqlTemporal.toInstant(REFERENCE.toEpochMilli())).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("null et valeur non temporelle rendent null, en un seul endroit repérable")
    void unknownShapesYieldNull() {
        assertThat(SqlTemporal.toInstant(null)).isNull();
        assertThat(SqlTemporal.toInstant("2026-07-20"))
                .as("une chaîne n'est pas silencieusement analysée : le format en base "
                    + "n'est pas garanti, et deviner produirait une date plausible et fausse")
                .isNull();
    }
}
