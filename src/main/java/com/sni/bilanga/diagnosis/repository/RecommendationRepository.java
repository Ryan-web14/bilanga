package com.sni.bilanga.diagnosis.repository;


import com.sni.bilanga.diagnosis.model.Recommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByDiagnostic_Id(Long diagnosticId);

    /**
     * Recherche paginée, principalement pour le suivi : quels conseils ont été
     * appliqués, lesquels sont restés lettre morte.
     */
    @Query("""
           select r from Recommendation r
           where (:plotId is null or r.diagnostic.plot.id = :plotId)
             and (:diagnosticId is null or r.diagnostic.id = :diagnosticId)
             and (:status is null or upper(r.status) = :status)
             and (:priority is null or upper(r.priority) = :priority)
             and (:type is null or upper(r.recommendationType) = :type)
             and r.createdAt >= :from
             and r.createdAt <= :to
           """)
    Page<Recommendation> search(@Param("plotId") Long plotId,
                                @Param("diagnosticId") Long diagnosticId,
                                @Param("status") String status,
                                @Param("priority") String priority,
                                @Param("type") String type,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable pageable);

    /**
     * Taux d'application par type de règle : la mesure la plus directe de la
     * pertinence du moteur. Un type systématiquement ignoré signale une règle
     * à revoir, ce qu'aucune donnée ne permettait de voir jusqu'ici.
     */
    @Query("""
           select r.recommendationType,
                  count(r),
                  sum(case when upper(r.status) = 'APPLIQUEE' then 1 else 0 end),
                  sum(case when upper(r.status) = 'IGNOREE'   then 1 else 0 end)
           from Recommendation r
           where (:plotId is null or r.diagnostic.plot.id = :plotId)
           group by r.recommendationType
           """)
    List<Object[]> uptakeByType(@Param("plotId") Long plotId);

    /**
     * Total et nombre d'appliqués sur une fenêtre, pour le bilan économique.
     *
     * <p>Deux nombres et non la liste : le bilan met en regard un taux de suivi
     * et un rendement, il n'a que faire du détail des conseils. Rapatrier des
     * centaines de lignes pour en compter deux serait absurde.
     *
     * <p>Colonnes : total, nombre d'appliqués. Une seule ligne, toujours : un
     * agrégat sans {@code group by} en produit une même sur un ensemble vide.
     *
     * <h3>⚠️ Pourquoi {@code List<Object[]>} et non {@code Object[]}</h3>
     *
     * <p>La signature naturelle serait {@code Object[]}, puisqu'on attend une ligne
     * unique. Elle est piégeuse : Spring Data voit un <strong>tableau</strong>, en
     * déduit un retour de collection, et rend donc un {@code Object[]} dont chaque
     * élément est lui-même la ligne. L'appelant qui lit {@code résultat[1]} pour
     * obtenir la deuxième colonne lit en réalité la <em>deuxième ligne</em>, et lève
     * un {@code ArrayIndexOutOfBoundsException} sur un résultat d'une seule ligne.
     *
     * <p>C'est-à-dire : systématiquement, et pour toute parcelle. La route
     * {@code /plots/{id}/economics} a répondu 500 en production tant que cette
     * signature a tenu. Les tests unitaires ne pouvaient rien y voir, la couche
     * fautive étant celle que le bouchon remplace.
     */
    @Query("""
           select count(r),
                  sum(case when upper(r.status) = 'APPLIQUEE' then 1 else 0 end)
           from Recommendation r
           where r.diagnostic.plot.id = :plotId
             and r.createdAt >= :from
             and r.createdAt <= :to
           """)
    List<Object[]> uptakeSummary(@Param("plotId") Long plotId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);
}
