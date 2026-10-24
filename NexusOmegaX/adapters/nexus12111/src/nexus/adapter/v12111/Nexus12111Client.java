package nexus.adapter.v12111;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nexus.api.*;
import nexus.api.math.Vec3d;

/** {@link IClient} implementation for Minecraft 1.21.11. */
public final class Nexus12111Client implements IClient {

    private final Minecraft minecraft;
    private final Nexus12111Player player = new Nexus12111Player(this);
    private final Nexus12111World world = new Nexus12111World(this);
    private final Nexus12111Inventory inventory = new Nexus12111Inventory(this);
    private final Nexus12111Options options = new Nexus12111Options(this);
    private final Nexus12111Input input = new Nexus12111Input(this);
    private final Nexus12111Chat chat = new Nexus12111Chat(this);
    private final Nexus12111Screens screens = new Nexus12111Screens(this);
    private final Nexus12111Interaction interaction = new Nexus12111Interaction(this);
    private final Nexus12111Resources resources = new Nexus12111Resources(this);
    private final Nexus12111Connection connection = new Nexus12111Connection(this);
    private final Nexus12111Window window = new Nexus12111Window(this);
    private Nexus12111Renderer renderer = new Nexus12111Renderer();
    private boolean inWorldRenderPass;

    Nexus12111Client(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public Minecraft handle() {
        return minecraft;
    }

    /** Called by the hooks before every render pass. */
    void setRenderer(Nexus12111Renderer value) {
        this.renderer = value;
    }

    void setWorldRenderPass(boolean value) {
        this.inWorldRenderPass = value;
    }

    /** Alias of {@link #handle()} kept for readability in the adapters. */
    public Minecraft minecraft() {
        return minecraft;
    }

    public LocalPlayer localPlayer() {
        return minecraft.player;
    }

    @Override
    public IPlayer player() {
        return player;
    }

    @Override
    public IWorld world() {
        return world;
    }

    @Override
    public IChat chat() {
        return chat;
    }

    @Override
    public IConnection connection() {
        return connection;
    }

    @Override
    public IOptions options() {
        return options;
    }

    @Override
    public IWindow window() {
        return window;
    }

    @Override
    public IRenderer renderer() {
        return renderer;
    }

    @Override
    public IInput input() {
        return input;
    }

    @Override
    public IScreenHost screens() {
        return screens;
    }

    @Override
    public IInteraction interaction() {
        return interaction;
    }

    @Override
    public IResources resources() {
        return resources;
    }

    @Override
    public boolean isInWorldRenderPass() {
        return inWorldRenderPass;
    }

    @Override
    public boolean isInGame() {
        return minecraft.player != null && minecraft.level != null;
    }

    @Override
    public IEntity crosshairTarget() {
        if (minecraft.hitResult == null || !(minecraft.hitResult instanceof net.minecraft.world.phys.EntityHitResult)) {
            return null;
        }
        return new Nexus12111Entity(((net.minecraft.world.phys.EntityHitResult) minecraft.hitResult).getEntity());
    }

    @Override
    public Vec3d cameraPosition() {
        net.minecraft.world.phys.Vec3 position = minecraft.gameRenderer.getMainCamera().position();
        return new Vec3d(position.x, position.y, position.z);
    }

    @Override
    public float cameraYaw() {
        return minecraft.gameRenderer.getMainCamera().yRot();
    }

    @Override
    public float cameraPitch() {
        return minecraft.gameRenderer.getMainCamera().xRot();
    }

    @Override
    public void execute(Runnable task) {
        minecraft.execute(task);
    }

    @Override
    public void schedule(Runnable task, int tickDelay) {
        minecraft.execute(task);
    }

    @Override
    public String minecraftVersion() {
        return Nexus12111Platform.MC_VERSION;
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }

    public Nexus12111Inventory inventory() {
        return inventory;
    }
}
