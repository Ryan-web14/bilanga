package com.sni.bilanga;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Démarrage du contexte complet.
 *
 * <p><strong>Marqué {@code integration}, et donc écarté par défaut.</strong> Ce
 * test démarre Spring en entier : Flyway s'exécute, Hibernate valide le schéma,
 * et il exige un PostgreSQL joignable. C'est sa valeur — c'est le seul test qui
 * vérifie que les migrations et les entités s'accordent — mais c'était aussi son
 * coût : sur un poste sans base, « mvn test » échouait, ce qui dissuadait de
 * lancer la commande, et donc d'écrire d'autres tests.
 *
 * <p>Les tests des classes de {@code service/support} n'ont besoin de rien et
 * s'exécutent en quelques secondes. Les séparer permet aux deux d'exister.
 *
 * <pre>
 * mvn test                              → tests unitaires seuls (aucune base)
 * mvn test -Dtest.excludedGroups=       → tout, base requise
 * mvn test -Dgroups=integration         → celui-ci seulement
 * </pre>
 */
@Tag("integration")
@SpringBootTest
class BilangaApplicationTests {

    @Test
    void contextLoads() {
    }

}
