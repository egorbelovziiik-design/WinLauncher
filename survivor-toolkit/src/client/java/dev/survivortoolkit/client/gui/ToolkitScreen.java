package dev.survivortoolkit.client.gui;

import dev.survivortoolkit.auction.PriceCalculator;
import dev.survivortoolkit.client.SurvivorToolkitClient;
import dev.survivortoolkit.client.WorldPolicy;
import dev.survivortoolkit.config.ToolkitConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Responsive, scrollable settings screen using native widgets and Minecraft's font. */
public final class ToolkitScreen extends Screen {
    private static final String P = "survivor_toolkit.";
    private enum Tab { OVERVIEW, WARDEN, AUCTION, CONTROLS, FREECAM, SETTINGS, ABOUT }
    private record Line(OrderedText text, int y, int color) {}
    private final List<Line> lines = new ArrayList<>();
    private Tab tab = Tab.OVERVIEW;
    private int px, py, pw, ph, sidebar, contentX, contentW, bodyTop, bodyBottom, cursor, scroll, maxScroll;
    private int rowHeight, gap;
    private long transitionStarted = System.currentTimeMillis();
    private String item = "", total = "", count = "1", reference = "0", result = "", error = "";
    private boolean bargain;
    private boolean awaitingOpeningKeyRelease;

    public ToolkitScreen() { this(false); }

    public ToolkitScreen(boolean openingKeyHeld) {
        super(Text.literal("Survivor Toolkit"));
        awaitingOpeningKeyRelease = openingKeyHeld;
        reference = SurvivorToolkitClient.config().priceReference;
    }

    @Override protected void init() {
        double scale = SurvivorToolkitClient.config().uiScale;
        pw = Math.max(280, Math.min(width - 12, (int) (760 * scale)));
        ph = Math.max(200, Math.min(height - 12, (int) (440 * scale)));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sidebar = Math.max(76, Math.min((int) (132 * scale), pw / 3));
        contentX = px + sidebar + 16;
        contentW = Math.max(80, pw - sidebar - 28);
        bodyTop = py + 51;
        bodyBottom = py + ph - 18;
        rowHeight = Math.max(18, Math.min(25, (int) Math.round(21 * scale)));
        gap = Math.max(4, (int) Math.round(6 * scale));
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        lines.clear();
        int tabY = py + 47;
        int tabHeight = Math.max(17, Math.min(22, (bodyBottom - tabY - 2) / 7 - 2));
        for (Tab value : Tab.values()) {
            Text label = Text.translatable(P + "tab." + value.name().toLowerCase());
            int w = sidebar - 14;
            ButtonWidget widget = ButtonWidget.builder(shortText(label, w - 12), b -> {
                tab = value; scroll = 0; error = ""; transitionStarted = System.currentTimeMillis(); rebuild();
            }).dimensions(px + 7, tabY, w, tabHeight).tooltip(Tooltip.of(label)).build();
            widget.active = value != tab;
            addDrawableChild(widget);
            tabY += tabHeight + 2;
        }
        cursor = bodyTop + 5 - scroll;
        switch (tab) {
            case OVERVIEW -> overview();
            case WARDEN -> warden();
            case AUCTION -> auction();
            case CONTROLS -> controls();
            case FREECAM -> freecam();
            case SETTINGS -> settings();
            case ABOUT -> about();
        }
        maxScroll = Math.max(0, cursor + scroll - bodyBottom + 3);
        if (scroll > maxScroll) { scroll = maxScroll; rebuild(); }
    }

