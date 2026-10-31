package dev.funtime.auctionhelper.adapter;

import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.model.ScreenSnapshot;
import dev.funtime.auctionhelper.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the adapter for a captured screen and keeps the "unknown GUI" state.
 *
 * <p>When a screen cannot be understood the registry disables analysis for that screen
 * (<b>no crash, no guessing</b>), records diagnostics and tells the player once.</p>
 */
public final class AdapterRegistry {
    private final List<AuctionAdapter> adapters = new ArrayList<AuctionAdapter>();
    private final AdapterDiagnostics diagnostics;
    private HelperConfig config;
    private int unknownScreenCount;
    private int analysedScreens;
    private int parsedListings;
    private String lastTitle = "";
    private String lastWarning = "";
    private ParseResult lastResult;

    public AdapterRegistry(HelperConfig config, AdapterDiagnostics diagnostics) {
        this.config = config;
        this.diagnostics = diagnostics;
        rebuild();
    }

    public void setConfig(HelperConfig config) {
        this.config = config;
        rebuild();
    }

    public void rebuild() {
        adapters.clear();
        if (config == null) {
            return;
        }
        for (int i = 0; i < config.adapterProfiles.size(); i++) {
            HelperConfig.AdapterProfileConfig profile = config.adapterProfiles.get(i);
            if (profile != null && profile.enabled) {
                adapters.add(new RegexAuctionAdapter(profile));
            }
        }
    }

    public List<AuctionAdapter> adapters() {
        return new ArrayList<AuctionAdapter>(adapters);
    }

    public ParseResult parse(ScreenSnapshot snapshot) {
        if (snapshot == null) {
            return ParseResult.unknown("none", "Nothing captured");
        }
        lastTitle = snapshot.getTitle();
        analysedScreens++;
        ParseResult best = null;
        for (int i = 0; i < adapters.size(); i++) {
            AuctionAdapter adapter = adapters.get(i);
            try {
                if (!adapter.matches(snapshot)) {
                    continue;
                }
                ParseResult result = adapter.parse(snapshot);
                if (best == null || result.getListings().size() > best.getListings().size()) {
                    best = result;
                }
            } catch (RuntimeException error) {
                Log.error("Adapter " + adapter.getId() + " failed on screen \"" + snapshot.getTitle() + "\"", error);
                lastWarning = "Adapter crashed: " + error.getClass().getSimpleName();
            }
        }
        if (best == null) {
            unknownScreenCount++;
            lastResult = ParseResult.unknown("none", "No adapter matched screen \"" + snapshot.getTitle() + "\"");
            lastWarning = lastResult.getWarnings().isEmpty() ? "" : lastResult.getWarnings().get(0);
            if (diagnostics != null && unknownScreenCount <= 40) {
                List<String> sample = new ArrayList<String>();
                for (int i = 0; i < snapshot.getSlots().size(); i++) {
                    if (!snapshot.getSlots().get(i).isEmpty()) {
                        sample.add(snapshot.getSlots().get(i).toString());
                    }
                }
                diagnostics.recordUnknownScreen(snapshot.getTitle(), snapshot.getScreenClass(),
                        snapshot.getNonEmptySlotCount(), sample);
            }
            return lastResult;
        }
        if (best.isEmpty()) {
            unknownScreenCount++;
        } else {
            parsedListings += best.getListings().size();
            if (diagnostics != null) {
                diagnostics.record("parsed " + best.getListings().size() + " offer(s) from \""
                        + snapshot.getTitle() + "\" with profile " + best.getProfileId()
                        + " (confidence " + best.getConfidence() + ")");
            }
        }
        lastResult = best;
        lastWarning = best.getWarnings().isEmpty() ? "" : best.getWarnings().get(0);
        return best;
    }

    public ParseResult getLastResult() {
        return lastResult;
    }

    public int getUnknownScreenCount() {
        return unknownScreenCount;
    }

    public int getAnalysedScreens() {
        return analysedScreens;
    }

    public int getParsedListings() {
        return parsedListings;
    }

    public String getLastTitle() {
        return lastTitle;
    }

    public String getLastWarning() {
        return lastWarning;
    }

    public AdapterDiagnostics getDiagnostics() {
        return diagnostics;
    }

    /** The GUI shows "UNKNOWN GUI" instead of pretending analysis works. */
    public boolean isAnalysisAvailable() {
        return lastResult != null && !lastResult.isEmpty();
    }
}
