use anyhow::Result;

#[cfg(windows)]
pub fn set(key: &str, value: &str) -> Result<()> { keyring::Entry::new("WinLauncher", key)?.set_password(value)?; Ok(()) }
#[cfg(windows)]
pub fn get(key: &str) -> Result<String> { Ok(keyring::Entry::new("WinLauncher", key)?.get_password()?) }
#[cfg(windows)]
pub fn delete(key: &str) -> Result<()> {
    match keyring::Entry::new("WinLauncher", key)?.delete_credential() { Ok(()) | Err(keyring::Error::NoEntry) => Ok(()), Err(e) => Err(e.into()) }
}
#[cfg(not(windows))]
pub fn set(_: &str, _: &str) -> Result<()> { anyhow::bail!("Защищённое хранилище требует Windows Credential Manager") }
#[cfg(not(windows))]
pub fn get(_: &str) -> Result<String> { anyhow::bail!("Защищённое хранилище требует Windows Credential Manager") }
#[cfg(not(windows))]
pub fn delete(_: &str) -> Result<()> { anyhow::bail!("Защищённое хранилище требует Windows Credential Manager") }
