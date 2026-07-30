package com.sni.bilanga.organization.service.interfaces;

import com.sni.bilanga.organization.dto.request.CooperativeRequest;
import com.sni.bilanga.organization.dto.request.FarmMembershipRequest;
import com.sni.bilanga.organization.dto.request.FarmRequest;
import com.sni.bilanga.organization.dto.response.CooperativeResponse;
import com.sni.bilanga.organization.dto.response.FarmMembershipResponse;
import com.sni.bilanga.organization.dto.response.FarmResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Administration du niveau d'organisation : coopératives, exploitations,
 * appartenances.
 *
 * <p>Un seul service pour les trois : ils forment une hiérarchie qu'on
 * administre d'un bloc, et les découper obligerait à faire circuler les mêmes
 * dépôts entre trois classes pour un gain de lisibilité nul.
 *
 * <p><strong>Rien de tout cela n'est requis pour utiliser le système.</strong>
 * Aucune parcelle n'a besoin d'exploitation, aucune exploitation n'a besoin de
 * coopérative. Ces routes servent à ceux qui en ont l'usage.
 */
public interface OrganizationService {

    // ── Coopératives ────────────────────────────────────────────
    CooperativeResponse createCooperative(CooperativeRequest request);

    CooperativeResponse updateCooperative(Long id, CooperativeRequest request);

    CooperativeResponse findCooperative(Long id);

    Page<CooperativeResponse> searchCooperatives(String status, String term, Pageable pageable);

    /** Archivage : les exploitations rattachées survivent, détachées. */
    void archiveCooperative(Long id);

    // ── Exploitations ───────────────────────────────────────────
    FarmResponse createFarm(FarmRequest request);

    FarmResponse updateFarm(Long id, FarmRequest request);

    FarmResponse findFarm(Long id);

    Page<FarmResponse> searchFarms(Long cooperativeId, Long ownerId, String status,
                                   String term, Pageable pageable);

    /** Archivage : les parcelles rattachées survivent et redeviennent indépendantes. */
    void archiveFarm(Long id);

    // ── Appartenances ───────────────────────────────────────────
    List<FarmMembershipResponse> membersOf(Long farmId);

    /**
     * Ajoute un membre, ou change son rôle s'il en est déjà un.
     *
     * Refuser un doublon obligerait l'appelant à savoir d'abord si la personne
     * est membre, pour choisir entre créer et modifier — une distinction sans
     * intérêt de son point de vue.
     */
    FarmMembershipResponse addOrUpdateMember(Long farmId, FarmMembershipRequest request);

    void removeMember(Long farmId, Long userId);
}
