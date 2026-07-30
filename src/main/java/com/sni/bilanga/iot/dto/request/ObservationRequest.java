package com.sni.bilanga.iot.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ObservationRequest {

    @NotNull(message = "La parcelle est obligatoire")
    private Long plotId;

    private Long userId;
    private String note;
    private String photoUrl;
}