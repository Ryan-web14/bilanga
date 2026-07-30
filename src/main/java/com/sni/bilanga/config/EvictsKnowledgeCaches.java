package com.sni.bilanga.config;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Vide les caches de la base de connaissance.
 *
 * <p>À poser sur toute méthode qui modifie une table de connaissance. Sans elle,
 * un seuil corrigé depuis la console d'administration resterait sans effet sur
 * les diagnostics jusqu'au redémarrage — le pire des défauts de cache, puisque
 * l'administrateur voit sa modification enregistrée et constate qu'elle ne
 * change rien.
 *
 * <p>L'éviction est volontairement grossière : tous les caches pour toute
 * écriture. Ces écritures sont rares — quelques-unes par mois — et cibler
 * finement introduirait un risque d'incohérence pour un gain nul.
 *
 * <p>Le regroupement en une annotation n'est pas cosmétique : la liste des
 * caches était recopiée sur chaque méthode, et un cache ajouté plus tard aurait
 * dû être reporté à vingt-et-un endroits, avec la quasi-certitude d'en oublier.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(value = {
        CacheConfig.CROP_REQUIREMENTS,
        CacheConfig.STAGE_REQUIREMENTS,
        CacheConfig.RULES,
        CacheConfig.DISEASES,
        CacheConfig.RISK_CONDITIONS,
        CacheConfig.CORRELATIONS,
        CacheConfig.ARBITRATIONS
}, allEntries = true)
public @interface EvictsKnowledgeCaches {
}
