package com.sni.bilanga.security.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CollectionId;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "token")
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_token"))
    private Users user;

    @Column(name = "expiration")
    private Instant expiration;

    @Column(name = "revoked")
    private boolean revoked;
}
