package net.kinggrief.connect;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нативный асинхронный пингер серверов по стандартному протоколу Minecraft (0x00 Handshake + Status).
 * Не требует никаких дополнительных портов — проверяет живой онлайн, слоты, пинг (мс) и режим Тех. работ!
 */
public final class ServerPinger {

    private static final Pattern ONLINE_PATTERN = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX_PATTERN = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");

    private ServerPinger() {}

    public static final class PingResult {
        public final boolean alive;
        public final int online;
        public final int max;
        public final int pingMs;
        public final boolean techWorks;

        public PingResult(boolean alive, int online, int max, int pingMs, boolean techWorks) {
            this.alive = alive;
            this.online = online;
            this.max = max;
            this.pingMs = pingMs;
            this.techWorks = techWorks;
        }
    }

    public static PingResult ping(String host, int port, int timeoutMs) {
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            return new PingResult(false, 0, 0, -1, false);
        }
        long t0 = System.nanoTime();
        Socket socket = new Socket();
        try {
            socket.setSoTimeout(timeoutMs);
            socket.connect(new InetSocketAddress(host.trim(), port), timeoutMs);
            long latencyMs = Math.max(1L, (System.nanoTime() - t0) / 1_000_000L);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. Handshake packet (ID = 0x00, Protocol = 767 [1.21], NextState = 1 [Status])
            ByteArrayOutputStream hsBuf = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(hsBuf);
            writeVarInt(hs, 0x00);
            writeVarInt(hs, 767);
            byte[] hostBytes = host.trim().getBytes(StandardCharsets.UTF_8);
            writeVarInt(hs, hostBytes.length);
            hs.write(hostBytes);
            hs.writeShort(port);
            writeVarInt(hs, 1);

            byte[] hsPacket = hsBuf.toByteArray();
            writeVarInt(out, hsPacket.length);
            out.write(hsPacket);

            // 2. Status Request packet (Length = 1, ID = 0x00)
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            // 3. Read Status Response
            readVarInt(in); // packet length
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                return new PingResult(false, 0, 0, -1, false);
            }
            int jsonLen = readVarInt(in);
            if (jsonLen <= 0 || jsonLen > 262_144) {
                return new PingResult(false, 0, 0, -1, false);
            }
            byte[] jsonBytes = new byte[jsonLen];
            in.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            int online = extractInt(ONLINE_PATTERN, json, 0);
            int max = extractInt(MAX_PATTERN, json, 100);
            String lower = json.toLowerCase();
            boolean tech = lower.contains("тех работы") || lower.contains("тех. работы") || lower.contains("techworks");
            return new PingResult(true, online, Math.max(1, max), (int) latencyMs, tech);
        } catch (Throwable ignored) {
            return new PingResult(false, 0, 0, -1, false);
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
