package com.sni.bilanga.iot.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import com.sni.bilanga.enums.EquipmentStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class IotDeviceRequest {

    @NotNull(message = "La parcelle est obligatoire")
    private Long plotId;

    @NotBlank(message = "L'identifiant technique du boîtier est obligatoire")
    private String technicalId;

    private String deviceName;
    /** Typé : la contrainte CHECK de la V11 n'accepte que ACTIVE et RETIRE. */
    private EquipmentStatus status;

    @Min(value = 0, message = "Le niveau de batterie ne peut être négatif")
    @Max(value = 100, message = "Le niveau de batterie ne peut dépasser 100 %")
    private Integer batteryLevel;

    @DecimalMin(value = "0", message = "La tension de batterie ne peut être négative")
    @DecimalMax(value = "30", message = "La tension de batterie ne peut dépasser 30 V")
    private Double batteryVoltage;

    @Size(max = 40, message = "La version du micrologiciel ne peut dépasser 40 caractères")
    private String firmwareVersion;

    /** Pose sur le terrain, à distinguer de l'enregistrement en base. */
    @PastOrPresent(message = "La date d'installation ne peut être dans le futur")
    private Instant installedAt;
}
