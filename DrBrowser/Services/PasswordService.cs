using System.Collections.ObjectModel;
using System.Security.Cryptography;

namespace DrBrowser;

/// <summary>
/// Local password vault. Secrets are encrypted with Windows DPAPI for the current user
/// before they touch SQLite, and are only decrypted in memory for display/copy.
/// There is intentionally no autofill: nothing is captured from pages automatically.
/// </summary>
public sealed class PasswordService
{
    private readonly Db _db;
    private readonly Logger _log;

    public PasswordService(Db db, Logger log)
    {
        _db = db;
        _log = log;
    }

    public ObservableCollection<PasswordEntry> Entries { get; } = new();

    public async Task LoadAsync()
    {
        List<(long Id, string Site, string Username, byte[] Secret, DateTime Created)> rows;
        try
        {
            rows = await Task.Run(_db.QueryPasswords).ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            _log.Error("password read failed", ex);
            rows = new List<(long, string, string, byte[], DateTime)>();
        }

        Entries.Clear();
        foreach (var row in rows)
        {
            var plain = SecretStore.UnprotectBytes(row.Secret) ?? string.Empty;
            Entries.Add(new PasswordEntry
            {
                Id = row.Id,
                Site = row.Site,
                Username = row.Username,
                Password = plain,
                CreatedAt = row.Created
            });
        }
    }

    public async Task<PasswordEntry> AddAsync(string site, string username, string password)
    {
        var encrypted = SecretStore.ProtectBytes(password);
        var created = DateTime.Now;
        var id = await Task.Run(() => _db.AddPassword(site.Trim(), username.Trim(), encrypted, created)).ConfigureAwait(true);

        var entry = new PasswordEntry
        {
            Id = id,
            Site = site.Trim(),
            Username = username.Trim(),
            Password = password,
            CreatedAt = created
        };

        Entries.Add(entry);
        return entry;
    }

    public async Task DeleteAsync(PasswordEntry entry)
    {
        await Task.Run(() => _db.DeletePassword(entry.Id)).ConfigureAwait(true);
        Entries.Remove(entry);
    }

    public IEnumerable<PasswordEntry> Search(string? query)
    {
        if (string.IsNullOrWhiteSpace(query)) return Entries;
        var text = query.Trim();
        return Entries.Where(e => e.Site.Contains(text, StringComparison.OrdinalIgnoreCase)
                                  || e.Username.Contains(text, StringComparison.OrdinalIgnoreCase));
    }

    /// <summary>Cryptographically strong password generator.</summary>
    public static string Generate(int length = 20, bool symbols = true, bool digits = true, bool upper = true)
    {
        length = Math.Clamp(length, 8, 64);
        const string lowerPool = "abcdefghijkmnopqrstuvwxyz";
        const string upperPool = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        const string digitPool = "23456789";
        const string symbolPool = "!@#$%^&*()-_=+[]{}";

        var pool = new List<char>(lowerPool);
        if (upper) pool.AddRange(upperPool);
        if (digits) pool.AddRange(digitPool);
        if (symbols) pool.AddRange(symbolPool);

        // A random draw from the combined pool can occasionally omit a requested
        // class. Seed one CSPRNG-selected character from every enabled class, fill
        // the remainder, then CSPRNG-shuffle so required characters have no fixed
        // positions.
        var chars = new List<char>(length)
        {
            lowerPool[RandomNumberGenerator.GetInt32(lowerPool.Length)]
        };
        if (upper) chars.Add(upperPool[RandomNumberGenerator.GetInt32(upperPool.Length)]);
        if (digits) chars.Add(digitPool[RandomNumberGenerator.GetInt32(digitPool.Length)]);
        if (symbols) chars.Add(symbolPool[RandomNumberGenerator.GetInt32(symbolPool.Length)]);
        while (chars.Count < length) chars.Add(pool[RandomNumberGenerator.GetInt32(pool.Count)]);

        for (var i = chars.Count - 1; i > 0; i--)
        {
            var j = RandomNumberGenerator.GetInt32(i + 1);
            (chars[i], chars[j]) = (chars[j], chars[i]);
        }

        return new string(chars.ToArray());
    }

    /// <summary>Rough strength estimate used by the UI (0..4).</summary>
    public static int Strength(string password)
    {
        if (string.IsNullOrEmpty(password)) return 0;

        var score = 0;
        if (password.Length >= 10) score++;
        if (password.Length >= 16) score++;
        if (password.Any(char.IsUpper) && password.Any(char.IsLower)) score++;
        if (password.Any(char.IsDigit)) score++;
        if (password.Any(c => !char.IsLetterOrDigit(c))) score++;
        return Math.Clamp(score, 0, 4);
    }

    public static string StrengthText(int score) => score switch
    {
        0 => "очень слабый",
        1 => "слабый",
        2 => "средний",
        3 => "хороший",
        _ => "сильный"
    };
}
