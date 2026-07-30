package com.sni.bilanga.organization.repository;

import com.sni.bilanga.organization.model.Farm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmRepository extends JpaRepository<Farm, Long> {

    boolean existsByCode(String code);

    @Query(value = "select nextval('farm_code_seq')", nativeQuery = true)
    long nextCodeSequence();

    /** Recherche paginée à critères facultatifs, sur le modèle de {@code PlotRepository}. */
    @Query("""
           select f from Farm f
           where (:cooperativeId is null or f.cooperative.id = :cooperativeId)
             and (:ownerId is null or f.owner.id = :ownerId)
             and (:status is null or upper(f.status) = :status)
             and (:term is null
                  or lower(f.name) like :term
                  or lower(f.location) like :term)
           """)
    Page<Farm> search(@Param("cooperativeId") Long cooperativeId,
                      @Param("ownerId") Long ownerId,
                      @Param("status") String status,
                      @Param("term") String term,
                      Pageable pageable);

    /**
     * Décompte des parcelles rattachées, pour la vue de liste.
     *
     * Une exploitation sans parcelle n'est pas une anomalie — elle vient d'être
     * créée — mais c'est une information que la liste doit montrer, sans quoi
     * l'utilisateur ne comprend pas pourquoi elle n'apparaît nulle part ailleurs.
     */
    @Query("select count(p) from Plot p where p.farm.id = :farmId")
    long countPlots(@Param("farmId") Long farmId);

    @Query("select count(m) from FarmMembership m where m.farm.id = :farmId")
    long countMembers(@Param("farmId") Long farmId);
}
