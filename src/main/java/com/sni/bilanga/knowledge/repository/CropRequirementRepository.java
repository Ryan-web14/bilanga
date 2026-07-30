package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.CropRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CropRequirementRepository extends JpaRepository<CropRequirement, Long> {

    @Cacheable(CacheConfig.CROP_REQUIREMENTS)
    Optional<CropRequirement> findByCropName(String cropName);

}