using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;

namespace StealAnEggUltimate;

public sealed record CommandResult(bool Ok, IReadOnlyList<string> Lines)
{
    public string Output => string.Join(Environment.NewLine, Lines);
}

/// <summary>
/// Hooks the command interpreter needs. Every capability is an explicit delegate,
/// so the interpreter has zero access to reflection, process APIs or arbitrary code.
/// </summary>
public sealed class CommandContext
{
    public required Func<bool> IsOverlayVisible { get; init; }
    public required Action ShowOverlay { get; init; }
    public required Action HideOverlay { get; init; }
    public required Action ToggleOverlay { get; init; }
    public required Action ScanEggs { get; init; }
    public required Action<bool> SetRareNotifications { get; init; }
    public required Action<bool> SetAutomation { get; init; }
    public required Func<bool> SaveConfig { get; init; }
    public required Func<bool> LoadConfig { get; init; }
    public required Action<string> Print { get; init; }
    public required Action<string> Notify { get; init; }
    public required Action<double, double, double> Teleport { get; init; }
    public required Func<IReadOnlyList<Egg>> Eggs { get; init; }
    public required Action<int> SetInterval { get; init; }
    public required Action<string> SetEspColor { get; init; }
    public required Action TrackBestEgg { get; init; }
    public required Action ClearConsole { get; init; }
    public required Func<bool> IsConnected { get; init; }
}

/// <summary>
/// Safe, sandboxed command interpreter for the hub's own scripting language.
/// Only the whitelisted functions below exist; arguments are parsed as literals
/// and validated. Nothing is executed inside Roblox and there is no eval/reflection.
/// </summary>
public sealed class CommandManager
{
    private static readonly Regex CallPattern = new(
        @"^(?<name>[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)\s*\((?<args>.*)\)\s*;?\s*$",
        RegexOptions.Compiled | RegexOptions.CultureInvariant);

    private readonly Logger _log;
    private CommandContext? _context;

    public CommandManager(Logger log)
    {
        _log = log;
    }

    public string? LastError { get; private set; }

    public void Attach(CommandContext context) => _context = context;

    public static IReadOnlyList<string> SupportedCommands { get; } = new List<string>
    {
        "overlay.show()",
        "overlay.hide()",
        "overlay.toggle()",
        "egg.scan()",
        "egg.trackbest()",
        "egg.notify(true|false)",
        "esp.color(\"#RRGGBB\")",
        "player.teleport(x, y, z)",
        "automation.start()",
        "automation.stop()",
        "automation.interval(ms)",
        "settings.save()",
        "settings.load()",
        "hub.status()",
        "hub.eggs()",
        "console.clear()",
        "help()"
    };

    public CommandResult Execute(string script)
    {
        var lines = new List<string>();
        if (_context is null)
            return new CommandResult(false, new[] { "Command interpreter is not ready." });

        if (string.IsNullOrWhiteSpace(script))
            return new CommandResult(false, new[] { "Nothing to execute. Type help() to list commands." });

        var statements = SplitStatements(script);
        var failures = 0;

        foreach (var statement in statements)
        {
            var result = ExecuteOne(statement, lines);
            if (!result) failures++;
        }

        return new CommandResult(failures == 0, lines);
    }

    private bool ExecuteOne(string raw, List<string> output)
    {
        var statement = raw.Trim();
        if (statement.Length == 0) return true;

        // Comments: -- and # to end of line
        var commentIndex = statement.IndexOf("--", StringComparison.Ordinal);
        if (commentIndex >= 0) statement = statement[..commentIndex].Trim();
        if (statement.StartsWith('#')) return true;
        if (statement.Length == 0) return true;

        var match = CallPattern.Match(statement);
        if (!match.Success)
        {
            output.Add($"error: '{statement}' is not a valid command call (expected name(args)).");
            _log.Warn($"Command syntax error: {statement}");
            return false;
        }

        var name = match.Groups["name"].Value.ToLowerInvariant();
        var argText = match.Groups["args"].Value.Trim();

        if (!TryParseArgs(argText, out var args, out var error))
        {
            output.Add($"error: {error}");
            return false;
        }

        try
        {
            return Dispatch(name, args, output);
        }
        catch (Exception ex)
        {
            output.Add($"error: {name} failed :: {ex.Message}");
            _log.Error($"Command '{name}' failed.", ex);
            return false;
        }
    }