    private ToolkitConfig cfg() { return SurvivorToolkitClient.config(); }
    private void changed() { SurvivorToolkitClient.STORE.save(); rebuild(); }
    private static Text tr(String key, Object... args) { return Text.translatable(P + key, args); }
    private Text shortText(Text text, int maxWidth) {
        String s = text.getString();
        if (textRenderer.getWidth(s) <= maxWidth) return text;
        return Text.literal(textRenderer.trimToWidth(s, Math.max(1, maxWidth - textRenderer.getWidth("…"))) + "…");
    }
    private void label(Text text, int color) {
        for (OrderedText line : textRenderer.wrapLines(text, contentW - 4)) {
            if (cursor >= bodyTop && cursor + 10 <= bodyBottom) lines.add(new Line(line, cursor, color));
            cursor += 11;
        }
        cursor += gap;
    }
    private void label(String key) { label(tr(key), 0xFFADBAB8); }
    private void heading(String key) { label(tr(key), 0xFFE7F4F0); }
    private void button(Text message, String tooltipKey, Runnable action) {
        if (cursor >= bodyTop && cursor + rowHeight <= bodyBottom) {
            ButtonWidget.Builder builder = ButtonWidget.builder(shortText(message, contentW - 15), b -> action.run())
                    .dimensions(contentX, cursor, contentW, rowHeight).tooltip(Tooltip.of(tr(tooltipKey)));
            addDrawableChild(builder.build());
        }
        cursor += rowHeight + gap;
    }
    private void toggle(String key, boolean value, Runnable action, String tip) {
        button(tr(key, tr(value ? "on" : "off")), tip, action);
    }
    private void field(String labelKey, String initial, Consumer<String> onChange) {
        label(tr(labelKey), 0xFFE7F4F0);
        if (cursor >= bodyTop && cursor + rowHeight <= bodyBottom) {
            TextFieldWidget field = new TextFieldWidget(textRenderer, contentX, cursor, contentW, rowHeight, tr(labelKey));
            field.setMaxLength(64);
            field.setText(initial);
            field.setChangedListener(onChange);
            field.setTooltip(Tooltip.of(tr("auction.input.tip")));
            addDrawableChild(field);
        }
        cursor += rowHeight + gap;
    }

