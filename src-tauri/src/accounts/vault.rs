use anyhow::Result;

// Windows Credential Manager limits a credential blob to 2560 bytes.
// OAuth token bundles can exceed this. Keep them in independently protected,
// bounded chunks; switch a small manifest only after every new chunk is saved.
#[cfg(windows)]
mod windows_vault {
    use anyhow::{bail, Context, Result};
    const PREFIX: &str = "WinLauncherVaultV1";
    fn entry(key: &str) -> Result<keyring::Entry> { Ok(keyring::Entry::new("WinLauncher", key)?) }
    fn manifest(raw: &str) -> Option<(String, usize)> {
        let parts: Vec<_> = raw.split('/').collect();
        if parts.len() != 3 || parts[0] != PREFIX || uuid::Uuid::parse_str(parts[1]).is_err() { return None; }
        let count: usize = parts[2].parse().ok()?;
        if !(1..=128).contains(&count) { return None; }
        Some((parts[1].into(), count))
    }
    fn remove_generation(key: &str, generation: &str, count: usize) {
        for index in 0..count { if let Ok(e) = entry(&format!("{key}/{generation}/{index}")) { let _ = e.delete_credential(); } }
    }
    pub fn set(key: &str, value: &str) -> Result<()> {
        if value.len() > 64 * 1024 { bail!("Секрет превышает безопасный лимит хранилища"); }
        let root = entry(key)?; let previous = root.get_password().ok();
        let mut chunks = vec![String::new()];
        for c in value.chars() {
            if chunks.last().map_or(0, |s| s.len()) + c.len_utf8() > 1000 { chunks.push(String::new()); }
            chunks.last_mut().context("Нет части секрета")?.push(c);
        }
        let generation = uuid::Uuid::new_v4().to_string();
        let result: Result<()> = (|| {
            for (index, chunk) in chunks.iter().enumerate() { entry(&format!("{key}/{generation}/{index}"))?.set_password(chunk)?; }
            root.set_password(&format!("{PREFIX}/{generation}/{}", chunks.len()))?; Ok(())
        })();
        if result.is_err() { remove_generation(key, &generation, chunks.len()); return result; }
        if let Some((old, count)) = previous.as_deref().and_then(manifest) { remove_generation(key, &old, count); }
        Ok(())
    }
    pub fn get(key: &str) -> Result<String> {
        let raw = entry(key)?.get_password()?;
        if let Some((generation, count)) = manifest(&raw) {
            let mut result = String::new();
            for index in 0..count { result.push_str(&entry(&format!("{key}/{generation}/{index}"))?.get_password()?); }
            Ok(result)
        } else { Ok(raw) } // Read older single-credential values, migrated on the next write.
    }
    pub fn delete(key: &str) -> Result<()> {
        let root = entry(key)?; let previous = root.get_password().ok();
        match root.delete_credential() { Ok(()) | Err(keyring::Error::NoEntry) => {}, Err(e) => return Err(e.into()) }
        if let Some((generation, count)) = previous.as_deref().and_then(manifest) { remove_generation(key, &generation, count); }
        Ok(())
    }
}
#[cfg(windows)] pub use windows_vault::{set, get, delete};
#[cfg(not(windows))]
pub fn set(_: &str, _: &str) -> Result<()> { anyhow::bail!("Защищённое хранилище требует Windows Credential Manager") }
#[cfg(not(windows))]
pub fn get(_: &str) -> Result<String> { anyhow::bail!("Защищённое хранилище требует Windows Credential Manager") }
#[cfg(not(windows))]
pub fn delete(_: &str) -> Result<()> { anyhow::bail!("Защищённое хранилище требует Windows Credential Manager") }
