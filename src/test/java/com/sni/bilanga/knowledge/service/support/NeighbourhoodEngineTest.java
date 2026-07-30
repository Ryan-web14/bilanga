package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Le huitième moteur : pondération, non-doublon, et dégradation propre.
 *
 * <p>Trois propriétés ne se vérifient pas en relisant le code : que la
 * pondération soit un <em>produit</em> et non une moyenne (un foyer éteint
 * mitoyen doit être négligeable), que le moteur se <em>taise</em> sur une maladie
 * déjà signalée localement, et qu'il rende une liste vide dans chacun des cas où
 * il ne sait rien.
 */
@DisplayName("NeighbourhoodEngine — le risque venu d'à côté")
class NeighbourhoodEngineTest {

    private DiagnosticRepository repository;
    private BilangaProperties.Neighbourhood config;
    private NeighbourhoodEngine engine;

    private static final long PLOT_ID = 42L;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DiagnosticRepository.class);
        config = new BilangaProperties.Neighbourhood();   // 2 km / 14 j / 0,60 / 3
        engine = new NeighbourhoodEngine(repository, config);

        outbreaks(List.of());
    }

    // ============================================================
    // Dégradation propre
    // ============================================================

    @Nested
    @DisplayName("Les cas où le moteur ne sait rien")
    class Silence {

        @Test
        @DisplayName("désactivé → liste vide, et la base n'est même pas interrogée")
        void disabledQueriesNothing() {
            config.setEnabled(false);

            assertThat(engine.assess(geolocated(), Set.of())).isEmpty();
            Mockito.verifyNoInteractions(repository);
        }

        /**
         * Une parcelle sans coordonnées ne peut avoir de voisins — c'est la même
         * limite que pour la météo, et elle se règle en renseignant la fiche.
         */
        @Test
        @DisplayName("parcelle sans coordonnées → liste vide, sans requête")
        void withoutCoordinatesQueriesNothing() {
            Plot plot = new Plot();
            plot.setId(PLOT_ID);

            assertThat(engine.assess(plot, Set.of())).isEmpty();
            Mockito.verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("parcelle nulle ou non persistée → liste vide, sans exception")
        void nullPlotIsTolerated() {
            assertThat(engine.assess(null, Set.of())).isEmpty();
            assertThat(engine.assess(new Plot(), Set.of())).isEmpty();
        }

        @Test
        @DisplayName("aucun voisin diagnostiqué → liste vide")
        void noNeighbourYieldsEmpty() {
            outbreaks(List.of());

            assertThat(engine.assess(geolocated(), Set.of())).isEmpty();
        }

        /**
         * Même posture que la météo : le voisinage est un supplément. Une requête
         * en échec ne doit pas coûter le diagnostic, qui a toutes les raisons
         * d'aboutir sans lui.
         */
        @Test
        @DisplayName("une requête en échec ne fait pas échouer le diagnostic")
        void repositoryFailureIsSwallowed() {
            Mockito.when(repository.findNeighbourOutbreaks(
                            anyLong(), anyDouble(), anyDouble(), anyDouble(), any(), anyDouble()))
                    .thenThrow(new RuntimeException("base injoignable"));

            assertThat(engine.assess(geolocated(), Set.of())).isEmpty();
        }
    }

    // ============================================================
    // Le non-doublon
    // ============================================================

    @Nested
    @DisplayName("Il ne double pas RiskEngine")
    class NoDuplication {

        /**
         * Si les conditions locales signalent déjà le risque, le voisinage ne fait
         * que le renforcer. Émettre un second conseil sur la même maladie ferait
         * douter du système plutôt que de la maladie — et l'exploitant lirait deux
         * fois la même chose sous deux étiquettes.
         */
        @Test
        @DisplayName("une maladie déjà signalée localement est écartée")
        void locallyKnownDiseaseIsSkipped() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "Voisin A", 0.5, hoursAgo(6))));

            assertThat(engine.assess(geolocated(), Set.of("Late_blight")))
                    .as("le risque local prime : un seul conseil pour un seul problème")
                    .isEmpty();
        }

        @Test
        @DisplayName("une autre maladie du même voisin passe malgré tout")
        void otherDiseaseStillReported() {
            outbreaks(List.<Object[]>of(
                    row("Late_blight", "tomate", "Voisin A", 0.5, hoursAgo(6)),
                    row("Early_blight", "tomate", "Voisin A", 0.5, hoursAgo(6))));

            List<RecommendationItem> items = engine.assess(geolocated(), Set.of("Late_blight"));

            assertThat(items).hasSize(1);
            assertThat(items.getFirst().getContent()).contains("Early blight");
        }
    }

    // ============================================================
    // La pondération
    // ============================================================

    @Nested
    @DisplayName("Pondération : distance × fraîcheur, jamais la moyenne")
    class Weighting {

        @Test
        @DisplayName("un foyer proche et récent est de priorité HAUTE")
        void closeAndRecentIsHigh() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "Voisin A", 0.2, hoursAgo(4))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getPriority())
                    .isEqualTo("HAUTE");
        }

        /**
         * <strong>Le cas qui justifie le produit plutôt que la moyenne.</strong> Un
         * foyer mitoyen — donc de proximité maximale — mais détecté il y a douze
         * jours sur une fenêtre de quatorze doit être <em>négligeable</em>. Une
         * moyenne des deux facteurs le placerait à mi-hauteur et il resterait de
         * priorité haute, alertant sur un foyer très probablement éteint.
         */
        @Test
        @DisplayName("un foyer mitoyen mais ancien retombe en priorité MOYENNE")
        void closeButStaleIsDowngraded() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "Voisin A", 0.05, daysAgo(12))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getPriority())
                    .as("la fraîcheur doit pouvoir annuler la proximité, non la compenser")
                    .isEqualTo("MOYENNE");
        }

        @Test
        @DisplayName("un foyer récent mais lointain retombe aussi en MOYENNE")
        void recentButFarIsDowngraded() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "Voisin A", 1.9, hoursAgo(2))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getPriority())
                    .isEqualTo("MOYENNE");
        }

        /**
         * C'est le foyer le plus menaçant qui décide de la priorité, non la moyenne
         * des foyers : un foyer mitoyen d'hier ne doit pas être dilué par deux
         * foyers lointains et anciens.
         */
        @Test
        @DisplayName("le foyer le plus menaçant décide de la priorité du groupe")
        void worstOutbreakDecides() {
            outbreaks(List.<Object[]>of(
                    row("Late_blight", "tomate", "Loin", 1.9, daysAgo(13)),
                    row("Late_blight", "tomate", "Tout près", 0.1, hoursAgo(3))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getPriority())
                    .isEqualTo("HAUTE");
        }

        @Test
        @DisplayName("les foyers sont rendus du plus menaçant au moins menaçant")
        void outbreaksAreSortedByThreat() {
            outbreaks(List.<Object[]>of(
                    row("Early_blight", "tomate", "Loin", 1.9, daysAgo(13)),
                    row("Late_blight", "tomate", "Près", 0.1, hoursAgo(3))));

            assertThat(engine.assess(geolocated(), Set.of()))
                    .extracting(RecommendationItem::getContent)
                    .satisfies(contents ->
                            assertThat(contents.getFirst()).contains("Late blight"));
        }
    }

    // ============================================================
    // Agrégation par maladie
    // ============================================================

    @Nested
    @DisplayName("Un conseil par maladie, pas par diagnostic")
    class Aggregation {

        /**
         * Trois voisins touchés par le même mildiou constituent <em>un</em> foyer.
         * En produire trois conseils identiques ferait passer la chronologie et la
         * liste de conseils pour du bruit.
         */
        @Test
        @DisplayName("trois voisins, même maladie → un seul conseil")
        void sameDiseaseIsGroupedOnce() {
            outbreaks(List.<Object[]>of(
                    row("Late_blight", "tomate", "Voisin A", 0.4, hoursAgo(5)),
                    row("Late_blight", "tomate", "Voisin B", 0.8, hoursAgo(9)),
                    row("Late_blight", "tomate", "Voisin C", 1.2, daysAgo(2))));

            List<RecommendationItem> items = engine.assess(geolocated(), Set.of());

            assertThat(items).hasSize(1);
            assertThat(items.getFirst().getContent())
                    .as("mais le nombre de parcelles est une information : "
                            + "il distingue un cas isolé d'une progression")
                    .contains("3 parcelles voisines");
        }

        @Test
        @DisplayName("la distance annoncée est celle du foyer le PLUS PROCHE")
        void nearestDistanceIsReported() {
            outbreaks(List.<Object[]>of(
                    row("Late_blight", "tomate", "Loin", 1.5, hoursAgo(5)),
                    row("Late_blight", "tomate", "Près", 0.35, hoursAgo(5))));

            RecommendationItem item = engine.assess(geolocated(), Set.of()).getFirst();

            assertThat(item.getContent())
                    .as("c'est le foyer le plus proche qui menace")
                    .contains("350 m");
            assertThat(item.getObservedValue()).isEqualTo(0.35);
        }

        @Test
        @DisplayName("un seul voisin → sa parcelle est nommée")
        void singleNeighbourIsNamed() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "Parcelle Rivière", 0.4, hoursAgo(5))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("Parcelle Rivière")
                    .doesNotContain("parcelles voisines");
        }

        @Test
        @DisplayName("le nombre de foyers est borné par la configuration")
        void outbreakCountIsCapped() {
            config.setMaxOutbreaks(2);
            outbreaks(List.<Object[]>of(
                    row("Late_blight", "tomate", "A", 0.1, hoursAgo(1)),
                    row("Early_blight", "tomate", "B", 0.2, hoursAgo(2)),
                    row("Leaf_Mold", "tomate", "C", 0.3, hoursAgo(3))));

            assertThat(engine.assess(geolocated(), Set.of())).hasSize(2);
        }

        @Test
        @DisplayName("une ligne sans code de maladie est ignorée")
        void rowWithoutDiseaseCodeIsSkipped() {
            outbreaks(List.<Object[]>of(row(null, "tomate", "A", 0.1, hoursAgo(1))));

            assertThat(engine.assess(geolocated(), Set.of())).isEmpty();
        }
    }

    // ============================================================
    // Le conseil rendu
    // ============================================================

    @Nested
    @DisplayName("Le conseil rendu")
    class Content {

        /**
         * <strong>Le type doit être VOISINAGE, et non RISQUE.</strong> Un risque
         * local est observable chez soi ; un risque de voisinage est préventif et
         * rien n'est encore visible. Les confondre produirait un conseil
         * incompréhensible, et l'exploitant chercherait l'erreur dans ses sondes.
         */
        @Test
        @DisplayName("type VOISINAGE, catégorie RISQUE_MALADIE partagée avec RiskEngine")
        void typeAndCategory() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "A", 0.4, hoursAgo(5))));

            RecommendationItem item = engine.assess(geolocated(), Set.of()).getFirst();

            assertThat(item.getType()).isEqualTo("VOISINAGE");
            assertThat(item.getCategory())
                    .as("la catégorie est partagée pour que l'arbitrage et la "
                            + "déduplication traitent les deux comme un même domaine")
                    .isEqualTo("RISQUE_MALADIE");
        }

        /**
         * Sans cette réserve, l'exploitant conclut à une détection chez lui, va
         * chercher des symptômes qui n'existent pas, et cesse de croire au système
         * quand il n'en trouve pas.
         */
        @Test
        @DisplayName("le conseil dit explicitement que rien n'a été observé sur la parcelle")
        void contentStatesNothingWasObservedLocally() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "A", 0.4, hoursAgo(5))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("Aucun symptôme n'a été relevé sur votre parcelle")
                    .contains("alerte de proximité, non un diagnostic")
                    .as("et il dit quoi faire, pas seulement ce qui se passe")
                    .contains("Inspectez le feuillage")
                    .contains("sans nettoyer outils et chaussures");
        }

        /**
         * Aucun {@code sourceRuleId} : ce conseil ne vient d'aucune règle de la base
         * de connaissance mais d'une observation faite ailleurs. En inventer un ferait
         * mentir {@code DiagnosisExplainer} sur son origine.
         */
        @Test
        @DisplayName("aucun sourceRuleId, mais une traçabilité par la distance")
        void traceabilityIsTheDistance() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "A", 0.4, hoursAgo(5))));

            RecommendationItem item = engine.assess(geolocated(), Set.of()).getFirst();

            assertThat(item.getSourceRuleId()).isNull();
            assertThat(item.getMeasureField()).isEqualTo("distance_km");
            assertThat(item.getObservedValue()).isEqualTo(0.4);
            assertThat(item.getThresholdValue()).isEqualTo(config.getRadiusKm());
        }

        /**
         * « 0,8 km » se lit moins bien que « 800 m », et c'est sous le kilomètre que
         * la précision compte — au-delà, l'ordre de grandeur suffit.
         */
        @Test
        @DisplayName("la distance est en mètres sous le kilomètre, en kilomètres au-delà")
        void distanceFormatting() {
            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "A", 0.8, hoursAgo(5))));
            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("800 m");

            outbreaks(List.<Object[]>of(row("Late_blight", "tomate", "A", 1.5, hoursAgo(5))));
            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("1,5 km");
        }

        @Test
        @DisplayName("les soulignés des codes du modèle sont retirés pour l'affichage")
        void diseaseCodeIsHumanised() {
            outbreaks(List.<Object[]>of(row("Septoria_leaf_spot", "tomate", "A", 0.4, hoursAgo(5))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("Septoria leaf spot")
                    .doesNotContain("Septoria_leaf_spot");
        }

        /**
         * Une culture hors vocabulaire est <strong>conservée telle quelle</strong>,
         * et non remplacée par une périphrase : {@code Culture.canonical} le fait
         * délibérément, pour qu'une donnée historique ne disparaisse pas d'une
         * réponse au motif qu'elle ne correspond à aucune constante. « détecté sur
         * haricot » est plus informatif que « détecté sur une culture voisine ».
         */
        @Test
        @DisplayName("une culture hors vocabulaire est conservée telle quelle")
        void unknownCropIsPassedThrough() {
            outbreaks(List.<Object[]>of(row("Late_blight", "haricot", "A", 0.4, hoursAgo(5))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("sur haricot")
                    .doesNotContain("une culture voisine");
        }

        /** La périphrase ne sert que quand la culture est réellement absente. */
        @Test
        @DisplayName("une culture absente donne la périphrase, pas un blanc")
        void missingCropFallsBackToPhrase() {
            outbreaks(List.<Object[]>of(row("Late_blight", null, "A", 0.4, hoursAgo(5))));

            assertThat(engine.assess(geolocated(), Set.of()).getFirst().getContent())
                    .contains("une culture voisine");
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private static Plot geolocated() {
        Plot plot = new Plot();
        plot.setId(PLOT_ID);
        plot.setLatitude(-2.7832);
        plot.setLongitude(15.4211);
        return plot;
    }

    private void outbreaks(List<Object[]> rows) {
        Mockito.when(repository.findNeighbourOutbreaks(
                        anyLong(), anyDouble(), anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(rows);
    }

    /** Une ligne telle que la requête native la rend. */
    private static Object[] row(String diseaseCode, String cropName, String plotName,
                                double distanceKm, Instant diagnosedAt) {
        return new Object[]{diseaseCode, cropName, plotName, distanceKm, diagnosedAt, 0.92};
    }

    private static Instant hoursAgo(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
