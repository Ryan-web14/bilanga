package com.sni.bilanga.iot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Compte rendu d'un rejeu de relevés tamponnés.
 *
 * Le lot n'est pas atomique, et c'est voulu : un relevé corrompu au milieu de
 * la série ne doit pas faire perdre les cent autres. Chaque échec est signalé
 * individuellement pour que le boîtier sache quoi ne pas réémettre.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IngestBatchResult {

    private Integer received;
    private Integer accepted;
    private Integer rejected;

    /** Nombre de relevés ayant effectivement déclenché un diagnostic. */
    private Integer diagnosed;

    private List<IngestResult> results;

    private List<BatchFailure> failures;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class BatchFailure {

        /** Position dans le lot transmis, pour identifier le relevé fautif. */
        private Integer index;

        private String technicalId;
        private String errorCode;
        private String message;
    }
}
