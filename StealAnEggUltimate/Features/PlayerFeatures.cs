namespace StealAnEggUltimate;

public enum PlayerMode
{
    /// <summary>No live official-place bridge: features are previews of the demo world.</summary>
    Demo,

    /// <summary>Bridge online inside the bundled official test place: features are real.</summary>
    Official
}

/// <summary>
/// Translates player feature toggles into (a) the desired-state document the Luau
/// bridge reads, and (b) local demo behaviour when no official bridge is online.
/// The hub itself never writes to the Roblox process.
/// </summary>
public sealed class PlayerFeatures : ObservableObject
{
    private readonly Logger _log;
    private readonly BridgeServer _bridge;
    private readonly EggFeatures _eggs;

    private PlayerMode _mode = PlayerMode.Demo;
    private string _bridgeStatus = "Bridge offline";
    private string _lastAction = "No actions yet.";
    private bool _autoRunning;

    public PlayerFeatures(Logger log, BridgeServer bridge, EggFeatures eggs)
    {
        _log = log;
        _bridge = bridge;
        _eggs = eggs;
    }

    public PlayerMode Mode
    {
        get => _mode;
        private set
        {
            if (!Set(ref _mode, value)) return;
            Raise(nameof(IsOfficial), nameof(ModeText), nameof(ModeDetail), nameof(FlyStatus), nameof(NoclipStatus),
                nameof(InfiniteJumpStatus), nameof(SpeedStatus), nameof(JumpStatus));
        }
    }

    public bool IsOfficial => Mode == PlayerMode.Official;

    public string ModeText => IsOfficial ? "OFFICIAL TEST PLACE · LIVE CONTROL" : "DEMO / PREVIEW MODE";

    public string ModeDetail => IsOfficial
        ? "Every movement feature below is executed by the Luau bridge inside your bundled test place."
        : "No live bridge. Movement features preview on the demo camera; they are active only in the official test place.";

    public string BridgeStatus
    {
        get => _bridgeStatus;
        private set => Set(ref _bridgeStatus, value);
    }

    public string LastAction
    {
        get => _lastAction;
        private set => Set(ref _lastAction, value);
    }

    public bool AutomationRunning
    {
        get => _autoRunning;
        set => Set(ref _autoRunning, value);
    }

    public string SpeedStatus => IsOfficial ? "LIVE" : "PREVIEW";
    public string JumpStatus => IsOfficial ? "LIVE" : "PREVIEW";
    public string FlyStatus => IsOfficial ? "LIVE" : "PREVIEW";
    public string NoclipStatus => IsOfficial ? "LIVE" : "PREVIEW";
    public string InfiniteJumpStatus => IsOfficial ? "LIVE" : "PREVIEW";

    public string FlyKeyText => _flyKey;
    public string SpeedKeyText => _speedKey;
    public string TeleportKeyText => _teleportKey;

    private string _flyKey = "F";
    private string _speedKey = "X";
    private string _teleportKey = "T";

    public event EventHandler<string>? Notice;

    /// <summary>Recomputes the live/dead bridge state. Called by the UI tick.</summary>
    public void RefreshMode(AppSettings settings)
    {
        var snapshotFresh = _bridge.LastSnapshotUtc.HasValue
                            && DateTime.UtcNow - _bridge.LastSnapshotUtc.Value < TimeSpan.FromSeconds(6);
        var official = snapshotFresh && _eggs.SourceMode == EggOrigin.Official;

        Mode = official ? PlayerMode.Official : PlayerMode.Demo;

        BridgeStatus = !_bridge.IsRunning
            ? $"Bridge offline · port {_bridge.Port} unavailable"
            : snapshotFresh
                ? $"Bridge live · {_bridge.Endpoint} · {_bridge.SnapshotCount} snapshots"
                : $"Bridge listening on {_bridge.Endpoint} · waiting for the test place";

        _flyKey = settings.Player.FlyKey;
        _speedKey = settings.Player.SpeedBoostKey;
        _teleportKey = settings.Player.TeleportKey;

        if (_eggs.SourceMode != EggOrigin.Official && snapshotFresh)
            BridgeStatus += " · external snapshot (read-only)";
    }

