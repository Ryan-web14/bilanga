package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.RecommendationArbitration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationArbitrationRepository extends JpaRepository<RecommendationArbitration, Long> {

    @Query("""
           SELECT a FROM RecommendationArbitration a
           WHERE a.active = TRUE
             AND (a.cropName = :cropName OR a.cropName = '*')
           """)
    @Cacheable(CacheConfig.ARBITRATIONS)
    List<RecommendationArbitration> findForCrop(@Param("cropName") String cropName);
}