    private void overview() {
        heading("overview.title");
        label("overview.description");
        label("overview.privacy");
        label("overview.rules");
        button(tr("overview.open_controls"), "controls.tip", () -> { tab = Tab.CONTROLS; scroll = 0; rebuild(); });
    }
    private void warden() {
        heading("warden.title");
        label("warden.description");
        toggle("warden.enabled", cfg().wardenEnabled, () -> { cfg().wardenEnabled = !cfg().wardenEnabled; changed(); }, "warden.tip");
        int[] radii = {8, 16, 24, 32, 48};
        button(tr("warden.radius", cfg().wardenRadius), "warden.radius.tip", () -> { cfg().wardenRadius = next(cfg().wardenRadius, radii); changed(); });
        serverConsent(true);
    }
    private void auction() {
        heading("auction.title");
        label("auction.description");
        toggle("auction.enabled", cfg().auctionEnabled, () -> { cfg().auctionEnabled = !cfg().auctionEnabled; changed(); }, "auction.tip");
        serverConsent(false);
        if (!cfg().auctionEnabled || !WorldPolicy.allowed(client, cfg().auctionAllowedServers)) { label("auction.locked"); return; }
        field("auction.item", item, s -> item = s);
        field("auction.total", total, s -> total = s);
        field("auction.count", count, s -> count = s);
        field("auction.reference", reference, s -> reference = s);
        button(tr("auction.calculate"), "auction.calculate.tip", this::calculate);
        if (!error.isEmpty()) label(tr(error), 0xFFFFA899);
        if (!result.isEmpty()) label(Text.literal(result), bargain ? 0xFF83E5AD : 0xFFE7F4F0);
        heading("auction.history");
        if (cfg().priceHistory.isEmpty()) label("auction.history.empty");
        for (int i = 0; i < Math.min(5, cfg().priceHistory.size()); i++) {
            var entry = cfg().priceHistory.get(i);
            label(Text.literal(entry.item + " · " + entry.total + " / " + entry.count + " = " + entry.unit), 0xFFADBAB8);
        }
        if (!cfg().priceHistory.isEmpty()) button(tr("auction.clear"), "auction.clear.tip", () -> { cfg().priceHistory.clear(); changed(); });
    }
    private void calculate() {
        try {
            var quote = PriceCalculator.calculate(total, count, reference);
            cfg().priceReference = PriceCalculator.price(reference).toPlainString();
            String unit = PriceCalculator.format(quote.unit());
            String stack = PriceCalculator.format(quote.stack());
            result = tr("auction.result", PriceCalculator.format(quote.total()), unit, stack).getString();
            bargain = quote.atOrBelowReference();
            if (bargain) result += " · " + tr("auction.match").getString();
            String safeItem = item.strip();
            if (safeItem.isEmpty()) safeItem = tr("auction.unnamed").getString();
            cfg().priceHistory.add(0, new ToolkitConfig.PriceRecord(safeItem, quote.total().toPlainString(), quote.count(), unit, System.currentTimeMillis()));
            while (cfg().priceHistory.size() > 50) cfg().priceHistory.remove(cfg().priceHistory.size() - 1);
            error = "";
            changed();
        } catch (IllegalArgumentException e) { result = ""; error = "auction.error"; rebuild(); }
    }
    private void controls() {
        heading("controls.title");
        label(tr("controls.menu_key", SurvivorToolkitClient.MENU_KEY.getBoundKeyLocalizedText()), 0xFFADBAB8);
        label(tr("controls.camera_key", SurvivorToolkitClient.FREECAM_KEY.getBoundKeyLocalizedText()), 0xFFADBAB8);
        label("controls.description");
        button(tr("controls.rebind"), "controls.tip", () -> client.setScreen(new KeybindsScreen(this, client.options)));
        toggle("build.enabled", cfg().buildAssistEnabled, () -> { cfg().buildAssistEnabled = !cfg().buildAssistEnabled; changed(); }, "build.tip");
        toggle("build.selected", cfg().showSelectedBlock, () -> { cfg().showSelectedBlock = !cfg().showSelectedBlock; changed(); }, "build.tip");
        label("build.description");
    }
    private void freecam() {
        heading("freecam.title");
        label("freecam.description");
        label("freecam.limit");
        button(tr("freecam.toggle"), "freecam.tip", () -> { close(); SurvivorToolkitClient.FREECAM.toggle(client); });
        double[] speeds = {0.1, 0.25, 0.5, 0.8};
        button(tr("freecam.speed", cfg().freecamSpeed), "freecam.tip", () -> { cfg().freecamSpeed = next(cfg().freecamSpeed, speeds); changed(); });
        double[] sensitivities = {0.5, 1.0, 1.5, 2.0};
        button(tr("freecam.sensitivity", cfg().freecamSensitivity), "freecam.tip", () -> { cfg().freecamSensitivity = next(cfg().freecamSensitivity, sensitivities); changed(); });
    }
    private void settings() {
        heading("settings.title");
        double[] scales = {0.8, 1.0, 1.1, 1.25};
        button(tr("settings.scale", (int) Math.round(cfg().uiScale * 100)), "settings.scale.tip", () -> { cfg().uiScale = next(cfg().uiScale, scales); SurvivorToolkitClient.STORE.save(); init(); });
        toggle("settings.sound", cfg().warningSoundEnabled, () -> { cfg().warningSoundEnabled = !cfg().warningSoundEnabled; changed(); }, "settings.sound.tip");
        button(tr("settings.sound_type", cfg().warningSound), "settings.sound.tip", () -> {
            cfg().warningSound = switch (cfg().warningSound) { case "pling" -> "bell"; case "bell" -> "none"; default -> "pling"; }; changed();
        });
        double[] volumes = {0, 0.3, 0.7, 1};
        button(tr("settings.volume", (int) (cfg().warningVolume * 100)), "settings.sound.tip", () -> { cfg().warningVolume = next(cfg().warningVolume, volumes); changed(); });
        int[] durations = {1, 2, 4, 8, 15};
        button(tr("settings.duration", cfg().warningSeconds), "settings.duration.tip", () -> { cfg().warningSeconds = next(cfg().warningSeconds, durations); changed(); });
        int[] colors = {0xF3A94F, 0xFF7777, 0x8CDAC3, 0xFFFFFF};
        button(tr("settings.color", String.format("#%06X", cfg().warningColor)), "settings.color.tip", () -> { cfg().warningColor = next(cfg().warningColor, colors); changed(); });
        label("settings.config_location");
    }
    private void about() {
        heading("about.title");
        label("about.version"); label("about.license"); label("about.safety"); label("about.server");
    }

