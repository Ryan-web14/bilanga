package com.sni.bilanga.organization.repository;

import com.sni.bilanga.organization.model.FarmMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FarmMembershipRepository extends JpaRepository<FarmMembership, Long> {

    Optional<FarmMembership> findByFarm_IdAndUser_Id(Long farmId, Long userId);

    List<FarmMembership> findByFarm_IdOrderByJoinedAtAsc(Long farmId);

    Page<FarmMembership> findByUser_Id(Long userId, Pageable pageable);

    /**
     * Rôle d'une personne sur l'exploitation d'une parcelle donnée.
     *
     * <p>Interrogé par {@code AccessGuard} à chaque accès à une parcelle : c'est
     * la requête la plus chaude du cloisonnement, d'où le passage direct par le
     * {@code farm_id} de la parcelle plutôt que par un chargement de l'entité.
     *
     * <p>Rend vide si la parcelle n'a pas d'exploitation — cas majoritaire, et
     * parfaitement normal : l'accès retombe alors sur la propriété directe.
     */
    @Query("""
           select m.role from FarmMembership m
           where m.user.id = :userId
             and m.farm.id = (select p.farm.id from Plot p where p.id = :plotId)
           """)
    Optional<String> findRoleForPlot(@Param("userId") Long userId, @Param("plotId") Long plotId);

    /** Exploitations dont la personne est membre : périmètre de ses recherches. */
    @Query("select m.farm.id from FarmMembership m where m.user.id = :userId")
    List<Long> findFarmIdsForUser(@Param("userId") Long userId);
}
