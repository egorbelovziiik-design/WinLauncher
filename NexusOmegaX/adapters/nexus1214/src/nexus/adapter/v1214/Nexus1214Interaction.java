package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import nexus.api.IEntity;
import nexus.api.IInteraction;
import nexus.api.math.Vec3d;

/** {@link IInteraction} implementation for Minecraft 1.21.4. */
public final class Nexus1214Interaction implements IInteraction {

    private final Nexus1214Client client;

    Nexus1214Interaction(Nexus1214Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public boolean breakBlock(int x, int y, int z) {
        if (handle().gameMode == null || handle().player == null) {
            return false;
        }
        return handle().gameMode.startDestroyBlock(new BlockPos(x, y, z), Direction.UP);
    }

    @Override
    public void stopBreaking() {
        if (handle().gameMode != null) {
            handle().gameMode.stopDestroyBlock();
        }
    }

    @Override
    public boolean placeBlock(int x, int y, int z, int faceIndex) {
        if (handle().gameMode == null || handle().player == null) {
            return false;
        }
        Direction direction = Direction.values()[faceIndex % Direction.values().length];
        BlockHitResult hit = new BlockHitResult(new net.minecraft.world.phys.Vec3(x + 0.5, y + 0.5, z + 0.5),
                direction, new BlockPos(x, y, z), false);
        return handle().gameMode.useItemOn(handle().player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    @Override
    public boolean useBlock(int x, int y, int z, int faceIndex) {
        return placeBlock(x, y, z, faceIndex);
    }

    @Override
    public void attackEntity(IEntity entity) {
        if (handle().gameMode == null || handle().player == null || entity == null) {
            return;
        }
        Entity target = ((Nexus1214Entity) entity).handle();
        handle().gameMode.attack(handle().player, target);
        handle().player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void interactEntity(IEntity entity) {
        if (handle().gameMode == null || handle().player == null || entity == null) {
            return;
        }
        Entity target = ((Nexus1214Entity) entity).handle();
        handle().gameMode.interact(handle().player, target, InteractionHand.MAIN_HAND);
    }

    @Override
    public void clickSlot(int syncId, int slot, int button, int actionType) {
        client.inventory().clickSlot(slot, button, nexus.api.IInventory.ClickType.PICKUP);
    }

    @Override
    public void closeContainer() {
        LocalPlayer player = client.localPlayer();
        if (player != null) {
            player.closeContainer();
        }
    }

    @Override
    public int openSyncId() {
        return client.inventory().syncId();
    }

    @Override
    public void lookAtBlock(int x, int y, int z) {
        client.player().lookAt(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
    }

    @Override
    public Vec3d hitPosition() {
        HitResult result = handle().hitResult;
        if (result == null || result.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new Vec3d(result.getLocation().x, result.getLocation().y, result.getLocation().z);
    }

    @Override
    public int[] hitBlock() {
        HitResult result = handle().hitResult;
        if (!(result instanceof BlockHitResult)) {
            return null;
        }
        BlockPos position = ((BlockHitResult) result).getBlockPos();
        return new int[]{position.getX(), position.getY(), position.getZ()};
    }

    @Override
    public IEntity hitEntity() {
        HitResult result = handle().hitResult;
        if (!(result instanceof EntityHitResult)) {
            return null;
        }
        return new Nexus1214Entity(((EntityHitResult) result).getEntity());
    }
}
