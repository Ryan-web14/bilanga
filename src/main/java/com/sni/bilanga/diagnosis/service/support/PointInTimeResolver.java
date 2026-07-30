package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Résout, pour un instant donné, le relevé le plus proche et la conclusion qui
 * était en vigueur.
 *
 * <p>Séparé de l'assemblage de la réponse pour une raison précise : <strong>le choix
 * du relevé et l'alignement du diagnostic sont les deux seules décisions non
 * triviales</strong> de cette fonctionnalité, et ce sont donc les deux qu'il faut
 * pouvoir tester isolément. Le reste n'est que du remplissage de DTO.
 *
 * <p>Sans état ni transaction, comme les autres classes de {@code service/support}.
 */
@Component
@RequiredArgsConstructor
public class PointInTimeResolver {

    public static final String SELECTION_EXACT = "EXACT";
    public static final String SELECTION_BEFORE = "AVANT";
    public static final String SELECTION_AFTER = "APRES";
    public static final String SELECTION_NONE = "AUCUN";

    public static final String ALIGNMENT_ON_READING = "SUR_CE_RELEVE";
    public static final String ALIGNMENT_IN_FORCE = "EN_VIGUEUR";
    public static final String ALIGNMENT_NONE = "AUCUN";

    /**
     * Tolérance par défaut, en minutes.
     *
     * <p>Vingt-quatre heures : assez large pour qu'un instant choisi à la souris sur
     * une courbe journalière trouve toujours son relevé, assez étroite pour ne pas
     * rendre les mesures de la semaine dernière en prétendant décrire aujourd'hui.
     * L'appelant peut la resserrer ; il ne peut pas la supprimer, car un écart non
     * borné ferait passer une donnée sans rapport pour une lecture de l'instant.
     */
    public static final int DEFAULT_TOLERANCE_MINUTES = 24 * 60;

    private final SensorReadingRepository sensorReadingRepository;
    private final DiagnosticRepository diagnosticRepository;

    /**
     * Relevé retenu et manière dont il a été trouvé.
     *
     * @param reading   {@code null} si rien n'est exploitable dans la tolérance
     * @param selection {@code EXACT} | {@code AVANT} | {@code APRES} | {@code AUCUN}
     * @param offsetMinutes écart signé — négatif avant, positif après ; {@code null}
     *                      si aucun relevé
     */
    public record ReadingChoice(SensorReading reading, String selection, Integer offsetMinutes) {

        public static final ReadingChoice NONE = new ReadingChoice(null, SELECTION_NONE, null);

        public boolean isPresent() {
            return reading != null;
        }
    }

    /**
     * Conclusion retenue et son alignement sur le relevé.
     *
     * @param alignment {@code SUR_CE_RELEVE} | {@code EN_VIGUEUR} | {@code AUCUN}
     * @param ageMinutes ancienneté de la conclusion à l'instant demandé
     */
    public record DiagnosticChoice(Diagnostic diagnostic, String alignment, Integer ageMinutes) {

        public static final DiagnosticChoice NONE = new DiagnosticChoice(null, ALIGNMENT_NONE, null);

        public boolean isPresent() {
            return diagnostic != null;
        }
    }

    // ============================================================
    // Relevé
    // ============================================================

