package com.sni.bilanga.security.model;




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
@Data
@Builder
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @IdGeneration
    @Column(name = "id")
    private  Long id;

    @Column(name = "password_token")
    private String passwordToken;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_token"))
    private Users user;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "used", nullable = false)
    private Boolean used;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expiration")
    private Long expiration;

    @Column(name = "created_by", length = 255)
    private String createdBy;
}
