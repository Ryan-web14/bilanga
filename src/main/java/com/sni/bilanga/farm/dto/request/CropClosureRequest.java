package com.sni.bilanga.farm.dto.request;

import com.sni.bilanga.enums.CropClosureReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Clôture d'une campagne.
 *
 * <p>Distinct de {@code DELETE /crops/{id}}, qui reste inchangé : celui-ci se contente
 * de passer le statut à {@code TERMINEE}, sans date réelle, sans motif et sans bilan.
 * Casser une route n'étant pas additif, la clôture riche arrive à côté.
 */
@Data
public class CropClosureRequest {

    /**
     * Pourquoi la campagne s'achève. <strong>Obligatoire.</strong>
     *
     * <p>C'est lui qui rend l'historique interprétable : un rendement nul après
     * {@code RECOLTE_NORMALE} signale un problème agronomique à chercher, le même
     * rendement nul après {@code PERTE_CLIMATIQUE} ne signale que la météo. Sans motif,
     * comparer deux campagnes revient à comparer deux chiffres dont on ignore ce qu'ils
     * mesurent.
     *
     * <p>Typé par l'énumération : une valeur hors vocabulaire est refusée à la
     * désérialisation, avec la liste des valeurs acceptées dans le message.
     */
    @NotNull(message = "Le motif de clôture est obligatoire")
    private CropClosureReason reason;

    /**
     * Date de fin <strong>réelle</strong>. Facultative : le jour de la saisie s'applique
     * à défaut.
     *
     * <p>Le repli est délibérément aujourd'hui et non {@code expectedHarvestDate} :
     * celle-ci est un objectif calculé, et l'employer comme constat enregistrerait une
     * prévision en la présentant comme un fait. Le jour de saisie est approximatif mais
     * vrai ; la date prévue serait précise et fausse.
     *
     * <p>Refusée si elle précède la plantation, ou si elle est dans le futur — une
     * campagne se clôt quand elle est finie, pas quand on prévoit qu'elle le sera.
     */
    private LocalDate actualEndDate;

    /**
     * Précision libre, à destination de celui qui relira la campagne dans un an.
     *
     * <p>C'est là que se trouve ce qu'aucun motif codifié ne peut porter : « grêle du
     * 12 juin, deux rangs épargnés » vaut mieux que {@code PERTE_CLIMATIQUE} seul.
     */
    @Size(max = 2000, message = "La note de clôture ne peut dépasser 2000 caractères")
    private String note;
}
