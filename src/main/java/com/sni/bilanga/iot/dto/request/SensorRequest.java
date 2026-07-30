package com.sni.bilanga.iot.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.sni.bilanga.enums.EquipmentStatus;
import lombok.Data;

@Data
public class SensorRequest {

    @NotNull(message = "Le boîtier est obligatoire")
    private Long deviceId;

    @NotBlank(message = "Le type de capteur est obligatoire")
    private String sensorType;

    /** Typé : la contrainte CHECK de la V11 n'accepte que ACTIVE et RETIRE. */
    private EquipmentStatus status;
    private Double defaultValue;
}