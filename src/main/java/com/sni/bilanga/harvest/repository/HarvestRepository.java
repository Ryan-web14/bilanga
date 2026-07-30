package com.sni.bilanga.harvest.repository;

import com.sni.bilanga.harvest.model.Harvest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HarvestRepository extends JpaRepository<Harvest, Long> {

    @Query("""
           select h from Harvest h
           where (:plotId is null or h.plot.id = :plotId)
             and (:cropId is null or h.crop.id = :cropId)
             and h.harvestedAt >= :from
             and h.harvestedAt <= :to
           """)
    Page<Harvest> search(@Param("plotId") Long plotId,
                         @Param("cropId") Long cropId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);

    /**
     * Récoltes d'une fenêtre, pour le calcul de marge.
     *
     * Les lignes sont rapatriées plutôt qu'agrégées en base : le produit brut est
     * {@code Σ quantité × prix unitaire}, une multiplication colonne à colonne
     * que SQL ferait aussi bien, mais on a besoin en parallèle du détail par
     * qualité et par unité — qui obligerait à plusieurs agrégats séparés. Une
     * campagne compte quelques dizaines de récoltes au plus.
     */
    @Query("""
           select h from Harvest h
           where h.plot.id = :plotId
             and (:cropId is null or h.crop.id = :cropId)
             and h.harvestedAt >= :from
             and h.harvestedAt <= :to
           order by h.harvestedAt asc
           """)
    List<Harvest> findForPeriod(@Param("plotId") Long plotId,
                                @Param("cropId") Long cropId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);
}
