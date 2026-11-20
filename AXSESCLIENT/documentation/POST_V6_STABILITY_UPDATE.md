# Post-v6 stability and interface update

The changes in commit `7c75382` are a focused stability/interface update, not a new numbered release. Existing v6.0.0 release assets and their manifest are unchanged. CI run [44820084014](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/44820084014) verified that commit.

- Debounced configuration saves snapshot client state on the calling thread and write the snapshot on a worker. Pending writes are resolved before switching, renaming, deleting or importing profiles.
- Configuration replacement uses a temporary file and atomic move where available. A malformed or newer-version profile is not silently overwritten; recovery preserves the last-good backup and quarantines a damaged file before attempting recovery.
- A failed config import restores the previous in-memory state. The config GUI no longer claims success when saving or loading fails.
- ClickGUI uses compact header labels when space is limited, trims category names to leave room for counters, and freezes background bokeh under reduced motion.
- Profiling uses separate rings for actual HUD and GUI render timings, and uses nearest-rank p95 for frame times.
- CI release publication now requires an explicit publish request; ordinary verification pushes do not overwrite the existing v6.0.0 release.

CI passed the common JUnit suite, builds for all three Fabric targets, Linux/Xvfb E2E launch self-tests for Minecraft 1.16.5, 1.21.4 and 1.21.11, and the Windows x64 launcher/installer tests. These automated checks do not establish that every module works in every real-world game or server scenario.

`AXSESCLIENT-RELEASE/update-preview/` contains the checksums and two CI-rendered interface images. The screenshot generator uses the real shared UI renderer with a synthetic background, not a capture of a live Minecraft session. The corresponding locally downloaded binaries are intentionally ignored by Git; the complete CI bundle is available through the run's artifacts while GitHub retains them. The binaries retain the existing 6.0.0 version identifier and are not the public v6.0.0 release assets.