    private bool Dispatch(string name, List<object> args, List<string> output)
    {
        var ctx = _context!;

        bool Expect(int count)
        {
            if (args.Count == count) return true;
            output.Add($"error: {name} expects {count} argument(s), got {args.Count}.");
            return false;
        }

        switch (name)
        {
            case "overlay.show":
                if (!Expect(0)) return false;
                ctx.ShowOverlay();
                output.Add("overlay: show requested.");
                return true;

            case "overlay.hide":
                if (!Expect(0)) return false;
                ctx.HideOverlay();
                output.Add("overlay: hide requested.");
                return true;

            case "overlay.toggle":
                if (!Expect(0)) return false;
                ctx.ToggleOverlay();
                output.Add($"overlay: toggled (visible = {ctx.IsOverlayVisible()}).");
                return true;

            case "egg.scan":
                if (!Expect(0)) return false;
                ctx.ScanEggs();
                output.Add($"egg: scan requested ({ctx.Eggs().Count} eggs indexed).");
                return true;

            case "egg.trackbest":
                if (!Expect(0)) return false;
                ctx.TrackBestEgg();
                output.Add("egg: tracking the best egg.");
                return true;

            case "egg.notify":
            {
                if (!Expect(1)) return false;
                if (args[0] is not bool flag)
                {
                    output.Add("error: egg.notify expects true or false.");
                    return false;
                }

                ctx.SetRareNotifications(flag);
                output.Add($"egg: rare notifications {(flag ? "enabled" : "disabled")}.");
                return true;
            }

            case "esp.color":
            {
                if (!Expect(1)) return false;
                if (args[0] is not string hex || !Regex.IsMatch(hex, "^#?[0-9A-Fa-f]{6}$"))
                {
                    output.Add("error: esp.color expects a hex colour like \"#C084FC\".");
                    return false;
                }

                ctx.SetEspColor(hex.StartsWith('#') ? hex : "#" + hex);
                output.Add($"esp: colour set to {hex}.");
                return true;
            }

            case "player.teleport":
            {
                if (!Expect(3)) return false;
                var values = new double[3];
                for (var i = 0; i < 3; i++)
                {
                    if (args[i] is not double d)
                    {
                        output.Add("error: player.teleport expects three numbers.");
                        return false;
                    }

                    if (double.IsNaN(d) || Math.Abs(d) > 100000)
                    {
                        output.Add("error: teleport coordinates must be finite and within ±100000.");
                        return false;
                    }

                    values[i] = d;
                }

                ctx.Teleport(values[0], values[1], values[2]);
                output.Add($"player: teleport to {values[0]:0.#}, {values[1]:0.#}, {values[2]:0.#} requested.");
                return true;
            }

            case "automation.start":
                if (!Expect(0)) return false;
                ctx.SetAutomation(true);
                output.Add("automation: start requested.");
                return true;

            case "automation.stop":
                if (!Expect(0)) return false;
                ctx.SetAutomation(false);
                output.Add("automation: stop requested.");
                return true;

            case "automation.interval":
            {
                if (!Expect(1)) return false;
                if (args[0] is not double ms || ms < 200 || ms > 15000)
                {
                    output.Add("error: automation.interval expects 200..15000 milliseconds.");
                    return false;
                }

                ctx.SetInterval((int)Math.Round(ms));
                output.Add($"automation: interval set to {(int)Math.Round(ms)} ms.");
                return true;
            }

            case "settings.save":
                if (!Expect(0)) return false;
                output.Add(ctx.SaveConfig() ? "settings: saved." : "error: settings could not be saved.");
                return true;

            case "settings.load":
                if (!Expect(0)) return false;
                output.Add(ctx.LoadConfig() ? "settings: loaded." : "error: settings could not be loaded.");
                return true;

            case "hub.status":
                if (!Expect(0)) return false;
                output.Add($"hub: overlay visible = {ctx.IsOverlayVisible()}, connected = {ctx.IsConnected()}.");
                return true;

            case "hub.eggs":
            {
                if (!Expect(0)) return false;
                var eggs = ctx.Eggs();
                output.Add($"hub: {eggs.Count} eggs indexed.");
                foreach (var egg in eggs.OrderByDescending(e => e.Rarity).ThenByDescending(e => e.Value).Take(8))
                    output.Add($"   {egg.Name,-16} {egg.RarityText,-10} {egg.Value,6}  {egg.Distance,6:0} studs  [{egg.OriginText}]");
                return true;
            }

            case "console.clear":
                if (!Expect(0)) return false;
                ctx.ClearConsole();
                return true;

            case "help":
                if (!Expect(0)) return false;
                output.Add("Supported commands:");
                foreach (var command in SupportedCommands) output.Add("   " + command);
                output.Add("Arguments are literals only: numbers, true/false, \"strings\".");
                return true;

            default:
                output.Add($"error: unknown command '{name}'. Type help().");
                return false;
        }
    }

