package com.sni.bilanga.knowledge.repository;


import com.sni.bilanga.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.sni.bilanga.knowledge.model.KnowledgeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeRuleRepository extends JpaRepository<KnowledgeRule, Long> {


    @Cacheable(CacheConfig.RULES)
    @Query("""
           SELECT k FROM KnowledgeRule k
           WHERE k.category = :category
             AND (k.cropName = :cropName OR k.cropName = '*')
           """)
    List<KnowledgeRule> findForDiagnostic(@Param("category") String category,
                                          @Param("cropName") String cropName);

    List<KnowledgeRule> findByCategory(String category);

}