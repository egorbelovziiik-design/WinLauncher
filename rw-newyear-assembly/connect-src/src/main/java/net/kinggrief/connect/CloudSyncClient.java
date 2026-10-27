package net.kinggrief.connect;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Облачный HTTP-клиент синхронизации связки серверов (/api/minecraft/network).
 * Работает поверх HTTPS (порт 443) — не требует ни MySQL, ни Redis, ни открытых портов на Pterodactyl!
 */
public final class CloudSyncClient {

    private CloudSyncClient() {}

    public static String postSync(String endpointUrl, String secretKey, String jsonPayload, int timeoutMs) {
        if (endpointUrl == null || endpointUrl.trim().isEmpty() || secretKey == null || secretKey.trim().isEmpty()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpointUrl.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "text/plain, application/json");
            conn.setRequestProperty("x-response-format", "lines");
            conn.setRequestProperty("x-network-secret", secretKey.trim());
            conn.setRequestProperty("User-Agent", "KingConnect/1.0");

            byte[] outBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(outBytes.length);
            OutputStream os = conn.getOutputStream();
            os.write(outBytes);
            os.flush();
            os.close();

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }
            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String buildStringArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(esc(items.get(i))).append('"');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static String buildStringMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        if (map != null) {
            boolean first = true;
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
