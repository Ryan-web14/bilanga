package com.sni.bilanga.farm.repository;


import com.sni.bilanga.farm.model.Plot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlotRepository extends JpaRepository<Plot, Long> {

    List<Plot> findByUser_Id(Long userId);

    boolean existsByPlotCode(String plotCode);

    /**
     * Numéro suivant de la référence lisible des parcelles.
     *
     * <p>Une séquence de base, et non un {@code count()} applicatif : deux
     * créations simultanées liraient le même total et produiraient le même code,
     * que l'index unique rejetterait ensuite. La séquence est créée par la
     * migration V16 et n'est jamais rejouée en arrière — un trou dans la
     * numérotation après une transaction annulée est sans conséquence, ce code
     * n'est qu'une référence de communication.
     */
    @Query(value = "select nextval('plot_code_seq')", nativeQuery = true)
    long nextPlotCodeSequence();

    /**
     * Recherche paginée à critères facultatifs.
     *
     * Chaque critère nul est neutre : un seul appel couvre « toutes les
     * parcelles », « celles d'un exploitant », « les archivées », ou n'importe
     * quelle combinaison — au lieu d'une méthode de dépôt par croisement.
     */
    /**
     * @param farmIds       exploitations dont l'appelant est membre
     * @param hasFarmScope  faux quand {@code farmIds} ne doit pas peser — la
     *                      liste est alors une valeur muette. Un {@code in ()}
     *                      sur collection vide n'est pas du SQL valide, et un
     *                      drapeau explicite vaut mieux qu'un test de taille
     *                      caché dans la requête
     */
    @Query("""
           select p from Plot p
           where ((:userId is null or p.user.id = :userId)
                  or (:hasFarmScope = true and p.farm.id in :farmIds))
             and (:status is null or upper(p.status) = :status)
             and (:soilType is null or upper(p.soilType) = :soilType)
             and (:term is null
                  or lower(p.name) like :term
                  or lower(p.location) like :term)
           """)
    Page<Plot> search(@Param("userId") Long userId,
                      @Param("hasFarmScope") boolean hasFarmScope,
                      @Param("farmIds") java.util.Collection<Long> farmIds,
                      @Param("status") String status,
                      @Param("soilType") String soilType,
                      @Param("term") String term,
                      Pageable pageable);

    /**
     * État de chaque parcelle en <strong>une</strong> requête.
     *
     * <p>La vue d'ensemble interrogeait la base plusieurs fois par parcelle —
     * dernier relevé, alertes, culture, risques, dernier diagnostic, boîtiers.
     * Sur cinquante parcelles, cela faisait plusieurs centaines d'allers-retours
     * pour afficher un tableau. Ici, les jointures agrégées font le travail une
     * fois, et le classement se fait en mémoire sur quelques dizaines de lignes.
     *
     * <p>Colonnes : identifiant, nom, alertes ouvertes, alertes critiques
     * ouvertes, dernier diagnostic anormal, présence d'au moins un relevé,
     * horodatage du dernier relevé.
     */
    @Query(value = """
           select p.id,
                  p.name,
                  coalesce(a.open_count, 0)                                as open_alerts,
                  coalesce(a.critical_count, 0)                            as critical_alerts,
                  (ld.result is not null and upper(ld.result) <> 'NORMAL')  as abnormal_diagnosis,
                  (r.last_reading_at is not null)                           as has_reading,
                  r.last_reading_at
           from plots p
           left join (
                  select plot_id,
                         count(*)                                          as open_count,
                         count(*) filter (where upper(level) = 'CRITIQUE')  as critical_count
                  from alerts
                  where status in ('NOUVELLE', 'ACQUITTEE')
                  group by plot_id
           ) a on a.plot_id = p.id
           left join lateral (
                  select d.result
                  from diagnostics d
                  where d.plot_id = p.id
                  order by d.diagnosed_at desc
                  limit 1
           ) ld on true
           left join (
                  select plot_id, max(recorded_at) as last_reading_at
                  from sensor_readings
                  group by plot_id
           ) r on r.plot_id = p.id
           where upper(coalesce(p.status, 'ACTIVE')) <> 'ARCHIVEE'
             and (:userId is null or p.user_id = :userId)
           """, nativeQuery = true)
    List<Object[]> aggregateFarmState(@Param("userId") Long userId);
}
