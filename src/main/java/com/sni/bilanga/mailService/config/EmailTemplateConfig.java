package com.sni.bilanga.mailService.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Moteur de gabarits pour les courriels.
 *
 * <p>{@code MicrosoftGraphMailProperties} a été retiré : la configuration Graph vit
 * désormais dans {@code BilangaProperties.Email.Graph}, avec le reste des réglages du
 * projet. Deux classes de configuration pour un même réglage est exactement la dérive que
 * le passage à {@code BilangaProperties} avait corrigée — une clé écrite dans le fichier
 * ne se liait pas à celle que le code lisait, et le réglage était ignoré en silence.
 */
@Configuration
public class EmailTemplateConfig {

    @Bean(name = "emailTemplateResolver")
    public ITemplateResolver thymeleafTemplateResolver(){
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        return resolver;
    }

    //@Bean(name = "emailTemplateEngine")
    public SpringTemplateEngine emailTemplateEngine(ITemplateResolver resolver) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }


}
