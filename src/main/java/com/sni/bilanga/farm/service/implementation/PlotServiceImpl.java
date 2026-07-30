package com.sni.bilanga.farm.service.implementation;


import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.utils.constant.SystemActors;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.enums.SoilType;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.dto.request.PlotRequest;
import com.sni.bilanga.farm.dto.response.PlotResponse;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.repository.PlotRepository;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.farm.service.support.PlotCodeGenerator;
import com.sni.bilanga.farm.service.support.PlotMapper;
import com.sni.bilanga.organization.model.Farm;
import com.sni.bilanga.organization.repository.FarmRepository;
import com.sni.bilanga.security.access.AccessGuard;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlotServiceImpl implements PlotService {

    /**
     * Valeur muette passée à la requête quand aucune exploitation n'entre en
     * jeu. {@code in ()} sur une collection vide n'est pas du SQL valide ; un
     * identifiant impossible garde la clause syntaxiquement correcte et
     * sémantiquement inerte.
     */
    private static final List<Long> NO_FARM_SCOPE = List.of(-1L);

    private final PlotRepository plotRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final PlotMapper plotMapper;
    private final PlotCodeGenerator plotCodeGenerator;
    private final AccessGuard accessGuard;

    /** Sert à attribuer la parcelle à son créateur quand aucun propriétaire n'est désigné. */
    private final com.sni.bilanga.audit.context.SecurityAuditContextProvider actorProvider;

    @Override
    @Transactional
    public PlotResponse create(PlotRequest request) {
        Plot plot = Plot.builder()
                .name(request.getName())
                .location(request.getLocation())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .altitude(request.getAltitude())
                .soilType(DomainEnums.nameOf(request.getSoilType()))
                .irrigationType(DomainEnums.nameOf(request.getIrrigationType()))
                .area(request.getArea())
                .status(DomainEnums.nameOf(
                        request.getStatus() == null ? PlotStatus.ACTIVE : request.getStatus()))
                .user(resolveUser(request.getUserId()))
                .farm(resolveFarm(request.getFarmId()))
                // Référence attribuée sans que l'appelant ait à la fournir : une
                // numérotation tenue à la main dérive dès la deuxième saisie.
                .plotCode(plotCodeGenerator.next())
                .build();

        return plotMapper.toResponse(plotRepository.save(plot));
    }

    @Override
    @Transactional
    public PlotResponse update(Long id, PlotRequest request) {
        Plot plot = require(id);

        plot.setName(request.getName());
        plot.setLocation(request.getLocation());
        plot.setLatitude(request.getLatitude());
        plot.setLongitude(request.getLongitude());
        plot.setAltitude(request.getAltitude());
        plot.setSoilType(DomainEnums.nameOf(request.getSoilType()));
        plot.setIrrigationType(DomainEnums.nameOf(request.getIrrigationType()));
        plot.setArea(request.getArea());
        // plotCode n'est pas modifiable : une référence déjà communiquée au
        // terrain ne doit pas changer de sens sous les pieds de l'exploitant.
        if (plot.getPlotCode() == null) {
            plot.setPlotCode(plotCodeGenerator.next());
        }
        if (request.getStatus() != null) {
            plot.setStatus(request.getStatus().name());
        }
        if (request.getUserId() != null) {
            plot.setUser(resolveUser(request.getUserId()));
        }
        // Le rattachement se modifie librement, y compris pour être retiré :
        // une parcelle sortie d'une exploitation redevient simplement celle de
        // son propriétaire, sans que rien d'autre ne change.
        plot.setFarm(resolveFarm(request.getFarmId()));
        plot.setUpdatedAt(Instant.now());

        return plotMapper.toResponse(plotRepository.save(plot));
    }

    @Override
    @Transactional(readOnly = true)
    public PlotResponse findById(Long id) {
        return plotMapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlotResponse> search(Long userId, PlotStatus status, SoilType soilType,
                                     String term, Pageable pageable) {

        // Les parcelles des exploitations dont l'appelant est membre s'AJOUTENT
        // aux siennes : un conseiller suit une exploitation dont il n'est
        // propriétaire d'aucune parcelle. Liste vide dans le cas courant — la
        // recherche est alors exactement celle d'avant.
        List<Long> farmIds = accessGuard.visibleFarmIds();
        boolean hasFarmScope = !farmIds.isEmpty();

        return plotRepository
                .search(accessGuard.resolveOwnerFilter(userId),
                        hasFarmScope,
                        hasFarmScope ? farmIds : NO_FARM_SCOPE,
                        DomainEnums.nameOf(status), DomainEnums.nameOf(soilType),
                        likePattern(term), pageable)
                .map(plotMapper::toResponse);
    }

    /**
     * Archivage plutôt que suppression physique : les diagnostics et relevés
     * rattachés constituent un historique qu'on ne détruit pas en cascade.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Plot plot = require(id);
        plot.setStatus(PlotStatus.ARCHIVEE.name());
        plot.setUpdatedAt(Instant.now());
        plotRepository.save(plot);
    }

    @Override
    @Transactional(readOnly = true)
    public Plot require(Long id) {
        Plot plot = plotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parcelle introuvable : " + id));

        // Point de passage obligé : cultures, boîtiers, relevés, diagnostics et
        // observations résolvent tous leur parcelle par ici. Contrôler à cet
        // endroit couvre l'ensemble sans disperser la règle.
        accessGuard.requireAccess(plot);
        return plot;
    }

    /**
     * Propriétaire de la parcelle : celui qu'on désigne, ou <strong>l'appelant</strong>.
     *
     * <h2>Pourquoi le repli sur l'appelant</h2>
     *
     * <p>Exiger que le client transmette son propre identifiant était à la fois redondant
     * et piégeux. {@code Users} porte <strong>deux</strong> identifiants — {@code id}, le
     * Snowflake numérique, et {@code userId}, le code lisible {@code USR-300726-…} — et
     * {@code GET /auth/me} rend le second sous le nom {@code userId}. Le champ
     * {@code PlotRequest.userId}, lui, attend le premier.
     *
     * <p>Même nom, contenus incompatibles : recopier ce que l'API vient de rendre
     * produisait un 400. Le repli supprime la question — celui qui crée une parcelle en
     * est le propriétaire, c'est le cas normal.
     *
     * <h2>Ce que le repli évite en plus</h2>
     *
     * <p>Une parcelle sans propriétaire n'est visible que d'un compte privilégié une fois
     * {@code ownership.enabled} actif. Un exploitant pouvait donc créer une parcelle et
     * ne plus la voir — un piège silencieux, et le plus déroutant qui soit.
     *
     * <p>Rendre {@code null} reste possible : il faut alors qu'aucun appelant ne soit
     * authentifié, ce qui n'arrive qu'à l'ingestion, où aucune parcelle n'est créée.
     */
    private Users resolveUser(Long userId) {
        Long target = userId != null ? userId : actorProvider.userIdOrNull();

        if (target == null || SystemActors.SYSTEM_USER_ID.equals(target)) {
            return null;
        }
        return userRepository.findById(target)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + target));
    }

    /** Nulle est une réponse valable : la parcelle n'appartient alors à aucune exploitation. */
    private Farm resolveFarm(Long farmId) {
        if (farmId == null) return null;
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Exploitation introuvable : " + farmId));
    }

    /**
     * Motif LIKE, déjà en minuscules.
     *
     * La normalisation se fait ici plutôt que dans la requête : appliquer
     * {@code lower()} au paramètre côté SQL empêchait PostgreSQL d'en inférer le
     * type lorsqu'il était nul — {@code function lower(bytea) does not exist} —
     * et faisait au passage travailler la base sur chaque ligne.
     */
    private String likePattern(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return "%" + term.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }
}
