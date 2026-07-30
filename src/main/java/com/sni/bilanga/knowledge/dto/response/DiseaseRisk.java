package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiseaseRisk {

    private String diseaseCode;
    private String displayName;

    /** Part du poids total des conditions qui sont réunies, de 0 à 1. */
    private Double riskScore;

    private String level;

    private List<String> satisfiedConditions;

    private String statement;

    private String prevention;
}
