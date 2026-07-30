package com.sni.bilanga.diagnosis.repository;


import com.sni.bilanga.diagnosis.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByPlot_IdOrderByCreatedAtDesc(Long plotId);

    List<Alert> findByPlot_IdAndStatusInOrderByCreatedAtDesc(Long plotId, Collection<String> statuses);

    List<Alert> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    /** Une alerte ouverte porte-t-elle déjà cette empreinte sur la parcelle ? */
    boolean existsByPlot_IdAndSignatureAndStatusIn(Long plotId, String signature, Collection<String> statuses);

    /** L'alerte ouverte portant cette empreinte, pour la rafraîchir plutôt que la dupliquer. */
    Optional<Alert> findFirstByPlot_IdAndSignatureAndStatusInOrderByCreatedAtDesc(
            Long plotId, String signature, Collection<String> statuses);

    /**
     * Alertes ouvertes de la parcelle issues de la même voie de diagnostic mais
     * portant une autre empreinte : leur situation n'est plus celle qu'on observe.
     *
     * Le filtre par voie est essentiel — une parcelle peut légitimement porter
     * une alerte issue de l'image et une autre issue des capteurs. Les fermer
     * toutes parce qu'un diagnostic capteur vient de tourner effacerait l'alerte
     * image, qui, elle, reste d'actualité.
     */
    @Query("""
           select a from Alert a
           where a.plot.id = :plotId
             and a.status in :openStatuses
             and a.signature <> :currentSignature
             and a.signature like concat(:sourcePrefix, '%')
           """)
    List<Alert> findStaleOnSameSource(@Param("plotId") Long plotId,
                                      @Param("currentSignature") String currentSignature,
                                      @Param("sourcePrefix") String sourcePrefix,
                                      @Param("openStatuses") Collection<String> openStatuses);

    @Query("""
           select a from Alert a
           where (:plotId is null or a.plot.id = :plotId)
             and (:category is null or upper(a.category) = :category)
             and (:level is null or upper(a.level) = :level)
             and (:status is null or upper(a.status) = :status)
             and (:openOnly = false or a.status in :openStatuses)
             and a.createdAt >= :from
             and a.createdAt <= :to
           """)
    Page<Alert> search(@Param("plotId") Long plotId,
                       @Param("category") String category,
                       @Param("level") String level,
                       @Param("status") String status,
                       @Param("openOnly") boolean openOnly,
                       @Param("openStatuses") Collection<String> openStatuses,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       Pageable pageable);
}
