package com.sni.bilanga.security.admin.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UserResponse {

    private String userId;
    private String email;
    private String firstname;
    private String lastname;
    private String businessEntityCode;
    private String businessEntityName;
    private String status;

    //returned only if there's a generated password
    private String generatedPassword;
}
