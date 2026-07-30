package com.sni.bilanga.farm.repository;


import com.sni.bilanga.farm.model.Crop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CropRepository extends JpaRepository<Crop, Long> {

    List<Crop> findByPlot_Id(Long plotId);

    /**
     * Toutes les campagnes d'une parcelle, de la plus récente à la plus ancienne.
     *
     * <p>Base de l'historique de succession. <strong>Aucune migration nécessaire</strong> :
     * {@code idx_crops_plot_status_date (plot_id, status, planting_date DESC)} existe
     * depuis la V5 et sert cet accès — le préfixe {@code plot_id} suffit, le tri sur
     * {@code planting_date} est déjà dans l'index.
     */
    List<Crop> findByPlot_IdOrderByPlantingDateDesc(Long plotId);

    /**
     * La campagne <strong>précédente</strong> de la même culture sur la même parcelle.
     *
     * <p>C'est la comparaison qui a du sens agronomiquement : opposer une tomate à un
     * manioc ne dit rien, opposer deux tomates dit tout. La borne stricte
     * ({@code LessThan}) écarte la campagne examinée elle-même.
     *
     * <p><strong>Aucune colonne {@code previous_crop_id} n'est stockée</strong>, et c'est
     * délibéré : un pointeur se périme dès qu'on corrige une date de plantation ou qu'on
     * saisit après coup une campagne oubliée. Le tri, lui, reste juste.
     */
    Optional<Crop> findFirstByPlot_IdAndCropNameIgnoreCaseAndPlantingDateLessThanOrderByPlantingDateDesc(
            Long plotId, String cropName, java.time.LocalDate before);

    Optional<Crop> findFirstByPlot_IdAndStatusOrderByPlantingDateDesc(Long plotId, String status);

    /** Sert à refuser une seconde plantation en cours sur la même parcelle. */
    boolean existsByPlot_IdAndStatusAndIdNot(Long plotId, String status, Long id);

    boolean existsByPlot_IdAndStatus(Long plotId, String status);

    @Query("""
           select c from Crop c
           where (:plotId is null or c.plot.id = :plotId)
             and (:cropName is null or lower(c.cropName) = :cropName)
             and (:status is null or upper(c.status) = :status)
             and (:stage is null or upper(c.growthStage) = :stage)
           """)
    Page<Crop> search(@Param("plotId") Long plotId,
                      @Param("cropName") String cropName,
                      @Param("status") String status,
                      @Param("stage") String stage,
                      Pageable pageable);
}
