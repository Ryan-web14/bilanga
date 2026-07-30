package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.MatchConfidence;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rapproche les opérations <strong>prévues</strong> des interventions
 * <strong>réellement menées</strong>.
 *
 * <h2>Pourquoi c'est une inférence, et pourquoi cela se dit</h2>
 *
 * <p>Rien, dans les données, n'établit qu'une fertilisation du 14 mai est celle qui
 * était prévue le 12. On le suppose parce que c'est la lecture la plus économique — et
 * c'est pourquoi chaque rapprochement porte sa {@link MatchConfidence} au lieu d'un
 * booléen : l'exploitant doit pouvoir distinguer « c'est certainement celle-là » de
 * « c'est probablement celle-là ».
 *
 * <h2>Calculé à la lecture, jamais persisté</h2>
 *
 * <p>Le résultat de cette classe n'est <strong>jamais écrit en base</strong>. Un mauvais
 * appariement qui se persiste se propage — au bilan économique, au taux de réalisation,
 * au clonage — et devra être défait à la main. Un mauvais appariement qui se recalcule
 * disparaît de lui-même dès que la donnée s'améliore : une date corrigée, une
 * intervention saisie après coup. Seule une confirmation humaine s'écrit
 * ({@code POST …/match}).
 *
 * <h2>Un pour un, au plus proche d'abord</h2>
 *
 * <p>Deux fertilisations prévues et deux réalisées doivent donner une paire chacune.
 * Un appariement naïf — pour chaque opération, l'intervention la plus proche —
 * rattacherait les deux opérations à la même intervention et laisserait l'autre
 * orpheline. L'algorithme est donc glouton sur l'écart croissant : la paire la plus
 * serrée est arrêtée d'abord, et ses deux membres sortent du jeu.
 *
 * <p>Ce n'est pas l'optimum global (Hongrois le serait), mais sur des jeux de quelques
 * lignes par type l'écart est nul en pratique, et le résultat reste
 * <strong>explicable</strong> — ce qui compte davantage ici, puisqu'il sera affiché.
 *
 * <p>Sans état ni transaction.
 */
@Component
public class ItineraryMatcher {

    /** Une opération prévue, réduite à ce que l'appariement regarde. */
    public record PlannedRef(Long id, String type, LocalDate plannedOn) {}

    /** Une intervention réelle, réduite de même. */
    public record ActualRef(Long id, String type, LocalDate performedOn) {}

    /**
     * Un rapprochement retenu.
     *
     * @param gapDays écart <strong>signé</strong> : négatif si l'intervention a eu lieu
     *                avant la date prévue, positif si après. Le signe est une
     *                information — « systématiquement en retard » ne se lit pas comme
     *                « systématiquement en avance »
     */
    public record Match(Long operationId, Long interventionId,
                        MatchConfidence confidence, long gapDays) {}

    /**
     * @param planned opérations prévues <strong>non encore confirmées</strong> ; celles
     *                qui portent déjà un rapprochement manuel doivent être écartées par
     *                l'appelant — une confirmation humaine ne se recalcule pas
     * @param actual  interventions de la campagne, hors celles déjà confirmées ailleurs
     */
    public List<Match> match(List<PlannedRef> planned, List<ActualRef> actual) {
        if (planned == null || actual == null || planned.isEmpty() || actual.isEmpty()) {
            return List.of();
        }

        List<Match> candidates = new ArrayList<>();

        for (PlannedRef operation : planned) {
            if (operation == null || operation.plannedOn() == null || operation.type() == null) {
                continue;
            }
            for (ActualRef intervention : actual) {
                if (intervention == null || intervention.performedOn() == null
                        || !operation.type().equalsIgnoreCase(intervention.type())) {
                    continue;
                }
                long gap = ChronoUnit.DAYS.between(operation.plannedOn(), intervention.performedOn());
                MatchConfidence confidence = MatchConfidence.forGap(gap);

                // Hors de toute tolérance : ce n'est pas un rapprochement douteux,
                // c'est une absence de rapprochement. Les confondre reviendrait à
                // relier une irrigation de mars à une opération prévue en août.
                if (confidence != null) {
                    candidates.add(new Match(operation.id(), intervention.id(), confidence, gap));
                }
            }
        }

        // Écart croissant d'abord ; les identifiants ensuite, pour que deux exécutions
        // sur les mêmes données donnent exactement le même résultat — un appariement
        // qui change d'un affichage à l'autre serait illisible.
        candidates.sort(Comparator
                .comparingLong((Match match) -> Math.abs(match.gapDays()))
                .thenComparing(Match::operationId)
                .thenComparing(Match::interventionId));

        Set<Long> usedOperations = new HashSet<>();
        Set<Long> usedInterventions = new HashSet<>();
        List<Match> retained = new ArrayList<>();

        for (Match candidate : candidates) {
            if (usedOperations.add(candidate.operationId())) {
                if (usedInterventions.add(candidate.interventionId())) {
                    retained.add(candidate);
                } else {
                    // L'intervention est prise : cette opération reste disponible
                    // pour une autre intervention plus loin dans la liste.
                    usedOperations.remove(candidate.operationId());
                }
            }
        }
        return retained;
    }
}
