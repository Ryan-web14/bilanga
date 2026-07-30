package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.DiseaseRiskCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiseaseRiskConditionRepository extends JpaRepository<DiseaseRiskCondition, Long> {

    @Cacheable(CacheConfig.RISK_CONDITIONS)
    @Query("""
           SELECT c FROM DiseaseRiskCondition c
           WHERE c.active = TRUE
             AND (c.cropName = :cropName OR c.cropName = '*')
           """)
    List<DiseaseRiskCondition> findForCrop(@Param("cropName") String cropName);

    @Cacheable(CacheConfig.RISK_CONDITIONS)
    List<DiseaseRiskCondition> findByCropNameAndDiseaseCode(String cropName, String diseaseCode);
}