    private void serverConsent(boolean warden) {
        if (client.world == null || WorldPolicy.privateLocal(client)) return;
        String address = WorldPolicy.serverAddress(client);
        Set<String> permissions = warden ? cfg().wardenAllowedServers : cfg().auctionAllowedServers;
        if (address.isEmpty()) { label("server.unknown"); return; }
        boolean allowed = permissions.contains(address);
        button(tr(allowed ? "server.revoke" : "server.allow"), "server.tip", () -> {
            if (allowed) { permissions.remove(address); changed(); return; }
            String feature = tr(warden ? "tab.warden" : "tab.auction").getString();
            client.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed && client.world != null && !WorldPolicy.privateLocal(client)
                        && address.equals(WorldPolicy.serverAddress(client))) {
                    permissions.add(address); SurvivorToolkitClient.STORE.save();
                }
                client.setScreen(this);
            }, tr("server.confirm.title"), tr("server.confirm.body", feature, address), tr("server.confirm.yes"), tr("server.confirm.no")));
        });
        label(tr("server.status", address, tr(allowed ? "on" : "off")), 0xFF91A6A1);
    }

    private static int next(int current, int[] values) { for (int v : values) if (v > current) return v; return values[0]; }
    private static double next(double current, double[] values) { for (double v : values) if (v > current + 0.00001) return v; return values[0]; }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB00A1013);
        context.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF344540);
        context.fill(px, py, px + pw, py + ph, 0xF5161E21);
        context.fill(px, py, px + sidebar, py + ph, 0xFF11191C);
        context.fill(px, py, px + pw, py + 32, 0xFF1B292B);
        float anim = Math.min(1f, (System.currentTimeMillis() - transitionStarted) / 220f);
        context.fill(px + 1, py + 31, px + 1 + (int) ((pw - 2) * anim), py + 33, 0xFF78D7B1);
        context.drawTextWithShadow(textRenderer, "SURVIVOR", px + 10, py + 7, 0xFFE8F7F1);
        context.drawTextWithShadow(textRenderer, "TOOLKIT", px + 10, py + 18, 0xFF83D3B4);
        context.drawTextWithShadow(textRenderer, shortText(tr("tab." + tab.name().toLowerCase()), contentW).getString(), contentX, py + 39, 0xFFE8F7F1);
        context.fill(contentX - 5, bodyTop, contentX - 3, bodyBottom, 0xFF2A403A);
        for (Line line : lines) context.drawTextWithShadow(textRenderer, line.text(), contentX, line.y(), line.color());
        if (maxScroll > 0) {
            int track = bodyBottom - bodyTop, thumb = Math.max(12, track * track / (track + maxScroll));
            int top = bodyTop + (track - thumb) * scroll / maxScroll;
            context.fill(px + pw - 6, bodyTop, px + pw - 5, bodyBottom, 0xFF33423F);
            context.fill(px + pw - 7, top, px + pw - 4, top + thumb, 0xFF72C8A8);
        }
        if (!SurvivorToolkitClient.STORE.warning().isEmpty()) {
            String msg = tr("config." + SurvivorToolkitClient.STORE.warning()).getString();
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(msg, contentW), contentX, py + ph - 13, 0xFFFFA799);
        }
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { /* custom overlay */ }
    @Override public boolean shouldPause() { return false; }
    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (SurvivorToolkitClient.MENU_KEY.matchesKey(input)) {
            if (!awaitingOpeningKeyRelease) close();
            return true;
        }
        return super.keyPressed(input);
    }
    @Override public boolean keyReleased(KeyInput input) {
        if (SurvivorToolkitClient.MENU_KEY.matchesKey(input)) awaitingOpeningKeyRelease = false;
        return super.keyReleased(input);
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (SurvivorToolkitClient.MENU_KEY.matchesMouse(click)) {
            if (!awaitingOpeningKeyRelease) close();
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(Click click) {
        if (SurvivorToolkitClient.MENU_KEY.matchesMouse(click)) awaitingOpeningKeyRelease = false;
        return super.mouseReleased(click);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= contentX && mouseX <= px + pw && mouseY >= bodyTop && mouseY <= bodyBottom && maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 36));
            rebuild(); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override public void removed() { SurvivorToolkitClient.STORE.save(); super.removed(); }
}
