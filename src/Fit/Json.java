package Fit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader, so the project needs no JSON library.
 * Objects become {@code Map<String, Object>}, arrays {@code List<Object>}, numbers {@code Double},
 * plus {@code String}, {@code Boolean} and {@code null}.
 */
public final class Json {
    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /** Parses a whole JSON document (a leading byte-order mark is ignored). */
    public static Object parse(String text) {
        Json json = new Json(text.startsWith("﻿") ? text.substring(1) : text);
        Object value = json.value();
        json.skipWhitespace();
        if (json.position != json.text.length()) {
            throw json.error("Unexpected text after the JSON value");
        }
        return value;
    }

    private Object value() {
        skipWhitespace();
        if (position >= text.length()) {
            throw error("Unexpected end of file");
        }
        char next = text.charAt(position);
        return switch (next) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        position++; // {
        skipWhitespace();
        if (peek('}')) {
            position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) {
                throw error("Expected a field name");
            }
            String key = string();
            skipWhitespace();
            expect(':');
            map.put(key, value());
            skipWhitespace();
            if (peek(',')) {
                position++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        position++; // [
        skipWhitespace();
        if (peek(']')) {
            position++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWhitespace();
            if (peek(',')) {
                position++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        position++; // opening quote
        StringBuilder result = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') {
                return result.toString();
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (position >= text.length()) {
                break;
            }
            char escaped = text.charAt(position++);
            switch (escaped) {
                case 'n' -> result.append('\n');
                case 't' -> result.append('\t');
                case 'r' -> result.append('\r');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'u' -> {
                    if (position + 4 > text.length()) {
                        throw error("Broken \\u escape");
                    }
                    result.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> result.append(escaped); // \" \\ \/
            }
        }
        throw error("Unterminated string");
    }

    private Double number() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        if (start == position) {
            throw error("Unexpected character '" + text.charAt(position) + "'");
        }
        try {
            return Double.parseDouble(text.substring(start, position));
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) {
            throw error("Unexpected text");
        }
        position += word.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }
        position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + position);
    }
}
