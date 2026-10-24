package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import nexus.api.*;
import nexus.api.math.Vec3d;

/** {@link IClient} implementation for Minecraft 1.16.5. */
public final class Nexus1165Client implements IClient {

    private final MinecraftClient minecraft;
    private final Nexus1165Player player = new Nexus1165Player(this);
    private final Nexus1165World world = new Nexus1165World(this);
    private final Nexus1165Inventory inventory = new Nexus1165Inventory(this);
    private final Nexus1165Options options = new Nexus1165Options(this);
    private final Nexus1165Input input = new Nexus1165Input(this);
    private final Nexus1165Chat chat = new Nexus1165Chat(this);
    private final Nexus1165Screens screens = new Nexus1165Screens(this);
    private final Nexus1165Interaction interaction = new Nexus1165Interaction(this);
    private final Nexus1165Resources resources = new Nexus1165Resources(this);
    private final Nexus1165Connection connection = new Nexus1165Connection(this);
    private final Nexus1165Window window = new Nexus1165Window(this);
    private Nexus1165Renderer renderer = new Nexus1165Renderer();

    Nexus1165Client(MinecraftClient minecraft) {
        this.minecraft = minecraft;
    }

    public MinecraftClient handle() {
        return minecraft;
    }

    public ClientPlayerEntity localPlayer() {
        return minecraft.player;
    }

    void setRenderer(Nexus1165Renderer value) {
        this.renderer = value;
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
        return renderer.isWorldPass();
    }

    @Override
    public boolean isInGame() {
        return minecraft.player != null && minecraft.world != null;
    }

    @Override
    public IEntity crosshairTarget() {
        if (minecraft.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult) {
            return new Nexus1165Entity(((net.minecraft.util.hit.EntityHitResult) minecraft.crosshairTarget).getEntity());
        }
        return null;
    }

    @Override
    public Vec3d cameraPosition() {
        net.minecraft.util.math.Vec3d position = minecraft.gameRenderer.getCamera().getPos();
        return new Vec3d(position.x, position.y, position.z);
    }

    @Override
    public float cameraYaw() {
        return minecraft.gameRenderer.getCamera().getYaw();
    }

    @Override
    public float cameraPitch() {
        return minecraft.gameRenderer.getCamera().getPitch();
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
        return Nexus1165Platform.MC_VERSION;
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }

    public Nexus1165Inventory inventory() {
        return inventory;
    }
}
