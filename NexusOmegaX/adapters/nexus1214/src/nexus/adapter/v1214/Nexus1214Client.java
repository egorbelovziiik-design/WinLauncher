package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nexus.api.*;
import nexus.api.math.Vec3d;

/** {@link IClient} implementation for Minecraft 1.21.4. */
public final class Nexus1214Client implements IClient {

    private final Minecraft minecraft;
    private final Nexus1214Player player = new Nexus1214Player(this);
    private final Nexus1214World world = new Nexus1214World(this);
    private final Nexus1214Inventory inventory = new Nexus1214Inventory(this);
    private final Nexus1214Options options = new Nexus1214Options(this);
    private final Nexus1214Input input = new Nexus1214Input(this);
    private final Nexus1214Chat chat = new Nexus1214Chat(this);
    private final Nexus1214Screens screens = new Nexus1214Screens(this);
    private final Nexus1214Interaction interaction = new Nexus1214Interaction(this);
    private final Nexus1214Resources resources = new Nexus1214Resources(this);
    private final Nexus1214Connection connection = new Nexus1214Connection(this);
    private final Nexus1214Window window = new Nexus1214Window(this);
    private Nexus1214Renderer renderer = new Nexus1214Renderer();
    private boolean inWorldRenderPass;

    Nexus1214Client(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public Minecraft handle() {
        return minecraft;
    }

    /** Called by the hooks before every render pass. */
    void setRenderer(Nexus1214Renderer value) {
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
        return new Nexus1214Entity(((net.minecraft.world.phys.EntityHitResult) minecraft.hitResult).getEntity());
    }

    @Override
    public Vec3d cameraPosition() {
        net.minecraft.world.phys.Vec3 position = minecraft.gameRenderer.getMainCamera().getPosition();
        return new Vec3d(position.x, position.y, position.z);
    }

    @Override
    public float cameraYaw() {
        return minecraft.gameRenderer.getMainCamera().getYRot();
    }

    @Override
    public float cameraPitch() {
        return minecraft.gameRenderer.getMainCamera().getXRot();
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
        return Nexus1214Platform.MC_VERSION;
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }

    public Nexus1214Inventory inventory() {
        return inventory;
    }
}
