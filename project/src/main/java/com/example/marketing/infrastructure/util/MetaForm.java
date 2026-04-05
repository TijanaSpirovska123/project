package com.example.marketing.infrastructure.util;



import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class MetaForm {
    private MetaForm() {}

    /** Add only if value is not null/blank */
    public static void add(LinkedMultiValueMap<String,String> p, String k, String v) {
        if (v != null && !v.isBlank()) p.add(k, v);
    }

    /** Meta expects account as "act_<id>" */
    public static String normalizeAct(String raw) {
        return (raw != null && raw.startsWith("act_")) ? raw : ("act_" + raw);
    }

    /** Convenience factory */
    public static LinkedMultiValueMap<String,String> newForm() {
        return new LinkedMultiValueMap<>();
    }
}
