package com.sni.bilanga.security.authentication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SucessOttResponse {
    private String accessToken;
    private String refreshToken;

}
