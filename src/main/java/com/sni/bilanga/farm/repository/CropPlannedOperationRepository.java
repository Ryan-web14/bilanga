package com.sni.bilanga.farm.repository;

import com.sni.bilanga.farm.model.CropPlannedOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CropPlannedOperationRepository extends JpaRepository<CropPlannedOperation, Long> {

    /**
     * L'itinéraire d'une campagne.
     *
     * <p>Trié sur {@code plannedOn} — les opérations datées seulement en {@code J+n}
     * n'ont pas de date en base et remontent donc en fin de liste. Le tri définitif se
     * fait après résolution, dans le mapper : c'est le seul endroit où
     * {@code plantingDate} est disponible.
     *
     * <p>Non paginé : un itinéraire compte quelques dizaines de lignes, et le paginer
     * obligerait le client à recomposer une séquence qui n'a de sens qu'entière.
     */
    List<CropPlannedOperation> findByCrop_IdOrderByPlannedOnAsc(Long cropId);

    /** Une intervention ne peut satisfaire qu'une opération — index unique partiel (V29). */
    Optional<CropPlannedOperation> findByIntervention_Id(Long interventionId);

    long countByCrop_Id(Long cropId);
}
