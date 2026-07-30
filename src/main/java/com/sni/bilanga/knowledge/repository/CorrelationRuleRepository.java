package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.CorrelationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorrelationRuleRepository extends JpaRepository<CorrelationRule, Long> {

    /**
     * Règles de corrélation candidates pour une culture + maladie données.
     * Inclut les règles génériques (crop_name = '*' et/ou disease_code = '*').
     * Le filtrage final sur la valeur mesurée (operator/threshold) se fait
     * côté service.
     */
    @Cacheable(CacheConfig.CORRELATIONS)
    @Query("""
           SELECT c FROM CorrelationRule c
           WHERE (c.cropName = :cropName OR c.cropName = '*')
             AND (c.diseaseCode = :diseaseCode OR c.diseaseCode = '*')
           """)
    List<CorrelationRule> findCandidates(@Param("cropName") String cropName,
                                         @Param("diseaseCode") String diseaseCode);

}