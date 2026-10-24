package nexus.core.module.misc;

import nexus.core.event.PacketEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Drops malformed or oversized packets that could crash the client. */
public final class AntiCrashModule extends Module {

    private final NumberSetting maxPacketSize;
    private final BooleanSetting logDrops;
    private final BooleanSetting blockUnknown;
    private int dropped;

    public AntiCrashModule() {
        super("anticrash", "AntiCrash", "Drops malformed packets that could crash the client.",
                ModuleCategory.MISC);
        this.maxPacketSize = number("Max Size", "Maximum accepted packet size in bytes", 2097152.0,
                1024.0, 33554432.0, 1024.0);
        this.logDrops = bool("Log Drops", "Write dropped packets to the log", true);
        this.blockUnknown = bool("Block Unknown", "Block packets with unknown ids", false);
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) {
            return;
        }
        boolean dangerous = false;
        if (event.size() > maxPacketSize.value()) {
            dangerous = true;
        }
        if (blockUnknown.isEnabled() && event.id() < 0) {
            dangerous = true;
        }
        if (event.payload() == null) {
            dangerous = true;
        }
        if (dangerous) {
            event.cancel();
            dropped++;
            if (logDrops.isEnabled()) {
                nexus.util.Log.warn("AntiCrash: dropped packet " + event.name()
                        + " (" + event.size() + " bytes)");
            }
        }
    }

    public int dropped() {
        return dropped;
    }

    @Override
    public String hudInfo() {
        return dropped + " dropped";
    }
}
