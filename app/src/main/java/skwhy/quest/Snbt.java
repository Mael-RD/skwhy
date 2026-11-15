package skwhy.quest;

import java.util.List;
import java.util.Map;

/**
 * Écriture d'un arbre ({@link Map} / {@link List} / {@link String} / nombres / {@link Boolean}) en NBT texte (SNBT), que
 * les scripts relisent avec {@code nbt compound from "..."}. Un booléen est un octet {@code 1b} / {@code 0b}.
 */
public final class Snbt {

    private Snbt() {}

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object v) {
        if (v instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote(out, String.valueOf(e.getKey()));
                out.append(':');
                write(out, e.getValue());
            }
            out.append('}');
        } else if (v instanceof List<?> list) {
            boolean decimals = false;
            for (Object o : list) if (o instanceof Double || o instanceof Float) decimals = true;
            out.append('[');
            boolean first = true;
            for (Object o : list) {
                if (!first) out.append(',');
                first = false;
                if (decimals && o instanceof Number n) out.append(n.doubleValue()).append('d');
                else write(out, o);
            }
            out.append(']');
        } else if (v instanceof Boolean b) {
            out.append(b ? "1b" : "0b");
        } else if (v instanceof Byte n) {
            out.append(n).append('b');
        } else if (v instanceof Short n) {
            out.append(n).append('s');
        } else if (v instanceof Integer n) {
            out.append(n);
        } else if (v instanceof Long n) {
            out.append(n).append('L');
        } else if (v instanceof Float n) {
            out.append(n).append('f');
        } else if (v instanceof Double n) {
            out.append(n).append('d');
        } else if (v == null) {
            out.append("\"\"");
        } else {
            quote(out, String.valueOf(v));
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
        out.append('"');
    }
}
