package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.audit.context.SecurityAuditContextProvider;
import com.sni.bilanga.audit.util.AuditDiffUtil;
import com.sni.bilanga.enums.CropJournalEvent;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.CropJournal;
import com.sni.bilanga.farm.repository.CropJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L'<strong>unique</strong> écrivain du journal de cycle.
 *
 * <p>Unique par conception : un journal auquel plusieurs endroits écriraient perdrait
 * sa cohérence de format, et c'est précisément le format qui permet de le relire. Le
 * dépôt n'expose donc aucune méthode d'écriture au-delà de celles héritées.
 *
 * <h2>Trois garanties</h2>
 *
 * <p><strong>1. Il ne peut jamais faire échouer l'opération qu'il décrit.</strong>
 * Chaque écriture est enveloppée : une entrée de journal perdue est regrettable, une
 * clôture de campagne refusée parce que sa journalisation a échoué serait absurde. Le
 * défaut est journalisé côté serveur, pas remonté à l'appelant.
 *
 * <p><strong>2. Il ne reçoit jamais l'entité.</strong> Le diff porte sur un
 * {@link CropSnapshot} plat — voir sa javadoc pour les trois raisons (proxys JPA
 * initialisés, bruit du verrou optimiste, identifiants Snowflake sortis en nombres).
 *
 * <p><strong>3. Il enregistre la version LUE AVANT la modification.</strong> Hibernate
 * incrémente le verrou optimiste au flush ; chercher la valeur d'après demanderait un
 * {@code flush()} explicite, et n'apporterait rien — ce qui intéresse un lecteur de
 * journal est l'état de départ.
 *
 * <p>Sans état, comme les autres classes de {@code service/support}. La transaction
 * est celle de l'appelant : l'entrée est écrite <em>avec</em> l'opération, ou pas du
 * tout — un journal qui survivrait à une transaction annulée décrirait un changement
 * qui n'a pas eu lieu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CropJournalWriter {

    private final CropJournalRepository journalRepository;
    private final SecurityAuditContextProvider actorProvider;

    /**
     * Consigne la création d'un cycle.
     *
     * <p>{@code changes} porte l'état initial complet plutôt qu'un diff vide : c'est
     * la seule entrée où « avant » n'existe pas, et un diff contre le néant ne dirait
     * rien. Un lecteur qui remonte le journal jusqu'à l'origine doit y trouver de quoi
     * reconstituer le point de départ.
     */
    public void recordCreation(Crop crop) {
        write(crop, CropJournalEvent.CREATION, initialStateOf(crop), null);
    }

    /**
     * Consigne une modification, en ne gardant que ce qui a réellement changé.
     *
     * <p>Ne écrit <strong>rien</strong> si le diff est vide : un {@code PUT} qui
     * renvoie exactement l'état courant n'est pas un événement, et le consigner
     * remplirait le journal de lignes sans information — exactement le bruit qui fait
     * qu'on cesse de lire un journal.
     *
     * @param before état capturé <em>avant</em> l'application de la requête
     */
    public void recordUpdate(Crop crop, CropSnapshot before, String reason) {
        Map<String, Object> changes = AuditDiffUtil.diff(before, CropSnapshot.of(crop));
        if (changes.isEmpty()) {
            return;
        }
        write(crop, CropJournalEvent.MODIFICATION, changes, reason);
    }

    /**
     * Consigne un réalignement automatique du stade.
     *
     * <p>Distinct d'une modification parce que <strong>personne ne l'a décidé</strong> :
     * c'est le temps qui passe. Les confondre ferait porter à un utilisateur un
     * changement qui n'est pas le sien, et noierait les vraies modifications sous les
     * recalculs.
     *
     * <p>Volume borné : {@code isStale} compare le stade calculé au stade stocké, et le
     * calcul est déterministe — la bascule a lieu au plus une fois par stade, soit
     * quatre ou cinq entrées par campagne.
     */
    public void recordStageRefresh(Crop crop, String previousStage) {
        if (previousStage != null && previousStage.equals(crop.getGrowthStage())) {
            return;
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("growthStage", changeOf(previousStage, crop.getGrowthStage()));

        write(crop, CropJournalEvent.STADE_RECALCULE, changes,
                "Stade réaligné sur la date de plantation et la durée du cycle.");
    }

    /** Consigne la clôture, avec son motif — celui-ci n'est jamais facultatif. */
    public void recordClosure(Crop crop, CropSnapshot before, String reason) {
        Map<String, Object> changes = AuditDiffUtil.diff(before, CropSnapshot.of(crop));
        write(crop, CropJournalEvent.CLOTURE, changes, reason);
    }

    /**
     * Consigne un clonage, en nommant la campagne d'origine.
     *
     * <p>{@code sourceCropId} est écrit en <strong>chaîne</strong>, comme tous les
     * identifiants Snowflake ailleurs dans l'API. L'écrire en nombre produirait un
     * entier à dix-neuf chiffres qu'un client JavaScript arrondirait silencieusement.
     */
    public void recordClone(Crop crop, Long sourceCropId) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("sourceCropId", sourceCropId == null ? null : String.valueOf(sourceCropId));
        changes.putAll(initialStateOf(crop));

        write(crop, CropJournalEvent.CLONAGE, changes,
                "Campagne créée par clonage de la campagne " + sourceCropId + ".");
    }

    // ============================================================
    // Interne
    // ============================================================

    /**
     * L'état initial, sous la même forme {@code {before, after}} que les diffs.
     *
     * <p>Uniformité voulue : un client qui sait afficher une entrée de modification
     * sait afficher une création, sans second gabarit. {@code before} vaut {@code null},
     * ce qui se lit exactement pour ce que c'est.
     */
    private Map<String, Object> initialStateOf(Crop crop) {
        Map<String, Object> initial = new LinkedHashMap<>();
        CropSnapshot snapshot = CropSnapshot.of(crop);
        if (snapshot == null) {
            return initial;
        }
        // Diff contre un instantané entièrement vide : produit une entrée par champ
        // renseigné, et aucune pour les champs laissés vides.
        return AuditDiffUtil.diff(empty(), snapshot);
    }

    private CropSnapshot empty() {
        return new CropSnapshot(null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private Map<String, Object> changeOf(Object before, Object after) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", before);
        change.put("after", after);
        return change;
    }

    private void write(Crop crop, CropJournalEvent event,
                       Map<String, Object> changes, String reason) {
        if (crop == null || crop.getId() == null) {
            return;
        }
        try {
            journalRepository.save(CropJournal.builder()
                    .cropId(crop.getId())
                    .plotId(crop.getPlot() == null ? null : crop.getPlot().getId())
                    // La version LUE, avant que le flush ne l'incrémente.
                    .cropVersion(crop.getVersion())
                    .eventType(event.name())
                    .changes(changes == null ? Map.of() : changes)
                    .reason(reason)
                    .changedBy(actorProvider.userIdOrNull())
                    .changedByEmail(actorProvider.emailOrSystem())
                    .build());

        } catch (Exception e) {
            // Une entrée de journal perdue est regrettable ; une clôture de campagne
            // refusée parce que sa journalisation a échoué serait absurde.
            log.warn("Journal du cycle {} non écrit ({}) : {}",
                    crop.getId(), event.name(), e.getMessage());
        }
    }
}
