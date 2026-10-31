package dev.funtime.auctionhelper.util;

/**
 * Minimal, dependency free logging facade.
 *
 * <p>The common core is compiled into every Minecraft module, therefore it only uses
 * {@code System.out}/{@code System.err}. The Minecraft layer installs a {@link Sink}
 * so the same messages also land in the game log and in the on-disk diagnostics file.</p>
 */
public final class Log {
    public interface Sink {
        void log(String level, String message, Throwable error);
    }

    private static volatile Sink sink = null;

    private Log() {
    }

    public static void setSink(Sink newSink) {
        sink = newSink;
    }

    public static void info(String message) {
        emit("INFO", message, null);
    }

    public static void warn(String message) {
        emit("WARN", message, null);
    }

    public static void error(String message) {
        emit("ERROR", message, null);
    }

    public static void error(String message, Throwable error) {
        emit("ERROR", message, error);
    }

    private static void emit(String level, String message, Throwable error) {
        Sink current = sink;
        if (current != null) {
            try {
                current.log(level, message, error);
            } catch (Throwable ignored) {
                // A broken sink must never break trading logic.
            }
        }
        String line = "[FunTimeAuctionHelper][" + level + "] " + message;
        System.out.println(line);
        if (error != null) {
            error.printStackTrace(System.out);
        }
    }
}
