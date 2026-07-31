package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.knowledge.model.DiseaseKnowledge;
import com.sni.bilanga.knowledge.repository.DiseaseKnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Le seul endroit du parcours où le domaine cessait d'être francophone.
 *
 * <p>Les modèles rendent des classes anglaises issues des jeux d'entraînement publics.
 * Elles ressortaient telles quelles dans {@code result}, dans le message des alertes et
 * dans la chronologie, à côté de conseils rédigés en français. Le nom français existait
 * pourtant en base depuis la V3 ; seul le calcul de risque le lisait.
 */
@DisplayName("DiseaseLabeller : traduire ce qui est connu, ne jamais inventer le reste")
class DiseaseLabellerTest {

    private DiseaseKnowledgeRepository repository;
    private DiseaseLabeller labeller;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DiseaseKnowledgeRepository.class);
        Mockito.when(repository.findByCropNameAndDiseaseCode(anyString(), anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(repository.findFirstByDiseaseCodeIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
        labeller = new DiseaseLabeller(repository);
    }

    private void known(String cropName, String code, String displayName) {
        DiseaseKnowledge knowledge = new DiseaseKnowledge();
        knowledge.setCropName(cropName);
        knowledge.setDiseaseCode(code);
        knowledge.setDisplayName(displayName);
        Mockito.when(repository.findByCropNameAndDiseaseCode(cropName, code))
                .thenReturn(Optional.of(knowledge));
        Mockito.when(repository.findFirstByDiseaseCodeIgnoreCase(code))
                .thenReturn(Optional.of(knowledge));
    }

    @Nested
    @DisplayName("Maladies de la base de connaissance")
    class FromKnowledge {

        @Test
        @DisplayName("le nom français remplace la classe du modèle")
        void frenchNameReplacesModelClass() {
            known("tomate", "Late_blight", "Mildiou de la tomate");

            assertThat(labeller.labelFor("tomate", "Late_blight"))
                    .isEqualTo("Mildiou de la tomate");
        }

        @Test
        @DisplayName("le préfixe de culture du modèle vision est retiré avant la recherche")
        void visionPrefixIsStripped() {
            known("tomate", "Late_blight", "Mildiou de la tomate");

            assertThat(labeller.labelFor("tomate", "Tomato___Late_blight"))
                    .as("l'étiquetage est appelé des deux côtés de la normalisation")
                    .isEqualTo("Mildiou de la tomate");
        }

        @Test
        @DisplayName("la culture est prise en compte : « healthy » n'a pas un seul nom")
        void cropDecidesWhenTheCodeIsShared() {
            known("tomate", "healthy", "Tomate saine");
            known("manioc", "healthy", "Manioc sain");

            assertThat(labeller.labelFor("tomate", "healthy")).isEqualTo("Tomate saine");
            assertThat(labeller.labelFor("manioc", "healthy")).isEqualTo("Manioc sain");
        }

        @Test
        @DisplayName("culture inconnue : on nomme quand même, plutôt que de laisser un code")
        void unknownCropStillYieldsAName() {
            known("tomate", "Leaf_Mold", "Cladosporiose");

            assertThat(labeller.labelFor(null, "Leaf_Mold")).isEqualTo("Cladosporiose");
        }

        @Test
        @DisplayName("la casse de la culture n'empêche pas la traduction")
        void cropNameCaseIsIrrelevant() {
            known("tomate", "Late_blight", "Mildiou de la tomate");

            assertThat(labeller.labelFor("TOMATE", "Late_blight"))
                    .as("l'API expose TOMATE, la base stocke tomate")
                    .isEqualTo("Mildiou de la tomate");
        }
    }

    @Nested
    @DisplayName("Issues de la chaîne capteur")
    class SensorOutcomes {

        @Test
        @DisplayName("les catégories du modèle tabulaire ont leur nom, sans passer par la base")
        void tabularCategoriesAreNamed() {
            assertThat(labeller.labelFor("tomate", "STRESS_HYDRIQUE")).isEqualTo("Stress hydrique");
            assertThat(labeller.labelFor("tomate", "CARENCES_NUTRITIVES")).isEqualTo("Carences nutritives");
            assertThat(labeller.labelFor("tomate", "EXCES_EAU")).isEqualTo("Excès d'eau");
            assertThat(labeller.labelFor("manioc", "NORMAL")).isEqualTo("Situation normale");

            Mockito.verify(repository, Mockito.never())
                    .findByCropNameAndDiseaseCode(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Ce qui n'est jamais inventé")
    class NeverInvented {

        @Test
        @DisplayName("un code absent est rendu lisible, pas traduit")
        void unknownCodeIsMadeReadableOnly() {
            assertThat(labeller.labelFor("tomate", "Some_New_Class"))
                    .as("fabriquer un nom français produirait une maladie qui n'existe pas, "
                        + "sous une forme que rien ne distinguerait d'un nom validé")
                    .isEqualTo("Some New Class");
        }

        @Test
        @DisplayName("un code absent ne devient jamais null : l'information brute vaut mieux que rien")
        void unknownCodeIsNeverDropped() {
            assertThat(labeller.labelFor(null, "xyz")).isNotNull();
        }

        @Test
        @DisplayName("code absent ou vide → null, et l'appelant décide quoi afficher")
        void blankYieldsNull() {
            assertThat(labeller.labelFor("tomate", null)).isNull();
            assertThat(labeller.labelFor("tomate", "   ")).isNull();
        }
    }
}
