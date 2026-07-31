package com.sni.bilanga.mailService.interfaces;



import com.sni.bilanga.security.admin.user.model.Users;

import java.util.concurrent.CompletableFuture;

public interface OttMailService {

    CompletableFuture<Boolean> sendOneTimeTokenMail(Users user, String token);
}
