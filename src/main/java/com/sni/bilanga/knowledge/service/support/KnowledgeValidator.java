package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.knowledge.repository.CropRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Contrôle la cohérence des connaissances saisies.
 *
 * C'est la pièce maîtresse de l'administration. Une règle mal formée ne
 * provoque aucune erreur visible : elle ne se déclenche simplement jamais,
 * ou se déclenche à contretemps. Un opérateur inconnu, un champ de mesure mal
 * orthographié, une culture absente du référentiel — chacun de ces défauts
 * fausse silencieusement les recommandations, parfois des mois durant.
 *
 * Mieux vaut refuser une saisie que laisser s'installer une règle morte.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeValidator {

    /** Joker désignant toutes les cultures. */
    public static final String ANY = "*";

    /** Champs de mesure présents dans un relevé de capteurs. */
    public static final Set<String> MEASURE_FIELDS = Set.of(
            "temperature", "humidite_sol", "humidite_air", "ph",
            "azote", "phosphore", "potassium", "luminosite");

    public static final Set<String> OPERATORS = Set.of(">", "<", ">=", "<=", "==");

    /** BETWEEN exige une borne haute, les autres opérateurs non. */
    public static final String OPERATOR_BETWEEN = "BETWEEN";

    public static final Set<String> PRIORITIES = Set.of("HAUTE", "MOYENNE", "BASSE");

    /** Catégories reconnues par le moteur de règles. */
    public static final Set<String> CATEGORIES = Set.of(
            "NORMAL", "STRESS_HYDRIQUE", "EXCES_EAU", "SOL_ACIDE", "SOL_ALCALIN",
            "CARENCES_NUTRITIVES", "RISQUE_MALADIE", "STRESS_THERMIQUE", "MALADIE_FOLIAIRE");

    private final CropRequirementRepository cropRequirementRepository;

    // ============================================================
    // Contrôles unitaires
    // ============================================================

    /** Normalise la culture et vérifie qu'elle est connue du référentiel. */
    public String requireCrop(String cropName, boolean allowAny) {
        if (cropName == null || cropName.isBlank()) {
            if (allowAny) return ANY;
            throw new BusinessRuleException("La culture est obligatoire.");
        }

        String normalized = cropName.trim().toLowerCase(Locale.FRANCE);
        if (ANY.equals(normalized)) {
            if (allowAny) return ANY;
            throw new BusinessRuleException("Cette règle exige une culture précise.");
        }

        if (cropRequirementRepository.findByCropName(normalized).isEmpty()) {
            throw new BusinessRuleException(
                    "Culture inconnue : " + normalized
                            + ". Enregistrez d'abord ses seuils agronomiques.");
        }
        return normalized;
    }

    public String requireMeasureField(String field) {
        if (field == null || !MEASURE_FIELDS.contains(field.trim())) {
            throw new BusinessRuleException(
                    "Champ de mesure inconnu : " + field + ". Valeurs admises : " + MEASURE_FIELDS);
        }
        return field.trim();
    }

    public String requireOperator(String operator) {
        if (operator == null) {
            throw new BusinessRuleException("L'opérateur est obligatoire.");
        }
        String value = operator.trim().toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(operator.trim()) && !OPERATOR_BETWEEN.equals(value)) {
            throw new BusinessRuleException(
                    "Opérateur inconnu : " + operator + ". Valeurs admises : " + OPERATORS + " et BETWEEN");
        }
        return OPERATOR_BETWEEN.equals(value) ? OPERATOR_BETWEEN : operator.trim();
    }

    public String requirePriority(String priority, String fallback) {
        if (priority == null || priority.isBlank()) return fallback;

        String value = priority.trim().toUpperCase(Locale.FRANCE);
        if (!PRIORITIES.contains(value)) {
            throw new BusinessRuleException(
                    "Priorité inconnue : " + priority + ". Valeurs admises : " + PRIORITIES);
        }
        return value;
    }

    public String requireCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessRuleException("La catégorie est obligatoire.");
        }
        String value = category.trim().toUpperCase(Locale.FRANCE);
        if (!CATEGORIES.contains(value)) {
            throw new BusinessRuleException(
                    "Catégorie inconnue : " + category + ". Valeurs admises : " + CATEGORIES);
        }
        return value;
    }

    // ============================================================
    // Contrôles croisés
    // ============================================================

    /** BETWEEN exige deux bornes ordonnées ; les autres opérateurs n'en admettent qu'une. */
    public void requireCoherentThresholds(String operator, Double threshold, Double thresholdMax) {
        if (threshold == null) {
            throw new BusinessRuleException("Le seuil est obligatoire.");
        }

        if (OPERATOR_BETWEEN.equals(operator)) {
            if (thresholdMax == null) {
                throw new BusinessRuleException(
                        "L'opérateur BETWEEN exige une borne haute (thresholdMax).");
            }
            if (thresholdMax <= threshold) {
                throw new BusinessRuleException(
                        "La borne haute doit être supérieure à la borne basse.");
            }
        } else if (thresholdMax != null) {
            throw new BusinessRuleException(
                    "Une borne haute n'a de sens qu'avec l'opérateur BETWEEN.");
        }
    }

    /** Un intervalle inversé rendrait la règle inapplicable. */
    public void requireOrderedRange(String label, Double min, Double max) {
        if (min != null && max != null && max <= min) {
            throw new BusinessRuleException(
                    "Plage incohérente pour " + label + " : le maximum doit dépasser le minimum.");
        }
    }

    /** Le poids d'une condition doit rester strictement positif : à zéro, elle ne pèse rien. */
    public double requireWeight(Double weight) {
        if (weight == null) return 1d;
        if (weight <= 0) {
            throw new BusinessRuleException("Le poids d'une condition doit être strictement positif.");
        }
        return weight;
    }
}
