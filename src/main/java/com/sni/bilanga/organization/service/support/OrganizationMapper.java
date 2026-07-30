package com.sni.bilanga.organization.service.support;

import com.sni.bilanga.enums.AccessScope;
import com.sni.bilanga.enums.MembershipRole;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.organization.dto.response.CooperativeResponse;
import com.sni.bilanga.organization.dto.response.FarmMembershipResponse;
import com.sni.bilanga.organization.dto.response.FarmResponse;
import com.sni.bilanga.organization.model.Cooperative;
import com.sni.bilanga.organization.model.Farm;
import com.sni.bilanga.organization.model.FarmMembership;
import com.sni.bilanga.security.admin.user.model.Users;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class OrganizationMapper {

    public CooperativeResponse toResponse(Cooperative c, long farmCount) {
        PlotStatus status = PlotStatus.from(c.getStatus());

        return CooperativeResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .location(c.getLocation())
                .contactPhone(c.getContactPhone())
                .status(c.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .farmCount(farmCount)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    public FarmResponse toResponse(Farm f, long plotCount, long memberCount) {
        PlotStatus status = PlotStatus.from(f.getStatus());

        return FarmResponse.builder()
                .id(f.getId())
                .code(f.getCode())
                .name(f.getName())
                .location(f.getLocation())
                .contactPhone(f.getContactPhone())
                .cooperativeId(f.getCooperative() == null ? null : f.getCooperative().getId())
                .cooperativeName(f.getCooperative() == null ? null : f.getCooperative().getName())
                .ownerUserId(f.getOwner() == null ? null : f.getOwner().getId())
                .ownerName(displayNameOf(f.getOwner()))
                .status(f.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .plotCount(plotCount)
                .memberCount(memberCount)
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    public FarmMembershipResponse toResponse(FarmMembership m) {
        MembershipRole role = MembershipRole.from(m.getRole());

        return FarmMembershipResponse.builder()
                .id(m.getId())
                .farmId(m.getFarm().getId())
                .farmName(m.getFarm().getName())
                .userId(m.getUser().getId())
                .userName(displayNameOf(m.getUser()))
                .userEmail(m.getUser().getEmail())
                .role(m.getRole())
                .roleLabel(role == null ? null : role.getLabel())
                .scopes(scopesOf(role))
                .joinedAt(m.getJoinedAt())
                .build();
    }

    /**
     * Domaines ouverts par le rôle, énumérés explicitement.
     *
     * « Conseiller » ne dit pas de lui-même s'il voit les marges ; un
     * administrateur qui attribue un rôle doit savoir ce qu'il ouvre, sans avoir
     * à lire le code.
     */
    private List<String> scopesOf(MembershipRole role) {
        if (role == null) {
            return List.of();
        }
        return Arrays.stream(AccessScope.values())
                .filter(role::allows)
                .map(Enum::name)
                .toList();
    }

    private String displayNameOf(Users user) {
        if (user == null) {
            return null;
        }
        String full = String.join(" ",
                        user.getFirstname() == null ? "" : user.getFirstname(),
                        user.getLastname() == null ? "" : user.getLastname())
                .trim();
        return full.isEmpty() ? user.getEmail() : full;
    }
}
