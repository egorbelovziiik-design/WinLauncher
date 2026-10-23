using System.Linq;

namespace StealAnEggUltimate;

public enum NoticeKind
{
    Info,
    Success,
    Rare,
    Automation,
    Error
}

public sealed record HubNotice(string Text, NoticeKind Kind, DateTime Time)
{
    public string TimeText => Time.ToString("HH:mm:ss");
    public string KindText => Kind switch
    {
        NoticeKind.Rare => "RARE",
        NoticeKind.Automation => "AUTO",
        NoticeKind.Error => "ERROR",
        NoticeKind.Success => "OK",
        _ => "INFO"
    };
}

/// <summary>
/// Runs the automation loop. Inside the official test place the Luau bridge owns
/// the actual movement and the hub only streams flags + commands. Without a bridge
/// the loop drives the built-in demo world, which is visibly simulated and never
/// presented as affecting a third-party game.
/// </summary>
public sealed class AutomationFeatures : ObservableObject
{
    private readonly Logger _log;
    private readonly EggFeatures _eggs;
    private readonly PlayerFeatures _player;

    private bool _isRunning;
    private int _cycles;
    private int _actions;
    private double _uptime;
    private double _cycleTimer;
    private double _finderTimer;
    private string _lastAction = "Automation idle.";
    private string _modeText = "DEMO SIMULATION";

    public AutomationFeatures(Logger log, EggFeatures eggs, PlayerFeatures player)
    {
        _log = log;
        _eggs = eggs;
        _player = player;
    }

    public bool IsRunning
    {
        get => _isRunning;
        private set
        {
            if (Set(ref _isRunning, value)) Raise(nameof(StateText), nameof(StartStopText));
        }
    }

    public string StartStopText => IsRunning ? "STOP AUTOMATION" : "START AUTOMATION";

    public string StateText => IsRunning ? "RUNNING" : "STOPPED";

    public string ModeText
    {
        get => _modeText;
        private set => Set(ref _modeText, value);
    }

    public int Cycles
    {
        get => _cycles;
        private set
        {
            if (Set(ref _cycles, value)) Raise(nameof(StatsText));
        }
    }

    public int Actions
    {
        get => _actions;
        private set
        {
            if (Set(ref _actions, value)) Raise(nameof(StatsText));
        }
    }

    public string UptimeText => TimeSpan.FromSeconds(_uptime).ToString(@"mm\:ss");

    public string StatsText => $"{Cycles} cycles  ·  {Actions} actions  ·  uptime {UptimeText}";

    public string LastAction
    {
        get => _lastAction;
        private set => Set(ref _lastAction, value);
    }

    public event EventHandler<HubNotice>? Notice;

    public event EventHandler? StateChanged;

    public static bool AnyFeatureEnabled(AppSettings settings)
        => FeatureIds.Automation.Any(id => settings.Features.TryGetValue(id, out var on) && on);

