package ru.reallyworld.ultimate.core.module;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.Map;

public final class LocalizationModule implements ReallyModule {
    private final ConfigManager configManager;
    private volatile boolean enabled;

    public LocalizationModule(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String id() { return "localization"; }

    @Override
    public String displayName() { return "LocalizationModule (RU MiniMessage)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.localization", true);
    }

    @Override
    public void reload() {
        enable();
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    public String raw(String key) {
        YamlConfiguration msg = configManager.get("messages_ru.yml");
        return msg.getString(key, "<red>Сообщение не найдено: " + key);
    }

    public String prefix() {
        return configManager.get("messages_ru.yml").getString("prefix", "<bold><#FBBC00>RW</bold> <dark_gray>»</dark_gray> <gray>");
    }

    public Component component(String key, Map<String, String> placeholders) {
        return TextUtil.mm(prefix() + raw(key), placeholders);
    }

    public Component componentNoPrefix(String key, Map<String, String> placeholders) {
        return TextUtil.mm(raw(key), placeholders);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender != null) {
            sender.sendMessage(component(key, placeholders));
        }
    }

    public void sendRaw(CommandSender sender, String miniMessage) {
        if (sender != null) {
            sender.sendMessage(TextUtil.mm(prefix() + miniMessage));
        }
    }
}
