package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Display NPC lifecycle and real protocol hitbox interactions on Paper 1.21.4. */
public final class NpcProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private SpawnEngine engine;
    private Player player;
    private World world;
    private List<SpawnEngine.NpcDef> defs;
    private final List<int[]> tickets = new ArrayList<int[]>();
    private final Set<String> requested = new LinkedHashSet<String>();
    private Iterator<String> chunks;
    private int checks, errors;
    private boolean running, finished;

    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @SuppressWarnings("unchecked") private <T> T field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(object);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if (running) return;
        running = true; player = e.getPlayer();
        later(35, () -> begin());
    }
    private void later(long delay, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); } catch (Throwable t) { fail(t); } }, delay);
    }
    private void ok(boolean condition, String message) {
        checks++;
        if (!condition) { errors++; getLogger().severe("KG-NPC FAIL: " + message); }
    }
    private void fail(Throwable t) {
        errors++; getLogger().severe("KG-NPC EXCEPTION: " + t); t.printStackTrace(); finish();
    }
    private void finish() {
        if (finished) return; finished = true;
        if (world != null) for (int[] c : tickets) world.removePluginChunkTicket(c[0], c[1], this);
        getLogger().info("KG-NPC RESULT " + (errors == 0 ? "PASS" : "FAIL") + " checks=" + checks + " errors=" + errors);
    }
    private void begin() {
        try {
            core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
            engine = core.getSpawnEngine(); world = Bukkit.getWorld("spawn_world");
            defs = field(engine, "npcDefs");
            ok(world != null && defs.size() == 28, "prepared spawn and 28 NPC definitions");
            ok(!world.isChunkLoaded(40, 40), "remote chunk not loaded before cleanup");
            int forced=0; for(int x=-8;x<=8;x++) for(int z=-8;z<=8;z++) if(world.isChunkForceLoaded(x,z)) forced++;
            ok(forced==0,"clean new template does not force-load the old 289-chunk square");
            Entity foreign = world.spawnEntity(player.getLocation(), EntityType.SKELETON);
            Entity moderation = world.spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
            moderation.addScoreboardTag("kg_mod_anim"); moderation.addScoreboardTag("kg_npc");
            Entity legacy = world.spawnEntity(player.getLocation(), EntityType.SKELETON);
            legacy.addScoreboardTag("kg_npc"); legacy.addScoreboardTag("kg_npc_rtp");
            engine.purgeAllSpawnEntities(world);
            ok(!world.isChunkLoaded(40, 40), "cleanup does not generate remote chunk");
            ok(foreign.isValid() && moderation.isValid() && !legacy.isValid(), "foreign and moderation objects survive, legacy NPC removed");
            foreign.remove(); moderation.remove();
            engine.enforceStrictSingletonsOnSpawn(world);
            for (SpawnEngine.NpcDef d : defs) requested.add((((int)Math.floor(d.x)) >> 4) + "," + (((int)Math.floor(d.z)) >> 4));
            requested.add("4,-1"); requested.add("-3,-3"); requested.add("-2,-3");
            chunks = requested.iterator();
            loadNext();
        } catch (Throwable t) { fail(t); }
    }
    private void loadNext() {
        if (!chunks.hasNext()) { later(30, () -> inspectModels()); return; }
        String[] xy = chunks.next().split(","); final int x = Integer.parseInt(xy[0]), z = Integer.parseInt(xy[1]);
        try {
            CompletableFuture<?> future = (CompletableFuture<?>) World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class)
                    .invoke(world, x, z, true);
            future.whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(this, () -> {
                if (error != null || chunk == null) { fail(new RuntimeException("async fixture chunk " + x + "," + z, error)); return; }
                world.addPluginChunkTicket(x, z, this); tickets.add(new int[]{x, z});
                later(2, () -> loadNext());
            }));
        } catch (Throwable t) { fail(t); }
    }
    private void inspectModels() {
        try {
            engine.enforceStrictSingletonsOnSpawn(world);
            Map<String, Object> models = field(engine, "npcModels");
            Map<String, UUID> hits = field(engine, "npcHitboxMap");
            Map<String, UUID> holos = field(engine, "npcHoloMap");
            Set<UUID> valid = field(engine, "validSpawnEntities");
            Set<Material> coats = new HashSet<Material>();
            int heads = 0, parts = 0;
            ok(models.size() == 28 && hits.size() == 28 && holos.size() == 28, "one model, hitbox and hologram per definition");
            for (SpawnEngine.NpcDef d : defs) {
                Object model = models.get(d.id);
                ok(model != null, "model exists: " + d.id);
                if (model == null) continue;
                List<?> modelParts = field(model, "parts");
                ok(modelParts.size() >= 12, "articulated model has enough parts: " + d.id);
                for (Object part : modelParts) {
                    UUID id = field(part, "id"); Entity e = Bukkit.getEntity(id); parts++;
                    ok(e instanceof BlockDisplay || e instanceof ItemDisplay, "part is display, not mob/stand: " + d.id);
                    ok(e != null && !e.isPersistent() && valid.contains(id), "part is nonpersistent and session-owned: " + d.id);
                    if (e instanceof BlockDisplay && e.getScoreboardTags().contains("kg_part_torso")) coats.add(((BlockDisplay)e).getBlock().getMaterial());
                    if (e instanceof ItemDisplay && e.getScoreboardTags().contains("kg_part_head")) {
                        ItemStack head = ((ItemDisplay)e).getItemStack();
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        if (head.getType() == Material.PLAYER_HEAD && meta.getOwnerProfile() != null && meta.getOwnerProfile().getTextures().getSkin() != null) heads++;
                    }
                }
                Entity h = Bukkit.getEntity(hits.get(d.id));
                Entity t = Bukkit.getEntity(holos.get(d.id));
                ok(h instanceof Interaction && engine.getNpcByEntity(h) == d, "canonical interactive hitbox: " + d.id);
                ok(t instanceof TextDisplay && engine.getNpcByEntity(t) == null, "hologram is not a mob/stand or hitbox: " + d.id);
            }
            ok(heads == 28 && coats.size() >= 6 && parts > 300, "distinct outfits, profile heads and individual model parts");
            // An untrusted/obsolete tagged interaction is not a canonical NPC.
            Interaction fake = (Interaction) world.spawnEntity(player.getLocation(), EntityType.INTERACTION);
            fake.addScoreboardTag("kg_npc"); fake.addScoreboardTag("kg_npc_rtp");
            ok(engine.getNpcByEntity(fake) == null, "fake tagged interaction rejected");
            engine.enforceStrictSingletonsOnSpawn(world);
            ok(!fake.isValid(), "old tagged interaction removed on reconciliation");
            // Removing one body part must reconstruct the whole model, without duplicates.
            Object oldModel = models.get("rtp"); List<?> oldParts = field(oldModel, "parts"); UUID missing = field(oldParts.get(0), "id");
            Entity lost = Bukkit.getEntity(missing); lost.remove();
            engine.enforceStrictSingletonsOnSpawn(world);
            Object newModel = models.get("rtp");
            ok(newModel != oldModel && !valid.contains(missing), "partial model rebuilt and old IDs released");
            int count = 0; for (Entity e : world.getEntities()) if (e.getScoreboardTags().contains("kg_npc_rtp") && e.getScoreboardTags().contains("kg_npc_model_part")) count++;
            List<?> freshParts = field(newModel, "parts");
            ok(count == freshParts.size(), "no duplicate rtp model parts after rebuild");
            ok(!world.isChunkLoaded(40, 40), "reconciliation did not load remote chunk");
            Entity hit = Bukkit.getEntity(hits.get("rtp"));
            player.teleport(new Location(world, -6.2, 100, 42.0, 90, 0));
            later(40, () -> {
                try {
                    float yaw = field(newModel, "yaw");
                    ok(Math.abs(yaw - (-35f)) > 1f, "model rotates towards a nearby player");
                    request("RIGHT", hit.getEntityId());
                    later(15, () -> afterRight(hit));
                } catch (Throwable t) { fail(t); }
            });
        } catch (Throwable t) { fail(t); }
    }
    private void request(String kind, int id) { getLogger().info("KG-NPC REQUEST " + kind + " " + id); }
    private boolean rtpOpen() {
        Object holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof GuiManager.KgMenuHolder && "rtp".equals(((GuiManager.KgMenuHolder) holder).menuType);
    }
    private void afterRight(Entity hit) {
        ok(rtpOpen(), "real right-click opens the correct NPC menu");
        player.closeInventory(); request("AT", hit.getEntityId());
        later(15, () -> {
            ok(rtpOpen(), "real interact-at opens the menu");
            player.closeInventory(); request("OFF", hit.getEntityId());
            later(15, () -> {
                ok(!rtpOpen(), "offhand duplicate does not reopen the menu");
                request("LEFT", hit.getEntityId());
                later(15, () -> {
                    ok(rtpOpen(), "real left-click opens the menu");
                    player.closeInventory();
                    core.getStore().toggleQuickFreeze(player.getUniqueId());
                    request("RIGHT", hit.getEntityId());
                    later(15, () -> {
                        ok(!rtpOpen(), "frozen player cannot use NPC");
                        core.getStore().clearQuickFreeze(player.getUniqueId());
                        economy();
                    });
                });
            });
        });
    }
    private void economy() {
        player.getInventory().clear();
        for (int i=0; i<36; i++) player.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
        core.getStore().setCoins(player.getName(), 50000);
        core.getStore().addSnowflakes(player.getName(), 10);
        double before = core.getStore().getCoins(player.getName()); int snow = core.getStore().getSnowflakes(player.getName());
        engine.spinFortuneWheel(player, false); engine.spinFortuneWheel(player, true);
        ok(core.getStore().getCoins(player.getName()) == before && core.getStore().getSnowflakes(player.getName()) == snow,
                "full inventory: wheel charges neither coins nor snowflakes");
        SpawnEngine.CasePedestal ditems = null;
        for (SpawnEngine.CasePedestal cp : engine.getCasePedestals()) if ("ditems".equals(cp.caseId)) ditems = cp;
        final SpawnEngine.CasePedestal chosen = ditems;
        core.getStore().addCaseKeys(player.getName(), "ditems", 1);
        int keys = core.getStore().getCaseKeys(player.getName(), "ditems");
        engine.openShulkerCaseAnimated(player, chosen);
        ok(core.getStore().getCaseKeys(player.getName(), "ditems") == keys, "full inventory: case key not charged");
        player.getInventory().clear();
        engine.openShulkerCaseAnimated(player, chosen);
        ok(core.getStore().getCaseKeys(player.getName(), "ditems") == keys, "case key reserved without charging during animation");
        for (int i=0; i<36; i++) player.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE,64));
        later(75, () -> {
            ok(core.getStore().getCaseKeys(player.getName(), "ditems") == keys, "inventory filled during animation: case key preserved");
            unloadAndReload();
        });
    }
    private void unloadAndReload() {
        try {
            // Isolated fixture: release tickets and leave spawn_world so Paper can unload a distant NPC chunk.
            world.setKeepSpawnInMemory(false);
            for (int[] c : tickets) world.removePluginChunkTicket(c[0], c[1], this);
            tickets.clear();
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            final int cx=-5, cz=4;
            world.unloadChunkRequest(cx,cz);
            later(100,()->{try{
                if(world.isChunkLoaded(cx,cz)) world.unloadChunk(cx,cz,true);
                boolean unloaded=!world.isChunkLoaded(cx,cz);
                ok(unloaded,"distant NPC chunk actually unloads after tickets are released");
                if(!unloaded) { finish(); return; }
                CompletableFuture<?> future=(CompletableFuture<?>)World.class.getMethod("getChunkAtAsync",int.class,int.class,boolean.class).invoke(world,cx,cz,true);
                future.whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
                    if(error!=null || chunk==null) { fail(new RuntimeException("reloading NPC chunk",error)); return; }
                    world.addPluginChunkTicket(cx,cz,this);tickets.add(new int[]{cx,cz});
                    later(30,()->{try{
                        Map<String,UUID> hits=field(engine,"npcHitboxMap");Map<String,Object> models=field(engine,"npcModels");
                        Entity hit=Bukkit.getEntity(hits.get("casino_npc"));
                        ok(hit instanceof Interaction && models.get("casino_npc")!=null,"display model and hitbox restored after real chunk reload");
                        int count=0;for(Entity e:world.getChunkAt(cx,cz).getEntities())if(e.getScoreboardTags().contains("kg_npc_casino_npc") && e.getScoreboardTags().contains("kg_npc_model_part"))count++;
                        List<?> parts=field(models.get("casino_npc"),"parts");
                        ok(count==parts.size(),"chunk reload does not duplicate the model");
                        Entity legacy=world.spawnEntity(new Location(world,-65.5,101,68.5),EntityType.SKELETON);
                        legacy.addScoreboardTag("kg_npc");legacy.addScoreboardTag("kg_npc_casino_npc");
                        Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.EntitiesLoadEvent(world.getChunkAt(cx,cz),Arrays.asList(legacy)));
                        later(15,()->{
                            ok(!legacy.isValid(),"entity-slice load triggers stale NPC cleanup");
                            ok(!world.isChunkLoaded(40,40),"chunk reload reconciliation does not generate distant terrain");finish();
                        });
                    }catch(Throwable t){fail(t);}});
                }));
            }catch(Throwable t){fail(t);}});
        } catch(Throwable t){fail(t);}
    }
}
