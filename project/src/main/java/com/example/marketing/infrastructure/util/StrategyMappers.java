package com.example.marketing.infrastructure.util;


import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

public final class StrategyMappers {
    private StrategyMappers() {}

    public static String s(Map<String,Object> m, String key) {
        Object v = m != null ? m.get(key) : null;
        return v != null ? v.toString() : null;
    }

    public static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception ignored) { return null; }
    }

    public static LocalDateTime ldt(Object v) {
        if (v == null) return null;
        try { return OffsetDateTime.parse(v.toString()).toLocalDateTime(); } catch (Exception ignored) { return null; }
    }
}

