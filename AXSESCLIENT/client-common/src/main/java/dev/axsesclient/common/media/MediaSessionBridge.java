package dev.axsesclient.common.media;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous Spotify & OS Media Session controller (Java 8+ compatible).
 * Detects active Spotify desktop playback on Windows/Linux without blocking render/tick threads,
 * dispatches real OS media transport keys (Play/Pause, Stop, Next, Previous),
 * and exposes instant optimistic transport state for HUD & Chat interaction.
 */
public final class MediaSessionBridge {
    public enum PlaybackState {
        PLAYING,
        PAUSED,
        STOPPED
    }

    private static final String[][] BUILTIN_PLAYLIST = new String[][] {
            { "Kordhell", "Murder In My Mind", "145" },
            { "DVRST", "Close Eyes", "132" },
            { "Øneheart x Reidenshi", "Snowfall", "124" },
            { "Hensonn", "Sahara", "171" },
            { "Mareux", "The Perfect Girl", "194" },
            { "INTERWORLD", "METAMORPHOSIS", "142" }
    };

    private static final MediaSessionBridge INSTANCE = new MediaSessionBridge();

    public static MediaSessionBridge getInstance() {
        return INSTANCE;
    }

    private final boolean isWindows;
    private final boolean isLinux;
    private final ExecutorService ioPool;

    private volatile boolean spotifyProcessDetected = false;
    private volatile PlaybackState state = PlaybackState.PLAYING;
    private volatile String artist = "Kordhell";
    private volatile String trackTitle = "Murder In My Mind";
    private volatile int durationSec = 145;
    private volatile float progressSec = 48.0f;
    private volatile int fallbackIndex = 0;
    private volatile long lastPollMs = 0L;
    private volatile long manualOverrideUntilMs = 0L;
    private volatile boolean pollingInFlight = false;

    private MediaSessionBridge() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        this.isWindows = os.contains("win");
        this.isLinux = os.contains("linux") || os.contains("nix");
        this.ioPool = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Axses-SpotifyMediaBridge");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void tick(float dtSeconds, boolean syncExternalSpotify) {
        if (state == PlaybackState.PLAYING) {
            progressSec += Math.max(0.0f, Math.min(0.25f, dtSeconds));
            if (progressSec >= durationSec) {
                if (!spotifyProcessDetected) {
                    advanceFallbackTrack(1);
                } else {
                    progressSec = 0.0f;
                }
            }
        }

