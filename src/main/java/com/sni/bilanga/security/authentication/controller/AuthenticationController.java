package com.sni.bilanga.security.authentication.controller;


import com.sni.bilanga.security.authentication.dto.request.*;
import com.sni.bilanga.security.authentication.dto.response.CurrentUserResponse;
import com.sni.bilanga.security.authentication.dto.response.LoginResponse;
import com.sni.bilanga.security.authentication.dto.response.OttResponse;
import com.sni.bilanga.security.authentication.service.interfaces.AuthenticationService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/auth")
public class AuthenticationController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.login(request, httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.refreshToken(request, httpRequest)));
    }

    @PostMapping("/ott/request")
    public ResponseEntity<ApiResponse<OttResponse>> requestOtt(@Valid @RequestBody EmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.ottLogin(request.getEmail())));
    }

    @PostMapping("/ott/validate")
    public ResponseEntity<ApiResponse<LoginResponse>> validateOtt(@Valid @RequestBody ValidateOttRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.validateOttLogin(request, httpRequest)));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        authenticationService.resetPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmationRequest request) {
        authenticationService.validateResetPassword(request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.currentUser(authentication)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authenticationService.logout(extractToken(authorizationHeader));
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @PostMapping("/unlock-account")
    public ResponseEntity<ApiResponse<OttResponse>> requestUnlockAccount(@Valid @RequestBody EmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.requestUnlockAccount(request.getEmail())));
    }

    @PostMapping("/unlock-account/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmUnlockAccount(@Valid @RequestBody ValidateOttRequest request) {
        authenticationService.confirmUnlockAccount(request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @PostMapping("/email/verify/resend")
    public ResponseEntity<ApiResponse<OttResponse>> resendEmailVerification(@Valid @RequestBody EmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authenticationService.resendEmailVerification(request.getEmail())));
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        return token.isBlank() ? null : token;
    }
}
