package skwhy.quest.editor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Conversion entre les arbres de données des quêtes ({@code Map} / {@code List} / texte / nombre / booléen) et le JSON
 * échangé avec le navigateur. Les entiers restent des entiers (jamais {@code 1.0}), l'ordre des clés est conservé.
 */
public final class EditorJson {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private EditorJson() {}

    public static String write(Object tree) {
        return GSON.toJson(toElement(tree));
    }

    public static JsonElement toElement(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Map<?, ?> map) {
            JsonObject out = new JsonObject();
            for (Map.Entry<?, ?> e : map.entrySet()) out.add(String.valueOf(e.getKey()), toElement(e.getValue()));
            return out;
        }
        if (value instanceof List<?> list) {
            JsonArray out = new JsonArray();
            for (Object o : list) out.add(toElement(o));
            return out;
        }
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Byte || value instanceof Short) return new JsonPrimitive(((Number) value).intValue());
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return new JsonPrimitive(String.valueOf(d));
            return new JsonPrimitive(n);
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    public static Object parse(String json) {
        return fromElement(JsonParser.parseString(json));
    }

    public static Object fromElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) out.put(e.getKey(), fromElement(e.getValue()));
            return out;
        }
        if (element.isJsonArray()) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (JsonElement e : element.getAsJsonArray()) out.add(fromElement(e));
            return out;
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return number(p.getAsNumber().toString());
        return p.getAsString();
    }

    private static Number number(String literal) {
        try {
            if (literal.indexOf('.') < 0 && literal.indexOf('e') < 0 && literal.indexOf('E') < 0) {
                long l = Long.parseLong(literal);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                return l;
            }
            return Double.parseDouble(literal);
        } catch (NumberFormatException e) {
            return Double.parseDouble(literal);
        }
    }

    /** Empreinte courte d'un arbre (change dès qu'une valeur change) : sert au contrôle de version. */
    public static String version(Object tree) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(write(tree).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