        if (!syncExternalSpotify) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPollMs < 2000L || pollingInFlight) {
            return;
        }
        lastPollMs = now;
        pollingInFlight = true;
        ioPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    pollExternalSpotifyOnce();
                } catch (Throwable ignored) {
                } finally {
                    pollingInFlight = false;
                }
            }
        });
    }

    private void pollExternalSpotifyOnce() {
        long now = System.currentTimeMillis();
        if (isWindows) {
            Process p = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/fi", "IMAGENAME eq Spotify.exe", "/v", "/fo", "csv", "/nh");
                pb.redirectErrorStream(true);
                p = pb.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String line;
                boolean foundSpotify = false;
                String detectedTitle = null;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.toLowerCase(Locale.ROOT).startsWith("\"spotify.exe\"")) {
                        foundSpotify = true;
                        int lastComma = trimmed.lastIndexOf("\",\"");
                        if (lastComma > 0 && trimmed.endsWith("\"")) {
                            String winTitle = trimmed.substring(lastComma + 3, trimmed.length() - 1).trim();
                            if (!winTitle.isEmpty() && !"N/A".equalsIgnoreCase(winTitle) && !"OleMainThreadWndName".equalsIgnoreCase(winTitle)) {
                                detectedTitle = winTitle;
                                break;
                            }
                        }
                    }
                }
                p.waitFor(900, TimeUnit.MILLISECONDS);
                spotifyProcessDetected = foundSpotify;
                if (foundSpotify && detectedTitle != null && now >= manualOverrideUntilMs) {
                    String lower = detectedTitle.toLowerCase(Locale.ROOT);
                    if ("spotify".equals(lower) || "spotify free".equals(lower) || "spotify premium".equals(lower)) {
                        if (state != PlaybackState.STOPPED) {
                            state = PlaybackState.PAUSED;
                        }
                    } else {
                        int sep = detectedTitle.indexOf(" - ");
                        String nextArtist = sep > 0 ? detectedTitle.substring(0, sep).trim() : "Spotify";
                        String nextTrack = sep > 0 ? detectedTitle.substring(sep + 3).trim() : detectedTitle;
                        if (!nextTrack.equals(trackTitle)) {
                            progressSec = 0.0f;
                            durationSec = 180;
                        }
                        artist = nextArtist;
                        trackTitle = nextTrack;
                        state = PlaybackState.PLAYING;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (p != null) {
                    p.destroy();
                }
            }
        } else if (isLinux) {
            Process p = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("playerctl", "-p", "spotify", "metadata", "--format", "{{status}}|{{artist}}|{{title}}");
                pb.redirectErrorStream(true);
                p = pb.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String line = br.readLine();
                boolean ok = p.waitFor(600, TimeUnit.MILLISECONDS) && p.exitValue() == 0;
                if (ok && line != null && line.contains("|")) {
                    spotifyProcessDetected = true;
                    if (now >= manualOverrideUntilMs) {
                        String[] parts = line.split("\\|", 3);
                        String st = parts[0].trim();
                        if ("Playing".equalsIgnoreCase(st)) state = PlaybackState.PLAYING;
                        else if ("Paused".equalsIgnoreCase(st)) state = PlaybackState.PAUSED;
                        else state = PlaybackState.STOPPED;
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) artist = parts[1].trim();
                        if (parts.length > 2 && !parts[2].trim().isEmpty()) trackTitle = parts[2].trim();
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (p != null) {
                    p.destroy();
                }
            }
        }
    }

    public PlaybackState togglePlayPause() {
        manualOverrideUntilMs = System.currentTimeMillis() + 3200L;
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
        } else {
            state = PlaybackState.PLAYING;
        }
        dispatchOsMediaCommand(0xB3, "play-pause");
        return state;
    }

    public void playContinue() {
        manualOverrideUntilMs = System.currentTimeMillis() + 3200L;
        if (state != PlaybackState.PLAYING) {
            state = PlaybackState.PLAYING;
            dispatchOsMediaCommand(0xB3, "play");
        }
    }

    public void stopMusic() {
        manualOverrideUntilMs = System.currentTimeMillis() + 5000L;
        boolean wasPlaying = (state == PlaybackState.PLAYING);
        state = PlaybackState.STOPPED;
        progressSec = 0.0f;
        if (wasPlaying) {
            dispatchOsMediaCommand(0xB2, "stop");
        }
    }

    public void nextTrack() {
        manualOverrideUntilMs = System.currentTimeMillis() + 3000L;
        if (!spotifyProcessDetected) {
            advanceFallbackTrack(1);
        } else {
            progressSec = 0.0f;
        }
        state = PlaybackState.PLAYING;
        dispatchOsMediaCommand(0xB0, "next");
    }

    public void previousTrack() {
        manualOverrideUntilMs = System.currentTimeMillis() + 3000L;
        if (!spotifyProcessDetected) {
            advanceFallbackTrack(-1);
        } else {
            progressSec = 0.0f;
        }
        state = PlaybackState.PLAYING;
        dispatchOsMediaCommand(0xB1, "previous");
    }

    private void advanceFallbackTrack(int delta) {
        int len = BUILTIN_PLAYLIST.length;
        fallbackIndex = ((fallbackIndex + delta) % len + len) % len;
        artist = BUILTIN_PLAYLIST[fallbackIndex][0];
        trackTitle = BUILTIN_PLAYLIST[fallbackIndex][1];
        durationSec = Integer.parseInt(BUILTIN_PLAYLIST[fallbackIndex][2]);
        progressSec = 0.0f;
    }

    private void dispatchOsMediaCommand(final int winVkCode, final String linuxCmd) {
        ioPool.submit(new Runnable() {
            @Override
            public void run() {
                Process p = null;
                try {
                    if (isWindows) {
                        String ps = "Add-Type -TypeDefinition 'using System.Runtime.InteropServices; public class AxK { [DllImport(\"user32.dll\")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, int dwExtraInfo); }'; "
                                + "[AxK]::keybd_event(" + winVkCode + ",0,0,0); [AxK]::keybd_event(" + winVkCode + ",0,2,0)";
                        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", ps);
                        p = pb.start();
                        p.waitFor(1500, TimeUnit.MILLISECONDS);
                    } else if (isLinux) {
                        ProcessBuilder pb = new ProcessBuilder("playerctl", linuxCmd);
                        p = pb.start();
                        p.waitFor(700, TimeUnit.MILLISECONDS);
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (p != null) {
                        p.destroy();
                    }
                }
            }
        });
    }

    public boolean isSpotifyProcessDetected() {
        return spotifyProcessDetected;
    }

    public void setSimulatedSpotifyStateForShowcase(boolean detected, PlaybackState st, String art, String title, float prog, int dur) {
        this.spotifyProcessDetected = detected;
        this.state = st;
        this.artist = art;
        this.trackTitle = title;
        this.progressSec = prog;
        this.durationSec = Math.max(10, dur);
        this.manualOverrideUntilMs = System.currentTimeMillis() + 60000L;
    }

    public PlaybackState getState() {
        return state;
    }

    public boolean isPlaying() {
        return state == PlaybackState.PLAYING;
    }

    public String getArtist() {
        return artist;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public float getProgressRatio() {
        return Math.max(0.0f, Math.min(1.0f, progressSec / Math.max(1.0f, (float) durationSec)));
    }

    public String formatTimeCode() {
        int cur = Math.max(0, Math.round(progressSec));
        int tot = Math.max(1, durationSec);
        return String.format(Locale.ROOT, "%d:%02d / %d:%02d",
                Integer.valueOf(cur / 60), Integer.valueOf(cur % 60),
                Integer.valueOf(tot / 60), Integer.valueOf(tot % 60));
    }
}
