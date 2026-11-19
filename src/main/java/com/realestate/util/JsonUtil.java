package com.realestate.util;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;

/**
 * Lightweight zero-dependency JSON formatting & parsing utility for REST APIs
 * and Supabase PostgREST communication.
 */
public final class JsonUtil {

    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a standardized JSON success response.
     */
    public static String success(String message, String dataJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"success\",\"message\":").append(escape(message));
        if (dataJson != null && !dataJson.trim().isEmpty()) {
            sb.append(",\"data\":").append(dataJson);
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Creates a standardized JSON error response.
     */
    public static String error(String message, int errorCode) {
        return "{\"status\":\"error\",\"code\":" + errorCode + ",\"message\":" + escape(message) + "}";
    }

    /**
     * Escapes standard characters for JSON string values.
     */
    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Serializes a Java Map, Collection, String, Number, Boolean, or null to JSON.
     */
    @SuppressWarnings("unchecked")
    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return escape((String) value);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Date || value instanceof Time || value instanceof Timestamp) {
            return escape(value.toString());
        }
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append(escape(e.getKey())).append(":").append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Collection) {
            Collection<?> col = (Collection<?>) value;
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Object item : col) {
                if (i++ > 0) sb.append(",");
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return escape(value.toString());
    }

    /**
     * Parses a JSON string into Map<String, Object>, List<Object>, String, Number, Boolean, or null.
     */
    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        return new Parser(json).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseListOfMaps(String json) {
        Object parsed = parse(json);
        List<Map<String, Object>> result = new ArrayList<>();
        if (parsed instanceof List) {
            for (Object item : (List<?>) parsed) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
        } else if (parsed instanceof Map) {
            result.add((Map<String, Object>) parsed);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> row, String key) {
        if (row == null) return null;
        Object val = row.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                return (Map<String, Object>) list.get(0);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getList(Map<String, Object> row, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (row == null) return out;
        Object val = row.get(key);
        if (val instanceof List) {
            for (Object o : (List<?>) val) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    public static String getString(Map<String, Object> row, String key) {
        if (row == null) return null;
        Object val = row.get(key);
        return val != null ? val.toString() : null;
    }

    public static int getInt(Map<String, Object> row, String key) {
        if (row == null) return 0;
        Object val = row.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt(((String) val).trim()); } catch (Exception ignored) {}
        }
        return 0;
    }

    public static double getDouble(Map<String, Object> row, String key) {
        if (row == null) return 0.0;
        Object val = row.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble(((String) val).trim()); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    public static boolean getBoolean(Map<String, Object> row, String key) {
        if (row == null) return false;
        Object val = row.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return "true".equalsIgnoreCase((String) val);
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return false;
    }

    public static Date getDate(Map<String, Object> row, String key) {
        String s = getString(row, key);
        if (s == null || s.length() < 10) return null;
        try { return Date.valueOf(s.substring(0, 10)); } catch (Exception e) { return null; }
    }

    public static Time getTime(Map<String, Object> row, String key) {
        String s = getString(row, key);
        if (s == null || s.isEmpty()) return null;
        try {
            if (s.length() == 5) s += ":00";
            if (s.length() > 8) s = s.substring(0, 8);
            return Time.valueOf(s);
        } catch (Exception e) { return null; }
    }

    public static Timestamp getTimestamp(Map<String, Object> row, String key) {
        String s = getString(row, key);
        if (s == null || s.isEmpty()) return null;
        try {
            String normalized = s.replace('T', ' ');
            int plus = normalized.indexOf('+');
            if (plus > 0) normalized = normalized.substring(0, plus);
            if (normalized.endsWith("Z")) normalized = normalized.substring(0, normalized.length() - 1);
            if (normalized.length() == 10) normalized += " 00:00:00";
            return Timestamp.valueOf(normalized);
        } catch (Exception e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= s.length()) return null;
            char c = s.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' && s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (c == 'f' && s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (c == 'n' && s.startsWith("null", pos)) { pos += 4; return null; }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return map; }
            while (pos < s.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos < s.length() && s.charAt(pos) == ':') pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                if (pos < s.length() && s.charAt(pos) == '}') { pos++; break; }
                break;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return list; }
            while (pos < s.length()) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                if (pos < s.length() && s.charAt(pos) == ']') { pos++; break; }
                break;
            }
            return list;
        }

        private String parseString() {
            if (pos < s.length() && s.charAt(pos) == '"') pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 <= s.length()) {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                                pos += 4;
                            }
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = s.substring(start, pos);
            if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                return Double.parseDouble(numStr);
            }
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }

        private void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}
