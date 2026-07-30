package com.sni.bilanga.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Corps d'erreur de l'API.
 *
 * Les quatre premiers champs sont volontairement identiques à ceux d'
 * {@link com.sni.bilanga.templateResponse.ApiResponse} : le client teste
 * {@code success} et rien d'autre pour savoir s'il tient un résultat ou une
 * erreur. Auparavant succès et erreur n'avaient aucun champ commun, ce qui
 * obligeait le frontend à discriminer sur le statut HTTP <em>et</em> sur la forme
 * du corps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    // ── Tronc commun avec ApiResponse ────────────────────────────────────────
    /** Toujours {@code false} : c'est ce qui distingue une erreur d'un succès. */
    private Boolean success;
    private String message;
    private String errorCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    // ── Propre à l'erreur ────────────────────────────────────────────────────
    private int status;

    /** Identifiant de corrélation : le même que celui journalisé côté serveur. */
    private String traceId;

    /** Détail des champs invalides · omis quand il n'y en a pas. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> errors;

    // ── Champs de diagnostic · uniquement si app.dev-mode=true ───────────────
    private String path;
    private String debugMessage;
    private String exceptionName;
}
