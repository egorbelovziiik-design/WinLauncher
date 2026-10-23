using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace DrBrowser;

public sealed record BackupManifest(
    int FormatVersion,
    string Product,
    string AppVersion,
    DateTimeOffset CreatedUtc,
    string DatabaseSha256,
    bool SecretsExcluded,
    string Note);

/// <summary>
/// Creates and validates portable ZIP backups. The API-key secret directory and WebView2 profile
/// are deliberately excluded. Password rows remain DPAPI ciphertext and are only usable by the
/// same Windows account. Imports are bounded and reject traversal/unexpected files.
/// </summary>
public sealed class BackupService
{
    private const int FormatVersion = 1;
    private const long MaxEntryBytes = 256L * 1024 * 1024;
    private static readonly HashSet<string> AllowedEntries = new(StringComparer.OrdinalIgnoreCase)
    {
        "manifest.json", "drbrowser.db", "settings.json"
    };

    private readonly Db _db;

    public BackupService(Db db) => _db = db;

    public Task<BackupManifest> CreateAsync(string destination, AppSettings settings) => Task.Run(() =>
    {
        if (string.IsNullOrWhiteSpace(destination)) throw new ArgumentException("Не выбран файл резервной копии.");
        var fullDestination = Path.GetFullPath(destination);
        Directory.CreateDirectory(Path.GetDirectoryName(fullDestination)!);
        var staging = Path.Combine(Path.GetTempPath(), "DrBrowser", "backup-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(staging);
        try
        {
            var dbSnapshot = Path.Combine(staging, "drbrowser.db");
            using (var source = _db.Connect())
            using (var target = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = dbSnapshot }.ToString()))
            {
                target.Open();
                source.BackupDatabase(target);
            }

            var settingsPath = Path.Combine(staging, "settings.json");
            File.WriteAllText(settingsPath, JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true }));
            var databaseHash = Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(dbSnapshot)));
            var manifest = new BackupManifest(
                FormatVersion,
                AppSettings.AppName,
                AppSettings.Version,
                DateTimeOffset.UtcNow,
                databaseHash,
                true,
                "API keys and the WebView2 profile are excluded. Vault passwords remain Windows DPAPI ciphertext.");
            File.WriteAllText(Path.Combine(staging, "manifest.json"), JsonSerializer.Serialize(manifest, new JsonSerializerOptions { WriteIndented = true }));

            if (File.Exists(fullDestination)) File.Delete(fullDestination);
            ZipFile.CreateFromDirectory(staging, fullDestination, CompressionLevel.Optimal, includeBaseDirectory: false);
            return manifest;
        }
        finally
        {
            TryDelete(staging);
        }
    });

    public Task<BackupManifest> ValidateAsync(string archivePath) => Task.Run(() => Validate(archivePath));

    /// <summary>Validates completely, then atomically replaces settings and the SQLite database.</summary>
    public Task<BackupManifest> RestoreAsync(string archivePath) => Task.Run(() =>
    {
        var manifest = Validate(archivePath);
        var staging = Path.Combine(Path.GetTempPath(), "DrBrowser", "restore-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(staging);
        try
        {
            using var archive = ZipFile.OpenRead(archivePath);
            foreach (var entry in archive.Entries)
            {
                var normalized = entry.FullName.Replace('\\', '/').TrimStart('/');
                if (!AllowedEntries.Contains(normalized)) continue;
                var output = Path.Combine(staging, normalized);
                using var input = entry.Open();
                using var target = File.Create(output);
                input.CopyTo(target);
            }

            var incomingDb = Path.Combine(staging, "drbrowser.db");
            var incomingSettings = Path.Combine(staging, "settings.json");
            using (var checkConnection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = incomingDb, Mode = SqliteOpenMode.ReadOnly }.ToString()))
            {
                checkConnection.Open();
                using var integrity = checkConnection.CreateCommand();
                integrity.CommandText = "PRAGMA integrity_check;";
                if (!string.Equals(integrity.ExecuteScalar()?.ToString(), "ok", StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("SQLite integrity_check не прошёл.");
            }
            Directory.CreateDirectory(Path.GetDirectoryName(_db.FilePath)!);
            ReplaceFile(incomingDb, _db.FilePath);
            ReplaceFile(incomingSettings, AppPaths.SettingsFile);
            TryDelete(_db.FilePath + "-wal");
            TryDelete(_db.FilePath + "-shm");
            return manifest;
        }
        finally
        {
            TryDelete(staging);
        }
    });

    private static BackupManifest Validate(string archivePath)
    {
        if (string.IsNullOrWhiteSpace(archivePath) || !File.Exists(archivePath))
            throw new FileNotFoundException("Файл резервной копии не найден.", archivePath);
        if (new FileInfo(archivePath).Length > MaxEntryBytes)
            throw new InvalidDataException("Архив превышает безопасный предел 256 МБ.");

        using var archive = ZipFile.OpenRead(archivePath);
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var entry in archive.Entries)
        {
            var normalized = entry.FullName.Replace('\\', '/').TrimStart('/');
            if (normalized.Contains("..", StringComparison.Ordinal) || normalized.Contains('/'))
                throw new InvalidDataException("Архив содержит недопустимый путь: " + entry.FullName);
            if (!AllowedEntries.Contains(normalized))
                throw new InvalidDataException("Архив содержит неожиданный файл: " + normalized);
            if (entry.Length > MaxEntryBytes)
                throw new InvalidDataException("Слишком большой файл в архиве: " + normalized);
            names.Add(normalized);
        }
        if (!AllowedEntries.All(names.Contains)) throw new InvalidDataException("Архив неполный.");

        var manifestEntry = archive.GetEntry("manifest.json")!;
        BackupManifest manifest;
        using (var stream = manifestEntry.Open())
            manifest = JsonSerializer.Deserialize<BackupManifest>(stream)
                       ?? throw new InvalidDataException("Manifest не читается.");
        if (manifest.FormatVersion != FormatVersion || manifest.Product != AppSettings.AppName)
            throw new InvalidDataException("Формат резервной копии не поддерживается.");
        using (var settingsStream = archive.GetEntry("settings.json")!.Open())
        {
            var settings = JsonSerializer.Deserialize<AppSettings>(settingsStream);
            if (settings is null) throw new InvalidDataException("Настройки в архиве не читаются.");
            settings.Normalize();
        }

        var databaseEntry = archive.GetEntry("drbrowser.db")!;
        using var databaseStream = databaseEntry.Open();
        var actualHash = Convert.ToHexString(SHA256.HashData(databaseStream));
        if (!string.Equals(actualHash, manifest.DatabaseSha256, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("Контрольная сумма базы не совпала.");
        return manifest;
    }

    private static void ReplaceFile(string source, string destination)
    {
        var temporary = destination + ".restore-new";
        File.Copy(source, temporary, overwrite: true);
        File.Move(temporary, destination, overwrite: true);
    }

    private static void TryDelete(string path)
    {
        try
        {
            if (Directory.Exists(path)) Directory.Delete(path, true);
            else if (File.Exists(path)) File.Delete(path);
        }
        catch { }
    }
}
