package nexus.util.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NexusOmegaX dependency-free JSON engine.
 * <p>
 * Provides a complete JSON object model plus a strict parser and a pretty writer.
 * Used by ConfigManager, ThemeManager, HUD layouts, waypoints, launcher settings
 * and every persistence layer of the client. No external dependencies so the same
 * source compiles on Minecraft 1.16.5 (Java 8) and 1.21.x (Java 21).
 */
public abstract class Json {

    // ------------------------------------------------------------------ types

    public static final class JsonNull extends Json {
        public static final JsonNull INSTANCE = new JsonNull();

        private JsonNull() {
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public String toString() {
            return "null";
        }
    }

    public static final class JsonBool extends Json {
        private final boolean value;

        public JsonBool(boolean value) {
            this.value = value;
        }

        @Override
        public boolean isBoolean() {
            return true;
        }

        public boolean value() {
            return value;
        }

        @Override
        public String toString() {
            return value ? "true" : "false";
        }
    }

    public static final class JsonNumber extends Json {
        private final String raw;

        public JsonNumber(String raw) {
            this.raw = raw;
        }

        public JsonNumber(double value) {
            if (value == (long) value && !Double.isInfinite(value) && !Double.isNaN(value)) {
                this.raw = Long.toString((long) value);
            } else {
                this.raw = Double.toString(value);
            }
        }

        @Override
        public boolean isNumber() {
            return true;
        }

