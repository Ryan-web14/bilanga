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

}