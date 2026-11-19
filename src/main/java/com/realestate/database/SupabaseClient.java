package com.realestate.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

import com.realestate.util.JsonUtil;

/**
 * Thread-safe Singleton Supabase PostgREST client.
 * Reads SUPABASE_URL and SUPABASE_KEY from environment variables or .env file.
 */
public final class SupabaseClient {

    private static volatile SupabaseClient instance;

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    private SupabaseClient() {
        Map<String, String> dotEnv = loadDotEnv();
        String url = getConfig("SUPABASE_URL", dotEnv);
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.apiKey = getConfig("SUPABASE_KEY", dotEnv);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public static SupabaseClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) {
                    instance = new SupabaseClient();
                }
            }
        }
        return instance;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isEmpty() && apiKey != null && !apiKey.isEmpty();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean testConnection() {
        if (!isConfigured()) return false;
        try {
            List<Map<String, Object>> rows = get("property_categories?select=category_id&limit=1");
            return rows != null;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> get(String resourceAndQuery) throws SQLException {
        String body = sendRequest("GET", resourceAndQuery, null, null);
        return JsonUtil.parseListOfMaps(body);
    }

    public Map<String, Object> post(String table, Map<String, Object> payload) throws SQLException {
        String body = sendRequest("POST", table, JsonUtil.toJson(payload), "return=representation");
        List<Map<String, Object>> rows = JsonUtil.parseListOfMaps(body);
        return rows.isEmpty() ? new HashMap<>() : rows.get(0);
    }

    public void postBatch(String table, List<Map<String, Object>> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) return;
        sendRequest("POST", table, JsonUtil.toJson(rows), "return=minimal");
    }

    public List<Map<String, Object>> patch(String resourceAndQuery, Map<String, Object> payload) throws SQLException {
        String body = sendRequest("PATCH", resourceAndQuery, JsonUtil.toJson(payload), "return=representation");
        return JsonUtil.parseListOfMaps(body);
    }

    public boolean delete(String resourceAndQuery) throws SQLException {
        String body = sendRequest("DELETE", resourceAndQuery, null, "return=representation");
        List<Map<String, Object>> rows = JsonUtil.parseListOfMaps(body);
        return !rows.isEmpty();
    }

    public static String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sendRequest(String method, String resourceAndQuery, String jsonBody, String prefer) throws SQLException {
        if (!isConfigured()) {
            throw new SQLException("Supabase is not configured. Set SUPABASE_URL and SUPABASE_KEY in .env.");
        }
        try {
            String fullUrl = baseUrl + "/rest/v1/" + resourceAndQuery;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", apiKey)
                    .header("User-Agent", "RealEstateBackend/1.0")
                    .header("Accept", "application/json");

            if (prefer != null) {
                builder.header("Prefer", prefer);
            }

            if ("POST".equals(method) || "PATCH".equals(method)) {
                builder.header("Content-Type", "application/json; charset=UTF-8");
                builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}", StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new SQLException("Supabase API error (HTTP " + status + "): " + response.body());
            }
            return response.body();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to communicate with Supabase: " + e.getMessage(), e);
        }
    }

    private static String getConfig(String key, Map<String, String> dotEnv) {
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.trim().isEmpty()) return envVal.trim();
        String fileVal = dotEnv.get(key);
        return fileVal != null ? fileVal.trim() : null;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        File envFile = new File(".env");
        if (!envFile.exists() || !envFile.isFile()) return map;
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    map.put(k, v);
                }
            }
        } catch (IOException ignored) {}
        return map;
    }
}
