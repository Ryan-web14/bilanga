package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class KnowledgeRuleResponse {

    private Long id;
    private String category;

    /** {@code *} signifie « quelle que soit la culture ». */
    private String cropName;

    private Boolean cropAgnostic;

    private String conditionText;
    private String proposedAction;

    private String priority;
    private String priorityLabel;

    /** Une règle non validée reste consultable mais n'alimente pas le moteur. */
    private Boolean validated;

    /** Coût indicatif de l'action, par hectare. {@code null} = non renseigné, jamais « gratuit ». */
    private java.math.BigDecimal estimatedCost;
}
