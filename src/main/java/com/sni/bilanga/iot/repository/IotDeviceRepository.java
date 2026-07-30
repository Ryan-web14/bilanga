package com.sni.bilanga.iot.repository;


import com.sni.bilanga.iot.model.IotDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IotDeviceRepository extends JpaRepository<IotDevice, Long> {

    List<IotDevice> findByPlot_Id(Long plotId);

    Optional<IotDevice> findByTechnicalId(String technicalId);

    boolean existsByTechnicalId(String technicalId);

    /** Parc en service, hors matériel retiré. */
    @Query("select count(d) from IotDevice d where upper(coalesce(d.status, 'ACTIVE')) <> 'RETIRE'")
    long countInService();

    /** Boîtiers dont la charge est passée sous le seuil de vigilance. */
    @Query("""
           select count(d) from IotDevice d
           where upper(coalesce(d.status, 'ACTIVE')) <> 'RETIRE'
             and d.batteryLevel is not null
             and d.batteryLevel <= :threshold
           """)
    long countLowBattery(@Param("threshold") int threshold);

    @Query("""
           select d from IotDevice d
           where (:plotId is null or d.plot.id = :plotId)
             and (:status is null or upper(d.status) = :status)
             and (:maxBattery is null or (d.batteryLevel is not null and d.batteryLevel <= :maxBattery))
             and (:term is null
                  or lower(d.technicalId) like :term
                  or lower(d.deviceName) like :term)
           """)
    Page<IotDevice> search(@Param("plotId") Long plotId,
                           @Param("status") String status,
                           @Param("maxBattery") Integer maxBattery,
                           @Param("term") String term,
                           Pageable pageable);
}