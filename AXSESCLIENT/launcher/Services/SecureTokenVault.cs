using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace AxsesClient.Launcher.Services;

public static partial class SecureTokenVault
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("AXSESCLIENT.GENESIS_X.TOKEN_VAULT.V1");
    private static readonly HashSet<string> ActiveSensitiveTokens = new(StringComparer.Ordinal);
    private static readonly object LockObj = new();

    public static void RegisterSensitiveToken(string? secret)
    {
        if (string.IsNullOrWhiteSpace(secret) || secret.Length < 8) return;
        lock (LockObj)
        {
            ActiveSensitiveTokens.Add(secret);
        }
    }

    public static string Encrypt(string? plainText, string vaultKeyDir)
    {
        if (string.IsNullOrEmpty(plainText)) return "";
        RegisterSensitiveToken(plainText);
        byte[] plainBytes = Encoding.UTF8.GetBytes(plainText);

        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            byte[] cipher = ProtectedData.Protect(plainBytes, Entropy, DataProtectionScope.CurrentUser);
            return "dpapi:" + Convert.ToBase64String(cipher);
        }

        // Cross-platform AES-256-GCM fallback using restricted per-user vault key
        byte[] key = GetOrCreateLocalKey(vaultKeyDir);
        byte[] nonce = RandomNumberGenerator.GetBytes(12);
        byte[] cipherBytes = new byte[plainBytes.Length];
        byte[] tag = new byte[16];
        using var aes = new AesGcm(key, 16);
        aes.Encrypt(nonce, plainBytes, cipherBytes, tag, Entropy);

        byte[] combined = new byte[12 + 16 + cipherBytes.Length];
        Buffer.BlockCopy(nonce, 0, combined, 0, 12);
        Buffer.BlockCopy(tag, 0, combined, 12, 16);
        Buffer.BlockCopy(cipherBytes, 0, combined, 28, cipherBytes.Length);
        return "aesgcm:" + Convert.ToBase64String(combined);
    }

    public static string Decrypt(string? encryptedPayload, string vaultKeyDir)
    {
        if (string.IsNullOrWhiteSpace(encryptedPayload)) return "";
        try
        {
            if (encryptedPayload.StartsWith("dpapi:", StringComparison.Ordinal))
            {
                if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
                    return "";
                byte[] cipher = Convert.FromBase64String(encryptedPayload["dpapi:".Length..]);
                byte[] plain = ProtectedData.Unprotect(cipher, Entropy, DataProtectionScope.CurrentUser);
                string token = Encoding.UTF8.GetString(plain);
                RegisterSensitiveToken(token);
                return token;
            }

            if (encryptedPayload.StartsWith("aesgcm:", StringComparison.Ordinal))
            {
                byte[] combined = Convert.FromBase64String(encryptedPayload["aesgcm:".Length..]);
                if (combined.Length < 28) return "";
                byte[] key = GetOrCreateLocalKey(vaultKeyDir);
                byte[] nonce = combined[..12];
                byte[] tag = combined[12..28];
                byte[] cipherBytes = combined[28..];
                byte[] plainBytes = new byte[cipherBytes.Length];
                using var aes = new AesGcm(key, 16);
                aes.Decrypt(nonce, cipherBytes, tag, plainBytes, Entropy);
                string token = Encoding.UTF8.GetString(plainBytes);
                RegisterSensitiveToken(token);
                return token;
            }
        }
        catch
        {
            return "";
        }
        return "";
    }

    public static string ScrubSecrets(string? logLine)
    {
        if (string.IsNullOrEmpty(logLine)) return "";
        string scrubbed = logLine;

        lock (LockObj)
        {
            foreach (string secret in ActiveSensitiveTokens)
            {
                if (scrubbed.Contains(secret, StringComparison.Ordinal))
                    scrubbed = scrubbed.Replace(secret, "[REDACTED_TOKEN]", StringComparison.Ordinal);
            }
        }

        // Also mask --accessToken <value> CLI argument patterns and JWT-like tokens
        scrubbed = AccessTokenArgRegex().Replace(scrubbed, "--accessToken [REDACTED]");
        scrubbed = JwtPatternRegex().Replace(scrubbed, "[REDACTED_JWT]");
        return scrubbed;
    }

    private static byte[] GetOrCreateLocalKey(string vaultKeyDir)
    {
        Directory.CreateDirectory(vaultKeyDir);
        string keyPath = Path.Combine(vaultKeyDir, ".vault.key");
        if (File.Exists(keyPath))
        {
            byte[] existing = File.ReadAllBytes(keyPath);
            if (existing.Length == 32) return existing;
        }
        byte[] generated = RandomNumberGenerator.GetBytes(32);
        File.WriteAllBytes(keyPath, generated);
        if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            try
            {
                File.SetUnixFileMode(keyPath, UnixFileMode.UserRead | UnixFileMode.UserWrite);
            }
            catch
            {
                // Ignore if filesystem does not support POSIX permissions
            }
        }
        return generated;
    }

    [GeneratedRegex(@"--accessToken\s+\S+", RegexOptions.IgnoreCase)]
    private static partial Regex AccessTokenArgRegex();

    [GeneratedRegex(@"eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}")]
    private static partial Regex JwtPatternRegex();
}