        public double asDouble(double fallback) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        public int asInt(int fallback) {
            try {
                return (int) Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        public long asLong(long fallback) {
            try {
                return (long) Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        public float asFloat(float fallback) {
            return (float) asDouble(fallback);
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    public static final class JsonString extends Json {
        private final String value;

        public JsonString(String value) {
            this.value = value == null ? "" : value;
        }

        @Override
        public boolean isString() {
            return true;
        }

        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return quote(value);
        }
    }

    public static final class JsonArray extends Json {
        private final List<Json> items = new ArrayList<Json>();

        @Override
        public boolean isArray() {
            return true;
        }

        public int size() {
            return items.size();
        }

        public Json get(int index) {
            return items.get(index);
        }

        public JsonArray add(Json value) {
            items.add(value == null ? JsonNull.INSTANCE : value);
            return this;
        }

        public JsonArray add(String value) {
            return add(new JsonString(value));
        }

        public JsonArray add(double value) {
            return add(new JsonNumber(value));
        }

        public JsonArray add(boolean value) {
            return add(new JsonBool(value));
        }

        public List<Json> items() {
            return items;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(items.get(i).toString());
            }
            return sb.append(']').toString();
        }
    }

    public static final class JsonObject extends Json {
        private final Map<String, Json> members = new LinkedHashMap<String, Json>();

        @Override
        public boolean isObject() {
            return true;
        }

        public int size() {
            return members.size();
        }

        public boolean has(String key) {
            return members.containsKey(key) && !members.get(key).isNull();
        }

        public Json get(String key) {
            Json value = members.get(key);
            return value == null ? JsonNull.INSTANCE : value;
        }

        public JsonObject set(String key, Json value) {
            if (value == null) {
                members.put(key, JsonNull.INSTANCE);
            } else {
                members.put(key, value);
            }
            return this;
        }

        public JsonObject set(String key, String value) {
            return set(key, new JsonString(value));
        }

        public JsonObject set(String key, double value) {
            return set(key, new JsonNumber(value));
        }

        public JsonObject set(String key, boolean value) {
            return set(key, new JsonBool(value));
        }

        public JsonObject set(String key, JsonObject value) {
            return set(key, (Json) value);
        }

        public JsonObject object(String key) {
            Json value = get(key);
            if (value instanceof JsonObject) {
                return (JsonObject) value;
            }
            JsonObject created = new JsonObject();
            set(key, created);
            return created;
        }

        public JsonObject getObject(String key, JsonObject fallback) {
            Json value = get(key);
            return value instanceof JsonObject ? (JsonObject) value : fallback;
        }

        public JsonArray array(String key) {
            Json value = get(key);
            if (value instanceof JsonArray) {
                return (JsonArray) value;
            }
            JsonArray created = new JsonArray();
            set(key, created);
            return created;
        }

        public String string(String key, String fallback) {
            Json value = get(key);
            return value instanceof JsonString ? ((JsonString) value).value() : fallback;
        }

        public boolean bool(String key, boolean fallback) {
            Json value = get(key);
            if (value instanceof JsonBool) {
                return ((JsonBool) value).value();
            }
            if (value instanceof JsonNumber) {
                return ((JsonNumber) value).asInt(0) != 0;
            }
            if (value instanceof JsonString) {
                return Boolean.parseBoolean(((JsonString) value).value());
            }
            return fallback;
        }

        public int int_(String key, int fallback) {
            Json value = get(key);
            if (value instanceof JsonNumber) {
                return ((JsonNumber) value).asInt(fallback);
            }
            if (value instanceof JsonString) {
                try {
                    return Integer.parseInt(((JsonString) value).value());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        public long long_(String key, long fallback) {
            Json value = get(key);
            if (value instanceof JsonNumber) {
                return ((JsonNumber) value).asLong(fallback);
            }
            if (value instanceof JsonString) {
                try {
                    return Long.parseLong(((JsonString) value).value());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        public double double_(String key, double fallback) {
            Json value = get(key);
            if (value instanceof JsonNumber) {
                return ((JsonNumber) value).asDouble(fallback);
            }
            if (value instanceof JsonString) {
                try {
                    return Double.parseDouble(((JsonString) value).value());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        public float float_(String key, float fallback) {
            return (float) double_(key, fallback);
        }

        public Json remove(String key) {
            return members.remove(key);
        }

        /** Deep copy: used by config backups and profile cloning. */
        public JsonObject copy() {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, Json> e : members.entrySet()) {
                copy.set(e.getKey(), copyOf(e.getValue()));
            }
            return copy;
        }

        public Map<String, Json> members() {
            return members;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Json> e : members.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(e.getKey())).append(':').append(e.getValue().toString());
            }
            return sb.append('}').toString();
        }
    }

    // ------------------------------------------------------------------ model

    public boolean isObject() {
        return false;
    }

    public boolean isArray() {
        return false;
    }

    public boolean isString() {
        return false;
    }

    public boolean isNumber() {
        return false;
    }

    public boolean isBoolean() {
        return false;
    }

    public boolean isNull() {
        return false;
    }

    // --------------------------------------------------------------- factories

    public static JsonObject object() {
        return new JsonObject();
    }

    public static JsonArray array() {
        return new JsonArray();
    }

    public static Json of(String value) {
        return new JsonString(value);
    }

    public static Json of(double value) {
        return new JsonNumber(value);
    }

    public static Json of(boolean value) {
        return new JsonBool(value);
    }

    // ----------------------------------------------------------------- parsing

    public static JsonObject parseObject(String text) {
        Json value = parse(text);
        return value instanceof JsonObject ? (JsonObject) value : new JsonObject();
    }

    public static Json parse(String text) {
        if (text == null) {
            return JsonNull.INSTANCE;
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Json value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.position < parser.length) {
            throw new JsonException("Trailing content at " + parser.position);
        }
        return value;
    }

    public static Json parseLenient(String text) {
        try {
            return parse(text);
        } catch (RuntimeException e) {
            return JsonNull.INSTANCE;
        }
    }

    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private final int length;
        private int position;

        Parser(String text) {
            this.text = text;
            this.length = text.length();
        }

        void skipWhitespace() {
            while (position < length) {
                char c = text.charAt(position);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
                    position++;
                } else {
                    break;
                }
            }
        }

        Json parseValue() {
            if (position >= length) {
                throw new JsonException("Unexpected end of input");
            }
            char c = text.charAt(position);
            switch (c) {
                case '{':
                    return parseObjectValue();
                case '[':
                    return parseArrayValue();
                case '"':
                    return new JsonString(parseString());
                case 't':
                    expect("true");
                    return new JsonBool(true);
                case 'f':
                    expect("false");
                    return new JsonBool(false);
                case 'n':
                    expect("null");
                    return JsonNull.INSTANCE;
                default:
                    return parseNumber();
            }
        }

        private void expect(String word) {
            if (!text.startsWith(word, position)) {
                throw new JsonException("Invalid literal at " + position);
            }
            position += word.length();
        }

        private JsonObject parseObjectValue() {
            JsonObject object = new JsonObject();
            position++; // {
            skipWhitespace();
            if (position < length && text.charAt(position) == '}') {
                position++;
                return object;
            }
            while (position < length) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (position >= length || text.charAt(position) != ':') {
                    throw new JsonException("Expected ':' at " + position);
                }
                position++;
                skipWhitespace();
                object.set(key, parseValue());
                skipWhitespace();
                if (position >= length) {
                    throw new JsonException("Unterminated object");
                }
                char c = text.charAt(position);
                if (c == ',') {
                    position++;
                    continue;
                }
                if (c == '}') {
                    position++;
                    return object;
                }
                throw new JsonException("Expected ',' or '}' at " + position);
            }
            throw new JsonException("Unterminated object");
        }

        private JsonArray parseArrayValue() {
            JsonArray array = new JsonArray();
            position++; // [
            skipWhitespace();
            if (position < length && text.charAt(position) == ']') {
                position++;
                return array;
            }
            while (position < length) {
                skipWhitespace();
                array.add(parseValue());
                skipWhitespace();
                if (position >= length) {
                    throw new JsonException("Unterminated array");
                }
                char c = text.charAt(position);
                if (c == ',') {
                    position++;
                    continue;
                }
                if (c == ']') {
                    position++;
                    return array;
                }
                throw new JsonException("Expected ',' or ']' at " + position);
            }
            throw new JsonException("Unterminated array");
        }

        private String parseString() {
            if (position >= length || text.charAt(position) != '"') {
                throw new JsonException("Expected string at " + position);
            }
            position++;
            StringBuilder sb = new StringBuilder();
            while (position < length) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (position >= length) {
                    throw new JsonException("Dangling escape");
                }
                char esc = text.charAt(position++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (position + 4 > length) {
                            throw new JsonException("Bad unicode escape");
                        }
                        sb.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default:
                        throw new JsonException("Bad escape \\" + esc);
                }
            }
            throw new JsonException("Unterminated string");
        }

        private Json parseNumber() {
            int start = position;
            if (position < length && (text.charAt(position) == '-' || text.charAt(position) == '+')) {
                position++;
            }
            while (position < length) {
                char c = text.charAt(position);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    position++;
                } else {
                    break;
                }
            }
            String raw = text.substring(start, position);
            if (raw.isEmpty()) {
                throw new JsonException("Invalid number at " + start);
            }
            return new JsonNumber(raw);
        }
    }

    private static Json copyOf(Json value) {
        if (value instanceof JsonObject) {
            return ((JsonObject) value).copy();
        }
        if (value instanceof JsonArray) {
            JsonArray copy = new JsonArray();
            for (Json item : ((JsonArray) value).items()) {
                copy.add(copyOf(item));
            }
            return copy;
        }
        return value;
    }

    // ------------------------------------------------------------------ writing

    public static String write(Json value) {
        return value == null ? "null" : value.toString();
    }

    public static String pretty(Json value) {
        StringBuilder sb = new StringBuilder();
        writePretty(value, sb, 0);
        return sb.toString();
    }

    private static void writePretty(Json value, StringBuilder sb, int indent) {
        if (value instanceof JsonObject) {
            JsonObject object = (JsonObject) value;
            if (object.size() == 0) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<String, Json> e : object.members().entrySet()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                pad(sb, indent + 1);
                sb.append(quote(e.getKey())).append(": ");
                writePretty(e.getValue(), sb, indent + 1);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof JsonArray) {
            JsonArray array = (JsonArray) value;
            if (array.size() == 0) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    sb.append(",\n");
                }
                pad(sb, indent + 1);
                writePretty(array.get(i), sb, indent + 1);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append(']');
        } else {
            sb.append(value == null ? "null" : value.toString());
        }
    }

    private static void pad(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
