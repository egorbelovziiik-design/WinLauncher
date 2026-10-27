package net.kinggrief.hub;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HubPingAndCloud {

    private static final Pattern ONLINE_PATTERN = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX_PATTERN = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");

    private HubPingAndCloud() {}

    public static final class PingStatus {
        public final boolean alive;
        public final int online;
        public final int max;
        public final int pingMs;
        public final boolean techWorks;

        public PingStatus(boolean alive, int online, int max, int pingMs, boolean techWorks) {
            this.alive = alive;
            this.online = online;
            this.max = max;
            this.pingMs = pingMs;
            this.techWorks = techWorks;
        }
    }

    public static PingStatus pingServer(String host, int port, int timeoutMs) {
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            return new PingStatus(false, 0, 250, -1, false);
        }
        long start = System.nanoTime();
        Socket sock = new Socket();
        try {
            sock.setSoTimeout(timeoutMs);
            sock.connect(new InetSocketAddress(host.trim(), port), timeoutMs);
            int ms = (int) Math.max(1L, (System.nanoTime() - start) / 1_000_000L);

            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());

            ByteArrayOutputStream hsBuf = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(hsBuf);
            writeVarInt(hs, 0x00);
            writeVarInt(hs, 767);
            byte[] hb = host.trim().getBytes(StandardCharsets.UTF_8);
            writeVarInt(hs, hb.length);
            hs.write(hb);
            hs.writeShort(port);
            writeVarInt(hs, 1);

            byte[] pkt = hsBuf.toByteArray();
            writeVarInt(out, pkt.length);
            out.write(pkt);
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            readVarInt(in);
            int pid = readVarInt(in);
            if (pid != 0x00) return new PingStatus(false, 0, 250, -1, false);
            int len = readVarInt(in);
            if (len <= 0 || len > 262_144) return new PingStatus(false, 0, 250, -1, false);
            byte[] raw = new byte[len];
            in.readFully(raw);
            String json = new String(raw, StandardCharsets.UTF_8);
            int on = extractInt(ONLINE_PATTERN, json, 0);
            int mx = extractInt(MAX_PATTERN, json, 250);
            String lo = json.toLowerCase();
            boolean tw = lo.contains("тех работы") || lo.contains("тех. работы") || lo.contains("techworks");
            return new PingStatus(true, on, Math.max(1, mx), ms, tw);
        } catch (Throwable t) {
            return new PingStatus(false, 0, 250, -1, false);
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {}
        }
    }

    public static String postCloudLines(String endpointUrl, String secretKey, String jsonPayload, int timeoutMs) {
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
            conn.setRequestProperty("Accept", "text/plain");
            conn.setRequestProperty("x-response-format", "lines");
            conn.setRequestProperty("x-network-secret", secretKey.trim());
            conn.setRequestProperty("User-Agent", "KingHub/1.0");

            byte[] outBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(outBytes.length);
            OutputStream os = conn.getOutputStream();
            os.write(outBytes);
            os.flush();
            os.close();

            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            br.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
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
                default: sb.append(c < 0x20 ? ' ' : c);
            }
        }
        return sb.toString();
    }

    public static String buildStrArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(esc(list.get(i))).append('"');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static int extractInt(Pattern p, String s, int def) {
        Matcher m = p.matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            result |= ((read & 0x7F) << (7 * numRead));
            if (++numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0x80) != 0);
        return result;
    }
}
