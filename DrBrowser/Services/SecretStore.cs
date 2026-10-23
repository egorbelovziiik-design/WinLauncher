using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace DrBrowser;

/// <summary>
/// Windows DPAPI (per-user) wrapper. Secrets are never written in plain text and are
/// bound to the current Windows account, so copying the file to another PC is useless.
/// </summary>
public static class SecretStore
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("DrBrowser.SecretStore.v1");

    public static string Protect(string plainText)
    {
        var bytes = Encoding.UTF8.GetBytes(plainText);
        var enc = ProtectedData.Protect(bytes, Entropy, DataProtectionScope.CurrentUser);
        return Convert.ToBase64String(enc);
    }

    public static string? Unprotect(string protectedBase64)
    {
        try
        {
            var enc = Convert.FromBase64String(protectedBase64);
            var bytes = ProtectedData.Unprotect(enc, Entropy, DataProtectionScope.CurrentUser);
            return Encoding.UTF8.GetString(bytes);
        }
        catch
        {
            return null;
        }
    }

    public static byte[] ProtectBytes(string plainText)
    {
        var bytes = Encoding.UTF8.GetBytes(plainText);
        return ProtectedData.Protect(bytes, Entropy, DataProtectionScope.CurrentUser);
    }

    public static string? UnprotectBytes(byte[] protectedBytes)
    {
        try
        {
            return Encoding.UTF8.GetString(ProtectedData.Unprotect(protectedBytes, Entropy, DataProtectionScope.CurrentUser));
        }
        catch
        {
            return null;
        }
    }

    // ---------------- AI key file ----------------

    public static string AiKeyFile => Path.Combine(AppPaths.SecretsDir, "ai.key");

    public static bool HasAiKey()
        => File.Exists(AiKeyFile) && !string.IsNullOrWhiteSpace(ReadAiKey());

    public static void SaveAiKey(string key)
    {
        AppPaths.EnsureCreated();
        File.WriteAllText(AiKeyFile, Protect(key.Trim()));
    }

    public static string? ReadAiKey()
    {
        try
        {
            if (!File.Exists(AiKeyFile)) return null;
            return Unprotect(File.ReadAllText(AiKeyFile).Trim());
        }
        catch
        {
            return null;
        }
    }

    public static void ClearAiKey()
    {
        try
        {
            if (File.Exists(AiKeyFile)) File.Delete(AiKeyFile);
        }
        catch
        {
            // ignore
        }
    }
}
