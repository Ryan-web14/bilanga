package com.sni.bilanga.utils.error;


public final class ErrorCode {

    //Request errors
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";

    // Resource errors
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String RESOURCE_ALREADY_EXISTS = "RESOURCE_ALREADY_EXISTS";

    // Validation errors
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String CONSTRAINT_VIOLATION = "CONSTRAINT_VIOLATION";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";

    // Authentication errors
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    // Server errors
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    // File errors
    public static final String FILE_STORAGE_ERROR = "FILE_STORAGE_ERROR";
    public static final String FILE_SIZE_EXCEEDED = "FILE_SIZE_EXCEEDED";
    public static final String INVALID_FILE_FORMAT = "INVALID_FILE_FORMAT";

    // HTTP errors
    public static final String METHOD_NOT_SUPPORTED = "METHOD_NOT_SUPPORTED";
    public static final String MEDIA_TYPE_NOT_SUPPORTED = "MEDIA_TYPE_NOT_SUPPORTED";
    public static final String ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND";

    // Business logic errors
    public static final String CONFLICT = "CONFLICT";
    public static final String OPERATION_NOT_ALLOWED = "OPERATION_NOT_ALLOWED";
    public static final String INSUFFICIENT_PRIVILEGES = "INSUFFICIENT_PRIVILEGES";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    // Persistence errors
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    // ── Domaine Bilanga ──────────────────────────────────────────────────────
    // Éventualités propres à la chaîne capteur → IA → conseil. Sans code dédié,
    // elles remontaient toutes en INTERNAL_SERVER_ERROR, indiscernables côté client.

    /** Le microservice d'inférence ne répond pas ou renvoie un statut non exploitable. */
    public static final String ML_SERVICE_UNAVAILABLE = "ML_SERVICE_UNAVAILABLE";

    /** Aucune culture EN_COURS sur la parcelle : le diagnostic n'a pas de contexte. */
    public static final String NO_ACTIVE_CROP = "NO_ACTIVE_CROP";

    /** Aucun relevé disponible : les moteurs n'ont rien à analyser. */
    public static final String NO_SENSOR_READING = "NO_SENSOR_READING";

    /** Boîtier inconnu du parc : son identifiant matériel n'est pas enregistré. */
    public static final String DEVICE_NOT_REGISTERED = "DEVICE_NOT_REGISTERED";

    /** Clé de boîtier absente ou incorrecte dans l'en-tête X-Device-Key. */
    public static final String INVALID_DEVICE_KEY = "INVALID_DEVICE_KEY";

    /** Aucune clé de boîtier n'est configurée côté serveur : l'ingestion est fermée. */
    public static final String DEVICE_KEY_NOT_CONFIGURED = "DEVICE_KEY_NOT_CONFIGURED";

    /** Mesure physiquement impossible : sonde vraisemblablement défaillante. */
    public static final String IMPLAUSIBLE_MEASURE = "IMPLAUSIBLE_MEASURE";

    /** Culture absente de la base de connaissance agronomique. */
    public static final String UNKNOWN_CROP = "UNKNOWN_CROP";

    /** Relevé accepté mais diagnostic écarté : cadence trop rapprochée. */
    public static final String DIAGNOSIS_THROTTLED = "DIAGNOSIS_THROTTLED";

    private ErrorCode() {
        throw new IllegalStateException("Utility class");
    }
}