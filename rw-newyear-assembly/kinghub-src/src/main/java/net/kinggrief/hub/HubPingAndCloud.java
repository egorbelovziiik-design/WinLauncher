package net.kinggrief.hub;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HubPingAndCloud {

    private static final Pattern ONLINE_PATTERN = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX_PATTERN = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");
    private static final Map<String, HostPort> SRV_CACHE = new ConcurrentHashMap<String, HostPort>();

    private HubPingAndCloud() {}

    public static final class HostPort {
        public final String host;
        public final int port;
        public final long resolvedAtMs;

        public HostPort(String host, int port) {
            this.host = host;
            this.port = port;
            this.resolvedAtMs = System.currentTimeMillis();
        }
    }

    public static final class PingStatus {
        public final boolean alive;
        public final int online;
        public final int max;
        public final int pingMs;
        public final boolean techWorks;
        public final String resolvedHost;
        public final int resolvedPort;

        public PingStatus(boolean alive, int online, int max, int pingMs, boolean techWorks, String resolvedHost, int resolvedPort) {
            this.alive = alive;
            this.online = online;
            this.max = max;
            this.pingMs = pingMs;
            this.techWorks = techWorks;
            this.resolvedHost = resolvedHost;
            this.resolvedPort = resolvedPort;
        }
    }

    public static boolean isLoopbackHost(String h) {
        if (h == null) return true;
        String s = h.trim().toLowerCase(Locale.ROOT);
        return s.isEmpty() || "127.0.0.1".equals(s) || "localhost".equals(s) || "0.0.0.0".equals(s) || "::1".equals(s);
    }

    /**
     * Resolves _minecraft._tcp.<host> DNS SRV records via direct UDP DNS (1.1.1.1 / 8.8.8.8)
     * so domains without explicit port work with Minecraft 1.21 Player#transfer without getsockopt!
     */
    public static HostPort resolveMinecraftAddress(String rawHost, int rawPort) {
        if (rawHost == null) return new HostPort("127.0.0.1", rawPort > 0 ? rawPort : 25565);
        String host = rawHost.trim();
        int port = rawPort > 0 ? rawPort : 25565;
        if (isLoopbackHost(host) || isIpv4Literal(host) || host.contains(":")) {
            return new HostPort(host, port);
        }
        // Only query SRV if default port 25565 is used (or cached within 5 min)
        if (port != 25565) {
            return new HostPort(host, port);
        }
        String cacheKey = host.toLowerCase(Locale.ROOT);
        HostPort cached = SRV_CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.resolvedAtMs < 300_000L) {
            return cached;
        }
        String[] dnsServers = {"1.1.1.1", "8.8.8.8"};
        for (String dns : dnsServers) {
            HostPort srv = queryUdpDnsSrv("_minecraft._tcp." + host, dns, 500);
            if (srv != null && srv.host != null && !srv.host.isEmpty() && srv.port > 0) {
                SRV_CACHE.put(cacheKey, srv);
                return srv;
            }
        }
        HostPort fallback = new HostPort(host, port);
        SRV_CACHE.put(cacheKey, fallback);
        return fallback;
    }

    private static boolean isIpv4Literal(String s) {
        String[] p = s.split("\\.");
        if (p.length != 4) return false;
        for (String seg : p) {
            if (seg.isEmpty() || seg.length() > 3) return false;
            for (int i = 0; i < seg.length(); i++) {
                if (!Character.isDigit(seg.charAt(i))) return false;
            }
        }
        return true;
    }

    private static HostPort queryUdpDnsSrv(String qname, String dnsIp, int timeoutMs) {
        DatagramSocket sock = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(96);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeShort(0x4B47); // ID
            out.writeShort(0x0100); // Standard query, RD=1
            out.writeShort(1);      // QDCOUNT=1
            out.writeShort(0);      // ANCOUNT
            out.writeShort(0);      // NSCOUNT
            out.writeShort(0);      // ARCOUNT
            for (String label : qname.split("\\.")) {
                if (label.isEmpty()) continue;
                byte[] lb = label.getBytes(StandardCharsets.US_ASCII);
                out.writeByte(lb.length);
                out.write(lb);
            }
            out.writeByte(0);
            out.writeShort(33); // QTYPE = SRV (33)
            out.writeShort(1);  // QCLASS = IN (1)

            byte[] req = bos.toByteArray();
            sock = new DatagramSocket();
            sock.setSoTimeout(timeoutMs);
            sock.send(new DatagramPacket(req, req.length, InetAddress.getByName(dnsIp), 53));

            byte[] buf = new byte[512];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            int len = resp.getLength();
            if (len < 12) return null;

            int qdCount = ((buf[4] & 0xFF) << 8) | (buf[5] & 0xFF);
            int anCount = ((buf[6] & 0xFF) << 8) | (buf[7] & 0xFF);
            if (anCount <= 0) return null;

            int idx = 12;
            for (int q = 0; q < qdCount; q++) {
                idx = skipDnsName(buf, idx, len);
                idx += 4;
            }
            for (int a = 0; a < anCount && idx < len; a++) {
                idx = skipDnsName(buf, idx, len);
                if (idx + 10 > len) break;
                int type = ((buf[idx] & 0xFF) << 8) | (buf[idx + 1] & 0xFF);
                int rdLen = ((buf[idx + 8] & 0xFF) << 8) | (buf[idx + 9] & 0xFF);
                idx += 10;
                if (idx + rdLen > len) break;
                if (type == 33 && rdLen >= 7) {
                    int srvPort = ((buf[idx + 4] & 0xFF) << 8) | (buf[idx + 5] & 0xFF);
                    String target = readDnsName(buf, idx + 6, len);
                    if (target != null && !target.isEmpty() && srvPort > 0) {
                        if (target.endsWith(".")) target = target.substring(0, target.length() - 1);
                        return new HostPort(target, srvPort);
                    }
                }
                idx += rdLen;
            }
        } catch (Throwable ignored) {
        } finally {
            if (sock != null) sock.close();
        }
        return null;
    }

    private static int skipDnsName(byte[] buf, int idx, int len) {
        while (idx < len) {
            int b = buf[idx] & 0xFF;
            if (b == 0) return idx + 1;
            if ((b & 0xC0) == 0xC0) return idx + 2;
            idx += 1 + b;
        }
        return len;
    }

    private static String readDnsName(byte[] buf, int idx, int len) {
        StringBuilder sb = new StringBuilder();
        int jumps = 0;
        while (idx < len && jumps < 12) {
            int b = buf[idx] & 0xFF;
            if (b == 0) break;
            if ((b & 0xC0) == 0xC0) {
                if (idx + 1 >= len) break;
                idx = ((b & 0x3F) << 8) | (buf[idx + 1] & 0xFF);
                jumps++;
                continue;
            }
            idx++;
            if (idx + b > len) break;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(buf, idx, b, StandardCharsets.US_ASCII));
            idx += b;
        }
        return sb.toString();
    }

    public static PingStatus pingServer(String rawHost, int rawPort, int timeoutMs) {
        if (rawHost == null || rawHost.trim().isEmpty() || rawPort <= 0 || rawPort > 65535) {
            return new PingStatus(false, 0, 250, -1, false, rawHost, rawPort);
        }
        HostPort resolved = resolveMinecraftAddress(rawHost, rawPort);
        String host = resolved.host;
        int port = resolved.port;

        long start = System.nanoTime();
        Socket sock = new Socket();
        try {
            sock.setSoTimeout(timeoutMs);
            sock.connect(new InetSocketAddress(host, port), timeoutMs);
            int ms = (int) Math.max(1L, (System.nanoTime() - start) / 1_000_000L);

            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());

            ByteArrayOutputStream hsBuf = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(hsBuf);
            writeVarInt(hs, 0x00);
            writeVarInt(hs, 767);
            byte[] hb = host.getBytes(StandardCharsets.UTF_8);
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
            if (pid != 0x00) return new PingStatus(true, 0, 250, ms, false, host, port);
            int len = readVarInt(in);
            if (len <= 0 || len > 262_144) return new PingStatus(true, 0, 250, ms, false, host, port);
            byte[] raw = new byte[len];
            in.readFully(raw);
            String json = new String(raw, StandardCharsets.UTF_8);
            int on = extractInt(ONLINE_PATTERN, json, 0);
            int mx = extractInt(MAX_PATTERN, json, 250);
            String lo = json.toLowerCase();
            boolean tw = lo.contains("тех работы") || lo.contains("тех. работы") || lo.contains("techworks");
            return new PingStatus(true, on, Math.max(1, mx), ms, tw, host, port);
        } catch (Throwable t) {
            return new PingStatus(false, 0, 250, -1, false, host, port);
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
            conn.setRequestProperty("User-Agent", "KingHub/1.1");

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
