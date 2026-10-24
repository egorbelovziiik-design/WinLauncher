package nexus.core.social;

import nexus.core.Nexus;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Friends and enemies lists (used by ESP colors, TargetHUD, chat filtering). */
public final class FriendManager {

    private final Nexus nexus;
    private final Set<String> friends = new LinkedHashSet<String>();
    private final Set<String> enemies = new LinkedHashSet<String>();
    private boolean friendsOnlyTarget;
    private boolean protectNames;

    public FriendManager(Nexus nexus) {
        this.nexus = nexus;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- friends

    public boolean addFriend(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        boolean added = friends.add(normalize(name));
        if (added) {
            nexus.configManager().markDirty();
            nexus.notifications().success("Friend", "Added " + name);
        }
        return added;
    }

    public boolean removeFriend(String name) {
        boolean removed = friends.remove(normalize(name));
        if (removed) {
            nexus.configManager().markDirty();
            nexus.notifications().info("Friend", "Removed " + name);
        }
        return removed;
    }

    public boolean isFriend(String name) {
        return friends.contains(normalize(name));
    }

    public List<String> friends() {
        return new ArrayList<String>(friends);
    }

    // ---------------------------------------------------------------- enemies

    public boolean addEnemy(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        boolean added = enemies.add(normalize(name));
        if (added) {
            nexus.configManager().markDirty();
            nexus.notifications().warn("Enemy", "Added " + name);
        }
        return added;
    }

    public boolean removeEnemy(String name) {
        boolean removed = enemies.remove(normalize(name));
        if (removed) {
            nexus.configManager().markDirty();
            nexus.notifications().info("Enemy", "Removed " + name);
        }
        return removed;
    }

    public boolean isEnemy(String name) {
        return enemies.contains(normalize(name));
    }

    public List<String> enemies() {
        return new ArrayList<String>(enemies);
    }

    public void clear() {
        friends.clear();
        enemies.clear();
    }

    // ---------------------------------------------------------------- options

    public boolean friendsOnlyTarget() {
        return friendsOnlyTarget;
    }

    public void setFriendsOnlyTarget(boolean value) {
        friendsOnlyTarget = value;
    }

    public boolean protectNames() {
        return protectNames;
    }

    public void setProtectNames(boolean value) {
        protectNames = value;
    }

    /** Masks a name for the streamer mode / name protect feature. */
    public String protect(String name) {
        if (!protectNames || name == null || name.isEmpty()) {
            return name;
        }
        if (isFriend(name)) {
            return name;
        }
        return "Player" + Math.abs(name.hashCode() % 900 + 100);
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        Json.JsonArray friendArray = Json.array();
        for (String friend : friends) {
            friendArray.add(friend);
        }
        Json.JsonArray enemyArray = Json.array();
        for (String enemy : enemies) {
            enemyArray.add(enemy);
        }
        object.set("friends", friendArray);
        object.set("enemies", enemyArray);
        object.set("friendsOnlyTarget", friendsOnlyTarget);
        object.set("protectNames", protectNames);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        friends.clear();
        enemies.clear();
        Json friendsValue = object.get("friends");
        if (friendsValue.isArray()) {
            Json.JsonArray array = (Json.JsonArray) friendsValue;
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof Json.JsonString) {
                    friends.add(((Json.JsonString) array.get(i)).value());
                }
            }
        }
        Json enemiesValue = object.get("enemies");
        if (enemiesValue.isArray()) {
            Json.JsonArray array = (Json.JsonArray) enemiesValue;
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof Json.JsonString) {
                    enemies.add(((Json.JsonString) array.get(i)).value());
                }
            }
        }
        friendsOnlyTarget = object.bool("friendsOnlyTarget", false);
        protectNames = object.bool("protectNames", false);
    }
}
