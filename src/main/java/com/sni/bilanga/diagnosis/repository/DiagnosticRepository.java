package com.sni.bilanga.diagnosis.repository;


import com.sni.bilanga.diagnosis.model.Diagnostic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticRepository extends JpaRepository<Diagnostic, Long> {

    /**
     * Diagnostics anormaux récents sur les parcelles <strong>voisines</strong>
     * (V27, moteur de voisinage).
     *
     * <h2>Pourquoi une requête native</h2>
     *
     * <p>Elle calcule une distance géographique, ce que JPQL ne sait pas exprimer.
     * Deux choix s'imposaient : PostGIS, ou la trigonométrie en SQL. PostGIS a été
     * écarté — ajouter une extension géospatiale entière pour un rayon de deux
     * kilomètres serait exactement la dépendance surdimensionnée dont le projet
     * vient de se débarrasser.
     *
     * <h2>Le pré-filtre rectangulaire, et pourquoi il n'est pas cosmétique</h2>
     *
     * <p>La clause commence par un encadrement de {@code latitude} et
     * {@code longitude} par des bornes constantes. C'est ce qui permet au
     * planificateur d'utiliser {@code idx_plots_coordinates}. Sans lui, la formule
     * de distance apparaîtrait seule dans le {@code WHERE}, aucun index ne serait
     * exploitable, et <em>chaque diagnostic</em> balaierait toute la table des
     * parcelles — sur un chemin déclenché à chaque relevé reçu.
     *
     * <p>Le rectangle est volontairement un peu large : il retient des parcelles
     * situées dans les coins, au-delà du rayon. La formule de Haversine, appliquée
     * ensuite, les écarte. Un pré-filtre approximatif suivi d'un filtre exact est le
     * motif habituel, et le seul qui garde l'index utilisable.
     *
     * <h2>Ce que la requête rend</h2>
     *
     * <p>Une ligne par diagnostic anormal retenu :
     * {@code [diseaseCode, cropName, plotName, distanceKm, diagnosedAt, confidence]}.
     *
     * <p>La distance est rendue, et non seulement utilisée pour filtrer : le moteur
     * en tire la pondération, et le conseil peut dire « détecté à 800 m » — une
     * information qui change ce que l'exploitant fera, là où « détecté à proximité »
     * ne dit rien d'actionnable.
     *
     * @param excludedPlotId la parcelle examinée, exclue de ses propres voisins
     * @param radiusKm       rayon de recherche, en kilomètres
     * @param since          borne de fraîcheur : un mildiou détecté il y a trois
     *                       semaines n'annonce plus rien
     * @param minConfidence  confiance minimale du diagnostic voisin — un diagnostic
     *                       non fiable ne doit pas déclencher une alerte chez le
     *                       voisin, exactement comme il n'en déclenche pas chez lui
     */
    @Query(value = """
           select d.result           as disease_code,
                  d.crop_name        as crop_name,
                  n.name             as plot_name,
                  (6371 * acos(
                       least(1.0, greatest(-1.0,
                           cos(radians(:latitude)) * cos(radians(n.latitude))
                             * cos(radians(n.longitude) - radians(:longitude))
                           + sin(radians(:latitude)) * sin(radians(n.latitude))
                       ))
                  ))                 as distance_km,
                  d.diagnosed_at     as diagnosed_at,
                  d.confidence_score as confidence_score
           from diagnostics d
           join plots n on n.id = d.plot_id
           where n.id <> :excludedPlotId
             and n.latitude  is not null
             and n.longitude is not null
             -- Pré-filtre rectangulaire : c'est LUI qui rend idx_plots_coordinates
             -- utilisable. Un degré de latitude vaut ~111 km ; la longitude est
             -- corrigée par le cosinus de la latitude, sans quoi le rectangle
             -- serait trop étroit loin de l'équateur.
             and n.latitude  between :latitude  - (:radiusKm / 111.0)
                                 and :latitude  + (:radiusKm / 111.0)
             and n.longitude between :longitude - (:radiusKm / (111.0 * cos(radians(:latitude))))
                                 and :longitude + (:radiusKm / (111.0 * cos(radians(:latitude))))
             and upper(n.status) = 'ACTIVE'
             and d.diagnosed_at >= :since
             and d.result is not null
             and upper(d.result) <> 'NORMAL'
             and (d.confidence_score is null or d.confidence_score >= :minConfidence)
           order by d.diagnosed_at desc
           """, nativeQuery = true)
    List<Object[]> findNeighbourOutbreaks(@Param("excludedPlotId") Long excludedPlotId,
                                          @Param("latitude") double latitude,
                                          @Param("longitude") double longitude,
                                          @Param("radiusKm") double radiusKm,
                                          @Param("since") java.time.Instant since,
                                          @Param("minConfidence") double minConfidence);

    /**
     * Diagnostic <strong>issu de ce relevé précis</strong>, s'il en existe un.
     *
     * <p>La question exacte : « ce relevé a-t-il produit une conclusion ? ». La
     * réponse est <strong>non dans la grande majorité des cas</strong>, et ce n'est
     * pas un défaut : {@code DiagnosisThrottle} n'autorise un diagnostic qu'au-delà
     * d'un intervalle minimal et à condition qu'une mesure ait bougé, et trois motifs
     * d'abandon s'y ajoutent ({@code SONDE_DEFAILLANTE}, {@code CONTEXTE_ABSENT},
     * {@code ML_INDISPONIBLE}) — le relevé étant conservé dans tous les cas.
     *
     * <p>C'est pourquoi elle ne suffit pas seule : voir
     * {@link #findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc}.
     *
     * <p>Indexée par {@code idx_diagnostics_reading} (V5).
     */
    Optional<Diagnostic> findFirstByReading_Id(Long readingId);

    /**
     * Diagnostic <strong>en vigueur</strong> à un instant donné : le dernier antérieur.
     *
     * <p>C'est l'autre moitié de la question, et la plus utile. Elle ne demande pas
     * « ce relevé a-t-il conclu ? » mais « quelle conclusion l'exploitant avait-il
     * sous les yeux à ce moment-là ? » — à quoi la réponse est presque toujours oui.
     *
     * <p>Les deux ne disent pas la même chose, et l'appelant doit <strong>exposer
     * laquelle a répondu</strong> : confondre « diagnostic issu de ce relevé » et
     * « diagnostic contemporain de ce relevé » ferait attribuer à une mesure une
     * conclusion qu'elle n'a pas produite.
     *
     * <p>Indexée par {@code idx_diagnostics_plot_date} (V5).
     */
    Optional<Diagnostic> findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(
            Long plotId, Instant at);

    List<Diagnostic> findByPlot_IdOrderByDiagnosedAtDesc(Long plotId);

    List<Diagnostic> findByPlot_IdOrderByDiagnosedAtDesc(Long plotId, Pageable pageable);

    /** Dernier diagnostic de la parcelle : sert à réguler la production de nouveaux diagnostics. */
    Optional<Diagnostic> findFirstByPlot_IdOrderByDiagnosedAtDesc(Long plotId);

    /**
     * Historique paginé et filtrable.
     *
     * {@code minConfidence} permet d'écarter les diagnostics peu fiables — ceux
     * qui n'ont jamais levé d'alerte et qu'il est trompeur de présenter au même
     * rang que les autres.
     */
    @Query("""
           select d from Diagnostic d
           where (:plotId is null or d.plot.id = :plotId)
             and (:source is null or upper(d.source) = :source)
             and (:result is null or upper(d.result) = :result)
             and (:minConfidence is null
                  or (d.confidenceScore is not null and d.confidenceScore >= :minConfidence))
             and d.diagnosedAt >= :from
             and d.diagnosedAt <= :to
           """)
    Page<Diagnostic> search(@Param("plotId") Long plotId,
                            @Param("source") String source,
                            @Param("result") String result,
                            @Param("minConfidence") Double minConfidence,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);
}
