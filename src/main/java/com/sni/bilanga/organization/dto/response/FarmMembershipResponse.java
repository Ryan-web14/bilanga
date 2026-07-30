package com.sni.bilanga.organization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FarmMembershipResponse {

    private Long id;

    private Long farmId;
    private String farmName;

    private Long userId;
    private String userName;
    private String userEmail;

    private String role;
    private String roleLabel;

    /**
     * Domaines auxquels ce rôle donne accès :
     * {@code AGRONOMIQUE}, {@code ECONOMIQUE}, {@code TECHNIQUE}.
     *
     * <p>Exposés plutôt que laissés à deviner : « conseiller » ne dit pas de
     * lui-même s'il voit les marges, et un administrateur qui attribue un rôle
     * doit savoir ce qu'il ouvre.
     */
    private List<String> scopes;

    private Instant joinedAt;
}
