package com.sni.bilanga.security.admin.provisioning.controller;


import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.security.admin.provisioning.service.interfaces.UserProvisioningService;
import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.dto.response.UserResponse;
import com.sni.bilanga.security.admin.user.service.interfaces.UserService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Amorçage des comptes.
 *
 * <p>Ces deux routes n'ont pas le même statut, et il importe de ne pas les
 * confondre.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/provisioning")
public class UserProvisioningController {

    private final UserProvisioningService userProvisioningService;
    private final UserService userService;

    /**
     * Crée le tout premier administrateur.
     *
     * <p><strong>Volontairement sans autorisation</strong> — et c'est la seule
     * route du système dans ce cas. Au premier démarrage aucun compte n'existe :
     * exiger une permission pour créer le compte qui les délivre serait un
     * cercle sans issue.
     *
     * <p>Ce qui rend cette ouverture acceptable est le garde-fou du service :
     * la route <strong>refuse de s'exécuter dès qu'un administrateur existe</strong>
     * (409). Sa fenêtre d'exposition est donc l'intervalle entre le premier
     * démarrage et le premier appel — après quoi elle est close définitivement.
     */
    @PostMapping("/bootstrap-admin")
    public ResponseEntity<ApiResponse<UserResponse>> bootstrapAdmin(@Valid @RequestBody UserRequest request) {
        var user = userProvisioningService.initializeGlobalAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Administrateur initial créé. Cette route est désormais close.",
                        userService.getUserByEmail(user.getEmail())));
    }

    /**
     * Crée un compte de personnel interne (rôle {@code STAFF}).
     *
     * <p><strong>Gardée</strong>, contrairement à la précédente : elle ne
     * participe pas à l'amorçage, et rien ne justifiait qu'un anonyme puisse
     * créer des comptes internes en série. Le raccourci du cercle sans issue ne
     * s'applique qu'au tout premier administrateur.
     */
    @PostMapping("/staff")
    @Audited(module = "USER", action = "CREATE", ressource = "staff")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<UserResponse>> createStaff(@Valid @RequestBody UserRequest request, Authentication authentication) {
        var user = userProvisioningService.createStaff(request, actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.getUserByEmail(user.getEmail())));
    }

    private String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "SYSTEM"
                : authentication.getName();
    }
}
