package com.sni.bilanga.mailService.controller;

import com.sni.bilanga.mailService.baseService.DefaultEmailSender;
import com.sni.bilanga.mailService.dto.response.EmailDeliveryResponse;
import com.sni.bilanga.mailService.enums.EmailDeliveryStatus;
import com.sni.bilanga.mailService.service.EmailDeliveryTracker;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/email-deliveries")
public class EmailDeliveryController {

    private final EmailDeliveryTracker tracker;
    private final DefaultEmailSender emailSender;

    @GetMapping
    public ResponseEntity<PaginatedResponse<EmailDeliveryResponse>> list(
            @RequestParam(required = false) EmailDeliveryStatus status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) String relatedCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PaginatedResponse<>(tracker.list(
                status,
                provider,
                recipientEmail,
                relatedType,
                relatedCode,
                createdFrom,
                createdTo,
                pageable
        )));
    }

    @GetMapping("/{emailNumber}")
    public ResponseEntity<EmailDeliveryResponse> get(@PathVariable String emailNumber) {
        return ResponseEntity.ok(tracker.get(emailNumber));
    }

    @PatchMapping("/{emailNumber}/retry")
    public ResponseEntity<EmailDeliveryResponse> retry(@PathVariable String emailNumber) {
        return ResponseEntity.accepted().body(emailSender.retry(emailNumber));
    }
}
