package com.sni.bilanga.security.admin.role.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RoleResponse {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private Boolean isActive;
    private Map<String, String> permissions;
}
