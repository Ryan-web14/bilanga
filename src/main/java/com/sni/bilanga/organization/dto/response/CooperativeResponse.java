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
public class CooperativeResponse {

    private Long id;
    private String code;
    private String name;
    private String location;
    private String contactPhone;

    private String status;
    private String statusLabel;

    /** Exploitations rattachées. Compteur : reste un nombre en JSON. */
    @JsonSerialize(using = CounterSerializer.class)
    private Long farmCount;

    private Instant createdAt;
    private Instant updatedAt;
}
