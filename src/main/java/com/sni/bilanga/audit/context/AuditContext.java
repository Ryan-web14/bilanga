package com.sni.bilanga.audit.context;

import java.util.HashMap;
import java.util.Map;

public final class AuditContext {
    private AuditContext() {}

    private static final ThreadLocal<Map<String, Object>> META =
            ThreadLocal.withInitial(HashMap::new);

    private static final ThreadLocal<Map<String, Object>> DIFF = new ThreadLocal<>();

    public static void putMeta(String key, Object value) {
        META.get().put(key, value);
    }

    public static Map<String, Object> getMeta() {
        return META.get();
    }

    public static void setDiff(Map<String, Object> diff) {
        DIFF.set(diff);
    }

    public static Map<String, Object> getDiff() {
        return DIFF.get();
    }

    public static void clear() {
        META.remove();
        DIFF.remove();
    }
}
