package com.sni.bilanga.iot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ObservationResponse {

    private Long id;
    private Long plotId;
    private String plotName;
    private Long userId;
    private String note;
    private String photoUrl;
    private Instant observedAt;
}
 