package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Counts down to a moment you set and warns you when it is reached. */
public final class CountdownTimerModule extends Module {

    private final NumberSetting minutes;
    private final BooleanSetting notify;
    private final NumberSetting x;
    private final NumberSetting y;
    private long endsAt = -1L;

    public CountdownTimerModule() {
        super("countdowntimer", "CountdownTimer", "Counts down to a moment you set.",
                ModuleCategory.MISC);
        this.minutes = number("Minutes", "Countdown length in minutes", 5.0, 0.1, 240.0, 0.1);
        this.notify = bool("Notify", "Show a notification when it ends", true);
        this.x = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 220.0, 0.0, 1080.0, 1.0);
    }

    public void start() {
        endsAt = System.currentTimeMillis() + (long) (minutes.value() * 60000.0);
    }

    public void cancel() {
        endsAt = -1L;
    }

    public long remainingMs() {
        if (endsAt < 0L) {
            return 0L;
        }
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (endsAt < 0L) {
            return;
        }
        long remaining = remainingMs();
        if (remaining <= 0L) {
            if (notify.isEnabled()) {
                nexus.core.Nexus.instance().notifications().success("CountdownTimer", "Time is up");
            }
            endsAt = -1L;
            markActivity();
            return;
        }
        long seconds = remaining / 1000L;
        String text = String.format("Countdown %02d:%02d", Long.valueOf(seconds / 60L),
                Long.valueOf(seconds % 60L));
        event.renderer.fillRoundRect(x.value() - 3.0, y.value() - 3.0,
                event.renderer.textWidth(text, 1.0) + 6.0, 16.0, 3.0, ColorUtil.withAlpha(0xFF08080D, 170));
        event.renderer.drawText(text, x.value(), y.value(),
                remaining < 10000L ? 0xFFF87171 : 0xFFC084FC, true, 1.0);
    }

    @Override
    public String hudInfo() {
        return endsAt < 0L ? "idle" : (remainingMs() / 1000L) + "s";
    }
}
