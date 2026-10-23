using System.Collections.ObjectModel;
using System.Linq;

namespace StealAnEggUltimate;

/// <summary>
/// Owns the egg world: the built-in demo island, the read-only snapshot index and
/// the screen projection used by the overlay ESP.
///
/// Data honestly comes from one of three places:
///  * DEMO     - generated locally by this class (always available);
///  * OFFICIAL - JSON written by the bundled Luau bridge inside the official test place;
///  * SNAPSHOT - a user-provided read-only JSON snapshot of any place.
///
/// Third-party places are never read from memory, probed or modified.
/// </summary>
public sealed class EggFeatures : ObservableObject
{
    private const int DemoEggCount = 26;
    private const double DetectionRadius = 320;
    private const double DemoWalkSpeed = 26;
    private const double DemoRespawnSeconds = 11;

    private static readonly (EggRarity Rarity, string[] Names, int Min, int Max, double Weight)[] Pools =
    {
        (EggRarity.Common, new[] { "Speckled Egg", "Moss Egg", "Sandstone Egg", "Clover Egg" }, 5, 28, 42),
        (EggRarity.Uncommon, new[] { "Azure Egg", "Copper Egg", "Fern Egg", "Tide Egg" }, 40, 95, 25),
        (EggRarity.Rare, new[] { "Golden Egg", "Frost Egg", "Coral Egg", "Sunburst Egg" }, 120, 280, 16),
        (EggRarity.Epic, new[] { "Amethyst Egg", "Storm Egg", "Magma Egg", "Moonlit Egg" }, 400, 950, 10),
        (EggRarity.Legendary, new[] { "Celestial Egg", "Obsidian Egg", "Prism Egg" }, 1500, 3600, 5),
        (EggRarity.Mythic, new[] { "Eclipse Egg", "Nebula Egg" }, 6000, 12000, 2)
    };

    private static readonly string[] Zones = { "Sunny Shore", "Crystal Cove", "Mushroom Marsh", "Lava Landing", "Sky Nest", "Void Garden" };

    private readonly Logger _log;
    private readonly Random _random = new(20261023);
    private readonly HashSet<string> _discovered = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, double> _respawnTimers = new(StringComparer.OrdinalIgnoreCase);

    private Egg? _trackedTarget;
    private Egg? _bestEgg;
    private EggOrigin _sourceMode = EggOrigin.Demo;
    private string _sourceDescription = "Demo world (generated locally)";
    private Vec3 _playerPosition;
    private double _heading;
    private double _cameraHeight = 14;
    private PlayerSnapshot _player = new() { Name = "DemoPlayer", Health = 100, MaxHealth = 100, WalkSpeed = 16 };
    private int _collected;
    private int _sessionCoins;
    private int _pendingValue;
    private int _visibleCount;
    private int _rareVisible;
    private double _viewWidth = 1280;
    private double _demoOrbitSpeed = 18;
    private double _demoCameraHeight = 14;
    private double _viewHeight = 720;
    private Vec3 _spawnPad = Vec3.Zero;

    public EggFeatures(Logger log)
    {
        _log = log;
        Eggs = new ObservableCollection<Egg>();
        Discovered = new ObservableCollection<Egg>();
        ResetDemo("startup");
    }

    /// <summary>All eggs in the current index.</summary>
    public ObservableCollection<Egg> Eggs { get; }

    /// <summary>Eggs the scanner has already seen (newest first, capped at 60).</summary>
    public ObservableCollection<Egg> Discovered { get; }

    public Egg? TrackedTarget
    {
        get => _trackedTarget;
        private set
        {
            if (ReferenceEquals(_trackedTarget, value)) return;
            if (_trackedTarget is not null) _trackedTarget.Tracked = false;
            _trackedTarget = value;
            if (_trackedTarget is not null) _trackedTarget.Tracked = true;
            OnPropertyChanged();
            OnPropertyChanged(nameof(TrackedTargetText));
            OnPropertyChanged(nameof(TrackedSummary));
        }
    }

    public Egg? BestEgg
    {
        get => _bestEgg;
        private set
        {
            if (Set(ref _bestEgg, value)) OnPropertyChanged(nameof(BestEggText));
        }
    }

