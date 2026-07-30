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
    private Double probability;
}