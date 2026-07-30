package com.sni.bilanga.farm.service.implementation;


import com.sni.bilanga.audit.context.SecurityAuditContextProvider;
import com.sni.bilanga.enums.CropClosureReason;
import com.sni.bilanga.enums.CropJournalEvent;
import com.sni.bilanga.farm.dto.request.CropClosureRequest;
import com.sni.bilanga.farm.dto.response.CropCalendar;
import com.sni.bilanga.farm.dto.response.CropClosureResponse;
import com.sni.bilanga.farm.dto.response.CropComparison;
import com.sni.bilanga.farm.dto.response.CropJournalEntry;
import com.sni.bilanga.farm.dto.response.PlotSuccession;
import com.sni.bilanga.enums.MatchConfidence;
import com.sni.bilanga.enums.PlannedOperationStatus;
import com.sni.bilanga.farm.dto.request.CropCloneRequest;
import com.sni.bilanga.farm.dto.request.PlannedOperationRequest;
import com.sni.bilanga.farm.dto.response.CropItinerary;
import com.sni.bilanga.farm.dto.response.CropThresholds;
import com.sni.bilanga.farm.dto.response.PlannedOperationResponse;
import com.sni.bilanga.farm.service.support.ThresholdsAssembler;
import com.sni.bilanga.farm.model.CropPlannedOperation;
import com.sni.bilanga.farm.repository.CropPlannedOperationRepository;
import com.sni.bilanga.farm.service.support.ItineraryAssembler;
import com.sni.bilanga.intervention.model.Intervention;
import com.sni.bilanga.intervention.repository.InterventionRepository;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.service.support.SuccessionAnalyzer;
import com.sni.bilanga.farm.model.CropJournal;
import com.sni.bilanga.farm.repository.CropJournalRepository;
import com.sni.bilanga.farm.service.support.CropClosureValidator;
import com.sni.bilanga.farm.service.support.CropJournalWriter;
import com.sni.bilanga.farm.service.support.CropSnapshot;
import com.sni.bilanga.farm.service.support.CropUpdateMerger;
import com.sni.bilanga.farm.service.support.EconomicsFreezer;
import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import com.sni.bilanga.harvest.service.support.MarginCalculator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.dto.request.CropRequest;
import com.sni.bilanga.farm.dto.response.CropResponse;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.repository.CropRepository;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.farm.service.support.CropMapper;
import com.sni.bilanga.farm.service.support.GrowthStageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CropServiceImpl implements CropService {

    public static final String STATUS_EN_COURS = CropStatus.EN_COURS.name();

    private final CropRepository cropRepository;
    private final PlotService plotService;
    private final CropMapper cropMapper;
    private final GrowthStageResolver growthStageResolver;

    // ── Clôture riche et journal (V28) ──────────────────────────
    private final CropJournalRepository journalRepository;
    private final CropJournalWriter journalWriter;
    private final CropClosureValidator closureValidator;
    private final CropUpdateMerger updateMerger;
    private final SuccessionAnalyzer successionAnalyzer;
    private final EconomicsFreezer economicsFreezer;

    // ── Itinéraire technique (V29) ──────────────────────────────
    private final CropPlannedOperationRepository plannedOperationRepository;
    private final ItineraryAssembler itineraryAssembler;
    private final InterventionRepository interventionRepository;
    private final ThresholdsAssembler thresholdsAssembler;

    /**
     * Injecté depuis {@code harvest} vers {@code farm}, et non l'inverse : le cycle
     * connaît son bilan, le calculateur n'a pas à connaître les cycles. C'est aussi
     * ce qui évite un cycle de dépendances, {@code HarvestServiceImpl} dépendant déjà
     * de {@code CropService}.
     */
    private final MarginCalculator marginCalculator;

    private final SecurityAuditContextProvider actorProvider;

    @Override
    @Transactional
    public CropResponse create(CropRequest request) {
        CropStatus status = request.getStatus() == null ? CropStatus.EN_COURS : request.getStatus();
        requireSingleActiveCrop(request.getPlotId(), status, null);

        Crop crop = Crop.builder()
                .plot(plotService.require(request.getPlotId()))
                .cropName(normalize(request.getCropName()))
                .variety(request.getVariety())
                .seedLot(request.getSeedLot())
                .plantingDate(request.getPlantingDate())
                .cycleDurationDays(request.getCycleDurationDays())
                .expectedHarvestDate(request.getExpectedHarvestDate())
                .plantedArea(request.getPlantedArea())
                .plantDensity(request.getPlantDensity())
                .growthStage(DomainEnums.nameOf(request.getGrowthStage()))
                .status(status.name())
                .build();

        Crop saved = cropRepository.save(deriveCycle(crop));
        journalWriter.recordCreation(saved);
        return cropMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CropResponse update(Long id, CropRequest request) {
        Crop crop = require(id);

        CropSnapshot before = CropSnapshot.of(crop);

        if (request.getPlotId() != null) {
            crop.setPlot(plotService.require(request.getPlotId()));
        }
        if (request.getStatus() != null) {
            requireSingleActiveCrop(crop.getPlot().getId(), request.getStatus(), id);
            crop.setStatus(request.getStatus().name());
        }

        // Mise à jour PARTIELLE : un champ absent ou null n'est plus touché.
        //
        // Ce service écrasait auparavant tous les champs inconditionnellement. Un
        // client qui n'envoyait que la variété effaçait donc la surface plantée, la
        // densité, le lot de semence et la date de plantation — silencieusement.
        //
        // La conséquence n'était pas cosmétique : plantedArea conditionne
        // yieldPerHectare et marginPerHectare, les deux seuls chiffres comparables
        // entre parcelles. Le bilan économique affichait alors null, à des semaines
        // de la cause.
        //
        // Effacer reste possible, mais se demande désormais explicitement par
        // clearFields — sans quoi on aurait troqué une perte silencieuse contre une
        // donnée indélébile.
        updateMerger.apply(crop, request);

        Crop saved = cropRepository.save(deriveCycle(crop));
        journalWriter.recordUpdate(saved, before, null);
        return cropMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CropResponse findById(Long id) {
        return cropMapper.toResponse(require(id));
    }

    /**
     * Calendrier cultural : les stades franchis et ceux <strong>à venir</strong>.
     *
     * <p><strong>Aucun calcul nouveau.</strong> {@code stageTimeline} reconstitue
     * depuis toujours la date de début de chaque phase, futures comprises — c'est
     * une fonction déterministe de la plantation et de la durée du cycle. Mais ses
     * deux seuls consommateurs ne regardaient que le passé : la chronologie de la
     * parcelle, qui écarte explicitement les stades à venir pour ne pas mêler le
     * constaté au prévu, et le recalcul du stade courant.
     *
     * <p>Le système <em>savait</em> donc qu'une floraison était attendue dans neuf
     * jours et ne le disait à personne. Or c'est l'information qui permet d'agir
     * <strong>avant</strong> : un traitement préventif se pose à l'entrée en
     * floraison, pas quand la maladie est détectée. Tout le reste du système est
     * réactif par construction — il constate une mesure, un symptôme, un écart.
     * Cette vue est la seule qui annonce.
     */
    @Override
    @Transactional(readOnly = true)
    public CropCalendar calendarFor(Long id) {
        Crop crop = require(id);
        LocalDate today = LocalDate.now();

        List<GrowthStageResolver.StageStart> starts = growthStageResolver.stageTimeline(crop);
        GrowthStage current = growthStageResolver.stageFor(crop);

        List<CropCalendar.StageWindow> windows = new ArrayList<>();
        CropCalendar.StageWindow next = null;

        for (int index = 0; index < starts.size(); index++) {
            GrowthStageResolver.StageStart start = starts.get(index);

            // La phase s'achève la veille du début de la suivante. La dernière n'a
            // pas de fin : elle court jusqu'à la récolte, dont la date est un
            // objectif et non une borne du cycle.
            LocalDate endsOn = index + 1 < starts.size()
                    ? starts.get(index + 1).startsOn().minusDays(1)
                    : null;

            boolean isCurrent = current != null && current == start.stage();
            boolean isPast = endsOn != null && endsOn.isBefore(today);

            CropCalendar.StageWindow window = CropCalendar.StageWindow.builder()
                    .stage(start.stage().name())
                    .label(start.stage().getLabel())
                    .startsOn(start.startsOn())
                    .endsOn(endsOn)
                    .past(isPast)
                    .current(isCurrent)
                    // Négatif quand la phase a commencé : l'appelant peut écrire
                    // « commencée depuis 4 jours » sans refaire le calcul.
                    .daysUntil((int) ChronoUnit.DAYS.between(today, start.startsOn()))
                    .build();

            windows.add(window);

            if (next == null && start.startsOn().isAfter(today)) {
                next = window;
            }
        }

        Integer daysSince = crop.getPlantingDate() == null
                ? null
                : (int) ChronoUnit.DAYS.between(crop.getPlantingDate(), today);

        int cycle = growthStageResolver.cycleDays(crop.getCropName(), crop.getCycleDurationDays());

        return CropCalendar.builder()
                .cropId(crop.getId())
                .plotId(crop.getPlot() == null ? null : crop.getPlot().getId())
                .plotName(crop.getPlot() == null ? null : crop.getPlot().getName())
                .cropName(Culture.canonical(crop.getCropName()))
                .variety(crop.getVariety())
                .plantingDate(crop.getPlantingDate())
                .cycleDurationDays(cycle)
                .expectedHarvestDate(growthStageResolver.expectedHarvestDate(crop))
                .currentStage(current == null ? crop.getGrowthStage() : current.name())
                .currentStageLabel(current == null ? null : current.getLabel())
                .daysSincePlanting(daysSince)
                .daysToHarvest(growthStageResolver.daysToHarvest(crop))
                .cycleProgress(daysSince == null ? null : round((double) daysSince / cycle))
                .stages(windows)
                // null quand le cycle est achevé : il n'y a plus rien à annoncer, et
                // fabriquer un stade suivant laisserait croire que la campagne continue.
                .nextStage(next)
                .limitation(CALENDAR_LIMITATION)
                .build();
    }

    /**
     * Réserve constante, toujours renvoyée.
     *
     * <p>Ces dates sont des projections issues de proportions de cycle
     * <em>indicatives</em>. Une levée lente, une sécheresse ou une variété mal
     * identifiée les décalent toutes. Affichées sans réserve, elles seraient lues
     * comme un calendrier agronomique établi — et un exploitant qui prépare un
     * traitement pour une date fausse perd le produit et la fenêtre.
     */
    private static final String CALENDAR_LIMITATION =
            "Ces dates sont calculées à partir de la date de plantation et de la durée de "
            + "cycle, selon des proportions indicatives non encore validées par une source "
            + "agronomique. Ce sont des prévisions, pas un calendrier établi : une levée "
            + "lente, un déficit d'eau ou une variété différente les décalent. Confrontez-les "
            + "à ce que vous observez au champ.";

    private Double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CropResponse> search(Long plotId, Culture cropName, CropStatus status,
                                     GrowthStage stage, Pageable pageable) {
        return cropRepository
                .search(plotId,
                        cropName == null ? null : cropName.name().toLowerCase(Locale.ROOT),
                        DomainEnums.nameOf(status),
                        DomainEnums.nameOf(stage),
                        pageable)
                .map(cropMapper::toResponse);
    }

    /**
     * Archivage logique <strong>inchangé</strong>.
     *
     * <p>Volontairement laissé tel quel malgré l'arrivée de {@link #close}. Casser une
     * route n'est pas additif : un client existant qui archive une campagne doit
     * continuer de fonctionner. La clôture riche s'ajoute à côté, elle ne remplace pas.
     *
     * <p>Conséquence assumée : une campagne fermée par ici n'a ni date de fin réelle, ni
     * motif, ni bilan figé. Ces {@code null} <em>sont</em> l'information — « close avant
     * que le système ne demande un motif » — et c'est pourquoi la V28 ne pose aucune
     * contrainte les rendant obligatoires.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Crop crop = require(id);
        crop.setStatus(CropStatus.TERMINEE.name());
        cropRepository.save(crop);
    }

    /**
     * Clôture riche : date de fin réelle, motif, et bilan économique <strong>figé</strong>.
     *
     * <p><strong>Le manque comblé.</strong> {@link #delete} se contentait de passer le
     * statut à {@code TERMINEE}. On ne savait ni quand la campagne s'était réellement
     * achevée, ni pourquoi, ni ce qu'elle avait rapporté au moment où on l'avait close.
     *
     * <p><strong>Le bilan est figé une seule fois, et jamais rafraîchi.</strong> C'est
     * ce qui préserve le contrat « rien n'est stocké » de {@code MarginCalculator} :
     * l'instantané est daté, attribué, et {@link #closureOf} le rend toujours
     * accompagné du bilan recalculé et de leur écart expliqué. L'écart devient le signal
     * d'audit, au lieu d'être une ambiguïté sur « lequel croire ».
     *
     * <p>Les bornes du bilan sont naturelles ici — de la plantation à la fin réelle — là
     * où la route économique générique applique des bornes par défaut.
     */
    @Override
    @Transactional
    public CropClosureResponse close(Long id, CropClosureRequest request) {
        Crop crop = require(id);

        closureValidator.validate(crop, request.getActualEndDate(), request.getReason());

        CropSnapshot before = CropSnapshot.of(crop);
        LocalDate endDate = closureValidator.effectiveEndDate(request.getActualEndDate());

        crop.setStatus(CropStatus.TERMINEE.name());
        crop.setActualEndDate(endDate);
        crop.setClosureReason(request.getReason().name());
        crop.setClosureNote(request.getNote());
        crop.setClosedAt(Instant.now());
        crop.setClosedBy(currentUserOrNull());

        // Figé AVANT le save : le bilan porte sur la campagne telle qu'elle s'achève,
        // et MarginCalculator est pur — il ne dépend pas de l'état persisté du cycle.
        PlotEconomics economics = marginCalculator.compute(
                crop.getPlot(), crop, startOf(crop), endDate);
        crop.setEconomicsSnapshot(economicsFreezer.freeze(economics));

        Crop saved = cropRepository.save(crop);
        journalWriter.recordClosure(saved, before, closureReasonOf(request));

        return closureOf(saved);
    }

    /**
     * Le bilan arrêté, le bilan vivant, et leur écart.
     *
     * <p>Les deux côté à côte, et c'est le point : le chiffre arrêté est la référence de
     * la campagne, et la divergence dit que quelque chose a été saisi, corrigé ou
     * supprimé depuis. {@code harvestRepository.delete()} étant une suppression réelle,
     * ce cas n'est pas théorique.
     */
    @Override
    @Transactional(readOnly = true)
    public CropClosureResponse closureOf(Long id) {
        return closureOf(require(id));
    }

    private CropClosureResponse closureOf(Crop crop) {
        LocalDate endDate = crop.getActualEndDate() != null
                ? crop.getActualEndDate()
                : LocalDate.now();

        PlotEconomics current = marginCalculator.compute(
                crop.getPlot(), crop, startOf(crop), endDate);

        EconomicsFreezer.Divergence divergence =
                economicsFreezer.divergence(crop.getEconomicsSnapshot(), current);

        CropClosureReason reason = CropClosureReason.from(crop.getClosureReason());

        return CropClosureResponse.builder()
                .cropId(crop.getId())
                .plotId(crop.getPlot() == null ? null : crop.getPlot().getId())
                .plotName(crop.getPlot() == null ? null : crop.getPlot().getName())
                .cropName(Culture.canonical(crop.getCropName()))
                .variety(crop.getVariety())
                .plantingDate(crop.getPlantingDate())
                .actualEndDate(crop.getActualEndDate())
                .expectedHarvestDate(crop.getExpectedHarvestDate())
                .daysVersusExpected(daysVersusExpected(crop))
                .closureReason(crop.getClosureReason())
                .closureReasonLabel(reason == null ? null : reason.getLabel())
                .harvested(reason == null ? null : reason.isHarvested())
                .closureNote(crop.getClosureNote())
                .closedAt(crop.getClosedAt())
                .closedByEmail(crop.getClosedBy() == null ? null : crop.getClosedBy().getEmail())
                .frozenEconomics(crop.getEconomicsSnapshot())
                .currentEconomics(current)
                .diverged(divergence.diverged())
                .divergenceChanges(divergence.changes())
                .divergenceStatement(divergence.statement())
                .limitation(CLOSURE_LIMITATION)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * L'histoire agronomique d'une parcelle : les campagnes qui s'y sont succédé.
     *
     * <p><strong>Aucune migration.</strong> {@code idx_crops_plot_status_date} existe
     * depuis la V5, et tout — intervalles de sol nu, monoculture, précédent cultural —
     * se dérive de l'ordre des dates de plantation. C'est aussi pourquoi aucune colonne
     * {@code previous_crop_id} n'est stockée : un pointeur se périme dès qu'on corrige
     * une date, le tri non.
     */
    @Override
    @Transactional(readOnly = true)
    public PlotSuccession successionOf(Long plotId) {
        Plot plot = plotService.require(plotId);
        return successionAnalyzer.analyze(plot,
                cropRepository.findByPlot_IdOrderByPlantingDateDesc(plotId));
    }

    /**
     * Cette campagne comparée à la précédente de la <strong>même culture</strong>.
     *
     * <p>Même culture, parce qu'opposer une tomate à un manioc ne dit rien : ni les
     * rendements, ni les cycles, ni les charges ne sont commensurables.
     *
     * <p>La comparaison porte sur les bilans <strong>figés</strong>, non recalculés :
     * c'est ce qui la rend stable. Recalculer les deux côtés à chaque appel les ferait
     * bouger dès qu'une récolte est saisie, et deux consultations successives donneraient
     * des écarts différents.
     */
    @Override
    @Transactional(readOnly = true)
    public CropComparison compareWithPrevious(Long id) {
        Crop current = require(id);
        Plot plot = current.getPlot();

        Crop previous = current.getPlantingDate() == null ? null : cropRepository
                .findFirstByPlot_IdAndCropNameIgnoreCaseAndPlantingDateLessThanOrderByPlantingDateDesc(
                        plot.getId(), current.getCropName(), current.getPlantingDate())
                .orElse(null);

        return successionAnalyzer.compare(plot, current, previous);
    }

    /** Journal d'un cycle, du plus récent au plus ancien. */
    @Override
    @Transactional(readOnly = true)
    public List<CropJournalEntry> journalOf(Long id) {
        require(id);   // fait jouer le contrôle d'accès par la parcelle
        return journalRepository.findByCropIdOrderByChangedAtDesc(id).stream()
                .map(this::toJournalEntry)
                .toList();
    }

    /** Journal d'une parcelle, toutes campagnes confondues. */
    @Override
    @Transactional(readOnly = true)
    public Page<CropJournalEntry> journalForPlot(Long plotId, Pageable pageable) {
        plotService.require(plotId);
        return journalRepository.findByPlotIdOrderByChangedAtDesc(plotId, pageable)
                .map(this::toJournalEntry);
    }

    private CropJournalEntry toJournalEntry(CropJournal entry) {
        CropJournalEvent event = CropJournalEvent.from(entry.getEventType());

        return CropJournalEntry.builder()
                .id(entry.getId())
                .cropId(entry.getCropId())
                .plotId(entry.getPlotId())
                .eventType(entry.getEventType())
                .eventLabel(event == null ? null : event.getLabel())
                .humanAction(event == null ? null : event.isHumanAction())
                .changes(entry.getChanges())
                .changeCount(entry.getChanges() == null ? 0 : entry.getChanges().size())
                .reason(entry.getReason())
                .cropVersion(entry.getCropVersion())
                .changedBy(entry.getChangedBy())
                .changedByEmail(entry.getChangedByEmail())
                .changedAt(entry.getChangedAt())
                .build();
    }

    // ============================================================
    // Clôture — interne
    // ============================================================

    private static final String CLOSURE_LIMITATION =
            "Le bilan arrêté est un état des comptes à la date de clôture, non une "
            + "vérité définitive sur ce que la campagne a valu : des saisies ou des "
            + "corrections postérieures le font diverger, et c'est précisément ce que "
            + "l'écart affiché signale. Le rapprochement entre conseils suivis et "
            + "rendement reste par ailleurs descriptif, jamais causal.";

    /** Début de la fenêtre du bilan : la plantation, à défaut la création du cycle. */
    private LocalDate startOf(Crop crop) {
        if (crop.getPlantingDate() != null) {
            return crop.getPlantingDate();
        }
        return crop.getCreatedAt() == null
                ? LocalDate.now().minusYears(1)
                : LocalDate.ofInstant(crop.getCreatedAt(), java.time.ZoneOffset.UTC);
    }

    /**
     * Écart entre la fin réelle et la fin prévue, en jours. Négatif si la campagne
     * s'est achevée en avance — le cas d'une récolte anticipée.
     */
    private Integer daysVersusExpected(Crop crop) {
        if (crop.getActualEndDate() == null || crop.getExpectedHarvestDate() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(crop.getExpectedHarvestDate(), crop.getActualEndDate());
    }

    private String closureReasonOf(CropClosureRequest request) {
        String label = request.getReason().getLabel();
        return request.getNote() == null || request.getNote().isBlank()
                ? label
                : label + " — " + request.getNote().trim();
    }

    private com.sni.bilanga.security.admin.user.model.Users currentUserOrNull() {
        try {
            return actorProvider.principal() == null ? null : actorProvider.principal().getUser();
        } catch (RuntimeException noPrincipal) {
            // Une clôture depuis un contexte non authentifié — script, tâche — ne doit
            // pas échouer faute d'auteur : le journal portera l'acteur SYSTEM.
            return null;
        }
    }

    /** Dernière culture en cours sur la parcelle : sert à déduire la culture d'un diagnostic. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Crop> findActiveCrop(Long plotId) {
        return cropRepository.findFirstByPlot_IdAndStatusOrderByPlantingDateDesc(plotId, STATUS_EN_COURS);
    }

    /**
     * Une seule plantation en cours par parcelle.
     *
     * {@code findActiveCrop} retient la plus récente sans le dire : avec deux
     * cultures en cours, le diagnostic choisissait silencieusement l'une des
     * deux, et l'exploitant recevait des conseils portant sur l'autre.
     */
    private void requireSingleActiveCrop(Long plotId, CropStatus status, Long excludedId) {
        if (plotId == null || status != CropStatus.EN_COURS) {
            return;
        }
        boolean conflict = excludedId == null
                ? cropRepository.existsByPlot_IdAndStatus(plotId, STATUS_EN_COURS)
                : cropRepository.existsByPlot_IdAndStatusAndIdNot(plotId, STATUS_EN_COURS, excludedId);

        if (conflict) {
            throw new BusinessRuleException(
                    "Une culture est déjà en cours sur cette parcelle. Terminez-la avant d'en déclarer une nouvelle.");
        }
    }

    /**
     * Complète ce que le système sait déduire seul.
     *
     * <p>La durée du cycle et la date de récolte se calculent depuis la culture
     * et la date de plantation ; le stade initial aussi. Les faire saisir
     * reviendrait à demander à l'exploitant ce que la machine sait déjà — et
     * chaque champ saisi est un champ qui peut être faux.
     */
    private Crop deriveCycle(Crop crop) {
        if (crop.getCycleDurationDays() == null) {
            crop.setCycleDurationDays(
                    growthStageResolver.cycleDays(crop.getCropName(), null));
        }
        if (crop.getExpectedHarvestDate() == null) {
            crop.setExpectedHarvestDate(growthStageResolver.expectedHarvestDate(crop));
        }
        if (crop.getGrowthStage() == null) {
            crop.setGrowthStage(DomainEnums.nameOf(growthStageResolver.stageFor(crop)));
        }
        return crop;
    }

    /**
     * Aligne le stade enregistré sur le stade calculé, et le persiste.
     *
     * <p>Appelée par {@code ContextResolver} au moment où le diagnostic va s'en
     * servir. Un stade recalculé que personne ne lit n'aurait aucune valeur ;
     * ce qui compte est qu'il soit juste quand les moteurs agronomiques
     * l'utilisent pour choisir les seuils applicables.
     *
     * @return la culture, stade à jour
     */
    @Override
    @Transactional
    public Crop refreshGrowthStage(Crop crop) {
        if (crop == null || !growthStageResolver.isStale(crop)) {
            return crop;
        }

        String previousStage = crop.getGrowthStage();

        GrowthStage computed = growthStageResolver.stageFor(crop);
        crop.setGrowthStage(DomainEnums.nameOf(computed));

        if (crop.getExpectedHarvestDate() == null) {
            crop.setExpectedHarvestDate(growthStageResolver.expectedHarvestDate(crop));
        }

        Crop saved = cropRepository.save(crop);

        // Consigné comme STADE_RECALCULE, jamais comme MODIFICATION : personne ne l'a
        // décidé, c'est le temps qui passe. Les confondre ferait porter à un
        // utilisateur un changement qui n'est pas le sien.
        //
        // Volume borné : isStale compare le stade calculé au stade stocké, et le calcul
        // est déterministe — la bascule a lieu au plus une fois par stade, soit quatre
        // ou cinq entrées par campagne, non une par diagnostic.
        journalWriter.recordStageRefresh(saved, previousStage);

        return saved;
    }

    // ============================================================
    // Itinéraire technique (V29)
    // ============================================================

    /**
     * L'itinéraire d'une campagne, rapprochements compris.
     *
     * <p><strong>Les rapprochements automatiques ne sont jamais écrits.</strong> Ils sont
     * recalculés ici, à chaque lecture. Un mauvais appariement persisté se propagerait au
     * coût constaté, au taux de réalisation et au clonage, et devrait être défait à la
     * main ; recalculé, il disparaît de lui-même dès qu'une date est corrigée ou qu'une
     * intervention est saisie après coup.
     */
    @Override
    @Transactional(readOnly = true)
    public CropItinerary itineraryOf(Long cropId) {
        return assembleItinerary(requireWithAccess(cropId));
    }

    @Override
    @Transactional
    public PlannedOperationResponse addOperation(Long cropId, PlannedOperationRequest request) {
        Crop crop = requireWithAccess(cropId);
        requireDatable(request);

        CropPlannedOperation operation = CropPlannedOperation.builder()
                .crop(crop)
                .type(request.getType().name())
                .label(request.getLabel())
                .plannedOn(request.getPlannedOn())
                .daysAfterPlanting(request.getDaysAfterPlanting())
                .growthStage(request.getGrowthStage())
                .product(request.getProduct())
                .dose(request.getDose())
                .unit(request.getUnit())
                .estimatedCost(request.getEstimatedCost())
                .status(request.getStatus() == null
                        ? PlannedOperationStatus.PREVUE.name()
                        : request.getStatus().name())
                .note(request.getNote())
                .build();

        return lineOf(crop, plannedOperationRepository.save(operation).getId());
    }

    /**
     * Mise à jour d'une opération prévue.
     *
     * <p>Remplacement complet, et non fusion partielle — à l'inverse de
     * {@link #update(Long, CropRequest)}. La raison est le volume : une ligne
     * d'itinéraire porte huit champs facultatifs qu'un formulaire rend d'un bloc, et une
     * sémantique partielle y rendrait l'effacement d'une dose impossible sans un
     * mécanisme de {@code clearFields} disproportionné pour l'enjeu.
     */
    @Override
    @Transactional
    public PlannedOperationResponse updateOperation(Long cropId, Long operationId,
                                                    PlannedOperationRequest request) {
        Crop crop = requireWithAccess(cropId);
        requireDatable(request);

        CropPlannedOperation operation = requireOperation(cropId, operationId);
        operation.setType(request.getType().name());
        operation.setLabel(request.getLabel());
        operation.setPlannedOn(request.getPlannedOn());
        operation.setDaysAfterPlanting(request.getDaysAfterPlanting());
        operation.setGrowthStage(request.getGrowthStage());
        operation.setProduct(request.getProduct());
        operation.setDose(request.getDose());
        operation.setUnit(request.getUnit());
        operation.setEstimatedCost(request.getEstimatedCost());
        operation.setNote(request.getNote());

        if (request.getStatus() != null) {
            operation.setStatus(request.getStatus().name());
        }

        return lineOf(crop, plannedOperationRepository.save(operation).getId());
    }

    /**
     * Suppression <strong>réelle</strong>, comme pour les interventions et les récoltes.
     *
     * <p>Une opération planifiée par erreur fausse le coût prévisionnel et le taux de
     * réalisation — les deux chiffres qui sont la raison d'être de l'itinéraire.
     * L'archiver reviendrait à conserver une erreur dans un calcul.
     */
    @Override
    @Transactional
    public void deleteOperation(Long cropId, Long operationId) {
        requireWithAccess(cropId);
        plannedOperationRepository.delete(requireOperation(cropId, operationId));
    }

    /**
     * Confirme à la main qu'une intervention satisfait une opération prévue.
     *
     * <p>Seul chemin par lequel un rapprochement s'écrit. {@code interventionId} nul
     * défait la confirmation et rend l'opération à l'inférence — sans quoi une erreur de
     * saisie serait définitive.
     */
    @Override
    @Transactional
    public PlannedOperationResponse confirmMatch(Long cropId, Long operationId,
                                                 Long interventionId) {
        Crop crop = requireWithAccess(cropId);
        CropPlannedOperation operation = requireOperation(cropId, operationId);

        if (interventionId == null) {
            operation.setIntervention(null);
            operation.setMatchedAt(null);
            operation.setMatchConfidence(null);
            if (PlannedOperationStatus.REALISEE.name().equals(operation.getStatus())) {
                operation.setStatus(PlannedOperationStatus.PREVUE.name());
            }
            return lineOf(crop, plannedOperationRepository.save(operation).getId());
        }

        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Intervention introuvable : " + interventionId));

        // Rapprocher une intervention d'une autre campagne fausserait le coût constaté
        // des deux — celle qui la perd comme celle qui la gagne.
        if (intervention.getCrop() == null || !cropId.equals(intervention.getCrop().getId())) {
            throw new BusinessRuleException(
                    "Cette intervention n'appartient pas à la campagne : elle ne peut pas "
                            + "être rapprochée d'une de ses opérations prévues.");
        }

        // L'index unique partiel de la V29 le refuserait de toute façon, mais avec une
        // erreur de contrainte illisible plutôt qu'un message métier.
        plannedOperationRepository.findByIntervention_Id(interventionId)
                .filter(other -> !other.getId().equals(operationId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(
                            "Cette intervention est déjà rapprochée d'une autre opération "
                                    + "prévue de la campagne. Défaites d'abord ce "
                                    + "rapprochement.");
                });

        operation.setIntervention(intervention);
        operation.setMatchedAt(Instant.now());
        operation.setMatchConfidence(MatchConfidence.MANUELLE.name());

        // Une opération abandonnée qu'on rapproche redevient réalisée : le geste dit
        // qu'elle a bien eu lieu, et laisser le statut d'avant ferait cohabiter deux
        // affirmations contraires.
        if (!PlannedOperationStatus.PARTIELLE.name().equals(operation.getStatus())) {
            operation.setStatus(PlannedOperationStatus.REALISEE.name());
        }

        return lineOf(crop, plannedOperationRepository.save(operation).getId());
    }

    /**
     * Les seuils sur lesquels le moteur juge cette campagne, stade par stade.
     *
     * <p><strong>Pure restitution.</strong> {@code CropRequirementResolver} fusionne
     * depuis toujours les seuils généraux avec les surcharges de stade (V10) ; seule
     * l'exposition manquait. Le système comparait chaque mesure à des seuils que
     * personne ne pouvait voir : quand il annonçait un stress hydrique à 34 %, rien ne
     * disait que le minimum retenu était 35, ni qu'il avait changé au passage en
     * fructification.
     */
    @Override
    @Transactional(readOnly = true)
    public CropThresholds thresholdsOf(Long cropId) {
        return thresholdsAssembler.assemble(requireWithAccess(cropId));
    }

    // ============================================================
    // Clonage (V29, aucune migration)
    // ============================================================

    /**
     * Relance une campagne sur le modèle d'une précédente.
     *
     * <p><strong>Ce qui n'est délibérément PAS copié</strong>, et pourquoi :
     *
     * <ul>
     *   <li>{@code seedLot} — un lot est consommé. Le reporter serait un mensonge de
     *       traçabilité, sur le champ précisément dont on a besoin le jour où l'on
     *       cherche l'origine d'un problème de levée.</li>
     *   <li>{@code growthStage} — redérivé de la nouvelle date de plantation par
     *       {@code deriveCycle}. Copié, il ferait démarrer la campagne au stade où
     *       finissait l'ancienne.</li>
     *   <li>Tout champ de clôture — la nouvelle campagne n'est close ni ne l'a jamais
     *       été. Copier un {@code economicsSnapshot} lui attribuerait le bilan d'une
     *       autre.</li>
     *   <li>Le journal — il appartient à la campagne d'origine. Une seule entrée
     *       {@code CLONAGE} est écrite, qui nomme la source.</li>
     * </ul>
     *
     * <p><strong>L'itinéraire, lui, est décalé.</strong> Les opérations en {@code J+n} se
     * reportent telles quelles — c'est ce qui fait la valeur de cette datation ; les
     * dates fermes glissent du même nombre de jours que la plantation. Rapprochements,
     * coûts constatés et statuts sont remis à zéro : ils décrivaient l'autre campagne.
     */
    @Override
    @Transactional
    public CropResponse cloneFrom(Long sourceCropId, CropCloneRequest request) {
        Crop source = requireWithAccess(sourceCropId);

        Long targetPlotId = request.getPlotId() == null
                ? source.getPlot().getId()
                : request.getPlotId();

        // La règle « une seule campagne en cours par parcelle » vaut aussi ici : sans
        // elle, le clonage serait la porte dérobée par laquelle on en déclare deux.
        requireSingleActiveCrop(targetPlotId, CropStatus.EN_COURS, null);

        Crop clone = Crop.builder()
                .plot(plotService.require(targetPlotId))
                .cropName(source.getCropName())
                .variety(request.getVariety() == null ? source.getVariety() : request.getVariety())
                .seedLot(request.getSeedLot())          // jamais repris de la source
                .plantingDate(request.getPlantingDate())
                .cycleDurationDays(request.getCycleDurationDays() == null
                        ? source.getCycleDurationDays() : request.getCycleDurationDays())
                .plantedArea(request.getPlantedArea() == null
                        ? source.getPlantedArea() : request.getPlantedArea())
                .plantDensity(request.getPlantDensity() == null
                        ? source.getPlantDensity() : request.getPlantDensity())
                .status(CropStatus.EN_COURS.name())
                .build();

        Crop saved = cropRepository.save(deriveCycle(clone));

        if (!Boolean.FALSE.equals(request.getCopyItinerary())) {
            copyItinerary(source, saved);
        }

        journalWriter.recordClone(saved, sourceCropId);
        return cropMapper.toResponse(saved);
    }

    /**
     * Reporte l'itinéraire, décalé du delta de plantation.
     *
     * <p>Le décalage ne s'applique qu'aux dates fermes. Une opération en {@code J+n}
     * décrit une position dans le cycle, pas un jour : la décaler la déplacerait deux
     * fois. C'est exactement l'avantage de cette datation, et le perdre au clonage — le
     * seul moment où elle sert — la viderait de son sens.
     */
    private void copyItinerary(Crop source, Crop target) {
        List<CropPlannedOperation> template =
                plannedOperationRepository.findByCrop_IdOrderByPlannedOnAsc(source.getId());

        if (template.isEmpty()) {
            return;
        }

        Long shift = source.getPlantingDate() == null || target.getPlantingDate() == null
                ? null
                : ChronoUnit.DAYS.between(source.getPlantingDate(), target.getPlantingDate());

        List<CropPlannedOperation> copies = new ArrayList<>();
        for (CropPlannedOperation original : template) {
            copies.add(CropPlannedOperation.builder()
                    .crop(target)
                    .type(original.getType())
                    .label(original.getLabel())
                    // Sans date de plantation d'un côté ou de l'autre, le décalage est
                    // indéterminé : on retient J+n s'il existe, plutôt qu'une date fausse.
                    .plannedOn(original.getPlannedOn() == null || shift == null
                            ? null : original.getPlannedOn().plusDays(shift))
                    .daysAfterPlanting(original.getDaysAfterPlanting())
                    .growthStage(original.getGrowthStage())
                    .product(original.getProduct())
                    .dose(original.getDose())
                    .unit(original.getUnit())
                    .estimatedCost(original.getEstimatedCost())
                    // Rapprochement, statut et horodatage décrivaient l'autre campagne.
                    .status(PlannedOperationStatus.PREVUE.name())
                    .note(original.getNote())
                    .build());
        }

        // Une opération que le décalage a rendue indatable violerait chk_planned_op_when.
        // Le cas est marginal — il suppose une source sans date de plantation — mais
        // l'erreur de contrainte qui en résulterait serait illisible.
        copies.removeIf(copy -> copy.getPlannedOn() == null && copy.getDaysAfterPlanting() == null);

        plannedOperationRepository.saveAll(copies);
    }

    private CropItinerary assembleItinerary(Crop crop) {
        return itineraryAssembler.assemble(crop,
                plannedOperationRepository.findByCrop_IdOrderByPlannedOnAsc(crop.getId()),
                interventionRepository.findByCrop_IdOrderByPerformedAtAsc(crop.getId()),
                LocalDate.now());
    }

    /**
     * La ligne d'une opération, reconstruite depuis l'itinéraire complet.
     *
     * <p>Réassembler l'ensemble pour rendre une ligne n'est pas un détour : le retard et
     * le rapprochement d'une opération dépendent des autres — une intervention prise par
     * sa voisine ne lui est plus disponible. Une ligne calculée isolément serait fausse.
     */
    private PlannedOperationResponse lineOf(Crop crop, Long operationId) {
        return assembleItinerary(crop).getOperations().stream()
                .filter(line -> operationId.equals(line.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Opération introuvable : " + operationId));
    }

    private CropPlannedOperation requireOperation(Long cropId, Long operationId) {
        CropPlannedOperation operation = plannedOperationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Opération planifiée introuvable : " + operationId));

        // Sans ce contrôle, l'identifiant d'une opération suffirait à la modifier depuis
        // n'importe quelle campagne — y compris une à laquelle on n'a pas accès.
        if (operation.getCrop() == null || !cropId.equals(operation.getCrop().getId())) {
            throw new ResourceNotFoundException(
                    "Opération planifiée introuvable sur cette campagne : " + operationId);
        }
        return operation;
    }

    /**
     * Une opération qu'on ne sait pas dater n'est pas un plan, c'est une note.
     *
     * <p>Doublé par {@code chk_planned_op_when} en base : l'énumération et le service
     * protègent l'API, la contrainte protège les données contre une écriture directe.
     */
    private void requireDatable(PlannedOperationRequest request) {
        if (request.getPlannedOn() == null && request.getDaysAfterPlanting() == null) {
            throw new BusinessRuleException(
                    "Indiquez soit une date prévue, soit un nombre de jours après "
                            + "plantation : une opération qu'on ne sait pas placer dans le "
                            + "calendrier ne peut pas être suivie.");
        }
    }

    /** Passe par {@code PlotService.require} pour que {@code AccessGuard} s'applique. */
    private Crop requireWithAccess(Long cropId) {
        Crop crop = require(cropId);
        plotService.require(crop.getPlot().getId());
        return crop;
    }

    private Crop require(Long id) {
        return cropRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Culture introuvable : " + id));
    }

    // La base de connaissance stocke les cultures en minuscules (tomate, manioc).
    // La conversion est portée par l'énumération : un seul endroit fait foi.
    private String normalize(Culture culture) {
        return Culture.toStorage(culture);
    }
}
