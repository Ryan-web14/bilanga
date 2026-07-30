package com.sni.bilanga.security.admin.role.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PermissionRequest {

    private String name;
    private String displayName;
    private String module;
    private String action;
    private Boolean isActive;

}
