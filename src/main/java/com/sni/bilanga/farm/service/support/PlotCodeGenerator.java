package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.farm.repository.PlotRepository;
import com.sni.bilanga.utils.code.CodeComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Attribue la référence lisible d'une parcelle : {@code PARC-2026-000014}.
 *
 * <p><strong>Pourquoi une référence en plus de l'identifiant.</strong> Les
 * identifiants sont des Snowflake à dix-neuf chiffres : ils ne se dictent pas au
 * téléphone, ne se recopient pas sur un carnet et ne se retiennent pas. Or le
 * terrain communique à l'oral et sur papier. Un code court porte l'année, ce qui
 * suffit à situer la parcelle dans le temps sans consulter la base.
 *
 * <p>Le numéro vient d'une séquence PostgreSQL, pas d'un comptage : deux
 * créations simultanées obtiendraient sinon le même code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlotCodeGenerator {

    private static final String PREFIX = "PARC";

    /**
     * Bornée : si la séquence produisait durablement des codes déjà pris — ce
     * qui supposerait des codes saisis à la main entrant en collision — mieux
     * vaut renoncer au code que boucler indéfiniment à la création.
     */
    private static final int MAX_ATTEMPTS = 5;

    private final PlotRepository plotRepository;

    /**
     * @return la référence attribuée, ou {@code null} si aucune n'a pu l'être.
     *         Une parcelle sans code reste parfaitement utilisable : ce n'est
     *         pas une raison de refuser sa création.
     */
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = CodeComposer.refWithYear(PREFIX, LocalDate.now(),
                    plotRepository.nextPlotCodeSequence());

            if (!plotRepository.existsByPlotCode(code)) {
                return code;
            }
        }

        log.warn("Référence de parcelle non attribuée après {} tentatives.", MAX_ATTEMPTS);
        return null;
    }
}
