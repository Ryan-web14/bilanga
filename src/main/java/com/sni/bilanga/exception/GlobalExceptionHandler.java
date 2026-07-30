package com.sni.bilanga.exception;

import com.sni.bilanga.exception.customs.*;
import com.sni.bilanga.utils.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Jackson nomme les valeurs acceptées d'une énumération dans son message,
     * entre crochets. Les extraire évite de répondre « corps malformé » là où le
     * client a simplement écrit « URGENT » au lieu de « HAUTE ».
     */
    private static final Pattern ENUM_VALUES = Pattern.compile("\\[([^\\[\\]]+)\\]");

    private final ErrorResponse errorResponse;

    // ── Exceptions métier ────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.RESOURCE_NOT_FOUND), HttpStatus.NOT_FOUND,
                ex.getMessage(), request, ex, null));
    }

    @ExceptionHandler(ResourceAlreadyExistException.class)
    public ResponseEntity<Object> handleResourceAlreadyExists(ResourceAlreadyExistException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.RESOURCE_ALREADY_EXISTS), HttpStatus.CONFLICT,
                ex.getMessage(), request, ex, null));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.BAD_REQUEST), HttpStatus.BAD_REQUEST,
                ex.getMessage(), request, ex, null));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.CONFLICT), HttpStatus.CONFLICT,
                ex.getMessage(), request, ex, null));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidation(ValidationException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                ex.getMessage(), request, ex, ex.getErrors()));
    }

    /**
     * Règle métier volontairement refusée par la couche service : seuils
     * incohérents, doublon, culture inconnue, transition d'état interdite.
     * Le message est rédigé pour l'utilisateur, donc renvoyé tel quel.
     *
     * Remplace l'ancien gestionnaire d'{@code IllegalArgumentException}, qui
     * transformait aussi les défauts de programmation en 400 et les rendait
     * indiscernables d'une erreur de saisie.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Object> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.BUSINESS_RULE_VIOLATION), HttpStatus.BAD_REQUEST,
                ex.getMessage(), request, ex, null));
    }

    /**
     * Dépendance externe momentanément hors service — en pratique le
     * microservice d'inférence. Rien n'est cassé côté backend : l'appelant a
     * intérêt à réessayer, ce qu'un 500 ne lui disait pas.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Object> handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.SERVICE_UNAVAILABLE), HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage(), request, ex, null));
    }

    // ── Authentification / autorisation ──────────────────────────────────────

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.UNAUTHORIZED), HttpStatus.UNAUTHORIZED,
                ex.getMessage(), request, ex, null));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.FORBIDDEN), HttpStatus.FORBIDDEN,
                "Access to this resource is forbidden.", request, ex, null));
    }

    @ExceptionHandler(AccesDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccesDeniedException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.ACCESS_DENIED), HttpStatus.FORBIDDEN,
                "Access denied.", request, ex, null));
    }

    /**
     * Refus prononcé par {@code @PreAuthorize}.
     *
     * Sans ce gestionnaire, l'exception de Spring Security tombait dans le
     * fourre-tout final et ressortait en <strong>500</strong> : un utilisateur
     * authentifié mais dépourvu de la permission recevait une erreur serveur au
     * lieu d'un refus. La classe maison {@code AccesDeniedException} (sans le
     * second « s ») ne l'interceptait pas — c'est une classe différente.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                "Vous n'avez pas la permission requise pour cette opération.", request, ex, null));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(
            org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                "Authentification requise pour accéder à cette ressource.", request, ex, null));
    }

    @ExceptionHandler(BadCredentialException.class)
    public ResponseEntity<Object> handleBadCredential(BadCredentialException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.INVALID_CREDENTIALS), HttpStatus.UNAUTHORIZED,
                "Invalid credentials provided.", request, ex, null));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        return respond(errorResponse.build(codeOf(ex, ErrorCode.ACCOUNT_LOCKED), HttpStatus.UNAUTHORIZED,
                ex.getMessage(), request, ex, null));
    }

    // ── Persistance ──────────────────────────────────────────────────────────

    /**
     * Contrainte de base violée : clé étrangère, unicité, ou l'un des
     * {@code CHECK} qui verrouillent le vocabulaire du domaine. Le détail SQL
     * n'est pas exposé — il nomme des colonnes et des index.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.DATA_INTEGRITY_VIOLATION, HttpStatus.CONFLICT,
                "L'opération contredit une contrainte de cohérence des données.", request, ex, null));
    }

    /** Deux modifications concurrentes du même enregistrement : la seconde est refusée. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                "Cet enregistrement a été modifié entre-temps. Rechargez-le puis recommencez.",
                request, ex, null));
    }

    // ── Liaison et validation Spring MVC ─────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return respond(errorResponse.build(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "Invalid request parameters.", request, ex, errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBind(BindException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return respond(errorResponse.build(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "Invalid request parameters.", request, ex, errors));
    }

    /** Contraintes portées par les paramètres de méthode ({@code @Validated} sur un contrôleur). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(this::formatViolation)
                .toList();
        return respond(errorResponse.build(ErrorCode.CONSTRAINT_VIOLATION, HttpStatus.BAD_REQUEST,
                "Invalid request parameters.", request, ex, errors));
    }

    /** Type incompatible dans l'URL ou la query : {@code ?plotId=abc} sur un {@code Long}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> required = ex.getRequiredType();
        String expected = required != null && required.isEnum()
                ? "valeurs acceptées : " + String.join(", ", enumNames(required))
                : "type attendu : " + (required == null ? "inconnu" : required.getSimpleName());

        return respond(errorResponse.build(ErrorCode.TYPE_MISMATCH, HttpStatus.BAD_REQUEST,
                String.format("Le paramètre '%s' est invalide (%s).", ex.getName(), expected),
                request, ex, null));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Object> handleMissingRequestPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "Missing required request part: " + ex.getRequestPartName() + ".", request, ex, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingRequestParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.MISSING_PARAMETER, HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName() + ".", request, ex, null));
    }

    /**
     * Corps illisible. Le cas de loin le plus fréquent est une valeur hors du
     * vocabulaire d'une énumération : le message le nomme explicitement plutôt
     * que de renvoyer un « corps malformé » qui n'aide personne.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                describeUnreadableBody(ex), request, ex, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.FILE_SIZE_EXCEEDED, HttpStatus.PAYLOAD_TOO_LARGE,
                "Le fichier transmis dépasse la taille autorisée.", request, ex, null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String supported = ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");
        return respond(errorResponse.build(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '" + ex.getContentType() + "' is not supported. Supported: " + supported + ".",
                request, ex, null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.METHOD_NOT_SUPPORTED, HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint.", request, ex, null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.ENDPOINT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "The requested endpoint does not exist.", request, ex, null));
    }

    /**
     * Statut posé directement par Spring ou par un contrôleur. Sans ce
     * gestionnaire, la réponse échappait au format {@link ApiError} et l'appelant
     * recevait une forme d'erreur différente du reste de l'API.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        return respond(errorResponse.build(codeFor(status), status, message, request, ex, null));
    }

    // ── Filet de sécurité ────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex, HttpServletRequest request) {
        return respond(errorResponse.build(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again or contact support.", request, ex, null));
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private ResponseEntity<Object> respond(ApiError apiError) {
        return new ResponseEntity<>(apiError, HttpStatus.valueOf(apiError.getStatus()));
    }

    /**
     * Chaque exception maison porte déjà son code ; le gestionnaire le reprend au
     * lieu d'en redéclarer un, ce qui les laissait dériver l'un de l'autre.
     */
    private String codeOf(BaseException ex, String fallback) {
        String code = ex.getErrorCode();
        return code == null || code.isBlank() ? fallback : code;
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case PAYLOAD_TOO_LARGE -> ErrorCode.FILE_SIZE_EXCEEDED;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.MEDIA_TYPE_NOT_SUPPORTED;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_SUPPORTED;
            case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is5xxServerError()
                    ? ErrorCode.INTERNAL_SERVER_ERROR
                    : ErrorCode.BAD_REQUEST;
        };
    }

    private String describeUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause == null ? null : cause.getMessage();

        if (detail != null && detail.contains("not one of the values accepted for Enum")) {
            Matcher matcher = ENUM_VALUES.matcher(detail);
            if (matcher.find()) {
                return "Valeur non reconnue. Valeurs acceptées : " + matcher.group(1) + ".";
            }
        }
        return "Request body is missing or malformed.";
    }

    private List<String> enumNames(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        return constants == null
                ? List.of()
                : java.util.Arrays.stream(constants).map(Object::toString).toList();
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
