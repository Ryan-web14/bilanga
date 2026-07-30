package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.audit.util.AuditDiffUtil;
import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que le journal consigne — et surtout ce qu'il ne doit jamais consigner.
 *
 * <p>Ces tests figent la raison d'être de {@link CropSnapshot}. Donner l'entité
 * {@code Crop} à {@code AuditDiffUtil} produirait trois défauts d'un coup :
 * l'initialisation du proxy {@code plot} (requêtes parasites et valeur illisible), du
 * bruit structurel à chaque écriture (le verrou optimiste bouge par définition), et des
 * identifiants Snowflake sortis en nombres à dix-neuf chiffres — incohérents avec le
 * reste de l'API.
 *
 * <p>Le test le plus important est {@link Exclusions#noJpaProxyLeaksIntoTheJournal()} :
 * il échouerait si quelqu'un « simplifiait » l'écrivain en lui passant l'entité.
 */
@DisplayName("CropSnapshot — le journal ne voit jamais l'entité")
class CropSnapshotDiffTest {

    private static final LocalDate PLANTED = LocalDate.of(2026, 4, 21);

    // ============================================================
    // Le diff, cas par cas
    // ============================================================

    @Nested
    @DisplayName("Le diff")
    class Diff {

        @Test
        @DisplayName("deux états identiques ne produisent aucune clé")
        void identicalStatesYieldNothing() {
            Crop crop = crop();

            assertThat(AuditDiffUtil.diff(CropSnapshot.of(crop), CropSnapshot.of(crop)))
                    .isEmpty();
        }

        @Test
        @DisplayName("un changement de statut produit exactement une clé")
        void statusChangeYieldsOneKey() {
            Crop before = crop();
            Crop after = crop();
            after.setStatus(CropStatus.TERMINEE.name());

            Map<String, Object> changes =
                    AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after));

            assertThat(changes).hasSize(1).containsKey("status");
            assertThat(asChange(changes, "status"))
                    .containsEntry("before", "EN_COURS")
                    .containsEntry("after", "TERMINEE");
        }

        /**
         * <strong>Le cas exact qui faisait lever {@code AuditDiffUtil}</strong> avant
         * correction : sa version d'origine utilisait {@code Map.of("before", b, …)}, et
         * les cartes immuables du JDK refusent les valeurs nulles. Or un champ qui passe
         * de {@code null} à une valeur est le diff le plus fréquent.
         */
        @Test
        @DisplayName("null → valeur, et valeur → null, sont tous deux consignés")
        void nullTransitionsAreRecorded() {
            Crop before = crop();
            before.setVariety(null);
            Crop after = crop();
            after.setVariety("Roma");

            assertThat(asChange(AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after)),
                    "variety"))
                    .containsEntry("before", null)
                    .containsEntry("after", "Roma");

            // Le sens inverse : c'est celui que le PUT partiel produira en rafale.
            assertThat(asChange(AuditDiffUtil.diff(CropSnapshot.of(after), CropSnapshot.of(before)),
                    "variety"))
                    .containsEntry("before", "Roma")
                    .containsEntry("after", null);
        }

        /**
         * Le diff sait représenter un effacement — et c'est toujours nécessaire, mais
         * pour une raison qui a changé.
         *
         * <p>Ce cas décrivait initialement un <strong>défaut</strong> : la mise à jour
         * écrasait les champs omis d'un {@code PUT} partiel, et le journal produisait
         * ces rafales sans que personne ne l'ait voulu. {@code CropUpdateMerger} a
         * corrigé cela — un champ absent n'est plus touché.
         *
         * <p>La forme reste exercée parce qu'elle décrit maintenant un effacement
         * <em>explicite</em>, demandé par {@code clearFields}. Le journal doit continuer
         * de le consigner fidèlement : c'est précisément l'opération qu'on veut pouvoir
         * relire, puisqu'elle est la seule à détruire de la donnée.
         */
        @Test
        @DisplayName("un effacement explicite est consigné champ par champ")
        void explicitClearIsRecordedFieldByField() {
            Crop before = crop();
            Crop after = crop();
            after.setVariety(null);
            after.setSeedLot(null);
            after.setPlantedArea(null);
            after.setPlantDensity(null);

            Map<String, Object> changes =
                    AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after));

            assertThat(changes).hasSize(4);
            assertThat(changes.values())
                    .allSatisfy(change -> assertThat(((Map<?, ?>) change).get("after")).isNull());
        }

        @Test
        @DisplayName("les dates sont rendues lisibles, pas en millisecondes")
        void datesAreHumanReadable() {
            Crop before = crop();
            Crop after = crop();
            after.setPlantingDate(LocalDate.of(2026, 5, 1));

            assertThat(asChange(AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after)),
                    "plantingDate"))
                    .containsEntry("before", "2026-04-21")
                    .containsEntry("after", "2026-05-01");
        }

        @Test
        @DisplayName("un instantané nul rend une carte vide, jamais une exception")
        void nullSnapshotIsTolerated() {
            assertThat(CropSnapshot.of(null)).isNull();
            assertThat(AuditDiffUtil.diff(null, CropSnapshot.of(crop()))).isEmpty();
            assertThat(AuditDiffUtil.diff(CropSnapshot.of(crop()), null)).isEmpty();
        }
    }

    // ============================================================
    // Ce qui ne doit JAMAIS apparaître
    // ============================================================

    @Nested
    @DisplayName("Les exclusions — la raison d'être de CropSnapshot")
    class Exclusions {

        /**
         * <strong>Le test phare.</strong> Il échouerait si quelqu'un « simplifiait »
         * l'écrivain en lui passant l'entité {@code Crop}.
         *
         * <p>{@code AuditDiffUtil.display()} convertit tout non-scalaire par
         * {@code String.valueOf} : sur {@code crop.plot} — proxy {@code LAZY} — cela
         * initialiserait le proxy, puis appellerait le {@code toString()} Lombok de
         * {@code Plot}, qui déréférence à son tour {@code user} et {@code farm}. Deux
         * requêtes parasites par écriture de journal, et une valeur illisible en base.
         */
        @Test
        @DisplayName("aucune association JPA ne fuit dans le journal")
        void noJpaProxyLeaksIntoTheJournal() {
            Crop before = crop();
            Crop after = crop();
            after.setPlot(otherPlot());
            after.setStatus(CropStatus.TERMINEE.name());

            Map<String, Object> changes =
                    AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after));

            assertThat(changes)
                    .as("le changement de parcelle n'a aucune raison de figurer, "
                            + "et le proxy encore moins")
                    .doesNotContainKey("plot")
                    .doesNotContainKey("plotId")
                    .containsOnlyKeys("status");
        }

        /**
         * Le verrou optimiste bouge à <strong>chaque</strong> écriture, par définition :
         * l'inclure noierait le journal sous du bruit structurel qui ne dit rien de ce que
         * l'utilisateur a changé.
         */
        @Test
        @DisplayName("ni version ni id ne figurent — ils bougent sans que personne n'agisse")
        void technicalFieldsAreExcluded() {
            Crop before = crop();
            before.setVersion(3L);
            before.setId(7L);

            Crop after = crop();
            after.setVersion(4L);
            after.setId(7L);
            after.setStatus(CropStatus.TERMINEE.name());

            assertThat(AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after)))
                    .doesNotContainKey("version")
                    .doesNotContainKey("id");
        }

        /**
         * Aucun {@code Long} dans l'instantané, et c'est délibéré :
         * {@code AuditDiffUtil.display()} laisse passer les {@code Number} tels quels, si
         * bien qu'un identifiant Snowflake sortirait en <strong>nombre JSON à dix-neuf
         * chiffres</strong> — incohérent avec le reste de l'API, où {@code JacksonConfig}
         * les sérialise en chaînes, et arrondi silencieusement par un client JavaScript.
         */
        @Test
        @DisplayName("aucun composant de l'instantané n'est un Long")
        void noLongComponentAtAll() {
            assertThat(CropSnapshot.class.getRecordComponents())
                    .as("un Long ici sortirait en nombre à 19 chiffres dans le jsonb")
                    .noneSatisfy(component ->
                            assertThat(component.getType()).isEqualTo(Long.class));
        }

        /**
         * L'instantané économique est volumineux et déjà tracé par son propre
         * {@code event_type} : le dupliquer dans chaque entrée ferait grossir la table
         * sans rien apprendre.
         */
        @Test
        @DisplayName("ni le bilan figé ni les horodatages techniques n'y figurent")
        void snapshotAndTimestampsAreExcluded() {
            Crop before = crop();
            Crop after = crop();
            after.setEconomicsSnapshot(Map.of("margin", "412000.00"));
            after.setUpdatedAt(Instant.now());
            after.setCreatedAt(Instant.now());
            after.setStatus(CropStatus.TERMINEE.name());

            assertThat(AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after)))
                    .containsOnlyKeys("status");
        }
    }

    // ============================================================
    // La clôture, vue par le journal
    // ============================================================

    @Nested
    @DisplayName("La clôture, telle que le journal la voit")
    class Closure {

        @Test
        @DisplayName("statut, date de fin et motif apparaissent ensemble")
        void closureFieldsAppearTogether() {
            Crop before = crop();
            Crop after = crop();
            after.setStatus(CropStatus.TERMINEE.name());
            after.setActualEndDate(LocalDate.of(2026, 8, 19));
            after.setClosureReason("RECOLTE_NORMALE");
            after.setClosureNote("Rendement conforme.");

            assertThat(AuditDiffUtil.diff(CropSnapshot.of(before), CropSnapshot.of(after)))
                    .containsOnlyKeys("status", "actualEndDate", "closureReason", "closureNote");
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asChange(Map<String, Object> changes, String field) {
        return (Map<String, Object>) changes.get(field);
    }

    private static Crop crop() {
        Crop crop = new Crop();
        crop.setId(7L);
        crop.setVersion(1L);
        crop.setPlot(plot());
        crop.setCropName("tomate");
        crop.setVariety("Roma");
        crop.setPlantingDate(PLANTED);
        crop.setCycleDurationDays(120);
        crop.setPlantedArea(0.8);
        crop.setPlantDensity(25000);
        crop.setSeedLot("LOT-2026-A17");
        crop.setGrowthStage("FRUCTIFICATION");
        crop.setStatus(CropStatus.EN_COURS.name());
        crop.setCreatedAt(Instant.parse("2026-04-21T08:00:00Z"));
        return crop;
    }

    private static Plot plot() {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");
        return plot;
    }

    private static Plot otherPlot() {
        Plot plot = new Plot();
        plot.setId(99L);
        plot.setName("Parcelle Sud");
        return plot;
    }
}
