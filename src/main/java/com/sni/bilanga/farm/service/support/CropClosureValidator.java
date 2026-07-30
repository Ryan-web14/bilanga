package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.CropClosureReason;
import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.farm.model.Crop;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Ce qu'une clôture doit satisfaire pour être enregistrable.
 *
 * <p>Séparé du service, sans état ni transaction : les règles de clôture sont la seule
 * partie de l'opération qui mérite d'être éprouvée isolément, et elles le sont sans
 * base de données.
 *
 * <p><strong>Chaque refus porte un message rédigé pour l'utilisateur.</strong> Le
 * projet expose les {@code BusinessRuleException} en 400 avec leur message tel quel :
 * « date invalide » obligerait l'exploitant à deviner laquelle et pourquoi.
 */
@Component
public class CropClosureValidator {

    /**
     * Vérifie qu'un cycle peut être clos, et que la clôture demandée est cohérente.
     *
     * @param actualEndDate date de fin réelle ; {@code null} sera remplacé par
     *                      aujourd'hui par l'appelant
     * @throws BusinessRuleException sur toute règle violée
     */
    public void validate(Crop crop, LocalDate actualEndDate, CropClosureReason reason) {
        requireOpen(crop);
        requireReason(reason);
        requireCoherentEndDate(crop, actualEndDate);
    }

    /**
     * Une campagne déjà close ne se referme pas.
     *
     * <p>Sans ce contrôle, une seconde clôture écraserait {@code closedAt},
     * {@code closureReason} et — le plus grave — le <strong>bilan figé</strong>, qui
     * n'est censé être écrit qu'une seule fois. Tout l'intérêt d'un instantané est
     * qu'il ne bouge plus ; le réécrire en ferait un total mis en cache, c'est-à-dire
     * exactement ce que le contrat de {@code MarginCalculator} interdit.
     */
    private void requireOpen(Crop crop) {
        if (crop.getStatus() != null
                && CropStatus.TERMINEE.name().equalsIgnoreCase(crop.getStatus())) {

            throw new BusinessRuleException(
                    "Cette campagne est déjà terminée. Une clôture ne se rejoue pas : "
                    + "elle fige un bilan à une date, et le réécrire ferait perdre "
                    + "l'état arrêté à la première clôture.");
        }
    }

    /**
     * Le motif n'est jamais facultatif.
     *
     * <p>C'est lui qui rend l'historique interprétable : un rendement nul après
     * {@code RECOLTE_NORMALE} signale un problème agronomique à chercher, le même
     * rendement nul après {@code PERTE_CLIMATIQUE} ne signale que la météo. Sans motif,
     * comparer deux campagnes revient à comparer deux chiffres dont on ignore ce qu'ils
     * mesurent — et c'est précisément le défaut que la clôture riche corrige.
     */
    private void requireReason(CropClosureReason reason) {
        if (reason == null) {
            throw new BusinessRuleException(
                    "Le motif de clôture est obligatoire. Sans lui, un rendement faible "
                    + "ne se distingue pas d'une perte, et les campagnes ne se comparent "
                    + "plus entre elles.");
        }
    }

    /**
     * La fin ne précède pas le début, et n'est pas dans le futur.
     *
     * <p>Les deux erreurs sont symétriques mais pas de même nature. Une fin antérieure
     * à la plantation est une incohérence pure. Une fin dans le futur est plus
     * insidieuse : elle passerait les contrôles de base, puis ferait apparaître la
     * campagne comme close alors qu'elle pousse encore — et le bilan figé compterait
     * des récoltes qui n'ont pas eu lieu.
     */
    private void requireCoherentEndDate(Crop crop, LocalDate actualEndDate) {
        if (actualEndDate == null) {
            return;   // l'appelant appliquera la date du jour
        }

        if (actualEndDate.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "La date de fin ne peut pas être dans le futur : une campagne se clôt "
                    + "quand elle est finie, pas quand on prévoit qu'elle le sera.");
        }

        if (crop.getPlantingDate() != null && actualEndDate.isBefore(crop.getPlantingDate())) {
            throw new BusinessRuleException(String.format(
                    "La date de fin (%s) précède la date de plantation (%s).",
                    actualEndDate, crop.getPlantingDate()));
        }
    }

    /**
     * Date de fin effective : celle demandée, ou aujourd'hui.
     *
     * <p>Le repli est délibérément la date du jour et non {@code expectedHarvestDate} :
     * celle-ci est un <em>objectif calculé</em>, et l'utiliser comme constat ferait
     * enregistrer une prévision en la présentant comme un fait. Le jour de la saisie
     * est approximatif mais vrai ; la date prévue serait précise et fausse.
     */
    public LocalDate effectiveEndDate(LocalDate requested) {
        return requested != null ? requested : LocalDate.now();
    }
}
