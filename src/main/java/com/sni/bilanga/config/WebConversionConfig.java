package com.sni.bilanga.config;

import com.sni.bilanga.enums.DomainEnums;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rend la lecture des énumérations insensible à la casse dans les paramètres
 * d'URL.
 *
 * <p>Le convertisseur par défaut de Spring exige la constante exacte :
 * {@code ?status=ACTIVE} passait, {@code ?status=active} donnait un 400. Rien
 * ne justifie cette rigueur du point de vue de l'appelant — d'autant que la
 * même valeur, écrite dans un corps JSON, est désormais acceptée dans
 * n'importe quelle casse. Deux chemins d'entrée pour une même valeur ne
 * doivent pas avoir deux comportements.
 *
 * <p>Une valeur réellement inconnue continue d'échouer, et le gestionnaire
 * global répond alors 400 en listant les valeurs acceptées.
 */
@Configuration
public class WebConversionConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverterFactory(new CaseInsensitiveEnumConverterFactory());
    }

    static class CaseInsensitiveEnumConverterFactory implements ConverterFactory<String, Enum<?>> {

        @Override
        @NonNull
        public <E extends Enum<?>> Converter<String, E> getConverter(@NonNull Class<E> targetType) {
            return source -> {
                if (source == null || source.isBlank()) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                Class<? extends Enum> raw = (Class<? extends Enum>) resolve(targetType);

                @SuppressWarnings("unchecked")
                E parsed = (E) DomainEnums.parse(raw, source);

                if (parsed == null) {
                    // Message repris par MethodArgumentTypeMismatchException, qui
                    // énumère les valeurs acceptées dans la réponse.
                    throw new IllegalArgumentException(
                            "Valeur inconnue pour " + targetType.getSimpleName() + " : " + source);
                }
                return parsed;
            };
        }

        /** Une constante dotée d'un corps a sa propre classe anonyme : remonter à l'énumération. */
        private Class<?> resolve(Class<?> targetType) {
            Class<?> type = targetType;
            while (type != null && !type.isEnum()) {
                type = type.getSuperclass();
            }
            return type == null ? targetType : type;
        }
    }
}
