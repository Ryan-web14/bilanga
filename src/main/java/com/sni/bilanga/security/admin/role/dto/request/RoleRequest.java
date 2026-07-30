package com.sni.bilanga.security.admin.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RoleRequest {

    @NotBlank(message = "the name of the role is required")
    @Size(max = 250, message = "the name of the role must not exceed 250 characters")
    @Pattern(regexp = "^[A-Z_]+$", message = "Role name must contain only uppercase letters and underscores")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 150, message = "Display name must not exceed 100 characters")
    private String displayName;

    private String description;

    private Boolean isActive;

}
