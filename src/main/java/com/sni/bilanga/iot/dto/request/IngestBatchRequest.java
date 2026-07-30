package com.sni.bilanga.iot.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Lot de relevés tamponnés par un boîtier pendant une coupure réseau.
 *
 * Un relevé par requête HTTP suppose une liaison permanente. Sur le terrain,
 * la connexion tombe : le boîtier accumule ses mesures et doit pouvoir les
 * rejouer d'un coup au retour du réseau — chacune avec son {@code recordedAt}
 * d'origine, sans quoi toute la série s'écrase sur l'instant de reconnexion.
 *
 * <p>La taille est bornée : un boîtier resté une semaine hors ligne enverrait
 * autrement des milliers de relevés en une requête, chacun susceptible de
 * déclencher un diagnostic.
 */
@Data
public class IngestBatchRequest {

    @NotEmpty(message = "Le lot ne peut pas être vide")
    @Size(max = 200, message = "Un lot ne peut dépasser 200 relevés")
    @Valid
    private List<IngestReadingRequest> readings;
}
