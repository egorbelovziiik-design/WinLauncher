package nexus.core.event;

import nexus.api.IEntity;

/** Fired when the local player attacks an entity or the air. */
public final class AttackEvent extends Event {

    public final IEntity target;
    public final boolean cooldownReady;

    public AttackEvent(IEntity target, boolean cooldownReady) {
        this.target = target;
        this.cooldownReady = cooldownReady;
    }
}
