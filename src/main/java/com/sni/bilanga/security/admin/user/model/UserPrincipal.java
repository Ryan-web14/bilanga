package com.sni.bilanga.security.admin.user.model;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;



public class UserPrincipal implements UserDetails, CredentialsContainer {

    public UserPrincipal() {}

    public UserPrincipal(Users user) {
        this.user = user;
        this.password = user.getPasswordHash();
        this.authorities = resolveAuthorities(user);
    }

    @Getter
    private Users user;

    private String password;

    private Collection<? extends GrantedAuthority> authorities = List.of();




    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return !Boolean.TRUE.equals(user.getIsAccountExpired());
    }

    @Override
    public boolean isAccountNonLocked() {
        return !Boolean.TRUE.equals(user.getIsAccountLocked());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getIsAccountEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    private Collection<? extends GrantedAuthority> resolveAuthorities(Users user) {
        if(user == null || user.getRoleUsers() == null){
            return List.of();
        }
        Set<String> authorities = new LinkedHashSet<>();
        user.getRoleUsers().stream()
                .filter(Objects::nonNull)
                .filter(roleUser -> roleUser.getRole() != null)
                .filter(roleUser -> Boolean.TRUE.equals(roleUser.getRole().getIsActive()))
                .forEach(roleUser -> {
                    authorities.add("ROLE_" + roleUser.getRole().getName());
                    if (roleUser.getRole().getRolePermissions() != null) {
                        roleUser.getRole().getRolePermissions().stream()
                                .filter(Objects::nonNull)
                                .filter(rolePermission -> Boolean.TRUE.equals(rolePermission.getIsActive()))
                                .filter(rolePermission -> rolePermission.getPermission() != null)
                                .filter(rolePermission -> Boolean.TRUE.equals(rolePermission.getPermission().getIsActive()))
                                .forEach(rolePermission -> {
                                    authorities.add(rolePermission.getPermission().getName());
                                    authorities.add(rolePermission.getPermission().getFullPermissionName());
                                });
                    }
                });
        return new ArrayList<>(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}


