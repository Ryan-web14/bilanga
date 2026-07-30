package com.sni.bilanga.security.authentication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CurrentUserResponse {
    private String userId;
    private String email;
    private boolean accountEnabled;
    private List<String> authorities;
}
