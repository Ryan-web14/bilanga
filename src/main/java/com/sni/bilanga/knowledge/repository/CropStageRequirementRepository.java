package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.CropStageRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CropStageRequirementRepository extends JpaRepository<CropStageRequirement, Long> {

    @Cacheable(CacheConfig.STAGE_REQUIREMENTS)
    Optional<CropStageRequirement> findByCropNameAndGrowthStage(String cropName, String growthStage);

    @Cacheable(CacheConfig.STAGE_REQUIREMENTS)
    List<CropStageRequirement> findByCropNameOrderByGrowthStage(String cropName);
}
