package com.sni.bilanga.farm.dto.request;

import com.sni.bilanga.enums.IrrigationType;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.enums.SoilType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Les champs à valeurs fermées sont typés par une énumération : une valeur hors
 * vocabulaire est refusée à la désérialisation, avec la liste des valeurs
 * acceptées dans le message. Auparavant une chaîne libre atteignait la base.
 */
@Data
public class PlotRequest {

    @NotBlank(message = "Le nom de la parcelle est obligatoire")
    @Size(max = 150, message = "Le nom ne peut dépasser 150 caractères")
    private String name;

    @Size(max = 255, message = "La localisation ne peut dépasser 255 caractères")
    private String location;

    /**
     * Coordonnées décimales de la parcelle.
     *
     * Facultatives — une parcelle non géolocalisée reste parfaitement
     * exploitable. Elles conditionnent seulement la météo et le risque de
     * voisinage, qui se taisent proprement en leur absence.
     */
    @DecimalMin(value = "-90", message = "La latitude doit être comprise entre -90 et 90")
    @DecimalMax(value = "90", message = "La latitude doit être comprise entre -90 et 90")
    private Double latitude;

    @DecimalMin(value = "-180", message = "La longitude doit être comprise entre -180 et 180")
    @DecimalMax(value = "180", message = "La longitude doit être comprise entre -180 et 180")
    private Double longitude;

    @DecimalMin(value = "-500", message = "L'altitude doit être comprise entre -500 et 9000 m")
    @DecimalMax(value = "9000", message = "L'altitude doit être comprise entre -500 et 9000 m")
    private Double altitude;

    private SoilType soilType;

    /**
     * Moyen d'irrigation. Détermine si un conseil d'arrosage est applicable :
     * sur une parcelle pluviale, il est remplacé par une mesure réalisable.
     */
    private IrrigationType irrigationType;

    @Positive(message = "La superficie doit être positive")
    private Double area;

    private PlotStatus status;

    /**
     * Propriétaire. <strong>Omis à la création, la parcelle revient à l'appelant.</strong>
     *
     * <p>Attend la clé technique — le champ {@code id} de {@code GET /auth/me}, et non le
     * code lisible {@code userId} de la même réponse. Les deux sortent en chaînes, ce qui
     * ne permet pas de les distinguer à l'œil ; le repli sur l'appelant supprime la
     * question dans le cas courant.
     */
    private Long userId;

    /**
     * Champs à <strong>vider</strong> explicitement.
     *
     * <p>La mise à jour est partielle : un champ absent n'est plus écrasé. Sans ce
     * mécanisme, certains champs deviendraient indélébiles — on aurait troqué une perte
     * silencieuse contre une donnée qu'on ne peut plus retirer.
     *
     * <p>Accepte : {@code location}, {@code latitude}, {@code longitude}, {@code altitude},
     * {@code soilType}, {@code irrigationType}, {@code area}, {@code farmId},
     * {@code userId}. Un nom inconnu est refusé en 400, jamais ignoré.
     */
    private java.util.List<String> clearFields;

    /**
     * Exploitation de rattachement — <strong>facultative</strong>.
     *
     * Absente, la parcelle appartient à son seul utilisateur et se comporte
     * comme avant l'introduction de ce niveau. Renseignée, elle devient visible
     * des membres de l'exploitation <em>en plus</em> de son propriétaire.
     */
    private Long farmId;
}
