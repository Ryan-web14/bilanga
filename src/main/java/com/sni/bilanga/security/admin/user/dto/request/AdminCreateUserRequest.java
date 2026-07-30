package com.sni.bilanga.security.admin.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminCreateUserRequest {
    @Email
    @NotBlank
    private String email;
    private String firstname;
    private String lastname;

    /**
     * Numéro de téléphone, destinataire des alertes SMS.
     *
     * Facultatif, mais sans lui l'utilisateur restera injoignable par le seul
     * canal qui atteigne réellement le terrain. Accepté en forme locale
     * ({@code 06 123 45 67}) : la mise au format international est faite à
     * l'envoi, pas à la saisie.
     */
    @Size(max = 30, message = "Le numéro de téléphone ne peut dépasser 30 caractères")
    private String phone;

    private String password;
    private Boolean generatePassword;
    private List<String> roleNames;
}
