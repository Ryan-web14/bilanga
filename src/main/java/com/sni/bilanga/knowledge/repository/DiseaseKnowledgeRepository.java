package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.DiseaseKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiseaseKnowledgeRepository extends JpaRepository<DiseaseKnowledge, Long> {


    @Cacheable(CacheConfig.DISEASES)
    Optional<DiseaseKnowledge> findByCropNameAndDiseaseCode(String cropName, String diseaseCode);

    /**
     * Repli d'étiquetage quand la culture du diagnostic n'a pas pu être résolue.
     *
     * <p>Un code de maladie est presque toujours propre à une espèce ; les rares
     * exceptions ({@code healthy}) sont traitées en amont par la recherche avec
     * culture, qui a la priorité. Ce repli sert à nommer plutôt qu'à laisser un code
     * anglais à l'écran.
     */
    @Cacheable(CacheConfig.DISEASES)
    Optional<DiseaseKnowledge> findFirstByDiseaseCodeIgnoreCase(String diseaseCode);

}