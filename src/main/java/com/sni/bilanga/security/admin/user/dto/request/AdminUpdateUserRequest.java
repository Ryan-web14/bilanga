package com.sni.bilanga.security.admin.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminUpdateUserRequest {
    @Email
    private String email;
    private String firstname;
    private String lastname;

    /** Destinataire des alertes SMS ; accepté en forme locale. */
    @Size(max = 30, message = "Le numéro de téléphone ne peut dépasser 30 caractères")
    private String phone;

    private Boolean isAccountEnabled;
    private Boolean isAccountLocked;
    private List<String> roleNames;
}
