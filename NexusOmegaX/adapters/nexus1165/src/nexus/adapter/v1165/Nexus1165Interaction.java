package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import nexus.api.IEntity;
import nexus.api.IInteraction;

/** {@link IInteraction} implementation for Minecraft 1.16.5. */
public final class Nexus1165Interaction implements IInteraction {

    private final Nexus1165Client client;

    Nexus1165Interaction(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    @Override
    public boolean breakBlock(int x, int y, int z) {
        if (handle().interactionManager == null || handle().player == null) {
            return false;
        }
        return handle().interactionManager.breakBlock(new BlockPos(x, y, z));
    }

    @Override
    public void stopBreaking() {
        if (handle().interactionManager != null) {
            handle().interactionManager.cancelBlockBreaking();
        }
    }

    @Override
    public boolean placeBlock(int x, int y, int z, int faceIndex) {
        if (!useBlock(x, y, z, faceIndex)) {
            return false;
        }
        if (handle().player != null) {
            handle().player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
        return true;
    }

    @Override
    public boolean useBlock(int x, int y, int z, int faceIndex) {
        if (handle().interactionManager == null || handle().player == null) {
            return false;
        }
        ClientPlayerEntity player = handle().player;
        Direction direction = faceIndex >= 0 && faceIndex < Direction.values().length
                ? Direction.values()[faceIndex] : Direction.UP;
        BlockHitResult hit = new BlockHitResult(new Vec3d(x + 0.5, y + 0.5, z + 0.5),
                direction, new BlockPos(x, y, z), false);
        return handle().interactionManager.interactBlock(player, handle().world,
                net.minecraft.util.Hand.MAIN_HAND, hit).isAccepted();
    }

    @Override
    public void attackEntity(IEntity entity) {
        if (handle().interactionManager != null && handle().player != null && entity != null) {
            handle().interactionManager.attackEntity(handle().player, ((Nexus1165Entity) entity).handle());
        }
    }

    @Override
    public void interactEntity(IEntity entity) {
        if (entity == null) {
            return;
        }
        if (handle().interactionManager != null && handle().player != null) {
            handle().interactionManager.interactEntity(handle().player,
                    ((Nexus1165Entity) entity).handle(), net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    @Override
    public void clickSlot(int syncId, int slot, int button, int actionType) {
        if (handle().interactionManager == null || handle().player == null) {
            return;
        }
        net.minecraft.screen.slot.SlotActionType type =
                net.minecraft.screen.slot.SlotActionType.values()[
                        Math.max(0, Math.min(6, actionType))];
        handle().interactionManager.clickSlot(syncId, slot, button, type, handle().player);
    }

    @Override
    public void closeContainer() {
        if (handle().player != null) {
            handle().player.closeHandledScreen();
        }
    }

    @Override
    public int openSyncId() {
        return handle().player == null ? -1 : handle().player.currentScreenHandler.syncId;
    }

    @Override
    public void lookAtBlock(int x, int y, int z) {
        client.player().lookAt(new nexus.api.math.Vec3d(x + 0.5, y + 0.5, z + 0.5));
    }

    @Override
    public nexus.api.math.Vec3d hitPosition() {
        HitResult target = handle().crosshairTarget;
        if (target == null) {
            return null;
        }
        Vec3d position = target.getPos();
        return new nexus.api.math.Vec3d(position.x, position.y, position.z);
    }

    @Override
    public int[] hitBlock() {
        HitResult target = handle().crosshairTarget;
        if (target instanceof BlockHitResult) {
            BlockPos pos = ((BlockHitResult) target).getBlockPos();
            return new int[]{pos.getX(), pos.getY(), pos.getZ()};
        }
        return null;
    }

    @Override
    public IEntity hitEntity() {
        HitResult target = handle().crosshairTarget;
        return target instanceof EntityHitResult
                ? new Nexus1165Entity(((EntityHitResult) target).getEntity()) : null;
    }
}
