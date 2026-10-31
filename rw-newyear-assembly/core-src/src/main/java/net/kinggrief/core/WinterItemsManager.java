package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 100 original runes and 50 distinct new self-only, single-use artifacts.
 * No block or other player's territory is modified. */
public final class WinterItemsManager implements Listener, CommandExecutor, TabCompleter {
    private static final int[] DURATIONS = {15, 25, 35, 45, 60};
    private static final int[] PRICES = {18, 30, 42, 58, 75};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};
    private static final String[] ORBS = {"shar_titana", "shar_andromedy", "shar_chimery", "shar_apollona",
            "shar_aida", "shar_osirisa", "shar_aresa", "shar_santy", "shar_kronosa", "shar_geliosa",
            "shar_morozko", "shar_kvadrat"};

    private static final class Effect {
        final String key, legacy, label;
        final boolean scales;
        Effect(String key, String legacy, String label, boolean scales) {
            this.key = key; this.legacy = legacy; this.label = label; this.scales = scales;
        }
    }
    private static final Effect SPEED = new Effect("speed", "SPEED", "Скорость", true);
    private static final Effect JUMP = new Effect("jump_boost", "JUMP", "Прыгучесть", true);
    private static final Effect HASTE = new Effect("haste", "FAST_DIGGING", "Спешка", true);
    private static final Effect NIGHT = new Effect("night_vision", "NIGHT_VISION", "Ночное зрение", false);
    private static final Effect RESIST = new Effect("resistance", "DAMAGE_RESISTANCE", "Сопротивление", true);
    private static final Effect REGEN = new Effect("regeneration", "REGENERATION", "Регенерация", true);
    private static final Effect STRENGTH = new Effect("strength", "INCREASE_DAMAGE", "Сила", true);
    private static final Effect FIRE = new Effect("fire_resistance", "FIRE_RESISTANCE", "Огнестойкость", false);
    private static final Effect WATER = new Effect("water_breathing", "WATER_BREATHING", "Подводное дыхание", false);
    private static final Effect DOLPHIN = new Effect("dolphins_grace", "DOLPHINS_GRACE", "Грация дельфина", false);
    private static final Effect SLOW_FALL = new Effect("slow_falling", "SLOW_FALLING", "Медленное падение", false);
    private static final Effect LUCK = new Effect("luck", "LUCK", "Удача", true);
    private static final Effect ABSORB = new Effect("absorption", "ABSORPTION", "Поглощение", true);

    private static final class Family {
        final String title;
        final Effect first, second;
        final int utility;
        Family(String title, Effect first, Effect second) { this(title, first, second, -1); }
        Family(String title, Effect first, Effect second, int utility) {
            this.title = title; this.first = first; this.second = second; this.utility = utility;
        }
    }
    private static final Family[] FAMILIES = {
            new Family("Ветер", SPEED, JUMP),
            new Family("Шахтёр", HASTE, NIGHT),
            new Family("Страж", RESIST, REGEN),
            new Family("Воин", STRENGTH, RESIST),
            new Family("Пламя", FIRE, SPEED),
            new Family("Дайвер", WATER, DOLPHIN),
            new Family("Перо", SLOW_FALL, JUMP),
            new Family("Искатель", LUCK, NIGHT),
            new Family("Эгида", ABSORB, RESIST),
            new Family("Берсерк", STRENGTH, SPEED),
            new Family("Звездопад", SPEED, NIGHT),
            new Family("Горн", FIRE, HASTE),
            new Family("Алхимик", REGEN, FIRE),
            new Family("Охотник", STRENGTH, NIGHT),
            new Family("Странник", SPEED, SLOW_FALL),
            new Family("Разведчик", JUMP, NIGHT),
            new Family("Мореплаватель", WATER, SPEED),
            new Family("Бастион", ABSORB, FIRE),
            new Family("Чародей", LUCK, HASTE),
            new Family("Зимний рассвет", REGEN, SPEED),
    };
    private static final Family[] EXTRA_FAMILIES = {
            // Five distinct combinations with the same immediate utility.
            new Family("Пульс жизни", REGEN, ABSORB, 0),
            new Family("Сердце тайги", REGEN, LUCK, 0),
            new Family("Кристалл лекаря", REGEN, STRENGTH, 0),
            new Family("Живой янтарь", RESIST, LUCK, 0),
            new Family("Маяк спасения", SPEED, ABSORB, 0),
            // Five distinct combinations with the same immediate utility.
            new Family("Сумка следопыта", SPEED, LUCK, 1),
            new Family("Зимний провиант", JUMP, LUCK, 1),
            new Family("Искра сытости", JUMP, REGEN, 1),
            new Family("Дар путешественника", JUMP, ABSORB, 1),
            new Family("Запас экспедиции", SPEED, RESIST, 1),
            // Five distinct combinations with the same immediate utility.
            new Family("Противоядие леса", REGEN, WATER, 2),
            new Family("Чистая роса", REGEN, DOLPHIN, 2),
            new Family("Оберег травника", REGEN, SLOW_FALL, 2),
            new Family("Снежный антидот", HASTE, REGEN, 2),
            new Family("Сердце можжевельника", NIGHT, REGEN, 2),
            // Five distinct combinations with the same immediate utility.
            new Family("Ясная душа", RESIST, FIRE, 3),
            new Family("Защита рассвета", RESIST, WATER, 3),
            new Family("Щит от иссушения", RESIST, DOLPHIN, 3),
            new Family("Надежда хранителя", RESIST, SLOW_FALL, 3),
            new Family("Последний луч", NIGHT, ABSORB, 3),
            // Five distinct combinations with the same immediate utility.
            new Family("Пепел феникса", FIRE, WATER, 4),
            new Family("Плащ искр", FIRE, DOLPHIN, 4),
            new Family("Сердце вулкана", FIRE, SLOW_FALL, 4),
            new Family("Морозный огнеборец", FIRE, LUCK, 4),
            new Family("Печать феникса", STRENGTH, FIRE, 4),
            // Five distinct combinations with the same immediate utility.
            new Family("Жемчужина дыхания", WATER, SLOW_FALL, 5),
            new Family("Аквамарин глубин", WATER, LUCK, 5),
            new Family("Ракушка странника", WATER, ABSORB, 5),
            new Family("Дыхание океана", NIGHT, WATER, 5),
            new Family("Сфера водолаза", HASTE, WATER, 5),
            // Five distinct combinations with the same immediate utility.
            new Family("Лёгкий шаг", SPEED, HASTE, 6),
            new Family("Крыло метели", SPEED, DOLPHIN, 6),
            new Family("Перо путешественника", JUMP, HASTE, 6),
            new Family("Стремительный бег", JUMP, RESIST, 6),
            new Family("Свобода движения", JUMP, FIRE, 6),
            // Five distinct combinations with the same immediate utility.
            new Family("Решимость", JUMP, STRENGTH, 7),
            new Family("Лезвие духа", HASTE, STRENGTH, 7),
            new Family("Искра храбрости", STRENGTH, WATER, 7),
            new Family("Сила бойца", STRENGTH, DOLPHIN, 7),
            new Family("Знамя стойкости", STRENGTH, SLOW_FALL, 7),
            // Five distinct combinations with the same immediate utility.
            new Family("Инструмент мастера", HASTE, RESIST, 8),
            new Family("Энергия шахтёра", HASTE, DOLPHIN, 8),
            new Family("Ключ забоя", HASTE, SLOW_FALL, 8),
            new Family("Молот упорства", HASTE, ABSORB, 8),
            new Family("Горный импульс", JUMP, WATER, 8),
            // Five distinct combinations with the same immediate utility.
            new Family("Луч зрения", NIGHT, RESIST, 9),
            new Family("Глаз путника", NIGHT, FIRE, 9),
            new Family("Фонарь полярника", NIGHT, DOLPHIN, 9),
            new Family("Звезда ориентирования", NIGHT, SLOW_FALL, 9),
            new Family("Свет маяка", DOLPHIN, SLOW_FALL, 9),
    };
    private static final String[] UTILITY_LABELS = {
            "Мгновенно восстанавливает здоровье", "Восстанавливает сытость",
            "Снимает отравление", "Снимает иссушение", "Тушит огонь",
            "Восстанавливает воздух", "Снимает замедление", "Снимает слабость",
            "Снимает усталость от копания", "Снимает слепоту"
    };
    private static final int PAGE_SIZE = 28;
    private static final class CatalogHolder implements InventoryHolder {
        final int page;
        CatalogHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }
    private final KingGriefCore plugin;
    private final Map<UUID, Long> runeReady = new HashMap<UUID, Long>();
    private final Map<UUID, Long> orbReady = new HashMap<UUID, Long>();

    WinterItemsManager(KingGriefCore plugin) {
        this.plugin = plugin;
        // Entries live past logout to prevent a relog bypass, but do not grow forever.
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                runeReady.values().removeIf(value -> value.longValue() <= now);
                orbReady.values().removeIf(value -> value.longValue() <= now);
            }
        }, 1200L, 1200L);
    }

    public static int itemCount() { return FAMILIES.length * DURATIONS.length + EXTRA_FAMILIES.length; }
    private static int pageCount() { return (itemCount() + PAGE_SIZE - 1) / PAGE_SIZE; }
    private static Family familyFor(int number) {
        return number <= 100 ? FAMILIES[(number - 1) / 5] : EXTRA_FAMILIES[number - 101];
    }
    private static int tierFor(int number) { return (number - 1) % 5; }
    public static String idFor(int number) { return String.format(Locale.ROOT, "winter_rune_%03d", number); }
    private static int numberFor(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) return -1;
        String id = ItemFactory.getCustomId(item);
        if (id == null || !id.matches("winter_rune_[0-9]{3}")) return -1;
        int number = Integer.parseInt(id.substring("winter_rune_".length()));
        return number >= 1 && number <= itemCount() ? number : -1;
    }
    private static int amplifier(Effect effect, int tier) { return effect.scales && tier >= 3 ? 1 : 0; }
    private static String effectLabel(Effect effect, int tier) {
        return effect.label + (effect.scales ? (amplifier(effect, tier) == 1 ? " II" : " I") : "");
    }
    public static int price(int number) {
        if (number < 1 || number > itemCount()) throw new IllegalArgumentException("Invalid rune");
        return PRICES[tierFor(number)] + ((number - 1) / 5 % 4) * 3 + (number > 100 ? 15 : 0);
    }
    public static ItemStack makeRune(int number, int amount) {
        if (number < 1 || number > itemCount()) throw new IllegalArgumentException("Invalid rune");
        int tier = tierFor(number);
        Family f = familyFor(number);
        String id = idFor(number);
        List<String> lore = new ArrayList<String>();
        lore.add("&8Зимняя коллекция • №" + number + "/" + itemCount());
        lore.add("");
        lore.add("&b• " + effectLabel(f.first, tier));
        lore.add("&b• " + effectLabel(f.second, tier));
        if (f.utility >= 0) lore.add("&d• " + UTILITY_LABELS[f.utility]
                + (f.utility <= 1 ? " (+" + (tier + 2) + ")" : ""));
        lore.add("&7Длительность эффектов: &f" + DURATIONS[tier] + " сек.");
        lore.add("&7ПКМ в основной руке: расходует 1 предмет.");
        lore.add("&7Общий КТ рун: &f20 сек. &8(до перезапуска сервера)");
        return ItemFactory.make(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, amount)),
                "&#8BE5FF&l✦ " + (number <= 100 ? "Руна" : "Артефакт") + " «" + f.title + "» " + ROMAN[tier],
                lore, false, id, id, 3000 + number);
    }
    private static int slotForIndex(int index) { return 10 + (index / 7) * 9 + index % 7; }
    private static int indexForSlot(int slot) {
        int row = slot / 9, col = slot % 9;
        return row >= 1 && row <= 4 && col >= 1 && col <= 7 ? (row - 1) * 7 + col - 1 : -1;
    }
    public void open(Player player, int page) {
        int pg = Math.max(0, Math.min(pageCount() - 1, page));
        Inventory inv = Bukkit.createInventory(new CatalogHolder(pg), 54,
                ColorUtil.c("&#80D8FF&l✦ АРТЕФАКТЫ &7[" + (pg + 1) + "/" + pageCount() + "]"));
        ItemStack frame = ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, frame);
        inv.setItem(4, ItemFactory.glowIcon(Material.AMETHYST_SHARD, "&#80D8FF&l150 зимних артефактов",
                "&7100 рун и 50 новых предметов с дополнительными действиями.",
                "&7Баланс: &b" + plugin.getStore().getRelics(player) + " ❖"));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int number = pg * PAGE_SIZE + i + 1;
            if (number > itemCount()) { inv.setItem(slotForIndex(i), null); continue; }
            ItemStack icon = makeRune(number, 1);
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            List<String> lore = new ArrayList<String>(meta.getLore());
            lore.add(""); lore.add(ColorUtil.c("&eЦена: &b" + price(number) + " ❖ &7• Нажмите для покупки"));
            meta.setLore(lore); icon.setItemMeta(meta);
            inv.setItem(slotForIndex(i), icon);
        }
        if (pg > 0) inv.setItem(45, ItemFactory.icon(Material.ARROW, "&e« Предыдущая"));
        inv.setItem(49, ItemFactory.icon(Material.BARRIER, "&7Закрыть"));
        if (pg + 1 < pageCount()) inv.setItem(53, ItemFactory.icon(Material.ARROW, "&eСледующая »"));
        player.openInventory(inv);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCatalogClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof CatalogHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;
        Player p = (Player) e.getWhoClicked();
        if (!plugin.getStore().isAuthenticated(p) || plugin.getModeration().isFrozenOrAscending(p)) return;
        int page = ((CatalogHolder) top.getHolder()).page, slot = e.getRawSlot();
        if (slot == 45 && page > 0) { open(p, page - 1); return; }
        if (slot == 53 && page + 1 < pageCount()) { open(p, page + 1); return; }
        if (slot == 49) { p.closeInventory(); return; }
        int index = indexForSlot(slot), number = page * PAGE_SIZE + index + 1;
        if (index < 0 || number > itemCount()) return;
        // One click = exactly one fresh server-created item. Never trust the GUI item's PDC/price.
        if (!InventoryCapacity.canFit(p, makeRune(number, 1))) {
            p.sendMessage(ColorUtil.c("&cОсвободите место в инвентаре.")); return;
        }
        int cost = price(number);
        if (!plugin.getStore().takeRelics(p, cost)) {
            if (plugin.getStore().getRelics(p) < cost)
                p.sendMessage(ColorUtil.c("&cНедостаточно риликов: нужно &b" + cost + " ❖&c."));
            else
                p.sendMessage(ColorUtil.c("&cНе удалось подтвердить списание риликов. Предмет не выдан; обратитесь к администрации."));
            return;
        }
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(makeRune(number, 1));
        if (!leftovers.isEmpty()) {
            plugin.getStore().addRelics(p.getName(), cost);
            p.sendMessage(ColorUtil.c("&cНе удалось выдать руну; рилики возвращены."));
        } else {
            p.sendMessage(ColorUtil.c("&aКуплена руна №" + number + " за &b" + cost + " ❖&a."));
            ColorUtil.sound(p, "entity.experience_orb.pickup", 0.8f, 1.2f);
        }
        plugin.getStore().save();
        open(p, page);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCatalogDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof CatalogHolder) e.setCancelled(true);
    }
    private boolean allowed(Player p) {
        return plugin.getStore().isAuthenticated(p) && !plugin.getModeration().isFrozenOrAscending(p)
                && !plugin.getStore().isQuickFrozen(p) && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }
    private static boolean giveEffect(Player p, Effect e, int seconds, int amplifier) {
        PotionEffectType type = ColorUtil.potType(e.key, e.legacy);
        if (type == null) return false;
        PotionEffect existing = p.getPotionEffect(type);
        if (existing != null && (existing.getAmplifier() > amplifier ||
                (existing.getAmplifier() == amplifier && (existing.getDuration() < 0 || existing.getDuration() >= seconds * 20)))) return true;
        // Paper 1.21.4's addPotionEffect() returns true even when a listener
        // cancels EntityPotionEffectEvent. Compare actual state instead.
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, false, true));
        PotionEffect actual = p.getPotionEffect(type);
        return actual != null && (existing == null || actual.getAmplifier() != existing.getAmplifier()
                || actual.getDuration() != existing.getDuration());
    }
    /** Immediate effects are self-only. Healing and hunger respect cancellation. */
    private static void applyUtility(Player p, int utility, int tier) {
        if (utility == 0) {
            double max = p.getMaxHealth(), current = p.getHealth();
            double amount = Math.min((double) tier + 2.0, Math.max(0.0, max - current));
            if (amount <= 0.0) return;
            org.bukkit.event.entity.EntityRegainHealthEvent event = new org.bukkit.event.entity.EntityRegainHealthEvent(
                    p, amount, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled() && Double.isFinite(event.getAmount()) && event.getAmount() > 0.0)
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + event.getAmount()));
        } else if (utility == 1) {
            int desired = Math.min(20, p.getFoodLevel() + tier + 2);
            if (desired <= p.getFoodLevel()) return;
            org.bukkit.event.entity.FoodLevelChangeEvent event = new org.bukkit.event.entity.FoodLevelChangeEvent(p, desired);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) p.setFoodLevel(Math.max(0, Math.min(20, event.getFoodLevel())));
        } else if (utility == 4) {
            p.setFireTicks(0);
        } else if (utility == 5) {
            p.setRemainingAir(p.getMaximumAir());
        } else {
            String[] keys = {"", "", "poison", "wither", "", "", "slowness", "weakness", "mining_fatigue", "blindness"};
            String[] legacy = {"", "", "POISON", "WITHER", "", "", "SLOW", "WEAKNESS", "SLOW_DIGGING", "BLINDNESS"};
            PotionEffectType type = ColorUtil.potType(keys[utility], legacy[utility]);
            if (type != null && p.hasPotionEffect(type)) p.removePotionEffect(type);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if ((e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND)
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player p = e.getPlayer();
        // A protection plugin's block-interaction denial is respected; Bukkit's
        // predicted no-op RIGHT_CLICK_AIR may itself start cancelled.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.isCancelled()) return;
        if (!allowed(p)) return;
        ItemStack main = p.getInventory().getItemInMainHand();
        int number = numberFor(main);
        if (number > 0 && e.getHand() == EquipmentSlot.HAND) {
            e.setCancelled(true);
            long now = System.currentTimeMillis(), ready = runeReady.getOrDefault(p.getUniqueId(), Long.valueOf(0)).longValue();
            if (ready > now) { ColorUtil.actionBar(p, "&cРуны: КТ ещё " + ((ready-now+999)/1000) + " сек."); return; }
            int tier = tierFor(number);
            Family f = familyFor(number);
            if (ColorUtil.potType(f.first.key, f.first.legacy) == null || ColorUtil.potType(f.second.key, f.second.legacy) == null) {
                p.sendMessage(ColorUtil.c("&cЭффект этой руны недоступен на версии сервера; предмет сохранён.")); return;
            }
            // Respect existing stronger/longer potions and consume only after validation.
            boolean firstApplied = giveEffect(p, f.first, DURATIONS[tier], amplifier(f.first, tier));
            boolean secondApplied = giveEffect(p, f.second, DURATIONS[tier], amplifier(f.second, tier));
            if (!firstApplied && !secondApplied) {
                p.sendMessage(ColorUtil.c("&cДействие отменено другим плагином; руна сохранена.")); return;
            }
            if (!firstApplied || !secondApplied) p.sendMessage(ColorUtil.c("&eОдин из эффектов отменён другим плагином."));
            if (f.utility >= 0) applyUtility(p, f.utility, tier);
            if (main.getAmount() > 1) main.setAmount(main.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);
            runeReady.put(p.getUniqueId(), Long.valueOf(now + 20_000L));
            ColorUtil.sound(p, "block.amethyst_block.chime", 0.8f, 1.3f);
            ColorUtil.actionBar(p, "&#80D8FF✦ Руна «" + f.title + "» " + ROMAN[tier] + ": " + DURATIONS[tier] + " сек.");
            return;
        }
        // Deliberate empty-main-hand gesture; other items and right-click actions are untouched.
        // Paper receives use-item for the nonempty off hand; an empty main hand
        // alone does not reliably produce a PlayerInteractEvent.
        if (e.getHand() != EquipmentSlot.OFF_HAND || !p.isSneaking()
                || (main != null && main.getType() != Material.AIR)) return;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off == null || off.getType() != Material.TOTEM_OF_UNDYING) return;
        String id = ItemFactory.getCustomId(off);
        int index = Arrays.asList(ORBS).indexOf(id);
        if (index < 0) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis(), ready = orbReady.getOrDefault(p.getUniqueId(), Long.valueOf(0)).longValue();
        if (ready > now) { ColorUtil.actionBar(p, "&cПриём шара: КТ ещё " + ((ready-now+999)/1000) + " сек."); return; }
        Effect[] bursts = {RESIST, SPEED, REGEN, HASTE, FIRE, REGEN, STRENGTH, ABSORB, HASTE, FIRE, RESIST, ABSORB};
        Effect burst = bursts[index];
        int amp = burst.scales ? 1 : 0;
        if (!giveEffect(p, burst, 8, amp)) { p.sendMessage(ColorUtil.c("&cСпособность шара недоступна на этой версии сервера.")); return; }
        orbReady.put(p.getUniqueId(), Long.valueOf(now + 60_000L));
        ColorUtil.sound(p, "block.amethyst_block.chime", 1.0f, 1.0f);
        ColorUtil.actionBar(p, "&#E7A8FF◼ Приём шара: " + effectLabel(burst, amp == 1 ? 3 : 0) + " на 8 сек. • КТ 60 сек.");
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "give".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof ConsoleCommandSender) && (!(sender instanceof Player) || !plugin.getRanks().isOwnerLevel((Player) sender))) {
                sender.sendMessage(ColorUtil.c("&cВыдача доступна только OWNER, TECH.ADMIN и консоли.")); return true;
            }
            if (args.length < 3 || args.length > 4) {
                sender.sendMessage("/artifacts give <ник> <1-150|mega> [1-64]"); return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("Игрок должен быть онлайн."); return true; }
            int amount = args.length == 4 ? ColorUtil.parseInt(args[3], -1) : 1;
            int number = ColorUtil.parseInt(args[2], -1);
            if (amount < 1 || amount > 64 || (!"mega".equalsIgnoreCase(args[2]) && (number < 1 || number > itemCount()))) {
                sender.sendMessage("Укажите номер 1–150 или mega и количество 1–64."); return true;
            }
            ItemStack reward = "mega".equalsIgnoreCase(args[2]) ? ItemFactory.megaGapple(amount) : makeRune(number, amount);
            if (!InventoryCapacity.canFit(target, reward)) { sender.sendMessage("В инвентаре игрока недостаточно места."); return true; }
            Map<Integer, ItemStack> remain = target.getInventory().addItem(reward);
            if (!remain.isEmpty()) { sender.sendMessage("Предмет не поместился полностью."); return true; }
            sender.sendMessage("Предмет выдан: " + target.getName()); return true;
        }
        if (!(sender instanceof Player)) { sender.sendMessage("/artifacts give <ник> <1-150|mega> [1-64]"); return true; }
        Player p = (Player) sender;
        if (!allowed(p)) return true;
        int page = args.length == 0 ? 1 : ColorUtil.parseInt(args[0], 1);
        open(p, page - 1); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Collections.singletonList("1");
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) return Arrays.asList("1", "25", "50", "75", "100", "125", "150", "mega");
        return Collections.emptyList();
    }
}
