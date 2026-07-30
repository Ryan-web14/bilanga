package com.sni.bilanga.organization.service.implementation;

import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.MembershipRole;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.organization.dto.request.CooperativeRequest;
import com.sni.bilanga.organization.dto.request.FarmMembershipRequest;
import com.sni.bilanga.organization.dto.request.FarmRequest;
import com.sni.bilanga.organization.dto.response.CooperativeResponse;
import com.sni.bilanga.organization.dto.response.FarmMembershipResponse;
import com.sni.bilanga.organization.dto.response.FarmResponse;
import com.sni.bilanga.organization.model.Cooperative;
import com.sni.bilanga.organization.model.Farm;
import com.sni.bilanga.organization.model.FarmMembership;
import com.sni.bilanga.organization.repository.CooperativeRepository;
import com.sni.bilanga.organization.repository.FarmMembershipRepository;
import com.sni.bilanga.organization.repository.FarmRepository;
import com.sni.bilanga.organization.service.interfaces.OrganizationService;
import com.sni.bilanga.organization.service.support.OrganizationMapper;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import com.sni.bilanga.utils.code.CodeComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final String COOPERATIVE_PREFIX = "COOP";
    private static final String FARM_PREFIX = "EXPL";
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final CooperativeRepository cooperativeRepository;
    private final FarmRepository farmRepository;
    private final FarmMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationMapper mapper;

    // ============================================================
    // Coopératives
    // ============================================================
    @Override
    @Transactional
    public CooperativeResponse createCooperative(CooperativeRequest request) {
        Cooperative cooperative = Cooperative.builder()
                .name(request.getName().trim())
                .location(trimmed(request.getLocation()))
                .contactPhone(trimmed(request.getContactPhone()))
                .status(DomainEnums.nameOf(
                        request.getStatus() == null ? PlotStatus.ACTIVE : request.getStatus()))
                .code(nextCooperativeCode())
                .build();

        return mapper.toResponse(cooperativeRepository.save(cooperative), 0);
    }

    @Override
    @Transactional
    public CooperativeResponse updateCooperative(Long id, CooperativeRequest request) {
        Cooperative cooperative = requireCooperative(id);

        cooperative.setName(request.getName().trim());
        cooperative.setLocation(trimmed(request.getLocation()));
        cooperative.setContactPhone(trimmed(request.getContactPhone()));
        if (request.getStatus() != null) {
            cooperative.setStatus(request.getStatus().name());
        }
        cooperative.setUpdatedAt(Instant.now());

        return toResponse(cooperativeRepository.save(cooperative));
    }

    @Override
    @Transactional(readOnly = true)
    public CooperativeResponse findCooperative(Long id) {
        return toResponse(requireCooperative(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CooperativeResponse> searchCooperatives(String status, String term, Pageable pageable) {
        return cooperativeRepository
                .search(upper(status), likePattern(term), pageable)
                .map(this::toResponse);
    }

    /**
     * Archivage, jamais suppression.
     *
     * <p>Les exploitations rattachées <strong>survivent</strong> : la clé
     * étrangère est en {@code ON DELETE SET NULL}, mais on ne l'exerce même pas.
     * Archiver signale que la coopérative n'est plus active, sans rien retirer à
     * personne — supprimer ferait disparaître le rattachement historique de
     * quarante exploitations pour une décision administrative.
     */
    @Override
    @Transactional
    public void archiveCooperative(Long id) {
        Cooperative cooperative = requireCooperative(id);
        cooperative.setStatus(PlotStatus.ARCHIVEE.name());
        cooperative.setUpdatedAt(Instant.now());
        cooperativeRepository.save(cooperative);
    }

    // ============================================================
    // Exploitations
    // ============================================================
    @Override
    @Transactional
    public FarmResponse createFarm(FarmRequest request) {
        Users owner = resolveUser(request.getOwnerUserId());

        Farm farm = Farm.builder()
                .name(request.getName().trim())
                .cooperative(resolveCooperative(request.getCooperativeId()))
                .owner(owner)
                .location(trimmed(request.getLocation()))
                .contactPhone(trimmed(request.getContactPhone()))
                .status(DomainEnums.nameOf(
                        request.getStatus() == null ? PlotStatus.ACTIVE : request.getStatus()))
                .code(nextFarmCode())
                .build();

        Farm saved = farmRepository.save(farm);

        // Le propriétaire devient membre PROPRIETAIRE dans la foulée : sans
        // cela, une exploitation naîtrait sans personne capable de la consulter,
        // et il faudrait un second appel pour réparer ce que le premier a créé.
        if (owner != null) {
            membershipRepository.save(FarmMembership.builder()
                    .farm(saved)
                    .user(owner)
                    .role(MembershipRole.PROPRIETAIRE.name())
                    .build());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public FarmResponse updateFarm(Long id, FarmRequest request) {
        Farm farm = requireFarm(id);

        farm.setName(request.getName().trim());
        farm.setCooperative(resolveCooperative(request.getCooperativeId()));
        if (request.getOwnerUserId() != null) {
            farm.setOwner(resolveUser(request.getOwnerUserId()));
        }
        farm.setLocation(trimmed(request.getLocation()));
        farm.setContactPhone(trimmed(request.getContactPhone()));
        if (request.getStatus() != null) {
            farm.setStatus(request.getStatus().name());
        }
        farm.setUpdatedAt(Instant.now());

        return toResponse(farmRepository.save(farm));
    }

    @Override
    @Transactional(readOnly = true)
    public FarmResponse findFarm(Long id) {
        return toResponse(requireFarm(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FarmResponse> searchFarms(Long cooperativeId, Long ownerId, String status,
                                          String term, Pageable pageable) {
        return farmRepository
                .search(cooperativeId, ownerId, upper(status), likePattern(term), pageable)
                .map(this::toResponse);
    }

    /**
     * Archivage, jamais suppression.
     *
     * <p>Les parcelles rattachées <strong>survivent et redeviennent
     * indépendantes</strong> : leur propriétaire garde son accès, puisqu'il ne
     * l'a jamais tenu de l'exploitation. C'est la propriété qui rend cette
     * hiérarchie non bloquante — dissoudre une exploitation ne peut pas priver
     * quelqu'un de ses propres parcelles.
     */
    @Override
    @Transactional
    public void archiveFarm(Long id) {
        Farm farm = requireFarm(id);
        farm.setStatus(PlotStatus.ARCHIVEE.name());
        farm.setUpdatedAt(Instant.now());
        farmRepository.save(farm);
    }

    // ============================================================
    // Appartenances
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<FarmMembershipResponse> membersOf(Long farmId) {
        requireFarm(farmId);
        return membershipRepository.findByFarm_IdOrderByJoinedAtAsc(farmId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public FarmMembershipResponse addOrUpdateMember(Long farmId, FarmMembershipRequest request) {
        Farm farm = requireFarm(farmId);
        Users user = resolveUser(request.getUserId());

        if (user == null) {
            throw new BusinessRuleException("L'utilisateur est obligatoire.");
        }

        // Créer ou modifier selon l'existant, plutôt que refuser un doublon :
        // obliger l'appelant à savoir d'abord si la personne est membre, pour
        // choisir entre deux routes, est une distinction sans intérêt de son
        // point de vue.
        FarmMembership membership = membershipRepository
                .findByFarm_IdAndUser_Id(farmId, user.getId())
                .orElseGet(() -> FarmMembership.builder().farm(farm).user(user).build());

        membership.setRole(request.getRole().name());

        return mapper.toResponse(membershipRepository.save(membership));
    }

    /**
     * Retire un membre.
     *
     * <p>Le propriétaire de référence ne peut pas être retiré : l'exploitation
     * se retrouverait sans personne capable de la consulter, état dont on ne
     * pourrait sortir que par une intervention en base.
     */
    @Override
    @Transactional
    public void removeMember(Long farmId, Long userId) {
        Farm farm = requireFarm(farmId);

        FarmMembership membership = membershipRepository
                .findByFarm_IdAndUser_Id(farmId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cette personne n'est pas membre de l'exploitation."));

        if (farm.getOwner() != null && farm.getOwner().getId().equals(userId)) {
            throw new BusinessRuleException(
                    "Le propriétaire de référence ne peut pas être retiré. "
                    + "Désignez d'abord un autre propriétaire sur l'exploitation.");
        }

        membershipRepository.delete(membership);
    }

    // ============================================================
    // Codes lisibles
    // ============================================================

    /**
     * Référence lisible, sur le modèle de {@code PlotCodeGenerator}.
     *
     * <p>Rend {@code null} plutôt que d'échouer si aucun code libre n'est
     * trouvé : une exploitation sans référence reste parfaitement utilisable, et
     * refuser sa création pour un détail de présentation serait disproportionné.
     */
    private String nextCooperativeCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = CodeComposer.refWithYear(COOPERATIVE_PREFIX, LocalDate.now(),
                    cooperativeRepository.nextCodeSequence());
            if (!cooperativeRepository.existsByCode(code)) {
                return code;
            }
        }
        log.warn("Référence de coopérative non attribuée après {} tentatives.", MAX_CODE_ATTEMPTS);
        return null;
    }

    private String nextFarmCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = CodeComposer.refWithYear(FARM_PREFIX, LocalDate.now(),
                    farmRepository.nextCodeSequence());
            if (!farmRepository.existsByCode(code)) {
                return code;
            }
        }
        log.warn("Référence d'exploitation non attribuée après {} tentatives.", MAX_CODE_ATTEMPTS);
        return null;
    }

    // ============================================================
    // Interne
    // ============================================================
    private CooperativeResponse toResponse(Cooperative c) {
        return mapper.toResponse(c, cooperativeRepository.countFarms(c.getId()));
    }

    private FarmResponse toResponse(Farm f) {
        return mapper.toResponse(f,
                farmRepository.countPlots(f.getId()),
                farmRepository.countMembers(f.getId()));
    }

    private Cooperative requireCooperative(Long id) {
        return cooperativeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coopérative introuvable : " + id));
    }

    private Farm requireFarm(Long id) {
        return farmRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exploitation introuvable : " + id));
    }

    /** Nulle est une réponse valable : l'exploitation est alors indépendante. */
    private Cooperative resolveCooperative(Long id) {
        return id == null ? null : requireCooperative(id);
    }

    private Users resolveUser(Long id) {
        if (id == null) return null;
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + id));
    }

    private String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Motif LIKE déjà en minuscules — même raison que dans {@code PlotServiceImpl} :
     * appliquer {@code lower()} au paramètre côté SQL empêche PostgreSQL d'en
     * inférer le type lorsqu'il est nul.
     */
    private String likePattern(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