    public bool Start(AppSettings settings)
    {
        if (!AnyFeatureEnabled(settings))
        {
            Notice?.Invoke(this, new HubNotice("Enable at least one automation feature first.", NoticeKind.Error, DateTime.Now));
            return false;
        }

        IsRunning = true;
        _cycleTimer = 0;
        _finderTimer = 0;
        LastAction = _player.IsOfficial
            ? "Automation streams to the official test place bridge."
            : "Automation started on the demo island.";
        _log.Info($"Automation started ({(_player.IsOfficial ? "official" : "demo")}).");
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public void Stop(string reason = "manual")
    {
        if (!IsRunning) return;
        IsRunning = false;
        LastAction = $"Automation stopped ({reason}).";
        _log.Info($"Automation stopped: {reason}.");
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    public void ResetStats()
    {
        Cycles = 0;
        Actions = 0;
        _uptime = 0;
        Raise(nameof(UptimeText), nameof(StatsText));
    }

    /// <summary>Called from the UI timer. Keep this fast and allocation-light.</summary>
    public void Update(TimeSpan delta, AppSettings settings)
    {
        if (!IsRunning) return;

        var dt = Math.Clamp(delta.TotalSeconds, 0, 1);
        _uptime += dt;
        ModeText = _player.IsOfficial ? "OFFICIAL PLACE BRIDGE" : "DEMO SIMULATION";

        if (!AnyFeatureEnabled(settings))
        {
            Stop("no features enabled");
            Notice?.Invoke(this, new HubNotice("Automation stopped: no features are enabled.", NoticeKind.Automation, DateTime.Now));
            return;
        }

        _cycleTimer += dt;
        var interval = Math.Clamp(settings.Automation.IntervalMs / 1000.0, 0.2, 15);

        if (_player.IsOfficial)
        {
            // The bridge does the work; the hub just tracks the heartbeat and one-shot actions.
            if (_cycleTimer < interval) return;
            _cycleTimer = 0;
            Cycles++;

            if (settings.Features.TryGetValue(FeatureIds.AutoSell, out var sell) && sell && _eggs.PendingValue > 0)
            {
                _player.SendAutomationControl(true);
                LastAction = "Auto Sell active in the test place bridge.";
            }
            else
            {
                LastAction = settings.Automation.Target switch
                {
                    "Rarest" => "Bridge farming the rarest egg.",
                    "Highest value" => "Bridge farming the highest-value egg.",
                    "All" => "Bridge sweeping all eggs.",
                    _ => "Bridge farming the nearest egg."
                };
            }

            Raise(nameof(UptimeText), nameof(StatsText));
            return;
        }

        if (_cycleTimer < interval) return;
        _cycleTimer = 0;
        Cycles++;

        var budget = Math.Clamp(settings.Automation.MaxActionsPerCycle, 1, 10);
        var performed = 0;
        var notes = new List<string>();

        void Act(string message)
        {
            performed++;
            notes.Add(message);
        }

        // 1) Auto Return — leave the island bounds.
        if (On(settings, FeatureIds.AutoReturn) && _eggs.DistanceFromSpawn > 150)
        {
            _eggs.DemoRespawn();
            Act("Returned to the spawn pad.");
        }

        // 2) Auto Sell / Auto Buy — demo economy.
        if (On(settings, FeatureIds.AutoSell) && _eggs.PendingValue >= 120 && performed < budget)
        {
            var sold = _eggs.SellAll();
            if (sold > 0) Act($"Auto sold {sold} coins of eggs.");
        }

        if (On(settings, FeatureIds.AutoBuy) && _eggs.SessionCoins >= 100 && _eggs.Eggs.Count < 40 && performed < budget)
        {
            if (_eggs.SpendCoins(100))
            {
                var rarity = _eggs.SessionCoins > 400 ? EggRarity.Legendary : EggRarity.Rare;
                _eggs.DemoSpawnBonusEgg(rarity);
                Act($"Bought a lucky crate ({rarity} egg) for 100 coins.");
            }
        }

        // 3) Auto Finder / Rare Tracker — selection.
        _finderTimer += interval;
        if (On(settings, FeatureIds.AutoEggFinder) && _finderTimer >= 2 && performed < budget)
        {
            _finderTimer = 0;
            var best = _eggs.SourceMode == EggOrigin.Demo
                ? settings.Automation.Target switch
                {
                    "Rarest" => _eggs.RarestEgg(),
                    "Highest value" => _eggs.HighestValueEgg(),
                    _ => _eggs.NearestEgg()
                }
                : _eggs.BestEgg;

            if (best is not null && _eggs.Track(best))
                Act($"Tracking {best.Name} ({best.RarityText}).");
        }

        if (On(settings, FeatureIds.AutoRareTracker) && performed < budget)
        {
            var rarest = _eggs.RarestEgg();
            if (rarest is { IsRare: true } && !ReferenceEquals(_eggs.TrackedTarget, rarest))
            {
                if (_eggs.Track(rarest))
                {
                    Act($"Rare tracker locked onto {rarest.Name}.");
                    if (settings.Automation.Notify)
                        Notice?.Invoke(this, new HubNotice($"Rare egg tracked: {rarest.Name} · {rarest.ValueText}", NoticeKind.Rare, DateTime.Now));
                }
            }
        }

        // 4) Steal / Farm — movement + collection.
        var stealOn = On(settings, FeatureIds.AutoSteal);
        var farmOn = On(settings, FeatureIds.AutoFarm);

        if ((stealOn || farmOn) && performed < budget)
        {
            var target = stealOn
                ? (_eggs.RarestEgg() ?? _eggs.NearestEgg())
                : (_eggs.TrackedTarget ?? _eggs.NearestEgg());

            if (target is not null)
            {
                var speedFactor = Math.Clamp(settings.Player.WalkSpeed / 16.0, 0.5, 6);
                _eggs.DemoWalkTowards(target.Position, 0.35 * speedFactor);

                if (target.Distance <= 20 && _eggs.Collect(target))
                {
                    Act(stealOn
                        ? $"Stole {target.Name} ({target.RarityText})."
                        : $"Collected {target.Name} ({target.RarityText}).");
                    if (target.IsRare)
                        Notice?.Invoke(this, new HubNotice($"{(stealOn ? "Stolen" : "Collected")} {target.Name} · {target.ValueText} coins", NoticeKind.Success, DateTime.Now));
                }
            }
        }

        // 5) Auto Collect — no walking, only nearby eggs.
        if (On(settings, FeatureIds.AutoCollect) && performed < budget)
        {
            var nearest = _eggs.NearestEgg();
            if (nearest is not null && nearest.Distance <= 20 && _eggs.Collect(nearest))
                Act($"Collected nearby {nearest.Name}.");
        }

        if (performed > 0)
        {
            Actions += performed;
            LastAction = string.Join("  ", notes);
            if (settings.Automation.Notify && performed > 1)
                Notice?.Invoke(this, new HubNotice(LastAction, NoticeKind.Automation, DateTime.Now));
        }
        else
        {
            LastAction = ModeText == "DEMO SIMULATION"
                ? "Idle: no egg in range this cycle."
                : LastAction;
        }

        Raise(nameof(UptimeText), nameof(StatsText));
    }

    private static bool On(AppSettings settings, string id)
        => settings.Features.TryGetValue(id, out var value) && value;
}
