package com.sni.bilanga.mailService.enums;

public enum EmailPriority {

    CRITICAL("email.critical"),
    HIGH("email.high"),
    NORMAL("email.normal"),
    BULK("email.bulk");

    private final String routingKey;

    EmailPriority(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }

    public static EmailPriority fromEventType(String eventType) {
        if (eventType == null) return NORMAL;
        String upper = eventType.toUpperCase();

        if (upper.contains("OTT") || upper.contains("OTP")
                || upper.contains("PASSWORD_RESET")
                || upper.contains("EMAIL_CONFIRMATION")
                || upper.contains("EMAIL_VERIFICATION")) {
            return CRITICAL;
        }

        if (upper.contains("PAYMENT") || upper.contains("CONTRACT_SIGNING")
                || upper.contains("BILLING_DOCUMENT")
                || upper.contains("REFUND")) {
            return HIGH;
        }

        if (upper.contains("REMINDER") || upper.contains("EXPIRY")
                || upper.contains("ADMIN_ALERT") || upper.contains("REPORT")) {
            return BULK;
        }

        return NORMAL;
    }
}