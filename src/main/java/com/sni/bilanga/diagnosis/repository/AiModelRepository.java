package com.sni.bilanga.diagnosis.repository;


import com.sni.bilanga.diagnosis.model.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    // Vision : distingué par culture
    Optional<AiModel> findByModelTypeAndCropName(String modelType, String cropName);

    // Tabulaire : pas de culture (crop_name NULL)
    Optional<AiModel> findFirstByModelType(String modelType);
}