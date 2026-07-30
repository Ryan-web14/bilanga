package com.sni.bilanga.utils.code;

import java.time.LocalDate;

/**
 * Builds contextual, information-rich codes from a raw sequence counter
 * and entity-specific context segments.
 *
 * Usage pattern:
 *   long seq = CodeComposer.extractSeq(sequenceGenerator.next("entity_code"));
 *   String code = CodeComposer.withMonth("PREFIX", contextAbbrev, LocalDate.now(), seq);
 */
public final class CodeComposer {

    private CodeComposer() {}

    // -------------------------------------------------------------------------
    // Counter extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts the raw counter value from a sequence-engine-generated code.
     * Assumes the counter is the last hyphen-delimited segment (standard pattern).
     * e.g. "RES-000001" → 1,  "BKG-202604-000001" → 1
     */
    public static long extractSeq(String generatedCode) {
        String seqStr = generatedCode.substring(generatedCode.lastIndexOf('-') + 1);
        return Long.parseLong(seqStr);
    }

    // -------------------------------------------------------------------------
    // Abbreviation helper
    // -------------------------------------------------------------------------

    /**
     * Produces a 3-char uppercase alphanumeric abbreviation of any string.
     * Used to embed entity context (type name, status, method…) in codes.
     * e.g. "Salle de reunion" → "SAL",  "COMPANY" → "COM",  "WALLET" → "WAL"
     */
    public static String abbrev(String text) {
        if (text == null || text.isBlank()) return "UNK";
        String clean = text.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) return "UNK";
        return clean.substring(0, Math.min(3, clean.length()));
    }

    // -------------------------------------------------------------------------
    // Code builders · operational entities (8-digit seq)
    // -------------------------------------------------------------------------

    /**
     * PREFIX-CTX-YYYYMM-00000001
     * For monthly-volume entities where context matters (resource, customer, subscription…).
     * e.g. "RES-SAL-202604-00000001"
     */
    public static String withMonth(String prefix, String context, LocalDate date, long seq) {
        return String.format("%s-%s-%d%02d-%08d",
                prefix, context, date.getYear(), date.getMonthValue(), seq);
    }

    /**
     * PREFIX-CTX-YYYYMMDD-00000001
     * For high-volume daily entities where context matters (booking, payment, invoice…).
     * e.g. "BKG-SAL-20260417-00000001"
     */
    public static String withDay(String prefix, String context, LocalDate date, long seq) {
        return String.format("%s-%s-%d%02d%02d-%08d",
                prefix, context, date.getYear(), date.getMonthValue(), date.getDayOfMonth(), seq);
    }

    /**
     * PREFIX-YYYYMM-00000001
     * For monthly entities without a meaningful context segment (member, wallet…).
     * e.g. "MBR-202604-00000001"
     */
    public static String simpleWithMonth(String prefix, LocalDate date, long seq) {
        return String.format("%s-%d%02d-%08d",
                prefix, date.getYear(), date.getMonthValue(), seq);
    }

    /**
     * PREFIX-YYYYMMDD-00000001
     * For daily entities without a meaningful context segment.
     * e.g. "PIN-20260417-00000001"
     */
    public static String simpleWithDay(String prefix, LocalDate date, long seq) {
        return String.format("%s-%d%02d%02d-%08d",
                prefix, date.getYear(), date.getMonthValue(), date.getDayOfMonth(), seq);
    }

    // -------------------------------------------------------------------------
    // Code builders · reference / master data (6-digit seq)
    // -------------------------------------------------------------------------

    /**
     * PREFIX-YYYY-000001
     * For reference/catalog entities that change rarely (resource_type, resource_group…).
     * e.g. "RTY-2026-000001"
     */
    public static String refWithYear(String prefix, LocalDate date, long seq) {
        return String.format("%s-%d-%06d", prefix, date.getYear(), seq);
    }
}