    public EggOrigin SourceMode
    {
        get => _sourceMode;
        private set
        {
            if (Set(ref _sourceMode, value)) Raise(nameof(SourceModeText), nameof(IsDemoMode), nameof(IsLiveData));
        }
    }

    public string SourceDescription
    {
        get => _sourceDescription;
        private set => Set(ref _sourceDescription, value);
    }

    public bool IsDemoMode => SourceMode == EggOrigin.Demo;
    public bool IsLiveData => SourceMode != EggOrigin.Demo;
    public string SourceModeText => SourceMode switch
    {
        EggOrigin.Demo => "DEMO DATA",
        EggOrigin.Official => "OFFICIAL PLACE",
        _ => "SNAPSHOT"
    };

    public Vec3 PlayerPosition
    {
        get => _playerPosition;
        private set
        {
            _playerPosition = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(PlayerPositionText));
        }
    }

    public string PlayerPositionText => $"Position  {PlayerPosition}";

    public double Heading
    {
        get => _heading;
        private set => Set(ref _heading, value);
    }

    public PlayerSnapshot Player
    {
        get => _player;
        private set
        {
            if (Set(ref _player, value)) Raise(nameof(PlayerInfoText), nameof(PlayerHealthText), nameof(PlayerCoinsText), nameof(PlayerName));
        }
    }

    public string PlayerName => Player.Name;
    public string PlayerInfoText => $"{Player.Name}  ·  HP {Player.Health:0}/{Player.MaxHealth:0}  ·  Speed {Player.WalkSpeed:0}";
    public string PlayerHealthText => $"{Player.Health:0} / {Player.MaxHealth:0} HP";
    public string PlayerCoinsText => $"{SessionCoins:N0} coins  ·  {PendingValue:N0} unsold";

    public int CollectedCount
    {
        get => _collected;
        private set
        {
            if (Set(ref _collected, value)) Raise(nameof(StatsText));
        }
    }

    public int SessionCoins
    {
        get => _sessionCoins;
        private set
        {
            if (Set(ref _sessionCoins, value)) Raise(nameof(SessionCoinsText), nameof(PlayerCoinsText), nameof(StatsText));
        }
    }

    public string SessionCoinsText => $"{SessionCoins:N0}";

    /// <summary>Value of collected eggs that have not been sold yet.</summary>
    public int PendingValue
    {
        get => _pendingValue;
        private set
        {
            if (Set(ref _pendingValue, value)) Raise(nameof(PendingValueText), nameof(PlayerCoinsText));
        }
    }

    public string PendingValueText => $"{PendingValue:N0} pending";

    /// <summary>Adds unsold value into the coin balance (Auto Sell / Sell All).</summary>
    public int SellAll()
    {
        if (PendingValue <= 0) return 0;
        var sold = PendingValue;
        PendingValue = 0;
        SessionCoins += sold;
        Player.Coins = SessionCoins;
        _log.Info($"Sold collected eggs for {sold} coins (balance {SessionCoins}).");
        Raise(nameof(PlayerInfoText), nameof(PlayerCoinsText));
        return sold;
    }
    public int VisibleCount
    {
        get => _visibleCount;
        private set
        {
            if (Set(ref _visibleCount, value)) Raise(nameof(StatsText));
        }
    }

    public int RareVisible
    {
        get => _rareVisible;
        private set
        {
            if (Set(ref _rareVisible, value)) Raise(nameof(StatsText));
        }
    }

    public string StatsText => $"{Eggs.Count} eggs indexed  ·  {VisibleCount} on ESP  ·  {RareVisible} rare  ·  {CollectedCount} collected";

    public string TrackedTargetText => TrackedTarget is null ? "No target tracked" : TrackedTarget.Summary;

    public string TrackedSummary => TrackedTarget is null
        ? "Track an egg to see bearing and distance."
        : $"{TrackedTarget.Name}\n{TrackedTarget.RarityText}  ·  {TrackedTarget.ValueText} coins\n{TrackedTarget.DistanceText} away  ·  bearing {TrackedTarget.Heading:0}°";

    public string BestEggText => BestEgg is null
        ? "No eggs in range"
        : $"{BestEgg.Name}  ·  {BestEgg.RarityText}  ·  {BestEgg.ValueText}  ·  {BestEgg.DistanceText}";

    /// <summary>Raised when a new egg enters detection range.</summary>
    public event EventHandler<Egg>? EggDiscovered;
    public event EventHandler<Egg>? RareEggDiscovered;
    public event EventHandler<Egg>? EggCollected;
    public event EventHandler? SourceChanged;

    // ------------------------------------------------------------------
    // Demo world
    // ------------------------------------------------------------------

    public void ResetDemo(string reason = "manual")
    {
        _discovered.Clear();
        _respawnTimers.Clear();
        Eggs.Clear();
        Discovered.Clear();
        TrackedTarget = null;
        BestEgg = null;
        CollectedCount = 0;
        SessionCoins = 0;
        PendingValue = 0;
        _heading = 0;
        _cameraHeight = 14;
        _spawnPad = new Vec3(0, 2, 0);
        PlayerPosition = _spawnPad;
        Player = new PlayerSnapshot
        {
            Name = "DemoPlayer",
            Health = 100,
            MaxHealth = 100,
            WalkSpeed = 16,
            X = _spawnPad.X,
            Y = _spawnPad.Y,
            Z = _spawnPad.Z,
            Place = "STEAL AN EGG · Demo Island",
            Coins = 0,
            Heading = 0,
            CameraHeight = 14
        };

        for (var i = 0; i < DemoEggCount; i++)
        {
            var pool = PickPool();
            var name = pool.Names[_random.Next(pool.Names.Length)];
            var angle = _random.NextDouble() * Math.PI * 2;
            var radius = 22 + _random.NextDouble() * 88;
            var position = new Vec3(
                Math.Round(Math.Cos(angle) * radius, 1),
                Math.Round(3 + _random.NextDouble() * 9, 1),
                Math.Round(Math.Sin(angle) * radius, 1));

            var value = _random.Next(pool.Min, pool.Max + 1);
            var zone = Zones[_random.Next(Zones.Length)];
            var egg = new Egg($"demo-{i:00}", name, pool.Rarity, value, position, EggOrigin.Demo, zone);
            Eggs.Add(egg);
        }

        SourceMode = EggOrigin.Demo;
        SourceDescription = $"Demo world · {DemoEggCount} generated eggs · no game connection";
        _log.Info($"Demo egg world rebuilt ({reason}).");
        UpdateStats();
        SourceChanged?.Invoke(this, EventArgs.Empty);
    }

    private (EggRarity Rarity, string[] Names, int Min, int Max, double Weight) PickPool()
    {
        var total = Pools.Sum(p => p.Weight);
        var roll = _random.NextDouble() * total;
        foreach (var pool in Pools)
        {
            roll -= pool.Weight;
            if (roll <= 0) return pool;
        }

        return Pools[0];
    }

    /// <summary>Moves the demo player along the ground toward a target (used by demo automation).</summary>
    public void DemoWalkTowards(Vec3 target, double deltaSeconds)
    {
        if (!IsDemoMode) return;

        var dx = target.X - PlayerPosition.X;
        var dz = target.Z - PlayerPosition.Z;
        var length = Math.Sqrt(dx * dx + dz * dz);
        if (length < 0.5) return;

        var step = Math.Min(length, DemoWalkSpeed * deltaSeconds);
        PlayerPosition = new Vec3(
            PlayerPosition.X + dx / length * step,
            PlayerPosition.Y,
            PlayerPosition.Z + dz / length * step);

        Heading = Math.Atan2(-dx, -dz) * 180 / Math.PI;
        Player.X = PlayerPosition.X;
        Player.Y = PlayerPosition.Y;
        Player.Z = PlayerPosition.Z;
        Player.Place = "STEAL AN EGG · Demo Island";
        OnPropertyChanged(nameof(PlayerInfoText));
    }

    public void DemoRespawn()
    {
        PlayerPosition = _spawnPad;
        Player.X = _spawnPad.X;
        Player.Y = _spawnPad.Y;
        Player.Z = _spawnPad.Z;
        OnPropertyChanged(nameof(PlayerInfoText));
    }

    // ------------------------------------------------------------------
    // Snapshot ingestion (official place / external file / HTTP)
    // ------------------------------------------------------------------

    /// <summary>Rebuilds the egg index from a read-only JSON snapshot.</summary>
    public void ApplySnapshot(EggSnapshot snapshot, EggOrigin origin)
    {
        if (snapshot is null) return;

        var previousIds = new HashSet<string>(Eggs.Select(e => e.Id), StringComparer.OrdinalIgnoreCase);
        var incoming = new List<Egg>();
        var index = 0;

        foreach (var entry in snapshot.Eggs ?? new List<EggSnapshotEntry>())
        {
            if (entry is null || entry.Taken) continue;

            var id = $"{origin}-{index++}-{entry.Name}".ToLowerInvariant();
            var zone = origin == EggOrigin.Official ? "Official test place" : (string.IsNullOrWhiteSpace(snapshot.Place) ? "Snapshot" : snapshot.Place);
            var egg = new Egg(
                id,
                string.IsNullOrWhiteSpace(entry.Name) ? "Egg" : entry.Name,
                entry.ParseRarity(),
                Math.Max(0, entry.Value),
                new Vec3(entry.X, entry.Y, entry.Z),
                origin,
                zone);

            incoming.Add(egg);
        }

        // Preserve the current selection where possible, otherwise keep something sensible.
        var trackedId = TrackedTarget?.Id;
        var trackedSourceName = TrackedTarget?.Name;

        Eggs.Clear();
        foreach (var egg in incoming) Eggs.Add(egg);

        var sourceChanged = SourceMode != origin;
        if (sourceChanged)
        {
            _discovered.Clear();
            Discovered.Clear();
        }

        SourceMode = origin;
        SourceDescription = origin == EggOrigin.Official
            ? "Official STEAL AN EGG test place · live JSON bridge"
            : $"Read-only snapshot · {snapshot.Place ?? "unknown place"} · {snapshot.Eggs?.Count ?? 0} eggs";

        if (snapshot.PlayerState is not null)
        {
            Player = snapshot.PlayerState;
            PlayerPosition = new Vec3(Player.X, Player.Y, Player.Z);
            Heading = Player.Heading;
            _cameraHeight = Player.CameraHeight > 1 ? Player.CameraHeight : 14;
            SessionCoins = Math.Max(SessionCoins, Player.Coins);
            if (sourceChanged) SessionCoins = Player.Coins;
        }

        TrackedTarget = Eggs.FirstOrDefault(e => e.Id == trackedId)
            ?? Eggs.FirstOrDefault(e => string.Equals(e.Name, trackedSourceName, StringComparison.OrdinalIgnoreCase));

        RefreshDiscovery(notify: true);
        UpdateStats();

        if (sourceChanged)
        {
            _log.Info($"Egg source switched to {SourceModeText} ({Eggs.Count} eggs).");
            SourceChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    public void ClearLiveSource()
    {
        if (IsDemoMode) return;
        ResetDemo("live source lost");
    }

    /// <summary>
    /// Demo-only feedback for the PLAYER tab: Speed/Fly/JumpPower change the simulated
    /// camera and the player card immediately, without a game client. In the official
    /// test place the Luau bridge does the real work and this method does nothing.
    /// </summary>
    public void ApplyDemoPlayerState(AppSettings settings)
    {
        if (!IsDemoMode) return;

        bool On(string id) => settings.Features.TryGetValue(id, out var value) && value;

        var speed = On(FeatureIds.Speed);
        var fly = On(FeatureIds.Fly);
        var jump = On(FeatureIds.JumpPower);

        _demoOrbitSpeed = fly ? 26 : speed ? 34 : 18;
        _demoCameraHeight = fly ? 34 : 14;

        var walkSpeed = speed ? Math.Max(16, settings.Player.WalkSpeed) : 16;
        var jumpPower = jump ? Math.Max(50, settings.Player.JumpPower) : 50;

        if (Math.Abs(Player.WalkSpeed - walkSpeed) < 0.01
            && Math.Abs(Player.CameraHeight - _demoCameraHeight) < 0.01
            && Math.Abs(Player.JumpPower - jumpPower) < 0.01)
            return;

        Player.WalkSpeed = walkSpeed;
        Player.JumpPower = jumpPower;
        Player.CameraHeight = _demoCameraHeight;
        Player.Heading = Heading;
        OnPropertyChanged(nameof(PlayerInfoText));
    }

    /// <summary>Returns to demo data if a live snapshot has not refreshed recently.</summary>
    public void ExpireLiveSource(TimeSpan staleAfter)
    {
        if (IsDemoMode) return;
        var stale = SnapshotUpdatedAt is { } time && DateTime.UtcNow - time > staleAfter;
        if (!stale) return;

        _log.Warn($"Live egg snapshot is stale (>{staleAfter.TotalSeconds:0}s); returning to demo data.");
        ResetDemo("stale snapshot");
    }

    public DateTime? SnapshotUpdatedAt { get; private set; }

    public void MarkSnapshotFresh() => SnapshotUpdatedAt = DateTime.UtcNow;

    // ------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------

    public IReadOnlyList<Egg> GetVisibleEggs(AppSettings settings)
    {
        var showEsp = settings.Features.TryGetValue(FeatureIds.EggEsp, out var esp) && esp;
        var rareOnly = settings.Features.TryGetValue(FeatureIds.RareEggEsp, out var rareEsp) && rareEsp
                       && !showEsp;

        if (!showEsp && !rareOnly) return Array.Empty<Egg>();

        var filter = settings.Features.TryGetValue(FeatureIds.EggRarityFilter, out var f) && f;

        return Eggs
            .Where(e => !filter || settings.Eggs.IsRarityVisible(e.Rarity))
            .Where(e => !filter || e.Value >= settings.Eggs.MinValue)
            .Where(e => !filter || e.Distance <= settings.Eggs.MaxDistance)
            .Where(e => !rareOnly || e.IsRare)
            .Where(e => e.OnScreen)
            .ToList();
    }

    public Egg? NearestEgg(bool rareOnly = false)
        => Eggs.Where(e => !rareOnly || e.IsRare)
            .OrderBy(e => e.Distance)
            .FirstOrDefault();

    public Egg? RarestEgg()
        => Eggs.OrderByDescending(e => e.Rarity)
            .ThenByDescending(e => e.Value)
            .ThenBy(e => e.Distance)
            .FirstOrDefault();

    public Egg? HighestValueEgg()
        => Eggs.OrderByDescending(e => e.Value).ThenBy(e => e.Distance).FirstOrDefault();

    public bool Track(Egg? egg)
    {
        if (egg is null || !Eggs.Contains(egg)) return false;
        TrackedTarget = egg;
        _log.Debug($"Tracking egg {egg.Name} ({egg.RarityText}, {egg.Value} coins, {egg.Distance:0} studs).");
        return true;
    }

    public bool TrackBest()
    {
        var best = BestEgg ?? HighestValueEgg();
        return Track(best);
    }

    // ------------------------------------------------------------------
    // Per-frame update
    // ------------------------------------------------------------------

    /// <summary>
    /// Advances the world: distances, discovery, projection and respawn timers.
    /// Must be called on the UI thread (it mutates observable collections).
    /// </summary>
    public void Update(TimeSpan delta, AppSettings settings, double viewWidth, double viewHeight)
    {
        if (viewWidth > 40 && viewHeight > 40)
        {
            _viewWidth = viewWidth;
            _viewHeight = viewHeight;
        }

        var dt = Math.Clamp(delta.TotalSeconds, 0, 0.25);
        if (dt <= 0) return;

        if (IsDemoMode)
        {
            // Demo camera orbits so the ESP is visibly alive and drags across the screen.
            // The orbit speed and camera height react to the PLAYER toggles, so switching
            // Speed / Fly on visibly changes the demo view instead of doing nothing.
            Heading = (Heading + dt * _demoOrbitSpeed) % 360;
            _cameraHeight = _demoCameraHeight;
            foreach (var egg in Eggs)
                egg.Distance = PlayerPosition.DistanceTo(egg.Position);
        }
        else
        {
            foreach (var egg in Eggs)
                egg.Distance = PlayerPosition.DistanceTo(egg.Position);
        }

        var scanner = settings.Features.TryGetValue(FeatureIds.EggScanner, out var s) && s;
        var esp = settings.Features.TryGetValue(FeatureIds.EggEsp, out var e1) && e1;
        var rareEsp = settings.Features.TryGetValue(FeatureIds.RareEggEsp, out var e2) && e2;
        var best = settings.Features.TryGetValue(FeatureIds.BestEggFinder, out var e3) && e3;
        var filter = settings.Features.TryGetValue(FeatureIds.EggRarityFilter, out var e4) && e4;

        foreach (var egg in Eggs)
        {
            Project(egg);
            if (filter && !settings.Eggs.IsRarityVisible(egg.Rarity)) egg.OnScreen = false;

            if (scanner && egg.Distance <= DetectionRadius && _discovered.Add(egg.Id))
                RegisterDiscovery(egg, settings);
        }

        if (best) BestEgg = RarestEgg();

        if (esp || rareEsp)
        {
            VisibleCount = Eggs.Count(e => e.OnScreen && (!filter || settings.Eggs.IsRarityVisible(e.Rarity)));
            RareVisible = Eggs.Count(e => e.OnScreen && e.IsRare && (!filter || settings.Eggs.IsRarityVisible(e.Rarity)));
        }
        else
        {
            VisibleCount = 0;
            RareVisible = 0;
        }

        TickRespawns(dt);
    }

    private void TickRespawns(double dt)
    {
        if (_respawnTimers.Count == 0) return;

        foreach (var id in _respawnTimers.Keys.ToList())
        {
            var remaining = _respawnTimers[id] - dt;
            if (remaining > 0)
            {
                _respawnTimers[id] = remaining;
                continue;
            }

            _respawnTimers.Remove(id);
            var egg = Eggs.FirstOrDefault(e => e.Id == id);
            if (egg is null) continue;
            egg.ResetToSpawn();
            egg.Distance = PlayerPosition.DistanceTo(egg.Position);
        }
    }

    private void RegisterDiscovery(Egg egg, AppSettings settings)
    {
        Discovered.Insert(0, egg);
        while (Discovered.Count > 60) Discovered.RemoveAt(Discovered.Count - 1);

        EggDiscovered?.Invoke(this, egg);

        var rareNotify = settings.Features.TryGetValue(FeatureIds.RareEggNotifications, out var n) && n;
        if (egg.IsRare && rareNotify) RareEggDiscovered?.Invoke(this, egg);
    }

    private void RefreshDiscovery(bool notify)
    {
        foreach (var egg in Eggs)
        {
            if (egg.Distance > DetectionRadius) continue;
            if (!_discovered.Add(egg.Id)) continue;

            Discovered.Insert(0, egg);
            if (notify) EggDiscovered?.Invoke(this, egg);
        }

        while (Discovered.Count > 60) Discovered.RemoveAt(Discovered.Count - 1);
    }

    private void UpdateStats()
    {
        Raise(nameof(StatsText), nameof(VisibleCount), nameof(RareVisible), nameof(TrackedTargetText));
    }

    /// <summary>
    /// Pseudo-3D projection of a world position onto the overlay viewport.
    /// Uses real snapshot coordinates + camera yaw; no game memory is read.
    /// Screen space is in WPF device-independent pixels.
    /// </summary>
    private void Project(Egg egg)
    {
        var yaw = Heading * Math.PI / 180.0;

        var dx = egg.Position.X - PlayerPosition.X;
        var dy = egg.Position.Y - (PlayerPosition.Y + _cameraHeight);
        var dz = egg.Position.Z - PlayerPosition.Z;

        // Rotate world into camera space (yaw only).
        var forward = dx * -Math.Sin(yaw) + dz * -Math.Cos(yaw);
        var right = dx * Math.Cos(yaw) - dz * Math.Sin(yaw);

        if (forward <= 6 || forward > 900)
        {
            egg.OnScreen = false;
            return;
        }

        var focal = _viewWidth * 0.62;
        var screenX = _viewWidth / 2.0 + right / forward * focal;
        var screenY = _viewHeight * 0.56 - (dy / forward) * focal;

        var marginX = _viewWidth * 0.22;
        var marginY = _viewHeight * 0.20;

        egg.OnScreen = screenX > -marginX && screenX < _viewWidth + marginX
                      && screenY > -marginY && screenY < _viewHeight + marginY;

        egg.ScreenX = screenX;
        egg.ScreenY = screenY;
        egg.Heading = (Math.Atan2(right, forward) * 180 / Math.PI + 360) % 360;
    }

    // ------------------------------------------------------------------
    // Collection
    // ------------------------------------------------------------------

    /// <summary>
    /// Collects an egg. In demo mode this updates the local demo world; when a live
    /// official-place snapshot is active the request is forwarded to the Luau bridge
    /// by the caller (the hub itself never touches the game).
    /// </summary>
    public bool Collect(Egg egg, double maxDistance = 20)
    {
        if (egg is null || egg.Distance > maxDistance) return false;
        if (!IsDemoMode) return false;

        egg.MoveTo(egg.SpawnPosition);
        _respawnTimers[egg.Id] = DemoRespawnSeconds;
        CollectedCount++;
        PendingValue += egg.Value;
        _log.Info($"Demo collected {egg.Name} ({egg.RarityText}) worth {egg.Value} coins; respawns in {DemoRespawnSeconds:0}s.");
        EggCollected?.Invoke(this, egg);
        OnPropertyChanged(nameof(PlayerInfoText));
        OnPropertyChanged(nameof(PlayerCoinsText));
        return true;
    }

    public bool CollectNearest()
    {
        var target = Eggs.OrderBy(e => e.Distance).FirstOrDefault();
        return target is not null && Collect(target, maxDistance: 22);
    }

    public double DistanceFromSpawn => PlayerPosition.DistanceTo(_spawnPad);

    /// <summary>Spends coins in the demo economy (Auto Buy).</summary>
    public bool SpendCoins(int amount)
    {
        if (amount <= 0) return false;
        if (SessionCoins < amount) return false;

        SessionCoins -= amount;
        Player.Coins = SessionCoins;
        Raise(nameof(PlayerInfoText), nameof(PlayerCoinsText));
        return true;
    }

    /// <summary>Adds a bonus egg to the demo island (used by the demo Auto Buy flow).</summary>
    public Egg? DemoSpawnBonusEgg(EggRarity rarity)
    {
        if (!IsDemoMode) return null;

        var angle = _random.NextDouble() * Math.PI * 2;
        var radius = 18 + _random.NextDouble() * 60;
        var position = new Vec3(
            Math.Round(Math.Cos(angle) * radius, 1),
            Math.Round(4 + _random.NextDouble() * 8, 1),
            Math.Round(Math.Sin(angle) * radius, 1));

        var value = rarity switch
        {
            EggRarity.Mythic => 7000 + _random.Next(0, 4000),
            EggRarity.Legendary => 1700 + _random.Next(0, 1500),
            EggRarity.Epic => 450 + _random.Next(0, 400),
            EggRarity.Rare => 130 + _random.Next(0, 120),
            _ => 20 + _random.Next(0, 40)
        };

        var egg = new Egg($"bonus-{Eggs.Count:000}-{_random.Next(1000, 9999)}",
            rarity >= EggRarity.Legendary ? "Celestial Crate Egg" : "Lucky Crate Egg",
            rarity, value, position, EggOrigin.Demo, Zones[_random.Next(Zones.Length)]);
        egg.Distance = PlayerPosition.DistanceTo(egg.Position);
        Eggs.Add(egg);
        UpdateStats();
        _log.Info($"Demo shop spawned a {rarity} egg worth {value} coins.");
        return egg;
    }

    public void SetPlayerFromExternal(Vec3 position, double heading, PlayerSnapshot snapshot)
    {
        PlayerPosition = position;
        Heading = heading;
        Player = snapshot;
    }
}
