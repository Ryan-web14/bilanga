package com.sni.bilanga.intervention.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.intervention.dto.response.InterventionResponse;
import com.sni.bilanga.intervention.model.Intervention;
import com.sni.bilanga.security.admin.user.model.Users;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class InterventionMapper {

    private static final Locale FR = Locale.FRANCE;

    /** Au-delà, l'extrait du conseil encombre la liste au lieu de la situer. */
    private static final int EXCERPT_LENGTH = 120;

    public InterventionResponse toResponse(Intervention i) {
        InterventionType type = InterventionType.from(i.getType());

        return InterventionResponse.builder()
                .id(i.getId())
                .plotId(i.getPlot().getId())
                .plotName(i.getPlot().getName())
                .cropId(i.getCrop() == null ? null : i.getCrop().getId())
                .cropName(i.getCrop() == null ? null : Culture.canonical(i.getCrop().getCropName()))
                .recommendationId(i.getRecommendation() == null ? null : i.getRecommendation().getId())
                .recommendationContent(excerpt(i))
                .type(i.getType())
                .typeLabel(type == null ? null : type.getLabel())
                .product(i.getProduct())
                .dose(i.getDose())
                .unit(i.getUnit())
                .dosage(dosage(i))
                .cost(i.getCost())
                .performedAt(i.getPerformedAt())
                .performedById(i.getPerformedBy() == null ? null : i.getPerformedBy().getId())
                .performedByName(displayNameOf(i.getPerformedBy()))
                .weatherNote(i.getWeatherNote())
                .note(i.getNote())
                .effectMeasurable(type != null && type.hasMeasurableEffect())
                .createdAt(i.getCreatedAt())
                .build();
    }

    /**
     * Dose et unité réunies en une chaîne lisible.
     *
     * Les deux champs restent séparés en base — on doit pouvoir sommer les
     * doses — mais les recomposer côté client conduit chacun à formater
     * différemment, et à afficher « 12.5 » sans unité quand elle manque.
     */
    private String dosage(Intervention i) {
        if (i.getDose() == null) {
            return null;
        }
        String unit = i.getUnit() == null || i.getUnit().isBlank() ? "" : " " + i.getUnit().trim();
        return String.format(FR, "%.2f%s", i.getDose(), unit);
    }

    /**
     * Extrait du conseil suivi.
     *
     * Le contenu intégral d'une recommandation fait plusieurs phrases ; le
     * charger entier dans chaque ligne de liste noierait l'information utile —
     * qui est simplement « cette action répondait à tel conseil ».
     */
    private String excerpt(Intervention i) {
        if (i.getRecommendation() == null || i.getRecommendation().getContent() == null) {
            return null;
        }
        String content = i.getRecommendation().getContent();
        return content.length() <= EXCERPT_LENGTH
                ? content
                : content.substring(0, EXCERPT_LENGTH - 3) + "...";
    }

    private String displayNameOf(Users user) {
        if (user == null) {
            return null;
        }
        String full = String.join(" ",
                        user.getFirstname() == null ? "" : user.getFirstname(),
                        user.getLastname() == null ? "" : user.getLastname())
                .trim();
        return full.isEmpty() ? user.getEmail() : full;
    }
}
