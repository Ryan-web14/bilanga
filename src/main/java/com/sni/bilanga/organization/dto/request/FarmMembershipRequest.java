package com.sni.bilanga.organization.dto.request;

import com.sni.bilanga.enums.MembershipRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Rattachement d'une personne à une exploitation.
 *
 * <p>Le rôle est obligatoire et typé : c'est lui qui décide de ce que la
 * personne verra. Le laisser libre ou facultatif reviendrait à ouvrir l'accès
 * complet par défaut — exactement l'inverse de ce que cette table sert à faire.
 */
@Data
public class FarmMembershipRequest {

    @NotNull(message = "L'utilisateur est obligatoire")
    private Long userId;

    @NotNull(message = "Le rôle est obligatoire : c'est lui qui détermine l'accès")
    private MembershipRole role;
}