    private static List<string> SplitStatements(string script)
    {
        var statements = new List<string>();
        var current = new StringBuilder();

        foreach (var rawLine in script.Replace("\r\n", "\n").Split('\n'))
        {
            var line = rawLine.Trim();
            if (line.Length == 0) continue;
            if (line.StartsWith('#') && current.Length == 0) continue;
            if (line.StartsWith("--", StringComparison.Ordinal) && current.Length == 0) continue;

            if (current.Length > 0) current.Append(' ');
            current.Append(line);

            var text = current.ToString();
            if (CountChar(text, '(') <= CountChar(text, ')'))
            {
                statements.Add(text);
                current.Clear();
            }
        }

        if (current.Length > 0) statements.Add(current.ToString());
        return statements;
    }

    private static int CountChar(string value, char c) => value.Count(ch => ch == c);

    private static bool TryParseArgs(string text, out List<object> args, out string error)
    {
        args = new List<object>();
        error = string.Empty;

        if (string.IsNullOrWhiteSpace(text)) return true;

        var buffer = new StringBuilder();
        var inString = false;
        var escaped = false;
        var parts = new List<string>();

        foreach (var ch in text)
        {
            if (inString)
            {
                buffer.Append(ch);
                if (escaped)
                {
                    escaped = false;
                    continue;
                }

                if (ch == '\\')
                {
                    escaped = true;
                    continue;
                }

                if (ch == '"') inString = false;
                continue;
            }

            switch (ch)
            {
                case '"':
                    inString = true;
                    buffer.Append(ch);
                    break;
                case ',':
                    parts.Add(buffer.ToString().Trim());
                    buffer.Clear();
                    break;
                default:
                    buffer.Append(ch);
                    break;
            }
        }

        if (inString)
        {
            error = "unterminated string literal.";
            return false;
        }

        parts.Add(buffer.ToString().Trim());

        foreach (var part in parts)
        {
            if (part.Length == 0)
            {
                error = "empty argument.";
                return false;
            }

            if (part.StartsWith('"'))
            {
                if (!part.EndsWith('"') || part.Length < 2)
                {
                    error = "malformed string literal.";
                    return false;
                }

                args.Add(part[1..^1].Replace("\\\"", "\"").Replace("\\\\", "\\"));
                continue;
            }

            if (bool.TryParse(part, out var boolean))
            {
                args.Add(boolean);
                continue;
            }

            if (double.TryParse(part, NumberStyles.Float, CultureInfo.InvariantCulture, out var number))
            {
                args.Add(number);
                continue;
            }

            error = $"'{part}' is not a number, boolean or string literal.";
            return false;
        }

        return true;
    }
}
