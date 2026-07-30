package com.sni.bilanga.farm.repository;

import com.sni.bilanga.farm.model.CropJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Lecture du journal de cycle.
 *
 * <p>Deux accès seulement, et ce sont exactement ceux que la V28 indexe :
 * {@code idx_crop_journal_crop (crop_id, changed_at DESC)} et
 * {@code idx_crop_journal_plot (plot_id, changed_at DESC)}.
 *
 * <p>Aucune méthode d'écriture au-delà de celles de {@code JpaRepository} : le seul
 * écrivain est {@code CropJournalWriter}. Un journal auquel plusieurs endroits
 * écriraient perdrait sa cohérence de format — et c'est précisément le format qui
 * permet de le relire.
 */
@Repository
public interface CropJournalRepository extends JpaRepository<CropJournal, Long> {

    /**
     * Le journal d'un cycle, du plus récent au plus ancien.
     *
     * <p>Non paginé : un cycle porte quelques dizaines d'entrées au plus — une par
     * modification, quatre ou cinq changements de stade, une clôture. Paginer
     * obligerait le client à un second appel pour un volume qui tient dans une
     * réponse.
     */
    List<CropJournal> findByCropIdOrderByChangedAtDesc(Long cropId);

    /**
     * Le journal d'une parcelle, <strong>toutes campagnes confondues</strong>.
     *
     * <p>Paginé, celui-là : une parcelle cumule les journaux de toutes ses campagnes
     * successives, et le volume croît sans borne avec les années.
     *
     * <p>C'est cet accès qui justifie la dénormalisation de {@code plot_id} dans la
     * table — sans elle, il faudrait joindre {@code crops} pour une lecture dont
     * l'axe naturel est la parcelle.
     */
    Page<CropJournal> findByPlotIdOrderByChangedAtDesc(Long plotId, Pageable pageable);

    /** Combien de fois ce cycle a été touché — utile à l'affichage d'un badge. */
    long countByCropId(Long cropId);
}
