package com.sni.bilanga.generator;



import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Générateur d'identifiants Snowflake : horodatage + identifiant machine +
 * séquence, sur 64 bits.
 *
 * <p><strong>Journalisation.</strong> Chaque identifiant généré donnait lieu à
 * une douzaine de {@code System.out.println} — un bloc de « vérification » qui
 * réextrayait les composants et les imprimait. Sur une ingestion de deux cents
 * relevés, cela produisait des milliers de lignes sur la sortie standard, hors de
 * toute configuration de journalisation : impossible à filtrer, impossible à
 * désactiver sans recompiler.
 *
 * <p>La vérification elle-même reste utile pour déboguer l'allocation de bits.
 * Elle est conservée, mais en {@code TRACE} et derrière un test d'activation —
 * de sorte que le coût de formatage n'est pas payé quand personne n'écoute.
 */
public class GeneratorOfId implements IdentifierGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeneratorOfId.class);

    //Custom epoch in bits
    private static long CUSTOM_EPOCH = 49852800000L; //this 30-07-2025

    //Bit allocation
    //12 bits
    private static int SEQUENCE_BITS = 12;
    //10 bits
    private static int MACHINE_ID_BITS = 10;

    //MAXIMUM VALUE
    private static long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1; // 4096
    private static long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1; //1093 values possible

    //Bit shift
    private static int MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static int TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private long lastTimestamp = -1L;
    private long sequence = 0;
    private long machineId;

    public GeneratorOfId(){
        this.machineId = getMachineId();
    }

    @Override
    public synchronized Serializable generate (SharedSessionContractImplementor session, Object object){
        return generateId();
    }

    public synchronized Long generateId() {

        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;

        if(timestamp < 0){
            throw new IllegalStateException("Time is negative, clock moved backwards");
        }

        if(timestamp == lastTimestamp){
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // If sequence overflows, wait for next millisecond
            if (sequence == 0) {
                timestamp = waitForNextMillis(timestamp);
            }
        } else {
            // Reset sequence for new millisecond
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        long finalId =  (timestamp << TIMESTAMP_SHIFT) |
                (machineId << MACHINE_ID_SHIFT) | sequence;


        if (log.isTraceEnabled()) {
            verifyIdComponents(finalId);
        }

        return finalId;
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        }
        return timestamp;
    }

    private long getMachineId() {

        try {
            String hostName = System.getProperty("user.name", "unknown");
            int hashCode = Math.abs(hostName.hashCode());
            return hashCode % (MAX_MACHINE_ID + 1);
        } catch (Exception e) {
            // Fallback to random if system properties are not available
            return ThreadLocalRandom.current().nextLong(0, MAX_MACHINE_ID + 1);
        }
    }

    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
    }

    public static long extractMachineId(long id) {
        return (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }

    public static long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }


    /**
     * Réextrait les composants et vérifie qu'ils correspondent à ce qui a été
     * encodé. N'a d'intérêt que pour valider l'allocation de bits ; appelée
     * uniquement lorsque {@code TRACE} est actif sur cette classe.
     */
    private void verifyIdComponents(long id) {
        long extractedTimestamp = extractTimestamp(id);
        long extractedMachineId = extractMachineId(id);
        long extractedSequence = extractSequence(id);

        boolean consistent = (extractedTimestamp - CUSTOM_EPOCH) == lastTimestamp
                && extractedMachineId == machineId
                && extractedSequence == sequence;

        log.trace("Snowflake {} · horodatage {} · machine {} · séquence {} · cohérent {}",
                id, extractedTimestamp, extractedMachineId, extractedSequence, consistent);

        if (!consistent) {
            // Une incohérence signalerait une erreur d'allocation de bits : le
            // dire au niveau warn, sinon elle resterait noyée dans le trace.
            log.warn("Incohérence d'encodage Snowflake sur l'identifiant {} : "
                    + "l'allocation de bits ne se relit pas.", id);
        }
    }


}


