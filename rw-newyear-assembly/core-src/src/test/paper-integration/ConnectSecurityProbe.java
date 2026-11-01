package net.kinggrief.probe;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Network-secret regression on a disposable Paper server, without a real cloud. */
public final class ConnectSecurityProbe extends JavaPlugin {
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { check(); getLogger().info("KG-CONNECT-SECURITY RESULT PASS checks=" + checks); }
            catch (Throwable failure) { getLogger().severe("KG-CONNECT-SECURITY RESULT FAIL " + failure); failure.printStackTrace(); }
        }, 20L);
    }
    private void ok(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
        getLogger().info("KG-CONNECT-SECURITY PASS " + description);
    }
    private static Field field(Plugin p, String name) throws Exception {
        Field f = p.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Method method(Plugin p, String name, Class<?>... types) throws Exception {
        Method m = p.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m;
    }
    private static String hmac(String raw, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
    private static String token(String key) throws Exception {
        String body = "FakePlayer|00000000-0000-4000-8000-000000000000|fixture|owner|" + System.currentTimeMillis() + "|hub";
        return body + "|" + hmac(body, key);
    }
    private static byte[] bus(String key, long id) throws Exception {
        String body = "unknown\tremote\tfixture\t\t\ttest\t" + id;
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        DataOutputStream msg = new DataOutputStream(inner);
        msg.writeUTF(body); msg.writeUTF(hmac(body, key));
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        DataOutputStream encoded = new DataOutputStream(outer);
        encoded.writeUTF("KingConnectBus"); encoded.writeShort(inner.size()); encoded.write(inner.toByteArray());
        return outer.toByteArray();
    }
    @SuppressWarnings("unchecked")
    private void check() throws Exception {
        Plugin p = Bukkit.getPluginManager().getPlugin("KingConnect");
        ok(p != null && p.isEnabled() && p.getDescription().getVersion().equals("2.3.1"), "new KingConnect starts");
        JavaPlugin plugin = (JavaPlugin) p;
        Method ready = method(p, "isNetworkSecretConfigured");
        Method accept = method(p, "handleVerifiedTokenString", String.class, org.bukkit.entity.Player.class);
        Method response = method(p, "applyCloudSyncResponse", String.class);
        Method reload = method(p, "reloadNetworkConfig");
        Map<String, ?> sso = (Map<String, ?>) field(p, "cloudSsoCache").get(p);
        Set<String> events = (Set<String>) field(p, "processedEventIds").get(p);
        ok("CHANGE_ME".equals(plugin.getConfig().getString("secret-key")), "fresh embedded configuration uses a placeholder");
        ok(!plugin.getConfig().getBoolean("cloud-sync.enabled", true), "cloud sync defaults to disabled");
        ok(!(boolean) ready.invoke(p) && !(boolean) field(p, "cloudSyncEnabled").get(p), "placeholder disables network authentication and cloud");
        accept.invoke(p, token("CHANGE_ME"), null);
        ok(sso.isEmpty(), "correctly signed placeholder SSO token is refused");
        ((PluginMessageListener) p).onPluginMessageReceived("BungeeCord", null, bus("CHANGE_ME", 1));
        ok(events.isEmpty(), "correctly signed placeholder proxy event is refused");
        response.invoke(p, "OK\nSSO\tFakePlayer\tfixture\towner\thub\n");
        ok(sso.isEmpty(), "cloud response cannot inject an SSO while disabled");

        String legacy = "KingGrief-Network-Secret-2026";
        plugin.getConfig().set("secret-key", legacy); plugin.getConfig().set("cloud-sync.enabled", true); plugin.saveConfig();
        reload.invoke(p);
        ok(!(boolean) ready.invoke(p) && !(boolean) field(p, "cloudSyncEnabled").get(p), "old public example key is rejected even if sync is enabled");
        accept.invoke(p, token(legacy), null);
        ok(sso.isEmpty(), "SSO signed with the old public example is refused");
        ((PluginMessageListener) p).onPluginMessageReceived("BungeeCord", null, bus(legacy, 2));
        ok(events.isEmpty(), "proxy events signed with the old public example are refused");

        String custom = "test-only-private-key-unique-and-long-12345";
        plugin.getConfig().set("secret-key", custom); plugin.getConfig().set("cloud-sync.enabled", false); plugin.saveConfig();
        reload.invoke(p);
        ok((boolean) ready.invoke(p) && !(boolean) field(p, "cloudSyncEnabled").get(p), "a custom key allows SSO without enabling cloud");
        accept.invoke(p, token("CHANGE_ME"), null);
        ok(sso.isEmpty(), "wrong signatures remain rejected with a custom key");
        accept.invoke(p, token(custom), null);
        ok(sso.containsKey("fakeplayer"), "properly signed SSO still works with a custom key");
        ((PluginMessageListener) p).onPluginMessageReceived("BungeeCord", null, bus(custom, 3));
        ok(events.size() == 1, "properly signed proxy event works with a custom key");
        plugin.getConfig().set("secret-key", "CHANGE_ME"); plugin.getConfig().set("cloud-sync.enabled", true); plugin.saveConfig();
        reload.invoke(p);
        ok(sso.isEmpty() && !(boolean) field(p, "cloudSyncEnabled").get(p), "reload to an unsafe key clears stale SSO and disables cloud");
    }
}
