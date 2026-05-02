package com.santiya.localaihub.windows;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class JsonUtil {
    private JsonUtil() {
    }

    static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + escape(s) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append(stringify(String.valueOf(entry.getKey())));
                builder.append(':');
                builder.append(stringify(entry.getValue()));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(stringify(list.get(i)));
            }
            builder.append(']');
            return builder.toString();
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            return stringify(List.of(array));
        }
        return stringify(value.toString());
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
