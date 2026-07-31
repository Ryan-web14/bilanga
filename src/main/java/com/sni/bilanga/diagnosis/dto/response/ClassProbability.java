package com.sni.bilanga.diagnosis.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ClassProbability {

    private String diseaseCode;

    /** Nom français de la maladie. Une alternative sans nom lisible ne peut pas
     *  être comparée à celle qui a été retenue. */
    private String displayName;
    private Double probability;
}