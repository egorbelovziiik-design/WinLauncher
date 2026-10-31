package dev.funtime.auctionhelper.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for every automation flag.
 *
 * <p>Invariants enforced here (and covered by unit tests):</p>
 * <ul>
 *   <li>Everything starts OFF and goes back to OFF on load, join, disconnect and error.</li>
 *   <li>AUTO BUY / AUTO SELL can only be enabled while the master switch is ON.</li>
 *   <li>Master switch can only be enabled in {@link Mode#AUTHORIZED_AUTOMATION} with an
 *       available {@link AuctionIntegration}; otherwise enabling is refused and reported.</li>
 *   <li>Emergency stop disables everything, clears the pending queue and is irreversible
 *       without an explicit user action.</li>
 * </ul>
 */
public final class AutomationState {
    public enum Mode {
        /** Analysis only: nothing is bought or sold, not even by hand through the mod. */
        ANALYSIS_ONLY,
        /** The mod proposes trades, the player confirms every single one. */
        MANUAL_ASSISTANT,
        /** Fully automated trading, only with an authorized server API. */
        AUTHORIZED_AUTOMATION;

        public static Mode parse(String raw) {
            if (raw != null) {
                for (Mode mode : values()) {
                    if (mode.name().equalsIgnoreCase(raw)) {
                        return mode;
                    }
                }
            }
            return ANALYSIS_ONLY;
        }
    }

    public interface Listener {
        void onAutomationChanged(AutomationState state, String reason);
    }

    /** Implemented by the trade engine so the emergency stop can drop queued work. */
    public interface PendingWork {
        int cancelAll(String reason);

        int getPendingCount();
    }

    private boolean master;
    private boolean autoBuy;
    private boolean autoSell;
    private Mode mode = Mode.ANALYSIS_ONLY;
    private final AuctionIntegration integration;
    private final List<Listener> listeners = new ArrayList<Listener>();
    private PendingWork pendingWork;
    private String lastChangeReason = "initial state";
    private long lastEmergencyStopAt = 0L;
    private int emergencyStopCount = 0;

    public AutomationState() {
        this(new AuctionIntegration.None());
    }

    public AutomationState(AuctionIntegration integration) {
        this.integration = integration == null ? new AuctionIntegration.None() : integration;
    }

    public AuctionIntegration getIntegration() {
        return integration;
    }

    public void setPendingWork(PendingWork pendingWork) {
        this.pendingWork = pendingWork;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public boolean isMasterEnabled() {
        return master;
    }

    public boolean isAutoBuyEnabled() {
        return autoBuy && master && isAutomationAllowed();
    }

    public boolean isAutoSellEnabled() {
        return autoSell && master && isAutomationAllowed();
    }

    /** Raw switch value, ignoring the master switch (used by the GUI to render the switch). */
    public boolean isAutoBuySwitchOn() {
        return autoBuy;
    }

    public boolean isAutoSellSwitchOn() {
        return autoSell;
    }

    public Mode getMode() {
        return mode;
    }

    public String getLastChangeReason() {
        return lastChangeReason;
    }

    public int getEmergencyStopCount() {
        return emergencyStopCount;
    }

    public long getLastEmergencyStopAt() {
        return lastEmergencyStopAt;
    }

    public boolean isAutomationAllowed() {
        return mode == Mode.AUTHORIZED_AUTOMATION && integration.isAvailable();
    }

    public String getAutomationBlockedReason() {
        if (mode != Mode.AUTHORIZED_AUTOMATION) {
            return "Mode is " + mode.name() + " - automation needs AUTHORIZED_AUTOMATION";
        }
        if (!integration.isAvailable()) {
            return "Automation API unavailable: " + integration.getUnavailableReason();
        }
        return "";
    }

    public boolean isAutomationActive() {
        return master && isAutomationAllowed();
    }

    public boolean setMode(Mode newMode, String reason) {
        if (newMode == null || newMode == mode) {
            return false;
        }
        if (newMode == Mode.AUTHORIZED_AUTOMATION && !integration.isAvailable()) {
            lastChangeReason = "AUTHORIZED_AUTOMATION refused: " + integration.getUnavailableReason();
            fireListeners(lastChangeReason);
            return false;
        }
        mode = newMode;
        if (mode != Mode.AUTHORIZED_AUTOMATION) {
            master = false;
            autoBuy = false;
            autoSell = false;
            if (pendingWork != null) {
                pendingWork.cancelAll("mode changed to " + mode.name());
            }
        }
        lastChangeReason = reason == null ? "mode -> " + mode.name() : reason;
        fireListeners(lastChangeReason);
        return true;
    }

    /** Turns the master switch on/off. Enabling may be refused, disabling never is. */
    public boolean setMaster(boolean enabled, String reason) {
        if (!enabled) {
            master = false;
            autoBuy = false;
            autoSell = false;
            if (pendingWork != null) {
                pendingWork.cancelAll("master switch off");
            }
            lastChangeReason = reason == null ? "master off" : reason;
            fireListeners(lastChangeReason);
            return true;
        }
        if (!isAutomationAllowed()) {
            lastChangeReason = "Master switch refused: " + getAutomationBlockedReason();
            fireListeners(lastChangeReason);
            return false;
        }
        if (!master) {
            master = true;
            lastChangeReason = reason == null ? "master on" : reason;
            fireListeners(lastChangeReason);
        }
        return true;
    }

    public boolean setAutoBuy(boolean enabled, String reason) {
        if (enabled && !master) {
            lastChangeReason = "AUTO BUY refused: master automation switch is OFF";
            fireListeners(lastChangeReason);
            return false;
        }
        boolean changed = autoBuy != enabled;
        autoBuy = enabled;
        if (changed) {
            lastChangeReason = reason == null ? "auto buy " + (enabled ? "on" : "off") : reason;
            fireListeners(lastChangeReason);
        }
        return true;
    }

    public boolean setAutoSell(boolean enabled, String reason) {
        if (enabled && !master) {
            lastChangeReason = "AUTO SELL refused: master automation switch is OFF";
            fireListeners(lastChangeReason);
            return false;
        }
        boolean changed = autoSell != enabled;
        autoSell = enabled;
        if (changed) {
            lastChangeReason = reason == null ? "auto sell " + (enabled ? "on" : "off") : reason;
            fireListeners(lastChangeReason);
        }
        return true;
    }

    /** Hard reset of every switch. Used on load, join, disconnect, errors and by the GUI. */
    public void reset(String reason) {
        boolean wasOn = master || autoBuy || autoSell;
        master = false;
        autoBuy = false;
        autoSell = false;
        int cancelled = pendingWork == null ? 0 : pendingWork.cancelAll(reason);
        lastChangeReason = reason + (cancelled > 0 ? " (" + cancelled + " pending task(s) cancelled)" : "");
        if (wasOn || cancelled > 0) {
            fireListeners(lastChangeReason);
        }
    }

    /**
     * Emergency stop: switch everything off, cancel queued operations and log the event.
     *
     * @return number of cancelled pending operations
     */
    public int emergencyStop(String reason, long now) {
        master = false;
        autoBuy = false;
        autoSell = false;
        int cancelled = pendingWork == null ? 0 : pendingWork.cancelAll(reason);
        lastEmergencyStopAt = now;
        emergencyStopCount++;
        lastChangeReason = "EMERGENCY STOP: " + reason;
        fireListeners(lastChangeReason);
        return cancelled;
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("mode=").append(mode.name());
        builder.append(" master=").append(master ? "ON" : "OFF");
        builder.append(" autoBuy=").append(autoBuy ? "ON" : "OFF");
        builder.append(" autoSell=").append(autoSell ? "ON" : "OFF");
        builder.append(" automationAvailable=").append(isAutomationAllowed());
        return builder.toString();
    }

    private void fireListeners(String reason) {
        List<Listener> copy = new ArrayList<Listener>(listeners);
        for (int i = 0; i < copy.size(); i++) {
            copy.get(i).onAutomationChanged(this, reason);
        }
    }
}
