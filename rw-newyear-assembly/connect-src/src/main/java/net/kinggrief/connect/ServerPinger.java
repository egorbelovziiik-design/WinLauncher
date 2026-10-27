package net.kinggrief.connect;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нативный асинхронный пингер серверов + UDP DNS SRV резолвер (_minecraft._tcp).
 * Автоматически раскрывает домены с SRV-записями и защищает от ошибки Connection refused: getsockopt!
 */
public final class ServerPinger {

    private static final Pattern ONLINE_PATTERN = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX_PATTERN = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");
    private static final Map<String, HostPort> SRV_CACHE = new ConcurrentHashMap<String, HostPort>();

    private ServerPinger() {}

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

    public static final class PingResult {
        public final boolean alive;
        public final int online;
        public final int max;
        public final int pingMs;
        public final boolean techWorks;
        public final String resolvedHost;
        public final int resolvedPort;

        public PingResult(boolean alive, int online, int max, int pingMs, boolean techWorks, String resolvedHost, int resolvedPort) {
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

    public static HostPort resolveMinecraftAddress(String rawHost, int rawPort) {
        if (rawHost == null) return new HostPort("127.0.0.1", rawPort > 0 ? rawPort : 25565);
        String host = rawHost.trim();
        int port = rawPort > 0 ? rawPort : 25565;
        if (isLoopbackHost(host) || isIpv4Literal(host) || host.contains(":") || port != 25565) {
            return new HostPort(host, port);
        }
        String key = host.toLowerCase(Locale.ROOT);
        HostPort cached = SRV_CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - cached.resolvedAtMs < 300_000L) {
            return cached;
        }
        for (String dns : new String[]{"1.1.1.1", "8.8.8.8"}) {
            HostPort srv = queryUdpDnsSrv("_minecraft._tcp." + host, dns, 500);
            if (srv != null && srv.host != null && !srv.host.isEmpty() && srv.port > 0) {
                SRV_CACHE.put(key, srv);
                return srv;
            }
        }
        HostPort fb = new HostPort(host, port);
        SRV_CACHE.put(key, fb);
        return fb;
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
            out.writeShort(0x4B43);
            out.writeShort(0x0100);
            out.writeShort(1);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            for (String label : qname.split("\\.")) {
                if (label.isEmpty()) continue;
                byte[] lb = label.getBytes(StandardCharsets.US_ASCII);
                out.writeByte(lb.length);
                out.write(lb);
            }
            out.writeByte(0);
            out.writeShort(33);
            out.writeShort(1);

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

    public static PingResult ping(String rawHost, int rawPort, int timeoutMs) {
        if (rawHost == null || rawHost.trim().isEmpty() || rawPort <= 0 || rawPort > 65535) {
            return new PingResult(false, 0, 0, -1, false, rawHost, rawPort);
        }
        HostPort resolved = resolveMinecraftAddress(rawHost, rawPort);
        String host = resolved.host;
        int port = resolved.port;

        long t0 = System.nanoTime();
        Socket socket = new Socket();
        try {
            socket.setSoTimeout(timeoutMs);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long latencyMs = Math.max(1L, (System.nanoTime() - t0) / 1_000_000L);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream hsBuf = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(hsBuf);
            writeVarInt(hs, 0x00);
            writeVarInt(hs, 767);
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(hs, hostBytes.length);
            hs.write(hostBytes);
            hs.writeShort(port);
            writeVarInt(hs, 1);

            byte[] hsPacket = hsBuf.toByteArray();
            writeVarInt(out, hsPacket.length);
            out.write(hsPacket);

            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            readVarInt(in);
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                return new PingResult(true, 0, 100, (int) latencyMs, false, host, port);
            }
            int jsonLen = readVarInt(in);
            if (jsonLen <= 0 || jsonLen > 262_144) {
                return new PingResult(true, 0, 100, (int) latencyMs, false, host, port);
            }
            byte[] jsonBytes = new byte[jsonLen];
            in.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            int online = extractInt(ONLINE_PATTERN, json, 0);
            int max = extractInt(MAX_PATTERN, json, 100);
            String lower = json.toLowerCase();
            boolean tech = lower.contains("тех работы") || lower.contains("тех. работы") || lower.contains("techworks");
            return new PingResult(true, online, Math.max(1, max), (int) latencyMs, tech, host, port);
        } catch (Throwable ignored) {
            return new PingResult(false, 0, 0, -1, false, host, port);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static int extractInt(Pattern pat, String text, int def) {
        Matcher m = pat.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
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
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }
}
