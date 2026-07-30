package com.sni.bilanga.organization.dto.response;

import com.sni.bilanga.utils.json.CounterSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FarmResponse {

    private Long id;
    private String code;
    private String name;
    private String location;
    private String contactPhone;

    /** Nuls pour une exploitation indépendante : c'est un cas normal. */
    private Long cooperativeId;
    private String cooperativeName;

    private Long ownerUserId;
    private String ownerName;

    private String status;
    private String statusLabel;

    /**
     * Parcelles rattachées. Zéro n'est pas une anomalie — l'exploitation vient
     * d'être créée — mais c'est une information que la liste doit montrer, sans
     * quoi on ne comprend pas pourquoi elle n'apparaît nulle part ailleurs.
     */
    @JsonSerialize(using = CounterSerializer.class)
    private Long plotCount;

    @JsonSerialize(using = CounterSerializer.class)
    private Long memberCount;

    private Instant createdAt;
    private Instant updatedAt;
}
