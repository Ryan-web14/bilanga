package com.sni.bilanga.diagnosis.service.implementation;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.dto.response.AlertResponse;
import com.sni.bilanga.diagnosis.model.Alert;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.AlertRepository;
import com.sni.bilanga.diagnosis.service.interfaces.AlertService;
import com.sni.bilanga.enums.AlertCategory;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.AlertStatus;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.notification.service.NotificationService;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import com.sni.bilanga.utils.format.TimeRange;
import com.sni.bilanga.utils.sort.SemanticSort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final Set<String> OPEN_STATUSES = AlertStatus.OPEN_NAMES;

    /** Au-delà, l'accumulation de conseils urgents justifie un signalement critique. */
    private static final int CRITICAL_URGENT_COUNT = 2;

    static final String REASON_MANUAL = "RESOLUE_MANUELLEMENT";
    static final String REASON_NORMALISED = "AUTO_SITUATION_NORMALISEE";
    static final String REASON_REPLACED = "AUTO_SITUATION_REMPLACEE";

    /**
     * Rangs de tri : trier sur les colonnes en clair donnait un ordre
     * alphabétique, donc la première page n'était pas la plus urgente.
     */
    private static final Map<String, String> SORTABLE_RANKS = Map.of(
            "level",  SemanticSort.rankExpression("a.level", "CRITIQUE", "ELEVEE", "MOYENNE"),
            "status", SemanticSort.rankExpression("a.status", "NOUVELLE", "ACQUITTEE", "RESOLUE"));

    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BilangaProperties.Alert alertConfig;


    // ============================================================
    // Génération et réconciliation
    // ============================================================
    @Override
    @Transactional
    public void raiseIfNeeded(Diagnostic diagnostic,
                              List<RecommendationItem> recommendations,
                              boolean reliable) {

        Long plotId = diagnostic.getPlot().getId();
        String signature = signatureOf(diagnostic);
        String sourcePrefix = diagnostic.getSource() + ":";

        long urgent = recommendations == null ? 0 : recommendations.stream()
                .filter(item -> RecommendationPriority.isUrgent(item.getPriority()))
                .count();

        // Un diagnostic peu fiable ne conclut rien : il ne doit ni lever d'alerte,
        // ni servir de motif pour en refermer une. L'exploitant se déplacerait —
        // ou cesserait de se déplacer — sur la foi d'une conclusion que le
        // système lui-même ne soutient pas.
        if (!reliable) {
            return;
        }

        // La situation observée sur cette voie a changé : ce qu'on y signalait
        // avant n'a plus lieu d'être.
        closeStaleAlerts(plotId, signature, sourcePrefix);

        if (urgent == 0) {
            // Plus rien d'urgent sur cette voie : la situation s'est normalisée.
            closeAlert(plotId, signature, REASON_NORMALISED);
            return;
        }

        Optional<Alert> existing = alertRepository
                .findFirstByPlot_IdAndSignatureAndStatusInOrderByCreatedAtDesc(
                        plotId, signature, OPEN_STATUSES);

        if (existing.isPresent()) {
            refresh(existing.get(), urgent);
            return;
        }

        Alert raised = alertRepository.save(Alert.builder()
                .plot(diagnostic.getPlot())
                .diagnostic(diagnostic)
                .level(levelFor(urgent).name())
                .message(messageFor(diagnostic, urgent))
                .status(AlertStatus.NOUVELLE.name())
                .signature(signature)
                .lastSeenAt(Instant.now())
                .escalationCount(0)
                .build());

        // L'alerte ne restait visible que sur le tableau de bord : personne
        // n'apprenait qu'il fallait intervenir sans aller le consulter.
        notificationService.enqueue(raised);
    }

    // ============================================================
    // Alertes techniques
    // ============================================================

    /**
     * Signale une panne de matériel, distincte des situations agronomiques.
     *
     * <p>Une sonde bloquée n'est pas un conseil de culture : elle s'adresse au
     * technicien, pas à l'exploitant, et le motif doit dire <em>quelle</em>
     * sonde changer plutôt que ce qu'il faudrait faire au champ.
     *
     * <p>La déduplication réutilise le mécanisme de signature déjà en place :
     * l'empreinte porte sur la <em>situation matérielle</em>
     * ({@code TECHNIQUE:<identifiant du boîtier>}) et non sur le relevé qui l'a
     * révélée. Une sonde en panne depuis trois jours produit ainsi une alerte,
     * pas trois cents.
     *
     * <p>Réciproquement, un verdict revenu à {@code SAINE} referme l'alerte :
     * sans quoi une sonde remplacée laisserait derrière elle un signalement que
     * plus rien ne justifie, et le technicien apprendrait à ignorer la liste.
     */
    @Override
    @Transactional
    public void raiseTechnical(Plot plot, String deviceKey, String reason, boolean faulty) {
        if (plot == null || deviceKey == null) {
            return;
        }

        String signature = "TECHNIQUE:" + deviceKey;

        if (!faulty) {
            closeAlert(plot.getId(), signature, REASON_NORMALISED);
            return;
        }

        Optional<Alert> existing = alertRepository
                .findFirstByPlot_IdAndSignatureAndStatusInOrderByCreatedAtDesc(
                        plot.getId(), signature, OPEN_STATUSES);

        if (existing.isPresent()) {
            Alert alert = existing.get();
            alert.setLastSeenAt(Instant.now());
            // Le motif est réécrit : la panne peut avoir gagné une autre sonde
            // depuis la levée, et l'intervention à prévoir n'est plus la même.
            alert.setMessage(reason);
            alertRepository.save(alert);
            return;
        }

        Alert raised = alertRepository.save(Alert.builder()
                .plot(plot)
                .category(Alert.CATEGORY_TECHNICAL)
                // ELEVEE et non CRITIQUE : la parcelle n'est pas en danger, c'est
                // la surveillance qui l'est. Réserver le niveau critique aux
                // situations qui menacent la culture préserve son sens.
                .level(AlertLevel.ELEVEE.name())
                .message(reason)
                .status(AlertStatus.NOUVELLE.name())
                .signature(signature)
                .lastSeenAt(Instant.now())
                .escalationCount(0)
                .build());

        notificationService.enqueue(raised);
    }

    /**
     * La situation dure. On ne crée pas de doublon — c'était déjà le cas — mais
     * on ne se contente plus de l'ignorer : l'alerte est datée à nouveau, et si
     * elle reste sans acquittement au fil des reconstats, elle monte d'un niveau.
     * Une alerte ignorée qui reste au même rang finit par se confondre avec le
     * bruit de fond.
     */
    private void refresh(Alert alert, long urgent) {
        alert.setLastSeenAt(Instant.now());
        boolean escalated = false;

        AlertLevel current = AlertLevel.from(alert.getLevel());
        AlertLevel warranted = levelFor(urgent);

        // Le nombre de conseils urgents a pu augmenter depuis la levée.
        if (current == null || warranted.isAtLeast(current)) {
            alert.setLevel(warranted.name());
            current = warranted;
        }

        if (AlertStatus.NOUVELLE.name().equals(alert.getStatus())) {
            int count = alert.getEscalationCount() == null ? 0 : alert.getEscalationCount() + 1;
            alert.setEscalationCount(count);

            if (count >= alertConfig.getEscalationThreshold() && current != AlertLevel.CRITIQUE) {
                alert.setLevel(current.escalated().name());
                alert.setEscalationCount(0);
                escalated = true;
                log.info("Alerte {} non acquittée après {} reconstats : niveau porté à {}",
                        alert.getId(), count, alert.getLevel());
            }
        }

        alertRepository.save(alert);

        // Une alerte qui monte d'un niveau est une situation nouvelle du point
        // de vue de l'exploitant : elle mérite d'être resignalée.
        if (escalated) {
            notificationService.enqueue(alert);
        }
    }

    /**
     * Ferme les alertes de la même voie dont l'empreinte n'est plus celle qu'on
     * observe. Le cas typique : la parcelle passait pour atteinte de mildiou,
     * le diagnostic conclut désormais autre chose.
     */
    private void closeStaleAlerts(Long plotId, String currentSignature, String sourcePrefix) {
        List<Alert> stale = alertRepository.findStaleOnSameSource(
                plotId, currentSignature, sourcePrefix, OPEN_STATUSES);

        for (Alert alert : stale) {
            close(alert, REASON_REPLACED);
        }
        if (!stale.isEmpty()) {
            alertRepository.saveAll(stale);
        }
    }

    private void closeAlert(Long plotId, String signature, String reason) {
        alertRepository.findFirstByPlot_IdAndSignatureAndStatusInOrderByCreatedAtDesc(
                        plotId, signature, OPEN_STATUSES)
                .ifPresent(alert -> {
                    close(alert, reason);
                    alertRepository.save(alert);
                });
    }

    private void close(Alert alert, String reason) {
        alert.setStatus(AlertStatus.RESOLUE.name());
        alert.setResolvedAt(Instant.now());
        alert.setResolutionReason(reason);
        log.info("Alerte {} refermée automatiquement ({})", alert.getId(), reason);
    }

    private AlertLevel levelFor(long urgent) {
        return urgent >= CRITICAL_URGENT_COUNT ? AlertLevel.CRITIQUE : AlertLevel.ELEVEE;
    }

    /**
     * Une alerte porte sur une situation, non sur une mesure : parcelle,
     * culture et diagnostic suffisent à l'identifier. Deux relevés successifs
     * aboutissant au même constat ne produisent donc qu'une alerte.
     */
    private String signatureOf(Diagnostic d) {
        return d.getSource() + ":" + d.getCropName() + ":" + d.getResult();
    }

    private String messageFor(Diagnostic d, long urgent) {
        String origin = "IMAGE".equals(d.getSource()) ? "l'analyse d'image" : "les capteurs";
        return String.format(
                "%s sur la parcelle %s : %s détecté par %s. %d action%s à mener sans délai.",
                urgent >= CRITICAL_URGENT_COUNT ? "Situation critique" : "Intervention requise",
                d.getPlot().getName(), d.getResult(), origin,
                urgent, urgent > 1 ? "s" : "");
    }

    // ============================================================
    // Consultation
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> findByPlot(Long plotId, boolean openOnly) {
        List<Alert> alerts = openOnly
                ? alertRepository.findByPlot_IdAndStatusInOrderByCreatedAtDesc(plotId, OPEN_STATUSES)
                : alertRepository.findByPlot_IdOrderByCreatedAtDesc(plotId);

        return alerts.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> findOpen() {
        return alertRepository.findByStatusInOrderByCreatedAtDesc(OPEN_STATUSES)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> search(Long plotId, AlertCategory category, AlertLevel level,
                                      AlertStatus status, boolean openOnly,
                                      Instant from, Instant to, Pageable pageable) {
        return alertRepository
                .search(plotId, DomainEnums.nameOf(category),
                        DomainEnums.nameOf(level), DomainEnums.nameOf(status),
                        openOnly, OPEN_STATUSES, TimeRange.from(from), TimeRange.to(to),
                        SemanticSort.rewrite(pageable, SORTABLE_RANKS))
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertResponse findById(Long id) {
        return toResponse(require(id));
    }

    // ============================================================
    // Cycle de vie manuel
    // ============================================================
    @Override
    @Transactional
    public AlertResponse acknowledge(Long id) {
        Alert alert = require(id);
        transitionTo(alert, AlertStatus.ACQUITTEE);
        alert.setAcknowledgedAt(Instant.now());
        // L'acquittement remet le compteur à zéro : quelqu'un a pris la main.
        alert.setEscalationCount(0);
        return toResponse(alertRepository.save(alert));
    }

    @Override
    @Transactional
    public AlertResponse resolve(Long id) {
        Alert alert = require(id);
        transitionTo(alert, AlertStatus.RESOLUE);
        alert.setResolvedAt(Instant.now());
        alert.setResolutionReason(REASON_MANUAL);
        return toResponse(alertRepository.save(alert));
    }

    /**
     * Désigne un responsable et, éventuellement, un terme.
     *
     * <p>Sans destinataire, une alerte reste dans la liste de tout le monde et
     * donc dans celle de personne. Le terme, lui, est ce qui distingue un
     * conseil d'un engagement : « à traiter sous 48 h » se vérifie, « pensez à
     * traiter » ne se vérifie pas.
     *
     * <p>Réassigner une alerte close est refusé : elle n'appelle plus d'action,
     * et l'y rattacher quelqu'un ne ferait que brouiller la trace de qui a
     * réellement traité quoi.
     */
    @Override
    @Transactional
    public AlertResponse assign(Long id, Long userId, Instant dueAt) {
        Alert alert = require(id);

        AlertStatus current = AlertStatus.from(alert.getStatus());
        if (current != null && !current.isOpen()) {
            throw new BusinessRuleException(
                    "Cette alerte est close : elle n'appelle plus d'intervention.");
        }

        alert.setAssignedTo(userId == null ? null : userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId)));
        alert.setDueAt(dueAt);

        return toResponse(alertRepository.save(alert));
    }

    /**
     * Une alerte résolue est définitive, et on n'acquitte pas deux fois.
     * Sans ce contrôle, réacquitter écrasait la date d'acquittement d'origine —
     * la trace de qui avait pris la main en premier était perdue.
     */
    private void transitionTo(Alert alert, AlertStatus target) {
        AlertStatus current = AlertStatus.from(alert.getStatus());
        if (current != null && !current.canTransitionTo(target)) {
            throw BusinessRuleException.invalidTransition(
                    "Alerte " + alert.getId(), current.getLabel(), target.getLabel());
        }
        alert.setStatus(target.name());
    }

    private Alert require(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte introuvable : " + id));
    }

    private AlertResponse toResponse(Alert a) {
        AlertLevel level = AlertLevel.from(a.getLevel());
        AlertStatus status = AlertStatus.from(a.getStatus());

        return AlertResponse.builder()
                .id(a.getId())
                .plotId(a.getPlot().getId())
                .plotName(a.getPlot().getName())
                .diagnosticId(a.getDiagnostic() == null ? null : a.getDiagnostic().getId())
                .category(a.getCategory())
                .categoryLabel(labelOfCategory(a.getCategory()))
                .level(a.getLevel())
                .levelLabel(level == null ? null : level.getLabel())
                .message(a.getMessage())
                .status(a.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .open(status == null || status.isOpen())
                .assignedToUserId(a.getAssignedTo() == null ? null : a.getAssignedTo().getId())
                .assignedToName(displayNameOf(a.getAssignedTo()))
                .dueAt(a.getDueAt())
                .overdue(isOverdue(a, status))
                .createdAt(a.getCreatedAt())
                .acknowledgedAt(a.getAcknowledgedAt())
                .resolvedAt(a.getResolvedAt())
                .lastSeenAt(a.getLastSeenAt())
                .ageHours(hoursSince(a.getCreatedAt()))
                .resolutionReason(a.getResolutionReason())
                .escalationCount(a.getEscalationCount())
                .build();
    }

    private Long hoursSince(Instant moment) {
        return moment == null ? null : Duration.between(moment, Instant.now()).toHours();
    }

    /**
     * Un terme dépassé sur une alerte close n'a pas d'intérêt : ce qui a été
     * traité, fût-ce en retard, n'appelle plus rien.
     */
    private Boolean isOverdue(Alert a, AlertStatus status) {
        if (a.getDueAt() == null) {
            return null;
        }
        boolean open = status == null || status.isOpen();
        return open && a.getDueAt().isBefore(Instant.now());
    }

    private String labelOfCategory(String stored) {
        AlertCategory category = AlertCategory.from(stored);
        return category == null ? null : category.getLabel();
    }

    private String displayNameOf(Users user) {
        if (user == null) {
            return null;
        }
        String full = String.join(" ",
                        user.getFirstname() == null ? "" : user.getFirstname(),
                        user.getLastname() == null ? "" : user.getLastname())
                .trim();
        return full.isEmpty() ? user.getEmail() : full;
    }
}
