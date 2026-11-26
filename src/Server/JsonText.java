package Server;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Writes JSON text from plain Java values: {@code Map} (object), {@code Collection} (array),
 * {@code String}, {@code Number}, {@code Boolean} and {@code null}. Decimals are rounded to three
 * places; NaN and infinity become {@code null}. No library needed.
 */
final class JsonText {
    private JsonText() {
    }

    static String write(Object value) {
        StringBuilder json = new StringBuilder();
        write(json, value);
        return json.toString();
    }

    static void write(StringBuilder json, Object value) {
        switch (value) {
            case null -> json.append("null");
            case String text -> string(json, text);
            case Boolean bool -> json.append(bool);
            case Double number -> decimal(json, number);
            case Float number -> decimal(json, number.doubleValue());
            case Number number -> json.append(number.longValue());
            case Map<?, ?> map -> {
                json.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    json.append(first ? "" : ",");
                    string(json, String.valueOf(entry.getKey()));
                    json.append(':');
                    write(json, entry.getValue());
                    first = false;
                }
                json.append('}');
            }
            case Collection<?> list -> {
                json.append('[');
                boolean first = true;
                for (Object item : list) {
                    json.append(first ? "" : ",");
                    write(json, item);
                    first = false;
                }
                json.append(']');
            }
            default -> string(json, value.toString());
        }
    }

    private static void decimal(StringBuilder json, double number) {
        if (!Double.isFinite(number)) {
            json.append("null");
            return;
        }
        json.append(BigDecimal.valueOf(Math.round(number * 1000) / 1000.0).stripTrailingZeros().toPlainString());
    }

    private static void string(StringBuilder json, String text) {
        json.append('"');
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
