package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.knowledge.model.CropRequirement;
import com.sni.bilanga.knowledge.model.CropStageRequirement;
import com.sni.bilanga.knowledge.repository.CropRequirementRepository;
import com.sni.bilanga.knowledge.repository.CropStageRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Détermine les seuils applicables à une parcelle, en tenant compte du stade
 * de croissance de la culture.
 *
 * Les besoins d'une plante ne sont pas constants sur son cycle : une tomate en
 * fructification réclame plus d'eau et de potassium qu'un plant en levée. Sans
 * cette nuance, le système applique les mêmes exigences à toutes les phases et
 * signale à contretemps.
 */
@Component
@RequiredArgsConstructor
public class CropRequirementResolver {

    private final CropRequirementRepository cropRequirementRepository;
    private final CropStageRequirementRepository stageRepository;

    /**
     * Seuils effectifs pour une culture à un stade donné.
     *
     * Le résultat est un objet neuf, jamais l'entité chargée : appliquer les
     * écarts de stade sur l'instance gérée par Hibernate écrirait ces valeurs
     * en base à la fermeture de la transaction, et corromprait durablement les
     * seuils généraux de la culture.
     */
    public Optional<CropRequirement> resolve(String cropName, String growthStage) {
        if (cropName == null) return Optional.empty();

        Optional<CropRequirement> base = cropRequirementRepository.findByCropName(cropName);
        if (base.isEmpty()) return Optional.empty();

        CropRequirement effective = copyOf(base.get());
        if (growthStage == null || growthStage.isBlank()) {
            return Optional.of(effective);
        }

        stageRepository
                .findByCropNameAndGrowthStage(cropName, normalize(growthStage))
                .ifPresent(stage -> applyOverrides(effective, stage));

        return Optional.of(effective);
    }

    /** Vrai si le stade infléchit effectivement les seuils de la culture. */
    public boolean hasStageOverride(String cropName, String growthStage) {
        if (cropName == null || growthStage == null || growthStage.isBlank()) return false;
        return stageRepository.findByCropNameAndGrowthStage(cropName, normalize(growthStage)).isPresent();
    }

    /**
     * Les seuils <strong>généraux</strong> de la culture, sans aucune surcharge de stade.
     *
     * <p>Exposé pour que l'origine de chaque valeur puisse être dite : sans le terme de
     * comparaison, {@link #resolve} rend une plage sans qu'on sache si elle vient de la
     * culture ou de la phase — or c'est précisément ce qui explique qu'une même mesure
     * déclenche un conseil à un stade et pas à un autre.
     *
     * <p>Copie défensive, comme {@link #resolve} : rendre l'entité gérée exposerait ses
     * seuils à une modification qui serait écrite en base à la fermeture de la
     * transaction.
     */
    public Optional<CropRequirement> baseline(String cropName) {
        if (cropName == null) return Optional.empty();
        return cropRequirementRepository.findByCropName(cropName).map(this::copyOf);
    }

    private CropRequirement copyOf(CropRequirement source) {
        return CropRequirement.builder()
                .id(source.getId())
                .cropName(source.getCropName())
                .phMin(source.getPhMin())
                .phMax(source.getPhMax())
                .humSolMin(source.getHumSolMin())
                .humSolMax(source.getHumSolMax())
                .tempMin(source.getTempMin())
                .tempMax(source.getTempMax())
                .azoteMin(source.getAzoteMin())
                .phosphoreMin(source.getPhosphoreMin())
                .potassiumMin(source.getPotassiumMin())
                .toleranceSecheresse(source.getToleranceSecheresse())
                .build();
    }

    /** Seuls les champs renseignés par le stade remplacent ceux de la culture. */
    private void applyOverrides(CropRequirement target, CropStageRequirement stage) {
        if (stage.getPhMin() != null) target.setPhMin(stage.getPhMin());
        if (stage.getPhMax() != null) target.setPhMax(stage.getPhMax());
        if (stage.getHumSolMin() != null) target.setHumSolMin(stage.getHumSolMin());
        if (stage.getHumSolMax() != null) target.setHumSolMax(stage.getHumSolMax());
        if (stage.getTempMin() != null) target.setTempMin(stage.getTempMin());
        if (stage.getTempMax() != null) target.setTempMax(stage.getTempMax());
        if (stage.getAzoteMin() != null) target.setAzoteMin(stage.getAzoteMin());
        if (stage.getPhosphoreMin() != null) target.setPhosphoreMin(stage.getPhosphoreMin());
        if (stage.getPotassiumMin() != null) target.setPotassiumMin(stage.getPotassiumMin());
        if (stage.getToleranceSecheresse() != null) {
            target.setToleranceSecheresse(stage.getToleranceSecheresse());
        }
    }

    private String normalize(String growthStage) {
        return growthStage.trim().toUpperCase(Locale.FRANCE);
    }
}
