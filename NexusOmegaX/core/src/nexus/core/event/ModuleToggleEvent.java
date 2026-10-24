package nexus.core.event;

import nexus.core.module.Module;

/** Fired after a module has been enabled or disabled. */
public final class ModuleToggleEvent extends Event {

    public final Module module;
    public final boolean enabled;
    public final boolean byUser;

    public ModuleToggleEvent(Module module, boolean enabled, boolean byUser) {
        this.module = module;
        this.enabled = enabled;
        this.byUser = byUser;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
