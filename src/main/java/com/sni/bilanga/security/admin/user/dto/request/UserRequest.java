package com.sni.bilanga.security.admin.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UserRequest {

    @Email
    @NotBlank
    private String email;

    private String firstname;

    private String lastname;

    @NotBlank
    private String password;

    private boolean generatePassword;
}
