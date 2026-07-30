package com.sni.bilanga.security.admin.role.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PermissionResponse  {

    private Long id;
    private String name;
    private String displayName;
    private String module;
    private String action;
    private String fullPermissionName;
    private Boolean isActive;
}
