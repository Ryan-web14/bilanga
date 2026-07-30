package com.sni.bilanga.farm.service.interfaces;


import com.sni.bilanga.farm.dto.request.CropClosureRequest;
import com.sni.bilanga.farm.dto.response.CropCalendar;
import com.sni.bilanga.farm.dto.response.CropClosureResponse;
import com.sni.bilanga.farm.dto.response.CropComparison;
import com.sni.bilanga.farm.dto.response.CropJournalEntry;
import com.sni.bilanga.farm.dto.response.PlotSuccession;
import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.farm.dto.request.CropRequest;
import com.sni.bilanga.farm.dto.response.CropResponse;
import com.sni.bilanga.farm.model.Crop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CropService {

    CropResponse create(CropRequest request);

    CropResponse update(Long id, CropRequest request);

    CropResponse findById(Long id);

    /**
     * Calendrier cultural : stades franchis ET a venir.
     *
     * <p>Aucun calcul nouveau — {@code GrowthStageResolver.stageTimeline} projette
     * deja les phases futures. Ses deux seuls consommateurs ne regardaient que le
     * passe, si bien que le systeme savait qu'une floraison etait attendue dans neuf
     * jours et ne le disait a personne. Or c'est l'information qui permet d'agir
     * AVANT : un traitement preventif se pose a l'entree en floraison, pas quand la
     * maladie est detectee.
     */
    CropCalendar calendarFor(Long id);

    /**
     * Clôture riche : date de fin réelle, motif, et bilan économique <strong>figé</strong>.
     *
     * <p>S'ajoute à {@link #delete(Long)}, qui reste inchangé — celui-ci se contente de
     * passer le statut à {@code TERMINEE}, sans dire ni quand ni pourquoi, ni ce que la
     * campagne a rapporté. Casser une route n'étant pas additif, les deux coexistent.
     *
     * <p>Le bilan est figé <strong>une seule fois</strong> et jamais rafraîchi. C'est ce
     * qui préserve le contrat « rien n'est stocké » de {@code MarginCalculator} :
     * {@link #closureOf(Long)} rend toujours l'arrêté <em>et</em> le recalculé, avec
     * leur écart expliqué.
     *
     * @throws com.sni.bilanga.exception.customs.BusinessRuleException si la campagne est
     *         déjà close, si le motif manque, ou si la date de fin est incohérente
     */
    CropClosureResponse close(Long id, CropClosureRequest request);

    /**
     * Le bilan arrêté à la clôture, le bilan recalculé aujourd'hui, et leur écart.
     *
     * <p>Les deux côte à côte, délibérément : le chiffre arrêté est la référence de la
     * campagne, et la divergence signale qu'une récolte ou une intervention a été
     * saisie, corrigée ou supprimée depuis. {@code harvestRepository.delete()} étant une
     * suppression réelle, ce cas n'est pas théorique.
     */
    CropClosureResponse closureOf(Long id);

    /** L'histoire agronomique d'une parcelle : les campagnes qui s'y sont succede. */
    PlotSuccession successionOf(Long plotId);

    /**
     * Cette campagne comparee a la precedente de la MEME culture.
     *
     * <p>Meme culture, parce qu'opposer une tomate a un manioc ne dit rien : ni les
     * rendements, ni les cycles, ni les charges ne sont commensurables.
     */
    CropComparison compareWithPrevious(Long id);

    /**
     * Journal d'un cycle, du plus récent au plus ancien.
     *
     * <p>Non paginé : quelques dizaines d'entrées au plus par campagne.
     */
    List<CropJournalEntry> journalOf(Long id);

    /**
     * Journal d'une parcelle, <strong>toutes campagnes confondues</strong>.
     *
     * <p>Paginé, celui-là : le volume croît sans borne avec les années.
     */
    org.springframework.data.domain.Page<CropJournalEntry> journalForPlot(
            Long plotId, org.springframework.data.domain.Pageable pageable);

    /**
     * Les seuils sur lesquels le moteur agronomique juge cette campagne, stade par stade,
     * avec l'<strong>origine</strong> de chaque valeur (générale ou propre au stade).
     *
     * <p>Pure restitution : {@code CropRequirementResolver} fusionne depuis toujours les
     * deux sources (V10), seule l'exposition manquait.
     */
    com.sni.bilanga.farm.dto.response.CropThresholds thresholdsOf(Long cropId);

    /**
     * Relance une campagne sur le modèle d'une précédente.
     *
     * <p><strong>Non copiés, délibérément</strong> : {@code seedLot} (un lot est
     * consommé — le reporter serait un mensonge de traçabilité), {@code growthStage}
     * (redérivé de la nouvelle plantation), les champs de clôture, et le journal.
     *
     * <p>L'itinéraire technique est reporté, décalé du delta de plantation — les
     * opérations en {@code J+n} le sont telles quelles.
     *
     * @throws com.sni.bilanga.exception.customs.BusinessRuleException si une campagne est
     *         déjà en cours sur la parcelle d'accueil
     */
    CropResponse cloneFrom(Long sourceCropId,
                           com.sni.bilanga.farm.dto.request.CropCloneRequest request);

    // ============================================================
    // Itinéraire technique (V29)
    // ============================================================

    /**
     * L'itinéraire d'une campagne : ce qui était prévu, et ce qui a suivi.
     *
     * <p><strong>Le troisième terme.</strong> Le système savait ce qui a été fait
     * ({@code interventions}) et ce qu'il conseille ({@code recommendations}), jamais ce
     * qui était <em>prévu</em>. Sans ce terme, une opération oubliée est indiscernable
     * d'une opération jamais planifiée, et le coût d'une campagne ne se connaît qu'après
     * la récolte.
     *
     * <p>Les rapprochements automatiques sont <strong>recalculés à chaque appel</strong>
     * et jamais persistés — seules les confirmations humaines s'écrivent.
     */
    com.sni.bilanga.farm.dto.response.CropItinerary itineraryOf(Long cropId);

    com.sni.bilanga.farm.dto.response.PlannedOperationResponse addOperation(
            Long cropId, com.sni.bilanga.farm.dto.request.PlannedOperationRequest request);

    com.sni.bilanga.farm.dto.response.PlannedOperationResponse updateOperation(
            Long cropId, Long operationId,
            com.sni.bilanga.farm.dto.request.PlannedOperationRequest request);

    void deleteOperation(Long cropId, Long operationId);

    /**
     * Confirme <strong>à la main</strong> qu'une intervention satisfait une opération
     * prévue.
     *
     * <p>Seul chemin par lequel un rapprochement s'écrit en base. Passer
     * {@code interventionId = null} défait la confirmation et rend l'opération à
     * l'inférence.
     *
     * @throws com.sni.bilanga.exception.customs.BusinessRuleException si l'intervention
     *         appartient à une autre campagne, ou satisfait déjà une autre opération
     */
    com.sni.bilanga.farm.dto.response.PlannedOperationResponse confirmMatch(
            Long cropId, Long operationId, Long interventionId);

    /** Recherche paginée à critères facultatifs ; remplace {@code findByPlot}. */
    Page<CropResponse> search(Long plotId, Culture cropName, CropStatus status,
                              GrowthStage stage, Pageable pageable);

    void delete(Long id);

    // Culture en cours sur la parcelle : base de la résolution automatique
    Optional<Crop> findActiveCrop(Long plotId);

    /**
     * Réaligne le stade de croissance sur la date de plantation, et le persiste
     * s'il avait dérivé.
     *
     * <p>Le stade est une colonne saisie que personne ne revient corriger : une
     * tomate plantée en mars reste « LEVEE » jusqu'à la récolte. Comme les
     * seuils agronomiques applicables en dépendent, le moteur raisonnait sur un
     * stade faux. Appelée là où le stade est consommé — à la résolution du
     * contexte de diagnostic — plutôt que par un ordonnanceur, que le projet n'a
     * pas.
     */
    Crop refreshGrowthStage(Crop crop);
}
