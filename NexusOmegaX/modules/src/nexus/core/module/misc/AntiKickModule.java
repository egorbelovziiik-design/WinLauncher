package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Warns you about kick messages and disconnects safely instead. */
public final class AntiKickModule extends Module {

    private final BooleanSetting notify;
    private final BooleanSetting safeDisconnect;
    private final NumberSetting idleFallback;

    public AntiKickModule() {
        super("antikick", "AntiKick", "Warns you about kicks and disconnects safely.",
                ModuleCategory.MISC);
        this.notify = bool("Notify", "Show a notification on a kick", true);
        this.safeDisconnect = bool("Safe Disconnect", "Leave the server instead of being kicked", false);
        this.idleFallback = number("Idle Fallback", "Seconds before the fallback action", 60.0, 5.0, 600.0, 5.0);
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        String lower = event.message.toLowerCase();
        if (!lower.contains("kicked") && !lower.contains("disconnect") && !lower.contains("banned")) {
            return;
        }
        if (notify.isEnabled()) {
            nexus.core.Nexus.instance().notifications().warn("AntiKick", "Kick message detected");
        }
        if (safeDisconnect.isEnabled()) {
            mc().schedule(new Runnable() {
                @Override
                public void run() {
                    mc().connection().disconnect("AntiKick fallback");
                }
            }, (int) idleFallback.value() * 20);
        }
    }

    public double idleFallbackSeconds() {
        return idleFallback.value();
    }
}
