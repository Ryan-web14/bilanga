package com.sni.bilanga.organization.repository;

import com.sni.bilanga.organization.model.Cooperative;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CooperativeRepository extends JpaRepository<Cooperative, Long> {

    boolean existsByCode(String code);

    @Query(value = "select nextval('cooperative_code_seq')", nativeQuery = true)
    long nextCodeSequence();

    @Query("""
           select c from Cooperative c
           where (:status is null or upper(c.status) = :status)
             and (:term is null
                  or lower(c.name) like :term
                  or lower(c.location) like :term)
           """)
    Page<Cooperative> search(@Param("status") String status,
                             @Param("term") String term,
                             Pageable pageable);

    @Query("select count(f) from Farm f where f.cooperative.id = :cooperativeId")
    long countFarms(@Param("cooperativeId") Long cooperativeId);
}
