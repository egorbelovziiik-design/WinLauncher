package nexus.api;

import nexus.api.math.Vec3d;

/**
 * Aggregate access to the running Minecraft instance. Implemented once per
 * supported version by the corresponding Nexus adapter.
 */
public interface IClient {

    IPlayer player();

    IWorld world();

    IChat chat();

    IConnection connection();

    IOptions options();

    IWindow window();

    IRenderer renderer();

    IInput input();

    IScreenHost screens();

    IInteraction interaction();

    IResources resources();

    /** True when the render pipeline is currently drawing the world (3D pass). */
    boolean isInWorldRenderPass();

    /** True while a world is loaded and the local player exists. */
    boolean isInGame();

    /** Entity currently under the crosshair, or null. */
    IEntity crosshairTarget();

    /** Free look / perspective camera offset applied by rendering modules. */
    Vec3d cameraPosition();

    /** Camera yaw/pitch actually used for rendering (may differ from the player). */
    float cameraYaw();

    float cameraPitch();

    /** Runs a task on the Minecraft client thread. */
    void execute(Runnable task);

    /** Runs a task on the Minecraft client thread with a tick delay. */
    void schedule(Runnable task, int tickDelay);

    /** Current Minecraft version string, e.g. {@code 1.21.4}. */
    String minecraftVersion();

    /** Short loader name, e.g. {@code fabric}. */
    String loaderName();
}