    /**
     * Le relevé le plus proche de {@code at}, dans la tolérance.
     *
     * <p><strong>Deux requêtes plutôt qu'un {@code order by abs(...)}.</strong> Une
     * expression sur la colonne de date serait non-sargable : PostgreSQL ne pourrait
     * pas utiliser {@code idx_readings_plot_date} et balaierait tout l'historique de
     * la parcelle — sur une série temporelle, c'est la différence entre lire une ligne
     * et en lire des dizaines de milliers, sur un chemin qu'un client peut appeler à
     * chaque déplacement de curseur.
     *
     * <p><strong>En cas d'égalité parfaite, le passé gagne.</strong> Ce n'est pas
     * arbitraire : on cherche l'état du sol <em>à</em> cet instant, et une mesure
     * postérieure décrit déjà autre chose. C'est surtout <em>déterministe</em> — deux
     * appels identiques rendent le même relevé, ce qu'une préférence implicite ne
     * garantirait pas.
     */
    public ReadingChoice resolveReading(Long plotId, Instant at, Integer toleranceMinutes) {
        if (plotId == null || at == null) {
            return ReadingChoice.NONE;
        }

        long tolerance = toleranceMinutes == null || toleranceMinutes <= 0
                ? DEFAULT_TOLERANCE_MINUTES
                : toleranceMinutes;

        Optional<SensorReading> before = sensorReadingRepository
                .findFirstByPlot_IdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(plotId, at);
        Optional<SensorReading> after = sensorReadingRepository
                .findFirstByPlot_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(plotId, at);

        SensorReading candidate = closest(before.orElse(null), after.orElse(null), at);
        if (candidate == null) {
            return ReadingChoice.NONE;
        }

        long gapMinutes = Duration.between(candidate.getRecordedAt(), at).toMinutes();
        if (Math.abs(gapMinutes) > tolerance) {
            // Un relevé existe, mais trop loin pour décrire l'instant demandé. Le
            // rendre quand même laisserait croire à une lecture directe.
            return ReadingChoice.NONE;
        }

        String selection;
        if (gapMinutes == 0) {
            selection = SELECTION_EXACT;
        } else if (gapMinutes > 0) {
            selection = SELECTION_BEFORE;   // le relevé précède l'instant demandé
        } else {
            selection = SELECTION_AFTER;
        }

        // Signe inversé par rapport à gapMinutes : le contrat exposé est « écart du
        // relevé PAR RAPPORT À l'instant demandé », donc négatif quand il précède.
        return new ReadingChoice(candidate, selection, (int) -gapMinutes);
    }

    /**
     * Départage les deux candidats. {@code null} si les deux manquent.
     *
     * <p>Sur écart identique, retient celui du passé — voir le contrat de
     * {@link #resolveReading}.
     */
    private SensorReading closest(SensorReading before, SensorReading after, Instant at) {
        if (before == null) {
            return after;
        }
        if (after == null) {
            return before;
        }
        long beforeGap = Math.abs(Duration.between(before.getRecordedAt(), at).toMillis());
        long afterGap = Math.abs(Duration.between(after.getRecordedAt(), at).toMillis());

        return afterGap < beforeGap ? after : before;
    }

    // ============================================================
    // Diagnostic
    // ============================================================

    /**
     * La conclusion d'époque, et d'où elle vient.
     *
     * <p><strong>L'ordre des deux tentatives porte tout le sens.</strong> On cherche
     * d'abord le diagnostic <em>issu de ce relevé</em> : c'est la réponse la plus
     * forte, celle qui rattache une conclusion à la mesure qui l'a produite. Elle
     * échoue le plus souvent, et il faut alors se rabattre sur le dernier diagnostic
     * antérieur — celui que l'exploitant avait effectivement sous les yeux.
     *
     * <p>Inverser l'ordre attribuerait à un relevé une conclusion qu'il n'a pas
     * produite, alors même qu'une conclusion issue de lui existait. L'appelant expose
     * l'alignement retenu, précisément pour que la nuance ne se perde pas.
     */
    public DiagnosticChoice resolveDiagnostic(Long plotId, Instant at, SensorReading reading) {
        if (plotId == null || at == null) {
            return DiagnosticChoice.NONE;
        }

        if (reading != null && reading.getId() != null) {
            Optional<Diagnostic> onReading = diagnosticRepository.findFirstByReading_Id(reading.getId());
            if (onReading.isPresent()) {
                return new DiagnosticChoice(onReading.get(), ALIGNMENT_ON_READING, 0);
            }
        }

        return diagnosticRepository
                .findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(plotId, at)
                .map(diagnostic -> new DiagnosticChoice(diagnostic, ALIGNMENT_IN_FORCE,
                        ageMinutes(diagnostic, at)))
                .orElse(DiagnosticChoice.NONE);
    }

    private Integer ageMinutes(Diagnostic diagnostic, Instant at) {
        if (diagnostic.getDiagnosedAt() == null) {
            return null;
        }
        return (int) Duration.between(diagnostic.getDiagnosedAt(), at).toMinutes();
    }
}
