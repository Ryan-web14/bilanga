package com.sni.bilanga.utils.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Écrit un entier 64 bits comme un <em>nombre</em>, à rebours de la règle globale.
 *
 * <p>{@code JacksonConfig} sérialise tout {@code Long} en chaîne pour protéger les
 * identifiants Snowflake de l'arrondi JavaScript. C'est la bonne règle par défaut :
 * l'oublier sur un identifiant produit un défaut silencieux et coûteux.
 *
 * <p>Mais elle attrapait aussi les compteurs, qui n'ont rien à craindre de la
 * précision : le client recevait {@code "totalElements": "42"} et devait convertir
 * avant tout calcul. Cette annotation rétablit le type naturel là où c'est
 * pertinent, sans affaiblir la protection des identifiants — se tromper ici
 * donne une chaîne inutile, jamais une valeur fausse.
 */
public class CounterSerializer extends StdSerializer<Long> {

    public CounterSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializationContext context) {
        if (value == null) {
            generator.writeNull();
        } else {
            generator.writeNumber(value.longValue());
        }
    }
}
