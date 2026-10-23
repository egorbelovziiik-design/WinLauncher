namespace StealAnEggUltimate;

public static class FeatureIds
{
    // Egg tab
    public const string EggEsp = "egg.esp";
    public const string RareEggEsp = "egg.rare.esp";
    public const string BestEggFinder = "egg.best";
    public const string EggScanner = "egg.scanner";
    public const string EggValueDisplay = "egg.value";
    public const string RareEggNotifications = "egg.rare.notify";
    public const string EggDistanceDisplay = "egg.distance";
    public const string EggRarityFilter = "egg.filter";
    public const string EggTracker = "egg.tracker";
    public const string EggList = "egg.list";

    // Player tab
    public const string Speed = "player.speed";
    public const string Fly = "player.fly";
    public const string Noclip = "player.noclip";
    public const string InfiniteJump = "player.infinitejump";
    public const string Teleport = "player.teleport";
    public const string JumpPower = "player.jumppower";
    public const string MovementSettings = "player.movement";
    public const string PlayerInfo = "player.info";

    // Automation tab
    public const string AutoCollect = "auto.collect";
    public const string AutoReturn = "auto.return";
    public const string AutoFarm = "auto.farm";
    public const string AutoSteal = "auto.steal";
    public const string AutoBuy = "auto.buy";
    public const string AutoSell = "auto.sell";
    public const string AutoEggFinder = "auto.finder";
    public const string AutoRareTracker = "auto.rare";

    public static readonly string[] Automation =
    {
        AutoCollect, AutoReturn, AutoFarm, AutoSteal, AutoBuy, AutoSell, AutoEggFinder, AutoRareTracker
    };
}

/// <summary>What a feature needs in order to do something real.</summary>
public enum FeatureScope
{
    /// <summary>Purely local overlay / UI behaviour. Always real, in any place.</summary>
    Local,

    /// <summary>Reads a read-only snapshot. Real data only when a snapshot source is available.</summary>
    Snapshot,

    /// <summary>Character movement. Real only inside the bundled official test place (Luau bridge).</summary>
    OfficialPlaceOnly
}

public sealed class FeatureDefinition
{
    public required string Id { get; init; }
    public required string Title { get; init; }
    public required string Description { get; init; }
    public required FeatureScope Scope { get; init; }
    public string Hint { get; init; } = string.Empty;

    /// <summary>Shown in the UI when the feature cannot act on a third-party place.</summary>
    public string? RestrictionNote => Scope switch
    {
        FeatureScope.OfficialPlaceOnly =>
            "Character control runs only in the bundled STEAL AN EGG test place (Luau bridge). " +
            "In other experiences this toggle is demo-only and never touches the game.",
        FeatureScope.Snapshot =>
            "Needs a read-only snapshot source: the official test place, or a JSON snapshot file. " +
            "Without one the hub shows generated demo eggs.",
        _ => null
    };
}

public static class FeatureRegistry
{
    public static readonly IReadOnlyList<FeatureDefinition> Egg = new List<FeatureDefinition>
    {
        new()
        {
            Id = FeatureIds.EggEsp, Title = "Egg ESP",
            Description = "Draws a coloured box, name and rarity label over every egg the hub knows about.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.RareEggEsp, Title = "Rare Egg ESP",
            Description = "Highlights only Rare and better eggs with the second ESP colour and a pulse.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.BestEggFinder, Title = "Best Egg Finder",
            Description = "Ranks eggs by rarity then value and marks the single best target with a locator.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.EggScanner, Title = "Egg Scanner",
            Description = "Periodically re-scans the egg index and updates distance + visibility data.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.EggValueDisplay, Title = "Egg Value Display",
            Description = "Shows each egg's measured coin value next to its ESP label.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.RareEggNotifications, Title = "Rare Egg Notifications",
            Description = "Raises an in-overlay toast when a rare egg appears or enters detection range.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.EggDistanceDisplay, Title = "Egg Distance Display",
            Description = "Adds live distance in studs to every egg label.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.EggRarityFilter, Title = "Egg Rarity Filter",
            Description = "Hides rarities disabled in the Egg Filters panel below.",
            Scope = FeatureScope.Local
        },
        new()
        {
            Id = FeatureIds.EggTracker, Title = "Egg Tracker",
            Description = "Follows one target egg: shows bearing, distance and an arrival ping on the HUD.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.EggList, Title = "Egg List",
            Description = "Feeds the discovered-eggs list on the right side of the Egg tab.",
            Scope = FeatureScope.Local
        }
    };

    public static readonly IReadOnlyList<FeatureDefinition> Player = new List<FeatureDefinition>
    {
        new()
        {
            Id = FeatureIds.Speed, Title = "Speed",
            Description = "Sets character WalkSpeed to the speed slider and streams it to the test place.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.Fly, Title = "Fly",
            Description = "Free-cam style flight using the fly key, at the configured fly speed.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.Noclip, Title = "Noclip",
            Description = "Disables collisions for your own character in the official test place.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.InfiniteJump, Title = "Infinite Jump",
            Description = "Lets you jump again in mid-air.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.Teleport, Title = "Teleport",
            Description = "Teleports your character to the X / Y / Z coordinates below.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.JumpPower, Title = "Jump Power",
            Description = "Applies the jump height slider to the character.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.MovementSettings, Title = "Movement Settings",
            Description = "Pushes speed, jump and keybind settings to the Luau movement module.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.PlayerInfo, Title = "Player Information",
            Description = "Live player name, health, position, coins and place from the active snapshot.",
            Scope = FeatureScope.Local
        }
    };

    public static readonly IReadOnlyList<FeatureDefinition> Automation = new List<FeatureDefinition>
    {
        new()
        {
            Id = FeatureIds.AutoCollect, Title = "Auto Collect",
            Description = "Walks the character to the nearest egg inside collection range and collects it.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.AutoReturn, Title = "Auto Return",
            Description = "Returns to the spawn pad whenever the character leaves the play area.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.AutoFarm, Title = "Auto Farm",
            Description = "Cycles egg locations and keeps collecting to grow a session coin total.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.AutoSteal, Title = "Auto Steal",
            Description = "Targets eggs currently held by rivals and steals them when in range.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.AutoBuy, Title = "Auto Buy",
            Description = "Purchases the cheapest eligible egg upgrade when the coin balance allows it.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.AutoSell, Title = "Auto Sell",
            Description = "Sells collected eggs once the configured value threshold is reached.",
            Scope = FeatureScope.OfficialPlaceOnly
        },
        new()
        {
            Id = FeatureIds.AutoEggFinder, Title = "Auto Egg Finder",
            Description = "Continuously re-scans and updates the tracker to the best new egg.",
            Scope = FeatureScope.Snapshot
        },
        new()
        {
            Id = FeatureIds.AutoRareTracker, Title = "Auto Rare Egg Tracker",
            Description = "Alerts and tracks any rare egg as soon as the scan sees it.",
            Scope = FeatureScope.Snapshot
        }
    };

    public static FeatureDefinition? Find(string id)
        => Egg.Concat(Player).Concat(Automation).FirstOrDefault(f => f.Id == id);
}
