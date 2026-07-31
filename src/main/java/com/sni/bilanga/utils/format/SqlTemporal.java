package com.sni.bilanga.utils.format;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Convertit en {@link Instant} la valeur temporelle rendue par une requête native.
 *
 * <h2>Pourquoi cette classe existe</h2>
 *
 * <p>Une requête native ne passe pas par le typage des entités : elle rend ce que le
 * pilote JDBC et le dialecte décident. Pour une colonne {@code timestamp} sans fuseau,
 * PostgreSQL donne selon les cas un {@code java.sql.Timestamp}, un {@link LocalDateTime}
 * ou un {@link OffsetDateTime}, et cela peut changer d'une version de pilote à l'autre
 * sans que rien ne le signale.
 *
 * <p>Trois conversions coexistaient dans le code, écrites séparément, et chacune
 * couvrait un sous-ensemble différent des formes possibles. Aucune ne couvrait
 * {@link LocalDateTime}, la forme que PostgreSQL rend effectivement.
 *
 * <h2>Le défaut que cela corrige, et pourquoi il coûtait cher</h2>
 *
 * <p>Une forme non reconnue retombait sur {@code null}. Chaque point de
 * {@code /plots/{id}/history} sortait donc sans date : la courbe n'était pas vide, elle
 * était <strong>fausse</strong>, et un client qui désérialise une date absente affiche
 * le 1ᵉʳ janvier 1970. Rien ne l'annonçait, ni côté serveur ni côté client.
 *
 * <p>C'est la raison du repli explicite ci-dessous : mieux vaut une forme inconnue qui
 * rende {@code null} <em>en un seul endroit repérable</em> que trois silences
 * indépendants.
 *
 * <h2>Convention de fuseau</h2>
 *
 * <p>Les colonnes temporelles du schéma sont {@code TIMESTAMP} sans fuseau, et
 * l'application n'y écrit que de l'UTC. Les formes locales sont donc relues en UTC.
 * Les relire dans le fuseau du serveur décalerait toute une série d'une heure selon la
 * machine qui l'exécute.
 *
 * <p>Sans état, sans transaction.
 */
public final class SqlTemporal {

    private SqlTemporal() {
    }

    /**
     * @param value colonne temporelle brute, telle que rendue par le pilote
     * @return l'instant correspondant, ou {@code null} si la valeur est absente ou
     *         d'une forme non temporelle
     */
    public static Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case java.sql.Timestamp timestamp -> timestamp.toInstant();
            case LocalDateTime local -> local.toInstant(ZoneOffset.UTC);
            case OffsetDateTime offset -> offset.toInstant();
            case ZonedDateTime zoned -> zoned.toInstant();
            case LocalDate day -> day.atStartOfDay(ZoneOffset.UTC).toInstant();
            case java.util.Date date -> date.toInstant();
            case Number epochMillis -> Instant.ofEpochMilli(epochMillis.longValue());
            case null, default -> null;
        };
    }
}
