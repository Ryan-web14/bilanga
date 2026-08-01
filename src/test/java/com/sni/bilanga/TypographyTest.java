package com.sni.bilanga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le tiret cadratin ne doit pas revenir dans un texte que le système émet.
 *
 * <h2>Pourquoi un test sur les sources</h2>
 *
 * <p>C'est une contrainte de forme, pas de comportement : aucun test fonctionnel ne
 * peut l'attraper, puisqu'un message reste correct avec ou sans. Elle se réintroduit
 * pourtant à chaque message ajouté, un par un, sans que personne ne le remarque avant
 * qu'un écran ne présente deux typographies côte à côte.
 *
 * <p>Le contrôle porte sur les lignes de code et les gabarits, <strong>pas sur les
 * commentaires ni la javadoc</strong> : ceux-ci ne sortent jamais vers un utilisateur,
 * et les réécrire coûterait un diff considérable pour aucun effet visible.
 *
 * <p>Les gabarits de courriel sont contrôlés en entier, commentaires HTML compris :
 * un commentaire HTML voyage dans le message et se lit dans la source reçue.
 */
@DisplayName("Typographie : pas de tiret cadratin dans les textes émis")
class TypographyTest {

    private static final char EM_DASH = '—';

    @Test
    @DisplayName("aucun tiret cadratin hors commentaire dans les sources Java")
    void noEmDashInJavaCode() throws IOException {
        List<String> offenders = scanJava(Path.of("src", "main", "java"));

        assertThat(offenders)
                .as("employez la virgule, le deux-points, la parenthèse ou une phrase "
                    + "séparée. Les commentaires et la javadoc ne sont pas concernés.")
                .isEmpty();
    }

    @Test
    @DisplayName("aucun tiret cadratin dans les gabarits, commentaires HTML compris")
    void noEmDashInTemplates() throws IOException {
        Path templates = Path.of("src", "main", "resources", "templates");
        List<String> offenders = new ArrayList<>();

        if (Files.isDirectory(templates)) {
            try (Stream<Path> files = Files.walk(templates)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).indexOf(EM_DASH) >= 0) {
                            offenders.add(file.getFileName() + ":" + (i + 1));
                        }
                    }
                }
            }
        }

        assertThat(offenders)
                .as("un commentaire HTML voyage dans le courriel et se lit dans la source")
                .isEmpty();
    }

    /**
     * Rend les emplacements fautifs, en écartant les lignes de commentaire.
     *
     * <p>Le repérage est volontairement rustique : une ligne dont le premier caractère
     * non blanc ouvre ou poursuit un commentaire est ignorée. Il laisserait passer un
     * tiret situé après du code sur la même ligne qu'un commentaire de fin de ligne,
     * cas qui ne s'est jamais présenté ici. Un analyseur syntaxique complet coûterait
     * beaucoup pour ce gain.
     */
    private List<String> scanJava(Path root) throws IOException {
        List<String> offenders = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return offenders;
        }

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.indexOf(EM_DASH) < 0) {
                        continue;
                    }
                    String trimmed = line.stripLeading();
                    if (trimmed.startsWith("*") || trimmed.startsWith("//")
                            || trimmed.startsWith("/*")) {
                        continue;
                    }
                    offenders.add(file.getFileName() + ":" + (i + 1) + "  " + trimmed.strip());
                }
            }
        }
        return offenders;
    }
}
