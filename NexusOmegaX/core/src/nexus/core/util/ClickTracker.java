package nexus.core.util;

import nexus.core.Nexus;
import nexus.core.event.EventBus;
import nexus.core.event.Listener;
import nexus.core.event.MouseEvent;

/** Click per second tracking for the CPS HUD element and combat modules. */
public final class ClickTracker {

    private static final int WINDOW_MS = 1000;
    private static final long[] LEFT = new long[64];
    private static final long[] RIGHT = new long[64];
    private static int leftIndex;
    private static int rightIndex;
    private static long totalClicks;
    private static boolean installed;

    private ClickTracker() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        EventBus bus = Nexus.eventBus();
        bus.subscribe(MouseEvent.class, new Listener<MouseEvent>() {
            @Override
            public void on(MouseEvent event) {
                if (event.action == MouseEvent.Action.PRESS) {
                    register(event.button);
                }
            }
        }, 100, ClickTracker.class);
    }

    public static void register(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) {
            LEFT[leftIndex++ % LEFT.length] = now;
            totalClicks++;
        } else if (button == 1) {
            RIGHT[rightIndex++ % RIGHT.length] = now;
            totalClicks++;
        }
    }

    private static int count(long[] buffer) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long time : buffer) {
            if (time > 0L && now - time <= WINDOW_MS) {
                count++;
            }
        }
        return count;
    }

    public static int leftCps() {
        return count(LEFT);
    }

    public static int rightCps() {
        return count(RIGHT);
    }

    public static int cps() {
        return leftCps() + rightCps();
    }

    public static long totalClicks() {
        return totalClicks;
    }

    public static void reset() {
        for (int i = 0; i < LEFT.length; i++) {
            LEFT[i] = 0L;
            RIGHT[i] = 0L;
        }
        totalClicks = 0;
    }
}
