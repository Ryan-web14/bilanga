package com.sni.bilanga.iot.repository;


import com.sni.bilanga.iot.model.Sensor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {

    List<Sensor> findByDevice_Id(Long deviceId);

    @Query("""
           select s from Sensor s
           where (:deviceId is null or s.device.id = :deviceId)
             and (:plotId is null or s.device.plot.id = :plotId)
             and (:status is null or upper(s.status) = :status)
             and (:sensorType is null or upper(s.sensorType) = :sensorType)
           """)
    Page<Sensor> search(@Param("deviceId") Long deviceId,
                        @Param("plotId") Long plotId,
                        @Param("status") String status,
                        @Param("sensorType") String sensorType,
                        Pageable pageable);
}