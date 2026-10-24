package nexus.core.util;

import nexus.api.IEntity;
import nexus.api.IWorld;
import nexus.api.math.Vec3d;
import nexus.core.Nexus;

import java.util.ArrayList;
import java.util.List;

/** Target selection helpers shared by combat and HUD features. */
public final class TargetUtil {

    private TargetUtil() {
    }

    /**
     * Finds the best target according to distance, angle and type filters.
     *
     * @param range      maximum distance
     * @param players    include players
     * @param mobs       include mobs
     * @param hostile    include hostile mobs
     * @param passive    include passive mobs
     * @param maxAngle   maximum angle between the view vector and the target
     */
    public static IEntity bestTarget(double range, boolean players, boolean mobs, boolean hostile,
                                     boolean passive, double maxAngle) {
        if (!Nexus.isInitialized() || !Nexus.instance().client().isInGame()) {
            return null;
        }
        IWorld world = Nexus.instance().client().world();
        nexus.api.IPlayer player = Nexus.instance().client().player();
        if (world == null || player == null || !player.exists()) {
            return null;
        }
        IEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (IEntity entity : world.entities()) {
            if (entity == null || !entity.isAlive() || entity.isLocalPlayer() || !entity.isAttackable()) {
                continue;
            }
            if (entity.isPlayer() && !players) {
                continue;
            }
            if (!entity.isPlayer() && !mobs) {
                continue;
            }
            if (!entity.isPlayer() && entity.isHostile() && !hostile) {
                continue;
            }
            if (!entity.isPlayer() && !entity.isHostile() && !passive) {
                continue;
            }
            if (Nexus.instance().friends().isFriend(entity.name()) && Nexus.instance().friends().friendsOnlyTarget()) {
                continue;
            }
            double distance = entity.distanceToPlayer();
            if (distance > range) {
                continue;
            }
            double angle = angleTo(entity);
            if (angle > maxAngle) {
                continue;
            }
            double score = distance + angle * 0.6;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    /** Angle in degrees between the player view direction and an entity. */
    public static double angleTo(IEntity entity) {
        nexus.api.IPlayer player = Nexus.instance().client().player();
        Vec3d eye = player.eyePosition();
        Vec3d target = entity.eyePosition();
        double yaw = Math.toDegrees(Math.atan2(target.z - eye.z, target.x - eye.x)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(target.y - eye.y,
                Math.sqrt(Math.pow(target.x - eye.x, 2) + Math.pow(target.z - eye.z, 2))));
        return Math.max(Math.abs(wrap(yaw - player.yaw())), Math.abs(wrap(pitch - player.pitch())));
    }

    private static double wrap(double value) {
        double result = value % 360.0;
        if (result >= 180.0) {
            result -= 360.0;
        }
        if (result < -180.0) {
            result += 360.0;
        }
        return result;
    }

    /** All entities matching the filters, sorted by distance. */
    public static List<IEntity> nearby(double range, boolean players, boolean mobs) {
        List<IEntity> result = new ArrayList<IEntity>();
        if (!Nexus.isInitialized() || !Nexus.instance().client().isInGame()) {
            return result;
        }
        for (IEntity entity : Nexus.instance().client().world().entities()) {
            if (!entity.isAlive() || entity.isLocalPlayer()) {
                continue;
            }
            if (entity.isPlayer() && !players) {
                continue;
            }
            if (!entity.isPlayer() && !mobs) {
                continue;
            }
            if (entity.distanceToPlayer() <= range) {
                result.add(entity);
            }
        }
        java.util.Collections.sort(result, new java.util.Comparator<IEntity>() {
            @Override
            public int compare(IEntity a, IEntity b) {
                return Double.compare(a.distanceToPlayer(), b.distanceToPlayer());
            }
        });
        return result;
    }
}
