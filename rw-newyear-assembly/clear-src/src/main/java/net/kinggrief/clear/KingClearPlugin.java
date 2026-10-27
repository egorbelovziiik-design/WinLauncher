package net.kinggrief.clear;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KingClearPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final Set<String> TRASH_MATERIALS = new HashSet<String>(Arrays.asList(
            "COBBLESTONE", "COBBLED_DEEPSLATE", "STONE", "DEEPSLATE", "DIRT", "COARSE_DIRT",
            "ROOTED_DIRT", "GRAVEL", "SAND", "RED_SAND", "DIORITE", "ANDESITE", "GRANITE",
            "TUFF", "CALCITE", "NETHERRACK", "BASALT", "BLACKSTONE", "GLASS_BOTTLE", "BOWL",
            "ROTTEN_FLESH", "SPIDER_EYE", "BONE", "STRING", "GUNPOWDER", "FEATHER", "FLINT",
            "WHEAT_SEEDS", "BEETROOT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS", "KELP",
            "SEAGRASS", "BAMBOO", "SUGAR_CANE", "SNOWBALL", "POISONOUS_POTATO", "DEAD_BUSH",
            "OAK_SAPLING", "SPRUCE_SAPLING", "BIRCH_SAPLING", "JUNGLE_SAPLING", "ACACIA_SAPLING",
            "DARK_OAK_SAPLING", "STICK"
    ));

    private static final class ClearGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class UndoSnapshot {
        final ItemStack[] storage;
        final ItemStack[] armor;
        final ItemStack offhand;
        final long expiresAtMs;
        final int totalItems;

        UndoSnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, long expiresAtMs, int totalItems) {
            this.storage = storage;
            this.armor = armor;
            this.offhand = offhand;
            this.expiresAtMs = expiresAtMs;
            this.totalItems = totalItems;
        }
    }

    private final Map<UUID, UndoSnapshot> undoMap = new HashMap<UUID, UndoSnapshot>();
    private final Map<UUID, Long> combatUntilMs = new HashMap<UUID, Long>();
    private final Map<UUID, Long> cooldownUntilMs = new HashMap<UUID, Long>();

    private int pvpTagSeconds = 15;
    private int undoSeconds = 30;
    private boolean keepSpawnMenuStar = true;
    private int cooldownSeconds = 2;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pvpTagSeconds = Math.max(5, getConfig().getInt("pvp-combat-tag-seconds", 15));
        this.undoSeconds = Math.max(10, getConfig().getInt("undo-expire-seconds", 30));
        this.keepSpawnMenuStar = getConfig().getBoolean("keep-spawn-menu-star", true);
        this.cooldownSeconds = Math.max(0, getConfig().getInt("cooldown-seconds", 2));

        PluginCommand cmd = getCommand("clear");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("[KingClear v1.0] Loaded! Commands: /clear, /ci, /clearinv, /clear gui, /clear inv, /clear trash, /clear undo.");
    }

    @Override
    public void onDisable() {
        undoMap.clear();
        combatUntilMs.clear();
        cooldownUntilMs.clear();
    }

    // ================= COLOR & SOUND HELPERS =================
    public static String c(String s) {
        if (s == null) return "";
        Matcher m = HEX_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder rep = new StringBuilder("\u00A7x");
            for (int i = 0; i < 6; i++) {
                rep.append('\u00A7').append(Character.toLowerCase(hex.charAt(i)));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private void playSound(Player p, String soundKey, float vol, float pitch) {
        try {
            p.playSound(p.getLocation(), "minecraft:" + soundKey, vol, pitch);
        } catch (Throwable ignored) {}
    }

    // ================= PVP & KINGGRIEFCORE INTEGRATION =================
    private boolean isInCombat(Player p) {
        Long until = combatUntilMs.get(p.getUniqueId());
        if (until != null && until.longValue() > System.currentTimeMillis()) {
            return true;
        }
        // Check KingGriefCore HudManager.isInCombat(p) if present
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getHud = kg.getClass().getMethod("getHud");
                Object hud = getHud.invoke(kg);
                if (hud != null) {
                    Method inCombat = hud.getClass().getMethod("isInCombat", Player.class);
                    Object res = inCombat.invoke(hud, p);
                    if (Boolean.TRUE.equals(res)) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean canClearOthers(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
        if (kg != null && kg.isEnabled()) {
            // Strict OWNER-only rule when running alongside KingGriefCore!
            try {
                Method getRanks = kg.getClass().getMethod("getRanks");
                Object ranks = getRanks.invoke(kg);
                Method getRank = ranks.getClass().getMethod("getRank", Player.class);
                Object rankDef = getRank.invoke(ranks, p);
                if (rankDef != null) {
                    Object idVal = rankDef.getClass().getField("id").get(rankDef);
                    return "owner".equalsIgnoreCase(String.valueOf(idVal));
                }
            } catch (Throwable ignored) {}
            return false;
        }
        return p.hasPermission("kingclear.others") || p.isOp();
    }

    private boolean isProtectedSpawnMenuStar(Player p, ItemStack it) {
        if (!keepSpawnMenuStar || it == null || it.getType() != Material.NETHER_STAR || !it.hasItemMeta()) {
            return false;
        }
        if (!"spawn_world".equalsIgnoreCase(p.getWorld().getName())) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null) {
                NamespacedKey key = new NamespacedKey(kg, "kg_id");
                String cid = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if ("menu_star".equalsIgnoreCase(cid)) return true;
            }
        } catch (Throwable ignored) {}
        return meta.hasDisplayName() && meta.getDisplayName().contains("МЕНЮ СЕРВЕРА");
    }

    private boolean isTrashItem(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return false;
        if (!TRASH_MATERIALS.contains(it.getType().name())) return false;
        if (!it.hasItemMeta()) return true;
        ItemMeta m = it.getItemMeta();
        if (m == null) return true;
        if (m.hasDisplayName() || m.hasLore() || m.hasEnchants() || m.hasCustomModelData()) {
            return false;
        }
        return m.getPersistentDataContainer().isEmpty();
    }

    // ================= SNAPSHOT & RESTORE (30s UNDO) =================
    private void saveUndoSnapshot(Player p, int clearedCount) {
        if (clearedCount <= 0) return;
        PlayerInventory inv = p.getInventory();
        ItemStack[] rawStorage = inv.getStorageContents();
        ItemStack[] copyStorage = new ItemStack[rawStorage.length];
        for (int i = 0; i < rawStorage.length; i++) {
            copyStorage[i] = rawStorage[i] != null ? rawStorage[i].clone() : null;
        }
        ItemStack[] rawArmor = inv.getArmorContents();
        ItemStack[] copyArmor = new ItemStack[rawArmor.length];
        for (int i = 0; i < rawArmor.length; i++) {
            copyArmor[i] = rawArmor[i] != null ? rawArmor[i].clone() : null;
        }
        ItemStack off = inv.getItemInOffHand();
        ItemStack copyOff = (off != null && off.getType() != Material.AIR) ? off.clone() : null;

        undoMap.put(p.getUniqueId(), new UndoSnapshot(
                copyStorage, copyArmor, copyOff,
                System.currentTimeMillis() + (undoSeconds * 1000L),
                clearedCount
        ));
    }

    private void sendUndoButton(Player p) {
        try {
            TextComponent prefix = new TextComponent(c("&#80D8FF💡 Очистили случайно? "));
            TextComponent btn = new TextComponent(c("&#00E676&l[ ↩ ВЕРНУТЬ ПРЕДМЕТЫ (" + undoSeconds + " СЕК) ]"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clear undo"));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(c("&aНажмите, чтобы восстановить удалённые предметы!\n&7Команда: &f/clear undo")).create()));
            p.spigot().sendMessage(prefix, btn);
        } catch (Throwable fallback) {
            p.sendMessage(c("&#80D8FF💡 Случайно очистили? Введите &#00E676/clear undo &7(в течение " + undoSeconds + " сек.)"));
        }
    }

    // ================= CLEAR OPERATIONS =================
    private void executeFullClear(Player target, CommandSender initiator, boolean includeArmorAndOffhand) {
        PlayerInventory inv = target.getInventory();
        int counted = 0;

        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack it = storage[i];
            if (it != null && it.getType() != Material.AIR && !isProtectedSpawnMenuStar(target, it)) {
                counted += it.getAmount();
            }
        }
        if (includeArmorAndOffhand) {
            for (ItemStack arm : inv.getArmorContents()) {
                if (arm != null && arm.getType() != Material.AIR) counted += arm.getAmount();
            }
            ItemStack off = inv.getItemInOffHand();
            if (off != null && off.getType() != Material.AIR && !isProtectedSpawnMenuStar(target, off)) {
                counted += off.getAmount();
            }
        }
        ItemStack cursor = target.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR && !isProtectedSpawnMenuStar(target, cursor)) {
            counted += cursor.getAmount();
        }

        if (counted == 0) {
            if (initiator.equals(target)) {
                playSound(target, "entity.villager.no", 0.8f, 1.1f);
                target.sendMessage(c("&#FFD740🗑 Ваш инвентарь уже пуст!"));
            } else {
                initiator.sendMessage(c("&#FFD740🗑 Инвентарь игрока &f" + target.getName() + " &#FFD740уже пуст!"));
            }
            return;
        }

        if (initiator.equals(target)) {
            saveUndoSnapshot(target, counted);
        }

        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null && !isProtectedSpawnMenuStar(target, storage[i])) {
                inv.setItem(i, null);
            }
        }
        if (includeArmorAndOffhand) {
            inv.setArmorContents(new ItemStack[4]);
            if (!isProtectedSpawnMenuStar(target, inv.getItemInOffHand())) {
                inv.setItemInOffHand(null);
            }
        }
        if (cursor != null && cursor.getType() != Material.AIR && !isProtectedSpawnMenuStar(target, cursor)) {
            target.setItemOnCursor(null);
        }
        target.updateInventory();

        playSound(target, "entity.item.pickup", 0.9f, 0.75f);
        playSound(target, "block.lava.extinguish", 0.55f, 1.55f);

        if (initiator.equals(target)) {
            String modeNote = includeArmorAndOffhand
                    ? "&7(Инвентарь + Броня + Левая рука)"
                    : "&7(Броня и Левая рука сохранены!)";
            target.sendMessage(c("&#00E676🗑 Инвентарь успешно очищен! &fУдалено предметов: &#FFD740" + counted + " шт. " + modeNote));
            sendUndoButton(target);
        } else {
            target.sendMessage(c("&#FF5252🗑 Ваш инвентарь был очищен администратором &#FFD740" + initiator.getName() + "&#FF5252!"));
            initiator.sendMessage(c("&#00E676✔ Инвентарь игрока &#FFD740" + target.getName() + " &#00E676очищен! Удалено: &f" + counted + " шт."));
        }
    }

    private void executeTrashClear(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        int counted = 0;
        for (int i = 0; i < storage.length; i++) {
            if (isTrashItem(storage[i])) {
                counted += storage[i].getAmount();
            }
        }
        if (counted == 0) {
            playSound(p, "entity.villager.no", 0.8f, 1.1f);
            p.sendMessage(c("&#FFD740🧹 В вашем инвентаре нет лишнего мусора или пустых бутылочек!"));
            return;
        }
        saveUndoSnapshot(p, counted);
        for (int i = 0; i < storage.length; i++) {
            if (isTrashItem(storage[i])) {
                inv.setItem(i, null);
            }
        }
        p.updateInventory();
        playSound(p, "entity.experience_orb.pickup", 0.9f, 1.3f);
        p.sendMessage(c("&#00E676🧹 Мусор и пустые колбы очищены! &fУдалено: &#FFD740" + counted + " шт. &7(Ценные вещи не затронуты)"));
        sendUndoButton(p);
    }

    private void executeHandClear(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || isProtectedSpawnMenuStar(p, hand)) {
            playSound(p, "entity.villager.no", 0.8f, 1.1f);
            p.sendMessage(c("&#FF5252✖ Возьмите в основную руку предмет, который хотите удалить!"));
            return;
        }
        int amt = hand.getAmount();
        String name = (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName())
                ? hand.getItemMeta().getDisplayName()
                : hand.getType().name();
        saveUndoSnapshot(p, amt);
        p.getInventory().setItemInMainHand(null);
        p.updateInventory();
        playSound(p, "block.lava.extinguish", 0.55f, 1.6f);
        p.sendMessage(c("&#00E676🗑 Предмет в руке удалён: &f" + name + " &7(×" + amt + ")"));
        sendUndoButton(p);
    }

    private void executeArmorClear(Player p) {
        PlayerInventory inv = p.getInventory();
        int counted = 0;
        for (ItemStack arm : inv.getArmorContents()) {
            if (arm != null && arm.getType() != Material.AIR) counted += arm.getAmount();
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() != Material.AIR && !isProtectedSpawnMenuStar(p, off)) {
            counted += off.getAmount();
        }
        if (counted == 0) {
            playSound(p, "entity.villager.no", 0.8f, 1.1f);
            p.sendMessage(c("&#FFD740🛡 На вас нет брони и предмета в левой руке!"));
            return;
        }
        saveUndoSnapshot(p, counted);
        inv.setArmorContents(new ItemStack[4]);
        if (!isProtectedSpawnMenuStar(p, off)) {
            inv.setItemInOffHand(null);
        }
        p.updateInventory();
        playSound(p, "item.armor.equip_generic", 0.9f, 0.8f);
        p.sendMessage(c("&#00E676🛡 Броня и левая рука очищены! &fУдалено: &#FFD740" + counted + " шт."));
        sendUndoButton(p);
    }

    private void executeUndo(Player p) {
        if (isInCombat(p)) {
            p.sendMessage(c("&#FF1744⚔ Нельзя восстанавливать инвентарь во время режима PvP!"));
            return;
        }
        UndoSnapshot snap = undoMap.remove(p.getUniqueId());
        if (snap == null || System.currentTimeMillis() > snap.expiresAtMs) {
            playSound(p, "entity.villager.no", 0.8f, 1.0f);
            p.sendMessage(c("&#FF5252✖ Нет недавней очистки для отмены (срок хранения — " + undoSeconds + " сек.)!"));
            return;
        }
        PlayerInventory inv = p.getInventory();
        int restored = 0;

        // Restore storage slots safely (never overwrite items the player picked up in the meantime — drop overflow safely)
        ItemStack[] curStorage = inv.getStorageContents();
        for (int i = 0; i < snap.storage.length && i < curStorage.length; i++) {
            ItemStack oldIt = snap.storage[i];
            if (oldIt == null || oldIt.getType() == Material.AIR) continue;
            if (isProtectedSpawnMenuStar(p, oldIt)) continue;
            if (curStorage[i] == null || curStorage[i].getType() == Material.AIR) {
                inv.setItem(i, oldIt.clone());
            } else {
                for (ItemStack rem : inv.addItem(oldIt.clone()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
            }
            restored += oldIt.getAmount();
        }

        // Restore armor
        ItemStack[] curArmor = inv.getArmorContents();
        for (int i = 0; i < snap.armor.length && i < curArmor.length; i++) {
            ItemStack oldArm = snap.armor[i];
            if (oldArm == null || oldArm.getType() == Material.AIR) continue;
            if (curArmor[i] == null || curArmor[i].getType() == Material.AIR) {
                curArmor[i] = oldArm.clone();
            } else {
                for (ItemStack rem : inv.addItem(oldArm.clone()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
            }
            restored += oldArm.getAmount();
        }
        inv.setArmorContents(curArmor);

        // Restore offhand
        if (snap.offhand != null && snap.offhand.getType() != Material.AIR && !isProtectedSpawnMenuStar(p, snap.offhand)) {
            ItemStack curOff = inv.getItemInOffHand();
            if (curOff == null || curOff.getType() == Material.AIR) {
                inv.setItemInOffHand(snap.offhand.clone());
            } else {
                for (ItemStack rem : inv.addItem(snap.offhand.clone()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
            }
            restored += snap.offhand.getAmount();
        }

        p.updateInventory();
        playSound(p, "entity.player.levelup", 0.9f, 1.35f);
        p.sendMessage(c("&#00E676↩ Очистка отменена! Восстановлено предметов: &#FFD740" + restored + " шт."));
    }

    // ================= GUI MENU (/clear gui) =================
    private ItemStack makeIcon(Material mat, String title, String... loreLines) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(c(title));
            if (loreLines != null && loreLines.length > 0) {
                List<String> l = new ArrayList<String>();
                for (String line : loreLines) l.add(c(line));
                m.setLore(l);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }
        return it;
    }

    public void openClearGui(Player p) {
        Inventory gui = Bukkit.createInventory(new ClearGuiHolder(), 27, c("&#FF1744&l🗑 ОЧИСТКА ИНВЕНТАРЯ (/clear)"));
        ItemStack glass = makeIcon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) gui.setItem(i, glass);

        gui.setItem(10, makeIcon(Material.LAVA_BUCKET, "&#FF1744&l🔥 Полная очистка (/clear)",
                "&7Удаляет все предметы из инвентаря,",
                "&7надетую броню и предмет в левой руке!",
                "",
                "&#80D8FF💡 Доступна отмена /clear undo (" + undoSeconds + " сек.)",
                "&#FF5252▸ Нажмите для полной очистки"));

        gui.setItem(12, makeIcon(Material.CHEST, "&#FFD740&l🎒 Только слоты (/clear inv)",
                "&7Очищает 36 основных слотов инвентаря,",
                "&aСОХРАНЯЯ &7надетую Броню и Левую руку",
                "&7(Талисман / 3D Шар / Тотем)!",
                "",
                "&#00E676▸ Нажмите для очистки слотов"));

        gui.setItem(14, makeIcon(Material.BRUSH, "&#00E5FF&l🧹 Только мусор (/clear trash)",
                "&7Удаляет булыжник, землю, гравий, камень,",
                "&7пустые пузырьки, гнилую плоть и семена!",
                "&aЦенные и зачарованные вещи не трогает.",
                "",
                "&#00E676▸ Нажмите для очистки мусора"));

        gui.setItem(16, makeIcon(Material.SHEARS, "&#E040FB&l✋ Предмет в руке (/clear hand)",
                "&7Удаляет только тот предмет,",
                "&7который вы держите в основной руке!",
                "",
                "&#00E676▸ Нажмите для удаления"));

        UndoSnapshot snap = undoMap.get(p.getUniqueId());
        boolean hasUndo = snap != null && System.currentTimeMillis() <= snap.expiresAtMs;
        if (hasUndo) {
            long secLeft = Math.max(1L, (snap.expiresAtMs - System.currentTimeMillis()) / 1000L);
            gui.setItem(22, makeIcon(Material.TOTEM_OF_UNDYING, "&#00E676&l↩ ВЕРНУТЬ ПРЕДМЕТЫ (" + secLeft + " сек.)",
                    "&7Восстановить последнюю очистку",
                    "&7(&f" + snap.totalItems + " шт.&7) обратно в инвентарь!",
                    "",
                    "&#00E676▸ Нажмите для возврата!"));
        } else {
            gui.setItem(22, makeIcon(Material.BARRIER, "&c✖ Закрыть меню", "&7Нажмите, чтобы выйти без очистки"));
        }

        p.openInventory(gui);
    }

    // ================= COMMAND EXECUTION =================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(c("&cИспользование из консоли: /clear <ник>"));
                return true;
            }
            Player p = (Player) sender;
            if (!checkSelfPreconditions(p)) return true;
            executeFullClear(p, p, true);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub) || "?".equals(sub)) {
            sender.sendMessage(c("&#FFD740&l🗑 Команды плагина KingClear:"));
            sender.sendMessage(c(" &#00E676/clear &8— &fМгновенно очистить весь инвентарь"));
            sender.sendMessage(c(" &#00E676/clear inv &8— &fОчистить слоты (сохранить Броню и Левую руку)"));
            sender.sendMessage(c(" &#00E676/clear trash &8— &fОчистить только мусор и пустые бутылочки"));
            sender.sendMessage(c(" &#00E676/clear hand &8— &fУдалить предмет в главной руке"));
            sender.sendMessage(c(" &#00E676/clear gui &8— &fОткрыть удобное GUI-меню очистки"));
            sender.sendMessage(c(" &#00E676/clear undo &8— &fВернуть удалённые предметы (" + undoSeconds + " сек.)"));
            if (canClearOthers(sender)) {
                sender.sendMessage(c(" &#FF5252/clear <ник> &8— &fОчистить инвентарь другого игрока [OWNER]"));
            }
            return true;
        }

        if ("gui".equals(sub) || "menu".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (isInCombat(p)) {
                p.sendMessage(c("&#FF1744⚔ Нельзя очищать инвентарь во время боя (PvP)!"));
                return true;
            }
            openClearGui(p);
            return true;
        }

        if ("undo".equals(sub) || "back".equals(sub) || "restore".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            executeUndo((Player) sender);
            return true;
        }

        if ("all".equals(sub) || "full".equals(sub) || "confirm".equals(sub) || "-f".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!checkSelfPreconditions(p)) return true;
            executeFullClear(p, p, true);
            return true;
        }

        if ("inv".equals(sub) || "slots".equals(sub) || "keeparmor".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!checkSelfPreconditions(p)) return true;
            executeFullClear(p, p, false);
            return true;
        }

        if ("trash".equals(sub) || "junk".equals(sub) || "musor".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!checkSelfPreconditions(p)) return true;
            executeTrashClear(p);
            return true;
        }

        if ("hand".equals(sub) || "item".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!checkSelfPreconditions(p)) return true;
            executeHandClear(p);
            return true;
        }

        if ("armor".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!checkSelfPreconditions(p)) return true;
            executeArmorClear(p);
            return true;
        }

        // Otherwise treat args[0] as target player nickname (/clear <nick>)
        if (!canClearOthers(sender)) {
            sender.sendMessage(c("&#FF1744✖ Очищать инвентарь других игроков может только Владелец (OWNER)! Для себя используйте: &#FFD740/clear"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(c("&#FF5252✖ Игрок &f" + args[0] + " &#FF5252сейчас не в сети!"));
            return true;
        }
        executeFullClear(target, sender, true);
        return true;
    }

    private boolean checkSelfPreconditions(Player p) {
        if (!p.hasPermission("kingclear.use")) {
            p.sendMessage(c("&#FF1744✖ У вас нет прав на использование этой команды!"));
            return false;
        }
        if (isInCombat(p)) {
            playSound(p, "entity.villager.no", 0.9f, 0.9f);
            p.sendMessage(c("&#FF1744⚔ Нельзя очищать инвентарь во время боя (PvP), чтобы не прятать лут от врага!"));
            return false;
        }
        if (cooldownSeconds > 0 && !canClearOthers(p)) {
            Long cd = cooldownUntilMs.get(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (cd != null && cd.longValue() > now) {
                long left = Math.max(1L, (cd.longValue() - now + 999L) / 1000L);
                p.sendMessage(c("&#FFD740⏱ Подождите &f" + left + " сек. &#FFD740перед повторной очисткой!"));
                return false;
            }
            cooldownUntilMs.put(p.getUniqueId(), Long.valueOf(now + cooldownSeconds * 1000L));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<String>(Arrays.asList("inv", "trash", "hand", "armor", "gui", "undo", "help"));
            if (canClearOthers(sender)) {
                for (Player on : Bukkit.getOnlinePlayers()) {
                    opts.add(on.getName());
                }
            }
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String o : opts) {
                if (o.toLowerCase(Locale.ROOT).startsWith(pref)) out.add(o);
            }
            return out;
        }
        return Collections.emptyList();
    }

    // ================= LISTENERS =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ClearGuiHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        if (slot == 10) {
            p.closeInventory();
            if (checkSelfPreconditions(p)) executeFullClear(p, p, true);
        } else if (slot == 12) {
            p.closeInventory();
            if (checkSelfPreconditions(p)) executeFullClear(p, p, false);
        } else if (slot == 14) {
            p.closeInventory();
            if (checkSelfPreconditions(p)) executeTrashClear(p);
        } else if (slot == 16) {
            p.closeInventory();
            if (checkSelfPreconditions(p)) executeHandClear(p);
        } else if (slot == 22) {
            UndoSnapshot snap = undoMap.get(p.getUniqueId());
            p.closeInventory();
            if (snap != null && System.currentTimeMillis() <= snap.expiresAtMs) {
                executeUndo(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile && ((Projectile) e.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) e.getDamager()).getShooter();
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        long until = System.currentTimeMillis() + (pvpTagSeconds * 1000L);
        combatUntilMs.put(victim.getUniqueId(), Long.valueOf(until));
        combatUntilMs.put(attacker.getUniqueId(), Long.valueOf(until));
        // Entering PvP immediately invalidates any cached undo snapshot so it can never be abused as extra storage!
        undoMap.remove(victim.getUniqueId());
        undoMap.remove(attacker.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRussianAliasAndVanillaIntercept(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.equals("/очистить") || low.startsWith("/очистить ")) {
            String rest = raw.length() > 9 ? raw.substring(9) : "";
            e.setMessage("/kingclear:clear" + rest);
        } else if (low.equals("/clear") || low.startsWith("/clear ")) {
            // Guarantee our plugin handles /clear instead of vanilla minecraft:clear!
            String rest = raw.length() > 6 ? raw.substring(6) : "";
            e.setMessage("/kingclear:clear" + rest);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        undoMap.remove(e.getEntity().getUniqueId());
        combatUntilMs.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        undoMap.remove(u);
        combatUntilMs.remove(u);
        cooldownUntilMs.remove(u);
    }
}