    public void SetKeyLabels(string fly, string speed, string teleport)
    {
        _flyKey = fly;
        _speedKey = speed;
        _teleportKey = teleport;
        Raise(nameof(FlyKeyText), nameof(SpeedKeyText), nameof(TeleportKeyText));
    }

    /// <summary>Builds the desired-state document pushed to the Luau bridge.</summary>
    public BridgeStateDoc BuildState(AppSettings settings)
    {
        bool On(string id) => settings.Features.TryGetValue(id, out var value) && value;

        return new BridgeStateDoc
        {
            Player = _eggs.PlayerName,
            Speed = On(FeatureIds.Speed),
            WalkSpeed = settings.Player.WalkSpeed,
            JumpPower = On(FeatureIds.JumpPower),
            JumpPowerValue = settings.Player.JumpPower,
            Fly = On(FeatureIds.Fly),
            FlySpeed = settings.Player.FlySpeed,
            Noclip = On(FeatureIds.Noclip),
            InfiniteJump = On(FeatureIds.InfiniteJump),
            FlyKey = settings.Player.FlyKey,
            SpeedKey = settings.Player.SpeedBoostKey,
            TeleportKey = settings.Player.TeleportKey,
            AutoCollect = On(FeatureIds.AutoCollect),
            AutoReturn = On(FeatureIds.AutoReturn),
            AutoFarm = On(FeatureIds.AutoFarm),
            AutoSteal = On(FeatureIds.AutoSteal),
            AutoBuy = On(FeatureIds.AutoBuy),
            AutoSell = On(FeatureIds.AutoSell),
            AutoEggFinder = On(FeatureIds.AutoEggFinder),
            AutoRareTracker = On(FeatureIds.AutoRareTracker),
            IntervalMs = settings.Automation.IntervalMs,
            Target = settings.Automation.Target,
            RarityPriority = settings.Automation.RarityPriority,
            Notify = settings.Automation.Notify
        };
    }

    public void PublishState(AppSettings settings) => _bridge.PublishState(BuildState(settings));

    /// <summary>Teleport: real command in the official place, demo camera move otherwise.</summary>
    public bool Teleport(double x, double y, double z)
    {
        if (!IsFinite(x) || !IsFinite(y) || !IsFinite(z))
        {
            Notice?.Invoke(this, "Teleport needs valid X / Y / Z numbers.");
            return false;
        }

        if (IsOfficial)
        {
            _bridge.Enqueue("teleport", x, y, z);
            LastAction = $"Teleport command sent → {x:0.#}, {y:0.#}, {z:0.#}";
            _log.Info(LastAction);
            return true;
        }

        var snapshot = _eggs.Player;
        snapshot.X = x;
        snapshot.Y = y;
        snapshot.Z = z;
        _eggs.SetPlayerFromExternal(new Vec3(x, y, z), _eggs.Heading, snapshot);
        LastAction = $"Demo camera moved to {x:0.#}, {y:0.#}, {z:0.#} (preview only).";
        Notice?.Invoke(this, LastAction);
        return true;
    }

    public void SendCollect()
    {
        if (IsOfficial)
        {
            _bridge.Enqueue("collect");
            LastAction = "Collect command sent to the test place.";
            return;
        }

        LastAction = _eggs.CollectNearest()
            ? "Collected the nearest demo egg."
            : "No demo egg inside collection range (22 studs).";
        Notice?.Invoke(this, LastAction);
    }

    public void SendScan()
    {
        if (IsOfficial) _bridge.Enqueue("scan");
        LastAction = IsOfficial ? "Scan requested from the test place." : "Demo egg index refreshed.";
        if (!IsOfficial) _eggs.ResetDemo("manual scan");
    }

    public void SendAutomationControl(bool start)
    {
        _bridge.Enqueue(start ? "automation.start" : "automation.stop");
        AutomationRunning = start;
        LastAction = start ? "Automation start command sent." : "Automation stop command sent.";
        _log.Info(LastAction);
    }

    public void SetMovement(PlayerMode mode, string action)
    {
        LastAction = action;
        if (mode == PlayerMode.Official) _log.Info(action);
    }

    private static bool IsFinite(double value) => !double.IsNaN(value) && !double.IsInfinity(value);
}
