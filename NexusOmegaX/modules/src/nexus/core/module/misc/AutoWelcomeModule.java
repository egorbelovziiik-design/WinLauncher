package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Greets your friends when they join the server. */
public final class AutoWelcomeModule extends Module {

    private final StringSetting message;
    private final BooleanSetting friendsOnly;
    private final NumberSetting delay;
    private final List<String> greeted = new ArrayList<String>();

    public AutoWelcomeModule() {
        super("autowelcome", "AutoWelcome", "Greets players when they join the server.",
                ModuleCategory.MISC);
        this.message = text("Message", "Welcome text, {name} is replaced", "Welcome back, {name}!");
        this.friendsOnly = bool("Friends Only", "Only greet your friends", true);
        this.delay = number("Delay", "Delay before greeting in ticks", 20.0, 0.0, 200.0, 5.0);
    }

    public void onPlayerJoin(final String name) {
        if (!isEnabled() || name == null || greeted.contains(name)) {
            return;
        }
        if (friendsOnly.isEnabled() && !nexus.core.Nexus.instance().friends().isFriend(name)) {
            return;
        }
        if (name.equalsIgnoreCase(player().name())) {
            return;
        }
        greeted.add(name);
        mc().schedule(new Runnable() {
            @Override
            public void run() {
                if (isEnabled()) {
                    player().sendChat(message.rawValue().replace("{name}", name));
                }
            }
        }, (int) delay.value());
        markActivity();
    }

    public void clearHistory() {
        greeted.clear();
    }

    @Override
    public String hudInfo() {
        return greeted.size() + " greeted";
    }
}
