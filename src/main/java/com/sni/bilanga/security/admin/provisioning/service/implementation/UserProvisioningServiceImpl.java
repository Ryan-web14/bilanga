package com.sni.bilanga.security.admin.provisioning.service.implementation;


import com.sni.bilanga.exception.customs.ConflictException;
import com.sni.bilanga.security.admin.provisioning.service.interfaces.UserProvisioningService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleUserService;
import com.sni.bilanga.security.authorization.SecurityRole;
import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private static final String SYSTEM_ASSIGNER = "SYSTEM";
    private static final String ADMIN_ROLE = SecurityRole.ADMIN.name();

    /**
     * Rôle attribué à un compte créé sans rôle explicite.
     *
     * {@code EXPLOITANT} et non l'ancien {@code STAFF} : le moindre privilège
     * est le bon défaut, et « personnel » ne désignait rien sur une plateforme
     * agricole.
     */
    private static final String DEFAULT_ROLE = SecurityRole.DEFAULT.name();

    private final UserService userService;
    private final RoleUserService roleUserService;

    /**
     * Crée le tout premier administrateur, une fois et une seule.
     *
     * <p><strong>Pourquoi cette route n'est pas gardée par une permission.</strong>
     * Au premier démarrage, aucun compte n'existe : exiger une autorisation pour
     * créer le compte qui les délivre serait un cercle sans issue. C'est le
     * problème classique de l'amorçage, et il n'a que deux solutions — un compte
     * semé par migration, ou une route qui s'auto-condamne.
     *
     * <p><strong>Pourquoi la seconde a été retenue.</strong> Un compte semé par
     * migration porte un mot de passe qui finit dans le dépôt Git et que personne
     * ne pense à changer : c'est la porte dérobée la plus courante et la plus
     * durable. Une route qui refuse de s'exécuter dès qu'un administrateur
     * existe ne laisse, elle, aucune trace exploitable.
     *
     * <p><strong>Le garde-fou était commenté.</strong> N'importe qui pouvait donc
     * créer autant d'administrateurs globaux qu'il le souhaitait, indéfiniment,
     * sans authentification — la route n'en exige aucune. Il est rétabli ici :
     * c'est ce qui rend l'absence d'autorisation acceptable.
     *
     * @throws ConflictException si un administrateur existe déjà
     */
    @Override
    @Transactional
    public Users initializeGlobalAdmin(UserRequest request) {
        if (roleUserService.hasAnyUserAssignedToRole(ADMIN_ROLE)) {
            throw new ConflictException(
                    "Un administrateur existe déjà : cette route ne sert qu'au tout premier "
                    + "amorçage. Les comptes suivants se créent via POST /admin/users, "
                    + "authentifié et gardé par la permission SYSTEM:USERS.");
        }

        Users admin = userService.createUser(request);
        roleUserService.addRoleToUser(admin.getId(), ADMIN_ROLE, SYSTEM_ASSIGNER);
        userService.activateUser(admin.getEmail());
        return admin;
    }

    /**
     * Crée un compte au rôle par défaut ({@code EXPLOITANT}).
     *
     * <p>Conservée pour l'amorçage : elle évite d'avoir à composer un
     * {@code POST /admin/users} complet juste après le premier administrateur.
     * Pour tout le reste — choisir le rôle, l'auditer, le rejouer sans risque —
     * {@code POST /admin/users} est la route à employer.
     */
    @Override
    @Transactional
    public Users createStaff(UserRequest request, String assignedBy) {
        Users user = userService.createUser(request);
        roleUserService.addRoleToUser(user.getId(), DEFAULT_ROLE, normalizeAssigner(assignedBy));
        userService.activateUser(user.getEmail());
        return user;
    }



    private String normalizeAssigner(String assignedBy) {
        return assignedBy == null || assignedBy.isBlank() ? SYSTEM_ASSIGNER : assignedBy;
    }
}
