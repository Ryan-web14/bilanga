package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Huitième moteur : le risque venu <strong>d'à côté</strong>.
 *
 * <h2>Ce qu'il apporte</h2>
 *
 * <p>Jusqu'ici le système diagnostiquait une parcelle ; il raisonne désormais sur
 * un territoire. Une maladie détectée chez un voisin proche élève le risque chez
 * soi — la propagation est un fait agronomique élémentaire, et le système
 * l'ignorait alors qu'il disposait de <em>tout</em> pour le voir : les coordonnées
 * et l'index géographique depuis la V16, et l'ensemble des diagnostics en base
 * depuis l'origine. Il ne manquait que la requête.
 *
 * <p><strong>C'est le seul moteur dont l'information ne peut venir d'aucune
 * mesure locale.</strong> Les sept autres examinent la parcelle et son ciel ;
 * celui-ci examine ses voisines. Une sonde parfaite ne dira jamais qu'un mildiou
 * progresse à huit cents mètres.
 *
 * <h2>Les quatre décisions qui le rendent utilisable</h2>
 *
 * <p><strong>1. Un type distinct, {@code VOISINAGE}, et non {@code RISQUE}.</strong>
 * Réutiliser {@code RISQUE} aurait évité une migration, mais produit un conseil
 * incompréhensible : « conditions favorables au mildiou » alors que les mesures
 * locales ne le disent pas. L'exploitant chercherait l'erreur dans ses sondes. Un
 * risque local est <em>observable chez soi</em> ; un risque de voisinage est
 * <em>préventif</em>, et rien n'est encore visible — c'est justement son intérêt.
 *
 * <p><strong>2. La catégorie, elle, est réutilisée</strong>
 * ({@code RISQUE_MALADIE}). C'est elle que {@code ConflictArbitrator} et la
 * déduplication regardent : un risque local et un risque de voisinage portant sur
 * la même maladie relèvent du même domaine d'action et doivent pouvoir être
 * réconciliés, non empilés.
 *
 * <p><strong>3. La distance ET la fraîcheur pondèrent.</strong> Un foyer à cent
 * mètres détecté hier n'appelle pas la même réaction qu'un foyer à deux
 * kilomètres détecté il y a douze jours. Une pondération sur la seule distance
 * ferait alerter indéfiniment sur des foyers éteints ; sur la seule fraîcheur,
 * elle mettrait sur le même plan le voisin mitoyen et l'autre bout du terroir.
 *
 * <p><strong>4. Il ne double pas {@code RiskEngine}.</strong> Si les conditions
 * locales réunissent déjà le risque pour cette maladie, le voisinage ne fait que
 * le renforcer : le moteur cède la place plutôt que d'émettre un second conseil
 * sur la même maladie. Deux conseils pour un même problème font douter du
 * système, pas de la maladie.
 *
 * <h2>Dégradation propre, comme les autres</h2>
 *
 * <p>Rend une <strong>liste vide</strong> si : le moteur est désactivé, la
 * parcelle n'a pas de coordonnées, ou aucun voisin n'a de diagnostic anormal
 * récent. Une capacité indisponible retire une capacité, elle ne casse rien.
 *
 * <p>Sans état ni transaction, comme les sept autres — donc directement
 * instanciable et testable sans base.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NeighbourhoodEngine {

    private static final Locale FR = Locale.FRANCE;

    /**
     * Catégorie <strong>partagée avec {@code RiskEngine}</strong>, délibérément :
     * c'est ce qui permet à l'arbitrage et à la déduplication de traiter les deux
     * comme relevant du même domaine.
     */
    private static final String CATEGORY = "RISQUE_MALADIE";

    /**
     * Au-delà de ce poids, le foyer est jugé assez proche et assez récent pour
     * mériter une priorité haute.
     *
     * <p>0,60 place la bascule autour d'un foyer à mi-rayon détecté à mi-fenêtre :
     * plus bas, tout deviendrait urgent et l'urgence perdrait son sens ; plus haut,
     * seul un foyer mitoyen de la veille alerterait — cas où l'exploitant est déjà
     * au courant par son voisin.
     */
    private static final double HIGH_PRIORITY_WEIGHT = 0.60;

    private final DiagnosticRepository diagnosticRepository;
    private final BilangaProperties.Neighbourhood config;

    /**
     * @param plot            parcelle examinée
     * @param localRiskCodes  maladies pour lesquelles les conditions locales
     *                        signalent déjà un risque ; le voisinage n'y ajoute
     *                        rien et se tait
     */
    public List<RecommendationItem> assess(Plot plot, java.util.Set<String> localRiskCodes) {
        if (!config.isEnabled() || plot == null || plot.getId() == null
                || plot.getLatitude() == null || plot.getLongitude() == null) {
            return List.of();
        }

        Instant since = Instant.now().minus(Duration.ofDays(config.getFreshnessDays()));

        List<Object[]> rows;
        try {
            rows = diagnosticRepository.findNeighbourOutbreaks(
                    plot.getId(), plot.getLatitude(), plot.getLongitude(),
                    config.getRadiusKm(), since, config.getMinConfidence());
        } catch (Exception e) {
            // Même posture que la météo : le voisinage est un supplément. Une
            // requête en échec ne doit pas coûter le diagnostic, qui a toutes les
            // raisons d'aboutir sans lui.
            log.warn("Risque de voisinage non évalué pour la parcelle {} : {}",
                    plot.getId(), e.getMessage());
            return List.of();
        }

        return buildItems(rows, localRiskCodes == null ? java.util.Set.of() : localRiskCodes);
    }

    // ============================================================
    // Agrégation
    // ============================================================

    /**
     * Un conseil par maladie, et non par diagnostic.
     *
     * <p>Trois voisins touchés par le même mildiou constituent <em>un</em> foyer et
     * doivent produire un conseil, pas trois. En revanche le nombre de parcelles
     * concernées est une information — c'est ce qui distingue un cas isolé d'une
     * progression — et elle est donc portée par le texte.
     *
     * <p>Le foyer le plus proche décide de la distance annoncée : c'est celui qui
     * menace.
     */
    private List<RecommendationItem> buildItems(List<Object[]> rows,
                                               java.util.Set<String> localRiskCodes) {

        Map<String, Outbreak> byDisease = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String diseaseCode = asString(row[0]);
            if (diseaseCode == null || diseaseCode.isBlank()) {
                continue;
            }
            // Le risque local prime : y ajouter le voisinage produirait deux
            // conseils pour la même maladie, ce qui fait douter du système.
            if (localRiskCodes.contains(diseaseCode)) {
                continue;
            }

            String cropName = asString(row[1]);
            String plotName = asString(row[2]);
            double distanceKm = asDouble(row[3], config.getRadiusKm());
            Instant diagnosedAt = asInstant(row[4]);

            Long plotId = asLong(row.length > 6 ? row[6] : null);

            byDisease.computeIfAbsent(diseaseCode,
                            code -> new Outbreak(code, cropName))
                    .record(plotId, plotName, distanceKm, diagnosedAt,
                            weightOf(distanceKm, diagnosedAt));
        }

        List<RecommendationItem> items = new ArrayList<>();

        byDisease.values().stream()
                // Le foyer le plus menaçant en premier : le tri final de
                // DiagnosisServiceImpl se fait sur la priorité, qui ne départage
                // pas deux conseils de même rang.
                .sorted(Comparator.comparingDouble(Outbreak::weight).reversed())
                .limit(config.getMaxOutbreaks())
                .forEach(outbreak -> items.add(toItem(outbreak)));

        return items;
    }

    /**
     * Poids combiné de la proximité et de la fraîcheur, dans {@code [0, 1]}.
     *
     * <p>Produit des deux facteurs, et non moyenne : un foyer très ancien doit être
     * négligeable <em>même s'il est mitoyen</em>, et un foyer très lointain
     * négligeable même s'il est d'hier. Une moyenne laisserait chacun des deux
     * facteurs compenser l'autre, et un foyer éteint de la parcelle voisine
     * continuerait d'alerter — c'est précisément ce qui apprend à l'exploitant que
     * ces conseils ne valent rien.
     */
    private double weightOf(double distanceKm, Instant diagnosedAt) {
        double proximity = Math.max(0d, 1d - (distanceKm / config.getRadiusKm()));

        double freshness = 1d;
        if (diagnosedAt != null) {
            double ageDays = Duration.between(diagnosedAt, Instant.now()).toHours() / 24d;
            freshness = Math.max(0d, 1d - (ageDays / config.getFreshnessDays()));
        }
        return proximity * freshness;
    }

    // ============================================================
    // Rédaction
    // ============================================================

    /**
     * Le conseil, rédigé pour être affiché tel quel.
     *
     * <p>Il dit trois choses, dans cet ordre : ce qui a été détecté et <em>où</em>,
     * ce que cela implique, et quoi faire. La distance chiffrée n'est pas
     * cosmétique : « détecté à 800 m » change ce que l'exploitant fera, là où
     * « détecté à proximité » ne dit rien d'actionnable.
     *
     * <p><strong>La réserve fait partie du conseil.</strong> Rien n'a été observé
     * sur cette parcelle — le dire évite que l'exploitant conclue à une détection
     * chez lui, aille chercher des symptômes qui n'existent pas, et cesse de croire
     * au système quand il n'en trouve pas.
     */
    private RecommendationItem toItem(Outbreak outbreak) {
        String content = String.format(FR,
                "%s détecté%s à %s sur %s. Aucun symptôme n'a été relevé sur votre "
                        + "parcelle : c'est une alerte de proximité, non un diagnostic. "
                        + "Inspectez le feuillage dans les jours qui viennent, en commençant "
                        + "par le bord de parcelle le plus exposé, et évitez de circuler d'une "
                        + "parcelle à l'autre sans nettoyer outils et chaussures — c'est le "
                        + "premier vecteur de propagation.%s",
                displayNameOf(outbreak.diseaseCode()),
                outbreak.plotCount() > 1
                        ? String.format(FR, " sur %d parcelles voisines", outbreak.plotCount())
                        : "",
                formatDistance(outbreak.nearestKm()),
                cropLabelOf(outbreak.cropName()),
                outbreak.plotCount() == 1 && outbreak.nearestPlotName() != null
                        ? " Parcelle concernée : " + outbreak.nearestPlotName() + "."
                        : "");

        return RecommendationItem.builder()
                .content(content)
                .type(RecommendationType.VOISINAGE.name())
                .priority(outbreak.weight() >= HIGH_PRIORITY_WEIGHT
                        ? RecommendationPriority.HAUTE.name()
                        : RecommendationPriority.MOYENNE.name())
                .category(CATEGORY)
                // Aucun sourceRuleId : ce conseil ne vient d'aucune règle de la base
                // de connaissance mais d'une observation faite ailleurs. Inventer un
                // identifiant ferait mentir DiagnosisExplainer sur son origine.
                .measureField("distance_km")
                .observedValue(round(outbreak.nearestKm()))
                .thresholdValue(config.getRadiusKm())
                .build();
    }

    /**
     * En mètres sous le kilomètre, en kilomètres au-delà.
     *
     * « 0,8 km » se lit moins bien que « 800 m », et c'est sous le kilomètre que
     * la précision compte — au-delà, l'ordre de grandeur suffit.
     */
    private String formatDistance(double km) {
        if (km < 1d) {
            return String.format(FR, "%.0f m", km * 1000);
        }
        return String.format(FR, "%.1f km", km);
    }

    /**
     * Le code de maladie est normalisé pour l'affichage : les codes du modèle
     * portent des soulignés ({@code Late_blight}), illisibles dans une phrase.
     * Le libellé français exact viendrait de {@code disease_knowledge} — non
     * interrogé ici pour ne pas ajouter une requête par foyer sur un chemin
     * déclenché à chaque relevé.
     */
    private String displayNameOf(String diseaseCode) {
        return diseaseCode.replace('_', ' ');
    }

    /**
     * Nom de la culture voisine.
     *
     * <p>Une valeur hors vocabulaire est <strong>conservée telle quelle</strong> —
     * {@code Culture.canonical} le fait délibérément, pour qu'une donnée historique
     * ne disparaisse pas d'une réponse au motif qu'elle ne correspond à aucune
     * constante. « détecté sur haricot » est plus informatif que « détecté sur une
     * culture voisine ».
     *
     * <p>La périphrase ne sert donc que lorsque la culture est réellement absente,
     * et évite une phrase trouée.
     */
    private String cropLabelOf(String cropName) {
        String canonical = Culture.canonical(cropName);
        return canonical == null ? "une culture voisine" : canonical.toLowerCase(FR);
    }

    // ============================================================
    // Accumulation par maladie
    // ============================================================

    /** Mutable et local à un appel : jamais partagé, donc sans risque de course. */
    private static final class Outbreak {

        private final String diseaseCode;
        private final String cropName;

        /**
         * Les parcelles distinctes, et non les lignes.
         *
         * <p>La requête rend un diagnostic par ligne : une parcelle voisine suivie
         * depuis deux semaines en compte des dizaines. Compter les lignes annonçait
         * « sur 24 parcelles voisines » là où il y en avait deux, sur une
         * exploitation qui n'en possède que quatre. Un chiffre invérifiable de
         * l'aveu même de celui qui le lit décrédibilise le conseil entier.
         */
        private final java.util.Set<Long> plots = new java.util.HashSet<>();
        private int rowCount;
        private double nearestKm = Double.MAX_VALUE;
        private String nearestPlotName;
        private double weight;

        Outbreak(String diseaseCode, String cropName) {
            this.diseaseCode = diseaseCode;
            this.cropName = cropName;
        }

        void record(Long plotId, String plotName, double distanceKm, Instant diagnosedAt,
                    double candidateWeight) {
            rowCount++;
            if (plotId != null) {
                plots.add(plotId);
            }
            if (distanceKm < nearestKm) {
                nearestKm = distanceKm;
                nearestPlotName = plotName;
            }
            // Le poids retenu est le plus élevé : c'est le foyer le plus menaçant
            // qui doit décider de la priorité, non la moyenne des foyers.
            weight = Math.max(weight, candidateWeight);
        }

        String diseaseCode() {
            return diseaseCode;
        }

        String cropName() {
            return cropName;
        }

        /** Repli sur le nombre de lignes si aucun identifiant n'a été rendu :
         *  mieux vaut un décompte imparfait qu'un « sur 0 parcelles ». */
        int plotCount() {
            return plots.isEmpty() ? rowCount : plots.size();
        }

        double nearestKm() {
            return nearestKm == Double.MAX_VALUE ? 0d : nearestKm;
        }

        String nearestPlotName() {
            return nearestPlotName;
        }

        double weight() {
            return weight;
        }
    }

    // ============================================================
    // Conversion des colonnes natives
    // ============================================================

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Le pilote peut rendre {@code Long}, {@code BigInteger} ou {@code Integer}
     *  pour un {@code bigint} : on prend la famille, pas le type supposé. */
    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private double asDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * Une requête native rend la forme temporelle que choisit le pilote, et pas
     * celle qu'on suppose. La conversion est centralisée dans
     * {@link com.sni.bilanga.utils.format.SqlTemporal} : ce code n'en couvrait qu'une
     * partie, dont pas {@code LocalDateTime}, la forme que PostgreSQL rend en
     * pratique. Ici l'effet aurait été une fraîcheur de diagnostic voisin toujours
     * nulle, donc une pondération faussée sans que rien ne le signale.
     */
    private Instant asInstant(Object value) {
        return com.sni.bilanga.utils.format.SqlTemporal.toInstant(value);
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
