package com.sni.bilanga.security.authentication.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "one_time_token")
public class OneTimeToken {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "token")
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "one_time_token_user_fk"))
    private Users users;

    @Column(name = "is_used")
    private boolean isUsed;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "expiration")
    private Long expiration;

}
