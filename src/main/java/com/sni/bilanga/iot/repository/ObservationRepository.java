package com.sni.bilanga.iot.repository;


import com.sni.bilanga.iot.model.Observation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findByPlot_IdOrderByObservedAtDesc(Long plotId);

    @Query("""
           select o from Observation o
           where (:plotId is null or o.plot.id = :plotId)
             and (:userId is null or o.user.id = :userId)
             and o.observedAt >= :from
             and o.observedAt <= :to
           """)
    Page<Observation> search(@Param("plotId") Long plotId,
                             @Param("userId") Long userId,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}