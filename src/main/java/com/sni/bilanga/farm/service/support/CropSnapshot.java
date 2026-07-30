package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.farm.model.Crop;

import java.time.LocalDate;

/**
 * Vue plate d'un cycle, destinée <strong>uniquement</strong> au calcul de diff.
 *
 * <h2>Pourquoi ne jamais donner l'entité {@code Crop} à {@code AuditDiffUtil}</h2>
 *
 * <p>C'est la raison d'être de cette classe, et elle n'est pas cosmétique.
 *
 * <p>{@code AuditDiffUtil} parcourt les champs par réflexion et convertit tout ce
 * qui n'est pas scalaire par {@code String.valueOf(value)}. Sur l'entité :
 *
 * <ul>
 *   <li>{@code crop.plot} est un proxy {@code LAZY}. Le convertir en chaîne
 *       <strong>initialise le proxy</strong> — une requête parasite — puis appelle le
 *       {@code toString()} Lombok de {@code Plot}, qui déréférence à son tour
 *       {@code user} et {@code farm}. Deux requêtes de plus par écriture de journal,
 *       et une valeur illisible en base ;</li>
 *   <li>{@code crop.version} et {@code crop.id} apparaîtraient comme des
 *       « changements » à chaque écriture — le compteur de verrou optimiste bouge par
 *       définition. Le journal serait noyé sous du bruit structurel ;</li>
 *   <li>{@code AuditDiffUtil.display()} laisse passer les {@code Number} tels quels :
 *       un identifiant Snowflake sortirait en <strong>nombre JSON à 19 chiffres</strong>,
 *       incohérent avec le reste de l'API où {@code JacksonConfig} les sérialise en
 *       chaînes.</li>
 * </ul>
 *
 * <p>Un {@code record} résout les trois d'un coup : ses composants sont des champs
 * privés finaux, donc {@code getDeclaredFields()} fonctionne tel quel ; aucun n'est
 * une association JPA ; aucun n'est un {@code Long}. Les clés de diff sont stables,
 * lisibles, et correspondent au vocabulaire de l'API.
 *
 * <p><strong>Ce qui n'y figure pas, délibérément</strong> : {@code id},
 * {@code version}, {@code plot}, {@code createdAt}, {@code updatedAt} et
 * {@code economicsSnapshot}. Les cinq premiers ne décrivent pas ce que
 * l'<em>utilisateur</em> a changé ; le dernier est un instantané volumineux que
 * dupliquer dans chaque entrée de journal ferait grossir la table sans rien apprendre
 * — la clôture est déjà tracée par son propre {@code event_type}.
 */
public record CropSnapshot(

        String cropName,
        String variety,
        LocalDate plantingDate,
        Integer cycleDurationDays,
        LocalDate expectedHarvestDate,
        Double plantedArea,
        Integer plantDensity,
        String seedLot,
        String growthStage,
        String status,
        LocalDate actualEndDate,
        String closureReason,
        String closureNote) {

    /**
     * Capture l'état courant d'un cycle.
     *
     * <p>{@code null} sur une entrée nulle plutôt qu'une exception :
     * {@code AuditDiffUtil.diff} traite déjà le cas et rend une carte vide, ce qui est
     * la bonne réponse — « rien à consigner » plutôt que « échec de la journalisation ».
     * Un journal ne doit jamais faire échouer l'opération qu'il décrit.
     */
    public static CropSnapshot of(Crop crop) {
        if (crop == null) {
            return null;
        }
        return new CropSnapshot(
                crop.getCropName(),
                crop.getVariety(),
                crop.getPlantingDate(),
                crop.getCycleDurationDays(),
                crop.getExpectedHarvestDate(),
                crop.getPlantedArea(),
                crop.getPlantDensity(),
                crop.getSeedLot(),
                crop.getGrowthStage(),
                crop.getStatus(),
                crop.getActualEndDate(),
                crop.getClosureReason(),
                crop.getClosureNote());
    }
}
