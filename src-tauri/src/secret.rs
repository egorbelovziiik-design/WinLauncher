use std::path::PathBuf;

/// Stores small secrets (Microsoft/Minecraft tokens, CurseForge key).
/// On Windows the OS credential vault (DPAPI-backed Credential Manager) is used.
/// Other platforms fall back to a private file, used only for development builds.
pub struct SecretStore {
    dir: PathBuf,
}

impl SecretStore {
    pub fn new(dir: PathBuf) -> Self {
        let _ = std::fs::create_dir_all(&dir);
        Self { dir }
    }

    fn file_for(&self, key: &str) -> PathBuf {
        let safe: String = key
            .chars()
            .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
            .collect();
        self.dir.join(format!("secret-{safe}"))
    }

    pub fn set(&self, key: &str, value: &str) -> Result<(), String> {
        #[cfg(windows)]
        {
            let stored = keyring::Entry::new("WinLaucher", key)
                .and_then(|entry| entry.set_password(value));
            if stored.is_ok() {
                let _ = std::fs::remove_file(self.file_for(key));
                return Ok(());
            }
            // Credential Manager can be unavailable (locked-down machines): keep working.
        }
        std::fs::write(self.file_for(key), value).map_err(|e| e.to_string())
    }

    pub fn get(&self, key: &str) -> Option<String> {
        #[cfg(windows)]
        {
            if let Ok(entry) = keyring::Entry::new("WinLaucher", key) {
                if let Ok(value) = entry.get_password() {
                    if !value.is_empty() {
                        return Some(value);
                    }
                }
            }
        }
        std::fs::read_to_string(self.file_for(key)).ok().filter(|v| !v.is_empty())
    }

    pub fn clear(&self, key: &str) {
        #[cfg(windows)]
        {
            if let Ok(entry) = keyring::Entry::new("WinLaucher", key) {
                let _ = entry.set_password("");
            }
        }
        let _ = std::fs::remove_file(self.file_for(key));
    }
}
