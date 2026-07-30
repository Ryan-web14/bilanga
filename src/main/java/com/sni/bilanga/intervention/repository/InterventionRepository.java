package com.sni.bilanga.intervention.repository;

import com.sni.bilanga.intervention.model.Intervention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByPlot_IdOrderByPerformedAtDesc(Long plotId, Pageable pageable);

    /**
     * Recherche paginée à critères facultatifs, sur le modèle des autres
     * domaines : un seul appel couvre toutes les combinaisons.
     */
    @Query("""
           select i from Intervention i
           where (:plotId is null or i.plot.id = :plotId)
             and (:cropId is null or i.crop.id = :cropId)
             and (:type is null or upper(i.type) = :type)
             and i.performedAt >= :from
             and i.performedAt <= :to
           """)
    Page<Intervention> search(@Param("plotId") Long plotId,
                              @Param("cropId") Long cropId,
                              @Param("type") String type,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

    /**
     * Charges agrégées par type, sur une campagne ou une fenêtre.
     *
     * <p>Agréger en base plutôt que rapatrier les lignes : l'analyse économique
     * porte sur une saison entière, et le total est ce qui intéresse — pas le
     * détail de chaque passage.
     *
     * <p>Colonnes : type, somme des coûts, nombre d'interventions.
     */
    @Query("""
           select i.type, coalesce(sum(i.cost), 0), count(i)
           from Intervention i
           where i.plot.id = :plotId
             and (:cropId is null or i.crop.id = :cropId)
             and i.performedAt >= :from
             and i.performedAt <= :to
           group by i.type
           """)
    List<Object[]> aggregateCostByType(@Param("plotId") Long plotId,
                                       @Param("cropId") Long cropId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    /** Interventions suivant un conseil donné : « ce conseil a-t-il été suivi ? ». */
    List<Intervention> findByRecommendation_Id(Long recommendationId);

    /**
     * Interventions d'une campagne, dans l'ordre chronologique.
     *
     * <p>Base du rapprochement avec l'itinéraire technique (V29). Non paginé : une
     * campagne compte quelques dizaines de passages, et l'appariement est un problème
     * global — il ne se résout pas page par page.
     */
    List<Intervention> findByCrop_IdOrderByPerformedAtAsc(Long cropId);
}
